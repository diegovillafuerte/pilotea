import { NextResponse } from "next/server";
import { z } from "zod";
import { createMagicLink, isRateLimited } from "@/lib/auth/magic-link";
import { sendMagicLink } from "@/lib/whatsapp/client";

// E.164 format: +{country code}{number}, 8-15 digits
const phoneSchema = z.object({
  phone: z
    .string()
    .regex(
      /^\+[1-9]\d{7,14}$/,
      "Numero de telefono invalido. Usa formato internacional: +5215512345678",
    ),
});

export async function POST(request: Request) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { ok: false, error: "Cuerpo de solicitud invalido" },
      { status: 400 },
    );
  }

  const parsed = phoneSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(
      { ok: false, error: parsed.error.issues[0].message },
      { status: 400 },
    );
  }

  const { phone } = parsed.data;

  // Rate limit: max 3 per 15 minutes per phone
  const rateLimited = await isRateLimited(phone);
  if (rateLimited) {
    return NextResponse.json(
      { ok: false, error: "Demasiados intentos. Espera unos minutos." },
      { status: 429 },
    );
  }

  // Generate magic link token
  const token = await createMagicLink(phone);

  // Send via WhatsApp
  try {
    await sendMagicLink(phone, token);
  } catch (error) {
    // Log only error type/code, never phone numbers or full error objects
    const errCode = error instanceof Error ? error.message.split(":")[0] : "unknown";
    console.error("Failed to send WhatsApp message, error code:", errCode);
    return NextResponse.json(
      {
        ok: false,
        error: "No pudimos enviar el mensaje. Intenta de nuevo.",
      },
      { status: 502 },
    );
  }

  return NextResponse.json({
    ok: true,
    message: "Te enviamos un link por WhatsApp. Tocalo para entrar.",
  });
}
