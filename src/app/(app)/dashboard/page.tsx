"use client";

import useSWR from "swr";
import { MetricCard } from "@/components/dashboard/metric-card";
import { RecommendationCard } from "@/components/dashboard/recommendation-card";

// ─── Types ────────────────────────────────────────────────────

interface DashboardResponse {
  driver: {
    name: string | null;
    city: string | null;
    tier: string;
    streak_weeks: number;
    platforms: string[];
  };
  current_week: {
    week_start: string;
    platforms: Record<
      string,
      {
        metrics: Record<string, number | null>;
        percentiles: Array<{
          metric: string;
          value: number;
          percentile: number;
          display_percentile: number;
          sample_size: number;
          is_national_fallback: boolean;
        }>;
        data_completeness: number;
      }
    >;
    totals: {
      net_earnings: number;
      total_trips: number;
    };
  } | null;
  recommendations: Array<{
    type: "positive" | "warning" | "actionable" | "info";
    message: string;
    priority: number;
  }>;
}

// ─── Helpers ──────────────────────────────────────────────────

const fetcher = (url: string) => fetch(url).then((r) => r.json());

const METRIC_LABELS: Record<string, string> = {
  earnings_per_trip: "$/viaje",
  earnings_per_km: "$/km",
  earnings_per_hour: "$/hora",
  trips_per_hour: "Viajes/hora",
  platform_commission_pct: "Comision %",
};

function formatCurrency(value: number): string {
  return `$${value.toLocaleString("es-MX", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatMetricValue(metric: string, value: number | null): string | null {
  if (value == null) return null;
  if (metric === "platform_commission_pct") return `${value.toFixed(1)}%`;
  if (metric === "trips_per_hour") return value.toFixed(2);
  return formatCurrency(value);
}

// ─── Component ────────────────────────────────────────────────

export default function DashboardPage() {
  const { data, error, isLoading } = useSWR<DashboardResponse>(
    "/api/dashboard",
    fetcher,
  );

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  if (error || !data) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center p-6">
        <p className="text-gray-500">Error al cargar el dashboard</p>
      </div>
    );
  }

  // Empty state: no uploads
  if (!data.current_week) {
    return <EmptyState name={data.driver.name} />;
  }

  const { current_week, driver, recommendations } = data;

  // Aggregate percentiles across platforms (use first platform for display)
  const platformKeys = Object.keys(current_week.platforms);
  const primaryPlatform = platformKeys[0];
  const primaryData = primaryPlatform
    ? current_week.platforms[primaryPlatform]
    : null;

  // Build a percentile lookup from all platforms combined
  const percentileMap = new Map<string, { display_percentile: number }>();
  if (primaryData) {
    for (const p of primaryData.percentiles) {
      percentileMap.set(p.metric, { display_percentile: p.display_percentile });
    }
  }

  // Check min data completeness across platforms
  const minCompleteness = Math.min(
    ...Object.values(current_week.platforms).map((p) => p.data_completeness),
  );

  return (
    <main className="mx-auto max-w-lg px-4 pt-6">
      {/* Header */}
      <div className="mb-6">
        <p className="text-sm text-gray-500">
          {driver.name ? `Hola, ${driver.name}` : "Hola"}
        </p>
        <div className="mt-1 flex items-baseline gap-3">
          <h1 className="text-3xl font-bold text-gray-900">
            {formatCurrency(current_week.totals.net_earnings)}
          </h1>
          {driver.streak_weeks > 1 && (
            <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-700">
              <svg className="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 20 20">
                <path d="M12.395 2.553a1 1 0 00-1.45-.385c-.345.23-.614.558-.822.88-.214.33-.403.713-.57 1.116-.334.804-.614 1.768-.84 2.734a31.365 31.365 0 00-.613 3.58 2.64 2.64 0 01-.945-1.067c-.328-.68-.398-1.534-.398-2.654A1 1 0 005.05 6.05 6.981 6.981 0 003 11a7 7 0 1011.95-4.95c-.592-.591-.98-.985-1.348-1.467-.363-.476-.724-1.063-1.207-2.03zM12.12 15.12A3 3 0 017 13s.879.5 2.5.5c0-1 .5-4 1.25-4.5.5 1 .786 1.293 1.371 1.879A2.99 2.99 0 0113 13a2.99 2.99 0 01-.879 2.121z" />
              </svg>
              {driver.streak_weeks} sem
            </span>
          )}
        </div>
        <p className="mt-1 text-sm text-gray-400">
          Ganancia neta esta semana &middot; {current_week.totals.total_trips} viajes
        </p>
        {platformKeys.length > 1 && (
          <div className="mt-2 flex gap-2">
            {platformKeys.map((p) => (
              <span
                key={p}
                className="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600 capitalize"
              >
                {p}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Data completeness warning */}
      {minCompleteness < 1 && (
        <div className="mb-4 rounded-lg bg-amber-50 border border-amber-200 px-4 py-3">
          <p className="text-xs text-amber-700">
            Datos {Math.round(minCompleteness * 100)}% completos. Sube mas archivos para mayor precision.
          </p>
        </div>
      )}

      {/* Metric cards */}
      <section className="mb-6">
        <h2 className="mb-3 text-sm font-semibold text-gray-700 uppercase tracking-wide">
          Metricas de eficiencia
        </h2>
        <div className="grid grid-cols-2 gap-3">
          {Object.entries(METRIC_LABELS).map(([key, label]) => {
            const value = primaryData?.metrics[key] ?? null;
            const pct = percentileMap.get(key);
            return (
              <MetricCard
                key={key}
                label={label}
                value={formatMetricValue(key, value)}
                percentile={pct?.display_percentile ?? null}
                nullReason="Datos insuficientes para calcular"
              />
            );
          })}
        </div>
      </section>

      {/* Recommendations */}
      {recommendations.length > 0 && (
        <section className="mb-6">
          <h2 className="mb-3 text-sm font-semibold text-gray-700 uppercase tracking-wide">
            Recomendaciones
          </h2>
          <div className="flex flex-col gap-3">
            {recommendations.map((rec, i) => (
              <RecommendationCard
                key={i}
                type={rec.type}
                message={rec.message}
              />
            ))}
          </div>
        </section>
      )}
    </main>
  );
}

// ─── Sub-components ───────────────────────────────────────────

function EmptyState({ name }: { name: string | null }) {
  return (
    <main className="flex min-h-[80vh] flex-col items-center justify-center px-6 text-center">
      <div className="mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-gray-100">
        <svg
          className="h-10 w-10 text-gray-400"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={1.5}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5m-13.5-9L12 3m0 0l4.5 4.5M12 3v13.5"
          />
        </svg>
      </div>
      <h2 className="text-xl font-bold text-gray-900">
        {name ? `Hola, ${name}` : "Bienvenido"}
      </h2>
      <p className="mt-2 text-gray-500">
        Sube tus datos para ver tu dashboard
      </p>
      <a
        href="/upload"
        className="mt-6 inline-flex items-center rounded-full bg-emerald-500 px-6 py-3 text-sm font-semibold text-white shadow-sm hover:bg-emerald-600"
      >
        Subir datos
      </a>
    </main>
  );
}

function DashboardSkeleton() {
  return (
    <main className="mx-auto max-w-lg px-4 pt-6">
      {/* Header skeleton */}
      <div className="mb-6">
        <div className="h-4 w-24 animate-pulse rounded bg-gray-200" />
        <div className="mt-2 h-8 w-40 animate-pulse rounded bg-gray-200" />
        <div className="mt-2 h-4 w-48 animate-pulse rounded bg-gray-200" />
      </div>
      {/* Cards skeleton */}
      <div className="grid grid-cols-2 gap-3">
        {Array.from({ length: 5 }, (_, i) => (
          <div
            key={i}
            className="h-28 animate-pulse rounded-xl bg-gray-200"
          />
        ))}
      </div>
    </main>
  );
}
