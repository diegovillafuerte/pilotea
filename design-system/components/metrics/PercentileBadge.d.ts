import React from "react";

export interface PercentileBadgeProps {
  /** The "Top X%" figure (1–99); already inverted upstream for the metric. */
  topPercent?: number;
  /** Locked variant: a neutral "Kompara Premium" pill hiding the real number. */
  locked?: boolean;
  style?: React.CSSProperties;
}

/**
 * "Top X%" percentile pill (brand green) with a locked Premium variant.
 */
export function PercentileBadge(props: PercentileBadgeProps): React.JSX.Element;
