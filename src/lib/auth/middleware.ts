import { NextResponse } from "next/server";
import { getSessionToken, verifySession } from "./session";

export type AuthenticatedContext = {
  driverId: string;
};

/**
 * Reusable auth check for API routes.
 * Reads the session cookie, verifies it, and returns the driver_id.
 * If unauthorized, returns a 401 JSON response.
 *
 * Usage in a route handler:
 *   const auth = await requireAuth();
 *   if (auth instanceof NextResponse) return auth;
 *   const { driverId } = auth;
 */
export async function requireAuth(): Promise<
  AuthenticatedContext | NextResponse
> {
  const token = await getSessionToken();

  if (!token) {
    return NextResponse.json(
      { ok: false, error: "No autenticado" },
      { status: 401 },
    );
  }

  const driverId = await verifySession(token);

  if (!driverId) {
    return NextResponse.json(
      { ok: false, error: "Sesion invalida o expirada" },
      { status: 401 },
    );
  }

  return { driverId };
}
