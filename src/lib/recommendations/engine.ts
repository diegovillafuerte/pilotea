import type { ParsedMetrics, PercentileResult } from "@/lib/percentiles/engine";

// ─── Types ────────────────────────────────────────────────────

export interface Recommendation {
  type: "positive" | "warning" | "actionable" | "info";
  message: string;
  priority: number; // 1 = highest
}

interface DriverContext {
  streak_weeks: number;
  tier: string;
}

// ─── Constants ────────────────────────────────────────────────

const MAX_RECOMMENDATIONS = 3;

// ─── Engine ───────────────────────────────────────────────────

/**
 * Generate personalized recommendations based on a driver's metrics,
 * percentile rankings, and optional cross-platform data.
 *
 * Rules are evaluated in priority order; max 3 recommendations returned.
 */
export function generateRecommendations(
  metrics: ParsedMetrics,
  percentiles: PercentileResult[],
  crossPlatform?: Record<string, ParsedMetrics>,
  driver?: DriverContext,
): Recommendation[] {
  const recommendations: Recommendation[] = [];

  const pctMap = new Map(
    percentiles.map((p) => [p.metric, p]),
  );

  // Rule: Streak celebration
  if (driver && driver.streak_weeks >= 4) {
    recommendations.push({
      type: "positive",
      message: `Llevas ${driver.streak_weeks} semanas consecutivas subiendo datos!`,
      priority: 3,
    });
  }

  // Rule: High earnings per hour (top 25%)
  const ephPct = pctMap.get("earnings_per_hour");
  if (ephPct && ephPct.display_percentile >= 75) {
    recommendations.push({
      type: "positive",
      message: `Tu ingreso por hora esta en el top ${100 - ephPct.display_percentile}% de conductores en tu ciudad.`,
      priority: 2,
    });
  }

  // Rule: Low earnings per trip (below p25)
  const eptPct = pctMap.get("earnings_per_trip");
  if (eptPct && eptPct.display_percentile < 25) {
    recommendations.push({
      type: "actionable",
      message:
        "Tu ingreso por viaje esta bajo. Considera rechazar viajes cortos en horas de alta demanda.",
      priority: 1,
    });
  }

  // Rule: High commission warning
  const commPct = pctMap.get("platform_commission_pct");
  if (
    commPct &&
    commPct.display_percentile < 30 &&
    metrics.platform_commission_pct != null
  ) {
    recommendations.push({
      type: "warning",
      message: `Tu comision esta semana fue ${metrics.platform_commission_pct.toFixed(1)}%. Revisa si tuviste viajes cancelados.`,
      priority: 1,
    });
  }

  // Rule: Cross-platform comparison
  if (crossPlatform) {
    const platforms = Object.keys(crossPlatform);
    if (platforms.length >= 2) {
      const ephByPlatform = platforms
        .filter((p) => crossPlatform[p].earnings_per_hour != null)
        .map((p) => ({
          platform: p,
          value: crossPlatform[p].earnings_per_hour!,
        }));

      if (ephByPlatform.length >= 2) {
        ephByPlatform.sort((a, b) => b.value - a.value);
        const best = ephByPlatform[0];
        const worst = ephByPlatform[ephByPlatform.length - 1];
        const diff = ((best.value - worst.value) / worst.value) * 100;

        if (diff > 15) {
          const bestName =
            best.platform.charAt(0).toUpperCase() + best.platform.slice(1);
          const worstName =
            worst.platform.charAt(0).toUpperCase() + worst.platform.slice(1);
          recommendations.push({
            type: "actionable",
            message: `${bestName} te paga ${Math.round(diff)}% mas por hora que ${worstName}. Priorizalo en horas pico.`,
            priority: 1,
          });
        }
      }
    }
  }

  // Rule: Low data completeness
  // (This is checked by the caller since it's per-platform in the dashboard)

  // Sort by priority (lower number = higher priority) and limit
  recommendations.sort((a, b) => a.priority - b.priority);
  return recommendations.slice(0, MAX_RECOMMENDATIONS);
}
