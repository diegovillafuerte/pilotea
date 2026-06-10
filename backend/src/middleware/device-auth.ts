import { createMiddleware } from "hono/factory";
import { HTTPException } from "hono/http-exception";
import { eq } from "drizzle-orm";
import { devices } from "../db/schema.js";
import type { Database } from "../db/client.js";

/**
 * Anonymous device authentication.
 *
 * The reader works with zero signup: an install registers a device UUID
 * (POST /v1/devices/register) and authenticates subsequent anonymous-data
 * endpoints by presenting that UUID in `X-Device-Id`. This is intentionally
 * lighter than the bearer-session auth used for account endpoints — the data
 * behind it (privacy-safe counters, scrubbed fixture reports) carries no
 * personal information, so a registered-device check is the right bar.
 *
 * The header must be a UUID that exists in the `devices` table. On success the
 * device id is attached to the context for downstream handlers; otherwise a 401
 * is thrown.
 */
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function requireDevice(db: Database) {
  return createMiddleware<{ Variables: { deviceId: string } }>(async (c, next) => {
    const header = c.req.header("x-device-id")?.trim();
    if (!header) {
      throw new HTTPException(401, { message: "Missing X-Device-Id header" });
    }
    if (!UUID_RE.test(header)) {
      throw new HTTPException(401, { message: "Malformed device id" });
    }

    const [row] = await db
      .select({ deviceId: devices.deviceId })
      .from(devices)
      .where(eq(devices.deviceId, header))
      .limit(1);

    if (!row) {
      throw new HTTPException(401, { message: "Unregistered device" });
    }

    c.set("deviceId", row.deviceId);
    await next();
  });
}

declare module "hono" {
  interface ContextVariableMap {
    deviceId?: string;
  }
}
