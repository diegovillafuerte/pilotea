import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { registerDevice } from "../auth/otp.js";
import type { Database } from "../db/client.js";

const registerInput = z.object({ deviceId: z.string().uuid() });

/**
 * Anonymous-first device registration. No account required — the reader and
 * local stats work with zero signup, and the device id later merges into an
 * account at OTP verify. Mounted under /v1 ⇒ POST /v1/devices/register.
 */
export function devicesRoutes(db: Database) {
  const app = new Hono();

  app.post("/devices/register", zValidator("json", registerInput), async (c) => {
    const { deviceId } = c.req.valid("json");
    await registerDevice(db, deviceId);
    return c.json({ ok: true }, 200);
  });

  return app;
}
