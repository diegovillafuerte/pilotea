import { NextResponse } from "next/server";
import { validateMagicLink } from "@/lib/auth/magic-link";
import { createSession, setSessionCookie } from "@/lib/auth/session";
import { db } from "@/lib/db";
import { drivers } from "@/lib/db/schema";
import { eq } from "drizzle-orm";

function getBaseUrl(request: Request): string {
  // Behind a reverse proxy, request.url is localhost — use forwarded headers
  const forwardedProto = request.headers.get("x-forwarded-proto");
  const forwardedHost = request.headers.get("x-forwarded-host");
  if (forwardedProto && forwardedHost) {
    return `${forwardedProto}://${forwardedHost}`;
  }
  return new URL(request.url).origin;
}

export async function GET(request: Request) {
  const url = new URL(request.url);
  const token = url.searchParams.get("token");
  const baseUrl = getBaseUrl(request);

  if (!token) {
    return NextResponse.redirect(
      new URL("/login?error=missing_token", baseUrl),
    );
  }

  // Validate and consume the magic link
  const phone = await validateMagicLink(token);

  if (!phone) {
    return NextResponse.redirect(
      new URL("/login?error=invalid_or_expired", baseUrl),
    );
  }

  // Find or create driver
  let [driver] = await db
    .select()
    .from(drivers)
    .where(eq(drivers.phone, phone))
    .limit(1);

  if (!driver) {
    const [newDriver] = await db
      .insert(drivers)
      .values({ phone })
      .returning();
    driver = newDriver;
  }

  // Create session
  const sessionToken = await createSession(driver.id);
  await setSessionCookie(sessionToken);

  // Redirect based on onboarding status
  const redirectUrl = driver.onboardingCompleted
    ? "/dashboard"
    : "/onboarding";

  return NextResponse.redirect(new URL(redirectUrl, baseUrl));
}
