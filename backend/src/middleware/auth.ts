import { createMiddleware } from "hono/factory";
import { HTTPException } from "hono/http-exception";
import { resolveSession } from "../auth/sessions.js";
import type { Database } from "../db/client.js";

/**
 * Real bearer-session auth.
 *
 * Reads the `Authorization: Bearer <token>` header, hashes the token, and
 * resolves it to a driver id against the `sessions` table (SHA-256 hash, not
 * expired). On success the driver id is attached to the context for downstream
 * handlers; otherwise a 401 is thrown.
 *
 * Built as a factory so it can be bound to the same Drizzle DB the routes use
 * (and a pglite instance in tests).
 */
export function requireBearer(db: Database) {
  return createMiddleware<{ Variables: { driverId: string } }>(async (c, next) => {
    const header = c.req.header("authorization");
    if (!header || !header.toLowerCase().startsWith("bearer ")) {
      throw new HTTPException(401, { message: "Missing or malformed bearer token" });
    }
    const token = header.slice(7).trim();
    if (token.length === 0) {
      throw new HTTPException(401, { message: "Empty bearer token" });
    }

    const driverId = await resolveSession(db, token);
    if (!driverId) {
      throw new HTTPException(401, { message: "Invalid or expired session" });
    }

    c.set("driverId", driverId);
    await next();
  });
}

declare module "hono" {
  interface ContextVariableMap {
    driverId: string;
  }
}
