import React from "react";

const TYPES = {
  positive: { accent: "var(--verde)" },
  warning:  { accent: "var(--amarillo)" },
  info:     { accent: "var(--info-blue)" },
};

function Glyph({ type, color }) {
  const common = { width: 22, height: 22, viewBox: "0 0 24 24", fill: "none", stroke: color, strokeWidth: 2, strokeLinecap: "round", strokeLinejoin: "round" };
  if (type === "positive")
    return <svg {...common}><circle cx="12" cy="12" r="9" /><path d="M8.5 12.5l2.4 2.4 4.6-5" /></svg>;
  if (type === "warning")
    return <svg {...common}><path d="M12 3 2.5 20h19z" /><path d="M12 10v4" /><path d="M12 17.5h.01" /></svg>;
  return <svg {...common}><circle cx="12" cy="12" r="9" /><path d="M12 11v5" /><path d="M12 8h.01" /></svg>;
}

/**
 * One "Consejo" card — type-styled with the verdict palette drivers already
 * read: verde for praise, ámbar for a money-leak warning, azul for an actionable
 * tip. A leading icon + bold title + one-or-two-sentence body, on a tinted card.
 */
export function RecommendationCard({ type = "info", title, body, style, ...rest }) {
  const t = TYPES[type] || TYPES.info;
  return (
    <div
      style={{
        display: "flex",
        gap: "12px",
        alignItems: "flex-start",
        borderRadius: "var(--radius-card)",
        padding: "14px",
        background: `color-mix(in srgb, ${t.accent} 12%, transparent)`,
        border: `1px solid color-mix(in srgb, ${t.accent} 50%, transparent)`,
        ...style,
      }}
      {...rest}
    >
      <div style={{ flex: "none", marginTop: "1px" }}>
        <Glyph type={type} color={t.accent} />
      </div>
      <div>
        <div
          style={{
            fontSize: "var(--title-sm-size)",
            fontWeight: "var(--weight-bold)",
            lineHeight: "var(--title-sm-lh)",
            color: "var(--text-strong)",
          }}
        >
          {title}
        </div>
        <div
          style={{
            marginTop: "2px",
            fontSize: "var(--body-size)",
            lineHeight: "var(--body-lh)",
            color: "var(--text-muted)",
          }}
        >
          {body}
        </div>
      </div>
    </div>
  );
}
