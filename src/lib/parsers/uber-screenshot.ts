import { callClaudeVision, extractJsonFromResponse } from "@/lib/claude/client";
import type { ParseInput, ParseResult, ParsedMetrics, UberScreenshotExtraction } from "./types";
import { uberScreenshotExtractionSchema } from "./types";
import { calculateDataCompleteness, getImageMediaType } from "./utils";

// ─── System prompt ────────────────────────────────────────────
const SYSTEM_PROMPT = `Eres un extractor de datos experto para capturas de pantalla de Uber.

La imagen es una captura de pantalla de la aplicacion de Uber que muestra el "Desglose de la tarifa" (fare breakdown) como una grafica de pastel (pie chart).

La grafica de pastel tiene los siguientes segmentos de colores:
1. **Tu ganancia** — El monto neto que recibe el conductor (segmento mas grande, generalmente en verde o azul)
2. **Tarifa de servicio de Uber** — La comision que cobra Uber (generalmente muestra monto y porcentaje)
3. **Impuestos** — Impuestos retenidos
4. **Incentivos/Promociones** — Bonificaciones o incentivos (puede no estar presente)
5. **Propinas** — Propinas recibidas (puede aparecer por separado)

El total de la grafica representa las ganancias brutas (gross_earnings).

IMPORTANTE:
- Esta vista NO tiene informacion de: viajes completados, kilometros, horas en linea, dias activos, ganancias por dia, metodos de pago, recompensas, sobrecargos ni tiempo de espera.
- NO inventes datos que no estan en la imagen.
- Si un segmento no aparece en la grafica, usa null para ese campo.
- Todos los montos son en pesos mexicanos (MXN).
- El week_start DEBE ser la fecha del lunes de la semana en formato ISO (YYYY-MM-DD). Si puedes ver la semana o fecha en la captura, extrae esa fecha. Si no es visible, haz tu mejor estimacion basada en cualquier contexto disponible. NUNCA uses "unknown" ni texto que no sea una fecha valida.
- La comision de Uber como porcentaje: calcula platform_commission_pct como (comision / gross_earnings * 100) si puedes.

Responde UNICAMENTE con un objeto JSON valido con la siguiente estructura (sin texto adicional):

{
  "week_start": "YYYY-MM-DD",
  "net_earnings": number | null,
  "gross_earnings": number | null,
  "platform_commission": number | null,
  "platform_commission_pct": number | null,
  "taxes": number | null,
  "incentives": number | null,
  "tips": number | null
}`;

const USER_PROMPT =
  "Extrae todos los datos visibles de esta captura de pantalla del desglose de tarifa de Uber. Responde SOLO con JSON valido, sin texto adicional.";

// ─── Main parser ──────────────────────────────────────────────
export async function parseUberScreenshot(input: ParseInput): Promise<ParseResult> {
  if (input.files.length === 0) {
    return {
      success: false,
      metrics: null,
      raw_extraction: {},
      data_completeness: 0,
      error: "No se recibio ninguna imagen para procesar.",
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
      ],
      userPrompt: USER_PROMPT,
    });

    // Parse and validate the JSON response
    const rawJson = extractJsonFromResponse(response.text);
    const parseResult = uberScreenshotExtractionSchema.safeParse(rawJson);

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

    const extraction: UberScreenshotExtraction = parseResult.data;

    // Build metrics — most fields are null since screenshot only shows pie chart
    const metrics: ParsedMetrics = {
      week_start: extraction.week_start,
      net_earnings: extraction.net_earnings,
      gross_earnings: extraction.gross_earnings,
      platform_commission: extraction.platform_commission,
      platform_commission_pct: extraction.platform_commission_pct,
      taxes: extraction.taxes,
      incentives: extraction.incentives,
      tips: extraction.tips,
      // Not available from pie chart screenshot
      total_trips: null,
      hours_online: null,
      total_km: null,
      active_days: null,
      surge_earnings: null,
      wait_time_earnings: null,
      peak_day_earnings: null,
      peak_day_name: null,
      cash_amount: null,
      card_amount: null,
      rewards: null,
      // Derived — cannot calculate without trips/hours/km
      earnings_per_trip: null,
      earnings_per_km: null,
      earnings_per_hour: null,
      trips_per_hour: null,
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
      error: `Error al procesar la captura de Uber: ${message}`,
    };
  }
}
