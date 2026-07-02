/**
 * Extraction-schema guards. Claude Vision output is untrusted: a malformed week_start must be
 * rejected at the schema, not flow into the weeklyAggregates DATE key. All four parser schemas must
 * enforce the same ISO-date shape (the Uber PDF one previously did not — it accepted any string).
 */
import { describe, it, expect } from "vitest";
import {
  uberPdfExtractionSchema,
  uberScreenshotExtractionSchema,
  didiScreenshotExtractionSchema,
  indriveScreenshotExtractionSchema,
} from "./types.js";

const schemas = {
  uberPdf: uberPdfExtractionSchema,
  uberScreenshot: uberScreenshotExtractionSchema,
  didiScreenshot: didiScreenshotExtractionSchema,
  indriveScreenshot: indriveScreenshotExtractionSchema,
};

describe("parser extraction schemas — week_start validation", () => {
  for (const [name, schema] of Object.entries(schemas)) {
    // Test the field in isolation (the objects have many other required-nullable keys).
    const weekStart = schema.shape.week_start;

    it(`${name}: accepts a valid ISO week_start`, () => {
      expect(weekStart.safeParse("2025-03-24").success).toBe(true);
    });

    it(`${name}: rejects a non-ISO-shaped week_start`, () => {
      expect(weekStart.safeParse("March 24").success).toBe(false);
      expect(weekStart.safeParse("2025/03/24").success).toBe(false);
    });

    it(`${name}: allows a null week_start (falls back to current Monday downstream)`, () => {
      expect(weekStart.safeParse(null).success).toBe(true);
    });
  }
});
