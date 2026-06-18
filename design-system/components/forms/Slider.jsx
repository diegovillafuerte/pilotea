import React from "react";

/**
 * A labelled slider for the semáforo threshold floors (verde / rojo, $/km or
 * $/h). A row of label + big bold value, then the track. Uses the native range
 * with `accent-color` set to the brand green.
 */
export function Slider({
  label,
  valueText,
  value = 0,
  min = 0,
  max = 100,
  step = 1,
  onChange,
  style,
  ...rest
}) {
  return (
    <div style={{ fontFamily: "var(--font-sans)", ...style }}>
      {(label || valueText != null) && (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          {label && <span style={{ fontSize: "var(--body-size)", color: "var(--text-muted)" }}>{label}</span>}
          {valueText != null && (
            <span style={{ fontFamily: "var(--font-numeric)", fontSize: "18px", fontWeight: "var(--weight-black)", color: "var(--text-strong)" }}>
              {valueText}
            </span>
          )}
        </div>
      )}
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange && onChange(Number(e.target.value))}
        style={{
          width: "100%",
          marginTop: "8px",
          accentColor: "var(--primary)",
          height: "24px",
          cursor: "pointer",
        }}
        {...rest}
      />
    </div>
  );
}
