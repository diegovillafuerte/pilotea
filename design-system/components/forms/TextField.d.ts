import React from "react";

export interface TextFieldProps {
  label?: string;
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  /** Fixed leading prefix, e.g. "+52". */
  prefix?: React.ReactNode;
  hint?: string;
  /** When set, the field renders in its error state and shows this message. */
  error?: string | null;
  type?: string;
  inputMode?: string;
  maxLength?: number;
  style?: React.CSSProperties;
}

/**
 * Labelled text input with optional prefix, hint and error state.
 *
 * @startingPoint section="Forms" subtitle="Text field with prefix / hint / error" viewport="700x150"
 */
export function TextField(props: TextFieldProps): React.JSX.Element;
