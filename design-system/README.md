# Kompara — Design System

Earnings analytics and a real-time **trip-offer verdict** for ride-hailing
drivers in Mexico. A driver mounts their phone on the dash; when an Uber or DiDi
offer pops up, Kompara reads it and overlays an instant **semáforo** — verde /
amarillo / rojo + the net rate — so they know if the trip is worth it *before
they accept*. The reader and the live verdict are free; benchmarks, cross-platform
comparison, history and fiscal tracking are the paid layer.

This design system is the single source of truth for that product's look, voice,
and reusable parts — across the **native Android app** (the canonical, forward
surface) and the **marketing website** (legacy, kept as the web brand reference).

> **Brand note:** the product has been renamed several times (Ruleteo → Pilotea
> → **Kompara**). "Kompara" is current. Audience: Android-first drivers, 25–45,
> Mexico, prepaid data, WhatsApp-native, low-to-mid tech comfort.

## Source material

Built by reading the product's own repository — not screenshots. Explore it to
go deeper before building anything Kompara-branded:

- **GitHub:** https://github.com/diegovillafuerte/pilotea
  - Android (canonical UI): `android/ui/.../theme/{Color,Type,Theme}.kt`, `android/ui/.../components/*`, `android/ui/.../screens/*`, the overlay hero `android/overlay/.../VerdictChipUi.kt`, Spanish copy in `android/ui/src/main/res/values/strings.xml`.
  - Design law: `docs/design-principles.md`. Product/business context: `docs/project-context.md`. Web tokens: `src/app/globals.css`. Web landing: `src/app/page.tsx`.

---

## CONTENT FUNDAMENTALS

**Language.** Plain **es-MX**, always **`tú`** (never `usted`). Mexican driver
vernacular where natural ("el jale", "ruletear", "te conviene"), never corporate.

**Voice.** Short, concrete, money-first. Let the colour and the number do the
talking — cut any word the verdict colour or the figure already says. The
overlay chip dropped its verdict *word* entirely because the colour + net rate
were enough.

**No internal jargon.** Never "IPK / IPH" — say **"por kilómetro" / "por hora"**.
Speak in net pesos: "lo que te queda después de gasolina y desgaste".

**Casing.** Sentence case for almost everything. Metric labels are the one
UPPERCASE exception ("GANANCIA NETA ESTA SEMANA", "$ POR HORA"). Headings are
tracked tight.

**Trust is a theme, not a footnote.** Privacy is stated plainly and often:
"Todo se queda en tu teléfono", "Tus datos, tu control", "Kompara solo lee y
nunca actúa por ti".

**Numbers are pesos.** Currency as `$1,234.56` (es-MX grouping, tabular figures).
Standings as "Top 22%" or "Le ganas al 78% de los conductores de tu ciudad".

**Emoji.** Used *sparingly* and only functionally: 🔥 for a streak, 🔒 for a
locked/Premium feature, 🎉 for a goal reached. Never decorative, never platform
logos (legal posture: no Uber/DiDi/inDrive marks).

**Examples (verbatim brand copy):**
- Empty state: *"Aún no hay números — activa el lector y maneja, tus números aparecen solos."*
- Warning consejo: *"Dejaste ir buenos viajes — rechazaste 3 ofertas que sí te convenían, $420 que se te fueron."*
- Disclosure: *"Para mostrarte el semáforo sobre las ofertas de DiDi, Kompara captura tu pantalla y la analiza únicamente en tu teléfono."*
- Hero (web): *"Sabe cuánto **realmente** ganas."*

---

## VISUAL FOUNDATIONS

**Two themes, one palette.** The Android app is **dark by default** (drivers work
nights; easier on the eyes and battery on an always-on mount). The website is
light. Both draw from the same tokens — emerald primary + slate neutrals + the
verdict triple. Light is the `:root` default; add `class="theme-dark"` to flip.

**Colour.**
- **Primary — Emerald.** `#059669` (600) is the canonical brand green (logomark, dark-theme primary, chip brand strip); `#047857` (700) is the light-theme primary. Full 50–950 scale.
- **Neutrals — Slate.** 50–950. Dark surfaces: bg slate-900 → card slate-800 → variant slate-700. Light: bg slate-50 → card white → variant slate-200.
- **Verdict triple (the heart).** Verde `#1B8A3A`, Amarillo `#F2B705` (dark text on it), Rojo `#D32F2F`. **Reserve these three for verdicts only** — never decorative red/green/yellow elsewhere. Colour is *never the only signal*: it always pairs with a number or word.
- **Accents.** Amber `#F59E0B` (web CTAs, streak); info-blue `#2D77E0` (actionable tips, sits outside the triple).

**Type.** **Inter**, one family, the whole way down (variable, weights 400–900).
The numbers ARE the product, so the scale is built around glanceable figures:
hero metric 44px/Black, card metric 30px/Bold, uppercase metric-label 13px, then
a standard Material-style scale (headline 24–28, title 16–22, body 14–16, label
12). Money uses tabular figures.

**Spacing.** Strict 4-based scale (4·8·12·16·24). **16px is canonical** for screen
edge padding, section rhythm, and card interior. 8px for tight in-card groups.
Don't invent in-between values.

**Shape.** Buttons 14px · cards 12px · the floating chip 16px · the logomark "K"
tile 7px · badges/pills/progress fully round (999px). Website cards use 16px
(rounded-2xl).

**Elevation.** **In-app, cards are tonal, not shadowed** — surfaces separate by a
colour step (background → surface-card → surface-variant). Shadows are reserved
for the **website** (soft slate shadows on white cards) and the **floating
overlay chip** (it literally hovers over another app).

**Backgrounds.** App: flat dark slate, occasionally a subtle radial slate
gradient behind a phone frame. Web: white with a faint emerald-tinted gradient
wash on the hero only. No textures, no photographic full-bleeds, no busy
patterns. The data is the decoration — bar charts, the 20-person percentile row,
metric numerals.

**Cards.** Rounded rectangles, generous 16px padding. App cards: tonal fill, no
border, no shadow. Accent/nudge cards: brand-tinted fill (`primary @ 12%`) + a
`primary @ 45%` 1px border. Recommendation cards follow the same tint recipe with
the relevant verdict-palette accent.

**Hover / press.** Web buttons darken one emerald step on hover (`600 → 700`) and
lift their shadow slightly. App primary actions are full-width, tall (≥52px) for
a thumb on a mount. No bounce, no playful spring.

**Motion.** Restrained. Short fades/cross-fades between states; the overlay chip
simply appears/updates as offers change. No infinite decorative loops, no
parallax. The one-second cognition target rules everything — nothing should
distract a driving user.

**Borders.** 1px hairlines in `--border` (slate-300 light / slate-600 dark) for
dividers, outlined buttons, and "off" status dots. Outlined buttons use the
stronger neutral.

**Imagery.** Minimal and functional — the brand leans on data viz and the
logomark rather than photography. When imagery is needed, keep it neutral; never
reproduce platform UIs or logos.

---

## ICONOGRAPHY

**System: Material Symbols (Rounded)** — the Android app's native icon language.
24px, ~2px stroke, rounded joins/caps. The five nav destinations map to: Inicio
`home`, Comparar `list`, Lector `play`, Fiscal `calendar` (date_range), Ajustes
`settings`. Common action/status glyphs: `share`, `check_circle` (positive),
`warning` (amber), `info` (blue).

- **In production (Android):** `androidx.compose.material.icons` (filled/rounded).
- **In these HTML artifacts:** the components embed small inline Material-derived
  SVG glyphs so they're self-contained (no font dependency). For richer sets in a
  mock, link **Material Symbols Rounded** from the Google Fonts CDN — it matches
  the stroke/fill.
- **Custom glyphs:** the 20-person percentile row (head + shoulders) is drawn,
  and the brand "K" logomark (a K built from strokes + two bar-chart bars).
- **Emoji as icons:** only 🔥 streak, 🔒 locked, 🎉 goal — functional, never decorative.
- **Never** hand-draw illustrations or use platform logos.

---

## INDEX — what's in this system

**Foundations**
- `styles.css` — the single entry point consumers link (imports only).
- `tokens/` — `colors.css`, `typography.css`, `spacing.css`, `fonts.css`, `base.css`.
- `assets/` — logos (`logo-full`, `logo-full-white`, `logo-monogram`, `logo-monogram-white`, `icon`) + `fonts/inter.ttf`.
- `guidelines/` — 16 foundation specimen cards (Type, Colors, Spacing, Brand).

**Components** (`window.KomparaDesignSystem_722871`) — each has `.jsx` + `.d.ts` + `.prompt.md` + a card:
- `components/core/` — **Button** (4 tiers), **Card** (tonal), **Chip** (platform filter), **EmptyState**.
- `components/verdict/` — **VerdictBadge** (the semáforo pill), **VerdictChip** (the floating offer-verdict hero).
- `components/metrics/` — **MetricCard**, **PercentileBadge** (Top X% / locked), **PercentileBar** (20 people), **RecommendationCard** (consejos).
- `components/navigation/` — **BottomNav** (+ `KOMPARA_TABS`).
- `components/forms/` — **TextField** (with prefix/hint/error), **OtpInput** (6-digit code), **Switch**, **Slider** (threshold floors).
- `components/feedback/` — **Dialog** (prominent-disclosure modal), **ProgressBar** (goal/completeness/IMSS), **StatusChip** (coverage states).

**UI kits**
- `ui_kits/android-app/` — the canonical app. `index.html` (Inicio · Comparar · Lector with live overlay chip · Fiscal) and `onboarding.html` (full 8-step flow). Every other screen (Paywall · Tu Mes · Historial · Día · Tu semáforo · Costos · Ajustes · Cuenta · Simulador · Importar · Ayuda) lives as its own editable template under `templates/`.
- `ui_kits/marketing-site/` — the legacy landing page (hero, features, how-it-works).

**Templates** (consuming projects start a new design from these) — one per unique screen:
- `templates/app-screen/` — blank Kompara app-screen scaffold (frame + status bar + content slot + bottom nav).
- `templates/onboarding/` — the **full interactive** onboarding flow (8 steps, real state machine).
- `templates/inicio/` — Inicio dashboard. `templates/comparar/` — Comparar. `templates/lector/` — Lector + overlay chip. `templates/fiscal/` — IMSS coverage.
- `templates/paywall/` — Kompara Premium. `templates/tu-mes/` — Wrapped-style shareable card.
- `templates/historial/` — Historial. `templates/dia/` — Detalle del día.
- `templates/semaforo/` — Tu semáforo (live sliders). `templates/costos/` — Editor de costos.
- `templates/ajustes/` — Ajustes. `templates/cuenta/` — Tu cuenta.
- `templates/simulador/` — Simulador (live verdict chip). `templates/importar/` — Importar (4-step progress). `templates/ayuda/` — Ayuda (FAQ accordion).

**Guide**
- `README.md` (this file), `SKILL.md` (Agent-Skill front matter for Claude Code).
