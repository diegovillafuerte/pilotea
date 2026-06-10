import { Hono } from "hono";

/**
 * Auth router placeholder.
 *
 * The real WhatsApp magic-link auth (token issue, verify, session creation)
 * lands in B-042. Until then this router exists so the app structure is stable
 * and downstream routes can reference the eventual mount point.
 */
export const auth = new Hono();

// TODO(B-042): POST /v1/auth/login   — issue WhatsApp magic link
// TODO(B-042): GET  /v1/auth/verify  — verify token, create session
// TODO(B-042): POST /v1/auth/logout  — revoke session
