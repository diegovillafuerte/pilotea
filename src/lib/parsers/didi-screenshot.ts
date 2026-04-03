import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";
import type { ParseInput, ParseResult, ParsedMetrics, DidiScreenshotExtraction } from "./types";
import { didiScreenshotExtractionSchema } from "./types";
import { calculateDataCompleteness, getImageMediaType, getCurrentMonday } from "./utils";

// ─── System prompt ────────────────────────────────────────────
const SYSTEM_PROMPT = `Eres un extractor de datos experto para capturas de pantalla de DiDi Driver.

Se te proporcionan 2 capturas de pantalla de la aplicacion DiDi Driver:

**Imagen 1 — Pantalla de ganancias:**
- Muestra las ganancias netas del periodo (lo que el conductor recibe)
- Ganancias brutas (tarifa total antes de descuentos)
- Numero total de viajes completados
- Desglose de metodos de pago: efectivo y tarjeta
- Impuestos retenidos
- Recompensas o bonificaciones

**Imagen 2 — Tablero/Dashboard:**
- Muestra metricas de eficiencia: ganancia por kilometro ($/km), ganancia por viaje ($/viaje), ganancia por hora ($/hora)
- Periodo de la semana (fecha de inicio)
- Puede mostrar resumen de viajes y ganancias

IMPORTANTE:
- DiDi NO reporta la comision de la plataforma de forma explicita. No inventes este dato.
- DiDi SI reporta $/km de forma nativa — es la unica plataforma que lo hace.
- Cruza los datos entre ambas imagenes: si las ganancias aparecen en ambas, verifica que coincidan.
- Si un dato aparece en una imagen pero no en la otra, usa el valor disponible.
- Todos los montos son en pesos mexicanos (MXN).
- El week_start DEBE ser la fecha del lunes de la semana en formato ISO (YYYY-MM-DD).
- Si solo puedes ver el periodo pero no la fecha exacta del lunes, haz tu mejor estimacion.
- NO inventes datos que no estan visibles en ninguna de las dos imagenes.

Responde UNICAMENTE con un objeto JSON valido con la siguiente estructura (sin texto adicional):

{
  "week_start": "YYYY-MM-DD",
  "net_earnings": number | null,
  "gross_earnings": number | null,
  "total_trips": number | null,
  "earnings_per_km": number | null,
  "earnings_per_trip": number | null,
  "earnings_per_hour": number | null,
  "cash_amount": number | null,
  "card_amount": number | null,
  "taxes": number | null,
  "rewards": number | null
}`;

const USER_PROMPT =
  "Extrae todos los datos visibles de estas 2 capturas de pantalla de DiDi Driver. La primera imagen es la pantalla de ganancias y la segunda es el tablero/dashboard. Cruza los datos entre ambas. Responde SOLO con JSON valido, sin texto adicional.";

// ─── Derived metrics ─────────────────────────────────────────
function deriveHoursOnline(extraction: DidiScreenshotExtraction): number | null {
  if (
    extraction.net_earnings != null &&
    extraction.earnings_per_hour != null &&
    extraction.earnings_per_hour > 0
  ) {
    return Math.round((extraction.net_earnings / extraction.earnings_per_hour) * 100) / 100;
  }
  return null;
}

function deriveTripsPerHour(
  totalTrips: number | null,
  hoursOnline: number | null,
): number | null {
  if (totalTrips != null && hoursOnline != null && hoursOnline > 0) {
    return Math.round((totalTrips / hoursOnline) * 100) / 100;
  }
  return null;
}

// ─── Main parser ──────────────────────────────────────────────
export async function parseDidiScreenshot(input: ParseInput): Promise<ParseResult> {
  if (input.files.length < 2) {
    return {
      success: false,
      metrics: null,
      raw_extraction: {},
      data_completeness: 0,
      error:
        "DiDi requiere 2 capturas de pantalla: la pantalla de ganancias y el tablero. Por favor sube ambas imagenes.",
    };
  }

  try {
    const mediaType = getImageMediaType(input.mimeType);

    const response = await callClaudeVision({
      systemPrompt: SYSTEM_PROMPT,
      inputs: [
        {
          data: input.files[0].toString("base64"),
          mediaType,
        },
        {
          data: input.files[1].toString("base64"),
          mediaType,
        },
      ],
      userPrompt: USER_PROMPT,
    });

    // Parse and validate the JSON response
    const rawJson = extractJsonFromResponse(response.text);
    const parseResult = didiScreenshotExtractionSchema.safeParse(rawJson);

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

    const extraction: DidiScreenshotExtraction = parseResult.data;

    // Derive hours from net_earnings / earnings_per_hour
    const hoursOnline = deriveHoursOnline(extraction);
    const tripsPerHour = deriveTripsPerHour(extraction.total_trips, hoursOnline);

    const metrics: ParsedMetrics = {
      week_start: extraction.week_start ?? getCurrentMonday(),
      net_earnings: extraction.net_earnings,
      gross_earnings: extraction.gross_earnings,
      total_trips: extraction.total_trips,
      earnings_per_km: extraction.earnings_per_km,
      earnings_per_trip: extraction.earnings_per_trip,
      earnings_per_hour: extraction.earnings_per_hour,
      cash_amount: extraction.cash_amount,
      card_amount: extraction.card_amount,
      taxes: extraction.taxes,
      rewards: extraction.rewards,
      // Derived
      hours_online: hoursOnline,
      trips_per_hour: tripsPerHour,
      // DiDi doesn't report these
      platform_commission: null,
      platform_commission_pct: null,
      // Not available from DiDi screenshots
      total_km: null,
      incentives: null,
      tips: null,
      surge_earnings: null,
      wait_time_earnings: null,
      active_days: null,
      peak_day_earnings: null,
      peak_day_name: null,
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
      error: `Error al procesar las capturas de DiDi: ${message}`,
    };
  }
}
