/* @ds-bundle: {"format":3,"namespace":"KomparaDesignSystem_722871","components":[{"name":"Button","sourcePath":"components/core/Button.jsx"},{"name":"Card","sourcePath":"components/core/Card.jsx"},{"name":"Chip","sourcePath":"components/core/Chip.jsx"},{"name":"EmptyState","sourcePath":"components/core/EmptyState.jsx"},{"name":"Dialog","sourcePath":"components/feedback/Dialog.jsx"},{"name":"ProgressBar","sourcePath":"components/feedback/ProgressBar.jsx"},{"name":"StatusChip","sourcePath":"components/feedback/StatusChip.jsx"},{"name":"OtpInput","sourcePath":"components/forms/OtpInput.jsx"},{"name":"Slider","sourcePath":"components/forms/Slider.jsx"},{"name":"Switch","sourcePath":"components/forms/Switch.jsx"},{"name":"TextField","sourcePath":"components/forms/TextField.jsx"},{"name":"MetricCard","sourcePath":"components/metrics/MetricCard.jsx"},{"name":"PercentileBadge","sourcePath":"components/metrics/PercentileBadge.jsx"},{"name":"PercentileBar","sourcePath":"components/metrics/PercentileBar.jsx"},{"name":"RecommendationCard","sourcePath":"components/metrics/RecommendationCard.jsx"},{"name":"KOMPARA_TABS","sourcePath":"components/navigation/BottomNav.jsx"},{"name":"BottomNav","sourcePath":"components/navigation/BottomNav.jsx"},{"name":"VerdictBadge","sourcePath":"components/verdict/VerdictBadge.jsx"},{"name":"VerdictChip","sourcePath":"components/verdict/VerdictChip.jsx"}],"sourceHashes":{"components/core/Button.jsx":"b302e5699780","components/core/Card.jsx":"85a7883054ca","components/core/Chip.jsx":"153e608e1031","components/core/EmptyState.jsx":"9c1cfefd2730","components/feedback/Dialog.jsx":"9f3bca60dcad","components/feedback/ProgressBar.jsx":"04899fcfe34a","components/feedback/StatusChip.jsx":"155d397831e3","components/forms/OtpInput.jsx":"43346b5201f5","components/forms/Slider.jsx":"0e728fd2a449","components/forms/Switch.jsx":"87d56625eede","components/forms/TextField.jsx":"f45ec5d03400","components/metrics/MetricCard.jsx":"701b0ef28544","components/metrics/PercentileBadge.jsx":"37e61e37194b","components/metrics/PercentileBar.jsx":"5e9b6b547339","components/metrics/RecommendationCard.jsx":"cef3940ea0a6","components/navigation/BottomNav.jsx":"0e4712add8c5","components/verdict/VerdictBadge.jsx":"d60201813aad","components/verdict/VerdictChip.jsx":"acf74e186481"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.KomparaDesignSystem_722871 = window.KomparaDesignSystem_722871 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/core/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Kompara's button. Emphasis hierarchy, not decoration — exactly one `primary`
 * per surface; everything else is `secondary` (outlined), `tonal` (sits on a
 * card) or `text`. Tall by default (≥52px) for an easy tap with the phone on a
 * dashboard mount.
 */
function Button({
  children,
  variant = "primary",
  size = "md",
  fullWidth = false,
  disabled = false,
  icon = null,
  onClick,
  type = "button",
  style,
  ...rest
}) {
  const heights = {
    md: "var(--tap-primary)",
    sm: "40px"
  };
  const pads = {
    md: "0 20px",
    sm: "0 14px"
  };
  const base = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    gap: "8px",
    width: fullWidth ? "100%" : "auto",
    minHeight: heights[size],
    padding: pads[size],
    borderRadius: "var(--radius-button)",
    fontFamily: "var(--font-sans)",
    fontSize: size === "sm" ? "14px" : "16px",
    fontWeight: "var(--weight-bold)",
    lineHeight: 1,
    letterSpacing: "-0.01em",
    cursor: disabled ? "not-allowed" : "pointer",
    border: "1.5px solid transparent",
    transition: "background 120ms ease, border-color 120ms ease, opacity 120ms ease, transform 60ms ease",
    opacity: disabled ? 0.45 : 1,
    whiteSpace: "nowrap"
  };
  const variants = {
    primary: {
      background: "var(--primary)",
      color: "var(--on-primary)"
    },
    secondary: {
      background: "transparent",
      color: "var(--primary)",
      borderColor: "var(--border-strong)"
    },
    tonal: {
      background: "var(--surface-variant)",
      color: "var(--primary)"
    },
    text: {
      background: "transparent",
      color: "var(--primary)",
      minHeight: size === "sm" ? "32px" : "40px",
      padding: "0 8px"
    }
  };
  return /*#__PURE__*/React.createElement("button", _extends({
    type: type,
    disabled: disabled,
    onClick: onClick,
    style: {
      ...base,
      ...variants[variant],
      ...style
    }
  }, rest), icon, children);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Button.jsx", error: String((e && e.message) || e) }); }

// components/core/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * The tonal card — the workhorse container. Cards are separated from the
 * background by a COLOUR STEP (surface → surface-card), never a shadow, in-app.
 * Pass `interactive` for a pressable card; `tone="accent"` tints it with the
 * brand green for nudges.
 */
function Card({
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
      border: "1px solid transparent"
    },
    variant: {
      background: "var(--surface-variant)",
      color: "var(--text-body)",
      border: "1px solid transparent"
    },
    accent: {
      background: "color-mix(in srgb, var(--primary) 12%, transparent)",
      color: "var(--text-body)",
      border: "1px solid color-mix(in srgb, var(--primary) 45%, transparent)"
    }
  };
  return /*#__PURE__*/React.createElement("div", _extends({
    onClick: onClick,
    role: interactive ? "button" : undefined,
    style: {
      borderRadius: "var(--radius-card)",
      padding,
      cursor: interactive ? "pointer" : "default",
      transition: "background 120ms ease",
      ...tones[tone],
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Card.jsx", error: String((e && e.message) || e) }); }

// components/core/Chip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * A filter / platform chip. Selectable pill used for the "Todas · Uber · DiDi"
 * platform switcher and similar toggles. Selected = brand-green tonal fill +
 * green text; unselected = outlined.
 */
function Chip({
  children,
  selected = false,
  onClick,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    onClick: onClick,
    "aria-pressed": selected,
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: "6px",
      height: "32px",
      padding: "0 14px",
      borderRadius: "var(--radius-pill)",
      fontFamily: "var(--font-sans)",
      fontSize: "var(--label-size)",
      fontWeight: "var(--weight-medium)",
      lineHeight: 1,
      cursor: "pointer",
      transition: "background 120ms ease, border-color 120ms ease, color 120ms ease",
      background: selected ? "color-mix(in srgb, var(--primary) 16%, transparent)" : "transparent",
      color: selected ? "var(--primary)" : "var(--text-muted)",
      border: selected ? "1px solid color-mix(in srgb, var(--primary) 40%, transparent)" : "1px solid var(--border)",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Chip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Chip.jsx", error: String((e && e.message) || e) }); }

// components/core/EmptyState.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * The empty / placeholder surface: a large brand-green icon, a title, a
 * supportive body and an optional primary CTA. Used whenever a screen has no
 * data yet ("Aún no hay números — activa el lector y maneja").
 */
function EmptyState({
  icon = null,
  title,
  body,
  ctaText = null,
  onCta,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      textAlign: "center",
      padding: "var(--space-6) var(--space-8)",
      ...style
    }
  }, rest), icon && /*#__PURE__*/React.createElement("div", {
    style: {
      width: "72px",
      height: "72px",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      color: "var(--primary)"
    }
  }, icon), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "20px",
      fontSize: "var(--title-lg-size)",
      fontWeight: "var(--title-lg-weight)",
      lineHeight: "var(--title-lg-lh)",
      color: "var(--text-strong)"
    }
  }, title), body && /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "8px",
      maxWidth: "32ch",
      fontSize: "var(--body-lg-size)",
      lineHeight: "var(--body-lg-lh)",
      color: "var(--text-muted)"
    }
  }, body), ctaText && /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "28px",
      width: "100%",
      maxWidth: "320px"
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Button, {
    variant: "primary",
    fullWidth: true,
    onClick: onCta
  }, ctaText)));
}
Object.assign(__ds_scope, { EmptyState });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/EmptyState.jsx", error: String((e && e.message) || e) }); }

// components/feedback/Dialog.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Modal dialog over a scrim — the prominent-disclosure consent ("Kompara leerá
 * tu pantalla"), delete confirmations, etc. Title + body (string or node) + up
 * to two actions. Render conditionally on `open`.
 */
function Dialog({
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
  return /*#__PURE__*/React.createElement("div", {
    onClick: onCancel,
    style: {
      position: "absolute",
      inset: 0,
      background: "rgba(2,6,23,.6)",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: "24px",
      zIndex: 50
    }
  }, /*#__PURE__*/React.createElement("div", _extends({
    onClick: e => e.stopPropagation(),
    style: {
      width: "100%",
      maxWidth: "360px",
      background: "var(--surface-card)",
      borderRadius: "16px",
      padding: "22px",
      boxShadow: "var(--shadow-lg)",
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, rest), title && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: "var(--title-size)",
      fontWeight: "var(--weight-bold)",
      color: "var(--text-strong)"
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "10px",
      fontSize: "var(--body-size)",
      lineHeight: "var(--body-lh)",
      color: "var(--text-muted)"
    }
  }, children), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "20px",
      display: "flex",
      flexDirection: "column",
      gap: "8px"
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Button, {
    variant: "primary",
    fullWidth: true,
    onClick: onConfirm,
    style: destructive ? {
      background: "var(--rojo)"
    } : undefined
  }, confirmText), cancelText && /*#__PURE__*/React.createElement(__ds_scope.Button, {
    variant: "text",
    fullWidth: true,
    onClick: onCancel
  }, cancelText))));
}
Object.assign(__ds_scope, { Dialog });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/Dialog.jsx", error: String((e && e.message) || e) }); }

// components/feedback/ProgressBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const TONES = {
  primary: "var(--primary)",
  verde: "var(--verde)",
  amarillo: "var(--amarillo)",
  rojo: "var(--rojo)"
};

/**
 * A thin rounded progress track + fill. Used for the weekly goal, data
 * completeness and the IMSS coverage bars. `value` is 0–1; `tone` picks the fill.
 */
function ProgressBar({
  value = 0,
  tone = "primary",
  height = 8,
  style,
  ...rest
}) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  return /*#__PURE__*/React.createElement("div", _extends({
    role: "progressbar",
    "aria-valuenow": Math.round(pct),
    style: {
      width: "100%",
      height: `${height}px`,
      borderRadius: "999px",
      background: "var(--surface-variant)",
      overflow: "hidden",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      width: `${pct}%`,
      height: "100%",
      borderRadius: "999px",
      background: TONES[tone] || TONES.primary,
      transition: "width 200ms ease"
    }
  }));
}
Object.assign(__ds_scope, { ProgressBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/ProgressBar.jsx", error: String((e && e.message) || e) }); }

// components/feedback/StatusChip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const TONES = {
  success: {
    fg: "var(--verde)",
    bg: "color-mix(in srgb, var(--verde) 16%, transparent)"
  },
  warning: {
    fg: "var(--amber-600)",
    bg: "color-mix(in srgb, var(--amarillo) 18%, transparent)"
  },
  danger: {
    fg: "var(--rojo)",
    bg: "color-mix(in srgb, var(--rojo) 15%, transparent)"
  },
  neutral: {
    fg: "var(--text-muted)",
    bg: "var(--surface-variant)"
  }
};

/**
 * A small status pill — "Cubierto este mes" (success), "En camino" (warning),
 * "No cubierto" (danger), "Importado" (neutral). Tonal tinted background with a
 * matching text colour.
 */
function StatusChip({
  children,
  tone = "neutral",
  style,
  ...rest
}) {
  const t = TONES[tone] || TONES.neutral;
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: "inline-flex",
      alignItems: "center",
      gap: "6px",
      padding: "4px 10px",
      borderRadius: "var(--radius-pill)",
      background: t.bg,
      color: t.fg,
      fontFamily: "var(--font-sans)",
      fontSize: "var(--label-size)",
      fontWeight: "var(--weight-semibold)",
      lineHeight: 1,
      whiteSpace: "nowrap",
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { StatusChip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/StatusChip.jsx", error: String((e && e.message) || e) }); }

// components/forms/OtpInput.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * The 6-digit OTP input used on the WhatsApp code-verification step. Renders a
 * row of cells reflecting `value`; a single transparent input over them handles
 * typing, paste and backspace. The active cell shows a brand-green outline.
 */
function OtpInput({
  value = "",
  onChange,
  length = 6,
  style,
  ...rest
}) {
  const ref = React.useRef(null);
  const [focused, setFocused] = React.useState(false);
  const chars = value.padEnd(length).split("").slice(0, length);
  const activeIdx = Math.min(value.length, length - 1);
  return /*#__PURE__*/React.createElement("div", _extends({
    onClick: () => ref.current && ref.current.focus(),
    style: {
      position: "relative",
      display: "flex",
      gap: "8px",
      ...style
    }
  }, rest), chars.map((c, i) => {
    const isActive = focused && i === activeIdx;
    const filled = c.trim() !== "";
    return /*#__PURE__*/React.createElement("div", {
      key: i,
      style: {
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
        transition: "border-color 120ms ease, box-shadow 120ms ease"
      }
    }, c.trim());
  }), /*#__PURE__*/React.createElement("input", {
    ref: ref,
    value: value,
    inputMode: "numeric",
    maxLength: length,
    onFocus: () => setFocused(true),
    onBlur: () => setFocused(false),
    onChange: e => onChange && onChange(e.target.value.replace(/\D/g, "").slice(0, length)),
    style: {
      position: "absolute",
      inset: 0,
      opacity: 0,
      cursor: "default",
      border: "none"
    },
    "aria-label": "C\xF3digo de verificaci\xF3n"
  }));
}
Object.assign(__ds_scope, { OtpInput });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/OtpInput.jsx", error: String((e && e.message) || e) }); }

// components/forms/Slider.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * A labelled slider for the semáforo threshold floors (verde / rojo, $/km or
 * $/h). A row of label + big bold value, then the track. Uses the native range
 * with `accent-color` set to the brand green.
 */
function Slider({
  label,
  valueText,
  value = 0,
  min = 0,
  max = 100,
  step = 1,
  onChange,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, (label || valueText != null) && /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between"
    }
  }, label && /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--body-size)",
      color: "var(--text-muted)"
    }
  }, label), valueText != null && /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: "var(--font-numeric)",
      fontSize: "18px",
      fontWeight: "var(--weight-black)",
      color: "var(--text-strong)"
    }
  }, valueText)), /*#__PURE__*/React.createElement("input", _extends({
    type: "range",
    min: min,
    max: max,
    step: step,
    value: value,
    onChange: e => onChange && onChange(Number(e.target.value)),
    style: {
      width: "100%",
      marginTop: "8px",
      accentColor: "var(--primary)",
      height: "24px",
      cursor: "pointer"
    }
  }, rest)));
}
Object.assign(__ds_scope, { Slider });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Slider.jsx", error: String((e && e.message) || e) }); }

// components/forms/Switch.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * On/off switch. Brand-green when on; tonal track when off. Tall enough to tap.
 */
function Switch({
  checked = false,
  onChange,
  disabled = false,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    role: "switch",
    "aria-checked": checked,
    disabled: disabled,
    onClick: () => !disabled && onChange && onChange(!checked),
    style: {
      width: "48px",
      height: "28px",
      borderRadius: "999px",
      border: "none",
      padding: "3px",
      cursor: disabled ? "not-allowed" : "pointer",
      background: checked ? "var(--primary)" : "var(--surface-variant)",
      opacity: disabled ? 0.5 : 1,
      transition: "background 140ms ease",
      display: "flex",
      justifyContent: checked ? "flex-end" : "flex-start",
      alignItems: "center",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("span", {
    style: {
      width: "22px",
      height: "22px",
      borderRadius: "999px",
      background: "#fff",
      boxShadow: "0 1px 3px rgba(2,6,23,.4)",
      transition: "transform 140ms ease"
    }
  }));
}
Object.assign(__ds_scope, { Switch });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Switch.jsx", error: String((e && e.message) || e) }); }

// components/forms/TextField.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Text field. Floating-free label above a tonal input, dark-aware. Supports a
 * fixed `prefix` (e.g. "+52" for the WhatsApp number), a `hint`, and an `error`.
 */
function TextField({
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
  return /*#__PURE__*/React.createElement("label", {
    style: {
      display: "block",
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, label && /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      fontSize: "var(--label-size)",
      fontWeight: "var(--weight-medium)",
      color: "var(--text-muted)",
      marginBottom: "6px"
    }
  }, label), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "8px",
      background: "var(--surface-card)",
      border: `1.5px solid ${borderColor}`,
      borderRadius: "12px",
      padding: "0 14px",
      height: "52px"
    }
  }, prefix && /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--text-muted)",
      fontSize: "16px",
      fontWeight: 600
    }
  }, prefix), /*#__PURE__*/React.createElement("input", _extends({
    type: type,
    inputMode: inputMode,
    maxLength: maxLength,
    value: value,
    placeholder: placeholder,
    onChange: e => onChange && onChange(e.target.value),
    style: {
      flex: 1,
      border: "none",
      outline: "none",
      background: "transparent",
      color: "var(--text-body)",
      fontFamily: "var(--font-sans)",
      fontSize: "16px",
      minWidth: 0
    }
  }, rest))), (hint || error) && /*#__PURE__*/React.createElement("span", {
    style: {
      display: "block",
      marginTop: "6px",
      fontSize: "var(--body-sm-size)",
      color: error ? "var(--rojo)" : "var(--text-muted)"
    }
  }, error || hint));
}
Object.assign(__ds_scope, { TextField });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/TextField.jsx", error: String((e && e.message) || e) }); }

// components/metrics/MetricCard.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * The workhorse of the stats surfaces: a small uppercase label, a big glanceable
 * value numeral, and an optional trailing badge (a percentile/"Top X%" pill).
 * The value uses tabular figures so "$1,234.56" reads from arm's length.
 */
function MetricCard({
  label,
  value,
  badge = null,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      background: "var(--surface-card)",
      borderRadius: "var(--radius-card)",
      padding: "var(--card-padding)",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      gap: "8px"
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--metric-label-size)",
      fontWeight: "var(--metric-label-weight)",
      letterSpacing: "0.04em",
      color: "var(--text-muted)",
      whiteSpace: "nowrap",
      overflow: "hidden",
      textOverflow: "ellipsis"
    }
  }, label), badge), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "4px",
      fontFamily: "var(--font-numeric)",
      fontVariantNumeric: "tabular-nums",
      fontSize: "var(--metric-value-size)",
      fontWeight: "var(--metric-value-weight)",
      lineHeight: "var(--metric-value-lh)",
      color: "var(--text-strong)"
    }
  }, value));
}
Object.assign(__ds_scope, { MetricCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/metrics/MetricCard.jsx", error: String((e && e.message) || e) }); }

// components/metrics/PercentileBadge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * The "Top X%" percentile pill on a metric card. For a higher-is-better metric a
 * 78th-percentile driver reads "Top 22%". Pass the already-computed `topPercent`.
 * The `locked` variant shows a neutral "Kompara Premium" pill in place of the
 * real number — the free driver sees the value of the gated feature, not the data.
 */
function PercentileBadge({
  topPercent = 22,
  locked = false,
  style,
  ...rest
}) {
  if (locked) {
    return /*#__PURE__*/React.createElement("span", _extends({
      style: {
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
        ...style
      }
    }, rest), "\uD83D\uDD12 Kompara Premium");
  }
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
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
      ...style
    }
  }, rest), "Top ", topPercent, "%");
}
Object.assign(__ds_scope, { PercentileBadge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/metrics/PercentileBadge.jsx", error: String((e && e.message) || e) }); }

// components/metrics/PercentileBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const TOTAL = 20;

/** A single person glyph (head + shoulders) in the given colour. */
function Person({
  color
}) {
  return /*#__PURE__*/React.createElement("svg", {
    width: "14",
    height: "28",
    viewBox: "0 0 14 28",
    fill: "none",
    "aria-hidden": "true",
    style: {
      display: "block"
    }
  }, /*#__PURE__*/React.createElement("circle", {
    cx: "7",
    cy: "6",
    r: "4.4",
    fill: color
  }), /*#__PURE__*/React.createElement("path", {
    d: "M1.6 27c0-3.6 2.4-6.4 5.4-6.4s5.4 2.8 5.4 6.4z",
    fill: color
  }));
}

/**
 * The "20 people" percentile visualization. Renders 20 little person glyphs;
 * the ones up to the driver's standing are filled in the brand green, the rest
 * are dimmed — so a driver sees at a glance "I'm ahead of ~N out of 20 drivers".
 * Position = round(displayPercentile / 100 * 20), clamped 1..20 (you're never
 * literally last, never more than the row).
 */
function PercentileBar({
  displayPercentile = 78,
  highlightColor = "var(--primary)",
  style,
  ...rest
}) {
  const filled = Math.min(Math.max(Math.round(displayPercentile / 100 * TOTAL), 1), TOTAL);
  const dim = "color-mix(in srgb, " + (highlightColor === "var(--primary)" ? "var(--primary)" : highlightColor) + " 18%, transparent)";
  return /*#__PURE__*/React.createElement("div", _extends({
    role: "img",
    "aria-label": `Estás por encima del ${displayPercentile}% de los conductores de tu ciudad`,
    style: {
      display: "flex",
      gap: "2px",
      ...style
    }
  }, rest), Array.from({
    length: TOTAL
  }, (_, i) => /*#__PURE__*/React.createElement(Person, {
    key: i,
    color: i < filled ? highlightColor : dim
  })));
}
Object.assign(__ds_scope, { PercentileBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/metrics/PercentileBar.jsx", error: String((e && e.message) || e) }); }

// components/metrics/RecommendationCard.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const TYPES = {
  positive: {
    accent: "var(--verde)"
  },
  warning: {
    accent: "var(--amarillo)"
  },
  info: {
    accent: "var(--info-blue)"
  }
};
function Glyph({
  type,
  color
}) {
  const common = {
    width: 22,
    height: 22,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: color,
    strokeWidth: 2,
    strokeLinecap: "round",
    strokeLinejoin: "round"
  };
  if (type === "positive") return /*#__PURE__*/React.createElement("svg", common, /*#__PURE__*/React.createElement("circle", {
    cx: "12",
    cy: "12",
    r: "9"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M8.5 12.5l2.4 2.4 4.6-5"
  }));
  if (type === "warning") return /*#__PURE__*/React.createElement("svg", common, /*#__PURE__*/React.createElement("path", {
    d: "M12 3 2.5 20h19z"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M12 10v4"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M12 17.5h.01"
  }));
  return /*#__PURE__*/React.createElement("svg", common, /*#__PURE__*/React.createElement("circle", {
    cx: "12",
    cy: "12",
    r: "9"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M12 11v5"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M12 8h.01"
  }));
}

/**
 * One "Consejo" card — type-styled with the verdict palette drivers already
 * read: verde for praise, ámbar for a money-leak warning, azul for an actionable
 * tip. A leading icon + bold title + one-or-two-sentence body, on a tinted card.
 */
function RecommendationCard({
  type = "info",
  title,
  body,
  style,
  ...rest
}) {
  const t = TYPES[type] || TYPES.info;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: "flex",
      gap: "12px",
      alignItems: "flex-start",
      borderRadius: "var(--radius-card)",
      padding: "14px",
      background: `color-mix(in srgb, ${t.accent} 12%, transparent)`,
      border: `1px solid color-mix(in srgb, ${t.accent} 50%, transparent)`,
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: "none",
      marginTop: "1px"
    }
  }, /*#__PURE__*/React.createElement(Glyph, {
    type: type,
    color: t.accent
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: "var(--title-sm-size)",
      fontWeight: "var(--weight-bold)",
      lineHeight: "var(--title-sm-lh)",
      color: "var(--text-strong)"
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: "2px",
      fontSize: "var(--body-size)",
      lineHeight: "var(--body-lh)",
      color: "var(--text-muted)"
    }
  }, body)));
}
Object.assign(__ds_scope, { RecommendationCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/metrics/RecommendationCard.jsx", error: String((e && e.message) || e) }); }

// components/navigation/BottomNav.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const ICONS = {
  home: p => /*#__PURE__*/React.createElement("svg", p, /*#__PURE__*/React.createElement("path", {
    d: "M3 10.5 12 3l9 7.5"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M5 9.7V21h14V9.7"
  })),
  list: p => /*#__PURE__*/React.createElement("svg", p, /*#__PURE__*/React.createElement("path", {
    d: "M8 6h13"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M8 12h13"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M8 18h13"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M3.5 6h.01"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M3.5 12h.01"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M3.5 18h.01"
  })),
  play: p => /*#__PURE__*/React.createElement("svg", p, /*#__PURE__*/React.createElement("path", {
    d: "M7 4.5v15l13-7.5z"
  })),
  calendar: p => /*#__PURE__*/React.createElement("svg", p, /*#__PURE__*/React.createElement("rect", {
    x: "3.5",
    y: "5",
    width: "17",
    height: "16",
    rx: "2.5"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M3.5 9.5h17"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M8 3v4"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M16 3v4"
  })),
  settings: p => /*#__PURE__*/React.createElement("svg", p, /*#__PURE__*/React.createElement("circle", {
    cx: "12",
    cy: "12",
    r: "3.2"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M19.4 13.5a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.2A1.6 1.6 0 0 0 6.8 19l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3a2 2 0 1 1 0-4h.2A1.6 1.6 0 0 0 4.4 6.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3 1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8 1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.4 1z"
  }))
};
const KOMPARA_TABS = [{
  key: "inicio",
  label: "Inicio",
  icon: "home"
}, {
  key: "comparar",
  label: "Comparar",
  icon: "list"
}, {
  key: "lector",
  label: "Lector",
  icon: "play"
}, {
  key: "fiscal",
  label: "Fiscal",
  icon: "calendar"
}, {
  key: "ajustes",
  label: "Ajustes",
  icon: "settings"
}];

/**
 * The app's bottom navigation: five uniform flat tabs (Inicio · Comparar ·
 * Lector · Fiscal · Ajustes). No raised centre button. The selected tab is
 * tinted in the brand primary; the bar sits on a tonal surface.
 */
function BottomNav({
  tabs = KOMPARA_TABS,
  current = "inicio",
  onSelect,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("nav", _extends({
    style: {
      display: "flex",
      width: "100%",
      background: "var(--surface-card)",
      borderTop: "1px solid var(--border)",
      ...style
    }
  }, rest), tabs.map(t => {
    const active = t.key === current;
    const Icon = ICONS[t.icon];
    const tint = active ? "var(--primary)" : "var(--text-muted)";
    return /*#__PURE__*/React.createElement("button", {
      key: t.key,
      type: "button",
      onClick: () => onSelect && onSelect(t.key),
      "aria-current": active ? "page" : undefined,
      style: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "2px",
        height: "64px",
        padding: "8px 0",
        background: "transparent",
        border: "none",
        cursor: "pointer",
        color: tint
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      width: "24",
      height: "24",
      viewBox: "0 0 24 24",
      fill: "none",
      stroke: tint,
      strokeWidth: "2",
      strokeLinecap: "round",
      strokeLinejoin: "round"
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: "var(--label-size)",
        fontWeight: "var(--weight-medium)",
        color: tint
      }
    }, t.label));
  }));
}
Object.assign(__ds_scope, { KOMPARA_TABS, BottomNav });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/BottomNav.jsx", error: String((e && e.message) || e) }); }

// components/verdict/VerdictBadge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const VERDICT = {
  green: {
    bg: "var(--verde)",
    fg: "var(--on-verde)",
    label: "Verde"
  },
  yellow: {
    bg: "var(--amarillo)",
    fg: "var(--on-amarillo)",
    label: "Amarillo"
  },
  red: {
    bg: "var(--rojo)",
    fg: "var(--on-rojo)",
    label: "Rojo"
  }
};

/**
 * The traffic-light verdict pill — the signal the whole system speaks in. The
 * colour carries good/regular/bad; the Spanish word just names it. Reserve the
 * three verdict colours for verdicts only, never decoration.
 */
function VerdictBadge({
  level = "green",
  label,
  style,
  ...rest
}) {
  const v = VERDICT[level] || VERDICT.green;
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: "inline-flex",
      alignItems: "center",
      padding: "6px 14px",
      borderRadius: "var(--radius-pill)",
      background: v.bg,
      color: v.fg,
      fontFamily: "var(--font-sans)",
      fontSize: "var(--body-size)",
      fontWeight: "var(--weight-bold)",
      lineHeight: 1,
      ...style
    }
  }, rest), label || v.label);
}
Object.assign(__ds_scope, { VerdictBadge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/verdict/VerdictBadge.jsx", error: String((e && e.message) || e) }); }

// components/verdict/VerdictChip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const BODY = {
  green: {
    bg: "var(--verde)",
    fg: "var(--on-verde)"
  },
  yellow: {
    bg: "var(--amarillo)",
    fg: "var(--on-amarillo)"
  },
  red: {
    bg: "var(--rojo)",
    fg: "var(--on-rojo)"
  }
};

/** The white "K" logomark, drawn inline so the chip is self-contained. */
function Logomark({
  size = 15
}) {
  return /*#__PURE__*/React.createElement("svg", {
    width: size,
    height: size,
    viewBox: "0 0 48 48",
    fill: "none",
    "aria-hidden": "true"
  }, /*#__PURE__*/React.createElement("path", {
    d: "M14 10L14 38",
    stroke: "#fff",
    strokeWidth: "4.5",
    strokeLinecap: "round"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M14 24L30 10",
    stroke: "#fff",
    strokeWidth: "4.5",
    strokeLinecap: "round",
    strokeLinejoin: "round"
  }), /*#__PURE__*/React.createElement("path", {
    d: "M14 24L34 38",
    stroke: "#fff",
    strokeWidth: "4.5",
    strokeLinecap: "round",
    strokeLinejoin: "round"
  }), /*#__PURE__*/React.createElement("rect", {
    x: "30",
    y: "18",
    width: "4",
    height: "10",
    rx: "2",
    fill: "#fff",
    opacity: "0.5"
  }), /*#__PURE__*/React.createElement("rect", {
    x: "36",
    y: "14",
    width: "4",
    height: "14",
    rx: "2",
    fill: "#fff",
    opacity: "0.5"
  }));
}

/**
 * THE HERO. The floating verdict chip the driver sees over Uber/DiDi before
 * accepting an offer. A brand strip (Kompara "K" + wordmark, so a shared
 * screenshot self-brands) sits atop a verdict-coloured body. The body shows the
 * driver's preferred net rate BIG (the one-second read) with the other rate
 * under it as context. No verdict word — the colour already carries it.
 */
function VerdictChip({
  level = "green",
  heroRate = "$9.20/km",
  secondaryRate = "$165/h",
  missingHint = null,
  style,
  ...rest
}) {
  const b = BODY[level] || BODY.green;
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: "inline-flex",
      flexDirection: "column",
      minWidth: "132px",
      width: "max-content",
      borderRadius: "var(--radius-chip)",
      overflow: "hidden",
      boxShadow: "var(--shadow-overlay)",
      fontFamily: "var(--font-sans)",
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: "6px",
      padding: "5px 12px",
      background: "var(--emerald-600)"
    }
  }, /*#__PURE__*/React.createElement(Logomark, null), /*#__PURE__*/React.createElement("span", {
    style: {
      color: "#fff",
      fontSize: "12px",
      fontWeight: "var(--weight-medium)"
    }
  }, "Kompara")), /*#__PURE__*/React.createElement("div", {
    style: {
      background: b.bg,
      padding: "10px 14px"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      color: b.fg,
      fontSize: "30px",
      fontWeight: "var(--weight-black)",
      lineHeight: 1.05
    }
  }, heroRate), /*#__PURE__*/React.createElement("div", {
    style: {
      color: b.fg,
      fontSize: "18px",
      fontWeight: "var(--weight-semibold)",
      lineHeight: 1.2
    }
  }, secondaryRate), missingHint && /*#__PURE__*/React.createElement("div", {
    style: {
      color: "#fff",
      fontSize: "10px",
      marginTop: "4px",
      opacity: 0.95
    }
  }, missingHint)));
}
Object.assign(__ds_scope, { VerdictChip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/verdict/VerdictChip.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Button = __ds_scope.Button;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.Chip = __ds_scope.Chip;

__ds_ns.EmptyState = __ds_scope.EmptyState;

__ds_ns.Dialog = __ds_scope.Dialog;

__ds_ns.ProgressBar = __ds_scope.ProgressBar;

__ds_ns.StatusChip = __ds_scope.StatusChip;

__ds_ns.OtpInput = __ds_scope.OtpInput;

__ds_ns.Slider = __ds_scope.Slider;

__ds_ns.Switch = __ds_scope.Switch;

__ds_ns.TextField = __ds_scope.TextField;

__ds_ns.MetricCard = __ds_scope.MetricCard;

__ds_ns.PercentileBadge = __ds_scope.PercentileBadge;

__ds_ns.PercentileBar = __ds_scope.PercentileBar;

__ds_ns.RecommendationCard = __ds_scope.RecommendationCard;

__ds_ns.KOMPARA_TABS = __ds_scope.KOMPARA_TABS;

__ds_ns.BottomNav = __ds_scope.BottomNav;

__ds_ns.VerdictBadge = __ds_scope.VerdictBadge;

__ds_ns.VerdictChip = __ds_scope.VerdictChip;

})();
