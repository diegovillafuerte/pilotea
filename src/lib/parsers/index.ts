import type { Platform, UploadType, ParseInput, ParseResult } from "./types";

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
 * Currently, only the uber+pdf combination is recognized (but not yet implemented -- B-007).
 * All other combinations return clear "not available" errors in Spanish.
 */
export async function parseUpload(
  platform: Platform,
  uploadType: UploadType,
  _input: ParseInput,
): Promise<ParseResult> {
  if (platform === "uber" && uploadType === "pdf") {
    return notAvailable(
      "El parser de PDF de Uber aun no esta implementado. Estara disponible pronto.",
    );
  }

  if (platform === "uber" && uploadType === "screenshot") {
    return notAvailable(
      "El parser de capturas de Uber aun no esta disponible. Por favor sube el PDF semanal.",
    );
  }

  if (platform === "didi" && uploadType === "screenshot") {
    return notAvailable(
      "El parser de capturas de DiDi aun no esta disponible. Estara disponible pronto.",
    );
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
