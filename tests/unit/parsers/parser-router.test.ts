import { describe, it, expect, vi, beforeEach } from "vitest";
import { parseUpload } from "@/lib/parsers";
import type { ParseResult } from "@/lib/parsers/types";

// ─── Mock the parsers ────────────────────────────────────────
vi.mock("@/lib/parsers/uber-screenshot", () => ({
  parseUberScreenshot: vi.fn(),
}));

vi.mock("@/lib/parsers/didi-screenshot", () => ({
  parseDidiScreenshot: vi.fn(),
}));

import { parseUberScreenshot } from "@/lib/parsers/uber-screenshot";
import { parseDidiScreenshot } from "@/lib/parsers/didi-screenshot";

const mockParseUberScreenshot = vi.mocked(parseUberScreenshot);
const mockParseDidiScreenshot = vi.mocked(parseDidiScreenshot);

const MOCK_SCREENSHOT_RESULT: ParseResult = {
  success: true,
  metrics: {
    week_start: "2025-03-24",
    net_earnings: 3850.5,
    gross_earnings: 5200.0,
    platform_commission: 1050.0,
    platform_commission_pct: 20.19,
    taxes: 299.5,
    incentives: 450.0,
    tips: 380.0,
    total_trips: null,
    hours_online: null,
    total_km: null,
    active_days: null,
    surge_earnings: null,
    wait_time_earnings: null,
    peak_day_earnings: null,
    peak_day_name: null,
    cash_amount: null,
    card_amount: null,
    rewards: null,
    earnings_per_trip: null,
    earnings_per_km: null,
    earnings_per_hour: null,
    trips_per_hour: null,
  },
  raw_extraction: {},
  data_completeness: 0.35,
};

const MOCK_DIDI_RESULT: ParseResult = {
  success: true,
  metrics: {
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
    hours_online: 24.0,
    trips_per_hour: 3.54,
    platform_commission: null,
    platform_commission_pct: null,
    total_km: null,
    incentives: null,
    tips: null,
    surge_earnings: null,
    wait_time_earnings: null,
    active_days: null,
    peak_day_earnings: null,
    peak_day_name: null,
  },
  raw_extraction: {},
  data_completeness: 0.58,
};

const emptyInput = { files: [Buffer.from("test")], mimeType: "application/pdf" };

describe("parseUpload router", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockParseUberScreenshot.mockResolvedValue(MOCK_SCREENSHOT_RESULT);
    mockParseDidiScreenshot.mockResolvedValue(MOCK_DIDI_RESULT);
  });

  it("returns not-implemented stub for uber + pdf", async () => {
    const result = await parseUpload("uber", "pdf", emptyInput);
    expect(result.success).toBe(false);
    expect(result.metrics).toBeNull();
    expect(result.error).toContain("Uber");
    expect(result.error).toContain("PDF");
  });

  it("routes uber + screenshot to parseUberScreenshot", async () => {
    const input = {
      files: [Buffer.from("test")],
      mimeType: "image/png",
    };
    const result = await parseUpload("uber", "screenshot", input);
    expect(mockParseUberScreenshot).toHaveBeenCalledWith(input);
    expect(result.success).toBe(true);
    expect(result.metrics).not.toBeNull();
    expect(result.data_completeness).toBe(0.35);
  });

  it("routes didi + screenshot to parseDidiScreenshot", async () => {
    const input = {
      files: [Buffer.from("earnings"), Buffer.from("tablero")],
      mimeType: "image/png",
    };
    const result = await parseUpload("didi", "screenshot", input);
    expect(mockParseDidiScreenshot).toHaveBeenCalledWith(input);
    expect(result.success).toBe(true);
    expect(result.metrics).not.toBeNull();
    expect(result.metrics!.earnings_per_km).toBe(8.75);
  });

  it("returns not-available for indrive + screenshot", async () => {
    const result = await parseUpload("indrive", "screenshot", {
      files: [Buffer.from("test")],
      mimeType: "image/png",
    });
    expect(result.success).toBe(false);
    expect(result.error).toContain("InDrive");
  });

  it("returns catch-all for unsupported combination", async () => {
    const result = await parseUpload("didi", "pdf", emptyInput);
    expect(result.success).toBe(false);
    expect(result.error).toContain("didi");
    expect(result.error).toContain("pdf");
  });

  it("always returns data_completeness of 0 for stubs", async () => {
    const result = await parseUpload("uber", "pdf", emptyInput);
    expect(result.data_completeness).toBe(0);
  });

  it("always returns empty raw_extraction for stubs", async () => {
    const result = await parseUpload("uber", "pdf", emptyInput);
    expect(result.raw_extraction).toEqual({});
  });
});
