import sharp from "sharp";
import type { ParsedMetrics } from "./types";
import { ALL_METRICS_FIELDS } from "./types";

const MAX_DIMENSION = 2048;

/**
 * Normalize an image buffer for Claude Vision: validate, resize if too large,
 * and convert to JPEG. Returns base64 data and the correct media type.
 * This prevents "Could not process image" errors from corrupted, HEIC,
 * or oversized images.
 */
export async function normalizeImage(buffer: Buffer): Promise<{ base64: string; mediaType: "image/jpeg" }> {
  const img = sharp(buffer).rotate(); // auto-rotate based on EXIF
  const metadata = await img.metadata();

  let pipeline = img;
  if (metadata.width && metadata.height) {
    const longest = Math.max(metadata.width, metadata.height);
    if (longest > MAX_DIMENSION) {
      pipeline = pipeline.resize({
        width: metadata.width >= metadata.height ? MAX_DIMENSION : undefined,
        height: metadata.height > metadata.width ? MAX_DIMENSION : undefined,
        fit: "inside",
        withoutEnlargement: true,
      });
    }
  }

  const jpegBuffer = await pipeline.jpeg({ quality: 85 }).toBuffer();
  return {
    base64: jpegBuffer.toString("base64"),
    mediaType: "image/jpeg",
  };
}

/**
 * Returns the Monday of the current week as an ISO date string (YYYY-MM-DD).
 * Used as fallback when Claude cannot determine week_start from the screenshot.
 */
export function getCurrentMonday(): string {
  const now = new Date();
  const day = now.getDay(); // 0=Sun, 1=Mon, ...
  const diff = day === 0 ? 6 : day - 1; // days since Monday
  const monday = new Date(now);
  monday.setDate(now.getDate() - diff);
  return monday.toISOString().slice(0, 10);
}

/**
 * Calculate data completeness as a ratio of non-null metrics fields.
 * Shared across all parsers (Uber PDF, Uber screenshot, DiDi, InDrive).
 */
export function calculateDataCompleteness(metrics: ParsedMetrics): number {
  const total = ALL_METRICS_FIELDS.length;
  const filled = ALL_METRICS_FIELDS.filter((field) => metrics[field] != null).length;
  return Math.round((filled / total) * 100) / 100;
}

/**
 * Map a MIME type string to a valid Claude Vision image media type.
 * Falls back to "image/png" for unrecognized types.
 * Shared across screenshot parsers (Uber, DiDi, InDrive).
 */
export function getImageMediaType(mimeType: string): "image/jpeg" | "image/png" | "image/webp" | "image/gif" {
  const validTypes = ["image/jpeg", "image/png", "image/webp", "image/gif"] as const;
  const match = validTypes.find((t) => t === mimeType);
  return match ?? "image/png";
}
