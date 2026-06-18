import React from "react";

export interface ChipProps {
  children?: React.ReactNode;
  /** Selected state — brand-green tonal fill + green text. */
  selected?: boolean;
  onClick?: () => void;
  style?: React.CSSProperties;
}

/**
 * Selectable filter / platform chip (Todas · Uber · DiDi · inDrive).
 */
export function Chip(props: ChipProps): React.JSX.Element;
