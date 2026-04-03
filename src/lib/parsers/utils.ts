import type { ParsedMetrics } from "./types";
import { ALL_METRICS_FIELDS } from "./types";

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
