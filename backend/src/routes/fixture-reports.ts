import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { and, eq, gte, sql } from "drizzle-orm";
import { fixtureReports } from "../db/schema.js";
import { requireDevice } from "../middleware/device-auth.js";
import type { Database } from "../db/client.js";

/**
 * POST /v1/fixture-reports — device-authed, consented submission of a scrubbed
 * accessibility snapshot the on-device parser failed to read ("Reportar tarjeta
 * no leída"). Feeds the parser-spec corpus (B-028) so a host UI change can be
 * fixed by shipping a new spec rather than guessing.
 *
 * Privacy + abuse posture:
 *  - Device-authed (X-Device-Id of a registered device) — see requireDevice.
 *  - Explicit per-report consent is required (`consent: true`); without it the
 *    report is rejected. The Android side gates this behind a confirmation
 *    button (B-036/B-040) and runs SnapshotScrubber before sending.
 *  - The snapshot is the SCRUBBED ParserSnapshot shape (structural nodes with
 *    masked text). The backend trusts the on-device scrub for masking but
 *    validates the shape and caps total size at 50 KB to bound storage/abuse.
 *  - Rate-limited to 10 reports per device per rolling 24h.
 */

// Max serialized snapshot size (defense against abuse / runaway payloads).
const MAX_SNAPSHOT_BYTES = 50 * 1024; // 50 KB
const RATE_LIMIT_MAX = 10;
const RATE_LIMIT_WINDOW_MS = 24 * 60 * 60 * 1000;

// A scrubbed node: structural fields only. `text` is the already-masked string.
const scrubbedNode = z.object({
  text: z.string().nullable().optional(),
  viewId: z.string().nullable().optional(),
  className: z.string().nullable().optional(),
  bounds: z
    .object({
      left: z.number().int(),
      top: z.number().int(),
      right: z.number().int(),
      bottom: z.number().int(),
    })
    .optional(),
  depth: z.number().int().nonnegative().optional(),
  index: z.number().int().nonnegative().optional(),
});

const scrubbedSnapshot = z.object({
  packageName: z.string().min(1).max(255),
  timestampMs: z.number().int().nonnegative().optional(),
  versionCode: z.number().int().nonnegative().nullable().optional(),
  nodes: z.array(scrubbedNode).max(2000),
});

const fixtureReportInput = z.object({
  consent: z.literal(true, { message: "Explicit per-report consent is required" }),
  hostPackage: z.string().min(1).max(255),
  hostVersion: z.string().max(100).optional(),
  specVersion: z.number().int().nonnegative().optional(),
  reason: z.enum(["NO_SPEC", "NOT_AN_OFFER", "OTHER"]).optional(),
  snapshot: scrubbedSnapshot,
});

export function fixtureReportsRoutes(db: Database) {
  const app = new Hono();

  app.post(
    "/fixture-reports",
    requireDevice(db),
    zValidator("json", fixtureReportInput),
    async (c) => {
      const deviceId = c.get("deviceId")!;
      const body = c.req.valid("json");

      // 50 KB cap on the structural snapshot payload.
      const snapshotBytes = Buffer.byteLength(JSON.stringify(body.snapshot), "utf8");
      if (snapshotBytes > MAX_SNAPSHOT_BYTES) {
        return c.json({ error: "Snapshot exceeds 50KB limit" }, 413);
      }

      // Per-device rolling 24h rate limit (counted in SQL, no infra dependency).
      const windowStart = new Date(Date.now() - RATE_LIMIT_WINDOW_MS);
      const [{ count } = { count: 0 }] = await db
        .select({ count: sql<number>`count(*)::int` })
        .from(fixtureReports)
        .where(
          and(
            eq(fixtureReports.deviceId, deviceId),
            gte(fixtureReports.createdAt, windowStart),
          ),
        );
      if ((count ?? 0) >= RATE_LIMIT_MAX) {
        return c.json({ error: "Daily fixture-report limit reached" }, 429);
      }

      const [row] = await db
        .insert(fixtureReports)
        .values({
          deviceId,
          hostPackage: body.hostPackage,
          hostVersion: body.hostVersion ?? null,
          specVersion: body.specVersion ?? null,
          reason: body.reason ?? null,
          snapshot: body.snapshot,
        })
        .returning({ id: fixtureReports.id, createdAt: fixtureReports.createdAt });

      return c.json({ report: row }, 201);
    },
  );

  return app;
}
