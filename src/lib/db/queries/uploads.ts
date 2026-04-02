import { db } from "@/lib/db";
import { uploads } from "@/lib/db/schema";
import { eq, desc } from "drizzle-orm";

type UploadStatus = "pending" | "processing" | "parsed" | "failed";

interface CreateUploadData {
  driverId: string;
  platform: string;
  uploadType: string;
  fileKey: string;
  status?: UploadStatus;
}

/**
 * Create a new upload record.
 */
export async function createUpload(data: CreateUploadData) {
  const [upload] = await db
    .insert(uploads)
    .values({
      driverId: data.driverId,
      platform: data.platform,
      uploadType: data.uploadType,
      fileKey: data.fileKey,
      status: data.status ?? "pending",
    })
    .returning();

  return upload;
}

/**
 * Update an upload's status and optionally set an error message or parsed data.
 */
export async function updateUploadStatus(
  id: string,
  status: UploadStatus,
  errorMessage?: string,
  parsedData?: Record<string, unknown>,
) {
  const [updated] = await db
    .update(uploads)
    .set({
      status,
      ...(errorMessage !== undefined && { errorMessage }),
      ...(parsedData !== undefined && { parsedData }),
    })
    .where(eq(uploads.id, id))
    .returning();

  return updated;
}

/**
 * Link an upload to a weekly_data record.
 */
export async function linkUploadToWeeklyData(
  uploadId: string,
  weeklyDataId: string,
) {
  const [updated] = await db
    .update(uploads)
    .set({ weeklyDataId })
    .where(eq(uploads.id, uploadId))
    .returning();

  return updated;
}

/**
 * Get uploads for a driver, ordered by most recent first.
 */
export async function getUploadsByDriver(
  driverId: string,
  limit: number = 20,
  offset: number = 0,
) {
  return db
    .select()
    .from(uploads)
    .where(eq(uploads.driverId, driverId))
    .orderBy(desc(uploads.createdAt))
    .limit(limit)
    .offset(offset);
}
