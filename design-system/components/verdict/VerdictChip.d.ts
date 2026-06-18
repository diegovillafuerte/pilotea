import React from "react";

export type VerdictLevel = "green" | "yellow" | "red";

/**
 * @startingPoint section="Verdict" subtitle="Floating real-time offer verdict chip" viewport="700x260"
 */
export interface VerdictChipProps {
  /** The verdict colour of the body. */
  level?: VerdictLevel;
  /** Preferred net rate, shown BIG (the one-second read), e.g. "$9.20/km". */
  heroRate?: string;
  /** Secondary net rate shown under the hero, e.g. "$165/h". */
  secondaryRate?: string;
  /** Optional one-line missing-data hint (e.g. "Sin distancia"). */
  missingHint?: string | null;
  style?: React.CSSProperties;
}

/**
 * The floating verdict chip shown over Uber/DiDi before the driver accepts an
 * offer — the product's hero. Brand strip + verdict-coloured body + big net rate.
 */
export function VerdictChip(props: VerdictChipProps): React.JSX.Element;
