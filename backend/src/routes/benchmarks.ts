import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { and, eq } from "drizzle-orm";
import { populationStats } from "../db/schema.js";
import type { Database } from "../db/client.js";

const benchmarkQuery = z.object({
  city: z.string().min(1).max(100),
  platform: z.string().min(1).max(20),
  period: z.string().min(1).max(20).default("current"),
});

/**
 * Build the benchmarks router. Returns the population_stats breakpoints for a
 * city × platform (× period), used by clients to render percentile context.
 */
export function benchmarksRoutes(db: Database) {
  const app = new Hono();

  // GET /v1/benchmarks?city=&platform=&period=
  app.get("/benchmarks", zValidator("query", benchmarkQuery), async (c) => {
    const { city, platform, period } = c.req.valid("query");

    const rows = await db
      .select()
      .from(populationStats)
      .where(
        and(
          eq(populationStats.city, city),
          eq(populationStats.platform, platform),
          eq(populationStats.period, period),
        ),
      );

    return c.json({ city, platform, period, stats: rows });
  });

  return app;
}
