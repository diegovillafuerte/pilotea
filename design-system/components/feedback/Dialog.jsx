import React from "react";
import { Button } from "../core/Button.jsx";

/**
 * Modal dialog over a scrim — the prominent-disclosure consent ("Kompara leerá
 * tu pantalla"), delete confirmations, etc. Title + body (string or node) + up
 * to two actions. Render conditionally on `open`.
 */
export function Dialog({
  open = true,
  title,
  children,
  confirmText = "Continuar",
  cancelText = null,
  onConfirm,
  onCancel,
  destructive = false,
  style,
  ...rest
}) {
  if (!open) return null;
  return (
    <div
      onClick={onCancel}
      style={{
        position: "absolute",
        inset: 0,
        background: "rgba(2,6,23,.6)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "24px",
        zIndex: 50,
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: "100%",
          maxWidth: "360px",
          background: "var(--surface-card)",
          borderRadius: "16px",
          padding: "22px",
          boxShadow: "var(--shadow-lg)",
          fontFamily: "var(--font-sans)",
          ...style,
        }}
        {...rest}
      >
        {title && (
          <div style={{ fontSize: "var(--title-size)", fontWeight: "var(--weight-bold)", color: "var(--text-strong)" }}>
            {title}
          </div>
        )}
        <div style={{ marginTop: "10px", fontSize: "var(--body-size)", lineHeight: "var(--body-lh)", color: "var(--text-muted)" }}>
          {children}
        </div>
        <div style={{ marginTop: "20px", display: "flex", flexDirection: "column", gap: "8px" }}>
          <Button variant="primary" fullWidth onClick={onConfirm}
            style={destructive ? { background: "var(--rojo)" } : undefined}>
            {confirmText}
          </Button>
          {cancelText && (
            <Button variant="text" fullWidth onClick={onCancel}>{cancelText}</Button>
          )}
        </div>
      </div>
    </div>
  );
}
