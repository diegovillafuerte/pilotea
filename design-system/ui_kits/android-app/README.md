# UI Kit — Kompara Android app

The canonical, forward-looking surface (the web MVP is legacy). A high-fidelity,
interactive recreation of the native Android app — **dark by default**, Inter
type, the semáforo verdict the whole product speaks in.

`index.html` is a click-through: the five-tab bottom bar switches between
**Inicio · Comparar · Lector · Fiscal · Ajustes**, and **Lector** runs the live
floating **verdict chip** cycling green → yellow → red over a mock incoming
offer (the product's hero gesture — "is this trip worth it before I accept?").

## Files
- `index.html` — the running app: Inicio · Comparar · Lector (live overlay chip) · Fiscal.
- `onboarding.html` — the full interactive onboarding flow (8 steps).

> Every individual screen (Paywall, Tu Mes, Historial, Día, Tu semáforo, Costos, Ajustes, Cuenta, Simulador, Importar, Ayuda) now lives as its own editable **template** under `templates/` — start there for single screens.

## Screens
- **Inicio** — weekly net hero number, streak, weekly-goal bar, cost nudge, platform chips, metric cards with Top X% pills, recommendation "Consejos".
- **Comparar** — branded shareable percentile hero (the 20-person bar) + the cross-platform benchmark table (Premium payoff).
- **Lector** — reader status + the live overlay verdict chip over a simulated host app.
- **Fiscal** — IMSS coverage progress per platform.

## Built from
Composed entirely from the design-system components (`Button`, `Card`, `Chip`,
`MetricCard`, `PercentileBadge`, `PercentileBar`, `RecommendationCard`,
`VerdictChip`, `BottomNav`) via `window.KomparaDesignSystem_722871`, on the `.theme-dark`
palette. Recreated from the repo's Jetpack Compose screens
(`InicioDashboardScreen.kt`, `CompararScreen.kt`, `LectorScreen.kt`,
`VerdictChipUi.kt`) — not from screenshots.

> Cosmetic recreation only — no real OCR, billing, or navigation stack.
