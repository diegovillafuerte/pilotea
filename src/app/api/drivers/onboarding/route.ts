import { NextResponse } from "next/server";
import { z } from "zod";
import { db } from "@/lib/db";
import { drivers } from "@/lib/db/schema";
import { eq } from "drizzle-orm";
import { requireAuth } from "@/lib/auth/middleware";
import { CITY_KEYS, PLATFORM_KEYS } from "@/lib/constants";

const onboardingSchema = z.object({
  name: z
    .string()
    .min(2, "El nombre debe tener al menos 2 caracteres")
    .max(100, "El nombre no puede exceder 100 caracteres")
    .transform((s) => s.trim()),
  city: z.string().refine((val) => CITY_KEYS.has(val), {
    message: "Ciudad no valida",
  }),
  platforms: z
    .array(z.string())
    .min(1, "Selecciona al menos una plataforma")
    .max(3, "Maximo 3 plataformas")
    .refine((arr) => arr.every((p) => PLATFORM_KEYS.has(p)), {
      message: "Plataforma no valida",
    }),
});

export async function POST(request: Request) {
  // Authenticate the driver
  const auth = await requireAuth();
  if (auth instanceof NextResponse) return auth;
  const { driverId } = auth;

  // Check if already onboarded
  const [driver] = await db
    .select({ onboardingCompleted: drivers.onboardingCompleted })
    .from(drivers)
    .where(eq(drivers.id, driverId))
    .limit(1);

  if (!driver) {
    return NextResponse.json(
      { ok: false, error: "Conductor no encontrado" },
      { status: 404 },
    );
  }

  if (driver.onboardingCompleted) {
    return NextResponse.json(
      { ok: false, error: "Onboarding ya completado" },
      { status: 400 },
    );
  }

  // Parse and validate input
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { ok: false, error: "JSON invalido" },
      { status: 400 },
    );
  }

  const result = onboardingSchema.safeParse(body);
  if (!result.success) {
    const firstError = result.error.errors[0]?.message ?? "Datos invalidos";
    return NextResponse.json(
      { ok: false, error: firstError },
      { status: 400 },
    );
  }

  const { name, city, platforms } = result.data;

  // Update driver record
  await db
    .update(drivers)
    .set({
      name,
      city,
      platforms,
      onboardingCompleted: true,
      updatedAt: new Date(),
    })
    .where(eq(drivers.id, driverId));

  return NextResponse.json({ ok: true });
}
