/**
 * POST /v1/imports — authenticated Claude Vision import endpoint.
 *
 * Ported from the legacy web app's POST /api/uploads route. The validation
 * limits (≤10 MB, pdf/png/jpg/webp), the DiDi 2-image allowance, and every
 * Spanish error string are preserved exactly. The pipeline:
 *
 *   1. validate multipart fields (platform, upload_type, files[])
 *   2. store original(s) via the StorageAdapter (key `{driverId}/{importId}.{ext}`)
 *   3. route to the ported Claude Vision parser
 *   4. on success, upsert into weekly_aggregates (source='imported')
 *   5. record the attempt in the `imports` table (status parsed|failed)
 *
 * Conflict rule — field-level coalesce (import-strategy §6.1, PR-A): an import
 * does NOT blanket-skip a live-captured row anymore. The incoming parsed values
 * win for the fields they carry (net/gross/trips/commission); fields the import
 * lacks (e.g. Uber km/hours) keep their prior captured/imported value. Derived
 * ratios are recomputed from the merged raw fields, and a row that combines a
 * captured contribution with an imported one is marked `source='mixed'`. A
 * commission-only/partial import therefore never clobbers good totals to 0, and
 * a fresh import that carries no core earnings is refused (422) instead of
 * inserting a junk zero row. See `imports/aggregate-merge.ts`.
 *
 * Dry-run mode (`?dry_run=true`, B-045): validate + run the full Claude Vision
 * parse, but DO NOT mutate the durable record — and DO NOT store the original
 * (PR-A privacy fix / TD-018): the upload carries PII, so a preview must never
 * leave an orphaned file in object storage. The parse runs straight off the
 * in-memory multipart bytes. No `imports` row is inserted and no
 * weekly_aggregates upsert is performed. The response shape is identical to a
 * real import plus `dry_run: true`, so the Android review screen can preview the
 * parsed week before the driver confirms. A real import (`dry_run` absent/false)
 * returns `dry_run: false`, stores the original, and persists as before.
 */

import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { randomUUID } from "node:crypto";
import { imports } from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import type { Database } from "../db/client.js";
import type { VisionClient } from "../imports/claude.js";
import { AnthropicVisionClient } from "../imports/claude.js";
import type { StorageAdapter } from "../imports/storage.js";
import { storageFromEnv } from "../imports/storage.js";
import { parseUpload } from "../imports/parsers/index.js";
import type { Platform, UploadType, ParsedMetrics } from "../imports/types.js";
import type { AggregateColumns } from "../imports/aggregate-merge.js";
import { writeMergedAggregate } from "../imports/aggregate-write.js";

// ─── Constants (ported from web /api/uploads) ─────────────────
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

const PLATFORMS = new Set<Platform>(["uber", "didi", "indrive"]);
const UPLOAD_TYPES = new Set<UploadType>(["pdf", "screenshot"]);

// ─── Helpers ──────────────────────────────────────────────────
function toDecimalString(value: number | null): string | null {
  if (value === null) return null;
  return value.toFixed(2);
}

/**
 * Map ParsedMetrics → the mergeable weekly_aggregates column set, PRESERVING
 * nulls (PR-A): a metric the document didn't carry stays null so the merge can
 * tell "not reported" from a real 0 and never clobbers a prior value. Derived
 * ratios are recomputed downstream from the merged raw fields, so the values
 * passed here are advisory for a fresh insert only.
 */
function aggregateValuesFrom(metrics: ParsedMetrics): AggregateColumns {
  return {
    netEarnings: toDecimalString(metrics.net_earnings),
    grossEarnings: toDecimalString(metrics.gross_earnings),
    totalTrips: metrics.total_trips,
    totalKm: toDecimalString(metrics.total_km),
    hoursOnline: toDecimalString(metrics.hours_online),
    earningsPerTrip: toDecimalString(metrics.earnings_per_trip),
    earningsPerKm: toDecimalString(metrics.earnings_per_km),
    earningsPerHour: toDecimalString(metrics.earnings_per_hour),
    tripsPerHour: toDecimalString(metrics.trips_per_hour),
    platformCommissionPct: toDecimalString(metrics.platform_commission_pct),
    source: "imported",
  };
}

/**
 * Build the imports router. Takes the Drizzle DB plus an injectable Claude
 * Vision client and storage adapter so tests can run with a fixture-replaying
 * client and in-memory storage (no ANTHROPIC_API_KEY / R2 creds).
 */
export function importsRoutes(
  db: Database,
  vision: VisionClient = new AnthropicVisionClient(),
  storage: StorageAdapter = storageFromEnv(),
) {
  const app = new Hono();

  app.post("/imports", requireBearer(db), async (c) => {
    const driverId = c.get("driverId");

    // Dry-run preview (B-045): full parse, no durable record (no imports row,
    // no aggregate upsert). Any value other than the exact strings "true"/"1"
    // is treated as a real import, so a missing/garbage param is safe.
    const dryRunRaw = c.req.query("dry_run");
    const dryRun = dryRunRaw === "true" || dryRunRaw === "1";

    // 1. Parse multipart form
    let form: FormData;
    try {
      form = await c.req.formData();
    } catch {
      return c.json(
        { error: "No se pudo leer el formulario. Envia los datos como FormData." },
        400,
      );
    }

    const platformRaw = form.get("platform");
    const uploadTypeRaw = form.get("upload_type");
    const files = form.getAll("files");

    // 2. Validate platform / upload_type (Spanish messages preserved from web)
    if (typeof platformRaw !== "string" || !PLATFORMS.has(platformRaw as Platform)) {
      return c.json({ error: 'Plataforma invalida. Debe ser "uber", "didi" o "indrive".' }, 400);
    }
    if (typeof uploadTypeRaw !== "string" || !UPLOAD_TYPES.has(uploadTypeRaw as UploadType)) {
      return c.json({ error: 'Tipo de archivo invalido. Debe ser "pdf" o "screenshot".' }, 400);
    }
    const platform = platformRaw as Platform;
    const uploadType = uploadTypeRaw as UploadType;

    // 3. Validate files exist
    if (!files || files.length === 0) {
      return c.json(
        { error: "No se encontraron archivos. Selecciona al menos un archivo para subir." },
        400,
      );
    }

    // 4. Validate each file (MIME + size), collect buffers
    const fileBuffers: Buffer[] = [];
    let mimeType = "";

    for (const file of files) {
      if (!(file instanceof File)) {
        return c.json({ error: "Formato de archivo invalido." }, 400);
      }
      if (!ALLOWED_MIME_TYPES.has(file.type)) {
        return c.json(
          {
            error: `Tipo de archivo no permitido: ${file.type}. Solo se aceptan PNG, JPG, WebP y PDF.`,
          },
          400,
        );
      }
      if (file.size > MAX_FILE_SIZE) {
        return c.json({ error: `El archivo "${file.name}" excede el limite de 10 MB.` }, 413);
      }
      const arrayBuffer = await file.arrayBuffer();
      fileBuffers.push(Buffer.from(arrayBuffer));
      mimeType = file.type;
    }

    // 5. Store original(s) — REAL imports only. Key scheme: single →
    //    {driverId}/{importId}.{ext}; multi (DiDi) → {driverId}/{importId}_{i}.{ext}.
    //    Dry-run NEVER writes to storage (PR-A / TD-018): the upload carries PII,
    //    so a preview must not leave an orphaned file behind. The dry-run parse
    //    runs straight off the in-memory `fileBuffers` below.
    const importId = randomUUID();
    const ext = MIME_TO_EXT[mimeType] ?? "bin";
    const fileKeys: string[] = [];
    if (!dryRun) {
      try {
        if (fileBuffers.length === 1) {
          const key = `${driverId}/${importId}.${ext}`;
          fileKeys.push(key);
          await storage.uploadFile(key, fileBuffers[0]!, mimeType);
        } else {
          await Promise.all(
            fileBuffers.map((buf, i) => {
              const key = `${driverId}/${importId}_${i}.${ext}`;
              fileKeys.push(key);
              return storage.uploadFile(key, buf, mimeType);
            }),
          );
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : "Error desconocido";
        console.error("Storage upload error:", message);
        return c.json({ error: "Error al subir el archivo. Intentalo de nuevo." }, 500);
      }
    }
    const fileKey = fileKeys.join(",");

    // 6. Create the import record (status: pending). Reuse `importId` as the
    //    primary key so the row id and the storage key scheme agree (the web
    //    app used a single id for both the upload row and the R2 object key).
    //    Skipped in dry-run: a preview never leaves a durable record behind.
    let importRow: typeof imports.$inferSelect | undefined;
    if (!dryRun) {
      [importRow] = await db
        .insert(imports)
        .values({ id: importId, driverId, platform, uploadType, fileKey, status: "pending" })
        .returning();
      if (!importRow) {
        // INSERT … RETURNING always yields a row; defensive guard for the type.
        return c.json({ error: "Error interno del servidor. Intentalo de nuevo." }, 500);
      }
    }

    // 7. Parse via the ported Claude Vision pipeline
    const parseResult = await parseUpload(platform, uploadType, { files: fileBuffers, mimeType }, vision);

    // 8. Parser failure → mark failed (real import only), return 422 with Spanish error
    if (!parseResult.success || !parseResult.metrics) {
      const errorMsg =
        parseResult.error ??
        "No pudimos leer tus datos. Asegurate que el screenshot sea claro y completo.";
      if (!dryRun && importRow) {
        await db
          .update(imports)
          .set({ status: "failed", errorMessage: errorMsg, parsedPayload: parseResult.raw_extraction })
          .where(eq(imports.id, importRow.id));
      }
      return c.json({ error: errorMsg }, 422);
    }

    const metrics = parseResult.metrics;

    // Dry-run preview: parse succeeded — return the metrics for the review
    // screen WITHOUT persisting anything (no imports row was created in step 6,
    // and no aggregate upsert happens here). The response shape matches a real
    // import; `import_id` is null because no durable row exists yet.
    if (dryRun) {
      return c.json(
        {
          import_id: null,
          metrics,
          data_completeness: parseResult.data_completeness,
          dry_run: true,
        },
        200,
      );
    }

    // `importRow` is guaranteed defined here — dry-run returned above, and the
    // real path created it in step 6 (with its own guard).
    if (!importRow) {
      return c.json({ error: "Error interno del servidor. Intentalo de nuevo." }, 500);
    }

    // 9. Success → merge into weekly_aggregates (field-level coalesce, PR-A).
    //    The import wins for the fields it carries (net/gross/trips/commission);
    //    fields it lacks (e.g. Uber km/hours) keep their prior captured/imported
    //    value; derived ratios are recomputed from the merged raw fields; a row
    //    that combines a captured contribution with an imported one becomes
    //    source='mixed'. See imports/aggregate-merge.ts + aggregate-write.ts.
    const writeResult = await writeMergedAggregate(
      db,
      { driverId, platform, weekStart: metrics.week_start },
      aggregateValuesFrom(metrics),
    );

    // A fresh import carrying no core earnings (net/gross/trips all absent) is
    // not a real week — refuse it and mark the pending import failed rather than
    // inserting a junk zero row.
    if (writeResult.kind === "rejected") {
      const errorMsg =
        "No pudimos leer las cifras de tu semana. Asegurate de subir la pantalla con tus ganancias totales.";
      await db
        .update(imports)
        .set({ status: "failed", errorMessage: errorMsg, parsedPayload: parseResult.raw_extraction })
        .where(eq(imports.id, importRow.id));
      return c.json({ error: errorMsg }, 422);
    }

    const aggregateRow = writeResult.row;

    // 10. Mark the import parsed + link to the merged aggregate row.
    await db
      .update(imports)
      .set({
        status: "parsed",
        errorMessage: null,
        parsedPayload: parseResult.raw_extraction,
        weeklyAggregateId: aggregateRow.id,
      })
      .where(eq(imports.id, importRow.id));

    // 11. Return the ported response shape
    return c.json(
      {
        import_id: importRow.id,
        metrics,
        data_completeness: parseResult.data_completeness,
        dry_run: false,
      },
      200,
    );
  });

  return app;
}
