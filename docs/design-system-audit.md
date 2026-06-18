# Kompara Design System — implementation status & audit

> Handoff doc for continuing the claude.ai/design → Compose implementation. Written 2026-06-18.

## TL;DR

The full Claude Design handoff (`~/Downloads/Kompara Design System-handoff.zip`, projectId `722871c2-5f17-40b1-a902-baca5f75b044`) has been implemented in the **Android Compose app** across 5 batches + 4 follow-up redesigns, shipped as a **stacked PR chain**. An end-to-end **design-vs-current audit** (17 screens) is captured below, including which screens were *not* recreated faithfully and **why**.

Everything compiles, unit tests pass, the full app builds, and it runs on-device (Samsung `RFCY30MW06E`). Onboarding screens are compile-verified only (can't reach them on-device without wiping data).

## ✅ V1.0 SHIPPED — 2026-06-18 (the "🔴 not recreated" gaps below are now CLOSED)

The full design-vs-Compose drift audit was completed and **all redesigns merged to `main`** as 9 focused PRs (#26–#34) + a design-bundle sync (#35), each Codex-reviewed (Gemini joined until its license 403'd mid-run), built, and on-device verified where reachable. Tagged `v1.0`.

| Area | PR | On-device |
|---|---|---|
| Comparar — value-cell clip fix + compact labels + verdict-leak | #26 | ✅ before/after (real data) |
| Importar — card + step-checklist + linear bar | #27 | ⚠️ transient upload state (build+review; reuses verified components) |
| Paywall — emerald price card + fixed footer + close ✕ | #28 | ⚠️ gate+debug-toggle trigger blocked by DiDi overlay (build+review) |
| Cluster A — Día listrows / Costos preview / Cuenta KomparaTextField / Historial slim rows | #29 | ✅ Historial; others build+review |
| Simulador — 3-way segmented verdict picker (:overlay) | #30 | build+review (chip room pre-mitigated) |
| Cluster B — Lector verdict-leak→BrandGreen / Fiscal clip fixes / Semáforo·Ajustes nits | #31 | ✅ Fiscal + Semáforo; Lector OFF state (Activo is a11y-gated) |
| Onboarding — tonal cards / numbered pill badges / status cards / a11y | #32 | not device-reachable w/o data wipe (build + a11y semantics) |
| Tu Mes — inline emerald Wrapped hero + real-data brag grid | #33 | ✅ hero verified ($0 month + real Mejor app/Racha + graceful "—") |
| Sentence-case metric tiles + slim app-shell top bar | #34 | ✅ Inicio |
| Design bundle sync (Importar label, Inicio casing; Lector via #31) | #35 | n/a (design) |

**Adversarial review caught real bugs** unit tests missed — Gemini flagged Importar's empty progress bar + Comparar metric-label clip; Codex caught Tu Mes's monthly-hours undercount (inflated $/h + corrupted percentile), the "Mejor app" enabled-filter bug, and several a11y label gaps — all fixed before merge.

**Documented deferrals / not-fully-verified:** Día "Mejor día"/best-hours has no verdict field → kept net + a `// QUESTION` (no fabricated verdict); Paywall / Importar / Onboarding / Lector-connected states are build+review verified (not reliably adb-triggerable — the **DiDi floating overlay** intercepts right-edge taps during on-device runs).

## How to continue (env / build / verify)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd <repo>/android
./gradlew :ui:compileDebugKotlin --offline      # fast gate
./gradlew :ui:testDebugUnitTest --offline
./gradlew :app:assembleDebug --offline           # full app (~25s)
# install + on-device screenshot:
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" exec-out screencap -p > /tmp/s.png   # then Read it
```

- Work was done in a **git worktree off `main`** (one branch/PR per batch) to dodge a live parallel session that branch-switches the shared `main` working tree. Last worktree: `/tmp/kompara-batch2` on `feat/design-system-onboarding-shell` (ephemeral — recreate with `git worktree add <path> feat/design-system-onboarding-shell`).
- App package: `mx.kompara.app`. Bottom-nav tap coords on the device (1080×2340): tabs at y≈2060, x≈108/325/540/755/972 (Inicio/Comparar/Lector/Fiscal/Ajustes). After `install -r` + launch, the **first** screencap races the splash — capture twice.
- Only **`material-icons-core`** is a dependency → no `Icons.Outlined.*` / `Icons.Rounded.*`. `:ui` **cannot** import `:overlay` (so the Lector chip is a static replica).

## PR chain (merge top-down)

| PR | Branch | Contents |
|---|---|---|
| [#17](https://github.com/diegovillafuerte/pilotea/pull/17) → `main` | `feat/design-system-foundations` | Batch 1 tokens (tabular figures, tracking, `headlineSmall`) + Batch 2 all shared components |
| [#20](https://github.com/diegovillafuerte/pilotea/pull/20) → #17 | `feat/design-system-screens-flagship` | Batch 3: Semáforo restructure, Inicio nudge |
| [#21](https://github.com/diegovillafuerte/pilotea/pull/21) → #20 | `feat/design-system-screens-rest` | Batch 4: surgical deltas across 10 screens |
| [#22](https://github.com/diegovillafuerte/pilotea/pull/22) → #21 | `feat/design-system-onboarding-shell` | Batch 5 + Ajustes list + Inicio grid + Lector pane + onboarding redesigns + Inicio tile fix (tip = `b82ac7a`) |

Standalone (off `main`): [#18](https://github.com/diegovillafuerte/pilotea/pull/18) build-env doc, [#19](https://github.com/diegovillafuerte/pilotea/pull/19) `.agents/` Codex tooling.

## Components built (`:ui/components`)

`KomparaButton` (4 tiers, `PrimaryButton` delegates), `KomparaCard` (tones), `KomparaChip`, `KomparaTextField` (label-above filled), `KomparaOtpInput`, `KomparaDialog`, `KomparaStatusChip`, `KomparaSwitch`, `KomparaSlider` + existing `MetricCard`/`RecommendationCard`/`PercentileBar`/`PercentileBadge`/`VerdictBadge`/`EmptyState`/`KomparaProgressBar`.

## Standing design rules

- **Verdict colours (verde/amarillo/rojo) = verdicts ONLY.** Recolour any decorative `secondary`/`secondaryContainer` (an unwired Material lavender) to brand slate/nudge.
- Preserve behaviour/features over matching a happy-path mock; surface scope/behaviour changes rather than silently applying them.
- Emerald `#059669` primary, slate neutrals, Inter, 4-based spacing (16/8), radius card 12 / button 14 / pill 999, tonal cards (no shadow). Dark by default.

---

## Audit — design mock vs current implementation (17 screens)

Faithfulness 0–100; "extra" deviations = legitimate richer real states the static mock omits (not faults).

### 🔴 Not recreated faithfully — need a real redesign

These got only **surgical** Batch-4 deltas (token/component swaps), **not structural relayouts** — that was deliberate (avoid regressing screens that couldn't be runtime-verified). Same reason Ajustes first "looked the same".

- **Tu Mes — 34 (major).** Mock = Wrapped-style **emerald-gradient hero card** (radius 20, white text, brand row "Kompara · Junio", big white net, 2×2 brag grid: Tu lugar / Mejor app / Mejor día / Racha), rendered **inline**, one screen, CTA "Compartir en WhatsApp". Current = a **week data-table** (`WeekSummaryScreen`) + a **bitmap** share-image (`ShareCardScreen`/`ShareCardRenderer`), framed as "Tu Semana"/"Mi semana", net drawn emerald-on-dark (inverted), no month, no brag grid. **Effectively not built.** Files: `screens/WeekSummaryScreen.kt`, `share/ShareCardScreen.kt`, `share/ShareCardRenderer.kt`.
- **Importar — 42 (major).** Mock upload state = a **card** with a 4-row **step checklist** (18px ✓ circles, pending dimmed) + a **linear progress bar** + duration hint, page header kept. Current `UploadingState` = centered **CircularProgressIndicator** + plain text, no card, no checklist, no linear bar, no header. Use `KomparaProgressBar`. File: `imports/ImportScreen.kt:293-324`.
- **Paywall — 52 (major).** Missing: the **emerald price card** (`Premium` / `$99` `/mes` + sub-line), a **fixed footer** (CTA + Restaurar + legal anchored), a **close ✕**. Price currently a plain text line *below* the CTA (mock has it above). Feature check icon Filled vs outline. File: `paywall/PaywallScreen.kt`.

### ⬜ Intentionally not touched

- **Simulador — 42.** Lives in **`:overlay`** (parallel session's module). Different paradigm (step-nav + threshold playground vs the mock's 3-way segmented verdict picker), chip top-right vs bottom-right. File: `overlay/simulator/SimulatorScreen.kt`.

### 🟡 Minor deviations (structurally faithful; nits + intentional extras)

- **Comparar — 74.** ⚠️ **Real clip risk:** per-platform value cells use `maxLines=1` + **`softWrap=false`** → `$160/h` / `$9.2/km` **hard-clip** in narrow Uber/DiDi columns (`CompararScreen.kt:384-385`). Also: 6 metric rows vs mock's 4; spelled-out labels (`Ganancia neta` vs `Neto`); column weights 1.6/1.3 vs 1.4/1.0; PercentileCell adds a mini progress bar; "Tu lugar" vs "Lugar". Many extras (legend, opportunities title, cross-link, gate) are legit.
- **Historial — 62.** Card rows (30sp net) vs mock's **slim list rows** (15px net); full-width green CTA vs small tonal inline button; blurred **PaywallGate** vs flat tinted nudge; no "Historial" title (shell hosts it); adds a trips/hours line. Clip risk: week-label + chip on a SpaceBetween row, no weight. File: `screens/HistoryScreen.kt`.
- **Día — 68.** Rows are plain text, **not tonal listrow cards** (mock wants surface-card/radius-12/14×16 rows); title collapses "Detalle del día" + spelled subtitle into one abbreviated date; shift net loses emerald/bold/right-align. File: `screens/DayDetailScreen.kt`.
- **Costos — 72.** Preview card hoisted to top (mock: above save button at bottom) and under-styled (inline 16sp vs 22px/800 hero number); EV toggle is a bare row, not a tonal pill; 2 extra fields. File: `screens/CostProfileScreen.kt`.
- **Cuenta — 72.** Uses Material `OutlinedTextField` (floating label) instead of **`KomparaTextField`** (label-above filled); WhatsApp field disabled/greyed; city is a dropdown not a flat field; no `+52` prefix. File: `screens/AccountScreen.kt`.
- **Inicio — 76 → FIXED.** Now a clean 4-tile 2×2 grid, labels fully visible (label/value/pill stacked), acceptance tile moved off Inicio, `VIAJES POR HORA`→`VIAJES/HORA`. Remaining nits: labels still ALL-CAPS vs mock sentence-case (copy decision); pill is *below* the value not top-right (deliberate — side-by-side clips on a real device); header has a share icon the mock lacks (intentional, B-055).
- **Fiscal — 78.** Richer than mock (month picker, full withholding summary, regime/disclaimer copy). Platform cards on `CardTone.VARIANT` (slate-700) vs mock's default (slate-800). Clip risk: "Resumen fiscal" + "Año (acum.)" chip on one row; platform title + coverage chip. ProgressBar 10dp vs 8px. File: `screens/FiscalScreen.kt`.
- **Lector — 80.** Host pane + chip replica faithful. Deviations: primary CTA is start/restart (mock = "Detener"); 4 buttons vs 2; raw `OutlinedButton` vs `KomparaButton` SECONDARY tier; "Activo" status uses `VerdictGreen` (verdict leak — should be `BrandGreen`). File: `screens/LectorScreen.kt`.
- **Ajustes — 82.** Faithful list. Extras: 7th "Historial" row (shifts order), 2 production toggles; label 16sp vs 15px; label/chevron a touch brighter than the muted mock. File: `screens/PlaceholderScreens.kt`.
- **Onboarding — 82.** Flow/copy/components faithful (logomark tiles + progress topbar landed). Gaps: disclosure sections not in tonal `.sec` cards; numbered **pill step badges** missing (flat "1. …"); accessibility/OEM **status card + inline Switch** missing (relocated to Done); city is a floating-label dropdown not label-above; edge padding 24dp vs 16/20; step counter `n/5` not mock's `n/7` (signup is one nav destination — documented). Files: `onboarding/OnboardingScreens.kt`, `onboarding/OnboardingNavGraph.kt`, `auth/SignupScreens.kt`.
- **Ayuda — 84.** Faithful FAQ accordion. 7 items vs 4; chevron icon vs `+/–` glyph; multi-open vs single-open; first item not open by default. File: `screens/HelpScreen.kt`.
- **app-shell — 86.** Faithful 5-tab scaffold. Extra **brand top bar** (KomparaTopBar) the mock shell omits (deliberate S-024 addition — keep-or-drop is a product call); Filled nav icons vs mock's outline (no extended-icons dep). Files: `nav/KomparaApp.kt`, `nav/KomparaBottomBar.kt`, `nav/KomparaDestination.kt`.
- **Semáforo — 88.** Faithful (segmented control + single card + badge legend). Nits: slider value 700 vs mock 900; slider labels brighter than muted; section label not uppercased; reset button is an extra; longer copy.

---

## Why deviations exist — categories

1. **Surgical-only (the 3 red screens):** Batch 4 swapped tokens/components but did NOT restructure layouts (to avoid regressing un-verifiable screens). → redesign Tu Mes / Importar / Paywall to recreate the mocks.
2. **Cross-module:** Simulador is in `:overlay` (parallel session) — left untouched.
3. **Intentional richer states:** paywall gates, empty/loading, confirmations, extra metrics/fields, month pickers — the mocks are single happy-path frames.
4. **Copy/casing left to product:** spelled-out vs compact labels, uppercase vs sentence-case, longer intros — `strings.xml` not rewritten.
5. **Text-clipping pattern:** label/value competing with a badge/chip in a `SpaceBetween` row without `weight`/`maxLines`. Fixed on Inicio; **Comparar value cells (`softWrap=false`) hard-clip** and are the next real one; Fiscal/Historial header rows are at-risk.

## Suggested next-session TODO

- [ ] **Redesign Tu Mes** as the inline Wrapped-style emerald gradient hero card (+ brag grid).
- [ ] **Redesign Importar** upload state: card + ✓-circle step checklist + `KomparaProgressBar`.
- [ ] **Redesign Paywall:** emerald price card, fixed footer, close ✕, price above CTA.
- [ ] **Fix Comparar clipping** (drop `softWrap=false`, add `weight`/ellipsis on value cells) + Fiscal/Historial header rows.
- [ ] **Settle copy/casing:** compact table labels (Comparar), sentence-case metric labels (Inicio), shorter intros — product decision.
- [ ] Smaller nits per screen above (Día listrow cards, Costos preview, Cuenta `KomparaTextField`, Lector secondary-tier buttons + verdict-green status, onboarding step-pill badges / status card).
- [ ] **Verify onboarding on a fresh install** (compile-verified only).
- [ ] Decide: keep the app-shell brand top bar? (mock omits it).

Full per-screen deviation detail (with file:line) is in the audit workflow output if a deeper drill-down is needed.
