import React from "react";

const TONES = {
  success: { fg: "var(--verde)", bg: "color-mix(in srgb, var(--verde) 16%, transparent)" },
  warning: { fg: "var(--amber-600)", bg: "color-mix(in srgb, var(--amarillo) 18%, transparent)" },
  danger:  { fg: "var(--rojo)", bg: "color-mix(in srgb, var(--rojo) 15%, transparent)" },
  neutral: { fg: "var(--text-muted)", bg: "var(--surface-variant)" },
};

/**
 * A small status pill — "Cubierto este mes" (success), "En camino" (warning),
 * "No cubierto" (danger), "Importado" (neutral). Tonal tinted background with a
 * matching text colour.
 */
export function StatusChip({ children, tone = "neutral", style, ...rest }) {
  const t = TONES[tone] || TONES.neutral;
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "6px",
        padding: "4px 10px",
        borderRadius: "var(--radius-pill)",
        background: t.bg,
        color: t.fg,
        fontFamily: "var(--font-sans)",
        fontSize: "var(--label-size)",
        fontWeight: "var(--weight-semibold)",
        lineHeight: 1,
        whiteSpace: "nowrap",
        ...style,
      }}
      {...rest}
    >
      {children}
    </span>
  );
}
