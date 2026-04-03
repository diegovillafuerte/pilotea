"use client";

interface PercentileBarProps {
  /** Percentile value 1-99 (display_percentile, already inverted for commission) */
  percentile: number;
}

/**
 * Visual percentile indicator using 20 person icons.
 * The driver's position is highlighted based on their percentile ranking.
 * Each icon represents a 5-percentile bucket.
 */
export function PercentileBar({ percentile }: PercentileBarProps) {
  // Map percentile (1-99) to position (0-19) among 20 icons
  // Position 0 = lowest (p1-5), position 19 = highest (p96-100)
  const position = Math.min(19, Math.max(0, Math.floor(percentile / 5)));

  return (
    <div className="flex items-center gap-[2px]" aria-label={`Percentil ${percentile}`}>
      {Array.from({ length: 20 }, (_, i) => {
        const isDriver = i === position;
        return (
          <svg
            key={i}
            width="10"
            height="14"
            viewBox="0 0 10 14"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className={`flex-shrink-0 ${
              isDriver
                ? "text-emerald-500"
                : i < position
                  ? "text-gray-300"
                  : "text-gray-300"
            }`}
          >
            {/* Head */}
            <circle cx="5" cy="3" r="2.5" fill="currentColor" />
            {/* Body */}
            <path
              d="M1 13V10C1 8.34315 2.34315 7 4 7H6C7.65685 7 9 8.34315 9 10V13"
              fill="currentColor"
            />
          </svg>
        );
      })}
    </div>
  );
}
