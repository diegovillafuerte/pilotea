import React from "react";

export interface DialogProps {
  open?: boolean;
  title?: string;
  children?: React.ReactNode;
  confirmText?: string;
  /** When set, renders a secondary text dismiss action. */
  cancelText?: string | null;
  onConfirm?: () => void;
  onCancel?: () => void;
  /** Tints the confirm action red (delete account, etc). */
  destructive?: boolean;
  style?: React.CSSProperties;
}

/**
 * Modal dialog over a scrim (prominent disclosure, confirmations).
 * Positioned within the nearest positioned ancestor — wrap your phone frame in
 * `position: relative`.
 */
export function Dialog(props: DialogProps): React.JSX.Element | null;
