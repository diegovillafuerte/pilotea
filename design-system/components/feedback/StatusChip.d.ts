import React from "react";

export type StatusTone = "success" | "warning" | "danger" | "neutral";

export interface StatusChipProps {
  children?: React.ReactNode;
  /** @default "neutral" */
  tone?: StatusTone;
  style?: React.CSSProperties;
}

/**
 * Small tinted status pill (IMSS coverage state, import labels).
 *
 * @startingPoint section="Feedback" subtitle="Status pill" viewport="700x90"
 */
export function StatusChip(props: StatusChipProps): React.JSX.Element;
