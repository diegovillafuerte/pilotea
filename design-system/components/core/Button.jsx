import React from "react";

/**
 * Kompara's button. Emphasis hierarchy, not decoration — exactly one `primary`
 * per surface; everything else is `secondary` (outlined), `tonal` (sits on a
 * card) or `text`. Tall by default (≥52px) for an easy tap with the phone on a
 * dashboard mount.
 */
export function Button({
  children,
  variant = "primary",
  size = "md",
  fullWidth = false,
  disabled = false,
  icon = null,
  onClick,
  type = "button",
  style,
  ...rest
}) {
  const heights = { md: "var(--tap-primary)", sm: "40px" };
  const pads = { md: "0 20px", sm: "0 14px" };

  const base = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    gap: "8px",
    width: fullWidth ? "100%" : "auto",
    minHeight: heights[size],
    padding: pads[size],
    borderRadius: "var(--radius-button)",
    fontFamily: "var(--font-sans)",
    fontSize: size === "sm" ? "14px" : "16px",
    fontWeight: "var(--weight-bold)",
    lineHeight: 1,
    letterSpacing: "-0.01em",
    cursor: disabled ? "not-allowed" : "pointer",
    border: "1.5px solid transparent",
    transition: "background 120ms ease, border-color 120ms ease, opacity 120ms ease, transform 60ms ease",
    opacity: disabled ? 0.45 : 1,
    whiteSpace: "nowrap",
  };

  const variants = {
    primary: {
      background: "var(--primary)",
      color: "var(--on-primary)",
    },
    secondary: {
      background: "transparent",
      color: "var(--primary)",
      borderColor: "var(--border-strong)",
    },
    tonal: {
      background: "var(--surface-variant)",
      color: "var(--primary)",
    },
    text: {
      background: "transparent",
      color: "var(--primary)",
      minHeight: size === "sm" ? "32px" : "40px",
      padding: "0 8px",
    },
  };

  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      style={{ ...base, ...variants[variant], ...style }}
      {...rest}
    >
      {icon}
      {children}
    </button>
  );
}
