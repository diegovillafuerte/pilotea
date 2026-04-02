import { db } from "@/lib/db";
import { drivers } from "@/lib/db/schema";
import { eq } from "drizzle-orm";

/**
 * Update a driver's last_upload_at timestamp to the current time.
 */
export async function updateLastUploadAt(driverId: string) {
  const [updated] = await db
    .update(drivers)
    .set({ lastUploadAt: new Date() })
    .where(eq(drivers.id, driverId))
    .returning();

  return updated;
}
