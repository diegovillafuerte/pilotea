/**
 * Runtime database client (postgres driver + Drizzle).
 *
 * Tests do NOT use this module — they spin up an in-memory pglite instance
 * (see src/test/db.ts) so DB tests run with zero infrastructure.
 */

import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema.js";

let dbSingleton: ReturnType<typeof drizzle<typeof schema>> | null = null;

/**
 * Lazily build the Drizzle client from DATABASE_URL. Lazy so importing this
 * module (e.g. for the route handlers) never throws when DATABASE_URL is
 * absent — it only fails if a handler actually touches the database.
 */
export function getDb() {
  if (dbSingleton) return dbSingleton;

  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error(
      "DATABASE_URL is not set. Provide a Postgres connection string via env.",
    );
  }

  const client = postgres(url, { max: 10 });
  dbSingleton = drizzle(client, { schema });
  return dbSingleton;
}

export type Database = ReturnType<typeof getDb>;
