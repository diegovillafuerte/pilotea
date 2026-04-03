import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { NextRequest } from "next/server";

// Mock getDirectClient before importing
const mockSql = vi.fn();
vi.mock("@/lib/db", () => ({
  getDirectClient: () => mockSql,
}));

const { POST } = await import("@/app/api/cron/update-stats/route");

describe("POST /api/cron/update-stats", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    vi.clearAllMocks();
    process.env = { ...originalEnv, CRON_SECRET: "test-secret-123" };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  function makeRequest(authHeader?: string): NextRequest {
    const headers: Record<string, string> = {};
    if (authHeader) {
      headers["authorization"] = authHeader;
    }
    return new NextRequest("http://localhost:3000/api/cron/update-stats", {
      method: "POST",
      headers,
    });
  }

  it("returns 401 when no authorization header", async () => {
    const response = await POST(makeRequest());
    expect(response.status).toBe(401);

    const body = await response.json();
    expect(body.error).toBe("Unauthorized");
  });

  it("returns 401 when authorization header is wrong", async () => {
    const response = await POST(makeRequest("Bearer wrong-secret"));
    expect(response.status).toBe(401);
  });

  it("returns 500 when CRON_SECRET is not configured", async () => {
    delete process.env.CRON_SECRET;
    const response = await POST(makeRequest("Bearer anything"));
    expect(response.status).toBe(500);

    const body = await response.json();
    expect(body.error).toBe("CRON_SECRET not configured");
  });

  it("processes all 5 metrics with valid auth", async () => {
    // Mock SQL tagged template calls - each metric returns a result with count
    mockSql.mockImplementation(() => {
      const result: unknown[] = [];
      Object.defineProperty(result, "count", { value: 3 });
      return Promise.resolve(result);
    });

    const response = await POST(makeRequest("Bearer test-secret-123"));
    expect(response.status).toBe(200);

    const body = await response.json();
    expect(body.ok).toBe(true);
    expect(body.metrics_processed).toBe(5);
    expect(body.timestamp).toBeDefined();
  });

  it("returns 500 on database error", async () => {
    mockSql.mockRejectedValueOnce(new Error("DB connection failed"));

    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const response = await POST(makeRequest("Bearer test-secret-123"));

    expect(response.status).toBe(500);
    const body = await response.json();
    expect(body.error).toBe("Internal server error");

    consoleSpy.mockRestore();
  });
});
