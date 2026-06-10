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
 * Conflict rule — "captured beats imported": a weekly_aggregates row whose
 * `source='captured'` (live Android capture) is NEVER overwritten by an import.
 * The upsert's ON CONFLICT … WHERE clause skips the update for captured rows;
 * the import still succeeds and links to the existing row, but its parsed values
 * are not written. Imported-over-imported re-imports DO overwrite (idempotent
 * re-import of the same week).
 *
 * Dry-run mode (`?dry_run=true`, B-045): validate + store the original + run the
 * full Claude Vision parse, but DO NOT mutate the durable record — no `imports`
 * row is inserted and no weekly_aggregates upsert is performed. The original IS
 * still stored (the storage write is cheap, idempotent on the import id, and a
 * later real import reuses a fresh id anyway), but nothing is recorded against
 * it. The response shape is identical to a real import plus `dry_run: true`, so
 * the Android review screen can preview the parsed week before the driver
 * confirms. A real import (`dry_run` absent/false) returns `dry_run: false` and
 * persists as before. This lets the client show parsed metrics for confirmation
 * without leaving orphaned import rows for every preview.
 */

import { Hono } from "hono";
import { and, eq, ne, sql } from "drizzle-orm";
import { randomUUID } from "node:crypto";
import { imports, weeklyAggregates } from "../db/schema.js";
import { requireBearer } from "../middleware/auth.js";
import type { Database } from "../db/client.js";
import type { VisionClient } from "../imports/claude.js";
import { AnthropicVisionClient } from "../imports/claude.js";
import type { StorageAdapter } from "../imports/storage.js";
import { storageFromEnv } from "../imports/storage.js";
import { parseUpload } from "../imports/parsers/index.js";
import type { Platform, UploadType, ParsedMetrics } from "../imports/types.js";

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
 * Map ParsedMetrics → the weekly_aggregates column set. Earnings default to
 * "0" when null (ported from the web upsert) so the NOT NULL columns are
 * satisfied; the rest stay null.
 */
function aggregateValuesFrom(driverId: string, platform: string, metrics: ParsedMetrics) {
  return {
    driverId,
    platform,
    weekStart: metrics.week_start,
    netEarnings: toDecimalString(metrics.net_earnings) ?? "0",
    grossEarnings: toDecimalString(metrics.gross_earnings) ?? "0",
    totalTrips: metrics.total_trips ?? 0,
    totalKm: toDecimalString(metrics.total_km),
    hoursOnline: toDecimalString(metrics.hours_online),
    earningsPerTrip: toDecimalString(metrics.earnings_per_trip),
    earningsPerKm: toDecimalString(metrics.earnings_per_km),
    earningsPerHour: toDecimalString(metrics.earnings_per_hour),
    tripsPerHour: toDecimalString(metrics.trips_per_hour),
    platformCommissionPct: toDecimalString(metrics.platform_commission_pct),
    source: "imported" as const,
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

    // 5. Store original(s). Key scheme: single → {driverId}/{importId}.{ext};
    //    multi (DiDi) → {driverId}/{importId}_{i}.{ext}.
    const importId = randomUUID();
    const ext = MIME_TO_EXT[mimeType] ?? "bin";
    const fileKeys: string[] = [];
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

    // 9. Success → upsert weekly_aggregates with captured-beats-imported rule.
    const values = aggregateValuesFrom(driverId, platform, metrics);

    const [upserted] = await db
      .insert(weeklyAggregates)
      .values(values)
      .onConflictDoUpdate({
        target: [weeklyAggregates.driverId, weeklyAggregates.platform, weeklyAggregates.weekStart],
        // Captured beats imported: only overwrite when the existing row is NOT
        // a live capture. Imported-over-imported updates (idempotent re-import).
        set: {
          netEarnings: values.netEarnings,
          grossEarnings: values.grossEarnings,
          totalTrips: values.totalTrips,
          totalKm: values.totalKm,
          hoursOnline: values.hoursOnline,
          earningsPerTrip: values.earningsPerTrip,
          earningsPerKm: values.earningsPerKm,
          earningsPerHour: values.earningsPerHour,
          tripsPerHour: values.tripsPerHour,
          platformCommissionPct: values.platformCommissionPct,
          source: values.source,
          updatedAt: sql`now()`,
        },
        setWhere: ne(weeklyAggregates.source, "captured"),
      })
      .returning();

    // When the conflicting row is source='captured', the WHERE blocks the
    // update and RETURNING yields nothing — fetch the existing row so we can
    // still link the import and return its (captured) metrics.
    let aggregateRow = upserted;
    if (!aggregateRow) {
      [aggregateRow] = await db
        .select()
        .from(weeklyAggregates)
        .where(
          and(
            eq(weeklyAggregates.driverId, driverId),
            eq(weeklyAggregates.platform, platform),
            eq(weeklyAggregates.weekStart, metrics.week_start),
          ),
        )
        .limit(1);
    }

    // 10. Mark the import parsed + link to the aggregate row. `importRow` is
    //     guaranteed defined here — the dry-run path returned above, and the
    //     real path created it in step 6.
    if (!importRow) {
      return c.json({ error: "Error interno del servidor. Intentalo de nuevo." }, 500);
    }
    await db
      .update(imports)
      .set({
        status: "parsed",
        errorMessage: null,
        parsedPayload: parseResult.raw_extraction,
        weeklyAggregateId: aggregateRow?.id ?? null,
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
