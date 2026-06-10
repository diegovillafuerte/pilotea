import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { health } from "./routes/health.js";
import { auth } from "./routes/auth.js";
import { aggregatesRoutes } from "./routes/aggregates.js";
import { benchmarksRoutes } from "./routes/benchmarks.js";
import { parserConfigsRoutes } from "./routes/parser-configs.js";
import { telemetryRoutes } from "./routes/telemetry.js";
import type { Database } from "./db/client.js";

/**
 * Build the Hono app against a given Drizzle DB. Keeping construction in a
 * factory (rather than at module top-level) lets tests inject an in-memory
 * pglite database without any global state or real DATABASE_URL.
 */
export function createApp(db: Database) {
  const app = new Hono();

  app.route("/", health);
  app.route("/v1/auth", auth);
  app.route("/v1", aggregatesRoutes(db));
  app.route("/v1", benchmarksRoutes(db));
  app.route("/v1", parserConfigsRoutes(db));
  app.route("/v1", telemetryRoutes(db));

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
