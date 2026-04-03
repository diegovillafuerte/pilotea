import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";
import type { ParseInput, ParseResult, ParsedMetrics, UberPdfExtraction } from "./types";
import { uberPdfExtractionSchema } from "./types";
import { calculateDataCompleteness } from "./utils";

// ─── System prompt ────────────────────────────────────────────
const SYSTEM_PROMPT = `Eres un extractor de datos experto para reportes de ganancias semanales de Uber.

El documento es un PDF del reporte semanal de ganancias descargado de drivers.uber.com. Tiene las siguientes secciones:

1. **Encabezado**: Muestra el periodo de la semana (ej: "Semana del 24 al 30 de marzo de 2025")
2. **Resumen de ganancias**: Ganancias netas, ganancias brutas, viajes completados
3. **Desglose de la tarifa**: Muestra comision de Uber (monto y porcentaje), impuestos, propinas, incentivos
4. **Detalles de actividad**: Horas en linea, dias activos, ganancias por surcharge/sobrecargo
5. **Ganancias por dia**: Tabla con ganancias de cada dia de la semana, de donde se puede extraer el dia pico
6. **Propinas y extras**: Monto de propinas, tiempo de espera
7. **Metodos de pago**: Efectivo, tarjeta
8. **Recompensas/Promociones**: Bonificaciones o recompensas

IMPORTANTE:
- Uber NUNCA reporta kilometros recorridos. No inventes este dato.
- Todos los montos son en pesos mexicanos (MXN).
- Si un campo no esta presente en el documento, usa null.
- El week_start debe ser la fecha del lunes de esa semana en formato ISO (YYYY-MM-DD).

Responde UNICAMENTE con un objeto JSON valido con la siguiente estructura (sin texto adicional):

{
  "week_start": "YYYY-MM-DD",
  "net_earnings": number | null,
  "gross_earnings": number | null,
  "total_trips": number | null,
  "hours_online": number | null,
  "platform_commission": number | null,
  "platform_commission_pct": number | null,
  "taxes": number | null,
  "incentives": number | null,
  "tips": number | null,
  "surge_earnings": number | null,
  "wait_time_earnings": number | null,
  "active_days": number | null,
  "peak_day_earnings": number | null,
  "peak_day_name": string | null,
  "cash_amount": number | null,
  "card_amount": number | null,
  "rewards": number | null
}`;

const USER_PROMPT =
  "Extrae todos los datos de ganancias de este reporte semanal de Uber. Responde SOLO con JSON valido, sin texto adicional.";

// ─── Derived metrics ──────────────────────────────────────────
function calculateDerivedMetrics(
  extraction: UberPdfExtraction,
): Pick<ParsedMetrics, "earnings_per_trip" | "earnings_per_hour" | "trips_per_hour" | "earnings_per_km" | "total_km"> {
  const earningsPerTrip =
    extraction.net_earnings != null && extraction.total_trips != null && extraction.total_trips > 0
      ? Math.round((extraction.net_earnings / extraction.total_trips) * 100) / 100
      : null;

  const earningsPerHour =
    extraction.net_earnings != null && extraction.hours_online != null && extraction.hours_online > 0
      ? Math.round((extraction.net_earnings / extraction.hours_online) * 100) / 100
      : null;

  const tripsPerHour =
    extraction.total_trips != null && extraction.hours_online != null && extraction.hours_online > 0
      ? Math.round((extraction.total_trips / extraction.hours_online) * 100) / 100
      : null;

  return {
    earnings_per_trip: earningsPerTrip,
    earnings_per_hour: earningsPerHour,
    trips_per_hour: tripsPerHour,
    // Uber never reports km
    earnings_per_km: null,
    total_km: null,
  };
}

// ─── Main parser ──────────────────────────────────────────────
export async function parseUberPdf(input: ParseInput): Promise<ParseResult> {
  if (input.files.length === 0) {
    return {
      success: false,
      metrics: null,
      raw_extraction: {},
      data_completeness: 0,
      error: "No se recibio ningun archivo para procesar.",
    };
  }

  try {
    const response = await callClaudeVision({
      systemPrompt: SYSTEM_PROMPT,
      inputs: [
        {
          data: input.files[0].toString("base64"),
          mediaType: "application/pdf",
        },
      ],
      userPrompt: USER_PROMPT,
    });

    // Parse and validate the JSON response
    const rawJson = extractJsonFromResponse(response.text);
    const parseResult = uberPdfExtractionSchema.safeParse(rawJson);

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

    const extraction = parseResult.data;
    const derived = calculateDerivedMetrics(extraction);

    const metrics: ParsedMetrics = {
      week_start: extraction.week_start,
      net_earnings: extraction.net_earnings,
      gross_earnings: extraction.gross_earnings,
      total_trips: extraction.total_trips,
      hours_online: extraction.hours_online,
      platform_commission: extraction.platform_commission,
      platform_commission_pct: extraction.platform_commission_pct,
      taxes: extraction.taxes,
      incentives: extraction.incentives,
      tips: extraction.tips,
      surge_earnings: extraction.surge_earnings,
      wait_time_earnings: extraction.wait_time_earnings,
      active_days: extraction.active_days,
      peak_day_earnings: extraction.peak_day_earnings,
      peak_day_name: extraction.peak_day_name,
      cash_amount: extraction.cash_amount,
      card_amount: extraction.card_amount,
      rewards: extraction.rewards,
      // Derived
      earnings_per_trip: derived.earnings_per_trip,
      earnings_per_km: derived.earnings_per_km,
      earnings_per_hour: derived.earnings_per_hour,
      trips_per_hour: derived.trips_per_hour,
      total_km: derived.total_km,
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
      error: `Error al procesar el PDF de Uber: ${message}`,
    };
  }
}
