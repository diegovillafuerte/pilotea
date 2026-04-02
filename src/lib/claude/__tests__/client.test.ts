import { describe, it, expect } from "vitest";
import { extractJsonFromResponse } from "@/lib/claude/client";

describe("extractJsonFromResponse", () => {
  it("parses plain JSON", () => {
    const input = '{"week_start": "2025-03-24", "net_earnings": 3500}';
    const result = extractJsonFromResponse(input);
    expect(result).toEqual({ week_start: "2025-03-24", net_earnings: 3500 });
  });

  it("parses JSON wrapped in markdown code fence", () => {
    const input = '```json\n{"week_start": "2025-03-24", "net_earnings": 3500}\n```';
    const result = extractJsonFromResponse(input);
    expect(result).toEqual({ week_start: "2025-03-24", net_earnings: 3500 });
  });

  it("parses JSON wrapped in plain code fence (no language tag)", () => {
    const input = '```\n{"week_start": "2025-03-24"}\n```';
    const result = extractJsonFromResponse(input);
    expect(result).toEqual({ week_start: "2025-03-24" });
  });

  it("trims whitespace before parsing", () => {
    const input = '  \n  {"week_start": "2025-03-24"}\n  ';
    const result = extractJsonFromResponse(input);
    expect(result).toEqual({ week_start: "2025-03-24" });
  });

  it("throws descriptive error for invalid JSON", () => {
    const input = "this is not json at all";
    expect(() => extractJsonFromResponse(input)).toThrow(
      "No se pudo interpretar la respuesta de Claude como JSON",
    );
  });

  it("throws descriptive error for partial JSON", () => {
    const input = '{"week_start": "2025-03-24"';
    expect(() => extractJsonFromResponse(input)).toThrow(
      "No se pudo interpretar la respuesta de Claude como JSON",
    );
  });
});
