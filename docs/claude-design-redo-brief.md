# Kompara — Claude Design redo brief

> Feedback from implementing the Kompara design-system mocks (claude.ai/design project `722871c2-5f17-40b1-a902-baca5f75b044`) into the **real Android Compose app**. Paste the relevant section into claude.ai/design and ask the design agent to redo that screen with the noted constraint. Engineer-side detail (file:line) lives in `docs/design-system-audit.md`.

## Why this exists

Most screens translated faithfully. This brief lists **(A)** screens not yet rebuilt to match the mock and why, and **(B)** places where the **mock itself should change** because it doesn't render cleanly on a real phone.

**Real-device constraints the mocks sometimes miss** (design against these):
- Phone is ~360–400dp wide. A 2-column grid cell is only ~140–170dp inside padding — **a label + a "Top X%" pill do not both fit on one line there.**
- Tables wider than ~4 columns clip on a phone; per-cell values like `$160/h` need room.
- Real **data states** exist that the happy-path mocks don't show — no-data/empty, loading, premium-locked, error. Design these if you want them on-brand.
- **Verdict colours (verde/amarillo/rojo) are verdicts ONLY** — never decorative or status colours (e.g. don't tint an "Activo" status verde — use brand emerald).
- Icons: production uses Material **Filled** only (no thin-outline set) — design with solid glyphs or expect a substitution.

---

## A. Screens NOT yet rebuilt to the mock (mocks are sound; need building / reconciling)

These got only token/colour swaps in code, not a structural rebuild — deliberately, to avoid regressing screens that couldn't be runtime-tested. A design pass to confirm intent is worthwhile:

1. **Tu Mes (34% faithful).** Mock = inline **Wrapped-style emerald-gradient hero card** (radius 20, white text, brand row "Kompara · Junio", big white net number, **2×2 brag grid**: Tu lugar / Mejor app / Mejor día / Racha), CTA "Compartir en WhatsApp". App today = a week data-**table** + a flat raster share-image, framed "Tu Semana", net drawn emerald-on-dark (**inverted** vs the mock). **Decide:** is this a *month* Wrapped card (mock) or the *week* table (app)? They need reconciling — and "Mejor app / Mejor día" data may not exist yet.

2. **Importar — upload state (42%).** Mock = a **card** with a 4-row **step checklist** (18px ✓ circles; pending rows dimmed) + a **linear progress bar** + duration hint, page header kept. App = a centered spinner + plain text. Rebuild the mock's checklist + linear-bar card.

3. **Paywall (52%).** Mock has an **emerald price card** (`Premium` / `$99` `/mes` + sub-line), a **fixed footer** (CTA + "Restaurar compras" + legal anchored to the bottom), a **close ✕**, and price **above** the CTA. App = plain scrolling list, price as a text line **below** the CTA, no price card / footer / ✕.

---

## B. Designs to REDO in Claude Design (they don't render cleanly on a phone)

1. **Inicio metric tiles — label + "Top X%" pill side-by-side does NOT fit** a half-width tile; the label (`$ POR VIAJE`) *and* the value clip. **Redo the tile** as **label (own line) → value → pill below it**, or full-width tiles, or much shorter labels. (We shipped the stacked version; confirm that's the intended design.) Also: the mock shows **4 tiles** but the app computes **5 metrics** — confirm 4 is intended (acceptance rate moves to Día/Tu mes).

2. **Metric label casing — keep them SHORT.** Mock source is sentence-case (`$ por hora`, `Viajes/hora`) rendered UPPERCASE via CSS, which makes long ones (`VIAJES POR HORA`) clip. Pick the canonical label set and keep it short (`VIAJES/HORA`, not `VIAJES POR HORA`).

3. **Comparar benchmark table — too dense for a phone.** 5 columns (Métrica / Tú / Uber / DiDi / Lugar) with per-platform values (`$160/h`, `$9.2/km`) clip in ~50dp cells. **Redo** as fewer columns, a stacked/grouped layout, or a horizontally-scrollable table — and consider dropping the `/h` `/km` suffixes inside cells. (Also: the app shows 6 metric rows vs the mock's 4 — align the set.)

4. **Onboarding step counter.** Mock shows `1/7 … 7/7` (signup split into phone/code/profile sub-pages). The app routes signup as **one** screen, so the counter is `n/5`. **Decide:** design signup as a single step (counter to 5), or specify the three sub-steps as real routed pages.

5. **Add the states the mocks omit** (if you want them on-brand): empty/no-data, loading, **premium-locked / paywall-gate**, error. Inicio, Comparar, Fiscal, Historial, Importar and Paywall all have these in code with no mock counterpart, so the design currently only covers the happy path.

---

## C. Cross-module / out of scope

- **Simulador (42%)** lives in a separate code module and was left as the app's existing, richer version (platform toggle + step-nav + threshold-playground), **not** the mock's 3-way segmented "Conviene / Regular / No conviene" verdict picker, and the chip is top-right not bottom-right. **Decide which paradigm wins** before redesigning.

---

## Smaller design nits (optional)

- **Lector** "Activo" status uses verdict-green → should be **brand emerald** (verdict colours are verdicts-only). Primary CTA in the mock is "Detener" (a stop action); the app shows start/restart depending on state — the mock only depicts the running state.
- **Día / Historial / Costos** rows: mock wants tonal **"listrow" cards**; the app renders some as plain rows / heavier metric cards — confirm the card treatment and row weight.
- **Cuenta / onboarding** fields: mock wants **label-above filled** fields (not floating-label); a DS `TextField` exists — confirm it's used everywhere.
- **Ayuda**: mock is a **single-open** accordion with a `+/–` glyph and the first item open; the app is multi-open with chevrons.
- **app-shell**: the app adds a brand **top bar** (logo + tab label) the mock shell doesn't show — decide keep-or-drop.

---

## How to use this

Open claude.ai/design project `722871c2-5f17-40b1-a902-baca5f75b044`, paste the relevant section, and ask the design agent to redo that screen with the constraint noted (especially the phone-width facts in **§B**). Aim for designs that render cleanly at ~393dp before they go back to engineering.

> Note: a live two-way sync (repo ⇄ claude.ai/design) via the `/design-sync` skill isn't possible for this project — it targets *web* component libraries (compiled JS `dist/` + Storybook), whereas Kompara's UI is native Android (Kotlin/Compose), and DesignSync needs an interactive `/login` this session can't grant. So this hand-off is a paste-in brief, not an automated push.
