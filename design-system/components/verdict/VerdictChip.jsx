import React from "react";

const BODY = {
  green:  { bg: "var(--verde)",    fg: "var(--on-verde)" },
  yellow: { bg: "var(--amarillo)", fg: "var(--on-amarillo)" },
  red:    { bg: "var(--rojo)",     fg: "var(--on-rojo)" },
};

/** The white "K" logomark, drawn inline so the chip is self-contained. */
function Logomark({ size = 15 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <path d="M14 10L14 38" stroke="#fff" strokeWidth="4.5" strokeLinecap="round" />
      <path d="M14 24L30 10" stroke="#fff" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M14 24L34 38" stroke="#fff" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="30" y="18" width="4" height="10" rx="2" fill="#fff" opacity="0.5" />
      <rect x="36" y="14" width="4" height="14" rx="2" fill="#fff" opacity="0.5" />
    </svg>
  );
}

/**
 * THE HERO. The floating verdict chip the driver sees over Uber/DiDi before
 * accepting an offer. A brand strip (Kompara "K" + wordmark, so a shared
 * screenshot self-brands) sits atop a verdict-coloured body. The body shows the
 * driver's preferred net rate BIG (the one-second read) with the other rate
 * under it as context. No verdict word — the colour already carries it.
 */
export function VerdictChip({
  level = "green",
  heroRate = "$9.20/km",
  secondaryRate = "$165/h",
  missingHint = null,
  style,
  ...rest
}) {
  const b = BODY[level] || BODY.green;
  return (
    <div
      style={{
        display: "inline-flex",
        flexDirection: "column",
        minWidth: "132px",
        width: "max-content",
        borderRadius: "var(--radius-chip)",
        overflow: "hidden",
        boxShadow: "var(--shadow-overlay)",
        fontFamily: "var(--font-sans)",
        ...style,
      }}
      {...rest}
    >
      {/* Brand strip */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "6px",
          padding: "5px 12px",
          background: "var(--emerald-600)",
        }}
      >
        <Logomark />
        <span style={{ color: "#fff", fontSize: "12px", fontWeight: "var(--weight-medium)" }}>
          Kompara
        </span>
      </div>
      {/* Verdict-coloured body */}
      <div style={{ background: b.bg, padding: "10px 14px" }}>
        <div style={{ color: b.fg, fontSize: "30px", fontWeight: "var(--weight-black)", lineHeight: 1.05 }}>
          {heroRate}
        </div>
        <div style={{ color: b.fg, fontSize: "18px", fontWeight: "var(--weight-semibold)", lineHeight: 1.2 }}>
          {secondaryRate}
        </div>
        {missingHint && (
          <div style={{ color: "#fff", fontSize: "10px", marginTop: "4px", opacity: 0.95 }}>
            {missingHint}
          </div>
        )}
      </div>
    </div>
  );
}
