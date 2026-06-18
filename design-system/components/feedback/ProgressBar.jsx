import React from "react";

const TONES = {
  primary: "var(--primary)",
  verde: "var(--verde)",
  amarillo: "var(--amarillo)",
  rojo: "var(--rojo)",
};

/**
 * A thin rounded progress track + fill. Used for the weekly goal, data
 * completeness and the IMSS coverage bars. `value` is 0–1; `tone` picks the fill.
 */
export function ProgressBar({ value = 0, tone = "primary", height = 8, style, ...rest }) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(pct)}
      style={{
        width: "100%",
        height: `${height}px`,
        borderRadius: "999px",
        background: "var(--surface-variant)",
        overflow: "hidden",
        ...style,
      }}
      {...rest}
    >
      <div
        style={{
          width: `${pct}%`,
          height: "100%",
          borderRadius: "999px",
          background: TONES[tone] || TONES.primary,
          transition: "width 200ms ease",
        }}
      />
    </div>
  );
}
