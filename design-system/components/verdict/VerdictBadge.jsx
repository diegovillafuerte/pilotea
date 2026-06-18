import React from "react";

const VERDICT = {
  green:  { bg: "var(--verde)",    fg: "var(--on-verde)",    label: "Verde" },
  yellow: { bg: "var(--amarillo)", fg: "var(--on-amarillo)", label: "Amarillo" },
  red:    { bg: "var(--rojo)",     fg: "var(--on-rojo)",     label: "Rojo" },
};

/**
 * The traffic-light verdict pill — the signal the whole system speaks in. The
 * colour carries good/regular/bad; the Spanish word just names it. Reserve the
 * three verdict colours for verdicts only, never decoration.
 */
export function VerdictBadge({ level = "green", label, style, ...rest }) {
  const v = VERDICT[level] || VERDICT.green;
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: "6px 14px",
        borderRadius: "var(--radius-pill)",
        background: v.bg,
        color: v.fg,
        fontFamily: "var(--font-sans)",
        fontSize: "var(--body-size)",
        fontWeight: "var(--weight-bold)",
        lineHeight: 1,
        ...style,
      }}
      {...rest}
    >
      {label || v.label}
    </span>
  );
}
