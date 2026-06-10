import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { and, desc, eq } from "drizzle-orm";
import { parserConfigs } from "../db/schema.js";
import type { Database } from "../db/client.js";

const configQuery = z.object({
  package: z.string().min(1).max(255),
  // versionCode is reserved for future server-side range matching; accepted now
  // so clients can send it without breaking. Range resolution lands later.
  versionCode: z.coerce.number().int().nonnegative().optional(),
});

/**
 * Build the parser-configs router. Returns the active spec bundle(s) for a host
 * package. Version-range matching against `versionCode` is a follow-up; for now
 * we return all active specs for the package, newest spec_version first.
 */
export function parserConfigsRoutes(db: Database) {
  const app = new Hono();

  // GET /v1/parser-configs?package=&versionCode=
  app.get("/parser-configs", zValidator("query", configQuery), async (c) => {
    const { package: pkg } = c.req.valid("query");

    const rows = await db
      .select()
      .from(parserConfigs)
      .where(and(eq(parserConfigs.targetPackage, pkg), eq(parserConfigs.active, true)))
      .orderBy(desc(parserConfigs.specVersion));

    return c.json({ package: pkg, configs: rows });
  });

  return app;
}
