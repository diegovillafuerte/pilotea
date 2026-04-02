import { describe, it, expect, vi, beforeEach } from "vitest";
import { parseUberPdf } from "@/lib/parsers/uber-pdf";
import type { ParseInput } from "@/lib/parsers/types";

// ─── Mock Claude client ───────────────────────────────────────
vi.mock("@/lib/claude/client", () => ({
  callClaudeVision: vi.fn(),
  extractJsonFromResponse: vi.fn(),
}));

import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";

const mockCallClaudeVision = vi.mocked(callClaudeVision);
const mockExtractJson = vi.mocked(extractJsonFromResponse);

// ─── Fixture: complete Uber PDF extraction ────────────────────
const COMPLETE_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 3850.5,
  gross_earnings: 5200.0,
  total_trips: 72,
  hours_online: 45.5,
  platform_commission: 1050.0,
  platform_commission_pct: 20.19,
  taxes: 299.5,
  incentives: 450.0,
  tips: 380.0,
  surge_earnings: 620.0,
  wait_time_earnings: 185.0,
  active_days: 6,
  peak_day_earnings: 890.0,
  peak_day_name: "Sabado",
  cash_amount: 1200.0,
  card_amount: 2650.5,
  rewards: 150.0,
};

// ─── Fixture: partial extraction (some nulls) ─────────────────
const PARTIAL_EXTRACTION = {
  week_start: "2025-03-24",
  net_earnings: 3850.5,
  gross_earnings: 5200.0,
  total_trips: 72,
  hours_online: null,
  platform_commission: 1050.0,
  platform_commission_pct: 20.19,
  taxes: null,
  incentives: null,
  tips: null,
  surge_earnings: null,
  wait_time_earnings: null,
  active_days: null,
  peak_day_earnings: null,
  peak_day_name: null,
  cash_amount: null,
  card_amount: null,
  rewards: null,
};

function makeFakeInput(): ParseInput {
  return {
    files: [Buffer.from("fake-pdf-content")],
    mimeType: "application/pdf",
  };
}

describe("parseUberPdf", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("returns error when no files provided", async () => {
    const result = await parseUberPdf({ files: [], mimeType: "application/pdf" });

    expect(result.success).toBe(false);
    expect(result.error).toBe("No se recibio ningun archivo para procesar.");
    expect(result.metrics).toBeNull();
    expect(result.data_completeness).toBe(0);
  });

  it("extracts metrics from a complete Uber PDF", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(true);
    expect(result.metrics).not.toBeNull();

    const m = result.metrics!;

    // Core fields
    expect(m.week_start).toBe("2025-03-24");
    expect(m.net_earnings).toBe(3850.5);
    expect(m.gross_earnings).toBe(5200.0);
    expect(m.total_trips).toBe(72);
    expect(m.hours_online).toBe(45.5);

    // Commission
    expect(m.platform_commission).toBe(1050.0);
    expect(m.platform_commission_pct).toBe(20.19);

    // Extra fields
    expect(m.taxes).toBe(299.5);
    expect(m.incentives).toBe(450.0);
    expect(m.tips).toBe(380.0);
    expect(m.surge_earnings).toBe(620.0);
    expect(m.wait_time_earnings).toBe(185.0);
    expect(m.active_days).toBe(6);
    expect(m.peak_day_earnings).toBe(890.0);
    expect(m.peak_day_name).toBe("Sabado");
    expect(m.cash_amount).toBe(1200.0);
    expect(m.card_amount).toBe(2650.5);
    expect(m.rewards).toBe(150.0);
  });

  it("calculates derived metrics correctly", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberPdf(makeFakeInput());
    const m = result.metrics!;

    // earnings_per_trip = net_earnings / total_trips = 3850.5 / 72 = 53.48
    expect(m.earnings_per_trip).toBe(53.48);

    // earnings_per_hour = net_earnings / hours_online = 3850.5 / 45.5 = 84.63
    expect(m.earnings_per_hour).toBe(84.63);

    // trips_per_hour = total_trips / hours_online = 72 / 45.5 = 1.58
    expect(m.trips_per_hour).toBe(1.58);
  });

  it("sets earnings_per_km and total_km to null (Uber never reports km)", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberPdf(makeFakeInput());
    const m = result.metrics!;

    expect(m.earnings_per_km).toBeNull();
    expect(m.total_km).toBeNull();
  });

  it("data_completeness is ~0.95 for a complete PDF", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberPdf(makeFakeInput());

    // 23 total fields, 2 always null (earnings_per_km, total_km) = 21/23 = 0.91
    // That's the theoretical max for Uber PDF
    expect(result.data_completeness).toBeGreaterThanOrEqual(0.85);
    expect(result.data_completeness).toBeLessThanOrEqual(1.0);
  });

  it("handles partial extraction with null derived metrics", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(PARTIAL_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(PARTIAL_EXTRACTION);

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(true);
    const m = result.metrics!;

    // earnings_per_trip can still be calculated (net_earnings and total_trips present)
    expect(m.earnings_per_trip).toBe(53.48);

    // earnings_per_hour should be null (hours_online is null)
    expect(m.earnings_per_hour).toBeNull();

    // trips_per_hour should be null (hours_online is null)
    expect(m.trips_per_hour).toBeNull();

    // data_completeness should be lower
    expect(result.data_completeness).toBeLessThan(0.5);
  });

  it("returns Zod validation error for malformed Claude response", async () => {
    const badExtraction = {
      week_start: 12345, // should be string
      net_earnings: "not a number", // should be number
    };

    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(badExtraction),
      usage: { input_tokens: 1000, output_tokens: 100 },
    });
    mockExtractJson.mockReturnValue(badExtraction);

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("formato esperado");
    expect(result.metrics).toBeNull();
  });

  it("handles Claude API errors gracefully", async () => {
    mockCallClaudeVision.mockRejectedValue(new Error("API rate limit exceeded"));

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error al procesar el PDF de Uber");
    expect(result.error).toContain("API rate limit exceeded");
    expect(result.metrics).toBeNull();
  });

  it("handles JSON parsing errors from Claude response", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: "I cannot parse this document",
      usage: { input_tokens: 1000, output_tokens: 50 },
    });
    mockExtractJson.mockImplementation(() => {
      throw new Error("No se pudo interpretar la respuesta de Claude como JSON");
    });

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(false);
    expect(result.error).toContain("Error al procesar el PDF de Uber");
    expect(result.metrics).toBeNull();
  });

  it("sends PDF as base64 with correct media type", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const pdfBuffer = Buffer.from("test-pdf-content");
    await parseUberPdf({ files: [pdfBuffer], mimeType: "application/pdf" });

    expect(mockCallClaudeVision).toHaveBeenCalledWith(
      expect.objectContaining({
        inputs: [
          {
            data: pdfBuffer.toString("base64"),
            mediaType: "application/pdf",
          },
        ],
      }),
    );
  });

  it("preserves raw extraction in result", async () => {
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(COMPLETE_EXTRACTION),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(COMPLETE_EXTRACTION);

    const result = await parseUberPdf(makeFakeInput());

    expect(result.raw_extraction).toEqual(COMPLETE_EXTRACTION);
  });

  it("handles zero trips gracefully (no division by zero)", async () => {
    const zeroTrips = { ...COMPLETE_EXTRACTION, total_trips: 0 };
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(zeroTrips),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(zeroTrips);

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(true);
    expect(result.metrics!.earnings_per_trip).toBeNull();
  });

  it("handles zero hours gracefully (no division by zero)", async () => {
    const zeroHours = { ...COMPLETE_EXTRACTION, hours_online: 0 };
    mockCallClaudeVision.mockResolvedValue({
      text: JSON.stringify(zeroHours),
      usage: { input_tokens: 1000, output_tokens: 500 },
    });
    mockExtractJson.mockReturnValue(zeroHours);

    const result = await parseUberPdf(makeFakeInput());

    expect(result.success).toBe(true);
    expect(result.metrics!.earnings_per_hour).toBeNull();
    expect(result.metrics!.trips_per_hour).toBeNull();
  });
});
