import React from "react";

const ICONS = {
  home: (p) => <svg {...p}><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.7V21h14V9.7" /></svg>,
  list: (p) => <svg {...p}><path d="M8 6h13" /><path d="M8 12h13" /><path d="M8 18h13" /><path d="M3.5 6h.01" /><path d="M3.5 12h.01" /><path d="M3.5 18h.01" /></svg>,
  play: (p) => <svg {...p}><path d="M7 4.5v15l13-7.5z" /></svg>,
  calendar: (p) => <svg {...p}><rect x="3.5" y="5" width="17" height="16" rx="2.5" /><path d="M3.5 9.5h17" /><path d="M8 3v4" /><path d="M16 3v4" /></svg>,
  settings: (p) => <svg {...p}><circle cx="12" cy="12" r="3.2" /><path d="M19.4 13.5a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 1 1-4 0v-.2A1.6 1.6 0 0 0 6.8 19l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3a2 2 0 1 1 0-4h.2A1.6 1.6 0 0 0 4.4 6.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3 1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 2.7 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8 1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.4 1z" /></svg>,
};

export const KOMPARA_TABS = [
  { key: "inicio", label: "Inicio", icon: "home" },
  { key: "comparar", label: "Comparar", icon: "list" },
  { key: "lector", label: "Lector", icon: "play" },
  { key: "fiscal", label: "Fiscal", icon: "calendar" },
  { key: "ajustes", label: "Ajustes", icon: "settings" },
];

/**
 * The app's bottom navigation: five uniform flat tabs (Inicio · Comparar ·
 * Lector · Fiscal · Ajustes). No raised centre button. The selected tab is
 * tinted in the brand primary; the bar sits on a tonal surface.
 */
export function BottomNav({ tabs = KOMPARA_TABS, current = "inicio", onSelect, style, ...rest }) {
  return (
    <nav
      style={{
        display: "flex",
        width: "100%",
        background: "var(--surface-card)",
        borderTop: "1px solid var(--border)",
        ...style,
      }}
      {...rest}
    >
      {tabs.map((t) => {
        const active = t.key === current;
        const Icon = ICONS[t.icon];
        const tint = active ? "var(--primary)" : "var(--text-muted)";
        return (
          <button
            key={t.key}
            type="button"
            onClick={() => onSelect && onSelect(t.key)}
            aria-current={active ? "page" : undefined}
            style={{
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
              color: tint,
            }}
          >
            <Icon
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              stroke={tint}
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <span style={{ fontSize: "var(--label-size)", fontWeight: "var(--weight-medium)", color: tint }}>
              {t.label}
            </span>
          </button>
        );
      })}
    </nav>
  );
}
