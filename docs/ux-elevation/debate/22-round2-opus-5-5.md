# Kompara: Round 2 (Opus), cross-critique and converged v2

**Inputs:**
- My Round 1: `r1-opus.md`
- The other designer's Round 1: `r1-codex.md` (called "Codex" below)
- The rubric: `rubric-uber-bar.md`
- Ground truth: `inventory.md` and the docs cited in both Round 1 documents

I also re-checked two source files:
- `android/ui/.../onboarding/SettingsLinks.kt`: today the app opens only the generic Accessibility list, and deliberately avoids the Play-restricted `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- `ocr/SilentScreenshotLane.kt`: on API 30+ the reader is live whenever the accessibility service is on.

**Tags:**
- `[mine]` is my Round 1 position, kept.
- `[theirs]` is adopted from Codex.
- `[new]` is a decision neither of us made in Round 1.

---

## 0. Position in one paragraph

Codex wrote the more *honest* product. I wrote the more *alive* one. The converged v2 keeps two things from Codex:
- **The integrity model:** one meaning per money word, scope labels under every period figure, neutral states when a reading is incomplete, readiness reported as "preparado" vs "funcionando", like-for-like comparisons, and no charge for data that doesn't exist.

It keeps two things from my Round 1:
- **The growth and habit surfaces:** an informative Hoy, a shift summary, the week close with sharing, referrals, a free IMSS tracker, import.
- **A chip that stays small and fast:** a glyph, two rates, about 150×72dp.

On integrity I concede more than I defend. I had five real errors:
1. My chip substituted the deciding metric when an input was missing.
2. My "Dónde puedes ganar más" example ("Casi todo lo hacen entre 18 y 22 h") requires hourly population data that does not exist.
3. My IMSS status "Ya te toca" asserts a coverage outcome the app can't know.
4. My insight copy "tómalo con confianza" is advice, not a fact.
5. My "Ganancia neta" label meant two things depending on the screen.

On structure I changed my third tab to **Comparar**. On growth I hold my ground: Codex removes import, IMSS, referrals, insights and the share-card facts, and that leaves a correct app with no reason to open it on Monday.

---

## 1. Steelman: five things Codex does better

| # | What Codex does better | Where | Adopt? |
|---|---|---|---|
| 1 | **One meaning per money word, made visible.** Its table maps each context to one label: "Tras gasolina y desgaste" (offer), "Te quedan aprox." (period, costs configured), "Ingresos registrados" (no costs), "Sin confirmar" (lifecycle unknown). Every period hero carries a **scope line** ("Después de tus costos · antes de impuestos"), and "**Cómo se calculó**" is one tap away. It also says plainly: never sum offers as earnings, never treat missing commission or pickup as zero. This fixes a defect I under-ranked: the inventory's Fiscal footnote says "tu neto aparecerá igual al bruto" (where neto means *after commission*), while Tus costos says "ganancia neta de verdad" (where neto means *after costs*). One word, two meanings. | §2 "financial language", §4.7 | **Adopt.** v2 uses "Te quedan" for period results with a scope line underneath, and keeps "neto" only as the compact unit on rates, defined once. The "Cómo se calculó" sheet is in. The fiscal "Bruto/Neto" pair is renamed. |
| 2 | **A truthful readiness model.** "Lector preparado" (permission granted, nothing read yet) is distinct from "Lector funcionando" (a real host offer was read). Per-app rows: "Uber — Esperando una oferta", "DiDi — Lectura comprobada", "inDrive — No disponible en esta versión". "Última oferta leída: hace 2 min." This directly kills the inventory's false "Kompara ya está leyendo tus ofertas". | §4.1, §4.2, §6 step 4 | **Adopt fully.** It is the best idea in either document. |
| 3 | **Never swap the deciding unit; unknown is not a verdict.** "'Unknown' is a data state, not a fourth economic verdict." "Falta distancia / Sin cálculo"; "Leyendo oferta…" only if the delay is perceptible; close the explanation panel if the offer changes. | §5 state contract | **Adopt, with one addition** `[new]`: when the deciding input is missing, the chip goes grey (no verdict), but still shows any rate that *can* be computed, as information. |
| 4 | **Comparison integrity.** It separates "**Tus apps**" (your own results across platforms) from "**Tu ciudad**" (your result vs a matching cohort). It insists on the same metric, week, expense basis and platform on both sides. It uses "Promedio", never "conductor típico", and adds a "**De dónde salen estos datos**" provenance sheet. "**Do not charge for absence**": if city data doesn't exist, say so before the paywall. | §4.8, §4.9, §7 | **Adopt the structure.** I keep the cohort-specific standing sentence and the 20-person bar (only on real populations) because they're the viral hook. I kill my "Dónde puedes ganar más". |
| 5 | **Honest trial and paywall mechanics.** "Sin tarjeta. **No habrá ningún cobro al terminar.**" Contextual paywall titles by intent ("Revisa todas tus semanas", "Prepara tu resumen del mes"). A "**Tu plan**" status page for subscribers instead of a sales page. The free window is defined by **calendar dates**, not "two most recent weeks with data". The deletion dialog adds "Si tienes una suscripción en Google Play, debes cancelarla ahí." | §4.16, §4.17, §7 | **Adopt all five.** |

**Smaller adoptions**, each tagged where it appears in v2:
- An explicit-save draft editor in the overlay, with the line "Ajusta cuando estés estacionado".
- Tap on the chip as the *discoverable* route, with long-press only as a shortcut.
- "Pausar lector" that genuinely stops capture.
- Imported weeks never invent daily rows ("Este resumen no incluye el detalle por día").
- A "Tus datos" privacy page.
- Recovery notifications that say "cuando te estaciones".
- No punitive "Pantalla limitada" screen.
- No "typical city value" claim unless it's substantiated ("valor inicial").

---

## 2. Attack: what Codex gets wrong for a driver on a windshield mount (ranked by damage)

| Rank | Codex decision | Why it's wrong | Evidence | Damage |
|---|---|---|---|---|
| 1 | **No net verdict until the driver enters costs.** "Missing personal costs → 'Faltan tus costos' / 'Sin cálculo neto'", with the cost step skippable in onboarding. | Most drivers don't know their km/L (the inventory flags "Drivers may not know their km/L"). Anyone who skips sees a **grey chip on every offer**, and the free hook is dead on day 1. A labelled typical-sedan assumption is not a "made-up personal cost". It's a stated estimate, the same epistemic class as Codex's own "aprox.". | Rubric criterion 7 ("first value < 60 s… a live or simulated verdict before any… ask that isn't strictly required"); inventory Tus costos smell. | **Critical (activation)** |
| 2 | **Kills import and the share-to-import target.** | The synthesizer clarified that the on-device rule covers *screen content*, not a file the driver chooses to send. Import is the **only** source of pre-install history and the **only** source of the driver's commission ("Take rate is sparse … only imported weeks / Uber PDF supply it", `comparar-redesign-spec.md` §2). Codex's own §7 admits the risk that "history and reports may not justify the subscription", and then removes the feature that fills history. | Inventory Importar; spec §2 gap 3; Codex §7 last paragraph. | **High (Premium value, data quality)** |
| 3 | **Removes IMSS coverage tracking, the month-end notification and the tax estimates entirely.** | The problem was the *assertion* ("Cubierto este mes"), not the *tracker*. IMSS is "the feature opportunity no competitor has", "uniquely Mexican" (`competitive-analysis.md` §5). Progress toward a public threshold, labelled as an estimate, is a fact about the driver's own records. The fix is better copy and a legal check of the income basis, not deletion. | Competitive analysis §5; inventory Fiscal; E5 notifications. | **High (differentiation)** |
| 4 | **The chip is 168×96–112dp with three lines, one of them a constant caption ("Tras gasolina y desgaste"), and it grows with font size.** | It covers more of the host's offer card for 8–20 s. A caption that never changes is read once and is noise forever after. "neto" (4 letters) carries the same contrast with Uber's gross badge. | Design principles §1 ("cut words that repeat"); offer-card lifetime (`android-technical-design.md` §1). | **High (occlusion, glance)** |
| 5 | **One rate only: it drops $/h from the collapsed chip.** | $/h under $/km was a deliberate shipped decision ("shipped 2026-06-12: $/hr under the $/km hero"), and it is table stakes: GigU, Ruta Rentable and DecideRider all show both (`benchmark-ux-gaps.md` §1, competitive analysis §2). Hiding it behind a tap makes Kompara *less* informative at the glance than the incumbent. | Benchmark scorecard row "Overlay shows $/km and $/hr". | **Medium-high** |
| 6 | **"Conviene / Regular / No conviene" as the verdict vocabulary.** | This contradicts Codex's own principle ("Expired offers are not moral judgments"; "do not … tell the driver to accept"). "No conviene" reads as an instruction, while a driver may rightly take a red trip that heads home. It also *keeps* two vocabularies: Codex's own Tu semáforo uses "Verde desde" next to a "Conviene / Regular / No conviene" preview. | Codex §4.14 items 4 and 7; inventory cross-cutting finding 2. | **Medium** |
| 7 | **Kills every growth and habit loop at once:** referral, share facts, insights, weekly goal, IMSS notification. | The market is WhatsApp-native and word-of-mouth driven, and rivals run creator and referral programs (Ruta Rentable Partners, GigU; `benchmark-ux-gaps.md` P2). The referral code system is already built. Without *any* recurring reason to open the app, Kompara becomes an overlay that users forget is installed. | Benchmark gaps §3 P2; inventory Invita y gana. | **Medium (retention, CAC)** |
| 8 | **Fully optional account, asked only at trial time; city only as a Comparar filter.** | Optional is right (I concede that in F7). But asking *only* at trial time starves the consented aggregates that city cohorts are built from: there's no "Tu ciudad" without contributors. Also, with no city, thresholds lose their city starting point. | Spec §2 (population per city/platform); inventory Signup Perfil ("Choosing a city also seeds the default thresholds"). | **Medium** |
| 9 | **Cost changes apply only going forward ("Do not silently rewrite two years of history").** | That's right for *prices*, which really change over time. It's wrong for *efficiency*: correcting the car's km/L fixes an error that was always true, so past results should be fixed too. | Inventory Tus costos ("recomputes about 2 years of history"). | **Low-medium** `[new]` split rule in v2 |
| 10 | **Hoy has a filled "Ver mi día" primary on a read-only screen; Ganancias is a seven-row day list instead of a chart.** | Codex's own shared contract says "Do not add a filled 'Continue' button to a read-only screen". Seven rows means seven more numbers. A 7-bar chart is one hero plus a shape (rubric criterion 4: "one big number, one chart, breakdown behind a tap"). | Codex §4 shared contract; rubric 4. | **Low** |

---

## 3. The forks: final positions (F1–F16)

**F1: The third tab.** **Final: Hoy · Ganancias · Comparar, with Cuenta behind a profile avatar at the top right of every tab root. Changed.**
- The founder locked "Inicio = individual / Comparar = comparison", Comparar is the paid revenue surface, and the product is literally named *Kompara*.
- My Round 1 objection was that a comparison tab would sit empty. Codex's "**Tus apps**" segment removes it: any driver with two apps gets real, personal comparison content even before city cohorts exist.
- Uber's Account slot is satisfied by the always-visible avatar, and the rubric accepts 3 tabs with settings "tucked away behind Account".
- Week state is shared between Ganancias and Comparar `[new]`, so switching tabs never loses the period.

**F2: The chip's non-colour cue and vocabulary.** **Final: glyph (✓ ⚠ ✕, plus ? for grey) + one hero rate + a small second line "neto · $186/h"; one vocabulary, verde / amarillo / rojo / sin color. Held.**
- A shape is read before words are (preattentive), it is colour-blind-safe, it is language-free, and it costs no width.
- The words "Conviene / No conviene" judge the trip. The colour names only report the driver's own rule, which is already written in colour terms ("Verde desde").
- "neto" keeps the brand's "speak in net pesos" and the contrast with Uber's gross badge in four letters.
- Adopted from Codex: no brand strip, and ≥13sp text on every chip line `[theirs]`.

**F3: What a tap on the chip does.** **Final: a tap opens a compact "por qué" panel. Long-press is a shortcut to the threshold editor. Changed.**
- Without a tap route, both the explanation and the editor depend on an invisible gesture. The parked doc's three failure causes can each be engineered away:
  - **(a) Window relayout:** the panel lives in its *own* overlay window anchored to the chip, so the chip window never resizes.
  - **(b) Drag registering as a tap:** a tap only fires on touch-up within the touch slop and under 300 ms.
  - **(c) Low payoff:** the panel answers the one question the colour can't: "¿por qué $148 sale amarillo?".
- It auto-closes when the offer changes or disappears `[theirs]`.

**F4: Missing-input behaviour.** **Final: never swap the deciding unit. A missing deciding input makes the chip grey, with no colour, but any rate that can be computed is still shown as information ("Falta distancia · $186/h neto · sin color"). Changed.**
- Colour must always mean the rule the driver chose `[theirs]`.
- Throwing away a valid number helps no one `[new]`.
- A missing pickup leg also produces grey ("Falta la recogida"), never a trip-only $/km `[theirs]`.

**F5: Import.** **Final: keep import (detect → parse once → review → save), keep the share-sheet entry, and say plainly that the chosen file is uploaded. Held.**
- The on-device rule is about screen content, and the driver's own PDF is a deliberate, different act (synthesizer's clarification).
- It's the only source of commission and pre-install history.
- Imported weeks never invent daily rows `[theirs]`.
- Import requires an account, but no payment.

**F6: IMSS.** **Final: keep a FREE, clearly estimated IMSS progress tracker per app ("Llevas $5,200 de $8,364") with no coverage assertions. Keep a factual month-end notification. Keep withholding estimates in the Premium PDF, labelled as estimates. Changed (partly).**
- I drop "Ya te toca / En camino / Difícil" and all coverage verdicts `[theirs]`.
- **Launch gate** `[new]`: the income basis must match the law's definition (legal B-038), not Kompara's cost-adjusted "neto".
- It is the only uniquely Mexican differentiator. Fix the claim; keep the feature.

**F7: Signup placement and optionality.** **Final: the account is asked once, as the *last* onboarding step after the reader is live, with a visible "Ahora no". It is re-offered at the first week close together with the trial, and is required only for the trial, import, backup and referrals. Changed (from effectively required to optional).**
- The free reader is on-device and must never depend on WhatsApp, the network or Twilio `[theirs]`.
- Asking once at peak motivation (rather than only at trial time) keeps the city cohorts fed `[mine]`.
- City is inferred from the area code when a number is given `[mine]`. Otherwise it's asked the first time the driver opens Tu ciudad `[theirs]`.

**F8: What goes on Hoy.** **Final: status row + hero "Te quedan hoy" (with scope line) + ONE factual observation + the last 3 offers. No week row and no filled button when healthy. Converged.**
- The last three offers are the best readiness proof there is: a driver sees that the reader actually read them.
- The week is one tab away, so the week row was duplication `[theirs]`.

**F9: Insights / consejos.** **Final: keep one observation on Hoy, strictly factual, with no advice and no blame. For example: "2 ofertas verdes se fueron sin tomar · sumaban $310", "Tu mejor hora hoy: 18 a 19 h · te quedaron $232". Kill all coaching. Changed (partly).**
- Killed: "Maneja más en ese horario", "Baja tu mínimo", "tómalo con confianza", "Estás rechazando casi todo", and the misleading "…para que la app filtre…".
- "Se fueron sin tomar" covers both expired and passed offers, which answers Codex's "not moral judgments" point.

**F10: Goal, streak, referral, share brag grid.** **Final: goal killed (replaced by the weekly car cost, see F14); streak killed everywhere; referral kept under Cuenta; share card kept with one hero + two own-data facts, amounts hidden by default. Changed (partly).**
- I drop "Mejor app" (not like-for-like) and the streak `[theirs]`.
- I keep referral, because word-of-mouth is the growth engine in a WhatsApp-native market `[mine]`.

**F11: Comparison structure.** **Final: Comparar has two segments, "Tus apps | Tu ciudad", and shows ONE metric at a time (chips: Por km · Por hora · Por viaje) as aligned rows, with a provenance sheet and strict like-for-like `[theirs]`. Tu ciudad also leads with a cohort-specific standing sentence and the 20-person bar, but only for real populations `[mine]`. "Dónde puedes ganar más" is killed. Changed.**
- A five-metric list puts 10+ numbers on one screen. One metric at a time passes the rubric's ≤3 numbers rule.

**F12: When the trial starts.** **Final: for drivers with an account, the trial starts automatically at the first week close that has Premium content to show, and is announced ("sin cobro al terminar"). For drivers without an account, it starts on an explicit choice. It is never consumed on an empty week. Converged.**
- A "reverse trial" exposes every engaged driver to Premium at the moment it has something to show.
- Codex's condition ("when there is something to show") and its "no charge at the end" promise make this honest.

**F13: Free history window.** **Final: defined by calendar, "esta semana y las 3 anteriores" (Monday to Sunday), plus the current month's IMSS. Converged.**
- The calendar rule is Codex's `[theirs]`; the 4-week window is mine `[mine]`. Two weeks makes a free month look broken.

**F14: Fixed costs (rent, insurance).** **Final: one optional weekly figure, "Lo que pagas por el coche cada semana" (renta, crédito o seguro). It is subtracted once per week, only from week and month results, and never from the chip or the day. It is shown as its own line with a "cubierto el miércoles" marker. The per-day fields are killed. Changed.**
- Codex is right that rent defines what a renter really keeps `[theirs]`. Per-day allocation rules are too complex.
- Renters pay *weekly*, so the weekly figure matches reality and absorbs the job my goal bar was doing `[new]`.

**F15: Hero labels.** **Final: period heroes say "Te quedan hoy / esta semana", with a muted scope line directly underneath ("Después de gasolina y desgaste · aprox.", "…y lo del coche" when set, "Con costos típicos" until the driver confirms). "neto" survives only as the compact unit on rates. Changed.**
- These are driver words with scope always visible `[theirs]`, and the chip stays short `[mine]`.

**F16: Before-shift greeting pill.** **Final: keep it, tightened.**
- **When:** at most once per day, only over a *supported ride app*, and only after a real capture self-check on that app's screen succeeds.
- **What:** "✓ Kompara listo" for 2 s; touches pass through to the ride app.
- **Placement:** in the chip's own safe zone.
- **Opt-out:** can be switched off in Avisos.

**Held, narrowed.** It is the only readiness signal that reaches the driver *at the moment of need*, which addresses Codex's own critical problem #3 ("false readiness"). It never appears over unrelated apps `[theirs]`.

---

## 4. Converged proposal v2

### v2 §3. Information architecture

**Navigation `[theirs]` + `[mine]`:** three tabs and an always-present account entry.

| # | Tab | Icon | Purpose |
|---|---|---|---|
| 1 | **Hoy** | `Icons.Filled.Home` | Is Kompara reading, what do I have left today, and what happened with my offers. |
| 2 | **Ganancias** | `bar_chart` (Material Symbols vector drawable; not in `material-icons-core`) | My money over time: week ↔ month ↔ day ↔ offer. |
| 3 | **Comparar** | `compare_arrows` (Material Symbols vector drawable) | My apps against each other, and me against drivers of my city, one metric at a time. |
| — | **Cuenta** (avatar at top right of each tab root, contentDescription "Tu cuenta y ajustes") | `Icons.Filled.AccountCircle` | Plan, semáforo, costs, reader, notifications, invites, help, data. |

**Chrome rules:**
- Tab roots use a large left title and the avatar; there is no "K Kompara" wordmark bar `[mine]`.
- Drill-ins use a top app bar with ← and a title `[both]`.
- The bottom bar shows on tab roots and data drill-ins (Tu día, IMSS del mes). It is **hidden** on tasks and editors: Paywall, Importar, Compartir, Tu semáforo, Tus costos, signup and onboarding `[theirs]`.
- The selected week or month is **shared** by Ganancias and Comparar `[new]`.

**Map:**

```
Hoy ──────── Tu día ── Detalle de oferta (sheet) ── Cómo se calculó (sheet)
 └ status ── Lector (per-app status · checklist · Pausar)
Ganancias [Semana | Mes] ── Elegir periodo (sheet)
 ├ Semana: Tu día · Lo del coche (sheet) · Cómo se calculó · Compartir · Importar · "Comparar esta semana" → Comparar
 └ Mes:    IMSS del mes (── ¿Cómo funciona? sheet) · Resumen para tu contador (PDF, Premium) · Compartir
Comparar [Tus apps | Tu ciudad] · metric chips · Ciudad/App (sheets) · De dónde salen estos datos (sheet)
Cuenta (avatar) ── Tu plan / Paywall · Tu semáforo · Tus costos · Lo del coche · Lector · Avisos
                   · Invita y gana · Ayuda · Tus datos · Tu perfil (Cerrar sesión, Eliminar cuenta) · Entrar con WhatsApp
Overlay: chip · por qué panel · editor · greeting · paused chip
```

#### Fate table v2 (every current surface)

| Current surface | v2 fate | Destination / reason | Tag |
|---|---|---|---|
| Root `LOADING` | Keep, redesign | A visible skeleton of Hoy; never waits on the network | `[theirs]` |
| Root `ONBOARDING` | Keep, shorten | 4 screens on stock phones (§v2 6) | `[both]` |
| Root `AUTH` | Merge → optional "Entra con tu WhatsApp" | An expired session never disables the reader | `[theirs]` |
| Wordmark top bar | Kill | Title + avatar | `[both]` |
| 5-tab bar | Replace | Hoy · Ganancias · Comparar | `[theirs]` |
| Pushed screens without back | Kill pattern | Top app bar everywhere | `[both]` |
| **Inicio** | Merge → **Hoy** | Today plus readiness | `[both]` |
| ↳ WatchdogBanner | Merge → Hoy status row | One status model | `[both]` |
| ↳ Weekly net hero | Move → Ganancias · Semana | | `[both]` |
| ↳ Streak badge | Kill | Not decision-relevant; pressure | `[theirs]` |
| ↳ Share icon | Move → Ganancias "Compartir tu semana" | | `[mine]` |
| ↳ GoalBar | Kill → replaced by "Lo del coche" line | Gives renters a real deduction instead of an arbitrary goal | `[new]` |
| ↳ CostNudge | Replace → "Con costos típicos · Ajustar" scope line + a one-time km/L card | | `[new]` |
| ↳ Platform chips | Kill → Comparar › Tus apps | | `[theirs]` |
| ↳ 2×2 metric grid + Top X% pills | Kill → Comparar metric chips | | `[both]` |
| ↳ Benchmarks PaywallGate tease | Kill | Locks live only on Comparar › Tu ciudad and on old periods | `[both]` |
| ↳ Consejos ×3 | Keep 1 factual observation; kill coaching | | `[new]` |
| ↳ Completeness note | Move → scope line / Tu día | | `[both]` |
| ↳ EmptyState "Aún no hay números" | Replace → "Lector preparado" first-run | | `[theirs]` |
| **Comparar** | Keep tab, rebuild | Tus apps / Tu ciudad, one metric | `[theirs]` |
| ↳ Week pill | Merge → shared period stepper | | `[new]` |
| ↳ Free hero "Le ganas al 78%…" | Keep, cohort-specific, only on real data | | `[mine]`+`[theirs]` |
| ↳ 5-column table | Kill → aligned rows per metric | | `[theirs]` |
| ↳ "Dónde puedes ganar más" | Kill | Needs data we don't have | `[theirs]` |
| ↳ Cross-link, "Pronto" footnote, single-app note | Kill | | `[both]` |
| **Lector** | Merge → Hoy status + **Lector** drill-in | | `[both]` |
| ↳ Mock offer | Kill | | `[both]` |
| ↳ "Lector para DiDi" / Iniciar | Kill on API 30+; on <30, a checklist row | | `[both]` |
| **Fiscal** | Merge → Ganancias › Mes › **IMSS del mes** + PDF | Tracker free, without assertions | `[new]` |
| ↳ Coverage chips "Cubierto/Difícil" | Kill | Unverifiable assertion | `[theirs]` |
| ↳ Resumen fiscal | Move → Premium PDF "Resumen para tu contador" | | `[both]` |
| ↳ Legal explainers | Replace → "¿Cómo funciona?" sheet, 3 bullets | | `[mine]` |
| **Ajustes** | Replace → **Cuenta** (avatar) | Grouped rows with values | `[both]` |
| ↳ Toggles | Move → Avisos | | `[both]` |
| **Tus costos** | Keep, simplified + "Ya sé mi costo por km" | | `[mine]`+`[theirs]` |
| ↳ Seguro/Renta per day | Kill → weekly "Lo del coche" | | `[new]` |
| **Tu semáforo** | Keep; absorbs the Simulator preview; explicit save | | `[mine]`+`[theirs]` |
| **Ayuda** | Keep, 5 questions + WhatsApp contact | | `[mine]` |
| **Tu cuenta** | Keep → **Tu perfil** (optional account) | | `[both]` |
| ↳ Delete dialog | Keep + Play subscription line | | `[theirs]` |
| **Historial** | Kill → period stepper + Mes list | | `[both]` |
| **Resumen de la semana** | Merge → Ganancias · Semana | | `[both]` |
| **Detalle del día** | Keep → **Tu día**, any date | | `[both]` |
| `day/{day}` | Used by Tu día arrows | | `[both]` |
| **Paywall** | Keep, contextual titles, no-charge trial | | `[theirs]`+`[mine]` |
| *(new)* **Tu plan** | Add | Status page for trial/subscribers | `[theirs]` |
| **Importar semana** | Keep: detect → parse once → review → save | | `[mine]` |
| **Invita y gana** | Keep under Cuenta | | `[mine]` |
| **Share card** | Keep → **Compartir**; preview = PNG; hero + 2 facts | | `[new]` |
| **Simulador** | Kill as destination → onboarding demo + Tu semáforo preview + "Ver ejemplo" | | `[both]` |
| Pitch ×3 | Merge → Demo | | `[both]` |
| Signup Teléfono / Código | Keep, optional, last onboarding step + trial | | `[new]` |
| Signup Perfil | Kill | City from area code or the Tu ciudad selector | `[both]` |
| Divulgación | Keep, rewritten (Qué lee / Qué guarda / Qué hace / Tú decides) | | `[theirs]`+`[mine]` |
| Pantalla limitada | Kill → Hoy "Lector apagado" | | `[theirs]` |
| Activa el lector (waiting) | Keep; on API 33+ deep-link straight to Kompara's own toggle | | `[new]` |
| Activa el lector (granted) | Merge → "Lector preparado", auto-advance | | `[both]` |
| OEM kit | Keep conditional, 1 ask, skippable; the rest in Lector › Batería | | `[mine]`+`[theirs]` |
| ¡Todo listo! | Kill | | `[both]` |
| Verdict chip | Keep, redesigned (§v2 5) | | `[new]` |
| Quick sheet | Replace → editor (steppers, draft + Guardar) | | `[theirs]`+`[mine]` |
| *(new)* "Por qué" panel | Add (tap) | | `[theirs]` |
| Reader-down banner | Merge → paused chip + Lector | | `[mine]` |
| *(new)* Greeting "✓ Kompara listo" | Add, gated | | `[mine]` |
| E1 Lector dialog | Keep only where capture consent is needed (API <30 or a disabled silent lane) | | `[both]` |
| E3 `OcrConsentActivity` | Keep; return to a visible result | | `[theirs]` |
| E4 `ShareImportActivity` | Keep; opens Importar step 2 directly | | `[mine]` |
| Notif. watchdog + OCR stopped | Merge → "Kompara se apagó" / "…no está leyendo DiDi" | | `[both]` |
| Notif. OCR foreground | Keep (<30) → "Lector de Kompara activo" + "Detener" | | `[theirs]` |
| Notif. IMSS month-end | Keep, factual | | `[new]` |
| Notif. week close | Keep → opens that week | | `[both]` |
| *(new)* Day summary notif. | Add, "Tu resumen de hoy", no certainty claim | | `[theirs]`+`[mine]` |
| *(new)* Trial notifications | Add (start, 3 days before end) | | `[mine]` |
| Fiscal PDF | Keep (Premium) → "Resumen para tu contador" | | `[both]` |
| Share PNG | Keep; single renderer shared with the preview | | `[both]` |
| *(new)* Tus datos | Add | Privacy controls, "Borrar datos de este teléfono" | `[theirs]` |
| Debug Premium toggle | Debug builds only | | `[both]` |
| 43 + 12 dead strings, `HomeUiText.kt`, `HomeViewModel.kt`, second verdict palette | Delete | | `[mine]` |

---

### v2 §4. Screen-by-screen

#### 4.0 Shared contract

**Vocabulary (one per concept)** `[new]`, merging both proposals:

| Concept | Word in the UI | Never |
|---|---|---|
| Period result | **"Te quedan"** + a scope line underneath | "Ganancia neta" |
| Rate after running costs | **"neto"** (e.g. `$8.40/km neto`) | "neto" for after-commission |
| Money the app paid | **"Te pagó la app"** | "Bruto" |
| Commission | **"Comisión de la app"** | — |
| Verdict states | **verde / amarillo / rojo / sin color** | Conviene / Regular / veredicto |
| Driver's floor | **"tu mínimo"** ("Verde desde") | umbral |
| The reader | **"lector"** | "lector de pantalla", "OCR", "captura" (except in the disclosure) |
| Estimate | **"aprox."** in the scope line | — |

**Scope line rule** `[theirs]`: every period hero has a muted line directly under it, chosen from:
- "Después de gasolina y desgaste · aprox."
- "…y lo del coche · aprox."
- "Con costos típicos · aprox. · Ajustar" (until the driver confirms costs)

**Numbers:**
- Period totals in whole pesos.
- `$/km` with 2 decimals; `$/h` in whole pesos.
- Tabular figures.
- At most 3 numbers in the first viewport of any screen `[mine]`.
- Rate precision matches threshold precision `[theirs]`.

**Type and tokens:**
- Existing tokens; 16dp rhythm.
- No critical copy below 13sp; hero 44sp; chip hero 32sp `[both]`.
- New token `chipNeutral` slate-600 `#475569` for "sin color" states `[mine]`.
- Readiness uses emerald (good) or `error` red (broken); attention uses an accent-amber `#F59E0B` outline; verdict colours appear only on verdicts `[both]`.

**States everywhere:**
- Loading keeps the title and geometry and shows a static skeleton. It never shows `$0` in place of a known value `[theirs]`.
- Offline: local screens work; only network-dependent blocks show "Sin conexión. Lo intentamos cuando tengas datos." + "Reintentar" `[both]`.
- Locked: the driver's own value stays visible, only the city or archive side is masked `•••`, one benefit card per screen, never a blur wall, and **no lock on absent data** `[mine]`+`[theirs]`.

**Primary action:** at most one filled button. Read-only screens may have none, in which case the main interaction is a row or the hero itself `[theirs]`.

---

#### 4.1 Hoy (tab)

- **Purpose:** Is Kompara reading, and what do I have left today?
- **Hero:** "Te quedan hoy" `$1,240`.
- **Primary action:** depends on state.
  - Off: **"Encender lector"**.
  - First run on API 33+ without notification permission: **"Avísame si se apaga"**.
  - Paused: **"Reanudar"**.
  - Healthy: none; the hero card opens Tu día `[theirs]` (no filled button on a read screen).

**Layout:**
1. Title "Hoy", avatar on the right.
2. **Status row** (tappable → Lector) `[theirs]`:
   - Emerald dot + "**Lector funcionando** · última oferta hace 2 min"
   - Or emerald-outline dot + "**Lector preparado** · tu primera oferta lo confirma"
3. **Hero card** (→ Tu día):
   - "Te quedan hoy"
   - **`$1,240`**
   - Scope line: "Después de gasolina y desgaste · aprox."
   - "9 viajes · 5 h 20 min"
4. **Observation** (exactly one, factual, tappable → Tu día with a filter) `[new]`. The engine picks by priority:
   1. "**2 ofertas verdes se fueron sin tomar**" / "Sumaban $310."
   2. "**Tu mejor hora hoy: 18 a 19 h**" / "Te quedaron $232."
   3. "**Tomaste 3 ofertas rojas**" / "Te dejaron $4.10/km neto en promedio."
   - No imperatives, no "maneja más" `[theirs]`.
5. **Últimas ofertas** (3 trip-card rows) `[mine]`:
   - `[✓ Verde]` "$148 · $7.20/km neto" / "14:32 · La tomaste"
   - `[✕ Rojo]` "$62 · $3.10/km neto" / "14:18 · Se fue"
   - `[? Sin color]` "Falta distancia" / "14:05"
   - Text button "Ver tu día".

**States:**
- **Off:** the hero is replaced by a red-outlined card: "**Kompara está apagado**" / "Tu teléfono lo cerró. Sin él no ves el semáforo." with primary "Encender lector". If consent was never given, Aviso comes first. Today's figures stay visible underneath, muted.
- **Prepared, first run:**
  - "**Lector preparado**" / "Abre tu app de viajes. Tu primer semáforo aparece sobre la oferta, así:"
  - A static sample chip tagged "Ejemplo".
  - Primary "Avísame si se apaga" (API 33+). The text "Te avisamos si tu teléfono apaga Kompara."
  - Text button "Ver ejemplo".
- **Partial** (one app failing): amber-outline status "**DiDi necesita atención** · Uber funcionando" → Lector `[theirs]`.
- **Paused:** "**En pausa**" status + primary "Reanudar" `[theirs]`.
- **Typical costs:** the scope line reads "Con costos típicos · aprox. · **Ajustar**". After the first day, the observation slot shows once: "**¿Tu coche rinde unos 12 km por litro?**" with tonal "Sí" and text "Cambiar" `[new]`.
- **No trips yet today:** hero text "Aún no registramos viajes hoy" (no `$0`) `[theirs]`, plus "Ayer te quedaron $1,120" `[mine]`.
- **Only offers, no confirmed trips:** "Vimos 6 ofertas; aún no confirmamos viajes." `[theirs]`
- **Loading:** status row and hero skeleton.
- **Error:** "No pudimos abrir tu resumen." + "Reintentar".
- **Locked:** never.

**Removed vs today:** the weekly wall, the 2×2 grid, pills, platform chips, the paywall tease, three consejos, the streak, the goal bar, the week row (v1), and the filled "Ver mi día" (Codex v1).

**1-second glance:** a dot, one number, one coloured row of facts.

---

#### 4.2 Lector (from the Hoy status row, Cuenta, and recovery notifications)

- **Purpose:** Which apps Kompara can read right now, and the one fix if not.
- **Hero:** the status word.
- **Primary action:** only the first failing fix.

**Layout:**
1. ← "Lector".
2. Hero: dot + "**Funcionando**" / "**Preparado**" / "**Apagado**" / "**En pausa**".
3. Explanation line:
   - "La primera oferta confirma la lectura." (preparado)
   - "Última oferta leída: hace 2 min." (funcionando)
4. **Per-app rows** `[theirs]`:
   - "Uber · Lectura comprobada"
   - "DiDi · Esperando una oferta"
   - "inDrive · Aún no disponible" (until its parser ships; never claim coverage)
5. **Checklist** `[mine]`. Each row is ✓ or a tonal fix button:
   - "Permiso de Android · ✓" / [Activar]
   - "Batería sin restricciones · ✓" / [Revisar]. Opens a sheet with *this phone's* steps, one deep link each. These deep links are informational, never `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (`SettingsLinks.kt`).
   - "Aviso si se apaga · ✓" / [Activar]
   - API <30 / silent lane disabled only: "Permiso de captura · ✓" / [Continuar]. This shows E1, then the system prompt.
6. Text buttons:
   - "**Pausar lector**". It genuinely stops capture and the chip; Hoy shows "En pausa" `[theirs]`.
   - "Ver ejemplo"
   - "Qué lee Kompara" (the disclosure, read-only)

**States:**
- Starting: "Preparando lector…"; buttons are disabled `[theirs]`.
- Returning from settings: "Revisando…" for ≤1 s.
- Parser kill-switched: "DiDi · Esta versión aún no es compatible. Ya lo estamos arreglando." with no restart loop `[theirs]`.
- Consent canceled: "El lector sigue apagado" + "Intentar de nuevo" `[theirs]`.
- Offline: no change, since reading is local `[theirs]`.
- Locked: never.

**Removed:** the fake mock offer, four stacked buttons, "Lector para DiDi", and OCR/accessibility jargon in status copy.

**1-second glance:** a green dot and three app names with ticks.

---

#### 4.3 Tu día (from the Hoy hero, an observation, the week chart, the day notification)

- **Purpose:** What happened on a given day, offer by offer.
- **Hero:** "Te quedan" for that day.
- **Primary action:** none; rows open details.

**Layout:**
1. ← "Tu día". Day stepper "‹ Martes 21 oct ›" `[both]`.
2. Hero:
   - "Te quedan" **`$1,240`**
   - Scope line
   - "9 viajes · 5 h 20 min"
   - Text button "Cómo se calculó" `[theirs]`
3. "Turnos": "08:00 – 12:30 · 5 viajes · $640"; "17:10 – ahora · 4 viajes · $600".
4. "Ofertas", with filter chips "Todas (23)" | "Verdes que se fueron (2)".
   - Rows: verdict pill (glyph + word), "$fare · $X/km neto", then "hh:mm · La tomaste / Se fue / Sin confirmar" `[theirs]` lifecycle words.
   - Tap → **Detalle de oferta**.
5. Only when needed:
   - "El lector estuvo apagado de 13:10 a 14:00." `[theirs]`
   - "Sin turno detectado: aún no podemos calcular tus horas."

**Detalle de oferta (sheet)** `[both]`:
- Title "Oferta de las 14:32" + pill.
- Hero "Te quedarían **$114**", with "$7.10/km · $186/h neto (de la oferta)" `[theirs]` (the offer's rate, not the shift's).
- Rows:
  - "Te pagaba la app · $148"
  - "Distancia · 16 km con la recogida"
  - "Tiempo · 22 min"
  - "Gasolina y desgaste · −$34"
- Why line (`overlay_explain_*`): e.g. "Bien por km, pero lento: poco por hora."
- Outcome line.
- "El pago final puede cambiar." when no final amount is known `[theirs]`.
- Text button "¿Se leyó mal? Avísanos". It opens WhatsApp support prefilled with the time and derived numbers only, no screen content; the driver sends it.

**Cómo se calculó (sheet)** `[theirs]`:
- "Calculamos con los viajes que registró el lector. Algunos pagos pueden cambiar."
- "Gasolina y desgaste: tus costos guardados ($2.15 por km)." or "…costos típicos ($2.15 por km). Ajústalos en Tus costos."
- "Lo del coche: se resta una vez por semana, no en tu día."
- "Antes de impuestos."
- "No incluye viajes que Kompara no pudo leer."
- Button "Entendido".

**States:**
- Empty past day: "Este día no hubo ofertas."
- Imported-only day: "Este día viene de tu resumen importado; no tiene detalle por oferta." `[theirs]`
- Only offers: "Vimos ofertas, pero no confirmamos viajes." (no total) `[theirs]`
- Loading: skeleton.
- Locked (outside the free window): masked hero + the card "Revisa todas tus semanas · Prueba 14 días gratis".

**Removed:** five metric cards, the acceptance funnel, best hours (moved to the week).

**1-second glance:** one number and a column of coloured pills.

---

#### 4.4 Ganancias · Semana (tab, default)

- **Purpose:** My week as a timeline.
- **Hero:** "Te quedan esta semana".
- **Primary action:** none at the root; a bar opens a day.

**Layout:**
1. "Ganancias" + avatar. Segmented "**Semana** | Mes".
2. Period stepper "‹ 13 – 19 oct ›". Tapping the label opens **Elegir periodo** (newest first, grouped by month, locked periods show 🔒 "Premium") `[theirs]`. Current week: "13 – 19 oct · en curso".
3. Hero:
   - "Te quedan esta semana" **`$6,480`**
   - Scope line
   - "62 viajes · 41 h"
4. **Lo del coche** line, only if set `[new]`:
   - "Menos lo del coche ($2,000): **$4,480**"
   - A thin emerald bar with "Cubierto el miércoles ✓" (or "Te faltan $650 para cubrirlo")
   - If not set: text button "¿Rentas o pagas el coche? Agrégalo" → **Lo del coche** sheet.
5. **7-bar chart** (L M M J V S D) `[mine]`: today in emerald, future days outlined. A tap shows "Vie · $1,380"; a second tap opens Tu día.
6. Row "**Comparar esta semana** ›" → the Comparar tab, same week `[theirs]`.
7. "Tus mejores horas": "Viernes, 19 a 21 h · $340" (own trips) `[mine]`.
8. Text button "Cómo se calculó" `[theirs]`.
9. Outlined "**Compartir tu semana**" `[mine]`.
10. Text button "¿Te faltan viajes? Importa tu resumen" `[mine]`.

**Lo del coche (sheet)** `[new]`:
- Title "Lo que pagas por el coche cada semana".
- Field `$` "2,000"; helper "Renta, crédito o seguro. Lo restamos una vez por semana, no en cada viaje."
- Primary "Guardar"; text "No pago nada".
- After saving, the week scope line becomes "Después de gasolina, desgaste y lo del coche · aprox."

**States:**
- Empty week: "Esta semana aún no hay viajes" (chart outlines); the stepper keeps working.
- Imported week: tag "Importada", the bars are replaced by "Este resumen no incluye el detalle por día" `[theirs]`.
- Partial capture: "El lector estuvo apagado parte de la semana." `[theirs]`
- Locked (before the 4-week window): the period label stays visible, the hero is masked `$•••`, one card "**Revisa todas tus semanas** · Prueba 14 días gratis" `[theirs]` copy, `[mine]` mask.
- Loading: the period is kept.
- Error: other periods stay reachable.

**Removed:** Historial, Resumen de la semana, "Cómo ganaste" rates (now in Comparar), per-app rows (now in Comparar › Tus apps), the goal bar.

**1-second glance:** one number and the shape of the week.

---

#### 4.5 Ganancias · Mes

- **Purpose:** My month, and IMSS progress.
- **Hero:** "Te quedan en octubre".
- **Primary action:** none; the IMSS card is the main row.

**Layout:**
1. Segmented "Semana | **Mes**", stepper "‹ Octubre 2026 ›".
2. Hero "Te quedan en octubre" **`$24,310`**, scope line, "248 viajes · 160 h".
3. **IMSS card** (→ IMSS del mes; free) `[mine]`:
   - "IMSS: lo que llevas este mes"
   - "Uber · $5,200 de $8,364" with a mini bar
   - "DiDi · pasaste el mínimo"
4. "Semanas": rows "13 – 19 oct · $6,480 ›".
5. Row "**Resumen para tu contador** · PDF" (🔒 for free users) `[theirs]` naming.
6. Outlined "Compartir tu mes".

**States:** empty month, locked month (masked hero + card), loading, error.

**1-second glance:** the month's number and two IMSS bars.

---

#### 4.6 IMSS del mes (from the Mes card and the month-end notification)

- **Purpose:** How close my *recorded* income in each app is to the IMSS minimum.
- **Hero:** the per-app progress.
- **Primary action:** "**Preparar PDF para tu contador**" (Premium). When locked: "Prueba 14 días gratis".

**Layout:**
1. ← "IMSS del mes" + month chip "Octubre ▾".
2. One card per app (free):
   - "Uber"
   - Emerald bar, "**$5,200 de $8,364**"
   - "Te faltan $3,164 · quedan 11 días"
   - Muted: "A este ritmo llegarías a unos $9,100."
3. Once over the minimum: "**Pasaste el mínimo de este mes.** Confirma tu alta con la app o en el IMSS." There are no "cubierto / ya te toca" words `[theirs]`.
4. Basis line `[new]`: "Calculado con lo que te pagó cada app según Kompara. La app y el IMSS usan su propio cálculo." + text button "¿Cómo funciona?".
5. Premium section "**Lo que te retienen (aprox.)**": "≈ $612 este mes"; per app "Uber · ISR $98 · IVA $374"; muted "Con las tasas de ley para plataformas; tu constancia de la app es la que vale."
6. Primary "Preparar PDF para tu contador"; muted "Sirve también para pedir un crédito."
7. Footer: "Son estimaciones con tus registros, no asesoría fiscal."

**"¿Cómo funciona?" sheet:** three bullets (IMSS minimum per app; 2.1% ISR + 8% IVA withholding; give your RFC to each app). Legal review required, B-038.

**Launch gate** `[new]`: the income basis used for the bar must match the reform's definition, which is the law's own basis and not Kompara's cost-adjusted "neto". Until legal signs off, the bar uses "Te pagó la app" and the basis line says so.

**States:**
- Empty: "Este mes aún no hay ingresos registrados."
- Past month: "Pasaste el mínimo" / "No llegaste ($7,100 de $8,364)".
- Partial capture: "El lector estuvo apagado algunos días; puede faltar ingreso."
- Generating: "Preparando tu PDF…".
- Error: retry, keeping the month.
- Locked: the free tracker stays visible; the withholding figure is masked `≈ $•••` with one card.

**Removed:** "Cobertura IMSS" on a "Fiscal" tab, verdict-coloured statuses, the umbral jargon, seven paragraphs.

**1-second glance:** a bar per app.

---

#### 4.7 Comparar (tab)

- **Purpose:** One weekly comparison at a time: my apps against each other, or me against my city.
- **Hero:** one supported difference.
- **Primary action:**
  - Tu ciudad unlocked with a standing: "**Compartir mi lugar**".
  - Tu ciudad locked: "**Prueba 14 días gratis**".
  - Tus apps: none.

**Layout `[theirs]` structure + `[mine]` hero:**
1. "Comparar" + avatar. Shared period stepper "‹ 13 – 19 oct ›" (defaults to the last completed week with data).
2. Segmented "**Tus apps** | **Tu ciudad**".
3. Metric chips "**Por km** · Por hora · Por viaje". The default is the driver's deciding metric `[new]`.

**Tus apps** (own data; free inside the 4-week window):
- Hero: "**DiDi te dejó $1.20 más por km**"
- Two aligned bar rows: "Uber · $6.80/km neto", "DiDi · $8.00/km neto"
- Scope: "Tus viajes de esta semana, después de gasolina y desgaste."
- Qualification: "Manejaste en horarios distintos; no asegura que una app siempre pague más." `[theirs]`

**Tu ciudad:**
- Selectors "**CDMX ▾**" (asked on first open if unknown; doesn't change thresholds) and "**DiDi ▾**" `[theirs]`.
- **Standing** (only with a real cohort for this city, app, metric and week) `[mine]`:
  - "**Le ganas al 78% de los conductores de DiDi en CDMX**"
  - 20-person bar
  - Pill "Top 22%"
  - Basis: "Por km, después de gasolina y desgaste."
- **Aligned rows** (Premium):
  - "Tú en DiDi · $8.40/km"
  - "Promedio de DiDi en CDMX · $7.90/km"
  - "Promedio de Uber en CDMX · —" with "Uber no muestra km". Per spec, there's no Uber per-km benchmark.
- Text button "**De dónde salen estos datos**" `[theirs]`. Sheet: week, city, app, metric definition, expense basis, number of drivers (when available), exclusions.
- **Like-for-like rule** `[theirs]`: if the population figure is before costs, the driver's figure switches to the same basis and both rows read "**antes de tus gastos**".

**States:**
- Single app (Tus apps): "Esta semana solo registraste Uber." plus the link "Mira Tu ciudad".
- No personal data: "Necesitas una semana con viajes para comparar." `[theirs]`
- **No valid cohort:**
  - "Todavía no hay datos confiables de DiDi en CDMX. Te avisamos cuando los haya."
  - **No paywall here** (do not charge for absence) `[theirs]`.
  - Falls back to a self-comparison: "Contra tu semana pasada: +12% por km." `[mine]`
- Metric unavailable: "Falta distancia para comparar por km" + chip suggestion "Por hora" (explicit, never automatic) `[theirs]`.
- Locked (cohort exists, free user): the standing sentence stays free; rows show "Tú $8.40" with "Promedio •••" masked; one card "**Compara tu semana con tu ciudad** · Prueba 14 días gratis, sin tarjeta" `[mine]`+`[theirs]`.
- Offline: last benchmark with its real week label, or "Conéctate para cargar la comparación."
- Loading: filters keep their values.
- Error: personal rows still render.

**Removed:** the table, 30 cells, the legend, "Dónde puedes ganar más", commission ranking, synthetic standings.

**1-second glance:** one sentence and two aligned bars.

---

#### 4.8 Compartir (task; from the week, the month, Comparar, the week notification)

- **Purpose:** Send my week to my group.
- **Hero:** the exact PNG.
- **Primary action:** "**Compartir en WhatsApp**" `[mine]`; text button "Más opciones" (system chooser) `[theirs]`.

**Layout:**
1. ✕ "Compartir tu semana" / "tu mes".
2. Preview, rendered once by `ShareCardRenderer` and shown scaled `[both]`:
   - "K Kompara · 13 – 19 oct"
   - **Hero**: "Top 22% de DiDi en CDMX" (real cohort only); otherwise "62 viajes"; or the amount if shown.
   - Two facts: "Mejor día · viernes", "Mejor hora · 19 a 21 h" `[new]`
   - "kompara.mx"
3. Switch "**Mostrar montos**", off by default `[theirs]`.
4. Caption sent: "Mi semana manejando con Kompara: Top 22% de DiDi en CDMX. Mide la tuya: kompara.mx". It omits the rank when there is none.

**States:**
- Rendering: "Preparando tu tarjeta…".
- No eligible data: "Aún no hay nada que compartir esta semana."
- Error: retry.
- Cancelled: return to the same period.

**Removed:** the brag grid's "Mejor app" and "Racha", and the preview/export mismatch.

---

#### 4.9 Importar (task; from the week, the share sheet; needs an account)

- **Purpose:** Bring in weeks the reader didn't see.
- **Hero:** the review.
- **Primary actions:** "Elegir archivos", then "Guardar".

**Step 1** "Trae tu resumen":
- "Para semanas que Kompara no vio, como las de antes de instalarla."
- Three info rows:
  - "Uber · el PDF semanal de tu correo (el más completo)"
  - "DiDi · 2 capturas: Ganancias y Tablero"
  - "inDrive · 1 captura de tu resumen"
- **Privacy line** `[new]`, prominent rather than muted: "**Este archivo sí se sube** a Kompara para leer tus números. Es el resumen que tú eliges, no tu pantalla." Add "Lo borramos al terminar." only once backend retention is confirmed.
- Primary "Elegir archivos".

**Step 2** "Leyendo tu archivo": a checklist driven by real events ("Subiendo", "Leyendo tus números", "Armando tu semana") with a linear bar `[mine]`.

**Step 3** "Revisa tu semana":
- "13 – 19 oct · Uber".
- Rows "Te pagó la app", "Comisión de la app", "Viajes", "Horas", "Km". A missing field shows "No venía en tu archivo".
- Primary "**Guardar**" commits the already-parsed result; there is no second parse `[mine]`.
- Text "Descartar".

**After save:** a snackbar "Listo, tu semana está en Ganancias", and the app opens that week.

**States:**
- Shared-in: starts at Step 2.
- Signed out: "Para importar necesitas tu cuenta" + "Entrar con WhatsApp".
- Missing DiDi pair: "Falta la captura del Tablero" + "Agregar".
- Unreadable file: "No pudimos leer este archivo" + guidance + "Elegir otro".
- Week exists: "Ya tienes esta semana. ¿La reemplazamos?"
- Network error.

**Removed:** the 4-card picker, the double parse, the cosmetic timer, "con IA".

---

#### 4.10 Cuenta (avatar hub)

- **Purpose:** Everything about me and how Kompara behaves.
- **Hero:** plan status.
- **Primary action:** none.

**Layout:**
1. ← "Cuenta".
2. Profile card → Tu perfil: "+52 55 1234 5678 · CDMX" with the plan pill "Gratis" / "Premium · prueba hasta el 3 nov" / "Premium". Signed out: "**Guarda tu configuración** · Entrar con WhatsApp".
3. Free users only: tonal row "Kompara Premium · 14 días gratis, sin tarjeta" → Paywall. Trial or subscribed: "**Tu plan**" row instead `[theirs]`.
4. "Tu manejo":
   - "Tu semáforo · Verde desde $8.00/km"
   - "Tus costos · $2.15 por km (típicos)"
   - "Lo del coche · $2,000 a la semana" (or "Sin agregar")
   - "Lector · Funcionando"
5. "Kompara": "Avisos", "Invita y gana · 7 días por amigo", "Ayuda", "Tus datos".
6. Footer: "Kompara {versión} · Términos · Privacidad".

**Tu plan** `[theirs]`:
- Status; trial end date ("Tu prueba termina el 3 de noviembre. No habrá cobro.") or the renewal date and price.
- "Administrar suscripción" (Play).
- "Restaurar compra".

**Tus datos** `[theirs]`:
- "**En este teléfono:** cifras de tus ofertas y viajes (pago, km, tiempo, color). Nunca imágenes ni textos de tu pantalla."
- "**En tu cuenta:** tu número, tu configuración y tus totales por semana."
- "**Importaciones:** el archivo que tú subes."
- Button "Borrar datos de este teléfono" (with a confirm dialog).

---

#### 4.11 Tu semáforo (from Cuenta, the chip editor's "Más ajustes", Hoy's "Ver ejemplo")

- **Purpose:** Set the one number that turns an offer green.
- **Hero:** "Verde desde **$8.00/km** neto".
- **Primary action:** "**Guardar**" `[theirs]` (explicit, the same model as the overlay editor).

**Layout:**
1. ← "Tu semáforo".
2. **Live preview**: three real chips on sample offers ("Viaje largo", "Viaje corto", "Recogida lejos") that recolour as the draft changes `[mine]`. This replaces the Simulator. It is a preview, so nothing is saved until Guardar `[theirs]`.
3. Stepper "Verde desde" **[−] $8.00/km [+]** (step $0.50 / $5 per hour).
4. "Decide por: **Km** | Hora" `[both]`. Switching never overwrites the other metric's saved values `[theirs]`.
5. Expander "**Cuándo sale rojo**" → stepper "Rojo por debajo de $6.00"; "Entre los dos, amarillo." `[theirs]`
6. Expander "La otra cifra (▼)": the other metric's floors, used only for the ▼ marker `[mine]`.
7. Text button "Volver al valor inicial ($8.05/km)". There's no "típico de CDMX" claim until it's substantiated `[theirs]`.
8. Primary "Guardar". Leaving with unsaved changes asks "¿Guardar tu semáforo?" with "Guardar" / "Descartar".

**States:**
- First visit: the values are tagged "Valor inicial".
- Saved: snackbar "Semáforo guardado".
- Invalid (red above green): auto-clamped, with a hint.

---

#### 4.12 Tus costos (from Cuenta, the "Ajustar" scope link, the km/L card)

- **Purpose:** Make "neto" true for my car.
- **Hero:** "Gasolina y desgaste: **$2.15 por km**".
- **Primary action:** "Guardar".

**Layout:**
1. ← "Tus costos".
2. Hero card (live): "En un viaje de 12 km son unos $26."
3. "Gasolina | Eléctrico".
4. Gasoline fields:
   - "Rendimiento" `12` "km por litro"; helper "¿No sabes? Deja 12: es lo típico de un sedán."
   - "Precio de la gasolina" `$24.50`; helper "Precio típico en tu ciudad", only when sourced from remote config, otherwise "Escribe lo que pagas".
5. EV fields: "Consumo" kWh per 100 km; "Costo de carga" per kWh.
6. Expander "Mantenimiento": `$0.80` per km, "Llantas, aceite y servicios."
7. Text button "**Ya sé mi costo por km**" → a single field `[theirs]`.
8. **Save scope** `[new]`, one muted line: "El rendimiento corrige todo tu historial; el precio de la gasolina cuenta desde hoy."
9. Primary "Guardar" → snackbar "Listo. Recalculamos tus viajes."

**States:**
- Validation: km/L outside 4–40 shows "Revisa este número".
- EV values pre-filled on edit.
- Saving.
- Error.
- Typical values are tagged "(típico)" until edited or confirmed.

---

#### 4.13 Avisos

Switch rows, each with a one-line description:
1. "**Si Kompara se apaga**": "Para que lo enciendas cuando te estaciones." (on)
2. "**Al abrir tu app de viajes**": "Un '✓ Kompara listo' de 2 segundos, una vez al día." (on) `[mine]`
3. "**Tu resumen de hoy**": "Lo que te quedó, cuando dejas de recibir ofertas un rato." (on) `[theirs]`+`[mine]`
4. "**Tu semana, los lunes**" (on)
5. "**IMSS a fin de mes**" (on)

When the system blocks notifications: a banner "Tienes los avisos apagados en tu teléfono" + primary "Activar avisos". The sharing reminder is removed `[theirs]`.

---

#### 4.14 Tu perfil and Entrar con WhatsApp

**Tu perfil:**
- WhatsApp (read-only; helper "¿Cambiaste de número? Escríbenos por WhatsApp.")
- "Tu nombre (opcional)"
- "Tu ciudad", accented ("Valle de México (CDMX y Edomex)", "León", "Querétaro"…); helper "Con ella te comparamos en Tu ciudad."
- Primary "Guardar cambios".
- Outlined "Cerrar sesión" → dialog "¿Cerrar sesión?" / "El lector sigue funcionando en este teléfono." `[theirs]`
- 24dp gap, then red text "Eliminar mi cuenta" → "¿Eliminar tu cuenta?" / "Se borrarán tu cuenta y los datos guardados en ella. Tu historial en este teléfono se conserva. Si tienes una suscripción en Google Play, debes cancelarla ahí." + "Administrar suscripción" `[theirs]`.

**Entrar con WhatsApp:** two screens (number → code, auto-submit on the 6th digit `[mine]`). The title depends on context:
- From onboarding: "¿Guardamos tu configuración?"
- From the trial: "**Activa tu prueba con WhatsApp**" `[theirs]`
- After a reinstall: "Entra con tu WhatsApp".

The code screen keeps "Cambiar número" · "Reenviar en 30 s". Errors keep the input and never block the reader.

---

#### 4.15 Invita y gana `[mine]`

- Code hero `PANA4821`.
- "Por cada conductor que entre con tu código, **los dos ganan 7 días de Premium**." (The number of days comes from the backend.)
- Primary "Compartir por WhatsApp"; outlined "Copiar código".
- Stats line "Invitaste a 3 · ganaste 21 días".
- Collapsed "¿Te invitaron? Escribe su código". It opens pre-filled from a deep link.

States: loading, error + retry, redeem error, success "¡Listo! Ganaste 7 días de Premium."

---

#### 4.16 Ayuda

Single-open accordion, with a pinned primary "**Escríbenos por WhatsApp**" `[mine]` and the secondary "Revisar mi lector" `[theirs]`:
1. "**No aparece el semáforo**": "Entra a Lector y revisa que todo tenga ✓. En Xiaomi, Oppo, Vivo o Huawei, revisa la batería."
2. "**¿Qué significan los colores?**": "Verde: llega a tu mínimo. Amarillo: entre tu mínimo y tu rojo. Rojo: abajo. Gris: no pudimos calcularlo. El número es lo que te queda por km o por hora, ya sin gasolina ni desgaste."
3. "**¿Qué guarda Kompara?**": "Lee la oferta en tu teléfono y borra la imagen al momento. Guarda solo cifras (pago, km, tiempo, color). Nunca toca nada por ti."
4. "**¿Me pueden bloquear Uber o DiDi?**": "Las apps de viajes podrían no permitir herramientas como esta. Kompara solo lee y nunca acepta ni rechaza por ti; usarla es tu decisión."
5. "**¿Cuánto cuesta?**": "El semáforo, tu día, tus últimas 4 semanas, tus apps y tu IMSS son gratis. Premium cuesta $79 al mes: tu ciudad, todo tu historial y el PDF para tu contador."

---

### v2 §5. The offer moment

**Laws** (both proposals):
1. Inform, never act (`verifyReadOnlyCapture`).
2. One second: shape + colour, then the number.
3. Never cover the host's controls: a no-go zone over the bottom 35% and the fare region; touchable area = visible area.
4. Unknown is grey, never red.
5. Silent during the trip.
6. Never retain a previous offer's numbers `[theirs]`.

**Collapsed chip** `[mine]` geometry with `[theirs]` text rules:

```
┌───────────────────────────┐
│ ✓  $9.40 /km           K │  glyph 20dp · hero 32sp Black tabular · unit 14sp · K 10dp @60%
│    neto · $212/h          │  15sp SemiBold; "▼ $96/h" when the other metric is below its own red floor
└───────────────────────────┘   ~150×72dp · radius 16 · shadow 6dp · min 132 / max 176dp · grows (never shrinks) with font scale
```

- No brand strip `[both]`. Verdict palette from one source (delete `overlay/VerdictColors.kt`).
- The hero is always the **deciding** metric; it never swaps `[theirs]`.

**State contract v2:**

| State | Colour | Row 1 | Row 2 | Tap | Long-press |
|---|---|---|---|---|---|
| Verde | verde | ✓ `$9.40` /km | `neto · $212/h` | por qué | editor |
| Amarillo | amarillo (dark text) | ⚠ `$7.10` /km | `neto · $164/h` | por qué | editor |
| Rojo | rojo | ✕ `$4.30` /km | `neto · ▼ $96/h` | por qué | editor |
| Deciding input missing (e.g. distance when deciding by km) | **sin color** (`chipNeutral`) | ? `Falta distancia` | `$186/h neto · sin color` `[new]` | por qué (names the missing field) | editor |
| Non-deciding input missing (e.g. time when deciding by km) | verdict as computed | ✓ `$9.40` /km | `neto · sin tiempo` `[theirs]` | por qué | editor |
| Pickup leg unknown | sin color | ? `Falta la recogida` | `sin color` `[theirs]` | por qué | — |
| Fare unreadable / layout unknown | sin color | ? `Sin leer` | `revisa la oferta` | — | — |
| Card shows a price that isn't the driver's payment (per parser spec) | sin color | ? `Pago sin confirmar` | `sin color` `[theirs]` | por qué | — |
| Parsing takes >300 ms | sin color | `Leyendo oferta…` `[theirs]` | — | — | — |
| Greeting (once/day, self-check passed, supported app only) | slate + emerald 1dp outline | ✓ `Kompara listo` | — | pass-through | — |
| Paused (capture lost on <30 / silent lane disabled) | sin color | `Kompara en pausa` | `toca cuando te estaciones` | → E1 → system prompt | — |
| Hidden | — | — | — | — | — |

**Costs:** the chip always computes with saved costs or the labelled typical costs `[mine]`. "Faltan tus costos" is never a chip state. The app's scope lines say "costos típicos" until the driver confirms.

**TalkBack:**
- "Semáforo verde. 9 pesos 40 netos por kilómetro; 212 por hora. Toca para ver por qué."
- "Sin color: falta la distancia."

**Position and motion** `[mine]`+`[theirs]`:
- Default top-end, 12dp inside, below the status bar, clamped out of the fare region and the bottom-35% no-go zone.
- Position is remembered per host app.
- Drag snaps to the nearest safe edge, and a drag never triggers a tap.
- Appears ≤150 ms after the card, with a 100 ms fade; colour crossfades in 120 ms on re-parse.
- Hides 500 ms after the card leaves.
- No pulse, beep, vibration or celebration.

**"Por qué" panel (tap)** `[theirs]` with `[new]` mitigations:
- Lives in its own overlay window anchored beside the chip and opening toward the screen centre, so the chip window never relayouts.
- Clamped out of the no-go zone, and not modal.
- Content (max 4 lines + 2 buttons):

```
⚠ Amarillo · $7.10/km neto
Te quedarían $114
Te paga $148 · gasolina y desgaste −$34
16 km con la recogida · 22 min
Verde desde $8.00/km
[Ajustar mínimo]                Cerrar
```

- When fixed costs are set, a muted 5th line reads "Lo del coche se resta en tu semana."
- In a grey state, line 1 names the gap: "Sin color: no pudimos leer la distancia."
- Closes when the offer leaves or changes (never swaps content while the driver is reading), after 8 s idle, or on "Cerrar".

**Editor** (from "Ajustar mínimo" or a long-press) `[theirs]` model with `[mine]` controls:

```
Tu mínimo para verde
Ajústalo cuando estés estacionado.
   [ − ]    $8.00 /km    [ + ]          56dp targets, 28sp value
Con este mínimo, esta oferta saldría: verde
Cuándo sale rojo ›
[Guardar]                       Cancelar
```

- Changes are a draft; the live line previews the colour of *this* offer.
- "Guardar" commits the rule for all apps and shows "Semáforo guardado" inside the overlay for 1 s. "Cancelar" discards.
- If a new offer arrives, the editor closes without saving `[theirs]`.
- "Más ajustes" (text) opens Tu semáforo. This is the only path that leaves the host app, and only on an explicit tap.

**Degraded states** `[both]`:
- **API 30+, service killed:** nothing can be drawn over the host, so the channel is the notification "**Kompara se apagó**" / "Tu teléfono lo cerró. Tócalo para encenderlo cuando te estaciones." Hoy and Lector turn red.
- **One app failing:** "**Kompara no está leyendo DiDi**" / "Revisa el lector cuando te estaciones." It is deduplicated, one per interruption `[theirs]`.
- **Parser kill-switched:** chip hidden for that app; Lector says "Esta versión aún no es compatible".
- **No internet:** nothing changes, since reading is local.

**After the offer:**

| Moment | Behaviour | Copy (unlocked / lock screen) | Tap → |
|---|---|---|---|
| During or after a trip | Nothing on screen `[both]` | — | — |
| Day summary (≥60 min with no offers and ≥2 trips; max 1/day) | Silent, channel "Resúmenes" | "**Hoy te quedan $1,240**" / "9 viajes · 5 h 20 min. Toca para ver tu día." · lock screen: "Tu resumen de hoy" `[theirs]` (no "turno cerrado" certainty) | Tu día |
| Week close (Mon 9:00) | "Resúmenes" | "**Tu semana: te quedan $6,480**" / "62 viajes. Le ganaste al 78% de los conductores de DiDi en CDMX." (only with a real cohort) · lock: "Tu semana está lista" | Ganancias · Semana |
| First week close (account holder) | + trial start | "**Tu primera semana con Kompara está lista**" / "Tienes 14 días de Premium gratis. Sin tarjeta y sin cobro al terminar." | Comparar › Tu ciudad (or Tus apps) |
| First week close (no account) | invitation | "**Tu primera semana está lista**" / "Activa 14 días de Premium gratis con tu WhatsApp." | Entrar → trial |
| Month close (day 1) | "Resúmenes" | "**Octubre: llevas $9,100 registrados en Uber**" / "Pasaste el mínimo del IMSS ($8,364). Confirma tu alta con Uber o en el IMSS." · lock: "Tu resumen del mes está listo" | IMSS del mes |
| Trial ending (3 days before) | once | "**Tu prueba termina el jueves**" / "No habrá cobro. Si quieres seguir con Premium, toca aquí." | Paywall |
| API <30 capture running | ongoing | "**Lector de Kompara activo**" + action "Detener" `[theirs]` | Lector |

---

### v2 §6. Onboarding

**Target: 4 screens** on a stock Android 13+ phone for a driver who skips the account. **6 screens** with the account (number + code). Add one screen on Xiaomi, Oppo, Vivo, Huawei or Samsung (battery) and one on API <30 (capture). Each screen has one ask. The progress chrome is a thin bar only.

| # | Screen | Single ask | Copy | Tag |
|---|---|---|---|---|
| 1 | **Ejemplo** | "Usar en mis viajes" | Title "**Mira lo que te deja cada viaje.**" A neutral sample offer "Oferta de ejemplo · $148 · 16 km con la recogida · 22 min", and the **real** chip fades in. One worked line underneath: "Te pagan $148, pero gastas $34 en gasolina y desgaste: **te quedan $114**, o $7.10 por km." Tonal "Ver otra" cycles verde → amarillo (▼) → rojo → sin color. Optional: tap the chip to open the real "por qué". The demo never touches real settings. Primary "**Usar en mis viajes**"; text "Ya tengo cuenta". | `[theirs]` title, verb and worked math + `[mine]` real chip |
| 2 | **Aviso** (Play prominent disclosure; fits 360×640 without scrolling) | "Acepto. Activar lector" | Title "**Antes de activar el lector**". "**Qué lee:** la oferta en tu app de viajes (pago, distancia, tiempo). Para leerla toma una imagen de la pantalla." · "**Qué guarda:** la imagen se lee en este teléfono y se borra al momento; no se guarda ni se envía. Solo guardamos cifras de tus viajes." · "**Qué hace:** te muestra el semáforo. No acepta, rechaza ni toca nada por ti." · "**Tú decides:** las apps de viajes podrían no permitir herramientas como esta. Puedes apagarlo cuando quieras." Muted: "Usa el servicio de accesibilidad de Android." Primary "**Acepto. Activar lector**"; text "Ahora no" → Hoy (off), no punitive screen. Legal review B-038. | `[theirs]` structure + `[mine]` screenshot truth |
| 3 | **Activar** | "Abrir ajustes" | Title "**Activa el lector**". On API 33+ the button deep-links **straight to Kompara's own toggle** (`Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS`, to verify per OEM), so the steps are "1. Activa **Kompara** · 2. Toca **Permitir** · 3. Regresa aquí". On older versions, step 1 is "Busca **Kompara**" (`SettingsLinks.kt` opens the generic list today). Primary "**Abrir ajustes**". On return, if granted: a 1 s "✓ **Lector preparado.** Tu primera oferta lo confirma." then auto-advance. If not granted: "Todavía no está activado" + "¿No encuentras Kompara?" (an OEM hint sheet). Text "Ahora no". | `[new]` deep link, `[theirs]` "preparado" |
| 4 | **Batería** (conditional by `Build.MANUFACTURER`) | "Abrir ajustes" | Title "**Que tu teléfono no apague Kompara**". One instruction per OEM. Xiaomi: "Activa **Inicio automático** para Kompara." Samsung: "Pon Kompara en **Sin restricciones**." Primary "Abrir ajustes" (informational deep link); after returning it reads "Ya quedó". Text "Saltar". The other steps live in Lector › Batería. | `[mine]` conditional + `[theirs]` optional |
| 4b | **Captura** (API <30 or a disabled silent lane only) | "Continuar" | "**Este teléfono necesita otro permiso.**" / "Android te pedirá permiso para capturar la pantalla. Kompara la lee aquí y no la guarda ni la envía. Si reinicias el teléfono, te lo volverá a pedir." Primary "Continuar" → system prompt; text "Ahora no". | `[theirs]` |
| 5 | **Tu cuenta** (optional) | "Mandar código" | Title "**¿Guardamos tu configuración?**" / "Con tu WhatsApp respaldamos tu semáforo y tus totales, y activas **14 días de Premium gratis** cuando cierres tu primera semana. Sin tarjeta y sin cobro al terminar." Field `+52`. Muted "Te mandamos un código por WhatsApp. No compartimos tu número." Primary "**Mandar código**"; text "**Ahora no**" → Hoy. | `[new]` |
| 6 | **Código** (only if 5 continues) | 6 digits | "**Escribe el código**" / "Te lo mandamos por WhatsApp al +52 55 1234 5678." Auto-submit on the 6th digit; "Autocompletar" if WhatsApp one-tap is configured. "Cambiar número" · "Reenviar en 30 s". A network error offers "Reintentar" and "Seguir sin cuenta". → Hoy. | `[mine]` |

**After onboarding:**
- Hoy opens in "**Lector preparado**" with the sample chip. Its primary action is "**Avísame si se apaga**" (API 33+). That is the single notification ask, made in context `[mine]`.
- City comes from the area code when a number was given (55/56 → Valle de México, 81 → Monterrey, 33 → Guadalajara, 222 → Puebla-Tlaxcala, 722 → Toluca, 664 → Tijuana, 477 → León, 442 → Querétaro, 999 → Mérida, 998 → Cancún). Otherwise thresholds start at "valor inicial" and the city is asked on the first visit to Tu ciudad `[mine]`+`[theirs]`.
- Costs start as labelled typical values. The one-time km/L card arrives after the first day `[new]`.

**The aha:**
- The worked math on screen 1 (~5 s).
- "Lector preparado" in ≤60–90 s.
- The greeting "✓ Kompara listo" the first time a ride app opens.
- The real chip on the first offer.
- Hoy's status flips to "**funcionando**" `[theirs]`.

**Compared with GigU** (phone → WhatsApp code → name):
- We keep the WhatsApp OTP and drop the name.
- We move the account after the value and make it optional, because our value is local.
- We drop the platform pick, because the reader detects which apps are actually used.

---

### v2 §7. Monetization and paywall

**The line:**

| | **Gratis** | **Premium ($79/mes; the actual Play price is shown)** |
|---|---|---|
| Offer | Reader, chip, por qué, editor, semáforo, costs | — |
| Day | Hoy, Tu día, offer detail, day summary | — |
| History | **This week + 3 previous calendar weeks** `[new]`; the current month | Every earlier week and month `[both]` |
| Your apps | Comparar › Tus apps inside the free window `[mine]` | Tus apps for older weeks |
| Your city | The cohort standing sentence + bar (real cohorts only) `[mine]` | Aligned rows vs. city averages, the provenance details, every metric `[both]` |
| IMSS / fiscal | IMSS progress per app + month notification `[mine]` | Withholding estimate + **PDF para tu contador** `[both]` |
| Account things | Referral, import (with an account) | — |

**Where the lock sits:** only on Comparar › Tu ciudad rows, periods older than the window, and the PDF. There is no lock on Hoy, Tu día's current window, the chip, setup or recovery `[both]`. **No lock on absent data**: if a city cohort doesn't exist, the screen says so and shows no paywall `[theirs]`.

**Tease:**
- The driver's own number is visible; only the comparison or archive figure is masked `•••` with 🔒 `[mine]`.
- One readable benefit card per screen, titled by intent `[theirs]`:
  - "Compara tu semana con tu ciudad"
  - "Revisa todas tus semanas"
  - "Prepara tu resumen para tu contador"
- Fictional values appear only inside a card fully labelled "Ejemplo" `[theirs]`.

**Paywall screen** (full-screen task):
1. ✕.
2. Title by intent `[theirs]`.
3. One personal line, only when true `[mine]`: "Esta semana te quedaron $6,480. Ve cómo vas contra los conductores de DiDi en CDMX."
4. Benefits, **listing only what exists for this driver** `[theirs]`. For example, if their city has no cohort: "Tu ciudad: aún sin datos confiables en Mérida; te avisamos cuando los haya."
5. Price card "**$79 al mes**" / "≈ $18 a la semana · cancela cuando quieras" `[mine]`.
6. Primary, by state:
   - "**Empezar prueba gratis**" (account, eligible)
   - "**Activa tu prueba con WhatsApp**" (no account)
   - "**Suscribirme por $79 al mes**" (trial used)
7. Under it: "**14 días gratis. Sin tarjeta. No habrá ningún cobro al terminar.**" `[theirs]`
8. "Restaurar compra" · "Términos" · "Privacidad". Purchase footer: "Se renueva cada mes hasta que canceles en Google Play."

States: loading the price, Play unavailable, purchase pending, cancelled (return with state preserved), restore results, and an already-subscribed driver redirected to **Tu plan** `[theirs]`.

**Trial:**
- A server-granted entitlement tied to the verified number, not a Play trial, so "sin tarjeta" and "sin cobro" are both true `[both]`. Validate with Play's payments policy.
- **Account holders:** auto-start at the first week close that has Premium content, announced by notification `[mine]`.
- **Everyone else:** an explicit start `[theirs]`.
- Never consumed on an empty week `[theirs]`.
- Countdown: the date in Tu plan, plus one notification 3 days before the end. No daily pressure `[theirs]`.
- After it ends: "Terminó tu prueba. Tu lector, tu día y tus últimas 4 semanas siguen gratis."

**Verification:** `NEEDS_VERIFICATION` never locks a paying driver. If the data team needs verified weeks for cohort quality, it gates *contribution* only, with a one-line note in Tu ciudad: "Importa tu resumen para que tu semana cuente en el promedio de CDMX." `[mine]`

**Why a free driver feels complete:** every moment of the day closes for free: offer, day, week, last month, own apps, IMSS. Premium answers a different question, "¿y contra los demás, y para el contador?" `[mine]`. Codex's warning stands as a business risk: if city cohorts stay sparse, Premium = history + PDF, and the price test (§9 in Round 1) must include that scenario `[theirs]`.

---

## 5. v2 scored against the rubric (`rubric-uber-bar.md`)

| # | Criterion | Score | One line |
|---|---|---|---|
| 1 | One job per screen | **9** | Every screen answers one question with one hero; Comparar shows one metric at a time; Hoy has no week wall. |
| 2 | One primary action | **8** | At most one filled button, placed at the thumb and state-dependent. Several read screens deliberately have none, relying on the hero or rows, which a strict reading of the rubric may mark down. |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar, max depth 2, a shared period across Ganancias and Comparar, no tab for a state or a chore. |
| 4 | Numbers discipline | **8** | ≤3 numbers in each first viewport, one number style, one metric at a time. The chip carries 2 rates and Tu día's offer list is dense by nature. |
| 5 | Copy | **8** | One vocabulary, scope lines, no coaching, plain disclosure. "neto", "Te quedan", "aprox." and "sin color" still need validation with drivers. |
| 6 | States are designed | **9** | Preparado vs funcionando, grey chip states, partial capture, imported weeks, no-cohort, locked, offline. Every one has copy and a next step. |
| 7 | Time-to-value | **7** | A simulated verdict in ~5 s and "preparado" in ~60–90 s, but a real verdict still needs a trip to Android settings and, on some OEMs, a battery screen. |
| 8 | Consistency and feel | **8** | One chip component across demo, preview and overlay; one renderer; one palette; an explicit-save editor in both places. Motion and haptic identity are not specified yet. |
| | **Total** | **66 / 80** | Hard fails: none (no auto-action, no logos, every driving surface passes the 1-second test, no paywall blanks the free product, one ask per onboarding screen). |

**Criteria still below 8, and exactly what would lift each:**
- **Time-to-value (7 → 8+):**
  1. Ship the API 33+ `ACTION_ACCESSIBILITY_DETAILS_SETTINGS` deep link (removes the "Busca Kompara" step); verify it on Samsung, Xiaomi and Motorola.
  2. Auto-detect the grant and auto-advance with no "Continuar" tap (in spec; it must survive OEM task kills, so persist the funnel step).
  3. Show the battery screen only when an OEM restriction is *detected* (e.g. `isBackgroundRestricted()`), not by brand alone.
  4. Measure install → "preparado" in the closed beta; the target median is ≤75 s.

**At 8 but fragile:**
- **Copy:** run the 1-second chip test and a 10-driver comprehension test on "te quedan / neto / aprox. / sin color" before freezing the strings.
- **Consistency and feel:** a motion and haptic spec (a single 120 ms crossfade vocabulary and no haptics on the overlay) and a token audit that fails CI if `:overlay` defines its own colours.
- **One primary action:** if the scorer requires a filled button, give Hoy a state-dependent filled primary in every state, e.g. "Ver tu día" only after 18:00 or after the day summary fires. I'd rather not.
- **Numbers:** test whether the chip's second rate improves decisions (residual R3).

---

## 6. Residual disagreements (not resolved here) and the evidence that would settle each

| # | Disagreement | My position | Codex's position | What settles it |
|---|---|---|---|---|
| R1 | **Verdict cue on the chip:** glyph + colour words vs the word "Conviene / Regular / No conviene" | Glyph; colour vocabulary | Word | 1-second exposure test at 60 cm on a mount (10–12 drivers, including ≥3 with colour-vision deficiency, day and night): accuracy of take/pass vs their stated floor, and the false-"bad" rate on grey states. Also ask drivers whether "No conviene" feels like an order. |
| R2 | **Typical default costs vs no net verdict until costs are entered** | Typical, labelled | No verdict without costs | Beta cohort A/B: % of drivers whose first real offer shows a coloured chip; the share who later edit costs; and the median verdict flip rate when typical costs are replaced with real ones (if under ~5% of offers flip, typical defaults are safe). |
| R3 | **Second rate on the collapsed chip** | Keep `$/h` (or `$/km`) small | One rate only | The same 1-second test with one rate vs two: decision accuracy and time to decision. Keep two only if accuracy doesn't drop. |
| R4 | **Before-shift greeting "✓ Kompara listo"** | Keep, gated | Stay silent | Beta A/B: median time to discover a dead reader (from the first offer missed to "Encender"); the opt-out rate in Avisos; complaints. Kill it if opt-out exceeds 25%. |
| R5 | **Trial auto-start for account holders** | Auto at the first week close with content | Explicit only | A/B on paid conversion at day 30 and on "¿me van a cobrar?" support contacts. Auto wins only if conversion rises with no increase in billing complaints. |
| R6 | **Tus apps free (inside the window) vs Premium** | Free; it's the driver's own data | Premium | A paywall fake-door test: does locking Tus apps raise trial starts more than it lowers Comparar tab visits and week-2 retention? |
| R7 | **IMSS income basis** (not really a design dispute) | Ship the tracker after legal confirms the basis | Remove it | **Legal (B-038):** which income the reform counts per platform, and whether Kompara's recorded "Te pagó la app" is a defensible proxy. If not, the tracker shows only "Te pagó la app en octubre" with no threshold line. |
| R8 | **Recompute history when efficiency changes** | Yes for km/L, no for price | Only going forward | A data check of how often drivers correct km/L in their first week (if often, the old history was wrong and must be fixed), plus 5 interviews on whether a changed past total feels like a bug. |
