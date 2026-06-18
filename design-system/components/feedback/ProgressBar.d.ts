import React from "react";

export type ProgressTone = "primary" | "verde" | "amarillo" | "rojo";

export interface ProgressBarProps {
  /** Fraction filled, 0–1. */
  value?: number;
  /** Fill colour. @default "primary" */
  tone?: ProgressTone;
  /** Track height in px. @default 8 */
  height?: number;
  style?: React.CSSProperties;
}

/**
 * Rounded progress bar (weekly goal, completeness, IMSS coverage).
 *
 * @startingPoint section="Feedback" subtitle="Progress / goal bar" viewport="700x90"
 */
export function ProgressBar(props: ProgressBarProps): React.JSX.Element;
