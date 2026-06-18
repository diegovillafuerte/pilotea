import React from "react";

/**
 * The 6-digit OTP input used on the WhatsApp code-verification step. Renders a
 * row of cells reflecting `value`; a single transparent input over them handles
 * typing, paste and backspace. The active cell shows a brand-green outline.
 */
export function OtpInput({ value = "", onChange, length = 6, style, ...rest }) {
  const ref = React.useRef(null);
  const [focused, setFocused] = React.useState(false);
  const chars = value.padEnd(length).split("").slice(0, length);
  const activeIdx = Math.min(value.length, length - 1);

  return (
    <div
      onClick={() => ref.current && ref.current.focus()}
      style={{ position: "relative", display: "flex", gap: "8px", ...style }}
      {...rest}
    >
      {chars.map((c, i) => {
        const isActive = focused && i === activeIdx;
        const filled = c.trim() !== "";
        return (
          <div
            key={i}
            style={{
              flex: 1,
              height: "56px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              borderRadius: "12px",
              background: "var(--surface-card)",
              border: `1.5px solid ${isActive ? "var(--primary)" : filled ? "var(--border-strong)" : "var(--border)"}`,
              boxShadow: isActive ? "0 0 0 3px color-mix(in srgb, var(--primary) 22%, transparent)" : "none",
              fontFamily: "var(--font-numeric)",
              fontSize: "24px",
              fontWeight: "var(--weight-bold)",
              color: "var(--text-strong)",
              transition: "border-color 120ms ease, box-shadow 120ms ease",
            }}
          >
            {c.trim()}
          </div>
        );
      })}
      <input
        ref={ref}
        value={value}
        inputMode="numeric"
        maxLength={length}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={(e) => onChange && onChange(e.target.value.replace(/\D/g, "").slice(0, length))}
        style={{ position: "absolute", inset: 0, opacity: 0, cursor: "default", border: "none" }}
        aria-label="Código de verificación"
      />
    </div>
  );
}
