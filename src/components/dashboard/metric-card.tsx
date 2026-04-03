"use client";

import { PercentileBar } from "./percentile-bar";

interface MetricCardProps {
  /** Metric display name in Spanish */
  label: string;
  /** Formatted value string (e.g., "$48.85") */
  value: string | null;
  /** Display percentile 1-99, null if metric is unavailable */
  percentile: number | null;
  /** Explanation when value is null */
  nullReason?: string;
}

/**
 * Card displaying a single efficiency metric with its value,
 * percentile badge, and mini person-bar visualization.
 */
export function MetricCard({
  label,
  value,
  percentile,
  nullReason,
}: MetricCardProps) {
  const isAvailable = value != null && percentile != null;

  return (
    <div className="rounded-xl bg-white p-4 shadow-sm border border-gray-100">
      <div className="flex items-start justify-between">
        <span className="text-sm text-gray-500">{label}</span>
        {isAvailable && (
          <PercentileBadge percentile={percentile} />
        )}
      </div>

      {isAvailable ? (
        <>
          <p className="mt-1 text-2xl font-bold text-gray-900">{value}</p>
          <div className="mt-2">
            <PercentileBar percentile={percentile} />
          </div>
        </>
      ) : (
        <div className="mt-2">
          <p className="text-lg font-medium text-gray-400">No disponible</p>
          {nullReason && (
            <p className="mt-1 text-xs text-gray-400">{nullReason}</p>
          )}
        </div>
      )}
    </div>
  );
}

function PercentileBadge({ percentile }: { percentile: number }) {
  let bgColor = "bg-gray-100 text-gray-600";
  if (percentile >= 75) {
    bgColor = "bg-emerald-100 text-emerald-700";
  } else if (percentile >= 50) {
    bgColor = "bg-blue-100 text-blue-700";
  } else if (percentile >= 25) {
    bgColor = "bg-amber-100 text-amber-700";
  } else {
    bgColor = "bg-red-100 text-red-700";
  }

  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${bgColor}`}
    >
      Top {100 - percentile}%
    </span>
  );
}
