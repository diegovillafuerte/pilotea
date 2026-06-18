import React from "react";

export interface EmptyStateProps {
  /** A 72px icon node (brand-green tinted). */
  icon?: React.ReactNode;
  title: string;
  body?: string;
  /** When set (with onCta), renders a primary CTA beneath the body. */
  ctaText?: string;
  onCta?: () => void;
  style?: React.CSSProperties;
}

/**
 * Empty / placeholder surface with optional primary CTA.
 */
export function EmptyState(props: EmptyStateProps): React.JSX.Element;
