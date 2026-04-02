import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

const COOKIE_NAME = "pilotea_session";

/**
 * Next.js Edge Middleware.
 * Protects /(app)/* routes by checking for a valid session cookie.
 *
 * Note: We cannot call the Drizzle DB from Edge middleware directly
 * (postgres.js requires Node.js runtime). Instead, we check for the
 * cookie presence and format here. The full DB-backed session
 * verification happens in the API-level requireAuth() middleware,
 * which gates all data access and mutations.
 *
 * Uses Web Crypto API (Edge-compatible) instead of Node crypto.
 */
export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Only protect (app) routes — dashboard, compare, upload, fiscal, tips
  const protectedPaths = [
    "/dashboard",
    "/compare",
    "/upload",
    "/fiscal",
    "/tips",
    "/onboarding",
  ];

  const isProtected = protectedPaths.some((p) => pathname.startsWith(p));

  if (!isProtected) {
    return NextResponse.next();
  }

  const sessionToken = request.cookies.get(COOKIE_NAME)?.value;

  if (!sessionToken) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("redirect", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // Basic format validation (64-char hex string)
  if (!/^[a-f0-9]{64}$/.test(sessionToken)) {
    const response = NextResponse.redirect(new URL("/login", request.url));
    response.cookies.delete(COOKIE_NAME);
    return response;
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/compare/:path*",
    "/upload/:path*",
    "/fiscal/:path*",
    "/tips/:path*",
    "/onboarding/:path*",
  ],
};
