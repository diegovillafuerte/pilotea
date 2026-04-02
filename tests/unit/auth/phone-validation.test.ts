import { describe, it, expect } from "vitest";
import { z } from "zod";

const phoneSchema = z.object({
  phone: z
    .string()
    .regex(
      /^\+[1-9]\d{7,14}$/,
      "Numero de telefono invalido. Usa formato internacional: +5215512345678",
    ),
});

describe("phone validation (E.164)", () => {
  it("should accept a valid Mexican phone number", () => {
    const result = phoneSchema.safeParse({ phone: "+5215512345678" });
    expect(result.success).toBe(true);
  });

  it("should accept a valid US phone number", () => {
    const result = phoneSchema.safeParse({ phone: "+14155238886" });
    expect(result.success).toBe(true);
  });

  it("should accept minimum length (8 digits + country code)", () => {
    const result = phoneSchema.safeParse({ phone: "+12345678" });
    expect(result.success).toBe(true);
  });

  it("should reject phone without + prefix", () => {
    const result = phoneSchema.safeParse({ phone: "5215512345678" });
    expect(result.success).toBe(false);
  });

  it("should reject phone starting with +0", () => {
    const result = phoneSchema.safeParse({ phone: "+0215512345678" });
    expect(result.success).toBe(false);
  });

  it("should reject phone with too few digits", () => {
    const result = phoneSchema.safeParse({ phone: "+1234567" });
    expect(result.success).toBe(false);
  });

  it("should reject phone with letters", () => {
    const result = phoneSchema.safeParse({ phone: "+521abc45678" });
    expect(result.success).toBe(false);
  });

  it("should reject empty string", () => {
    const result = phoneSchema.safeParse({ phone: "" });
    expect(result.success).toBe(false);
  });

  it("should reject phone with spaces", () => {
    const result = phoneSchema.safeParse({ phone: "+52 155 1234 5678" });
    expect(result.success).toBe(false);
  });

  it("should reject phone with dashes", () => {
    const result = phoneSchema.safeParse({ phone: "+52-155-1234-5678" });
    expect(result.success).toBe(false);
  });
});
