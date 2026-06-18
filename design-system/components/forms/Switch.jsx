import React from "react";

/**
 * On/off switch. Brand-green when on; tonal track when off. Tall enough to tap.
 */
export function Switch({ checked = false, onChange, disabled = false, style, ...rest }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => !disabled && onChange && onChange(!checked)}
      style={{
        width: "48px",
        height: "28px",
        borderRadius: "999px",
        border: "none",
        padding: "3px",
        cursor: disabled ? "not-allowed" : "pointer",
        background: checked ? "var(--primary)" : "var(--surface-variant)",
        opacity: disabled ? 0.5 : 1,
        transition: "background 140ms ease",
        display: "flex",
        justifyContent: checked ? "flex-end" : "flex-start",
        alignItems: "center",
        ...style,
      }}
      {...rest}
    >
      <span
        style={{
          width: "22px",
          height: "22px",
          borderRadius: "999px",
          background: "#fff",
          boxShadow: "0 1px 3px rgba(2,6,23,.4)",
          transition: "transform 140ms ease",
        }}
      />
    </button>
  );
}
