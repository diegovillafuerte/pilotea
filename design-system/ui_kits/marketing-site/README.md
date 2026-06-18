# UI Kit — Kompara marketing site

A faithful recreation of the legacy Next.js landing page (`src/app/page.tsx`),
light theme, emerald-on-slate. Single scrollable page:

- **Nav** — full logo, "Iniciar sesión", "Comenzar gratis" CTA.
- **Hero** — availability pill, "Sabe cuánto **realmente** ganas", subcopy, two CTAs, and the weekly-summary card with the tus-ganancias-vs-promedio bar chart + three stat tiles.
- **Features** — six cards (veredicto, percentiles, mejora, privacidad, WhatsApp, IMSS/fiscal).
- **How it works** — three numbered steps + closing CTA.
- **Footer** — monogram + "Hecho en México".

Copy is updated to the current Android-first positioning (real-time verdict
reader) while keeping the original layout, type scale, rounded-2xl cards, and
soft shadows. Uses design-system tokens via `styles.css`; the bar chart SVG is
ported verbatim from the repo's `BarChart`.

> The website is the **legacy** surface — the product moved to the native
> Android app. Kept here as the canonical web brand reference.
