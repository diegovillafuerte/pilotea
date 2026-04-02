import { describe, it, expect } from "vitest";
import { z } from "zod";
import { CITY_KEYS, PLATFORM_KEYS } from "@/lib/constants";

// Replicate the validation schema from the API route for unit testing
// without importing server-side dependencies (db, auth)
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

describe("onboarding validation schema", () => {
  it("should accept valid onboarding data", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan Garcia",
      city: "cdmx",
      platforms: ["uber"],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe("Juan Garcia");
      expect(result.data.city).toBe("cdmx");
      expect(result.data.platforms).toEqual(["uber"]);
    }
  });

  it("should accept multiple platforms", () => {
    const result = onboardingSchema.safeParse({
      name: "Maria Lopez",
      city: "monterrey",
      platforms: ["uber", "didi", "indrive"],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.platforms).toHaveLength(3);
    }
  });

  it("should trim name whitespace", () => {
    const result = onboardingSchema.safeParse({
      name: "  Juan  ",
      city: "cdmx",
      platforms: ["uber"],
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe("Juan");
    }
  });

  it("should reject name shorter than 2 characters", () => {
    const result = onboardingSchema.safeParse({
      name: "J",
      city: "cdmx",
      platforms: ["uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject name longer than 100 characters", () => {
    const result = onboardingSchema.safeParse({
      name: "A".repeat(101),
      city: "cdmx",
      platforms: ["uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject empty name", () => {
    const result = onboardingSchema.safeParse({
      name: "",
      city: "cdmx",
      platforms: ["uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject invalid city key", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan",
      city: "new_york",
      platforms: ["uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject empty platforms array", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan",
      city: "cdmx",
      platforms: [],
    });
    expect(result.success).toBe(false);
  });

  it("should reject invalid platform", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan",
      city: "cdmx",
      platforms: ["lyft"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject more than 3 platforms", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan",
      city: "cdmx",
      platforms: ["uber", "didi", "indrive", "uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject missing name field", () => {
    const result = onboardingSchema.safeParse({
      city: "cdmx",
      platforms: ["uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject missing city field", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan",
      platforms: ["uber"],
    });
    expect(result.success).toBe(false);
  });

  it("should reject missing platforms field", () => {
    const result = onboardingSchema.safeParse({
      name: "Juan",
      city: "cdmx",
    });
    expect(result.success).toBe(false);
  });

  it("should accept all 40 valid cities", () => {
    for (const cityKey of CITY_KEYS) {
      const result = onboardingSchema.safeParse({
        name: "Test Driver",
        city: cityKey,
        platforms: ["uber"],
      });
      expect(result.success).toBe(true);
    }
  });

  it("should accept each platform individually", () => {
    for (const platformKey of PLATFORM_KEYS) {
      const result = onboardingSchema.safeParse({
        name: "Test Driver",
        city: "cdmx",
        platforms: [platformKey],
      });
      expect(result.success).toBe(true);
    }
  });
});
