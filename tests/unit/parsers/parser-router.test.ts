import { describe, it, expect } from "vitest";
import { parseUpload } from "@/lib/parsers";

const emptyInput = { files: [Buffer.from("test")], mimeType: "application/pdf" };

describe("parseUpload router", () => {
  it("returns not-implemented stub for uber + pdf", async () => {
    const result = await parseUpload("uber", "pdf", emptyInput);
    expect(result.success).toBe(false);
    expect(result.metrics).toBeNull();
    expect(result.error).toContain("Uber");
    expect(result.error).toContain("PDF");
  });

  it("returns not-available for uber + screenshot", async () => {
    const result = await parseUpload("uber", "screenshot", {
      files: [Buffer.from("test")],
      mimeType: "image/png",
    });
    expect(result.success).toBe(false);
    expect(result.error).toContain("Uber");
  });

  it("returns not-available for didi + screenshot", async () => {
    const result = await parseUpload("didi", "screenshot", {
      files: [Buffer.from("test")],
      mimeType: "image/png",
    });
    expect(result.success).toBe(false);
    expect(result.error).toContain("DiDi");
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
