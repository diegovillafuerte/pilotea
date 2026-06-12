/**
 * Zero-infrastructure dev server: serves the real Hono app against an
 * in-memory PGlite Postgres (same helper the tests use), fully migrated and
 * seeded. For on-device testing sessions (docs/didi-test-plan.md P2) on a
 * machine with no local Postgres — data is EPHEMERAL and resets on restart.
 *
 * OTP codes print to this console via DevLogSender (no TWILIO_* env needed).
 *
 *   npx tsx scripts/dev-pglite.ts          # listens on :8080 (PORT to override)
 */

import { serve } from "@hono/node-server";
import { createApp } from "../src/app.js";
import { makeTestDb } from "../src/test/db.js";

const port = Number(process.env.PORT ?? 8080);

const db = await makeTestDb({ seed: true, seedSpecs: true, seedFiscal: true, seedApp: true });
// Same structural cast the smoke tests use: the pglite Drizzle client is
// compatible for the query surface the routes touch.
const app = createApp(db as unknown as Parameters<typeof createApp>[0]);

serve({ fetch: app.fetch, port }, (info) => {
  console.log(`kompara-backend (pglite, ephemeral) on http://localhost:${info.port}`);
});
