import React from "react";

export interface SliderProps {
  label?: string;
  /** Pre-formatted value display, e.g. "$9.2/km". */
  valueText?: string;
  value?: number;
  min?: number;
  max?: number;
  step?: number;
  onChange?: (value: number) => void;
  style?: React.CSSProperties;
}

/**
 * Labelled range slider for the semáforo threshold floors.
 *
 * @startingPoint section="Forms" subtitle="Threshold floor slider" viewport="700x120"
 */
export function Slider(props: SliderProps): React.JSX.Element;
