import React from "react";

const TOTAL = 20;

/** A single person glyph (head + shoulders) in the given colour. */
function Person({ color }) {
  return (
    <svg width="14" height="28" viewBox="0 0 14 28" fill="none" aria-hidden="true" style={{ display: "block" }}>
      <circle cx="7" cy="6" r="4.4" fill={color} />
      <path d="M1.6 27c0-3.6 2.4-6.4 5.4-6.4s5.4 2.8 5.4 6.4z" fill={color} />
    </svg>
  );
}

/**
 * The "20 people" percentile visualization. Renders 20 little person glyphs;
 * the ones up to the driver's standing are filled in the brand green, the rest
 * are dimmed — so a driver sees at a glance "I'm ahead of ~N out of 20 drivers".
 * Position = round(displayPercentile / 100 * 20), clamped 1..20 (you're never
 * literally last, never more than the row).
 */
export function PercentileBar({
  displayPercentile = 78,
  highlightColor = "var(--primary)",
  style,
  ...rest
}) {
  const filled = Math.min(Math.max(Math.round((displayPercentile / 100) * TOTAL), 1), TOTAL);
  const dim = "color-mix(in srgb, " + (highlightColor === "var(--primary)" ? "var(--primary)" : highlightColor) + " 18%, transparent)";
  return (
    <div
      role="img"
      aria-label={`Estás por encima del ${displayPercentile}% de los conductores de tu ciudad`}
      style={{ display: "flex", gap: "2px", ...style }}
      {...rest}
    >
      {Array.from({ length: TOTAL }, (_, i) => (
        <Person key={i} color={i < filled ? highlightColor : dim} />
      ))}
    </div>
  );
}
