import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { health } from "./routes/health.js";
import { authRoutes } from "./routes/auth.js";
import { meRoutes } from "./routes/me.js";
import { devicesRoutes } from "./routes/devices.js";
import { aggregatesRoutes } from "./routes/aggregates.js";
import { benchmarksRoutes } from "./routes/benchmarks.js";
import { parserConfigsRoutes } from "./routes/parser-configs.js";
import { telemetryRoutes } from "./routes/telemetry.js";
import { fixtureReportsRoutes } from "./routes/fixture-reports.js";
import { importsRoutes } from "./routes/imports.js";
import { subscriptionsRoutes } from "./routes/subscriptions.js";
import { adminRoutes } from "./routes/admin.js";
import { fiscalConfigRoutes } from "./routes/fiscal-config.js";
import { senderFromEnv, type MessageSender } from "./auth/message-sender.js";
import type { Database } from "./db/client.js";

/**
 * Build the Hono app against a given Drizzle DB and WhatsApp message sender.
 * Keeping construction in a factory (rather than at module top-level) lets
 * tests inject an in-memory pglite database and a fake sender without any
 * global state, real DATABASE_URL, or Twilio credentials.
 *
 * The `sender` defaults to {@link senderFromEnv} (Twilio when configured, else
 * a dev logger), so production wiring stays a one-liner.
 */
export function createApp(db: Database, sender: MessageSender = senderFromEnv()) {
  const app = new Hono();

  app.route("/", health);
  app.route("/v1/auth", authRoutes(db, sender));
  app.route("/v1", meRoutes(db));
  app.route("/v1", devicesRoutes(db));
  app.route("/v1", aggregatesRoutes(db));
  app.route("/v1", benchmarksRoutes(db));
  app.route("/v1", parserConfigsRoutes(db));
  app.route("/v1", telemetryRoutes(db));
  app.route("/v1", fixtureReportsRoutes(db));
  app.route("/v1", importsRoutes(db));
  app.route("/v1", subscriptionsRoutes(db));
  app.route("/v1", adminRoutes(db));
  app.route("/v1", fiscalConfigRoutes(db));

  app.onError((err, c) => {
    if (err instanceof HTTPException) {
      return c.json({ error: err.message }, err.status);
    }
    console.error(err);
    return c.json({ error: "Internal server error" }, 500);
  });

  app.notFound((c) => c.json({ error: "Not found" }, 404));

  return app;
}
