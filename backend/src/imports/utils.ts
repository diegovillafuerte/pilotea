/**
 * Import-pipeline shared utilities.
 *
 * Ported from the legacy web app (src/lib/parsers/utils.ts). Two behaviours are
 * production-proven and reproduced verbatim:
 *   1. `getCurrentMonday()` + the `extraction.week_start ?? getCurrentMonday()`
 *      fallback in every parser — the fix from web git commit 8f347d7
 *      ("Fix week_start null crash — fallback to current Monday when Claude
 *      can't determine date"). Preserved exactly.
 *   2. `prepareFileForVision()` — sharp resize/auto-rotate/JPEG conversion that
 *      prevents Claude Vision "Could not process image" 400s (web commit
 *      afca36f1). sharp is **lazy-loaded** so the import pipeline can be unit
 *      tested without the native binary present.
 */

import type { ParsedMetrics } from "./types.js";
import { ALL_METRICS_FIELDS } from "./types.js";

const MAX_DIMENSION = 2048;

type VisionMediaType = "image/jpeg" | "image/png" | "image/webp" | "image/gif" | "application/pdf";

// sharp's type is only needed for the lazily-imported module; avoid a top-level
// `import sharp from "sharp"` so the native addon is never required at module
// load (tests inject normalization or never reach the image path). sharp ships
// as a CommonJS `export = sharp`, so the dynamic import's namespace IS the
// callable; under esModuleInterop it's also surfaced as `.default`.
type SharpFactory = typeof import("sharp");
let sharpFactory: SharpFactory | null = null;

async function loadSharp(): Promise<SharpFactory> {
  if (!sharpFactory) {
    const mod = (await import("sharp")) as { default?: SharpFactory } & SharpFactory;
    sharpFactory = mod.default ?? mod;
  }
  return sharpFactory;
}

/**
 * Prepare a file buffer for Claude Vision. PDFs are passed through as-is;
 * images are normalized via sharp (resize, convert to JPEG, auto-rotate)
 * to prevent "Could not process image" errors.
 *
 * Ported from the web app; sharp is dynamically imported (lazy) so the module
 * graph loads without the native addon.
 */
export async function prepareFileForVision(
  buffer: Buffer,
  mimeType: string,
): Promise<{ base64: string; mediaType: VisionMediaType }> {
  if (mimeType === "application/pdf") {
    return {
      base64: buffer.toString("base64"),
      mediaType: "application/pdf",
    };
  }

  // Image path: normalize with sharp
  const sharp = await loadSharp();
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
 *
 * Ported verbatim from the web app (the week_start null-crash fix, commit 8f347d7).
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
 *
 * Ported verbatim from the web app.
 */
export function calculateDataCompleteness(metrics: ParsedMetrics): number {
  const total = ALL_METRICS_FIELDS.length;
  const filled = ALL_METRICS_FIELDS.filter((field) => metrics[field] != null).length;
  return Math.round((filled / total) * 100) / 100;
}
