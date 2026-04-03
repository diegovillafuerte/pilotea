import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { randomUUID } from "crypto";
import { requireAuth } from "@/lib/auth/middleware";
import { uploadFile } from "@/lib/storage/r2";
import { parseUpload } from "@/lib/parsers";
import type { Platform, UploadType } from "@/lib/parsers/types";
import {
  createUpload,
  updateUploadStatus,
  linkUploadToWeeklyData,
  getUploadsByDriver,
} from "@/lib/db/queries/uploads";
import { upsertWeeklyData } from "@/lib/db/queries/weekly-data";
import { updateLastUploadAt } from "@/lib/db/queries/drivers";

// ─── Constants ────────────────────────────────────────────────

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

const ALLOWED_MIME_TYPES = new Set([
  "image/png",
  "image/jpeg",
  "image/webp",
  "application/pdf",
]);

const MIME_TO_EXT: Record<string, string> = {
  "image/png": "png",
  "image/jpeg": "jpg",
  "image/webp": "webp",
  "application/pdf": "pdf",
};

// ─── Validation schema ───────────────────────────────────────

const uploadSchema = z.object({
  platform: z.enum(["uber", "didi", "indrive"], {
    errorMap: () => ({
      message:
        'Plataforma invalida. Debe ser "uber", "didi" o "indrive".',
    }),
  }),
  upload_type: z.enum(["pdf", "screenshot"], {
    errorMap: () => ({
      message: 'Tipo de archivo invalido. Debe ser "pdf" o "screenshot".',
    }),
  }),
});

// ─── Helpers ─────────────────────────────────────────────────

function toDecimalString(value: number | null): string | null {
  if (value === null) return null;
  return value.toFixed(2);
}

// ─── POST /api/uploads ──────────────────────────────────────

export async function POST(request: NextRequest) {
  // 1. Auth check
  const auth = await requireAuth();
  if (auth instanceof NextResponse) return auth;
  const { driverId } = auth;

  try {
    // 2. Parse FormData
    let formData: FormData;
    try {
      formData = await request.formData();
    } catch {
      return NextResponse.json(
        { ok: false, error: "No se pudo leer el formulario. Envia los datos como FormData." },
        { status: 400 },
      );
    }

    const platformRaw = formData.get("platform");
    const uploadTypeRaw = formData.get("upload_type");
    const files = formData.getAll("files");

    // 3. Validate platform and upload_type with Zod
    const validation = uploadSchema.safeParse({
      platform: platformRaw,
      upload_type: uploadTypeRaw,
    });

    if (!validation.success) {
      const errors = validation.error.errors.map((e) => e.message).join(" ");
      return NextResponse.json(
        { ok: false, error: errors },
        { status: 400 },
      );
    }

    const { platform, upload_type: uploadType } = validation.data;

    // 4. Validate files exist
    if (!files || files.length === 0) {
      return NextResponse.json(
        { ok: false, error: "No se encontraron archivos. Selecciona al menos un archivo para subir." },
        { status: 400 },
      );
    }

    // 5. Validate each file
    const fileBuffers: Buffer[] = [];
    let mimeType = "";

    for (const file of files) {
      if (!(file instanceof File)) {
        return NextResponse.json(
          { ok: false, error: "Formato de archivo invalido." },
          { status: 400 },
        );
      }

      // Check MIME type
      if (!ALLOWED_MIME_TYPES.has(file.type)) {
        return NextResponse.json(
          {
            ok: false,
            error: `Tipo de archivo no permitido: ${file.type}. Solo se aceptan PNG, JPG, WebP y PDF.`,
          },
          { status: 400 },
        );
      }

      // Check file size
      if (file.size > MAX_FILE_SIZE) {
        return NextResponse.json(
          {
            ok: false,
            error: `El archivo "${file.name}" excede el limite de 10 MB.`,
          },
          { status: 413 },
        );
      }

      const arrayBuffer = await file.arrayBuffer();
      fileBuffers.push(Buffer.from(arrayBuffer));
      mimeType = file.type;
    }

    // 6. Generate upload ID and upload to R2
    const uploadId = randomUUID();
    const ext = MIME_TO_EXT[mimeType] ?? "bin";

    // For single file: key is "{driver_id}/{upload_id}.{ext}"
    // For multiple files: keys are "{driver_id}/{upload_id}_0.{ext}", "_1.{ext}", etc.
    // fileKey stored in DB is the single-file key or the base pattern for multi-file
    const fileKeys: string[] = [];
    try {
      if (fileBuffers.length === 1) {
        const key = `${driverId}/${uploadId}.${ext}`;
        fileKeys.push(key);
        await uploadFile(key, fileBuffers[0], mimeType);
      } else {
        const uploadPromises = fileBuffers.map((buf, i) => {
          const key = `${driverId}/${uploadId}_${i}.${ext}`;
          fileKeys.push(key);
          return uploadFile(key, buf, mimeType);
        });
        await Promise.all(uploadPromises);
      }
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Error desconocido";
      console.error("R2 upload error:", message);
      return NextResponse.json(
        { ok: false, error: "Error al subir el archivo. Intentalo de nuevo." },
        { status: 500 },
      );
    }

    // Store all R2 keys so the DB record can locate every uploaded file
    const fileKey = fileKeys.join(",");

    // 7. Create upload record (status: processing)
    const upload = await createUpload({
      driverId,
      platform,
      uploadType,
      fileKey,
      status: "processing",
    });

    // 8. Call parser
    const parseResult = await parseUpload(
      platform as Platform,
      uploadType as UploadType,
      { files: fileBuffers, mimeType },
    );

    // 9. Handle parser result
    console.log(`[upload] platform=${platform} type=${uploadType} success=${parseResult.success} completeness=${parseResult.data_completeness}`, JSON.stringify(parseResult.metrics));

    if (!parseResult.success || !parseResult.metrics) {
      // Parser failed — update status and return error
      const errorMsg =
        parseResult.error ??
        "No pudimos leer tus datos. Asegurate que el screenshot sea claro y completo.";
      await updateUploadStatus(upload.id, "failed", errorMsg);

      return NextResponse.json(
        { ok: false, error: errorMsg },
        { status: 422 },
      );
    }

    // 10. Parser succeeded — upsert weekly data
    const metrics = parseResult.metrics;

    const weeklyDataRecord = await upsertWeeklyData({
      driverId,
      platform,
      weekStart: metrics.week_start,
      netEarnings: toDecimalString(metrics.net_earnings) ?? "0",
      grossEarnings: toDecimalString(metrics.gross_earnings) ?? "0",
      totalTrips: metrics.total_trips ?? 0,
      earningsPerTrip: toDecimalString(metrics.earnings_per_trip),
      earningsPerKm: toDecimalString(metrics.earnings_per_km),
      earningsPerHour: toDecimalString(metrics.earnings_per_hour),
      tripsPerHour: toDecimalString(metrics.trips_per_hour),
      platformCommissionPct: toDecimalString(metrics.platform_commission_pct),
      totalKm: toDecimalString(metrics.total_km),
      hoursOnline: toDecimalString(metrics.hours_online),
      platformCommission: toDecimalString(metrics.platform_commission),
      taxes: toDecimalString(metrics.taxes),
      incentives: toDecimalString(metrics.incentives),
      tips: toDecimalString(metrics.tips),
      surgeEarnings: toDecimalString(metrics.surge_earnings),
      waitTimeEarnings: toDecimalString(metrics.wait_time_earnings),
      activeDays: metrics.active_days,
      peakDayEarnings: toDecimalString(metrics.peak_day_earnings),
      peakDayName: metrics.peak_day_name,
      cashAmount: toDecimalString(metrics.cash_amount),
      cardAmount: toDecimalString(metrics.card_amount),
      rewards: toDecimalString(metrics.rewards),
      dataCompleteness: parseResult.data_completeness.toFixed(2),
      rawExtraction: parseResult.raw_extraction,
      uploadId: upload.id,
    });

    // 11. Update upload status, link to weekly data, and update driver timestamp in parallel
    await Promise.all([
      updateUploadStatus(upload.id, "parsed", undefined, parseResult.raw_extraction),
      linkUploadToWeeklyData(upload.id, weeklyDataRecord.id),
      updateLastUploadAt(driverId),
    ]);

    // 12. Return success response
    return NextResponse.json({
      ok: true,
      upload_id: upload.id,
      weekly_data_id: weeklyDataRecord.id,
      metrics,
      data_completeness: parseResult.data_completeness,
    });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Error desconocido";
    console.error("Upload API error:", message);

    // Note: upload record may not exist if the error happened before createUpload.
    // In that case we can't mark anything as failed — the R2 file becomes orphaned
    // and will be cleaned up by the 90-day lifecycle policy.

    return NextResponse.json(
      { ok: false, error: "Error interno del servidor. Intentalo de nuevo." },
      { status: 500 },
    );
  }
}

// ─── GET /api/uploads ───────────────────────────────────────

export async function GET(request: NextRequest) {
  // 1. Auth check
  const auth = await requireAuth();
  if (auth instanceof NextResponse) return auth;
  const { driverId } = auth;

  try {
    // Parse pagination query params
    const { searchParams } = new URL(request.url);
    const limit = Math.min(
      Math.max(parseInt(searchParams.get("limit") ?? "20", 10) || 20, 1),
      100,
    );
    const offset = Math.max(
      parseInt(searchParams.get("offset") ?? "0", 10) || 0,
      0,
    );

    const uploads = await getUploadsByDriver(driverId, limit, offset);

    return NextResponse.json({
      ok: true,
      uploads: uploads.map((u) => ({
        id: u.id,
        platform: u.platform,
        upload_type: u.uploadType,
        status: u.status,
        error_message: u.errorMessage,
        weekly_data_id: u.weeklyDataId,
        created_at: u.createdAt,
      })),
    });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Error desconocido";
    console.error("Upload list error:", message);
    return NextResponse.json(
      { ok: false, error: "Error al obtener las subidas. Intentalo de nuevo." },
      { status: 500 },
    );
  }
}
