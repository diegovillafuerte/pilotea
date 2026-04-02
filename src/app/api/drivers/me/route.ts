import { NextResponse } from "next/server";
import { requireAuth } from "@/lib/auth/middleware";
import { db } from "@/lib/db";
import { drivers } from "@/lib/db/schema";
import { eq } from "drizzle-orm";

export const dynamic = "force-dynamic";

export async function GET() {
  const auth = await requireAuth();
  if (auth instanceof NextResponse) return auth;
  const { driverId } = auth;

  const [driver] = await db
    .select()
    .from(drivers)
    .where(eq(drivers.id, driverId))
    .limit(1);

  if (!driver) {
    return NextResponse.json(
      { ok: false, error: "Conductor no encontrado" },
      { status: 404 },
    );
  }

  return NextResponse.json({
    id: driver.id,
    name: driver.name,
    city: driver.city,
    platforms: driver.platforms,
    tier: driver.tier,
    onboardingCompleted: driver.onboardingCompleted,
    streakWeeks: driver.streakWeeks,
    lastUploadAt: driver.lastUploadAt,
  });
}
