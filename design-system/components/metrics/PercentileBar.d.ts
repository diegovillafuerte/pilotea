import React from "react";

/**
 * @startingPoint section="Metrics" subtitle="20-person percentile bar" viewport="700x120"
 */
export interface PercentileBarProps {
  /** Driver's 1–99 display percentile (higher = better; already inverted upstream). */
  displayPercentile?: number;
  /** Fill colour for the highlighted glyphs. @default var(--primary) */
  highlightColor?: string;
  style?: React.CSSProperties;
}

/**
 * 20-person row visualizing where a driver stands vs. the population.
 */
export function PercentileBar(props: PercentileBarProps): React.JSX.Element;
