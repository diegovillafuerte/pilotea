import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";

function getDatabaseUrl(): string {
  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error(
      "DATABASE_URL environment variable is not set. " +
        "Set it to your Postgres connection string, e.g. " +
        "postgres://user:pass@host:5432/pilotea",
    );
  }
  return url;
}

/**
 * postgres.js connection pool.
 *
 * Uses `max: 10` by default, which is suitable for a single Next.js
 * server process. Render Postgres Starter allows up to 97 connections —
 * leaving headroom for migrations, seeding, and ad-hoc queries.
 */
const client = postgres(getDatabaseUrl(), {
  max: 10,
  idle_timeout: 20,
  connect_timeout: 10,
});

/** Drizzle ORM instance — use this everywhere. */
export const db = drizzle(client, { schema });

export { client };
