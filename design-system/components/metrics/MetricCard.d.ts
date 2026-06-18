import React from "react";

/**
 * @startingPoint section="Metrics" subtitle="Glanceable metric tile" viewport="700x150"
 */
export interface MetricCardProps {
  /** Small uppercase label, e.g. "$ POR HORA". */
  label: string;
  /** Pre-formatted value string, e.g. "$165.00" or "1.7". */
  value: string;
  /** Optional trailing badge (a PercentileBadge / "Top X%" pill). */
  badge?: React.ReactNode;
  style?: React.CSSProperties;
}

/**
 * Label + big glanceable metric value + optional percentile badge.
 */
export function MetricCard(props: MetricCardProps): React.JSX.Element;
