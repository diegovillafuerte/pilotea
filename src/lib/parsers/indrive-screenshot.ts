import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";
import type { ParseInput, ParseResult, ParsedMetrics, IndriveScreenshotExtraction } from "./types";
import { indriveScreenshotExtractionSchema } from "./types";
import { calculateDataCompleteness, prepareFileForVision, getCurrentMonday } from "./utils";

// ─── System prompt ────────────────────────────────────────────
const SYSTEM_PROMPT = `Eres un extractor de datos experto para capturas de pantalla de InDrive.

Se te proporciona 1 captura de pantalla de la aplicacion InDrive para conductores.

**Pantalla de ganancias:**
- Muestra las ganancias netas del periodo (lo que el conductor recibe despues de la comision)
- Tarifas brutas (monto total antes de la comision de InDrive)
- Pago por servicio / comision de InDrive (service fee)
- Numero total de solicitudes/viajes completados
- Kilometraje total recorrido
- Ganancia por kilometro ($/km)

IMPORTANTE:
- InDrive NO reporta horas en linea. No inventes este dato.
- InDrive SI reporta el service fee (comision de la plataforma) de forma explicita.
- Las ganancias netas = tarifas brutas - service fee.
- Todos los montos son en pesos mexicanos (MXN).
- El week_start DEBE ser la fecha del lunes de la semana en formato ISO (YYYY-MM-DD).
- Si solo puedes ver el periodo pero no la fecha exacta del lunes, haz tu mejor estimacion.
- NO inventes datos que no estan visibles en la imagen.

Responde UNICAMENTE con un objeto JSON valido con la siguiente estructura (sin texto adicional):

{
  "week_start": "YYYY-MM-DD",
  "net_earnings": number | null,
  "gross_earnings": number | null,
  "total_trips": number | null,
  "total_km": number | null,
  "earnings_per_km": number | null,
  "service_fee": number | null
}`;

const USER_PROMPT =
  "Extrae todos los datos visibles de esta captura de pantalla de InDrive. Responde SOLO con JSON valido, sin texto adicional.";

// ─── Derived metrics ─────────────────────────────────────────
function deriveCommission(extraction: IndriveScreenshotExtraction): {
  platform_commission: number | null;
  platform_commission_pct: number | null;
} {
  // Use service_fee directly if available
  const commission = extraction.service_fee;

  // Derive percentage: (gross - net) / gross * 100
  if (
    extraction.gross_earnings != null &&
    extraction.net_earnings != null &&
    extraction.gross_earnings > 0
  ) {
    const pct =
      Math.round(
        ((extraction.gross_earnings - extraction.net_earnings) / extraction.gross_earnings) * 10000,
      ) / 100;
    return { platform_commission: commission, platform_commission_pct: pct };
  }

  return { platform_commission: commission, platform_commission_pct: null };
}

function deriveEarningsPerTrip(
  netEarnings: number | null,
  totalTrips: number | null,
): number | null {
  if (netEarnings != null && totalTrips != null && totalTrips > 0) {
    return Math.round((netEarnings / totalTrips) * 100) / 100;
  }
  return null;
}

// ─── Main parser ──────────────────────────────────────────────
export async function parseIndriveScreenshot(input: ParseInput): Promise<ParseResult> {
  if (input.files.length < 1) {
    return {
      success: false,
      metrics: null,
      raw_extraction: {},
      data_completeness: 0,
      error: "No se recibio ninguna imagen para procesar.",
    };
  }

  try {
    const file = await prepareFileForVision(input.files[0], input.mimeType);

    const response = await callClaudeVision({
      systemPrompt: SYSTEM_PROMPT,
      inputs: [
        {
          data: file.base64,
          mediaType: file.mediaType,
        },
      ],
      userPrompt: USER_PROMPT,
    });

    // Parse and validate the JSON response
    const rawJson = extractJsonFromResponse(response.text);
    const parseResult = indriveScreenshotExtractionSchema.safeParse(rawJson);

    if (!parseResult.success) {
      const errorDetails = parseResult.error.issues
        .map((issue) => `${issue.path.join(".")}: ${issue.message}`)
        .join("; ");
      return {
        success: false,
        metrics: null,
        raw_extraction: rawJson as Record<string, unknown>,
        data_completeness: 0,
        error: `La respuesta de Claude no tiene el formato esperado. Errores: ${errorDetails}`,
      };
    }

    const extraction: IndriveScreenshotExtraction = parseResult.data;

    // Derive commission from gross - net
    const { platform_commission, platform_commission_pct } = deriveCommission(extraction);
    const earningsPerTrip = deriveEarningsPerTrip(extraction.net_earnings, extraction.total_trips);

    const metrics: ParsedMetrics = {
      week_start: extraction.week_start ?? getCurrentMonday(),
      net_earnings: extraction.net_earnings,
      gross_earnings: extraction.gross_earnings,
      total_trips: extraction.total_trips,
      total_km: extraction.total_km,
      earnings_per_km: extraction.earnings_per_km,
      // Derived
      earnings_per_trip: earningsPerTrip,
      platform_commission,
      platform_commission_pct,
      // InDrive does NOT report hours — these are always null
      hours_online: null,
      earnings_per_hour: null,
      trips_per_hour: null,
      // Not available from InDrive screenshots
      taxes: null,
      incentives: null,
      tips: null,
      surge_earnings: null,
      wait_time_earnings: null,
      active_days: null,
      peak_day_earnings: null,
      peak_day_name: null,
      cash_amount: null,
      card_amount: null,
      rewards: null,
    };

    const dataCompleteness = calculateDataCompleteness(metrics);

    return {
      success: true,
      metrics,
      raw_extraction: extraction as unknown as Record<string, unknown>,
      data_completeness: dataCompleteness,
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : "Error desconocido";
    return {
      success: false,
      metrics: null,
      raw_extraction: {},
      data_completeness: 0,
      error: `Error al procesar la captura de InDrive: ${message}`,
    };
  }
}
