import React from "react";

export type RecommendationType = "positive" | "warning" | "info";

/**
 * @startingPoint section="Metrics" subtitle="Actionable recommendation card" viewport="700x140"
 */
export interface RecommendationCardProps {
  /** verde praise · ámbar money-leak warning · azul actionable tip. */
  type?: RecommendationType;
  title: string;
  body: string;
  style?: React.CSSProperties;
}

/**
 * A "Consejo" card — tinted accent card with icon, bold title and body.
 */
export function RecommendationCard(props: RecommendationCardProps): React.JSX.Element;
