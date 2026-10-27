# Kompara — Round 2: cross-critique and converged proposal v2

**Final direction: Hoy · Ganancias · Comparar.** Keep the reader quiet, make its calculation trustworthy, and let the driver review money without decoding a dashboard.

I change my Round 1 position on the chip’s vocabulary and tap behavior, voluntary imports, the free IMSS utility, and the free history window. I retain the minimal home, optional signup, stable deciding unit, explicit trial activation, and strict comparison basis.

This is a design specification, not a claim that the current implementation supports every behavior or that the proposal has passed driver testing.

Decision labels:

- **[mine]** — retained from Codex Round 1.
- **[theirs]** — adopted from Opus Round 1.
- **[new]** — a synthesis, correction, or newly explicit requirement.

All example amounts are illustrative. Spanish strings are proposed product copy.

## Evidence register

References below use these short names plus the relevant section or screen.

| Reference | Source |
|---|---|
| **C1** | [Codex Round 1](./r1-codex.md) |
| **O1** | [Opus Round 1](./r1-opus.md) |
| **Inventory** | [Complete screen inventory](./inventory.md) |
| **Brief / Rubric** | [Original brief](./brief-round1.md), [Uber-class rubric](./rubric-uber-bar.md) |
| **Design / Voice** | [Design principles](../../docs/design-principles.md), [Design system and brand voice](../../design-system/README.md) |
| **Parked chip** | [Why tap-to-expand was removed](../../docs/parked/verdict-chip-tap-to-expand.md) |
| **Compare** | [Comparar redesign specification](../../docs/comparar-redesign-spec.md), especially §§0–2 |
| **Capture** | [Play-compliance posture](../../docs/play-compliance.md), [Android technical design](../../docs/android-technical-design.md), §§0–1 |
| **Market** | [Competitive analysis](../../docs/competitive-analysis.md), especially §5 |
| **Imports** | [Earnings import strategy](../../docs/earnings-import-strategy.md), especially governing revisions in §0.5 |
| **Account** | [Account, verification and onboarding design](../../docs/account-onboarding-verification-design.md), especially governing revisions in §0.5 |

The latest user clarification governs the on-device boundary: **reader-captured screen content stays on-device; a driver deliberately uploading a weekly document is a separate, disclosed action.** The requested redesign also authorizes proposing changes to existing product decisions; those changes are identified rather than presented as already implemented.

## 1. Steelman: five things the other proposal does better

| Decision in O1 | Why it improves on mine | Adoption |
|---|---|---|
| **1. Chip: a visible shape cue, without “Conviene / Regular / No conviene.”** O1 §5.2 uses ✓ / ⚠ / ✕ / ?. | My three-line chip reinstated words that Design and Voice explicitly retired. A shape distinguishes categories without requiring color perception and takes less space than a verdict sentence. | **Adopt [theirs], changed.** Keep the shape vocabulary. Retain one rate; do not adopt the second rate or tiny ▼ code. |
| **2. Chip tap does nothing.** O1 §5.5 respects the parked implementation. | Mine reintroduced a documented source of accidental activation, host-screen occlusion and window relayout. This is implementation evidence, stronger than my conceptual preference for immediate explanation. | **Adopt [theirs], changed.** Explain the recorded offer in Mi día. Keep a deliberate threshold shortcut with explicit Save. |
| **3. Preserve import and remove its duplicate parse.** O1 §4.8 proposes detect → parse once → review → save. | My blanket deletion misread the privacy boundary and removed the path for recovering weeks the reader missed. The user’s clarification resolves this directly. | **Adopt [theirs], changed.** Add upload consent on every entry route, including Android Share, and reconcile sources without losing data. |
| **4. Preserve an IMSS utility, free.** O1 §7 recognizes the Mexican differentiation. | My deletion threw away the user job together with the unsupported coverage claims. Market §5 identifies this opportunity explicitly. | **Adopt the job and free access [theirs], changed.** Reject “Ya te toca,” coverage confirmation and unvalidated tax calculations. |
| **5. Give free users a more useful recent history.** O1 §7 offers the current week plus three previous weeks. | My two-week window gives little room to revisit recent work. Four weeks is a better default hypothesis for a weekly earnings product. | **Adopt [theirs], changed.** Define calendar weeks explicitly. Make the current-month summary independently free: four weeks do not necessarily cover a calendar month. |

## 2. Attack: decisions that should not survive

Ranked by damage. These are objections to concrete decisions in O1, including decisions that merely preserve an existing flaw.

| Rank / damage | Decision to reject | Evidence and consequence |
|---|---|---|
| **1 — Critical** | **Substitute hourly “neto” when distance is missing.** O1 §5.3. | The [current engine](../../android/metrics/src/main/java/mx/kompara/metrics/NetProfitEngine.kt) subtracts zero when marginal cost is unavailable. Without distance, fuel and wear can be omitted. Yellow does not make that numerator valid. Inventory §3 documents the fallback. |
| **2 — Critical** | **Present captured estimates as money actually retained:** “Ganancia neta,” “de verdad,” and unqualified shift totals. | [TripEntity](../../android/data/src/main/java/mx/kompara/data/db/entity/TripEntity.kt) explicitly distinguishes offered amounts from final payment. Inventory also documents inferred hours and missing commission. The hero must communicate approximation and expense scope. |
| **3 — Critical** | **Replace “Cubierto” with “Ya te toca / Te tocó / No te tocó.”** O1 §§4.6, 5.9. | This still turns an internal estimate into an entitlement conclusion. [ImssCalculator](../../android/metrics/src/main/java/mx/kompara/metrics/imss/ImssCalculator.kt) compares recorded income with a threshold; it does not verify enrollment. The IMSS directs people to their official constancia to check status. [IMSS verification guidance](https://www.imss.gob.mx/faq/como-saber-si-estoy-dado-alta). |
| **4 — High** | **Hardcode `$8,364`, withholding percentages and “Sirve también para pedir un crédito.”** O1 §4.6. | [FiscalDefaults](../../android/data/src/main/java/mx/kompara/data/settings/FiscalDefaults.kt) labels defaults research-grade; [withholding rules](../../android/metrics/src/main/java/mx/kompara/metrics/fiscal/PlatformWithholdingRates.kt) require confirmation before public reliance. A disclaimer does not establish the applicable base or a lender’s acceptance. |
| **5 — High** | **“Dejaste ir 2 viajes verdes… Cuando veas verde, tómalo con confianza.”** O1 §4.1 and shift notification. | Inventory’s rejected bucket includes expired offers. The app cannot establish that the driver intentionally declined, could safely take the trip, or lost the stated amount. A threshold result is not an instruction to accept. |
| **6 — High** | **Invent population time-of-day guidance:** “Casi todo lo hacen entre 18 y 22 h.” O1 §4.5. | Compare §0 and the Brief explicitly provide weekly population observations only. A weekly hourly rate is not evidence about hours of the day. |
| **7 — High** | **Rank blended personal activity against one platform and headline it as citywide standing.** O1 §4.5. | Compare §2 admits no combined population. A muted “contra conductores de Uber” line does not repair a blended numerator or the broader headline. |
| **8 — High** | **Render imported-only days, daily bars and best hours from a weekly import.** O1 §§4.2–4.3 says “everything renders.” | Inventory Import and Imports’ storage map describe weekly aggregates. A weekly total cannot supply daily allocation, shift duration or hourly observations. |
| **9 — High** | **Upload shared-in files immediately, bypassing the sole upload notice.** O1 §4.8. | O1 places its disclosure in step 1, then skips that step for Android Share. Imports §0.5 explicitly requires consent before the first preview upload. |
| **10 — High** | **Replace an entire existing week with the import.** O1 §4.8. | Imports §0.5 documents source conflicts and missing-value-to-zero corruption. Blanket replacement can lose another app’s activity or captured distance. Reconcile by app, period, field and coverage. |
| **11 — High** | **Make signup effectively required after activation.** O1 §6. | O1 admits the reader needs no account; Account also preserves the account-free reader. Allowing escape only after a network failure turns a technical error into the sole way to decline signup. |
| **12 — High** | **Infer working city and operational thresholds from the phone’s area code.** O1 §6. | A phone prefix is not evidence of where this week’s work occurred. Inventory Perfil already identifies the consequences of silent city defaults. Ask when comparison needs a city; do not silently change the semáforo. |
| **13 — High** | **Treat capture fallback as an API <30-only path.** O1 §§4.13, 6. | Capture explicitly includes MediaProjection fallback on newer devices when the screenshot lane fails. O1 §5.8 partly acknowledges this, leaving the proposal internally inconsistent. Recovery must follow actual capability. |
| **14 — High** | **Start the trial automatically at week close.** O1 §7. | A closed week does not guarantee a valid cohort, verified data, or a driver present to use the trial. A notification cannot substitute for choosing when to consume a one-time benefit. |
| **15 — High** | **Remove fixed costs and substitute a weekly goal while retaining complete-net language.** O1 §§3, 4.12. | Inventory’s fixed-cost fields are ineffective today; deleting them does not eliminate rent. A target does not deduct an expense. Either implement deductions or label the result’s narrower scope. |
| **16 — High** | **Recalculate history whenever costs are saved.** O1 §4.12. | Inventory Tus costos says this affects roughly two years. Today’s fuel price should not silently rewrite past results. Cost changes need an effective date. |
| **17 — High** | **Remove Comparar from navigation and promote Cuenta.** O1 §3. | The user’s F1 context and Compare §0 explicitly preserve individual earnings versus comparison. Account configuration does not justify burying the paid comparison destination. |
| **18 — High** | **Keep Hoy’s insight, three offers and week/goal row.** O1 §4.1. | It rebuilds a dashboard with four or five competing blocks. Its hero already includes money, trips and hours before any insight or offer figures. Rubric §§1, 4 favor a single answer and ≤3 first-viewport numbers. |
| **19 — High** | **Replace the comparison table with a long stack of the same decisions.** O1 §4.5. | Hero sentence, twenty people, Top pill, five metric rows, app averages and opportunities remain several jobs. The standing is expressed three times. |
| **20 — High** | **Auto-save threshold taps and silently move the red floor.** O1 §5.7. | Larger controls improve targeting but do not resolve accidental persistence. A driver changing the visible green floor should not unknowingly change a second boundary. Inventory’s live editor is already a consequential interaction. |
| **21 — High** | **Remove viewing verification altogether.** O1 §7. | Account §0.5 preserves verification for population-dependent paid surfaces. V2 can eliminate the post-purchase surprise without silently discarding the underlying requirement: check eligibility before the sales action. |
| **22 — Medium** | **Use guessed costs as personal costs, then confirm them after the first shift.** O1 §§4.1, 4.12. | “Deja 12: es lo típico de un sedán” is not supported by the supplied evidence. Earlier green results would appear personalized before the driver supplied their costs. |
| **23 — Medium** | **Promise backup of configuration and totals during signup.** O1 §6. | Imports documents no server-to-client downsync at that point. Having an account or an upload does not establish restoration capability. |
| **24 — Medium** | **Show “Kompara activo” before an offer, and “Ya lo estamos arreglando” after a kill switch.** O1 §§5.3, 5.8. | The greeting adds an interruption and conflates readiness with proven reading. A disabled parser does not prove a team has begun a repair. |
| **25 — Medium** | **Attribute every interruption to “Tu teléfono lo cerró.”** | Inventory distinguishes revoked permission, stopped capture and parser unavailability. Wrong diagnosis leads to wrong recovery. |
| **26 — Medium** | **Assume the bottom 35% is the universal forbidden zone; shrink paused recovery to 32dp.** O1 §§5.1, 5.3. | The first is an unvalidated placement assumption across hosts and text sizes. The second falls below Design’s 48dp touch target if interactive. |
| **27 — Medium** | **Add a tiny ▼ explanation and three previews scaled to 70%.** O1 §§4.11, 5.6. | This creates a second symbolic language and shrinks the object whose legibility matters. One full-size preview and one deciding metric are clearer. |
| **28 — Medium** | **Retain the goal, share streak, referral destination and brag grid.** | These add setup and reading without fixing earnings or capture. “Mejor app” by total also confounds pay with time spent. The Brief prefers deletion. Existing earned benefits still need honoring. |
| **29 — Medium** | **Add inDrive “verde si pides $135+” as a fast-follow chip state.** O1 §5.3. | It introduces bid advice, another amount and a new interaction model without validated capture support. It is outside the converged core. Read-only alone does not make it useful or trustworthy. |
| **30 — Medium** | **Promise no-scroll disclosure, precise first-use timing and sub-150ms appearance without device evidence.** | Disclosure must remain readable at larger fonts. System handoffs and OCR performance are device-dependent; Capture records real-device validation debt. These are test targets, not established behavior. |
| **31 — Medium** | **Use “four weeks covers a month,” contradictory partial states, and inconsistent example arithmetic.** | Four weeks can omit part of a calendar month. O1 §5.3 allows yellow partials while §5.8 calls partials gray. Its `$24.50 ÷ 12 + $0.80` cost inputs yield about `$2.84/km`, not `$2.15/km`. Examples must run through the actual calculation. |
| **32 — Medium** | **Score against the wrong eight criteria.** O1 §8; my Round 1 also used the earlier scorecard. | The supplied final Rubric has specific categories. “Delight” cannot replace numbers discipline or one primary action in a valid 68/80 assessment. |

**Additional correction to my own proposal:** putting “Tras gasolina y desgaste” and a full verdict word on every live chip was too expensive in space. Explaining the expense scope remains necessary, but most of that teaching belongs before use and in recorded detail.

### 2.1 Explicit fork resolutions

Each first sentence is the final call.

| Fork | Both Round 1 positions | Final position and reason |
|---|---|---|
| **F1 — Third tab** | C1: Hoy · Ganancias · Comparar, gear. O1: Hoy · Ganancias · Cuenta, Tu lugar under week. | **[mine] Keep Hoy · Ganancias · Comparar, with a gear for Ajustes.** Comparison remains a visible paid destination and respects the founder’s individual/comparative split. Uber’s Account tab is a reference, not a requirement to copy. |
| **F2 — Chip cue and vocabulary** | C1: verdict word, one rate, long cost qualifier. O1: glyph, two rates, “neto,” color vocabulary. | **[new] changed: use a shape cue, one stable rate and “neto aprox.”; use verde/amarillo/rojo in teaching and history.** Adopt O1’s removal of verdict words; retain C1’s single rate. Explain “después de gasolina y desgaste” in onboarding, costs and offer detail. |
| **F3 — Chip tap** | C1: tap opens why. O1: inert tap; detail afterward. | **[theirs] changed: tap does nothing; why lives in Mi día → offer detail.** The Parked chip document records actual failure modes. Do not revive them without new evidence. |
| **F4 — Missing inputs** | C1: stable unit, neutral if required input missing. O1: other metric substitutes, capped yellow. | **[mine] Keep the selected unit; show a neutral missing-input state when its calculation is invalid.** Missing distance can invalidate costs in both units. Missing time alone does not invalidate an otherwise complete per-km calculation. |
| **F5 — Import** | C1: delete remote import. O1: keep parse-once import with upload notice. | **[theirs] changed: retain voluntary import with explicit pre-upload consent, one parse and a reviewed save.** The user clarified the privacy boundary. The Android Share route gets the same consent, and retention is not invented. |
| **F6 — IMSS** | C1: remove tracker, coverage notifications and tax estimates. O1: free tracker with “Ya te toca,” paid retenciones/PDF. | **[new] changed: keep free monthly IMSS review; enable estimated progress only with validated rules and sufficient data.** Never claim coverage or entitlement. Keep a factual paid PDF; remove current formula-based tax estimates. Market §5 supports the job, not the current calculation. |
| **F7 — Signup** | C1: optional, at trial/linking. O1: after activation, effectively required; city inferred. | **[mine] Keep signup optional and request it for a chosen account benefit, including import or trial.** Use phone → WhatsApp code. City is explicitly selected for comparison and never inferred into live thresholds. |
| **F8 — Hoy content** | C1: status, hero, one line, Ver mi día. O1: plus insight, three offers, week row. | **[mine] Keep the minimal Hoy.** Day activity is one tap away; week review already has a tab. A recovery state can replace the primary action without adding another card stack. |
| **F9 — Insights** | C1: delete advice feed. O1: retain one insight, including missed green offers. | **[mine] Delete the advice feed and missed-earnings coaching.** Keep only factual completeness information in the relevant record. An expired offer is not evidence of a bad decision. |
| **F10 — Goal, streak, referral, share grid** | C1: delete all four. O1: retain goal, share streak, referral and grid. | **[mine] Delete all four from v2; retain a simple period share and earned benefits.** Rent belongs in costs. None of these features earns space in the core loop under the supplied evidence. |
| **F11 — Comparison structure** | C1: Tus apps / Tu ciudad, one metric, two rows, provenance. O1: standing, people bar, five metrics, app means, opportunities. | **[mine] Keep two comparison scopes and one metric at a time.** Use matched app, week, cost basis and denominator coverage; no blended value against a single-platform cohort, no population schedules or earnings prediction. |
| **F12 — Trial start** | C1: explicit, once useful paid data exists. O1: automatic at first week close or manual earlier. | **[mine] Start only after an explicit choice and an available eligible paid result.** Week close can invite review; it cannot consume the trial. |
| **F13 — Free history** | C1: current + previous calendar week. O1: current + three previous weeks. | **[theirs] changed: current + three previous calendar weeks, plus a separately free current-month summary.** This improves continuity without pretending four weeks equals a month. Older detailed history remains paid. |
| **F14 — Fixed costs** | C1: retain with deduction rules. O1: remove unused fields; use a goal. | **[mine] Retain optional rent and insurance only with a working, explicit period deduction.** V2 uses a simple daily calendar rule, once across apps. This requires new calculation work; inert fields must not ship. |
| **F15 — Hero labels** | C1: scope-explicit labels. O1: “Ganancia neta / neto.” | **[mine] Keep “Te quedan aprox.,” “Después de gasolina y desgaste,” and “Ingresos registrados” according to evidence.** A short label must not erase uncertainty, missing expenses or the difference between offered and settled payment. |
| **F16 — Greeting overlay** | C1: silence outside the offer/recovery need. O1: two-second pass-through greeting. | **[mine] Do not add a greeting overlay.** Readiness belongs in Hoy and Tu lector. A running service is not proof that the next host layout will parse. |

## 3. Converged information architecture and fate table

### 3.1 Navigation

**[mine] Three tabs, in this order:**

| Tab | Icon | One job |
|---|---|---|
| **Hoy** | Existing Material `Home` | Know whether Kompara is ready and see today’s recorded result. |
| **Ganancias** | Existing Material `DateRange` | Review your money by week, month and day. |
| **Comparar** | Existing Material `List` | Compare one weekly measure across your apps or with a matching city cohort. |

**[mine] Ajustes** opens from a gear on each root. It contains account, plan, costs, semáforo, reader recovery, notifications and help.

**[theirs] Ganancias has Semana / Mes segments.** The selected period survives switching tabs. Comparar has its own explicitly displayed week; entering through “Comparar esta semana” carries the selected week.

**[mine] Bottom navigation appears only on roots.** Every drill-in has Back; task flows have Close. Closing import, signup or purchase returns to the exact originating context.

**[new] Reader status is an operational state, not a destination in the bar.** Its full recovery screen is available from Hoy and Ajustes. No mandatory authentication route can replace the local product.

```text
Hoy
 ├─ Mi día → Detalle de oferta
 └─ Tu lector → permiso / recuperación / ejemplo

Ganancias [Semana | Mes]
 ├─ Elegir periodo
 ├─ Mi día → Detalle de oferta
 ├─ Cómo se calculó
 ├─ Importar
 ├─ IMSS: revisar mi mes
 └─ Compartir / Revisar PDF

Comparar [Tus apps | Tu ciudad]
 ├─ Elegir semana / métrica / app / ciudad
 ├─ De dónde salen los datos
 └─ Requisito de datos o Premium, según el estado

Ajustes [gear]
 ├─ Tus costos → ayuda / renta y seguro
 ├─ Tu semáforo
 ├─ Tu lector
 ├─ Tu cuenta / Tu plan
 └─ Avisos / Ayuda / Tus datos
```

### 3.2 Fate of every current surface

“Kill” removes the UI or behavior, **not existing records or earned access**. Inventory sections A–E and its root/chrome inventory are the coverage basis.

| Current surface | Fate in v2 | Reason |
|---|---|---|
| Root `LOADING` | **[mine] Keep, visible local startup state** | No blank screen or dependency on benchmark networking. |
| Root `ONBOARDING` | **[mine] Replace with §6** | Demonstrate value first; separate asks. |
| Root mandatory `AUTH` | **[mine] Kill as a root gate** | Authentication is a resumable task, not a reader requirement. |
| Root `MAIN` | **[mine] Keep, three-tab shell** | Consistent local entry. |
| Wordmark-only top bar | **[mine] Merge into screen-title chrome** | The title carries more useful context. |
| Five-tab bar | **[mine] Replace with Hoy · Ganancias · Comparar** | Separate daily work, records and comparison. |
| Missing Back; bottom bar on every pushed screen | **[mine] Replace** | Explicit Back; bottom bar only at roots. |
| Inicio weekly hero | **[mine] Move to Ganancias; Hoy becomes daily** | One period per screen. |
| WatchdogBanner | **[mine] Merge into Hoy status/recovery state** | One source of operational truth. |
| GoalBar and setter gap | **[mine] Kill** | No new goal flow. |
| CostNudge | **[mine] Merge into missing-cost state** | Explain why a calculation is unavailable. |
| Inicio platform filters and 2×2 metric grid | **[mine] Remove from Hoy** | Detail and comparison carry these jobs. |
| Inicio percentile pills and Premium tease | **[mine] Move paid comparison to Comparar** | Keep individual earnings free of ranking clutter. |
| Consejos, acceptance coaching, streak badge | **[mine] Kill** | Unsupported advice and pressure. |
| Completeness note | **[mine] Keep contextually beside affected result** | A material limitation cannot disappear into settings. |
| Day link | **[mine] Promote to “Ver mi día” primary action** | Clear daily review path. |
| Inicio share icon | **[mine] Move to selected period** | Sharing needs a known period. |
| Comparar root | **[mine] Keep, rebuild** | One metric and one comparison scope. |
| Week dropdown | **[mine] Keep as shared period sheet** | Usable with many weeks. |
| Free standing hero | **[new] Keep one scoped sentence when valid** | No universal rank or synthetic personalization. |
| Twenty-person bar and duplicate Top badge | **[mine] Kill** | Duplicate the same standing. |
| Single-platform explanatory paragraph | **[new] Replace with explicit selected app/cohort** | Comparison labels should explain themselves. |
| Five-column benchmark table | **[mine] Replace with two aligned rows** | Comparable quantities at readable sizes. |
| “Dónde puedes ganar más” cards | **[mine] Kill** | No unsupported causal or schedule advice. |
| Cross-link to Inicio and “Pronto…” footnote | **[mine] Kill** | Navigation is already visible; future data is not present data. |
| Lector tab | **[mine] Move to Hoy → Tu lector and Ajustes** | Reader is a state. |
| Lector fake offer card | **[mine] Kill** | Recovery cannot masquerade as a live test. |
| Accessibility and capture controls | **[new] Merge into one capability-aware recovery flow** | The next action follows the actual failure. |
| Simulator/threshold buttons in Lector | **[new] Retain as secondary rows** | Reuse the example and settings screens. |
| Fiscal tab | **[new] Merge into Ganancias → Mes** | Monthly records and IMSS review belong together. |
| IMSS coverage badges and projections | **[new] Replace with free evidence-aware review** | No “Cubierto,” “Ya te toca,” or forecasted entitlement. |
| Stale/default IMSS threshold | **[new] Suppress calculated progress when unvalidated** | A reference constant cannot masquerade as current policy. |
| Formula-based retenciones and annual tax estimate | **[mine] Kill in v2** | Current assumptions do not establish actual withholding. |
| Fiscal explainers | **[new] Replace with short source-linked explanation** | The primary job is reviewing and verifying, not reading a legal essay. |
| Fiscal PDF | **[new] Keep as factual monthly report, Premium** | Sources and limitations travel with the numbers. |
| Ajustes tab and flat rows | **[mine] Move behind gear; group rows** | Occasional controls stay findable. |
| Settings notification switches | **[new] Move to Avisos** | Only working notifications; optional summaries. |
| Debug Premium override | **[mine] Keep debug-only** | Not a release surface. |
| Tus costos | **[mine] Keep, simplify and validate** | Personal costs are part of the core promise. |
| Rent/insurance fields | **[mine] Keep behind “Renta y seguro” with real deductions** | No inert fields; no goal substitute. |
| Blank EV edits | **[mine] Fix** | Restore saved values. |
| Save-costs retrospective recomputation | **[mine] Replace with prospective effective dates** | Preserve historical meaning. |
| Tu semáforo | **[new] Keep one full-size preview, steppers and explicit Save** | One visible rule; no accidental persistence. |
| “Valor típico de CDMX” reset | **[mine] Replace with “Restablecer punto de partida”** | No unsupported local authority. |
| Ayuda | **[mine] Keep, short task-based answers** | Remove nonexistent report controls and stale navigation. |
| Tu cuenta | **[mine] Keep, optional** | Account and data actions stay separate from the reader. |
| Account deletion dialog | **[new] Keep with precise cloud/local/subscription consequences** | Do not reuse ambiguous deletion promises. |
| Historial | **[mine] Merge into Ganancias period navigation** | No separate history destination. |
| Resumen de la semana | **[mine] Merge into Ganancias → Semana** | One weekly implementation. |
| Detalle del día | **[mine] Keep as Mi día** | Reach any eligible day. |
| Unreachable `day/{day}` and standalone variants | **[mine] Merge into canonical week → day navigation** | Remove duplicate presentations. |
| Paywall | **[mine] Keep as contextual task** | Terms and prerequisites are visible before purchase. |
| Reusable PaywallGate | **[new] Replace with availability/eligibility/access states** | Do not confuse missing data with unpaid access. |
| Import Loading, SignedOut, Picking, SharedReady, Uploading, Review, Saved, Error | **[theirs] Keep, consolidate under §4.9** | Parse once, review once, save once. |
| `ShareImportActivity` | **[new] Keep; route to selected-file review and upload consent** | Sharing a file into Kompara does not skip the cloud notice. |
| Invita y gana and new referral acquisition | **[mine] Kill** | Remove the growth destination. |
| Existing referral benefits/deep links | **[new] Preserve earned access; route old links to Tu plan with an explanation** | No disappearing entitlements or broken navigation. |
| Monthly share preview | **[new] Replace with selected-period share** | Week stays week; month stays month. |
| Share 2×2 grid, streak, best-app flex | **[mine] Kill** | One factual result. |
| Unreachable landscape variant | **[mine] Kill** | One maintained export format. |
| Simulator destination | **[theirs] Merge into onboarding and “Ver ejemplo”** | A demonstration does not need a tab or destination of its own. |
| Simulator’s persistent threshold playground | **[mine] Kill** | Example changes never modify live preferences. |
| Three pitch pages | **[theirs] Replace with one local demonstration** | First value before identity or permission. |
| Signup phone and code | **[mine] Keep in optional account task** | Preserve the chosen WhatsApp method. |
| Mandatory name/city profile step | **[new] Kill** | Name optional later; city selected when comparison needs it. |
| Prominent disclosure | **[mine] Keep immediately before permission activation** | Clear separate consent. |
| “Pantalla limitada” | **[new] Merge into honest reader-off Hoy** | Import and review remain available; no nonexistent manual-entry promise. |
| Permission waiting/granted screens | **[new] Merge into one resumable activation screen** | No success carousel. |
| OEM survival kit | **[mine] Make contextual and optional** | One actionable instruction at a time. |
| “¡Todo listo para manejar!” | **[mine] Kill** | Return to actual readiness, not a false reading claim. |
| Verdict chip | **[new] Keep, §5** | One rate, redundant shape, honest neutral states. |
| Quick threshold sheet | **[new] Keep deliberate shortcut with draft/Save/Cancel** | No slider or implicit second-threshold movement. |
| Reader-down banner | **[new] Keep only as bounded, factual recovery status when drawable** | Never retain stale offer numbers. |
| E1 duplicate capture disclosure dialog | **[new] Merge into capability-specific activation** | Preserve required notice without duplicate generic consent. |
| E3 capture system handoff | **[mine] Keep when actually needed** | Includes newer-device fallback. |
| Accessibility settings description | **[new] Keep aligned with the disclosure** | It supplements rather than replaces in-app consent. |
| Watchdog and OCR-stopped notifications | **[mine] Merge into one deduplicated interruption model** | Correct app and recovery route. |
| Required capture foreground notification | **[mine] Keep whenever the active path requires it** | Not restricted merely by Android version. |
| IMSS month-end notification | **[new] Optional “Revisa tu mes,” no coverage assertion** | Direct to free review. |
| Week-close notification | **[theirs] Keep; open the week, not Share** | Review before promotion. |
| Fiscal PDF and share PNG | **[new] Keep with exact previews** | Export the inspected period and evidence. |

## 4. Screen-by-screen specification

### 4.0 Shared visual, state and data contract

**[mine] Existing tokens remain the foundation:** emerald primary, slate surfaces, Inter, dark by default, current verdict triple, 16dp edges and section rhythm, 12dp cards, 14dp buttons and 16dp chip corners. Use semantic theme roles rather than another set of hardcoded colors. Yellow uses dark foreground throughout. Design and Voice are the basis.

**[new] One answer and one action hierarchy.**

- One hero per screen: an amount, selected result or operational status.
- At most one filled primary button, normally bottom-aligned and at least 52dp tall.
- Navigation rows and selectors are at least 48dp. A read-only list does not need a fabricated “Continue” button.
- At 360×640dp, the initial viewport contains at most three quantitative values. Dates are context; additional metrics, breakdowns and ledger sections are disclosed deliberately.
- An expanded ledger replaces the summary emphasis; it does not accumulate another dashboard beneath it.
- Large text reflows. No clipped units, tiny legal text or scaling the chip to fit three examples.
- The one-second test concerns recognizing the answer and next action. Permission consent, forms and accounting review are parked tasks.

**[new] Shared states apply to every screen below.**

| State | Treatment and real copy |
|---|---|
| First local load | Preserve title and geometry; static placeholders, no shimmer. Never flash `$0` as a loading value. |
| Local error | “No pudimos abrir tus datos.” Primary “Reintentar.” Preserve period and Back. |
| Network error | Keep local data. “Sin conexión. Tus datos de este teléfono siguen disponibles.” Retry only the affected remote operation. |
| Cached remote data | Label the actual period and update date. Never silently present last week as this week. |
| Empty | Say what is absent and give the relevant next action. No invented zero or blank card. |
| Locked | Identify the paid job, show its availability and preserve free information. One accessible CTA, no blur wall. |
| Missing data | Name the missing input. Missing is distinct from zero. |
| Busy mutation | Preserve inputs; disable duplicate submission; show “Guardando…” or the actual operation. |
| Cancel/Back | Preserve originating period/filter. Unsaved edits are discarded only through an explicit discard choice where necessary. |

**[new] Financial contract.**

| Evidence available | Hero label | Supporting scope |
|---|---|---|
| Recorded eligible trips, running costs and configured fixed costs | **“Te quedan aprox.”** | “Con los costos que registraste · antes de impuestos.” |
| Valid running costs, fixed costs not configured | **“Después de gasolina y desgaste”** | “Sin descontar renta, seguro ni impuestos.” |
| Running costs or required distance unavailable | **“Ingresos registrados”** | “Faltan datos para descontar tus gastos.” |
| Imported platform payout | **“Pago reportado por la app”** | “Tu resumen no descuenta todos los gastos del auto.” |
| Only observed offers, no usable trip record | **“Aún no hay viajes registrados”** | “Las ofertas vistas no cuentan como ingresos.” |

For electric cars, substitute **“energía y desgaste”** where appropriate.

**[new] Required implementation contracts, not cosmetic fixes:**

1. Preserve unknown values instead of replacing them with zero.
2. Distinguish observed offers, inferred trip activity and reported payments.
3. Record or identify the cost/rule basis used for a historical calculation; never reconstruct a past verdict with today’s thresholds without saying so.
4. Apply fixed costs once per calendar date, across all platforms, from their effective date.
5. Reconcile imported and captured records per app and period; combine fields only when their coverage is compatible.
6. Do not divide a complete imported week’s payment by a partially captured week’s kilometers or hours.
7. Preserve weekly aggregates as weekly aggregates. Do not prorate them into daily earnings or an IMSS month.
8. Keep reader frames, raw OCR and passenger information out of release storage and transmission.

These requirements follow Inventory’s calculation gaps, Imports §0.5, Capture, and the engine/source checks cited in §2.

### 4.1 Hoy

**[mine] Purpose:** “¿Está listo y cómo voy hoy?”  
**Hero:** today’s qualified result, or the condition preventing it.  
**Primary:** **“Ver mi día”**, replaced by the next necessary setup/recovery action.

**Top to bottom:**

1. **“Hoy”**, gear.
2. Reader status row with text and icon: **“Lector preparado”**, **“Lectura comprobada”**, or **“Necesita atención.”**
3. Scope-appropriate hero label and amount.
4. One supporting line: **“8 viajes registrados.”** A material completeness message replaces this line when needed.
5. Bottom primary **“Ver mi día.”**
6. Bottom navigation.

**[mine] No insight, recent-offer list, week row, goal, rank, share button or upsell.**

**States:**

- First install/off: **“Activa el lector para empezar”** → **“Activar lector.”**
- Prepared without a successful reading: **“Listo para recibir ofertas.”** Supporting: **“La primera oferta confirmará la lectura.”**
- Empty today: **“Aún no registramos viajes hoy.”**
- Weekly import only: **“Tu resumen no incluye el detalle de hoy.”** → **“Ver semana.”**
- Missing costs: show **“Ingresos registrados”** → **“Agregar mis costos.”**
- Reader stopped: status becomes the dominant answer → **“Revisar lector.”** Existing earnings remain available through a secondary “Ver mi día” row.
- Partial failure: **“No podemos leer DiDi por ahora.”** Do not imply Uber also stopped.
- Capture gap: **“Faltan registros de parte del día.”**
- Loading/error: shared contract.
- Locked: never.

**Removed / glance:** the dashboard becomes one operational answer and one financial answer, with only one visually dominant in the current state. Basis: Inventory Inicio; Rubric 1, 2, 4.

### 4.2 Tu lector

**[mine] Purpose:** understand the reader’s actual state and fix the next problem.  
**Hero:** **“Preparado”**, **“Necesita atención”**, **“Pausado”** or **“Apagado.”**  
**Primary:** one state-specific recovery action; none when healthy.

**Layout:**

1. Back, **“Tu lector.”**
2. Status with one factual sentence.
3. Plain-text app rows: **“Uber · Lectura comprobada”**, **“DiDi · Esperando una oferta”**, or **“inDrive · No disponible en esta versión.”**
4. If known: **“Última lectura: hace 2 min.”**
5. Primary for the first blocking problem.
6. Secondary rows: **“Tu semáforo,” “Ver ejemplo,” “Evitar interrupciones,” “Qué lee Kompara.”**
7. **“Detener lector”** or the accurately labeled route to the system control.

**[new] Readiness and proof are separate.** Permissions and a running capture path establish readiness; an actual parsed offer establishes a successful reading for that app. A demo establishes neither.

| State | Copy / action |
|---|---|
| Disclosure absent | “Antes de activarlo, revisa qué lee Kompara.” → disclosure |
| Permission absent/revoked | “Falta el permiso de accesibilidad.” → “Activar permiso” |
| Starting | “Preparando lector…” |
| Capture consent needed, on any applicable Android version | “Este teléfono necesita permiso para capturar la pantalla.” → “Continuar” |
| Consent canceled | “La lectura sigue apagada.” → “Intentar de nuevo” |
| Deliberately stopped | “Detuviste el lector.” → appropriate start action |
| Parser unavailable | “Esta versión de DiDi aún no se puede leer.” No restart loop |
| Cause unknown | “El lector se detuvo.” No unsupported “Tu teléfono lo cerró” |
| Offline | Local readiness unchanged |
| Battery setting unverified | “Revisa este ajuste.” Never show a false ✓ |
| Notifications denied | “No podremos avisarte si se apaga.” Optional settings route |
| Locked | Never |

**[mine] Battery guidance:** one device-specific instruction and one **“Abrir ajustes”** action at a time. Returning does not prove success unless the setting is actually observable.

**Removed / glance:** no fake offer, duplicated “lector para DiDi,” or four competing buttons. The driver sees the state and the next fix. Basis: Inventory Lector/E3; Capture.

### 4.3 Ganancias — Semana

**[new] Purpose:** review one week’s recorded result.  
**Hero:** the qualified weekly amount.  
**Primary:** **“Ver día”** for the selected day; empty states use **“Importar semana.”**

**Layout:**

1. **“Ganancias”**, gear.
2. **“Semana / Mes.”**
3. Period control, e.g. **“12–18 oct 2026.”** Current period says **“Semana en curso.”**
4. Financial hero and source/scope line.
5. **[theirs] Seven-day bar chart** when actual daily data exists; no amount labels on every bar. Future days are visually distinct from known days.
6. One selected-day line: **“Viernes 16 · $1,380 aprox.”**
7. Primary **“Ver día.”**
8. Secondary rows, below the summary: **“Cómo se calculó,” “Importar semana,” “Comparar esta semana,” “Compartir semana.”**

**[new] Chart behavior:** one tap selects a day; the explicit button opens it. Accessibility provides the same dates and values in a list alternative. Negative values have a real zero baseline. Unknown days are gaps, not zero bars.

**States:**

- Empty: **“Esta semana aún no tiene registros.”** Import remains available; reader activation is a secondary route.
- Captured-only: **“Estimado con los viajes registrados.”**
- Imported-only weekly total: **“Resumen importado.”** Hide the chart and “Ver día”; show **“Este archivo no incluye el detalle por día.”** Primary **“Ver datos importados.”**
- Mixed: **“Resumen y lector.”** Explain sources in calculation detail. If day totals do not reconcile with the reported weekly amount, do not draw them as a complete decomposition.
- Missing distance/costs: use the financial contract, not a fictional net result.
- Old locked week: date, source availability and archive explanation remain; **“Ver historial completo.”**
- Loading/error/offline: shared states.
- First week: show only recorded daily observations; no promise that a city ranking will certainly arrive Monday.

**Removed / glance:** no goal, five-metric grid, city standing or “best hours” advice. The chart supports the one weekly amount. Basis: Inventory Historial/WeekSummary; Imports.

### 4.4 Ganancias — Mes

**[new] Purpose:** review a calendar month and reach monthly records.  
**Hero:** the supported monthly amount.  
**Primary:** **“Ver semanas.”**

**Layout:**

1. Ganancias chrome, **“Mes.”**
2. Month selector: **“Octubre 2026.”**
3. Qualified amount and completeness line.
4. Primary **“Ver semanas”**, expanding the dated list in place.
5. Secondary rows: **“Revisar mi IMSS,” “Preparar PDF,” “Compartir mes,” “Cómo se calculó.”**

**[mine] No city rank or tax-total hero.**

**States:**

- Empty: **“No hay ingresos registrados de este mes.”**
- Incomplete: **“Faltan registros para completar el mes.”**
- A weekly import crosses the boundary without daily allocation: **“No podemos separar este resumen por mes.”** Exclude it from an apparently exact monthly total; show it among unresolved sources.
- Current month: summary free independently of the detailed history window.
- Older month: archive explanation; free IMSS verification guidance remains reachable.
- Generating PDF: handled in export, not by replacing the entire month.
- Loading/error: shared contract.

**Removed / glance:** one month and one amount, with IMSS as a review action rather than a second competing progress dashboard. Basis: Inventory Fiscal/share; F6 and F13.

### 4.5 Mi día

**[mine] Purpose:** understand the recorded activity behind a day.  
**Hero:** qualified day amount.  
**Primary:** **“Ver viajes.”**

**Summary layout:**

1. Back, **“Mi día.”**
2. Date with previous/next controls; future navigation disabled.
3. Financial hero.
4. One completeness/source line.
5. Primary **“Ver viajes.”**
6. Secondary **“Cómo se calculó.”**

**[new] “Ver viajes” expands the ledger in place and reduces the hero to a compact context header.** It does not open another full screen.

Ledger rows show:

- **“14:32 · Uber”**
- One amount, qualified as an estimate where applicable.
- **“Viaje registrado · estimado,” “Oferta vencida,”** or **“Sin confirmar.”**
- An optional verdict glyph and color word; no second rate in the row.

Separate collapsed group: **“Ofertas sin confirmar.”** Do not use “La pasaste” merely because the offer vanished.

**States:**

- Empty: **“No registramos actividad este día.”**
- Offers only: **“Vimos ofertas, pero no confirmamos viajes.”** No earned-money total.
- Open/inferred shift: **“El tiempo de trabajo es estimado.”** No declared end of shift.
- Partial reading: **“El lector estuvo apagado parte del día.”**
- Weekly import without day data: no fabricated day route.
- Historical detail unavailable: **“Conservamos el total, pero no el detalle de este día.”**
- Locked: follows the containing history period; date and access explanation remain.
- Loading/error: preserve date and Back.

**Removed / glance:** no acceptance funnel, missed-green filter, five metric cards or advice. First glance answers what was recorded and how complete it is. Basis: Inventory DayDetail.

### 4.6 Detalle de oferta

**[mine] Purpose:** explain the exact recorded offer, afterward.  
**Hero:** **“Te quedarían aprox. $96”**, or the missing-input reason.  
**Primary:** **“Ver cálculo.”**

**Layout:**

1. Sheet title **“Uber · 14:32.”**
2. Outcome: **“Oferta vencida,” “Sin confirmar,”** or **“Viaje registrado · estimado.”**
3. Hero.
4. **“Verde · según tu mínimo de ese momento.”**
5. Primary **“Ver cálculo.”**
6. Expanded calculation replaces the compact summary with labeled rows: **“Pago de la oferta,” “Distancia total,” “Incluye la recogida,” “Tiempo de la oferta,” “Gasolina y desgaste,” “Verde desde.”**
7. **“El pago final puede cambiar.”**

**[new] The why sentence uses the actual deciding rule:**  
**“Salió verde porque superó tu mínimo por kilómetro.”**  
Do not revive a “bien por km y por hora” explanation when only one metric decided the color.

**States:** missing payment/distance/time/costs; uncertain payment basis; expired offer; inferred trip; historical rule unavailable; local loading/error. If no historic rule was saved: **“No conservamos el mínimo usado en esta oferta.”**

**[mine] No passenger data, screenshot or raw text.** No auto-prefilled support upload is added.

**Removed / glance:** one result and its confidence; details are available after the offer has passed. Basis: Parked chip; Capture; Inventory chip.

### 4.7 Calculation and provenance sheets

**[mine] Purpose:** make a displayed number auditable.  
**Hero:** selected result and its scope.  
**Primary:** **“Entendido.”**

**“Cómo se calculó” layout:**

1. Period or offer.
2. **“Ingresos incluidos.”**
3. **“Gasolina y desgaste.”**
4. **“Renta y seguro”**, if configured.
5. Source and effective cost dates.
6. Missing activity or inputs.
7. **“Antes de impuestos.”**
8. Primary.

Essential qualifiers remain beside the original number; this sheet carries the detailed explanation.

**[new] “De dónde salen los datos” for comparisons** shows week, app, city, metric definition, source, actual sample information when supplied, and exclusions. If necessary metadata is missing, the comparison is withheld rather than explained away.

**States:** complete, partial, imported aggregate, mixed compatible sources, incompatible coverage, missing historical basis. Never independently paywalled.

**Glance:** “What this figure includes.” Basis: Imports §0.5; Compare §2.

### 4.8 Comparar

**[mine] Purpose:** answer one weekly comparison question.  
**Hero:** one supported difference or one scoped standing sentence.  
**Primary:** the appropriate availability, verification or access action; no redundant button after an unlocked result is already visible.

**Layout:**

1. **“Comparar”**, gear.
2. Explicit week, defaulting to the latest completed available week.
3. **“Tus apps / Tu ciudad.”**
4. Compact selection row for the current metric and necessary scope.
5. Hero.
6. Two aligned result rows when unlocked.
7. One source/basis sentence.
8. **“De dónde salen los datos.”**

**Tus apps [mine]:**

- Example hero: **“DiDi te dejó $1.20 más por km.”**
- Rows: **“Tú en Uber · $6.80/km”**, **“Tú en DiDi · $8.00/km.”**
- Scope: **“Después de gasolina y desgaste · esta semana.”**
- Qualification: **“Son tus resultados; los horarios y viajes fueron distintos.”**

No arbitrary allocation of fixed costs to an app. If only one platform exists: **“Esta semana solo registraste Uber.”**

**Tu ciudad [new]:**

- Explicit city and platform, e.g. **“CDMX · DiDi · Por hora.”**
- Free standing, only when valid: **“Por hora, estás arriba del 78% del grupo de DiDi en CDMX.”**
- No people bar or duplicate “Top 22%.”
- Paid rows: **“Tú en DiDi”** and **“Promedio del grupo.”**
- Same week, payment/expense basis, platform and time definition on both sides.
- “Promedio” means the arithmetic mean; it is not renamed “el conductor típico.”
- If the available population is before vehicle expenses, label both sides accordingly. Do not compare it with personal cost-adjusted earnings.

**[new] Availability precedes monetization:**

| State | Answer / primary action |
|---|---|
| No personal week | “Necesitas una semana con datos.” → “Importar semana” |
| No selected city | “Elige dónde manejaste esta semana.” → “Elegir ciudad” |
| Missing selected metric | “Falta distancia para comparar por km.” → “Elegir otra métrica” |
| Different coverage/basis | “Estos datos no se pueden comparar parejo.” → provenance |
| No real suitable cohort | “Aún no hay datos suficientes de este grupo.” No trial tease for that result |
| Synthetic data only | No personal standing. Optional separate “Ejemplo,” never in a personal hero |
| Verification required | “Para esta comparación, verifica tus datos con un resumen.” → “Verificar mis datos” |
| Eligible, free | Valid free standing if available; one benefit card → “Ver Premium” |
| Premium | Result shown; no second payment or generic import lock |
| Selected week in progress | Label it; do not silently swap to last week |
| Offline, cached | Show cached week explicitly |
| Fetch error | “No pudimos cargar este grupo.” → “Reintentar”; personal data remains |
| Metric unsupported for the app | Explain why; do not substitute automatically |

**[new] Verification applies to the population-dependent paid result, not the local reader or personal archive.** This preserves Account §0.5’s revised boundary while fixing the purchase surprise. Availability and verification are separate from subscription status.

**Removed / glance:** no five-metric stack, app-pay prediction, opportunities or population schedule. The driver can identify who, what and which week. Basis: Compare §§0–2; Account §0.5; Rubric.

### 4.9 Importar

**[theirs] Purpose:** bring a chosen weekly document into the records.  
**Hero:** the selected period/app and then the extracted result.  
**Primary:** changes by stage: **“Elegir archivos” → “Subir y leer” → “Guardar semana.”**

**Stage 1 — Choose [new].**

- Close, **“Importar semana.”**
- **“Trae un resumen de las semanas que te faltan.”**
- Primary **“Elegir archivos.”**
- Short guidance rows: **“Uber: PDF semanal”**, **“DiDi: Ganancias y Tablero.”** Show additional formats/apps only when supported by the shipped parser.
- Detect locally when possible. If detection is uncertain: **“¿De qué app es este archivo?”** Never pretend detection is infallible.

**Stage 2 — Upload consent [new].**

Show chosen filenames and detected app before any network upload.

> **“Para leer este archivo, hay que subirlo.”**  
> “Enviaremos los archivos que elegiste a Kompara y a su proveedor de lectura, Anthropic. Pueden incluir datos personales que aparezcan en tu resumen.”  
> “La lectura automática de tus ofertas sigue en este teléfono.”

Primary **“Subir y leer.”** Secondary **“Cancelar.”** A clear privacy link explains storage, processors and deletion.

**Do not promise immediate deletion until it is implemented and verified.** Imports §0.5 identifies retention, preview storage and payload minimization as unresolved requirements. A compliant storage/retention notice and behavior are prerequisites to releasing this flow.

**[mine] Account interstitial, only when needed:**  
**“Usa tu cuenta para guardar esta importación.”** → **“Continuar con WhatsApp.”**  
Secondary **“Ahora no.”** Preserve the selected files locally for resuming the task where supported; do not upload them during authentication.

**Stage 3 — Process [theirs].**

**“Leyendo tu resumen.”** Real event states: **“Subiendo archivo”**, **“Leyendo cifras.”** No cosmetic percentage, fake four-step timer or guaranteed completion time. Offer cancellation with an honest explanation if an upload already completed.

**Stage 4 — Review [new].**

- **“Revisa tu semana.”**
- Period and app, editable through correction of the file selection if extraction is wrong.
- Hero **“Pago reportado por la app.”**
- Up to two supporting values initially; **“Ver todos los datos”** reveals remaining extracted fields.
- Missing: **“No viene en el archivo.”**
- Primary **“Guardar semana.”**
- Secondary **“Elegir otro archivo”**, **“Descartar.”**

**[new] Save commits the same reviewed parse result.** No second upload or model interpretation. A stable result identifier and idempotent confirmation are required backend work.

**Conflict handling [new]:**

- Exact duplicate: **“Esta semana ya está guardada.”** → **“Ver semana.”**
- Existing capture: **“Tu resumen y el lector tienen datos de la misma semana.”**
- Show which reported fields will update and which captured fields remain.
- Do not sum duplicate earnings.
- Do not overwrite known fields with absent values.
- Do not construct rates from mismatched full-week/partial-week coverage.
- Preserve another platform’s records.

**Other states:** unsupported/oversize file; mixed file types; incomplete DiDi pair; wrong pair; picker canceled; offline; expired session; unreadable file; save failure; already saved after a retry.

Exact recovery examples:

- **“Falta la captura del Tablero.”** → **“Agregar captura.”**
- **“No pudimos leer este archivo.”** → **“Elegir otro.”**
- **“Se leyó el archivo, pero no pudimos guardarlo.”** → **“Reintentar guardado.”**
- Success: **“Semana guardada.”** Return to the chosen week.

**[new] Android Share enters at selected-file review, then consent—not upload.**

**Removed / glance:** no duplicate processing, AI theater or unsupported complete-net label. The driver knows which file will leave the phone and which figures will be saved. Basis: Inventory Import/E4; Imports §0.5.

### 4.10 IMSS: revisar mi mes

**[new] Purpose:** review the available monthly evidence and reach official verification.  
**Hero:** **“Tu alta se confirma en el IMSS.”**  
**Primary:** **“Consultar mi vigencia.”**

The primary opens the official IMSS route in the browser. Kompara does not request or retain IMSS credentials.

**Layout:**

1. Back, **“Revisar mi IMSS.”**
2. Month selector; current and most recently completed month are readily available.
3. Hero.
4. One sentence: **“Tus ingresos registrados no confirman que estés dado de alta.”**
5. Selected app’s monthly source/completeness.
6. If valid: **“Avance estimado”** with a neutral/emerald progress presentation.
7. Primary **“Consultar mi vigencia.”**
8. Secondary **“Cómo se calcula”** and **“Ver mis registros.”**

The official verification route is appropriate because IMSS identifies the constancia de vigencia as the way to check affiliation status. [IMSS guidance](https://www.imss.gob.mx/faq/como-saber-si-estoy-dado-alta).

**[new] Progress is conditional on all of:**

- A current, dated, validated rule for the period.
- The correct legal income definition, distinct from Kompara’s vehicle-cost estimate.
- Sufficient source data and a valid month allocation.
- No reliance on silently prorated weekly totals.

Until those conditions hold, ship the useful review/verification route with:

> **“Aún no podemos estimar tu avance con estos datos.”**

Do not display a stale threshold behind a caveat.

**Permitted future estimated states [new]:**  
**“Por debajo de la referencia”**, **“Referencia alcanzada”**, always paired with **“Estimación; no confirma tu alta.”**

**Removed [mine]:** “Cubierto,” “Ya te toca,” “No te tocó,” “Difícil,” month-end projections, fixed tax formulas and credit eligibility claims.

**States:** no records; partial month; cross-month weekly source; rule unverified/stale; valid estimated progress; past month with the applicable historical rule; browser/network failure. Free in all states.

**Glance:** “Kompara helps me review; the official record confirms my status.” Basis: Inventory Fiscal/E5; Market §5; fiscal source limitations.

### 4.11 Compartir and Revisar PDF

**[new] Purpose:** inspect exactly what will be exported.  
**Hero:** actual artifact preview.  
**Primary:** **“Compartir.”**

**Image layout [mine]:**

1. Close, **“Compartir semana”** or **“Compartir mes.”**
2. Exact period.
3. Preview containing Kompara attribution, period and one factual result.
4. **“Mostrar montos”**, off by default.
5. Primary opens the system chooser, including WhatsApp when available.

With amounts hidden, use a supported count such as **“62 viajes registrados.”** Do not imply that observed offers are completed trips. No streak, best app, rank grid or promotional caption promising improvement.

**PDF layout [new]:**

1. **“Revisar PDF.”**
2. Period and rendered preview.
3. **“Resumen de tus registros; no es un comprobante fiscal.”**
4. Primary **“Compartir.”**

Include recorded income, configured expenses, source coverage and calculation limitations. Omit current formula-based tax estimates. Actual document-reported tax fields may be added only when retained with reliable provenance; the present weekly aggregate store does not establish that capability.

**[theirs] Preview and export use the same rendered artifact.**

**States:** preparing; ready; no eligible data; partial with visible qualification; error/retry; chooser canceled; PDF locked before generation. Image sharing of an accessible free period is free.

**Removed / glance:** exactly the selected period and inspected content leave the phone. Basis: Inventory Share/E6; Imports storage map.

### 4.12 Tus costos, cost helper, and Renta y seguro

**[mine] Purpose:** set the expenses used in estimates.  
**Hero:** **“Gasolina y desgaste: $2.00/km”**, or **“Faltan tus datos.”**  
**Primary:** **“Guardar costos.”**

**Layout:**

1. Back, **“Tus costos.”**
2. Cost-per-km hero.
3. **“Gasolina / Eléctrico.”**
4. Gas: **“Precio por litro,” “Rendimiento: km por litro.”**
5. Electric: **“Precio por kWh,” “Consumo por 100 km.”**
6. **“Desgaste por km.”** Helper: **“Llantas, mantenimiento y servicios.”**
7. Secondary **“Ya sé mi costo por km”** switches to the direct-entry alternative.
8. **“Ayúdame a calcularlo.”**
9. Row **“Renta y seguro.”**
10. **“Se usarán desde hoy.”**
11. Primary.

**[mine] The helper handles one calculation at a time**, e.g. fuel price divided by efficiency. It never silently inserts “typical CDMX” values. Examples are labeled **“Ejemplo.”**

**[new] Fixed-cost editor:**

- Title **“Renta y seguro.”**
- Fields **“Renta o pago del auto por día”**, **“Seguro por día.”**
- Hero is their combined daily amount.
- Exact rule: **“Descontaremos este total una vez por día, aunque no manejes.”**
- **“Se aplica a tu resumen, no al semáforo de una oferta.”**
- Effective date: **“Desde hoy.”**
- Primary **“Guardar gastos.”**

Use one calendar-day rule in v2. Do not add a configurable allocation system. Drivers whose payment is weekly can use a small conversion helper; its result and divisor are visible.

**[new] Fixed costs count once across platforms and shifts.** A day with no known income must not be called a known loss merely because no trips were captured; show the recorded expenses and missing-income scope. A period with supported zero income can legitimately show a negative result.

**States:** unknown versus explicit zero; valid gas/EV/direct-entry; saved values restored; invalid number/zero denominator; draft; save failure; saved effective date. No automatic history rewrite.

Existing saved but unused fixed fields require confirmation before activating deductions. Historical cost basis absent → disclose it, rather than retroactively applying today’s profile.

**Removed / glance:** no inert fields, hidden defaults or unsupported “neto de verdad.” The driver sees what will be deducted and when. Basis: Inventory Tus costos; [CostProfile](../../android/metrics/src/main/java/mx/kompara/metrics/CostProfile.kt).

### 4.13 Tu semáforo

**[new] Purpose:** set the rule that colors offers.  
**Hero:** **“Verde desde $8.00/km.”**  
**Primary:** **“Guardar semáforo.”**

**Layout:**

1. Back, **“Tu semáforo.”**
2. **“Decide por kilómetro / Por hora.”**
3. Green-floor stepper.
4. Expandable **“Cuándo sale rojo.”**
5. Red-floor stepper and **“Entre los dos sale amarillo.”**
6. One full-size chip preview labeled **“Ejemplo.”**
7. Secondary **“Ver otro ejemplo.”**
8. **“Se usa en todas tus apps.”**
9. Primary; Back discards only after confirmation if edited.

**[theirs] Use color vocabulary and shape cues consistently.**  
**[mine] Save explicitly; keep the other unit’s saved settings.**  
**[new] Changing green does not move red.** Reject an invalid ordering with **“El mínimo rojo debe quedar por debajo del verde.”**

**States:** first values labeled **“Punto de partida”**; saved; draft; invalid bounds; canceled; saving/error; reset draft. Never locked.

**Removed / glance:** no scaled three-chip row, secondary ▼ thresholds, auto-save or city-typical claim. One selected rule is unmistakable. Basis: Inventory Tu semáforo; Design; Parked chip.

### 4.14 Ajustes, Ayuda, Tus datos and Avisos

**Ajustes [new].** One job: find an occasional control. Hero is the current plan/status header; no filled CTA in the normal state.

Grouped rows show current values:

- **“Tu manejo”**: Tus costos · `$2.00/km`; Tu semáforo · `Verde desde $8.00/km`; Tu lector · `Preparado`.
- **“Tu cuenta y plan”**: Tu cuenta · `Sin cuenta`; Tu plan · `Gratis`.
- **“Ayuda y privacidad”**: Ayuda; Tus datos; Avisos.

Value subtitles adopt O1’s useful settings pattern without promoting Cuenta to a tab.

**Ayuda [mine].** One job: resolve a problem. Primary **“Revisar lector.”** Short expandable answers:

- **“No aparece el semáforo.”**
- **“¿Qué significan los colores?”**
- **“¿Qué descuenta Kompara?”**
- **“¿Qué se queda en mi teléfono?”**
- **“¿Qué pasa cuando importo?”**

Secondary **“Ver ejemplo.”** Add human support only when a real supported destination exists; do not ship a fictitious report button or number.

**Tus datos [new].** One job: understand storage and deliberate sharing. Three clearly separated rows:

1. **“Lectura de ofertas”** — images/text processed locally; derived figures retained.
2. **“Archivos que importas”** — selected documents uploaded after consent.
3. **“Cuenta y servicios en línea”** — explain actual account, entitlement and consented aggregate behavior.

Primary **“Ver datos guardados.”** This shows categories and sources, not passenger material. Cloud deletion and local deletion are separate actions with explicit scope.

**Avisos [new].** One job: choose working notifications.

- **“Si el lector se detiene.”**
- **“Resumen del día.”**
- **“Resumen de la semana.”**
- **“Revisar mi IMSS al cerrar el mes.”**

Summaries are optional. If system permission is missing: primary **“Activar avisos.”** Otherwise switches persist with clear state. No promotional sharing reminders.

**States:** local content available offline; denied permission routes to actual system settings; disabled channel labeled **“Apagado en tu teléfono”**; account-dependent operations show their own errors. No paywall.

**Glance:** a labeled row with its actual value or state. Basis: Inventory Ajustes/Ayuda/E5; Capture; Imports.

### 4.15 Tu cuenta and account tasks

**[mine] Purpose:** use identity for a chosen online benefit.  
**Hero:** account status.  
**Primary:** **“Continuar con WhatsApp”**, **“Confirmar código”**, or **“Guardar cambios”**, depending on the task.

**Signed out:**

> **“Tu lector funciona sin cuenta.”**  
> “Usa tu cuenta para importar resúmenes o activar Premium.”

Primary **“Continuar con WhatsApp.”** Back/Close always remains available.

**Phone task [mine]:** explain the originating benefit, show `+52` and the number field, primary **“Recibir código.”** Validation: **“Escribe los 10 dígitos de tu número.”**

**Code task [mine]:** **“Escribe el código de WhatsApp.”** Paste-friendly input; masked destination; **“Cambiar número”**, timed **“Reenviar código”**; primary **“Confirmar código.”**

**[new] No promise of backup or restore unless implemented.** Successful OTP does not itself start the trial or upload the selected file; return to the deliberate action.

**Signed in:** masked phone, plan row, optional name, **“Cerrar sesión”**, separated **“Eliminar cuenta.”** City is selected in comparison; changing it never changes thresholds silently.

**Deletion dialog [new]:**

> **“¿Eliminar tu cuenta?”**  
> “Se eliminarán tu cuenta y sus datos guardados en línea. Los registros de este teléfono se borran por separado. Si tienes una suscripción, cancélala también en Google Play.”

Primary destructive **“Eliminar cuenta.”** Secondary **“Cancelar.”** Show **“Administrar suscripción”** when relevant. This copy requires matching deletion behavior; do not claim deletion while it is merely queued.

**States:** invalid phone; sending; wrong/expired code; resend cooldown; offline; expired session; signed in; logout; deletion pending/error/completed. Local reading remains available throughout.

**Legacy referral links [new]:** open Tu plan with **“El programa de invitaciones ya no está disponible.”** Existing earned entitlement and its end date remain visible.

**Glance:** why an account is needed for this action, or the current account state. Basis: Inventory Account/Auth; Account §0.5; Imports.

## 5. The offer moment

### 5.1 Chip anatomy

**[new] Default live chip:**

```text
┌─────────────────────┐
│ ✓  $8.00/km         │
│    neto aprox.      │
└─────────────────────┘
```

- **[theirs]** Shape cue: check, warning triangle, circle-cross; neutral question mark for unavailable calculation. Use consistent vector icons, not platform-dependent emoji.
- **[mine]** One rate, always in the driver’s selected deciding unit.
- **[new]** **“neto aprox.”** is the short live qualifier. Onboarding and detail define it as after running costs, before fixed costs and taxes.
- **[theirs]** Remove the emerald brand strip.
- **[new]** A small monochrome K may remain only if it fits without reducing rate, unit or status legibility. It is attribution, not another color band.

**[new] Geometry target:** content-sized, approximately 156–184dp wide and 72–88dp high at default text size; existing 16dp radius, 12dp padding, 30sp hero role and 14sp supporting role. These are prototype targets, not proof that every value fits. Large text expands the surface; never truncate currency or units.

**[mine] One-second message:** “This offer clears, approaches, or misses my chosen threshold; this is its estimated return per selected unit.”

A check is not an accept button. The chip never accepts, rejects, navigates or taps the host.

### 5.2 Exact financial states

Examples assume green from `$8.00/km`, red below `$6.00/km`.

| State | Exact visible content | Behavior |
|---|---|---|
| **[new] Valid green** | ✓ **`$8.40/km`** · **“neto aprox.”** | Green with readable foreground. |
| **[new] Valid yellow** | Warning **`$7.10/km`** · **“neto aprox.”** | Yellow, dark text throughout. |
| **[new] Valid red** | Circle-cross **`$4.30/km`** · **“neto aprox.”** | Red. |
| **[mine] Negative result** | Circle-cross **`−$1.20/km`** · **“neto aprox.”** | Preserve the minus sign; never clamp to zero. |
| **[mine] Missing fare** | ? **“Falta el pago”** · **“Sin cálculo”** | Neutral slate. |
| **[mine] Missing distance or pickup leg needed for costs** | ? **“Falta distancia”** · **“Sin cálculo”** | Neutral in either deciding unit. |
| **[mine] Hour selected, required time missing** | ? **“Falta el tiempo”** · **“Sin cálculo”** | No automatic per-km substitution. |
| **[mine] Km selected, time missing but all km/cost inputs valid** | Valid per-km state | Time absence is recorded in detail; it does not invalidate this calculation. |
| **[mine] Missing costs** | ? **“Faltan tus costos”** · **“Sin cálculo”** | No zero-cost green result. |
| **[mine] Payment/commission basis unclear** | ? **“Pago sin confirmar”** · **“Sin cálculo”** | Do not invent take-home or subtract commission twice. |
| **[new] Recognized new offer still processing** | ? **“Leyendo oferta”** | Clear the previous offer’s figures immediately. |
| **[mine] Recognized offer unreadable** | ? **“Sin lectura”** · **“Revisa la oferta”** | No red financial judgment. |
| **[mine] No recognized offer** | Hidden | No guessed neutral chip over unrelated host content. |
| **[mine] Parser disabled/unsupported** | No financial chip | Reader status explains the limitation. |
| **[mine] Hour selected and complete** | Corresponding glyph · **`$186/h`** · **“neto aprox.”** | Same hierarchy and stable unit. |

**[new] Unknown is a data state, not a fourth profitability band.** Remove the current “missing fare → red” and fallback numerator behavior in the engine before shipping this appearance.

TalkBack example:

> **“Semáforo verde. Ocho pesos con cuarenta por kilómetro, estimados después de gasolina y desgaste.”**

Do not rely on TalkBack as the only non-color accommodation for sighted drivers.

### 5.3 Position, updates and gestures

**[mine] Placement:**

- Choose a safe edge position using the supported host layout, avoiding fare, pickup information, decision controls and system gestures.
- Persist a driver-adjusted position per host app **[theirs]**, then revalidate it when the layout changes.
- Do not assume the bottom 35% rule is sufficient.
- If no safe position can fit the current text size, suppress the overlay rather than cover the host’s decision controls. Record the reason for later recovery.
- The touchable region matches visible Kompara content. No invisible full-screen interception.

**[mine] Motion:** no pulse, countdown, offer beep, vibration or celebratory animation. Change figures directly. Preserve deduplication. The existing 500ms disappearance grace may prevent flicker for the same offer; a new offer, host change or disabled parser clears stale values immediately.

**[theirs] Tap does nothing.** No expansion, tooltip or hidden host action.

**[mine] Drag moves only the chip.** A drag must not also trigger a tap or threshold edit.

**[new] Long-press opens the quick threshold editor deliberately.** Teach this in the example and Tu semáforo, not with a repeating live hint.

### 5.4 Quick threshold editor without leaving the host

**[new] Purpose:** change the chosen green floor while parked.  
**Hero:** selected green floor.  
**Primary:** **“Guardar.”**

Layout:

1. **“Ajusta cuando estés estacionado.”**
2. **“Verde desde.”**
3. Large minus / value / plus controls, at least 48dp.
4. Read-only **“Rojo por debajo de $6.00/km.”**
5. **“Guardar”** and secondary **“Cancelar.”**

**[mine] All changes are drafts.** No saving on tap, release or timeout.  
**[new] Red stays unchanged.** A green value that would cross red is blocked with an inline explanation. Full red-boundary editing is in Tu semáforo.

The editor fits only in verified free space. If it cannot fit: **“Ajusta en Kompara”** opens the in-app editor by explicit action. Do not promise a motion lock; none is established.

If the offer disappears or a new offer arrives, close and discard the draft. If the driver saved first, the saved rule persists. No change to host controls occurs.

### 5.5 Why, recovery and the rest of the shift

**[theirs] Why is explained afterward** in Mi día → Detalle de oferta, using the actual rule and saved input basis. No ▼ marker or second live rate.

**[new] Recovery states:**

- One unreadable offer: neutral chip; no notification.
- Repeated failures: affected app marked in Tu lector.
- Capture stopped while overlay service remains available: one neutral, bounded status pill **“Lector detenido · Revisar.”** If tappable, minimum 48dp target; no 32dp interactive dot.
- Accessibility service absent: no promised overlay; use the available watchdog notification and in-app status.
- Parser unavailable: **“Por ahora no podemos leer DiDi.”** No “ya lo estamos arreglando” unless that fact is independently known.
- Offline: local reader continues.

**[mine] One interruption notification per unresolved incident:**

> **“El lector necesita atención”**  
> “Revisa Kompara cuando te estaciones.”

Tap opens Tu lector before a new system consent prompt. Where an ongoing capture notification is required, use **“Lector de Kompara activo”** with a real **“Detener”** action.

**[mine] Before shift:** no greeting overlay.  
**[mine] After each trip:** no toast, praise or acceptance judgment.  
**[new] Optional daily summary:** **“Tu resumen de hoy está listo.”** Do not assert that the shift ended just because offers stopped.  
**[theirs] Weekly summary:** **“Revisa tu semana en Kompara.”** Opens that week.  
**[new] Monthly IMSS reminder:** **“Revisa tus registros del mes y consulta tu vigencia.”** Opens free review.

Amounts remain hidden on the lock screen by default. Trial and sales prompts never appear over a host offer.

## 6. Onboarding

### 6.1 Target and count

**[new] Four core Kompara screens:** example → one cost answer → disclosure → activation.

The cost calculator is an optional branch with one input question per screen. Device capture consent, battery guidance and notification permission are separate conditional tasks, not concealed inside a false “4 taps” promise.

**[mine] First value is the local example on screen 1.** Target under 60 seconds from first launch, before account or system permission. Actual reader activation time is device-dependent and must be measured.

### 6.2 Core screens

| Step | One ask | Exact content and actions | States |
|---|---|---|---|
| **1 — Example [new]** | Decide whether to use the assistance | **“Mira lo que te deja un viaje.”** Original neutral offer: `$120 · 12 km`, plus the actual chip `$8.00/km · neto aprox.` Label **“Ejemplo.”** Body: **“El ejemplo ya descuenta gasolina y desgaste. El color sigue el mínimo que tú eliges.”** Primary **“Usar en mis viajes.”** Secondary **“Ver otro ejemplo.”** | Local, no login/network. Cycle green/yellow/red/unknown without changing real settings. Accessible text fallback if preview fails. |
| **2 — Cost [new]** | Supply one running-cost amount | **“¿Cuánto gastas por kilómetro?”** Field **“Gasolina y desgaste por km.”** Primary **“Guardar y seguir.”** Secondary **“Ayúdame a calcularlo”** or **“Después.”** | Unknown, valid, invalid, save error, skipped. No hidden example value. |
| **3 — Disclosure [mine]** | Consent to reader access | Copy below. Primary **“Acepto. Activar lector.”** Secondary **“Ahora no.”** | Acceptance recorded explicitly; decline/Back is not consent. |
| **4 — Activation [new]** | Grant the required accessibility permission | **“Activa Kompara en Accesibilidad.”** Steps: **“Busca Kompara. Activa el servicio. Regresa aquí.”** Primary **“Abrir ajustes.”** Secondary **“Ahora no.”** | Waiting, denied, preparing, ready, fallback consent needed, failed. Return to the same task, then Hoy. |

**[new] Cost-helper branch:** each screen asks one question—fuel price, efficiency, then wear—or the corresponding electric input. Each uses **“Continuar.”** The result screen asks only **“Usar este costo.”** Drivers can leave without inventing an answer.

Skipping costs says:

> **“Puedes activar el lector. Para calcular el neto, faltarán tus costos.”**

Initial threshold values are labeled **“Punto de partida; puedes cambiarlos en Tu semáforo.”** They are not portrayed as the driver’s preference or a verified city benchmark.

### 6.3 Prominent disclosure

**[new] Suggested copy, subject to matching shipped behavior:**

> **Antes de activar el lector**
>
> **Qué lee**  
> Kompara usa el servicio de accesibilidad de Android para analizar la pantalla mientras está abierta una app compatible y encontrar las ofertas.
>
> **Para qué**  
> Calcula lo que te puede quedar y muestra el semáforo. Nunca acepta, rechaza ni toca nada por ti.
>
> **Qué guarda**  
> Las imágenes y el texto se procesan en este teléfono; no se guardan ni se envían. Guardamos cifras de los viajes para tus resúmenes.
>
> **Tú decides**  
> Las plataformas pueden restringir estas herramientas. Puedes detener el lector cuando quieras.

List supported apps as plain text. Do not promise inDrive capture based only on a future parser or an import capability.

The privacy link supplements this screen. Upload consent is separate and is not bundled into reader permission.

**[new] Allow readable scrolling at larger fonts.** Do not reduce text to preserve a “no scroll” mock. Google’s published policy requires in-app disclosure describing access/use and affirmative consent for non-accessibility-tool uses; it does not make a privacy-policy link sufficient. [Google Play AccessibilityService requirements](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en).

### 6.4 Conditional tasks and decline paths

**Capture consent [mine].** Based on the active capability, including newer-device fallback:

> **“Este teléfono necesita otro permiso.”**  
> “Android te pedirá permiso para capturar la pantalla. Kompara la analiza aquí y no guarda ni envía la imagen.”

Primary **“Continuar.”** Secondary **“Ahora no.”** Then the real Android prompt. Do not label it “solo DiDi” when the path serves multiple apps.

**Ready state [mine].**

> **“Lector preparado.”**  
> “La primera oferta confirmará la lectura.”

Return to Hoy. No “ya está leyendo tus ofertas” before a real read.

**Declined reader [new].** Return to Hoy with **“Lector apagado.”** Offer review/example/import as applicable. No manual-trip promise and no forced account.

**Notifications [mine].** Later, separately:

> **“¿Te avisamos si el lector se detiene?”**

**“Activar avisos” / “Después.”** Ask once in context.

**Battery [mine].** Optional, when relevant: **“Evita que se interrumpa la lectura.”** One instruction and one system action per view. Do not treat “Ya quedó” as verified success.

### 6.5 Signup placement

**[mine] Signup is absent from the free-reader setup.** It starts only after the driver chooses import, trial or an account service. Every branch can return to the free product.

Compared with the GigU flow documented in Market, retain phone → WhatsApp code and remove compulsory name/email/platform collection. Do not move a mandatory identity toll a few screens later and call it optional.

**[mine] City belongs in comparison.** Ask **“¿En qué ciudad manejaste esta semana?”** when needed. No location permission, phone-prefix inference or silent CDMX fallback.

## 7. Monetization and paywall placement

### 7.1 Final free/paid line

**[new] This explicitly changes the current paywall boundary.**

| Job | Free | Premium |
|---|---|---|
| Live reader | All supported reading, valid verdicts, neutral states, costs, thresholds, recovery and example | Nothing |
| Daily review | Hoy and days within the free history window | Older retained days |
| Weekly records | Current plus three previous **calendar** weeks | Older retained weeks |
| Monthly review | Current-month summary | Older monthly earnings archive |
| IMSS | Review, official verification route, source limitations, valid estimated progress if implemented | Nothing essential to this job |
| Import | Voluntary import, review and saved result; account required | No import fee |
| Comparison | Availability, definitions and one valid scoped city-standing sentence | Personal cross-app comparison and supported detailed city benchmarks |
| Export | Simple image for accessible free periods | Monthly record PDF and paid-period exports |
| Data controls/help | Free | Nothing |

**[new] Saving an older import never deletes or hides the confirmation of what was just saved.** The final review remains accessible for that task; future archive browsing follows the stated window. Before uploading an old week, disclose **“Esta semana quedará guardada; verla después está incluido en Premium.”**

**[theirs] Four-week generosity is a commercial hypothesis, not a proven retention result.** A current-month summary and free IMSS review are separate entitlements so calendar boundaries do not create misleading holes.

### 7.2 Availability, verification and payment are separate

**[new] Evaluate in this order:**

1. **Does the requested result exist?**  
   If not, explain the missing data. No sales CTA pretending payment will create it.
2. **Does the result meet the relevant data requirement?**  
   For paid population comparison, follow Account §0.5. Explain verification before purchase.
3. **Does the driver have access?**  
   Then show the result or one contextual Premium offer.

**[mine] Personal history and reports never acquire a hidden import-verification gate.**  
**[new] Already positive verification and entitlement should use their valid cached state during transient network errors.** No sudden downgrade from a fetch failure.

If an existing subscriber lacks the required data for a comparison:

> **“Tu plan está activo. Falta verificar los datos de esta comparación.”**

Primary **“Verificar mis datos.”** Other paid capabilities remain usable. No second purchase prompt.

### 7.3 Tease pattern

**[mine] No blur wall.** One readable card names the requested benefit.

Examples:

- **“Compara lo que te dejó cada app.”**
- **“Ve tu semana frente a este grupo de tu ciudad.”**
- **“Revisa todas tus semanas.”**
- **“Prepara tu resumen del mes.”**

Show actual date/app availability, not fabricated concealed amounts. A demonstration is explicitly labeled **“Ejemplo”** and never personalized.

**[new] Comparar remains a useful root even when free:** selectors, available source context, valid free standing, missing-data explanations and the relevant next action remain visible. A free user is not sent to a blank paid screen.

No Premium promotion on Hoy, the chip, recovery, permission setup or post-trip overlays.

### 7.4 Trial

**[mine] Fourteen days, explicit start, no card, no automatic conversion.** This requires a real promotional entitlement separate from a Play subscription; it is not achieved through copy alone.

Trial screen:

> **Prueba Premium 14 días**  
> “Sin tarjeta. No habrá ningún cobro al terminar.”  
> “Tu prueba empezará hoy y terminará el {fecha}.”

Primary **“Empezar prueba gratis.”** Secondary Close.

**[new] Authentication may precede final activation, but OTP completion does not start the clock.** Return to the final trial screen with dates and the usable benefit identified.

**[mine] Start only when at least one useful eligible paid result is available.** A calendar event does not qualify by itself.

Tu plan shows **“Tu prueba termina el {fecha}.”** One optional reminder near expiry; no daily modal or live countdown.

After expiry:

> **“Terminó tu prueba. Tu lector sigue gratis.”**

### 7.5 Purchase screen and Tu plan

**[mine] Purpose:** understand the chosen paid benefit and billing terms.  
**Hero:** actual localized monthly price.  
**Primary:** **“Suscribirme por {precio} al mes.”**

**Layout:**

1. Close.
2. Contextual benefit title.
3. One truthful preview or availability description.
4. Three short benefits: comparisons, complete history, monthly PDF.
5. Actual Play price; `$79 MXN/mes` remains a pricing hypothesis within the Brief, not hardcoded truth.
6. Primary.
7. **“Se renueva cada mes hasta que canceles en Google Play.”**
8. Secondary **“Restaurar compras.”**
9. Working **“Términos”** and **“Privacidad.”**

**[new] Trial terms and subscription renewal terms are visually distinct.** Do not place “sin tarjeta” beside ambiguous automatic-renewal language.

**Tu plan [mine]:** current access, end/renewal date, existing earned benefits, **“Administrar suscripción”**, restore. An active subscriber does not receive the acquisition page as their account screen.

**States:**

- Trial eligible/active/ended.
- No usable paid result.
- Price loading/unavailable.
- Purchase pending/canceled/failed.
- Subscribed/already active.
- Restore found/nothing found/error.
- Play unavailable.
- Offline with valid cached entitlement.
- Expired online session without losing local reader access.

Canceling returns to the exact originating week or comparison. No plan choice, countdown or purchase UI interrupts driving.

## 8. Score v2 against the supplied rubric

These are **provisional specification scores**, not measured driver outcomes.

| Criterion | Score | One-line assessment |
|---|---:|---|
| **1. One job per screen** | **9/10** | Minimal Hoy, period review, one comparison question and separate evidence sheets create a clear hierarchy. |
| **2. One primary action** | **9/10** | Setup, recovery, import and purchase each have one explicit verb CTA; read-only views avoid competing filled buttons. |
| **3. Navigation that disappears** | **9/10** | Three stable roots, Comparar visible, settings behind a familiar gear, explicit Back and period-preserving returns. |
| **4. Numbers discipline** | **9/10** | One chip rate, no grids or duplicate standing, three-value initial-viewport budget and deliberate detail disclosure. |
| **5. Copy** | **8/10** | Short tú copy and scope-aware labels remove overclaims; “neto aprox.” still needs driver comprehension testing. |
| **6. States are designed** | **9/10** | Unknown inputs, source conflicts, unsupported capture, empty cohorts, verification, offline and billing have distinct responses. |
| **7. Time-to-value** | **7/10** | Local value appears first, but cost uncertainty and device permission paths remain unmeasured. |
| **8. Consistency & feel** | **7/10** | The shared component contract is coherent; actual Compose rendering, mounted-phone legibility and gesture behavior are not yet demonstrated. |
| **Total** | **67/80** | **Does not yet pass:** the bar requires every criterion ≥8 and total ≥68. |

### What raises the two criteria below 8

**Time-to-value: 7 → at least 8 [new].**

Run a first-use test on representative 360–400dp Android phones, including a device needing capture fallback and one with aggressive battery restrictions.

Pass targets:

- At least 90% of participants reach and correctly explain the local example within 60 seconds.
- No account, notification or upload ask precedes the example.
- Participants who do not know their costs can continue without guessing or mistaking the result for personalized net.
- Every permission denial/cancel path returns to a usable local screen.
- Reader readiness and a successful real offer reading are understood as different states.

**Consistency & feel: 7 → at least 8 [new].**

Build the representative Compose surfaces with shared tokens and test them at default and enlarged text, dark and light, with a mounted phone at 60cm.

Pass targets:

- No clipped rates, labels or controls.
- All interactive targets meet 48dp; primary buttons meet 52dp.
- The chip avoids host fare and decision controls across supported layouts.
- No accidental edit commits during repeated drag/long-press attempts.
- Preview and exported image/PDF contain the same period and values.
- One-second exposure testing meets the comprehension criterion below.

**Hard-fail status:** the specification proposes no auto-actions or platform logos, preserves the free loop, and separates onboarding asks. The driving-glance hard fail remains unverified until the chip and recovery surfaces pass device testing. A written specification cannot certify it.

## 9. Residual disagreements and evidence needed

These are unresolved empirical or data questions. The defaults above are final for v2; none authorizes inventing data while waiting.

| Residual disagreement | V2 default | Evidence that would settle it |
|---|---|---|
| **One rate versus two; “neto aprox.” versus a longer expense qualifier.** O1 gives the second rate more live value than I do. | One rate, glyph, “neto aprox.” | Test randomized one-second exposures at 60cm with Mexican drivers, including color-vision differences and host gross-rate badges. Measure correct threshold category, unit identification, understanding of expenses and false confidence on missing data. Target ≥90% category/unit recognition; no recurring interpretation of green as route safety or automatic acceptance. Add a second rate only if it improves decisions without degrading those measures. |
| **Account tab versus Comparar tab.** | Keep Comparar, honoring the founder’s direction. | Tree/first-click tests for finding city comparison, costs and reader recovery. Compare completion time and mistakes, then trial discovery among eligible drivers. Evidence may justify proposing a later change to the founder; copying Uber’s Account label alone does not. |
| **Four free weeks versus two.** | Current plus three previous calendar weeks; current-month summary independently free. | Cohort test measuring retained weekly review, useful trial activation, paid conversion and support complaints. Compare drivers with genuinely available paid data. The documents provide no evidence that either window optimizes the business. |
| **Fixed-cost usefulness versus setup burden; a renter’s goal versus expense deduction.** | Optional real rent/insurance deductions; no goal. | Sessions with renters and owner-drivers using their actual payment schedules. Ask them to explain a day/week result and the daily deduction. Reconcile examples across non-driving days, multiple apps and cost changes. A goal can return only if drivers demonstrate a distinct planning need that accounting does not answer. |
| **How much IMSS progress Kompara can responsibly compute.** | Free records/verification utility now; calculated progress only with validated rules and sufficient data. | Current rule review, confirmed income definition, provenance audit and reconciliation against official/platform records for real cases. Test incomplete months and boundary weeks. Neither competitive positioning nor a threshold constant proves eligibility. |
| **Whether low-quality live capture supports an earnings hero at all.** | Qualify estimates and exclude unconfirmed offers from earned totals. | Compare the local ledger with final platform statements across completed, canceled, expired, multi-app and interrupted-reading scenarios. Set release criteria for completeness and outcome classification. If the evidence is weak, keep “Ingresos registrados” or an incomplete state rather than polishing an unreliable net total. |
| **Whether the quick overlay editor earns its remaining interaction risk.** | Deliberate long-press, one draft value, explicit Save/Cancel, safe placement only. | Parked-device gesture tests followed by controlled mounted-phone testing: accidental opens, accidental saves, host occlusion and task completion. If safe placement or gesture separation fails, remove the shortcut and keep the in-app editor. |
| **Whether imports can deliver the promised simple review.** | Consent → one parse → review → idempotent save; no invented daily detail. | Verify the server contract, retention behavior, preserved nulls, per-app reconciliation and coverage-matched ratios. Test duplicate submission and lost connections after save. Until that works, the UI must not claim parse-once saving or complete reconciled earnings. |