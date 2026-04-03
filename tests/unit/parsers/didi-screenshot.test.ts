import { describe, it, expect, vi, beforeEach } from "vitest";
import type { ParseInput } from "@/lib/parsers/types";

// ─── Mock the Claude client ─────────────────────────────────
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
import { parseDidiScreenshot } from "@/lib/parsers/didi-screenshot";

const mockCallClaudeVision = vi.mocked(callClaudeVision);
const mockExtractJson = vi.mocked(extractJsonFromResponse);

// ─── Fixture: valid DiDi extraction response ─────────────────
const VALID_DIDI_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 4200.5,
  gross_earnings: 5100.0,
  total_trips: 85,
  earnings_per_km: 8.75,
  earnings_per_trip: 49.42,
  earnings_per_hour: 175.0,
  cash_amount: 2600.0,
  card_amount: 1600.5,
  taxes: 380.0,
  rewards: 150.0,
};

const TWO_FILE_INPUT: ParseInput = {
  files: [Buffer.from("earnings-image"), Buffer.from("tablero-image")],
  mimeType: "image/png",
};

describe("parseDidiScreenshot", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(VALID_DIDI_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 200 },
    });
    mockExtractJson.mockReturnValue(VALID_DIDI_EXTRACTION);
  });

  // ─── Input validation ──────────────────────────────────────

  it("returns error when fewer than 2 files are provided", async () => {
    const result = await parseDidiScreenshot({
      files: [Buffer.from("single")],
      mimeType: "image/png",
    });
    expect(result.success).toBe(false);
    expect(result.error).toContain("2 capturas");
    expect(result.metrics).toBeNull();
  });

  it("returns error when no files are provided", async () => {
    const result = await parseDidiScreenshot({
      files: [],
      mimeType: "image/png",
    });
    expect(result.success).toBe(false);
    expect(result.error).toContain("2 capturas");
  });

  // ─── Successful extraction ─────────────────────────────────

  it("sends both images in a single Claude Vision call", async () => {
    await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(mockCallClaudeVision).toHaveBeenCalledTimes(1);
    const callArg = mockCallClaudeVision.mock.calls[0][0];
    expect(callArg.inputs).toHaveLength(2);
    expect(callArg.inputs[0].mediaType).toBe("image/jpeg");
    expect(callArg.inputs[1].mediaType).toBe("image/jpeg");
  });

  it("extracts all DiDi-specific fields including earnings_per_km", async () => {
    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    expect(result.metrics).not.toBeNull();
    expect(result.metrics!.earnings_per_km).toBe(8.75);
    expect(result.metrics!.net_earnings).toBe(4200.5);
    expect(result.metrics!.gross_earnings).toBe(5100.0);
    expect(result.metrics!.total_trips).toBe(85);
    expect(result.metrics!.earnings_per_trip).toBe(49.42);
    expect(result.metrics!.earnings_per_hour).toBe(175.0);
    expect(result.metrics!.cash_amount).toBe(2600.0);
    expect(result.metrics!.card_amount).toBe(1600.5);
    expect(result.metrics!.taxes).toBe(380.0);
    expect(result.metrics!.rewards).toBe(150.0);
  });

  it("derives hours_online from net_earnings / earnings_per_hour", async () => {
    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    // 4200.5 / 175.0 = 24.003... → rounded to 24.0
    expect(result.metrics!.hours_online).toBe(24.0);
  });

  it("derives trips_per_hour from total_trips / hours_online", async () => {
    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    // 85 / 24.0 = 3.5416... → rounded to 3.54
    expect(result.metrics!.trips_per_hour).toBe(3.54);
  });

  it("sets platform_commission to null (DiDi does not report it)", async () => {
    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    expect(result.metrics!.platform_commission).toBeNull();
    expect(result.metrics!.platform_commission_pct).toBeNull();
  });

  it("returns data_completeness > 0", async () => {
    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    expect(result.data_completeness).toBeGreaterThan(0);
    // With all fields filled except platform_commission, platform_commission_pct,
    // total_km, incentives, tips, surge_earnings, wait_time_earnings,
    // active_days, peak_day_earnings, peak_day_name → 14/24 ≈ 0.58
    // But hours_online and trips_per_hour are derived, so 16/24 ≈ 0.67
    expect(result.data_completeness).toBeGreaterThanOrEqual(0.5);
  });

  it("stores raw extraction in raw_extraction field", async () => {
    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    expect(result.raw_extraction).toEqual(
      expect.objectContaining({
        week_start: "2025-03-24",
        earnings_per_km: 8.75,
      }),
    );
  });

  // ─── Partial data handling ─────────────────────────────────

  it("handles null earnings_per_hour gracefully (no hours derivation)", async () => {
    const partialExtraction = { ...VALID_DIDI_EXTRACTION, earnings_per_hour: null };
    mockExtractJson.mockReturnValue(partialExtraction);

    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    expect(result.metrics!.earnings_per_hour).toBeNull();
    expect(result.metrics!.hours_online).toBeNull();
    expect(result.metrics!.trips_per_hour).toBeNull();
  });

  it("handles zero earnings_per_hour without division error", async () => {
    const zeroEph = { ...VALID_DIDI_EXTRACTION, earnings_per_hour: 0 };
    mockExtractJson.mockReturnValue(zeroEph);

    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(true);
    expect(result.metrics!.hours_online).toBeNull();
  });

  // ─── Error handling ────────────────────────────────────────

  it("returns error when Claude response fails Zod validation", async () => {
    mockExtractJson.mockReturnValue({ week_start: "not-a-date", net_earnings: "string" });

    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(false);
    expect(result.error).toContain("formato esperado");
    expect(result.data_completeness).toBe(0);
  });

  it("returns error when Claude Vision call throws", async () => {
    mockCallClaudeVision.mockRejectedValue(new Error("API timeout"));

    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(false);
    expect(result.error).toContain("API timeout");
    expect(result.error).toContain("DiDi");
  });

  it("handles non-Error thrown objects", async () => {
    mockCallClaudeVision.mockRejectedValue("network failure");

    const result = await parseDidiScreenshot(TWO_FILE_INPUT);

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error desconocido");
  });

  // ─── MIME type handling ────────────────────────────────────

  it("normalizes all images to jpeg regardless of input MIME type", async () => {
    await parseDidiScreenshot({
      files: [Buffer.from("a"), Buffer.from("b")],
      mimeType: "image/bmp",
    });

    const callArg = mockCallClaudeVision.mock.calls[0][0];
    expect(callArg.inputs[0].mediaType).toBe("image/jpeg");
    expect(callArg.inputs[1].mediaType).toBe("image/jpeg");
  });
});
