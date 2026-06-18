/**
 * Import endpoint tests — run end-to-end against an in-memory pglite DB and a
 * fixture-replaying Claude Vision client (no ANTHROPIC_API_KEY) plus in-memory
 * storage (no R2 creds).
 *
 * Fixtures are the exact extraction JSON shapes lifted from the legacy web
 * parser tests, so this doubles as the parity check the task's acceptance
 * criteria call for: the ported endpoint maps fields and computes derived
 * metrics / completeness identically to the web MVP.
 *
 * Covers: uber-pdf happy path with exact field mapping, didi 2-image path,
 * oversize + MIME rejections with Spanish messages, parse-failure → 422 with
 * imports.status='failed', the captured-beats-imported upsert rule, and
 * idempotent re-import of the same week.
 */

import { describe, it, expect, beforeEach, beforeAll } from "vitest";
import { Hono } from "hono";
import { eq } from "drizzle-orm";
import sharp from "sharp";
import { drivers, imports, weeklyAggregates } from "../db/schema.js";
import { createSession } from "../auth/sessions.js";
import { importsRoutes } from "./imports.js";
import { MemoryStorage } from "../imports/storage.js";
import type { VisionClient, VisionRequest, VisionResponse } from "../imports/claude.js";
import { makeTestDb, type TestDb } from "../test/db.js";

// ─── Fixtures (lifted from the web parser tests) ──────────────
const UBER_PDF_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 3850.5,
  gross_earnings: 5200.0,
  total_trips: 72,
  hours_online: 45.5,
  platform_commission: 1050.0,
  platform_commission_pct: 20.19,
  taxes: 299.5,
  incentives: 450.0,
  tips: 380.0,
  surge_earnings: 620.0,
  wait_time_earnings: 185.0,
  active_days: 6,
  peak_day_earnings: 890.0,
  peak_day_name: "Sabado",
  cash_amount: 1200.0,
  card_amount: 2650.5,
  rewards: 150.0,
};

const DIDI_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 4200.5,
  gross_earnings: 5100.0,
  total_trips: 85,
  earnings_per_km: 8.75,
  earnings_per_trip: 49.42,
  earnings_per_hour: 175.0,
  cash_amount: 2600.0,
  card_amount: 1600.5,
  taxes: 380.0,
  rewards: 150.0,
};

// ─── Fixture-replaying Claude Vision client ───────────────────
class FakeVision implements VisionClient {
  /** Raw text the next call returns. Set per-test. */
  response = "";
  /** If set, the next call rejects with this error. */
  throwError: Error | null = null;
  readonly calls: VisionRequest[] = [];

  setJson(value: unknown): void {
    this.response = JSON.stringify(value);
    this.throwError = null;
  }

  async callClaudeVision(request: VisionRequest): Promise<VisionResponse> {
    this.calls.push(request);
    if (this.throwError) throw this.throwError;
    return { text: this.response, usage: { input_tokens: 100, output_tokens: 50 } };
  }
}

let db: TestDb;
let vision: FakeVision;
let storage: MemoryStorage;
let app: Hono;
let token: string;
let driverId: string;

// A real, tiny PNG so the sharp normalization path in the screenshot parsers
// runs for real (the image flow converts to JPEG via sharp before sending to
// Claude). Built once; uber-pdf bytes can stay arbitrary since PDFs pass
// through without sharp.
let pngBytes: Uint8Array;

beforeAll(async () => {
  const buf = await sharp({
    create: { width: 8, height: 8, channels: 3, background: { r: 10, g: 20, b: 30 } },
  })
    .png()
    .toBuffer();
  pngBytes = new Uint8Array(buf);
});

function asDb(d: TestDb) {
  return d as unknown as Parameters<typeof importsRoutes>[0];
}

/** Build a multipart request to POST /v1/imports. */
function postImport(opts: {
  platform?: string;
  uploadType?: string;
  files?: { name: string; type: string; bytes?: Uint8Array }[];
  auth?: string;
  dryRun?: boolean;
}) {
  const form = new FormData();
  if (opts.platform !== undefined) form.set("platform", opts.platform);
  if (opts.uploadType !== undefined) form.set("upload_type", opts.uploadType);
  for (const f of opts.files ?? []) {
    const bytes = f.bytes ?? new Uint8Array([1, 2, 3, 4]);
    // Pass a fresh ArrayBuffer-backed view; sidesteps the Uint8Array<ArrayBufferLike>
    // vs Uint8Array<ArrayBuffer> typing wart in the File/Blob signature.
    const ab = new ArrayBuffer(bytes.byteLength);
    new Uint8Array(ab).set(bytes);
    form.append("files", new File([ab], f.name, { type: f.type }));
  }
  const url = opts.dryRun ? "/v1/imports?dry_run=true" : "/v1/imports";
  return app.request(url, {
    method: "POST",
    headers: opts.auth === undefined ? {} : { authorization: `Bearer ${opts.auth}` },
    body: form,
  });
}

/** Read the JSON error string off a response. */
async function errorOf(res: Response): Promise<string> {
  const body = (await res.json()) as { error: string };
  return body.error;
}

beforeEach(async () => {
  db = await makeTestDb();
  vision = new FakeVision();
  storage = new MemoryStorage();

  app = new Hono();
  app.route("/v1", importsRoutes(asDb(db), vision, storage));

  // Seed a driver + session so requests are authenticated.
  const [driver] = await db
    .insert(drivers)
    .values({ phone: "+5215511112222", city: "CDMX" })
    .returning();
  driverId = driver!.id;
  token = await createSession(asDb(db), driverId);
});

// ─── Auth ─────────────────────────────────────────────────────
describe("auth", () => {
  it("401s without a bearer token", async () => {
    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
    });
    expect(res.status).toBe(401);
  });
});

// ─── Uber PDF happy path (parity) ─────────────────────────────
describe("uber-pdf import", () => {
  it("parses, maps fields exactly, and upserts an imported aggregate", async () => {
    vision.setJson(UBER_PDF_EXTRACTION);

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      import_id: string;
      metrics: Record<string, unknown>;
      data_completeness: number;
    };

    const m = body.metrics;
    // Direct fields
    expect(m.week_start).toBe("2025-03-24");
    expect(m.net_earnings).toBe(3850.5);
    expect(m.gross_earnings).toBe(5200.0);
    expect(m.total_trips).toBe(72);
    expect(m.hours_online).toBe(45.5);
    expect(m.platform_commission_pct).toBe(20.19);
    expect(m.peak_day_name).toBe("Sabado");
    // Derived (matches web parser maths)
    expect(m.earnings_per_trip).toBe(53.48); // 3850.5 / 72
    expect(m.earnings_per_hour).toBe(84.63); // 3850.5 / 45.5
    expect(m.trips_per_hour).toBe(1.58); // 72 / 45.5
    // Uber never reports km
    expect(m.earnings_per_km).toBeNull();
    expect(m.total_km).toBeNull();
    // High completeness (Uber PDF ~0.95 ceiling per web fixture)
    expect(body.data_completeness).toBeGreaterThanOrEqual(0.85);

    // PDF was sent to Claude as a base64 document
    expect(vision.calls).toHaveLength(1);
    expect(vision.calls[0]!.inputs[0]!.mediaType).toBe("application/pdf");

    // imports row recorded as parsed and linked
    const [imp] = await db.select().from(imports).where(eq(imports.id, body.import_id));
    expect(imp!.status).toBe("parsed");
    expect(imp!.weeklyAggregateId).not.toBeNull();
    expect(imp!.fileKey).toBe(`${driverId}/${body.import_id}.pdf`);

    // weekly_aggregates row written with source='imported'
    const aggs = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(1);
    expect(aggs[0]!.source).toBe("imported");
    expect(aggs[0]!.platform).toBe("uber");
    expect(aggs[0]!.weekStart).toBe("2025-03-24");
    expect(Number(aggs[0]!.netEarnings)).toBe(3850.5);

    // original stored
    expect(storage.keys()).toContain(`${driverId}/${body.import_id}.pdf`);
  });
});

// ─── DiDi 2-image path ────────────────────────────────────────
describe("didi import (2 images)", () => {
  it("accepts two images, sends both to Claude, derives hours/trips-per-hour", async () => {
    vision.setJson(DIDI_EXTRACTION);

    const res = await postImport({
      platform: "didi",
      uploadType: "screenshot",
      files: [
        { name: "earnings.png", type: "image/png", bytes: pngBytes },
        { name: "tablero.png", type: "image/png", bytes: pngBytes },
      ],
      auth: token,
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as { import_id: string; metrics: Record<string, unknown> };

    expect(vision.calls).toHaveLength(1);
    expect(vision.calls[0]!.inputs).toHaveLength(2);

    const m = body.metrics;
    expect(m.earnings_per_km).toBe(8.75);
    expect(m.hours_online).toBe(24.0); // 4200.5 / 175.0
    expect(m.trips_per_hour).toBe(3.54); // 85 / 24.0
    expect(m.platform_commission).toBeNull(); // DiDi never reports it

    // The merged aggregate persists DiDi's NATIVE $/km: total km is back-derived
    // from net / (net-per-km), so the merge's ratio recompute reproduces 8.75
    // instead of dropping it to null (DiDi reports $/km but not total km).
    const aggs = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(1);
    expect(Number(aggs[0]!.earningsPerKm)).toBe(8.75);
    expect(Number(aggs[0]!.totalKm)).toBe(480.06); // 4200.50 / 8.75
    expect(aggs[0]!.source).toBe("imported");

    // both originals stored under the _0/_1 key scheme
    expect(storage.keys()).toContain(`${driverId}/${body.import_id}_0.png`);
    expect(storage.keys()).toContain(`${driverId}/${body.import_id}_1.png`);
  });

  it("422s when only one DiDi image is provided (Spanish message preserved)", async () => {
    vision.setJson(DIDI_EXTRACTION);

    const res = await postImport({
      platform: "didi",
      uploadType: "screenshot",
      files: [{ name: "earnings.png", type: "image/png" }],
      auth: token,
    });

    expect(res.status).toBe(422);
    const body = (await res.json()) as { error: string };
    expect(body.error).toContain("DiDi requiere 2 capturas de pantalla");

    // import recorded as failed
    const rows = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.status).toBe("failed");
  });
});

// ─── Validation rejections (Spanish messages preserved) ───────
describe("validation", () => {
  it("rejects an invalid platform with the exact Spanish message", async () => {
    const res = await postImport({
      platform: "lyft",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });
    expect(res.status).toBe(400);
    expect(await errorOf(res)).toBe(
      'Plataforma invalida. Debe ser "uber", "didi" o "indrive".',
    );
  });

  it("rejects an invalid upload_type with the exact Spanish message", async () => {
    const res = await postImport({
      platform: "uber",
      uploadType: "video",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });
    expect(res.status).toBe(400);
    expect(await errorOf(res)).toBe('Tipo de archivo invalido. Debe ser "pdf" o "screenshot".');
  });

  it("rejects a disallowed MIME type with the exact Spanish message", async () => {
    const res = await postImport({
      platform: "uber",
      uploadType: "screenshot",
      files: [{ name: "evil.gif", type: "image/gif" }],
      auth: token,
    });
    expect(res.status).toBe(400);
    expect(await errorOf(res)).toBe(
      "Tipo de archivo no permitido: image/gif. Solo se aceptan PNG, JPG, WebP y PDF.",
    );
  });

  it("rejects an oversize file (>10MB) with a 413 and Spanish message", async () => {
    const big = new Uint8Array(10 * 1024 * 1024 + 1); // 10MB + 1 byte
    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "huge.pdf", type: "application/pdf", bytes: big }],
      auth: token,
    });
    expect(res.status).toBe(413);
    expect(await errorOf(res)).toBe('El archivo "huge.pdf" excede el limite de 10 MB.');
  });

  it("rejects a request with no files", async () => {
    const res = await postImport({ platform: "uber", uploadType: "pdf", files: [], auth: token });
    expect(res.status).toBe(400);
    expect(await errorOf(res)).toBe(
      "No se encontraron archivos. Selecciona al menos un archivo para subir.",
    );
  });
});

// ─── Parse failure → 422 + imports.status='failed' ────────────
describe("parse failure", () => {
  it("returns 422 with the ported Spanish format error and marks the import failed", async () => {
    // Malformed extraction → zod validation failure inside the parser.
    vision.setJson({ week_start: 12345, net_earnings: "not a number" });

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });

    expect(res.status).toBe(422);
    const body = (await res.json()) as { error: string };
    expect(body.error).toContain("formato esperado");

    const rows = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(rows).toHaveLength(1);
    expect(rows[0]!.status).toBe("failed");
    expect(rows[0]!.errorMessage).toContain("formato esperado");
    // no aggregate written on failure
    const aggs = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(0);
  });

  it("returns 422 when the Claude call throws", async () => {
    vision.throwError = new Error("API rate limit exceeded");

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });

    expect(res.status).toBe(422);
    expect(await errorOf(res)).toContain("Error al procesar el PDF de Uber");
  });
});

// ─── Field-level coalesce merge (PR-A) ────────────────────────
describe("field-level coalesce merge (import onto captured)", () => {
  it("merges import onto a captured row: import fields win, captured-only fields preserved, source='mixed'", async () => {
    // Seed a live-captured aggregate for the same driver/platform/week, WITH a
    // km value the Uber PDF will not carry (Uber never reports km).
    await db.insert(weeklyAggregates).values({
      driverId,
      platform: "uber",
      weekStart: "2025-03-24",
      netEarnings: "9999.00",
      grossEarnings: "12000.00",
      totalTrips: 200,
      totalKm: "1500.00",
      source: "captured",
    });

    vision.setJson(UBER_PDF_EXTRACTION); // net=3850.50, trips=72, hours=45.5, commission=20.19, NO km

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as { import_id: string };

    const aggs = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(1);
    const row = aggs[0]!;
    // A captured + imported combination is honestly neither — it's 'mixed'.
    expect(row.source).toBe("mixed");
    // The import wins for the fields it carries.
    expect(Number(row.netEarnings)).toBe(3850.5);
    expect(Number(row.grossEarnings)).toBe(5200.0);
    expect(row.totalTrips).toBe(72);
    expect(Number(row.platformCommissionPct)).toBe(20.19);
    expect(Number(row.hoursOnline)).toBe(45.5);
    // The captured km is PRESERVED — the Uber import lacked it, so the merge must
    // not clobber it (the whole point of field-level coalesce).
    expect(Number(row.totalKm)).toBe(1500.0);
    // Derived ratios are recomputed from the MERGED raw fields (never stale).
    expect(Number(row.earningsPerTrip)).toBe(53.48); // 3850.5 / 72
    expect(Number(row.earningsPerHour)).toBe(84.63); // 3850.5 / 45.5
    expect(Number(row.earningsPerKm)).toBe(2.57); // 3850.5 / 1500 (preserved captured km)

    // The import succeeds and links to the merged row.
    const [imp] = await db.select().from(imports).where(eq(imports.id, body.import_id));
    expect(imp!.status).toBe("parsed");
    expect(imp!.weeklyAggregateId).toBe(row.id);
  });
});

// ─── Refuse a junk fresh import ───────────────────────────────
describe("fresh import with no core earnings", () => {
  it("refuses it (422, import marked failed, no aggregate row)", async () => {
    // A parse that succeeds but carries no net/gross/trips (all the core
    // earnings null) must not create a junk zero row.
    vision.setJson({
      week_start: "2025-03-24",
      net_earnings: null,
      gross_earnings: null,
      total_trips: null,
      hours_online: null,
      platform_commission: null,
      platform_commission_pct: 15.0,
      taxes: null,
      incentives: null,
      tips: null,
      surge_earnings: null,
      wait_time_earnings: null,
      active_days: null,
      peak_day_earnings: null,
      peak_day_name: null,
      cash_amount: null,
      card_amount: null,
      rewards: null,
    });

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });

    expect(res.status).toBe(422);
    expect(await errorOf(res)).toContain("cifras de tu semana");

    // The pending import row is marked failed; no aggregate is created.
    const imps = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(imps).toHaveLength(1);
    expect(imps[0]!.status).toBe("failed");
    const aggs = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(0);
  });
});

// ─── Dry-run preview (B-045) ──────────────────────────────────
describe("dry_run preview", () => {
  it("returns parsed metrics + dry_run:true without persisting anything", async () => {
    vision.setJson(UBER_PDF_EXTRACTION);

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
      dryRun: true,
    });

    expect(res.status).toBe(200);
    const body = (await res.json()) as {
      import_id: string | null;
      metrics: Record<string, unknown>;
      data_completeness: number;
      dry_run: boolean;
    };

    // Same metrics shape as a real import.
    expect(body.dry_run).toBe(true);
    expect(body.import_id).toBeNull();
    expect(body.metrics.week_start).toBe("2025-03-24");
    expect(body.metrics.net_earnings).toBe(3850.5);
    expect(body.metrics.earnings_per_trip).toBe(53.48); // derived identically
    expect(body.data_completeness).toBeGreaterThanOrEqual(0.85);

    // The parser still ran (Claude was called once).
    expect(vision.calls).toHaveLength(1);

    // NOTHING durable was written: no imports row, no aggregate.
    const imps = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(imps).toHaveLength(0);
    const aggs = await db
      .select()
      .from(weeklyAggregates)
      .where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(0);

    // PR-A privacy fix (TD-018): a dry-run preview must NOT store the original
    // file — the upload carries PII, so a preview leaves nothing in storage.
    expect(storage.keys()).toHaveLength(0);
  });

  it("returns 422 with the Spanish error on a parse failure and still persists nothing", async () => {
    vision.setJson({ week_start: 12345, net_earnings: "not a number" });

    const res = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
      dryRun: true,
    });

    expect(res.status).toBe(422);
    expect(await errorOf(res)).toContain("formato esperado");

    // No failed import row recorded in dry-run.
    const imps = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(imps).toHaveLength(0);
  });

  it("a real import after a dry-run persists exactly one durable record", async () => {
    vision.setJson(UBER_PDF_EXTRACTION);

    // Preview first…
    const preview = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
      dryRun: true,
    });
    expect(preview.status).toBe(200);

    // …then confirm (real import).
    const real = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });
    expect(real.status).toBe(200);
    const body = (await real.json()) as { import_id: string; dry_run: boolean };
    expect(body.dry_run).toBe(false);
    expect(body.import_id).not.toBeNull();

    // The dry-run left nothing behind, so there is exactly ONE import row and
    // ONE aggregate after the confirm.
    const imps = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(imps).toHaveLength(1);
    const aggs = await db
      .select()
      .from(weeklyAggregates)
      .where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(1);
    expect(aggs[0]!.source).toBe("imported");
  });
});

// ─── Idempotent re-import of the same week ────────────────────
describe("idempotent re-import", () => {
  it("re-importing the same week overwrites the prior imported row (one row total)", async () => {
    vision.setJson(UBER_PDF_EXTRACTION);
    const first = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });
    expect(first.status).toBe(200);

    // Re-import the same week with updated numbers.
    vision.setJson({ ...UBER_PDF_EXTRACTION, net_earnings: 4000.0, total_trips: 80 });
    const second = await postImport({
      platform: "uber",
      uploadType: "pdf",
      files: [{ name: "report.pdf", type: "application/pdf" }],
      auth: token,
    });
    expect(second.status).toBe(200);

    // Still exactly one aggregate row for the week; values reflect the re-import.
    const aggs = await db.select().from(weeklyAggregates).where(eq(weeklyAggregates.driverId, driverId));
    expect(aggs).toHaveLength(1);
    expect(Number(aggs[0]!.netEarnings)).toBe(4000.0);
    expect(aggs[0]!.totalTrips).toBe(80);
    expect(aggs[0]!.source).toBe("imported");

    // But two import attempts are recorded.
    const allImports = await db.select().from(imports).where(eq(imports.driverId, driverId));
    expect(allImports).toHaveLength(2);
    expect(allImports.every((r) => r.status === "parsed")).toBe(true);
  });
});
