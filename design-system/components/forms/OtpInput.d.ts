import React from "react";

export interface OtpInputProps {
  /** Current digits entered. */
  value?: string;
  onChange?: (value: string) => void;
  /** Number of cells. @default 6 */
  length?: number;
  style?: React.CSSProperties;
}

/**
 * 6-digit one-time-code input (WhatsApp verification).
 *
 * @startingPoint section="Forms" subtitle="6-digit OTP code input" viewport="700x120"
 */
export function OtpInput(props: OtpInputProps): React.JSX.Element;
