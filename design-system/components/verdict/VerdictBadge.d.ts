import React from "react";

export type VerdictLevel = "green" | "yellow" | "red";

/**
 * @startingPoint section="Verdict" subtitle="The semáforo verdict pill" viewport="700x120"
 */
export interface VerdictBadgeProps {
  /** verde = conviene · amarillo = regular · rojo = no conviene. */
  level?: VerdictLevel;
  /** Override the Spanish word (defaults to Verde / Amarillo / Rojo). */
  label?: string;
  style?: React.CSSProperties;
}

/**
 * Pill-shaped traffic-light verdict chip. Colour + word.
 */
export function VerdictBadge(props: VerdictBadgeProps): React.JSX.Element;
