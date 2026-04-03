import { describe, it, expect, vi, beforeEach } from "vitest";
import { parseIndriveScreenshot } from "@/lib/parsers/indrive-screenshot";
import type { ParseInput } from "@/lib/parsers/types";

// ─── Mock Claude client ───────────────────────────────────────
vi.mock("@/lib/claude/client", () => ({
  callClaudeVision: vi.fn(),
  extractJsonFromResponse: vi.fn(),
}));

// ─── Mock prepareFileForVision (sharp can't process fake buffers) ──
vi.mock("@/lib/parsers/utils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/parsers/utils")>();
  return {
    ...actual,
    prepareFileForVision: vi.fn(async (buf: Buffer) => ({
      base64: buf.toString("base64"),
      mediaType: "image/jpeg" as const,
    })),
  };
});

import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";

const mockCallClaudeVision = vi.mocked(callClaudeVision);
const mockExtractJson = vi.mocked(extractJsonFromResponse);

// ─── Fixture: complete InDrive extraction ─────────────────────
const COMPLETE_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 2850.0,
  gross_earnings: 3500.0,
  total_trips: 42,
  total_km: 380.5,
  earnings_per_km: 7.49,
  service_fee: 650.0,
};

// ─── Fixture: minimal extraction (some nulls) ────────────────
const MINIMAL_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 2850.0,
  gross_earnings: 3500.0,
  total_trips: null,
  total_km: null,
  earnings_per_km: null,
  service_fee: 650.0,
};

function makeFakeInput(): ParseInput {
  return {
    files: [Buffer.from("fake-indrive-screenshot")],
    mimeType: "image/png",
  };
}

describe("parseIndriveScreenshot", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("returns error when no files provided", async () => {
    const result = await parseIndriveScreenshot({ files: [], mimeType: "image/png" });

    expect(result.success).toBe(false);
    expect(result.error).toBe("No se recibio ninguna imagen para procesar.");
    expect(result.metrics).toBeNull();
    expect(result.data_completeness).toBe(0);
  });

  it("extracts metrics from a complete InDrive screenshot", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());

    expect(result.success).toBe(true);
    expect(result.metrics).not.toBeNull();

    const m = result.metrics!;

    // Fields directly extracted
    expect(m.week_start).toBe("2025-03-24");
    expect(m.net_earnings).toBe(2850.0);
    expect(m.gross_earnings).toBe(3500.0);
    expect(m.total_trips).toBe(42);
    expect(m.total_km).toBe(380.5);
    expect(m.earnings_per_km).toBe(7.49);
  });

  it("derives commission percentage from (gross - net) / gross", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());
    const m = result.metrics!;

    // service_fee = 650, gross = 3500, net = 2850
    // commission_pct = (3500 - 2850) / 3500 * 100 = 18.57%
    expect(m.platform_commission).toBe(650.0);
    expect(m.platform_commission_pct).toBeCloseTo(18.57, 1);
  });

  it("derives earnings_per_trip from net / trips", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());
    const m = result.metrics!;

    // earnings_per_trip = 2850 / 42 = 67.86
    expect(m.earnings_per_trip).toBeCloseTo(67.86, 1);
  });

  it("sets hours-dependent metrics to null (InDrive never reports hours)", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());
    const m = result.metrics!;

    // Hours-dependent metrics must always be null
    expect(m.hours_online).toBeNull();
    expect(m.earnings_per_hour).toBeNull();
    expect(m.trips_per_hour).toBeNull();
  });

  it("sets non-available fields to null", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());
    const m = result.metrics!;

    expect(m.taxes).toBeNull();
    expect(m.incentives).toBeNull();
    expect(m.tips).toBeNull();
    expect(m.surge_earnings).toBeNull();
    expect(m.wait_time_earnings).toBeNull();
    expect(m.active_days).toBeNull();
    expect(m.peak_day_earnings).toBeNull();
    expect(m.peak_day_name).toBeNull();
    expect(m.cash_amount).toBeNull();
    expect(m.card_amount).toBeNull();
    expect(m.rewards).toBeNull();
  });

  it("data_completeness is ~0.35-0.45 for a complete extraction", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());

    // 10 fields filled out of 23 total: week_start, net_earnings, gross_earnings,
    // total_trips, total_km, earnings_per_km, earnings_per_trip (derived),
    // platform_commission, platform_commission_pct (derived) = 9/23 ~= 0.39
    expect(result.data_completeness).toBeGreaterThanOrEqual(0.35);
    expect(result.data_completeness).toBeLessThanOrEqual(0.50);
  });

  it("handles partial extraction with some null fields", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(MINIMAL_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(MINIMAL_EXTRACTION);

    const result = await parseIndriveScreenshot(makeFakeInput());

    expect(result.success).toBe(true);
    const m = result.metrics!;

    expect(m.net_earnings).toBe(2850.0);
    expect(m.total_trips).toBeNull();
    expect(m.total_km).toBeNull();
    expect(m.earnings_per_km).toBeNull();

    // earnings_per_trip cannot be derived without trips
    expect(m.earnings_per_trip).toBeNull();

    // Commission still derivable from gross and net
    expect(m.platform_commission).toBe(650.0);
    expect(m.platform_commission_pct).toBeCloseTo(18.57, 1);
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

    const result = await parseIndriveScreenshot(makeFakeInput());

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

    const result = await parseIndriveScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("formato esperado");
    expect(result.metrics).toBeNull();
  });

  it("handles Claude API errors gracefully", async () => {
    mockCallClaudeVision.mockRejectedValue(new Error("API rate limit exceeded"));

    const result = await parseIndriveScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error al procesar la captura de InDrive");
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

    const result = await parseIndriveScreenshot(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error al procesar la captura de InDrive");
    expect(result.metrics).toBeNull();
  });

  it("sends normalized image as base64 with jpeg media type", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 500, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const imgBuffer = Buffer.from("test-image-content");
    await parseIndriveScreenshot({ files: [imgBuffer], mimeType: "image/png" });

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

    const result = await parseIndriveScreenshot(makeFakeInput());

    expect(result.raw_extraction).toEqual(COMPLETE_EXTRACTION);
  });
});
