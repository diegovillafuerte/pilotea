import React from "react";
import { Button } from "./Button.jsx";

/**
 * The empty / placeholder surface: a large brand-green icon, a title, a
 * supportive body and an optional primary CTA. Used whenever a screen has no
 * data yet ("Aún no hay números — activa el lector y maneja").
 */
export function EmptyState({
  icon = null,
  title,
  body,
  ctaText = null,
  onCta,
  style,
  ...rest
}) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        textAlign: "center",
        padding: "var(--space-6) var(--space-8)",
        ...style,
      }}
      {...rest}
    >
      {icon && (
        <div
          style={{
            width: "72px",
            height: "72px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "var(--primary)",
          }}
        >
          {icon}
        </div>
      )}
      <div
        style={{
          marginTop: "20px",
          fontSize: "var(--title-lg-size)",
          fontWeight: "var(--title-lg-weight)",
          lineHeight: "var(--title-lg-lh)",
          color: "var(--text-strong)",
        }}
      >
        {title}
      </div>
      {body && (
        <div
          style={{
            marginTop: "8px",
            maxWidth: "32ch",
            fontSize: "var(--body-lg-size)",
            lineHeight: "var(--body-lg-lh)",
            color: "var(--text-muted)",
          }}
        >
          {body}
        </div>
      )}
      {ctaText && (
        <div style={{ marginTop: "28px", width: "100%", maxWidth: "320px" }}>
          <Button variant="primary" fullWidth onClick={onCta}>
            {ctaText}
          </Button>
        </div>
      )}
    </div>
  );
}
