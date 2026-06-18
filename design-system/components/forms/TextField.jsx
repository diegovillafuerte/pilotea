import React from "react";

/**
 * Text field. Floating-free label above a tonal input, dark-aware. Supports a
 * fixed `prefix` (e.g. "+52" for the WhatsApp number), a `hint`, and an `error`.
 */
export function TextField({
  label,
  value,
  onChange,
  placeholder,
  prefix = null,
  hint = null,
  error = null,
  type = "text",
  inputMode,
  maxLength,
  style,
  ...rest
}) {
  const borderColor = error ? "var(--rojo)" : "var(--border)";
  return (
    <label style={{ display: "block", fontFamily: "var(--font-sans)", ...style }}>
      {label && (
        <span style={{ display: "block", fontSize: "var(--label-size)", fontWeight: "var(--weight-medium)", color: "var(--text-muted)", marginBottom: "6px" }}>
          {label}
        </span>
      )}
      <span
        style={{
          display: "flex",
          alignItems: "center",
          gap: "8px",
          background: "var(--surface-card)",
          border: `1.5px solid ${borderColor}`,
          borderRadius: "12px",
          padding: "0 14px",
          height: "52px",
        }}
      >
        {prefix && <span style={{ color: "var(--text-muted)", fontSize: "16px", fontWeight: 600 }}>{prefix}</span>}
        <input
          type={type}
          inputMode={inputMode}
          maxLength={maxLength}
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange && onChange(e.target.value)}
          style={{
            flex: 1,
            border: "none",
            outline: "none",
            background: "transparent",
            color: "var(--text-body)",
            fontFamily: "var(--font-sans)",
            fontSize: "16px",
            minWidth: 0,
          }}
          {...rest}
        />
      </span>
      {(hint || error) && (
        <span style={{ display: "block", marginTop: "6px", fontSize: "var(--body-sm-size)", color: error ? "var(--rojo)" : "var(--text-muted)" }}>
          {error || hint}
        </span>
      )}
    </label>
  );
}
