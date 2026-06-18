import React from "react";

/**
 * A filter / platform chip. Selectable pill used for the "Todas · Uber · DiDi"
 * platform switcher and similar toggles. Selected = brand-green tonal fill +
 * green text; unselected = outlined.
 */
export function Chip({
  children,
  selected = false,
  onClick,
  style,
  ...rest
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        height: "32px",
        padding: "0 14px",
        borderRadius: "var(--radius-pill)",
        fontFamily: "var(--font-sans)",
        fontSize: "var(--label-size)",
        fontWeight: "var(--weight-medium)",
        lineHeight: 1,
        cursor: "pointer",
        transition: "background 120ms ease, border-color 120ms ease, color 120ms ease",
        background: selected
          ? "color-mix(in srgb, var(--primary) 16%, transparent)"
          : "transparent",
        color: selected ? "var(--primary)" : "var(--text-muted)",
        border: selected
          ? "1px solid color-mix(in srgb, var(--primary) 40%, transparent)"
          : "1px solid var(--border)",
        ...style,
      }}
      {...rest}
    >
      {children}
    </button>
  );
}
