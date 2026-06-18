import React from "react";

export interface SwitchProps {
  checked?: boolean;
  onChange?: (checked: boolean) => void;
  disabled?: boolean;
  style?: React.CSSProperties;
}

/** On/off switch, brand-green when on. */
export function Switch(props: SwitchProps): React.JSX.Element;
