import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import { requestOtp, verifyOtp } from "../auth/otp.js";
import { revokeSession } from "../auth/sessions.js";
import type { MessageSender } from "../auth/message-sender.js";
import type { Database } from "../db/client.js";

// E.164-ish: leading +, 8–15 digits. Kept permissive; deep validation/normalization
// can follow once the client locks its input mask.
const phoneSchema = z
  .string()
  .trim()
  .regex(/^\+\d{8,15}$/, "phone must be E.164, e.g. +5215512345678");

const otpRequestInput = z.object({ phone: phoneSchema });

const otpVerifyInput = z.object({
  phone: phoneSchema,
  code: z.string().regex(/^\d{6}$/, "code must be 6 digits"),
  deviceId: z.string().uuid().optional(),
});

/**
 * WhatsApp OTP auth router. Takes the DB and the message sender so tests can
 * inject a pglite database and a fake sender that captures the code.
 *
 * Endpoints:
 *  - POST /otp/request  — issue + send an OTP (always 200, no enumeration)
 *  - POST /otp/verify   — verify code, create/merge driver, mint session
 *  - POST /logout       — revoke the bearer session
 */
export function authRoutes(db: Database, sender: MessageSender) {
  const app = new Hono();

  app.post("/otp/request", zValidator("json", otpRequestInput), async (c) => {
    const { phone } = c.req.valid("json");
    // Fire-and-await; we deliberately do not surface rate-limit state to the
    // caller (constant response shape ⇒ no phone enumeration / probing).
    await requestOtp(db, sender, phone);
    return c.json({ ok: true }, 200);
  });

  app.post("/otp/verify", zValidator("json", otpVerifyInput), async (c) => {
    const { phone, code, deviceId } = c.req.valid("json");
    const result = await verifyOtp(db, phone, code, deviceId);
    if (!result.ok) {
      return c.json({ error: "Invalid or expired code" }, 401);
    }
    return c.json({ token: result.token, driver: result.driver }, 200);
  });

  app.post("/logout", async (c) => {
    const header = c.req.header("authorization");
    if (!header || !header.toLowerCase().startsWith("bearer ")) {
      return c.json({ error: "Missing or malformed bearer token" }, 401);
    }
    const token = header.slice(7).trim();
    if (token.length === 0) {
      return c.json({ error: "Empty bearer token" }, 401);
    }
    await revokeSession(db, token);
    return c.json({ ok: true }, 200);
  });

  return app;
}
