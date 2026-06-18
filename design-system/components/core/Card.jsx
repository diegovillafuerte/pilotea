import React from "react";

/**
 * The tonal card — the workhorse container. Cards are separated from the
 * background by a COLOUR STEP (surface → surface-card), never a shadow, in-app.
 * Pass `interactive` for a pressable card; `tone="accent"` tints it with the
 * brand green for nudges.
 */
export function Card({
  children,
  tone = "default",
  interactive = false,
  padding = "var(--card-padding)",
  onClick,
  style,
  ...rest
}) {
  const tones = {
    default: {
      background: "var(--surface-card)",
      color: "var(--text-body)",
      border: "1px solid transparent",
    },
    variant: {
      background: "var(--surface-variant)",
      color: "var(--text-body)",
      border: "1px solid transparent",
    },
    accent: {
      background: "color-mix(in srgb, var(--primary) 12%, transparent)",
      color: "var(--text-body)",
      border: "1px solid color-mix(in srgb, var(--primary) 45%, transparent)",
    },
  };

  return (
    <div
      onClick={onClick}
      role={interactive ? "button" : undefined}
      style={{
        borderRadius: "var(--radius-card)",
        padding,
        cursor: interactive ? "pointer" : "default",
        transition: "background 120ms ease",
        ...tones[tone],
        ...style,
      }}
      {...rest}
    >
      {children}
    </div>
  );
}
