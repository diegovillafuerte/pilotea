/**
 * Parser router: dispatch (platform, uploadType) to the correct parser. Ported
 * verbatim from the web app (src/lib/parsers/index.ts), including the Spanish
 * "no parser available" fallback. The Claude client is threaded through to each
 * parser so the whole pipeline stays injectable for tests.
 *
 * Implemented parsers:
 * - uber+pdf        → parseUberPdf (weekly report, ~0.95 completeness)
 * - uber+screenshot → parseUberScreenshot (pie chart, ~0.40 completeness)
 * - didi+screenshot → parseDidiScreenshot (2 images, ~0.85 completeness)
 * - indrive+screenshot → parseIndriveScreenshot (1 image, ~0.70 completeness)
 */

import type { VisionClient } from "../claude.js";
import type { Platform, UploadType, ParseInput, ParseResult } from "../types.js";
import { parseUberPdf } from "./uber-pdf.js";
import { parseUberScreenshot } from "./uber-screenshot.js";
import { parseDidiScreenshot } from "./didi-screenshot.js";
import { parseIndriveScreenshot } from "./indrive-screenshot.js";

function notAvailable(error: string): ParseResult {
  return {
    success: false,
    metrics: null,
    raw_extraction: {},
    data_completeness: 0,
    error,
  };
}

export async function parseUpload(
  platform: Platform,
  uploadType: UploadType,
  input: ParseInput,
  vision: VisionClient,
): Promise<ParseResult> {
  if (platform === "uber" && uploadType === "pdf") {
    return parseUberPdf(input, vision);
  }

  if (platform === "uber" && uploadType === "screenshot") {
    return parseUberScreenshot(input, vision);
  }

  if (platform === "didi" && uploadType === "screenshot") {
    return parseDidiScreenshot(input, vision);
  }

  if (platform === "indrive" && uploadType === "screenshot") {
    return parseIndriveScreenshot(input, vision);
  }

  return notAvailable(
    `No hay parser disponible para ${platform} + ${uploadType}. Verifica la plataforma y tipo de archivo.`,
  );
}
