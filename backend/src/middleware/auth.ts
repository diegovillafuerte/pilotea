import { createMiddleware } from "hono/factory";
import { HTTPException } from "hono/http-exception";

/**
 * Stub bearer-token auth.
 *
 * B-042 replaces this with real session lookup (SHA-256 token hash against the
 * `sessions` table). For now it only enforces the *presence* of a bearer token
 * so the protected routes have the right shape and callers can be wired up.
 */
export const requireBearer = createMiddleware(async (c, next) => {
  const header = c.req.header("authorization");
  if (!header || !header.toLowerCase().startsWith("bearer ")) {
    throw new HTTPException(401, { message: "Missing or malformed bearer token" });
  }
  const token = header.slice(7).trim();
  if (token.length === 0) {
    throw new HTTPException(401, { message: "Empty bearer token" });
  }

  // TODO(B-042): resolve token -> session -> driverId via the sessions table.
  // Until then we attach a placeholder so downstream handlers compile against
  // the eventual contract.
  c.set("driverId", null as string | null);
  await next();
});

declare module "hono" {
  interface ContextVariableMap {
    driverId: string | null;
  }
}
