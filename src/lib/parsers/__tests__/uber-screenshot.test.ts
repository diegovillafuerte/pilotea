import { describe, it, expect, vi, beforeEach } from "vitest";
import { parseUberScreenshot } from "@/lib/parsers/uber-screenshot";
import type { ParseInput } from "@/lib/parsers/types";

// ─── Mock Claude client ───────────────────────────────────────
vi.mock("@/lib/claude/client", () => ({
  callClaudeVision: vi.fn(),
  extractJsonFromResponse: vi.fn(),
}));

// ─── Mock normalizeImage (sharp can't process fake buffers) ──
vi.mock("@/lib/parsers/utils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/parsers/utils")>();
  return {
    ...actual,
    normalizeImage: vi.fn(async (buf: Buffer) => ({
      base64: buf.toString("base64"),
      mediaType: "image/jpeg" as const,
    })),
  };
});

import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";

const mockCallClaudeVision = vi.mocked(callClaudeVision);
const mockExtractJson = vi.mocked(extractJsonFromResponse);

// ─── Fixture: complete screenshot extraction ─────────────────
const COMPLETE_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 3850.5,
  gross_earnings: 5200.0,
  platform_commission: 1050.0,
  platform_commission_pct: 20.19,
  taxes: 299.5,
  incentives: 450.0,
  tips: 380.0,
};

// ─── Fixture: minimal extraction (some nulls) ────────────────
const MINIMAL_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 3850.5,
  gross_earnings: 5200.0,
  platform_commission: 1050.0,
  platform_commission_pct: 20.19,
  taxes: null,
  incentives: null,
  tips: null,
};

function makeFakeInput(): ParseInput {
  return {
    files: [Buffer.from("fake-screenshot-content")],
    mimeType: "image/png",
  };
}

describe("parseUberScreenshot", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("returns error when no files provided", async () => {
    const result = await parseUberScreenshot({ files: [], mimeType: "image/png" });

    expect(result.success).toBe(false);
    expect(result.error).toBe("No se recibio ninguna imagen para procesar.");
    expect(result.metrics).toBeNull();
    expect(result.data_completeness).toBe(0);
  });

  it("extracts metrics from a complete Uber screenshot", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.success).toBe(true);
    expect(result.metrics).not.toBeNull();

    const m = result.metrics!;

    // Fields available from pie chart
    expect(m.week_start).toBe("2025-03-24");
    expect(m.net_earnings).toBe(3850.5);
    expect(m.gross_earnings).toBe(5200.0);
    expect(m.platform_commission).toBe(1050.0);
    expect(m.platform_commission_pct).toBe(20.19);
    expect(m.taxes).toBe(299.5);
    expect(m.incentives).toBe(450.0);
    expect(m.tips).toBe(380.0);
  });

  it("sets all non-extractable fields to null", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberScreenshot(makeFakeInput());
    const m = result.metrics!;

    // Fields NOT available from pie chart screenshot
    expect(m.total_trips).toBeNull();
    expect(m.hours_online).toBeNull();
    expect(m.total_km).toBeNull();
    expect(m.active_days).toBeNull();
    expect(m.surge_earnings).toBeNull();
    expect(m.wait_time_earnings).toBeNull();
    expect(m.peak_day_earnings).toBeNull();
    expect(m.peak_day_name).toBeNull();
    expect(m.cash_amount).toBeNull();
    expect(m.card_amount).toBeNull();
    expect(m.rewards).toBeNull();

    // Derived metrics — cannot calculate without trips/hours/km
    expect(m.earnings_per_trip).toBeNull();
    expect(m.earnings_per_km).toBeNull();
    expect(m.earnings_per_hour).toBeNull();
    expect(m.trips_per_hour).toBeNull();
  });

  it("data_completeness is ~0.35-0.40 for a complete screenshot", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberScreenshot(makeFakeInput());

    // 8 fields filled out of 23 total = 8/23 = 0.35
    expect(result.data_completeness).toBeGreaterThanOrEqual(0.3);
    expect(result.data_completeness).toBeLessThanOrEqual(0.45);
  });

  it("handles partial extraction with some null fields", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(MINIMAL_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(MINIMAL_EXTRACTION);

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.success).toBe(true);
    const m = result.metrics!;

    expect(m.net_earnings).toBe(3850.5);
    expect(m.taxes).toBeNull();
    expect(m.incentives).toBeNull();
    expect(m.tips).toBeNull();

    // Lower completeness than full extraction
    expect(result.data_completeness).toBeLessThan(0.35);
  });

  it("returns Zod validation error for malformed Claude response", async () => {
    const badExtraction = {
      week_start: 12345, // should be string
      net_earnings: "not a number", // should be number
    };

    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(badExtraction),
      usage: { input_tokens: 500, output_tokens: 100 },
    });
    mockExtractJson.mockReturnValue(badExtraction);

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("formato esperado");
    expect(result.metrics).toBeNull();
  });

  it("rejects non-ISO date strings like 'unknown'", async () => {
    const unknownDate = { ...COMPLETE_EXTRACTION, week_start: "unknown" };

    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(unknownDate),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(unknownDate);

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("formato esperado");
    expect(result.metrics).toBeNull();
  });

  it("handles Claude API errors gracefully", async () => {
    mockCallClaudeVision.mockRejectedValue(new Error("API rate limit exceeded"));

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error al procesar la captura de Uber");
    expect(result.error).toContain("API rate limit exceeded");
    expect(result.metrics).toBeNull();
  });

  it("handles JSON parsing errors from Claude response", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: "I cannot parse this image",
      usage: { input_tokens: 500, output_tokens: 50 },
    });
    mockExtractJson.mockImplementation(() => {
      throw new Error("No se pudo interpretar la respuesta de Claude como JSON");
    });

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error al procesar la captura de Uber");
    expect(result.metrics).toBeNull();
  });

  it("sends normalized image as base64 with jpeg media type", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const imgBuffer = Buffer.from("test-image-content");
    await parseUberScreenshot({ files: [imgBuffer], mimeType: "image/png" });

    expect(mockCallClaudeVision).toHaveBeenCalledWith(
      expect.objectContaining({
        inputs: [
          {
            data: imgBuffer.toString("base64"),
            mediaType: "image/jpeg",
          },
        ],
      }),
    );
  });

  it("preserves raw extraction in result", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberScreenshot(makeFakeInput());

    expect(result.raw_extraction).toEqual(COMPLETE_EXTRACTION);
  });
});
