import { NextResponse } from "next/server";
import { getDirectClient } from "@/lib/db";

const APP_VERSION = process.env.RENDER_GIT_COMMIT?.slice(0, 7) ?? "dev";

export async function GET() {
  const timestamp = new Date().toISOString();

  try {
    const sql = getDirectClient();
    const result = await sql`SELECT 1 AS ok`;
    const dbOk = result[0]?.ok === 1;

    return NextResponse.json({
      status: dbOk ? "ok" : "degraded",
      timestamp,
      version: APP_VERSION,
      db: dbOk ? "connected" : "unreachable",
    });
  } catch {
    return NextResponse.json(
      {
        status: "degraded",
        timestamp,
        version: APP_VERSION,
        db: "unreachable",
      },
      { status: 200 },
    );
  }
}
