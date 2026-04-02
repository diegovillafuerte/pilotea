import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { NextRequest } from "next/server";

// Mock the DB module
vi.mock("@/lib/db", () => ({
  db: {
    execute: vi.fn().mockResolvedValue([]),
  },
}));

vi.mock("@/lib/db/schema", () => ({
  populationStats: {},
}));

vi.mock("drizzle-orm", () => ({
  sql: Object.assign(
    (strings: TemplateStringsArray, ...values: unknown[]) => ({
      strings,
      values,
    }),
    {
      raw: (s: string) => s,
    },
  ),
  and: (...args: unknown[]) => args,
  eq: (a: unknown, b: unknown) => [a, b],
}));

describe("POST /api/cron/update-stats", () => {
  const originalEnv = process.env;

  beforeEach(() => {
    vi.resetModules();
    process.env = { ...originalEnv, CRON_SECRET: "test-secret-123" };
  });

  afterEach(() => {
    process.env = originalEnv;
  });

  async function importRoute() {
    return import("@/app/api/cron/update-stats/route");
  }

  it("returns 401 without authorization header", async () => {
    const { POST } = await importRoute();
    const request = new NextRequest("http://localhost/api/cron/update-stats", {
      method: "POST",
    });

    const response = await POST(request);
    expect(response.status).toBe(401);

    const body = await response.json();
    expect(body.error).toBe("Unauthorized");
  });

  it("returns 401 with wrong secret", async () => {
    const { POST } = await importRoute();
    const request = new NextRequest("http://localhost/api/cron/update-stats", {
      method: "POST",
      headers: { Authorization: "Bearer wrong-secret" },
    });

    const response = await POST(request);
    expect(response.status).toBe(401);
  });

  it("returns 500 when CRON_SECRET is not configured", async () => {
    delete process.env.CRON_SECRET;
    const { POST } = await importRoute();
    const request = new NextRequest("http://localhost/api/cron/update-stats", {
      method: "POST",
      headers: { Authorization: "Bearer anything" },
    });

    const response = await POST(request);
    expect(response.status).toBe(500);

    const body = await response.json();
    expect(body.error).toBe("CRON_SECRET not configured");
  });

  it("returns 200 with correct secret", async () => {
    const { POST } = await importRoute();
    const request = new NextRequest("http://localhost/api/cron/update-stats", {
      method: "POST",
      headers: { Authorization: "Bearer test-secret-123" },
    });

    const response = await POST(request);
    expect(response.status).toBe(200);

    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.timestamp).toBeDefined();
  });
});
