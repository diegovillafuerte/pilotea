# Kompara design principles

The visual language for the Android app. The goal is cohesion: a driver should feel the same hand
across Lector, Inicio, Comparar, Ajustes, and the overlay. **When building UI, reach for an existing
component before inventing one** — the recipes below point at the canonical ones. New tokens or
components are a deliberate decision, not a default.

This doc describes what the code *already does* (tokens are pulled from `theme/Color.kt`, `Type.kt`,
`Theme.kt`) plus the rules that keep it consistent. Where today's screens disagree, the
"reconcile" notes say which way is canonical.

---

## 1. Principles (driver-first)

The user is driving. Every screen is read in glances, often on a windshield mount, often at night.

- **Glanceable first.** The one number that matters is biggest; everything else is support. The
  overlay chip's net rate is 30sp; a dashboard hero is `metricValueLarge` (44sp).
- **Colour carries meaning.** The semáforo (verde/amarillo/rojo) *is* the verdict — the overlay chip
  shows colour + the net number and **no verdict word** (the word was redundant noise). Reserve the
  three verdict colours for verdicts; don't use red/green/yellow decoratively elsewhere.
- **One primary action per surface.** At most one filled green button per screen. Everything else is
  secondary (outlined) or tertiary (text). If two things look equally important, neither reads as the
  action.
- **Plain es-MX, `tú`.** Short, concrete, no jargon, no internal vocabulary (never "IPK/IPH" — say
  "por kilómetro / por hora"). Let the colour and the number do the talking; cut words that repeat them.
- **Dark by default.** Drivers work nights; `DARK_PREFERRED = true`. Design for the dark palette first,
  verify light. Never use Material You / dynamic colour — the verdict colours are fixed brand signals.
- **Big tap targets.** Min 52dp for a primary button; never put a critical action behind a tiny target.

---

## 2. Colour tokens

Defined in `android/ui/src/main/java/mx/kompara/ui/theme/Color.kt`, wired in `Theme.kt`. Use the
`MaterialTheme.colorScheme` role, **never a raw hex**, in screens.

| Role | Dark | Light | Use for |
|---|---|---|---|
| `primary` (BrandGreen) | `#12A150` | `#0E7A3C` | Primary buttons, links, on-brand accents, active text |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | Text/icon on a primary fill |
| `surface` / `background` | `#121417` | `#FAFBFC` | Screen background |
| `surfaceContainer` | `#1C1F23` | `#FFFFFF` | **Card background** (the standard) |
| `surfaceVariant` | `#2A2E33` | `#EAEDF0` | Tonal lift on a card: in-card tonal buttons, status dots-off |
| `onSurface` | `#F2F3F5` | `#14181C` | Primary text |
| `onSurfaceVariant` | `#BFC4CA` | `#454B52` | Secondary/supporting text, hints, bodies |
| `outline` | `#44494F` | `#C3C9CF` | Borders (outlined buttons, dividers, dots-off) |
| `secondary` (VerdictYellow) | `#F2B705` | same | Attention/marginal accents (use sparingly) |
| `error` (VerdictRed) | `#D32F2F` | same | Destructive/alert surfaces (e.g. WatchdogBanner) |

**Verdict palette** (`VerdictLevel.brandColor` / `onBrandColor`): Verde `#1B8A3A` on white · Amarillo
`#F2B705` on `#1A1300` · Rojo `#D32F2F` on white. These are the semáforo — only for verdicts.

> **Debt:** `overlay/VerdictColors.kt` defines a *second*, slightly different verdict palette
> (`#2E7D32 / #F9A825 / #C62828`). Two sources of truth for the brand's core signal. Consolidate onto
> the `:ui` theme values when the overlay next gets touched.

---

## 3. Typography

Defined in `theme/Type.kt` (`KomparaTypography` + custom `KomparaType` styles). Use the role, not a
raw `fontSize`.

| Role | Size / weight | Use for |
|---|---|---|
| `KomparaType.metricValueLarge` | 44sp Black | The hero number on a dashboard (weekly net) |
| `KomparaType.metricValue` | 30sp Bold | Prominent metric inside a card; overlay chip hero rate |
| `headlineMedium` | 28sp Bold | (reserved — largest headline) |
| `headlineSmall` | ~24sp Bold | **Screen title** |
| `titleLarge` | 22sp SemiBold | Primary button label; large emphasis |
| `titleMedium` | ~16sp Bold | **Section title / card title** |
| `bodyLarge` | 16sp | Emphasised body (empty-state body) |
| `bodyMedium` | 14sp | **Default body / screen intro** |
| `bodySmall` | ~12sp | Hints, captions, compact card bodies |
| `KomparaType.metricLabel` | 13sp Medium | Small label above a metric value |
| `labelMedium` | 12sp Medium | Chips, status text, step counters |

**Header pattern:** screen title (`headlineSmall`, Bold) → 8dp → intro (`bodyMedium`,
`onSurfaceVariant`). Section title (`titleMedium`, Bold) → 4dp → optional hint (`bodySmall`).

---

## 4. Spacing scale

Use a 4-based scale: **4 · 8 · 12 · 16 · 24**. Don't invent in-between values.

| Context | Value |
|---|---|
| Screen edge padding | **16dp** (canonical) |
| Vertical rhythm between sections (`Arrangement.spacedBy`) | **16dp** |
| Card interior padding | **16dp** |
| Inside a card / tight groups (`spacedBy`) | **8dp** (or 12dp for looser) |
| Micro gaps (dot↔label, title↔hint) | **4–8dp** |

> **Reconcile:** edge padding is inconsistent today — Inicio/Comparar use 16dp, Thresholds/Ajustes/
> Lector use 24dp, Simulator 20dp. **16dp is canonical** (Material default + the primary data screens).
> Section rhythm splits 12 vs 16; **16dp is canonical.** Move screens toward these when you touch them
> — don't mass-refactor untouched screens.

---

## 5. Shape & elevation

| Element | Corner radius |
|---|---|
| Buttons | **14dp** (`PrimaryButton`) |
| Cards / containers | **12dp** (Material default; `RecommendationCard`) |
| Pills & badges (`VerdictBadge`, `PercentileBadge`, progress) | **50dp** (fully round) |

**Cards are tonal, not shadowed.** Separate surfaces by colour step (surface → surfaceContainer →
surfaceVariant), not elevation. `Card` default (no `elevation`) is correct; only `Surface` for the
bottom bar (`tonalElevation = 3dp`) and FAQ rows (`1dp`) use tonal elevation.

---

## 6. Buttons — emphasis hierarchy

Pick by importance, not by looks. **Never use bare text for an action that should read as tappable.**

| Tier | Component | Looks | When |
|---|---|---|---|
| Primary | `PrimaryButton` | Full-width, filled BrandGreen, 14dp, ≥52dp | The one main action of a screen ("Encender lector") |
| Secondary | `OutlinedButton` full-width | Outline + green text | Navigate elsewhere / alternative actions ("Probar en el simulador") |
| Tonal (in-card) | `FilledTonalButton`, `containerColor = surfaceVariant`, `contentColor = primary`, 14dp | Soft filled, sits on a card | The action that belongs to a card |
| Tertiary | `TextButton` | Text only | Low-stakes inline actions (dialog dismiss, "Ahora no") only |

> **Tonal-button colour note:** `secondaryContainer` is **not** wired in the theme, so don't rely on
> `FilledTonalButton`'s defaults (they fall back to a Material baseline lavender). Always pass explicit
> `ButtonDefaults.filledTonalButtonColors(containerColor = surfaceVariant, contentColor = primary)`.

---

## 7. Components — use these, don't reinvent

| Component | File | Recipe |
|---|---|---|
| `PrimaryButton` | `ui/components/PrimaryButton.kt` | Full-width filled CTA, 14dp, ≥52dp, `titleLarge` Bold |
| `EmptyState` | `ui/components/EmptyState.kt` | 72dp icon + `titleLarge` + `bodyLarge` + optional `PrimaryButton` CTA |
| `MetricCard` | `ui/components/MetricCard.kt` | `Card(surfaceContainer)`, 16dp pad, `metricValue` + `metricLabel` |
| `RecommendationCard` | `ui/components/RecommendationCard.kt` | Accent card: `accent.copy(0.12)` bg + `accent.copy(0.5)` 1dp border, 12dp, 14dp pad |
| `VerdictBadge` | `ui/components/VerdictBadge.kt` | Pill (50dp), `level.brandColor` fill — for verdict pills in lists |
| `ConnectionCard` | `ui/screens/LectorScreen.kt` | Status-on-title-row card + tonal action — pattern for "a thing with status + one action" |
| Status dot | inline | 8–10dp circle, `VerdictGreen` on / `outline` off |

Cards: `Card(colors = CardDefaults.cardColors(containerColor = surfaceContainer, contentColor =
onSurface))`, 16dp interior padding, 8–12dp internal `spacedBy`.

---

## 8. Accessibility

- **Tap targets ≥ 48dp** (52dp for primary). 
- **`contentDescription` on every meaningful surface.** When colour is the only on-screen signal
  (the overlay chip dropped its verdict word), the verdict **must** be spoken — the chip folds the
  level into its `contentDescription`.
- **Colour is never the *only* signal.** The chip pairs colour with the net number; verdict pills pair
  colour with a word. A colour alone (e.g. a status dot) is always accompanied by text.

---

## 9. Definition of done (new/changed UI)

- [ ] Uses `MaterialTheme.colorScheme` roles, no raw hex.
- [ ] Uses a typography role, no raw `fontSize`.
- [ ] Spacing is on the 4/8/12/16/24 scale; edge padding 16dp, section rhythm 16dp.
- [ ] At most one `PrimaryButton`; other actions are outlined/tonal/text per the hierarchy — no bare
      text masquerading as a button.
- [ ] Reuses a component from §7 where one fits.
- [ ] Verified in **dark** (default) and light.
- [ ] Tap targets ≥ 48dp; meaningful surfaces have `contentDescription`; colour isn't the only signal.
- [ ] Copy is plain es-MX, `tú`, no internal jargon, nothing the colour/number already says.
