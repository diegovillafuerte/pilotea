import React from "react";

/**
 * The workhorse of the stats surfaces: a small uppercase label, a big glanceable
 * value numeral, and an optional trailing badge (a percentile/"Top X%" pill).
 * The value uses tabular figures so "$1,234.56" reads from arm's length.
 */
export function MetricCard({ label, value, badge = null, style, ...rest }) {
  return (
    <div
      style={{
        background: "var(--surface-card)",
        borderRadius: "var(--radius-card)",
        padding: "var(--card-padding)",
        ...style,
      }}
      {...rest}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: "8px",
        }}
      >
        <span
          style={{
            fontSize: "var(--metric-label-size)",
            fontWeight: "var(--metric-label-weight)",
            letterSpacing: "0.04em",
            color: "var(--text-muted)",
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
        >
          {label}
        </span>
        {badge}
      </div>
      <div
        style={{
          marginTop: "4px",
          fontFamily: "var(--font-numeric)",
          fontVariantNumeric: "tabular-nums",
          fontSize: "var(--metric-value-size)",
          fontWeight: "var(--metric-value-weight)",
          lineHeight: "var(--metric-value-lh)",
          color: "var(--text-strong)",
        }}
      >
        {value}
      </div>
    </div>
  );
}
