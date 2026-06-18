import React from "react";

export type CardTone = "default" | "variant" | "accent";

/**
 * @startingPoint section="Core" subtitle="Tonal card container" viewport="700x160"
 */
export interface CardProps {
  children?: React.ReactNode;
  /**
   * `default` = standard card surface; `variant` = one tonal step up (in-card
   * groupings); `accent` = brand-green tint + border for first-run nudges.
   * @default "default"
   */
  tone?: CardTone;
  /** Render as a pressable card. */
  interactive?: boolean;
  /** Interior padding (token or CSS length). @default var(--card-padding) */
  padding?: string;
  onClick?: () => void;
  style?: React.CSSProperties;
}

/**
 * Tonal container. In-app cards never use shadow — they separate by colour step.
 */
export function Card(props: CardProps): React.JSX.Element;
