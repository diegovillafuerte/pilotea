import type { Platform, UploadType, ParseInput, ParseResult } from "./types";
import { parseUberScreenshot } from "./uber-screenshot";
import { parseUberPdf } from "./uber-pdf";
import { parseDidiScreenshot } from "./didi-screenshot";

function notAvailable(error: string): ParseResult {
  return {
    success: false,
    metrics: null,
    raw_extraction: {},
    data_completeness: 0,
    error,
  };
}

/**
 * Router function: given a platform and upload type, dispatch to the correct parser.
 *
 * Implemented parsers:
 * - uber+screenshot → parseUberScreenshot (pie chart, ~0.40 completeness)
 * - uber+pdf → parseUberPdf (weekly report, ~0.85 completeness)
 * - didi+screenshot → parseDidiScreenshot (2 images, ~0.85 completeness)
 *
 * Not yet implemented:
 * - indrive+screenshot → future
 */
export async function parseUpload(
  platform: Platform,
  uploadType: UploadType,
  input: ParseInput,
): Promise<ParseResult> {
  if (platform === "uber" && uploadType === "pdf") {
    return parseUberPdf(input);
  }

  if (platform === "uber" && uploadType === "screenshot") {
    return parseUberScreenshot(input);
  }

  if (platform === "didi" && uploadType === "screenshot") {
    return parseDidiScreenshot(input);
  }

  if (platform === "indrive" && uploadType === "screenshot") {
    return notAvailable(
      "El parser de capturas de InDrive aun no esta disponible. Estara disponible pronto.",
    );
  }

  return notAvailable(
    `No hay parser disponible para ${platform} + ${uploadType}. Verifica la plataforma y tipo de archivo.`,
  );
}
