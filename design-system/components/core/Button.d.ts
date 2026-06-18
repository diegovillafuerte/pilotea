import React from "react";

export type ButtonVariant = "primary" | "secondary" | "tonal" | "text";
export type ButtonSize = "md" | "sm";

/**
 * @startingPoint section="Core" subtitle="Brand button, four emphasis tiers" viewport="700x180"
 */
export interface ButtonProps {
  /** Button label / contents. */
  children?: React.ReactNode;
  /**
   * Emphasis tier. `primary` = the one filled green CTA per surface; `secondary`
   * = outlined alternative; `tonal` = soft filled action that belongs to a card;
   * `text` = low-stakes inline action.
   * @default "primary"
   */
  variant?: ButtonVariant;
  /** @default "md" */
  size?: ButtonSize;
  /** Stretch to the container width (full-width CTAs are the app default). */
  fullWidth?: boolean;
  disabled?: boolean;
  /** Optional leading icon node (inline SVG or icon font glyph). */
  icon?: React.ReactNode;
  onClick?: () => void;
  type?: "button" | "submit" | "reset";
  style?: React.CSSProperties;
}

/**
 * The Kompara button. At most one `primary` per surface; pick the tier by
 * importance, not by looks.
 */
export function Button(props: ButtonProps): React.JSX.Element;
