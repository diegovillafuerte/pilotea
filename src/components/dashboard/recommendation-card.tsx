"use client";

interface RecommendationCardProps {
  type: "positive" | "warning" | "actionable" | "info";
  message: string;
}

const TYPE_STYLES: Record<
  RecommendationCardProps["type"],
  { bg: string; border: string; icon: string }
> = {
  positive: {
    bg: "bg-emerald-50",
    border: "border-emerald-200",
    icon: "text-emerald-600",
  },
  warning: {
    bg: "bg-amber-50",
    border: "border-amber-200",
    icon: "text-amber-600",
  },
  actionable: {
    bg: "bg-blue-50",
    border: "border-blue-200",
    icon: "text-blue-600",
  },
  info: {
    bg: "bg-gray-50",
    border: "border-gray-200",
    icon: "text-gray-600",
  },
};

const TYPE_ICONS: Record<RecommendationCardProps["type"], string> = {
  positive: "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z", // check circle
  warning:
    "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z", // exclamation
  actionable:
    "M13 10V3L4 14h7v7l9-11h-7z", // lightning bolt
  info: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z", // info circle
};

/**
 * Actionable tip card with type-specific styling (positive/warning/info).
 */
export function RecommendationCard({ type, message }: RecommendationCardProps) {
  const styles = TYPE_STYLES[type];
  const iconPath = TYPE_ICONS[type];

  return (
    <div
      className={`flex items-start gap-3 rounded-xl border p-4 ${styles.bg} ${styles.border}`}
    >
      <svg
        className={`mt-0.5 h-5 w-5 flex-shrink-0 ${styles.icon}`}
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={2}
      >
        <path strokeLinecap="round" strokeLinejoin="round" d={iconPath} />
      </svg>
      <p className="text-sm text-gray-700">{message}</p>
    </div>
  );
}
