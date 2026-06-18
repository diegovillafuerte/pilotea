import React from "react";

/**
 * The "Top X%" percentile pill on a metric card. For a higher-is-better metric a
 * 78th-percentile driver reads "Top 22%". Pass the already-computed `topPercent`.
 * The `locked` variant shows a neutral "Kompara Premium" pill in place of the
 * real number — the free driver sees the value of the gated feature, not the data.
 */
export function PercentileBadge({ topPercent = 22, locked = false, style, ...rest }) {
  if (locked) {
    return (
      <span
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "4px",
          padding: "4px 10px",
          borderRadius: "var(--radius-pill)",
          background: "var(--surface-variant)",
          color: "var(--text-muted)",
          fontFamily: "var(--font-sans)",
          fontSize: "var(--label-size)",
          fontWeight: "var(--weight-semibold)",
          lineHeight: 1,
          ...style,
        }}
        {...rest}
      >
        🔒 Kompara Premium
      </span>
    );
  }
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: "4px 10px",
        borderRadius: "var(--radius-pill)",
        background: "var(--primary)",
        color: "var(--on-primary)",
        fontFamily: "var(--font-sans)",
        fontSize: "var(--label-size)",
        fontWeight: "var(--weight-bold)",
        lineHeight: 1,
        ...style,
      }}
      {...rest}
    >
      Top {topPercent}%
    </span>
  );
}
