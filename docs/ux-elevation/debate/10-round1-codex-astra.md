# Kompara — Round 1: independent product & UX elevation

The core idea is strong. The current product obscures it with an analytics dashboard, a permission-management screen, a tax product, and a subscription funnel competing for attention.

**Kompara should become a dependable answer to two questions: “¿Me conviene este viaje?” and “¿Qué me quedó?”** Everything else must earn its place by improving those answers.

This review treats the [screen inventory](./inventory.md) as the behavioral ground truth, supported by the requested documents, HTML references, templates, and selective Compose source checks. Older documents contradict the inventory in several places. The recommendations below are design proposals, not validated usability results or claims that the current implementation already supports them.

### 1. Diagnosis (ruthless, evidence-based)

| Rank | Problem | Evidence from the current product | Why it hurts a driver | Severity |
|---|---|---|---|---|
| **1** | **“Neto” does not have a dependable meaning.** | **Tus costos:** “ganancia neta de verdad”; its `$2.15/km` preview excludes insurance and rent. **Fiscal:** “tu neto aparecerá igual al bruto” when DiDi commission is unavailable. **Inicio:** “GANANCIA NETA ESTA SEMANA.” | The product’s central promise becomes impossible to audit. A driver can reasonably believe all driving expenses have been deducted when they have not. Missing commission cannot become zero commission. | **Critical** |
| **2** | **A failed reading can look like an unfavorable offer.** | **Verdict chip:** “Sin tarifa,” “Datos incompletos,” and `—`; the inventory’s state rules assign red when fare is missing and cap incomplete readings at yellow. | Red should mean below the driver’s financial threshold. It must not mean Kompara failed to read the screen. This ambiguity can influence a time-sensitive decision. | **Critical** |
| **3** | **Setup declares success before the reader is operational.** | **Onboarding completion:** “Kompara ya está leyendo tus ofertas.” The inventory says screen capture has not started. **Lector:** a static “Oferta entrante · Uber / $148 / 16 km · 22 min · efectivo” occupies 220dp. | After granting an alarming permission, the driver expects a working tool. Seeing no chip on the next offer is a credible first-five-minute uninstall trigger. The fake offer provides no evidence of readiness. | **Critical** |
| **4** | **The privacy promise conflicts with the import flow.** | **Pitch:** “Lo que ves en tu pantalla se queda en tu teléfono.” **Importar semana:** “Extrayendo datos con IA…”; the inventory describes uploading selected files for preview and again for saving. | The brief prohibits transmitting screen content. Restyling this flow cannot resolve that conflict. The driver should not need to discover which screen-related data the privacy promise excludes. | **Critical** |
| **5** | **Comparar presents a broader conclusion than its data supports.** | **Comparar:** “Le ganas al 78% de los conductores de tu ciudad,” “Top 22%,” and optional “Datos de muestra.” The [comparison spec](../../docs/comparar-redesign-spec.md) documents no combined population and no net-total benchmark; the benchmark notes identify synthetic seeds. | A personal-looking city ranking implies real, comparable observations. Blended personal earnings, a single-platform population, and demonstration data do not establish that ranking. | **Critical** |
| **6** | **Fiscal turns an estimate into an apparent coverage confirmation.** | **Fiscal:** “Cubierto este mes.” **Notification:** “Uber: cubierto por el IMSS.” The same screen ends with “Estas son estimaciones…” | A prominent confirmation and a distant disclaimer communicate different things. The app has not demonstrated that it knows the driver’s actual enrollment or coverage status. | **Critical** |
| **7** | **The free reader is behind a long acquisition and permission funnel.** | Three pitch pages precede **“Crea tu cuenta,”** phone, code, profile, disclosure, Accessibility, a granted state, OEM instructions, and completion. Signup’s three screens all appear under **“1/5.”** | The driver spends attention, data, and trust before seeing value. The progress indicator understates the work. A WhatsApp outage can block a product whose core computation is local. | **High** |
| **8** | **The five-tab structure reflects implementation modules rather than the driver’s day.** | **Inicio · Comparar · Lector · Fiscal · Ajustes** receive equal bottom-bar weight. **Historial** sits inside Ajustes. | Earnings history is harder to find than configuration and fiscal estimates. “Lector” is an operating state and control, not a recurring information destination deserving one fifth of the navigation. | **High** |
| **9** | **The app repeatedly asks the driver to interpret too many numbers.** | **Inicio:** weekly total, four tiles, “Top X%” pills, locked strip, recommendations, completeness note. **Comparar:** “Métrica \| Tú \| Uber \| DiDi \| Lugar,” six rows at 12sp. **Resumen de la semana:** five totals plus five metric cards. | There is no stable answer to “Which number matters here?” Shrinking the table or stacking its labels does not solve its information burden. | **High** |
| **10** | **The paywall combines payment, missing data, and verification into one confusing lock.** | Premium users can see **“Importa una semana para desbloquear.”** Free weekly-summary locks have no CTA. **Ayuda** promises “sin pedir tarjeta,” while the paywall launches Play billing and describes automatic renewal. | A driver can pay and still encounter an unexplained prerequisite. A payment decision cannot solve unavailable data. The trial promise and purchase mechanics need separate treatment. | **High** |
| **11** | **Cost setup is essential but designed like a technical questionnaire.** | **Tus costos:** “Rendimiento (km por litro),” “Mantenimiento (MXN por km),” “Seguro (MXN por día),” “Renta o financiamiento (MXN por día).” No validation messages; EV values return blank on edit. | The driver may not know these values or understand their effect. An unanswered or misunderstood field undermines every subsequent “net” figure. | **High** |
| **12** | **The chip conceals its explanation while exposing a hidden, consequential edit.** | **Verdict chip:** no visible verdict word; plain tap does nothing. Long-press opens **“Verde desde”** and **“Rojo por debajo de”** sliders that save immediately. The missing-data hint is white at 10sp. | The driver cannot easily answer “Why is this yellow?” but can accidentally change future judgments. TalkBack alone does not address visual color blindness or a tired glance at night. | **High** |
| **13** | **Several messages blame or promise more than the app knows.** | **Inicio:** “Dejaste ir buenos viajes,” “Estás rechazando casi todo,” “Maneja más en ese horario.” **Pitch:** “Gana hasta 30% más por turno.” **Paywall:** “Si te ayuda a ganar $200 más a la semana, se paga solo.” | Expired offers are not necessarily deliberate rejections. An unattractive destination, a break, or personal safety can outweigh a rate. Historical earnings do not prove future uplift. | **High** |
| **14** | **Empty, loading, and recovery states are not designed as a coherent product.** | Multiple screens load blank. **Inicio:** “Aún no hay números” even with imported-only weeks; “Probar el lector” opens the simulator. **Ayuda** mentions a nonexistent “botón de reporte.” OEM setup includes an inert **“Avísame si el lector se apaga”** switch. | Silence and misleading actions feel like malfunction. A recovery tool that cannot explain its own state loses trust quickly. | **High** |
| **15** | **Navigation, chronology, and presentation disagree across surfaces.** | Pushed screens have no in-app back button. **“Semana del 8 jun”** differs from **“Semana del 8–14 jun.”** Weekly sharing opens **“Tu mes en Kompara.”** The exported PNG differs from its preview. Simulator changes real thresholds while its explanation can remain unchanged. | Drivers cannot trust where Back goes, which period they are viewing, what they are sharing, or whether an example changed the live tool. These are product-quality failures, not cosmetic inconsistencies. | **High** |

The most damaging combination is immediate: **register → grant permission → receive a false readiness message → see an unclear or absent verdict.** Improving acquisition copy without fixing that chain would merely deliver more drivers to the same disappointment.

### 2. The elevated concept

**Promise, es-MX:** “Antes de aceptar, ve cuánto te puede quedar con tus costos; al terminar, entiende cómo te fue.”

**Promise, English:** “Understand an offer’s estimated return after your driving costs, then review what your work actually produced.”

“Estimated” matters. Kompara observes offers and inferred activity; it does not become the platform’s payment ledger simply by calculating a rate.

#### The driver’s day

| Moment | Driver’s question | Kompara’s job | Surface |
|---|---|---|---|
| Before the shift | “¿Ya funciona?” | Show actual reader readiness, affected apps, and any missing setup. | **Hoy**, reader status |
| **Offer moment — priority 1** | “¿Me conviene con mis costos?” | Show one stable rate and an understandable verdict, or admit that it cannot calculate one. | **Floating chip** |
| During the shift | “¿Sigue funcionando?” | Stay quiet; surface a real interruption once. Keep accumulated estimates available without taking over the host app. | Status pill, **Hoy** |
| **End of shift — priority 2** | “¿Qué me quedó?” | Present today’s estimate, its expenses, and any missing or uncertain activity. | **Mi día** |
| **Week close — priority 3** | “¿Cómo me fue y qué cambió?” | Provide an earnings timeline and valid weekly comparisons, separating personal app results from city benchmarks. | **Ganancias**, **Comparar** |
| Month / fiscal preparation | “¿Qué le paso a mi contador?” | Export recorded income and estimated costs with their sources and limitations. | **Resumen del mes** |

Build around **the offer, the day’s result, and the week’s review**. Readiness is the prerequisite for all three.

#### The financial language must become explicit

| Context | Main label | Meaning |
|---|---|---|
| Live offer | **“Tras gasolina y desgaste”** | Estimated offer payment minus the driver’s variable running costs, including pickup distance where available. |
| Day/week with complete configured costs | **“Te quedan aprox.”** | Recorded eligible activity minus running costs and applicable fixed costs, before taxes. |
| Day/week without fixed-cost setup | **“Después de gasolina y desgaste”** | A partial expense-adjusted result; not an all-expenses profit claim. |
| Costs unavailable | **“Ingresos registrados”** | Income estimate before expenses; no net verdict. |
| Trip lifecycle uncertain | **“Sin confirmar”** | An observed offer that cannot yet be treated as completed earnings. |

Do not sum every displayed offer into earnings. Do not silently treat missing commission, pickup distance, or costs as zero. Do not equate offer-time hourly return with earnings per hour across an entire shift.

#### What to take from Uber Driver

Uber’s documented earnings structure supports a useful hierarchy: **week → day → individual trip**, with estimated amounts distinguished from payment statements. That hierarchy is worth adopting. [Uber earnings help](https://help.uber.com/en/driving-and-delivering/article/where-can-i-find-my-payment-statement?nodeId=b9cd62ee-81e1-4707-9b0b-9cda3cd4cb77)

| Principle | Kompara translation |
|---|---|
| One dominant result | One hero per screen; supporting numbers answer a specific follow-up question. |
| Status-first operational home | Reader readiness is visible immediately. No decorative offer card masquerading as live status. |
| One obvious next action | Activate, recover, inspect today, or review a selected period—according to state. |
| Earnings as a timeline | A week opens days; a day opens recorded activity. Period and source survive every navigation. |
| Disciplined trip cards | Time, platform name, amount, and status. Details live one level deeper. |
| Settings outside the main work | Costs, thresholds, account, and notifications behind a standard settings entry. |

Do **not** copy:

- A map without a genuine location-based job. Kompara has no reason to request location just to resemble Uber.
- Demand heatmaps or hourly city predictions unsupported by the available data.
- Streak pressure, acceptance coaching, or incentives to keep driving.
- “Aceptar” or “Rechazar” controls, even as verdict labels.
- Platform branding, logos, or replicas of proprietary host screens.
- Uber’s definition of earnings without checking what expenses it excludes.

Uber’s own offer badge already divides upfront fare by pickup-plus-trip distance; it is explicitly an estimate. Kompara’s reason to exist is the driver’s costs, chosen thresholds, coverage across apps, and review after the offer. [Uber’s badge explanation](https://www.uber.com/us/en/blog/piloting-estimated-earnings-per-kilometer-on-trip-requests/)

#### What to cut

1. **The standalone Lector tab.** Preserve its controls in operational status.
2. **The Fiscal tab and IMSS coverage verdicts.** Keep a modest monthly report; remove unsupported coverage confirmation and inferred tax liabilities.
3. **The Ajustes tab.** Keep settings behind the gear.
4. **The five-column comparison table, repeated percentile pills, and twenty-person graphics.** They duplicate meaning and obscure the comparison’s basis.
5. **Streaks, the unreachable weekly goal, and the brag grid.** They do not improve the core decision.
6. **Acceptance-rate coaching and “missed money” recommendations.** The app lacks the context to judge those decisions.
7. **The three-page pitch and earnings-uplift claims.** Demonstrate the calculation immediately.
8. **The current remote import flow and share-to-import target.** They conflict with this brief’s on-device constraint. This proposal does not assume an unbuilt local replacement.
9. **The standalone referral program screen.** Remove growth machinery from the initial elevated product; preserve already-earned benefits.
10. **The simulator’s live-setting playground.** A demonstration must not silently alter real preferences.
11. **Monthly Wrapped-style sharing as a destination.** Keep an accurate, optional share preview attached to the selected period.
12. **“Pronto” promises, simulated processing progress, and blanket blur walls.**

These are deliberate scope changes. A smaller product with dependable figures is more valuable than a larger one that requires footnotes to retract its headlines.

### 3. Information architecture

**Three top-level destinations, in this order:**

| Order | Name | Icon | Purpose |
|---|---|---|---|
| 1 | **Hoy** | Material Filled `Home` | Know whether the reader is ready and see today’s result. |
| 2 | **Ganancias** | Material Filled `DateRange` | Follow your money through weeks, days, and recorded activity. |
| 3 | **Comparar** | Material Filled `List`, with two aligned comparison rows | Compare your own app results or a supported city benchmark for one week. |

**Lector is a persistent state, not a tab.** **Ajustes** is a gear at the top right of tab roots. **Resumen del mes** lives under Ganancias.

Comparar has two explicitly separated views:

- **“Tus apps”**: your own results across platforms.
- **“Tu ciudad”**: your result against a matching weekly population.

This prevents city averages from masquerading as the driver’s own Uber or DiDi earnings. It also gives Comparar a useful personal-data job when real city benchmarks are unavailable.

Bottom navigation appears only on the three roots. Drill-ins have an explicit Back button. Editors, permission flows, purchase screens, and export previews have no bottom bar.

#### Fate of every current surface

“Kill” means remove the surface or behavior from the proposed product—not delete a driver’s existing records.

| Current screen / surface | Fate | Reason / destination |
|---|---|---|
| Root `LOADING` | **Keep, redesign** | Visible startup state; local home should not wait for account or benchmark networking. |
| Root `ONBOARDING` | **Keep, shorten** | Four first-run tasks, described in §6. |
| Root `AUTH` | **Merge into optional account flow** | Expired login cannot disable the free local reader. |
| Root `MAIN` | **Keep** | Three-root shell. |
| Wordmark-only top bar | **Merge into shared chrome** | Screen title and settings entry are more useful than repeated branding. |
| Five-tab bottom bar | **Replace** | Hoy, Ganancias, Comparar. |
| Pushed screens without Back | **Kill this pattern** | Explicit Back and preserved navigation context. |
| Inicio | **Merge into Hoy** | Today and readiness replace the weekly dashboard wall. |
| Inicio WatchdogBanner | **Merge into reader status** | One operational status model across the product. |
| Inicio GoalBar | **Kill** | No usable setup today; not necessary for the core loop. |
| Inicio CostNudge | **Merge into contextual setup** | Visible only when costs prevent a valid calculation. |
| Inicio recommendations | **Kill advice feed** | Retain factual personal observations only inside relevant period details. |
| Comparar | **Keep, rebuild** | One metric; separate personal and city comparisons. |
| Lector | **Merge into Hoy → Tu lector** | No independent tab or fake offer preview. |
| Fiscal | **Merge useful reporting into Ganancias → Resumen del mes** | Remove coverage assertions and estimated-tax product. |
| Ajustes | **Move behind gear** | Secondary navigation with grouped rows. |
| Tus costos | **Keep, simplify** | Separate running costs from optional fixed costs. |
| Tu semáforo | **Keep** | Shared settings model with the overlay editor. |
| Ayuda | **Keep** | Short, task-based recovery and explanation. |
| Tu cuenta | **Keep, optional** | Account entitlement, sign-out, deletion; no reader gate. |
| Historial | **Merge into Ganancias** | Earnings history becomes a main destination. |
| Resumen de la semana | **Merge into selected week in Ganancias** | One week-summary pattern. |
| Detalle del día | **Keep as Mi día** | Any recorded day is reachable. |
| Unreachable `day/{day}` variant | **Merge into Mi día navigation** | Week → day becomes real. |
| Standalone week/detail variants | **Merge** | One period-aware implementation and design pattern. |
| Paywall | **Keep, redesign** | Contextual benefit, clear prerequisites, separate trial and purchase. |
| Reusable PaywallGate | **Replace** | Readable benefit card; no blur wall or dead lock. |
| Importar semana: Loading, SignedOut, Picking, SharedReady, Uploading, Review, Saved, Error | **Kill current flow** | No remote file processing under the stated constraint. Retain existing local aggregate records. |
| Invita y gana | **Kill standalone screen** | Not part of the core loop; honor existing earned access. |
| Tu mes en Kompara / share preview | **Merge into Compartir resumen** | Selected period is preserved; preview equals exported artifact. |
| Simulador de viajes | **Merge into first-run example and Ayuda → Ver ejemplo** | Demonstration, not another product destination. |
| Pitch page 1 | **Merge into example** | Show the offer calculation. |
| Pitch page 2, “30%” | **Kill** | Unsupported gain claim. |
| Pitch page 3, privacy | **Merge into disclosures and privacy reference** | Explain processing where consent occurs. |
| Signup: Teléfono | **Move to optional Premium activation** | After value, following an explicit trial or account choice. |
| Signup: Código | **Keep within optional signup** | Two-step WhatsApp verification. |
| Signup: Perfil | **Kill mandatory screen** | City is selected in comparisons; name is unnecessary. |
| Divulgación | **Keep prominently** | Immediately before Accessibility permission. |
| Pantalla limitada | **Merge into Hoy, lector apagado** | No punitive detour or fictitious manual-entry promise. |
| Accessibility waiting | **Keep as activation state** | Clear instructions and resumable system handoff. |
| Accessibility granted | **Merge into activation state** | Permission granted is not proof that a real offer was read. |
| OEM survival kit | **Move to contextual reader help** | Device-specific guidance when relevant, not a mandatory wall. |
| ¡Todo listo para manejar! | **Kill separate screen** | Actual readiness appears on Hoy. |
| Verdict chip | **Keep, redesign** | One rate, visible verdict, neutral unknown states. |
| Quick threshold sheet | **Keep, simplify** | Large controls and explicit save; no live sliders. |
| Reader-down overlay banner | **Keep, unify** | Same status and recovery route as Hoy. |
| E1 “Kompara leerá tu pantalla” dialog | **Keep only when capture consent is required** | Distinct, contextual disclosure for the actual device path. |
| E2 Account deletion dialog | **Keep** | Explicit deletion scope and subscription implications. |
| E3 `OcrConsentActivity` | **Keep system handoff** | Return to a visible granted, canceled, or failed state. |
| E4 `ShareImportActivity` | **Kill** | Removed with remote import; no invisible upload path. |
| Watchdog notification | **Merge into Estado del lector** | One failure family, with the affected app or permission identified. |
| OCR foreground notification | **Keep when required** | Plain reader status and “Detener”; no “OCR” jargon. |
| OCR stopped notification | **Merge into Estado del lector** | Avoid duplicate technical-failure messages. |
| IMSS month-end notification | **Kill** | No inferred coverage confirmation. |
| Weekly-close notification | **Keep, optional** | Opens that week’s earnings, not a monthly share card. |
| Fiscal PDF | **Replace with recorded-income report** | Sources, expenses, missing data, and estimation status are explicit. |
| Share-card PNG | **Keep, simplify** | Exact period and exact preview; no unsupported ranking. |
| Accessibility-settings description | **Keep, rewrite** | Match supported apps, local processing, and read-only behavior. |
| Settings notification switches | **Keep only functional controls** | Reader alerts and optional summaries; delete sharing pressure and inert switches. |
| Debug Premium toggle / development strings | **Exclude from release UX** | They are not product surfaces. |

### 4. Screen-by-screen spec (every surface in the NEW IA, including drill-ins)

#### Shared design contract

Preserve the existing identity: **emerald `#059669`, slate neutrals, Inter, dark by default, and the verdict triple**. Reconcile the stale palette in `design-principles.md` with the current tokens instead of introducing another palette.

- **Hero:** 44sp on roots; 30sp for offer rates and detail figures.
- **Titles:** 24sp; body and controls 16sp; supporting text 14sp. No critical 10–12sp copy.
- **Layout:** 16dp screen edges and card padding; existing 4/8/12/16/24 spacing scale.
- **Shapes:** existing 12dp cards, 14dp buttons, 16dp overlay.
- **Targets:** at least 48dp; primary controls at least 52dp.
- **Cards:** group one calculation or task. Ordinary lists use rows and dividers.
- **Color:** verdict fills belong to offer judgments. Reader readiness uses emerald and explicit text. Unknown states use slate.
- **Contrast:** dark text throughout yellow verdicts. Refine green/on-green roles for small text; use the existing darker emerald family where white button labels need stronger contrast.
- **Numbers:** whole pesos for period heroes; cents in breakdowns. Rates use consistent precision matching threshold precision. Never truncate a value or unit.
- **Large text:** stack content and grow surfaces; do not shrink text to preserve a mock’s geometry.
- **Primary action:** the main interaction can be a row or selector. Do not add a filled “Continue” button to a read-only screen merely to satisfy a template.
- **Loading:** preserve the screen’s title and geometry. Never replace an established value with zero while loading.
- **Offline:** local reading and earnings remain available. Only network-dependent information gets an offline state.

The one-second test means **recognizing the screen’s answer and next action**, not reading a disclosure, completing a form, or auditing a report while driving.

All example figures below are illustrative design content.

#### 4.1 Hoy

**Purpose:** Establish readiness and show today’s result.  
**Hero:** Today’s expense-adjusted estimate, or the missing setup that prevents it.  
**Primary action:** State-dependent: **“Activar lector,” “Revisar lector,” “Agregar mis costos,”** or **“Ver mi día.”**

**Top to bottom:**

1. Header **“Hoy”**; settings gear.
2. Compact reader row: **“Lector preparado”**, **“Lector funcionando”**, or **“DiDi necesita atención”**; chevron.
3. Label **“Te quedan aprox. hoy”**; hero **“$680”**.
4. Scope line **“Después de tus costos · antes de impuestos.”**
5. One supporting line: **“8 viajes registrados.”**
6. If material: **“Faltan datos de algunos viajes.”** Tappable for the explanation.
7. Primary **“Ver mi día.”**
8. Bottom navigation.

No weekly metrics, platform chips, streak, sharing, percentile badges, recommendations, or paywall.

**Secondary actions:** Reader row and settings only.

**States:**

- **First run / reader off:** “Activa el lector para empezar”; primary activates it.
- **Prepared, no activity:** “Aún no registramos viajes hoy”; no fake `$0` implying a completed day.
- **Missing costs:** “Ingresos registrados” can show the income estimate; primary “Agregar mis costos.”
- **Loading:** Stable title and reader row; skeleton in the amount.
- **Partial reader failure:** One recovery action; unaffected platforms stay identified as available.
- **Error:** “No pudimos abrir tu resumen”; “Intentar de nuevo.”
- **Locked:** Never.

**Removed:** The weekly dashboard and acquisition clutter.  
**One-second glance:** “The reader is working; today I have approximately this much left.”

#### 4.2 Tu lector

**Purpose:** Explain readiness and recover a specific failure.  
**Hero:** One operational status.  
**Primary action:** The one action needed by the current state.

**Top to bottom:**

1. Back; **“Tu lector.”**
2. **“Lector preparado”**, **“Lector funcionando”**, or **“Necesita atención.”**
3. State-specific explanation.
4. Plain-name rows for supported apps: **“Uber — Esperando una oferta,” “DiDi — Lectura comprobada,” “inDrive — No disponible en esta versión.”**
5. When known: **“Última oferta leída: hace 2 min.”**
6. Main recovery button, if required.
7. Secondary rows: **“Tu semáforo,” “Ver ejemplo,” “Evitar interrupciones.”**
8. **“Pausar lector”** when active.

Pausing affects Kompara only; it does not put the driver offline in another app.

**States:**

- **No permission:** “Falta activar el permiso”; **“Activar lector.”**
- **Starting:** “Preparando lector…”; repeated start disabled.
- **Prepared:** “La primera oferta confirmará la lectura.”
- **Working:** Actual successful host reading recorded.
- **Partial failure:** “No podemos leer DiDi por ahora. Uber sigue disponible.”
- **Unsupported parser:** “Esta versión de DiDi aún no es compatible”; no futile restart loop.
- **Capture consent required:** “Este teléfono necesita otro permiso”; **“Continuar.”**
- **Canceled consent:** “El lector sigue apagado”; **“Intentar de nuevo.”**
- **Offline:** Existing local reader keeps working; do not show a generic disconnection failure.
- **Locked:** Never.

**Removed:** Static offer, unconditional restart, and accessibility/OCR terminology in ordinary status.  
**One-second glance:** “Which app works, what is wrong, and what fixes it.”

#### 4.3 Ganancias — selected week

**Purpose:** Review earnings chronologically.  
**Hero:** Selected week’s result.  
**Primary action:** Open a day.

**Top to bottom:**

1. **“Ganancias”**; settings gear.
2. Period selector **“8–14 jun 2026 ▾.”**
3. **“Te quedan aprox.”**; **“$3,450.”**
4. Scope/source line; **“Cómo se calculó.”**
5. Seven simple day rows, each with weekday/date and amount.
6. A row **“Comparar esta semana.”**
7. A row **“Resumen del mes.”**
8. Secondary share icon labeled **“Compartir esta semana.”**
9. Bottom navigation.

A seven-row timeline is the default. A chart is unnecessary until testing shows that it improves comprehension.

**States:**

- **First run / empty:** “Aquí aparecerá lo que registres con el lector”; **“Ir a Hoy.”**
- **Current week:** “Semana en curso.”
- **Imported aggregate already stored locally:** Show **“Resumen importado”** and its known period. Do not invent daily rows; state **“Este resumen no incluye el detalle por día.”**
- **Partial:** Show coverage and missing-data notice; never extrapolate a full week without saying so.
- **Loading:** Preserve selected week.
- **Locked old week:** Show period and readable Premium explanation; open paywall.
- **Error:** Keep other locally available periods accessible.

**Removed:** Separate Historial and WeekSummary presentations, metric grids, ranks, source conflicts.  
**One-second glance:** “This is the week, this is the estimate, and these are its days.”

#### 4.4 Elegir periodo

**Purpose:** Reach another recorded week or month.  
**Hero:** Selected period.  
**Primary action:** Select a period.

**Layout:** Bottom-sheet title **“Elegir semana”** or **“Elegir mes”**; periods newest first, grouped by month/year; checkmark on selection; close control.

Old locked weeks show a small lock and **“Premium”**, not blurred money. Tapping opens the relevant paywall and preserves the requested period.

**States:** No data → “Todavía no hay semanas registradas”; loading → rows; error → retry; locked → explicit access action; first run → current week remains selectable.

**Removed:** Tiny dropdown menus and inconsistent week labels.  
**One-second glance:** “I can find last week without learning a new screen.”

#### 4.5 Mi día

**Purpose:** Understand the result and activity for a specific day.  
**Hero:** That day’s result.  
**Primary action:** Open a recorded trip or offer.

**Top to bottom:**

1. Back; **“Miércoles 10 de junio.”**
2. **“Te quedan aprox.”**; amount.
3. A three-line breakdown: **“Ingresos registrados,” “Gasolina y desgaste,” “Gastos fijos.”**
4. Coverage notice and **“Cómo se calculó.”**
5. **“Viajes registrados”** with rows: **“14:32 · Uber”**, amount, **“Estimado.”**
6. Collapsed **“Ofertas sin confirmar.”**
7. Optional collapsed **“Tus horarios de ese día.”**
8. Previous/next recorded day navigation.

Personal hourly data may appear in the optional section only when the time basis is available. Label it **“Tiempo estimado”** if inferred. Do not imply a city forecast.

**States:**

- **Empty:** “No registramos actividad este día.”
- **Only offers:** “Vimos ofertas, pero no confirmamos viajes”; no earned-money total.
- **Incomplete capture:** “El lector estuvo apagado parte del día.”
- **Missing fixed costs:** Omit a fabricated deduction; label the result’s actual scope.
- **Loading/error:** Keep date and Back.
- **Locked:** Only when opening a day inside an older paid period.

**Removed:** Five metric cards, acceptance funnel, automatic “best hours” advice.  
**One-second glance:** “What remained that day, and whether the estimate is complete.”

#### 4.6 Detalle de una oferta / viaje registrado

**Purpose:** Explain a specific recorded calculation.  
**Hero:** **“Te quedarían $96”** or the recorded estimated result.  
**Primary action:** **“Ver cálculo.”**

**Top to bottom:**

1. Back; **“Uber · 14:32.”**
2. Explicit lifecycle status: **“Viaje registrado · estimado,” “Oferta no tomada,”** or **“Sin confirmar.”**
3. Hero amount with its qualification.
4. The verdict recorded at that moment.
5. **“Pago de la oferta,” “Distancia, incluida la recogida,” “Tiempo de la oferta.”**
6. **“Ver cálculo”** expands running-cost deduction and threshold used.
7. **“El pago final puede cambiar.”** when no final payment is known.

No passenger name, address, raw text, map, or screenshot is retained.

**States:** Missing input gets its named reason; no verdict when uncomputable; historical calculation unavailable → “No guardamos el desglose de esta oferta”; loading/error retain context; locked follows the containing period.

**Removed:** Ambiguous offer rows that imply confirmed income.  
**One-second glance:** “This was the estimate for this specific offer, with this confidence.”

#### 4.7 Cómo se calculó

**Purpose:** Make a number auditable without cluttering every screen.  
**Hero:** The selected result and its scope.  
**Primary action:** **“Entendido.”**

**Layout:** Sheet title; period; income basis; running-cost deduction; fixed-cost deduction where applicable; included activity; missing data; time basis if relevant; button.

Example copy:

> “Calculamos con los viajes registrados por el lector. Algunos pagos pueden cambiar.”  
> “Gasolina y desgaste: tus costos guardados para esas fechas.”  
> “Antes de impuestos.”  
> “No incluye viajes que Kompara no pudo registrar.”

This sheet explains limitations; it does not hide essential qualifications. **“Estimado”** and **“Faltan datos”** remain beside the original result.

**States:** Known calculation, partial calculation, aggregate-only source, or unavailable breakdown. Never locked independently.

**Removed:** Scattered technical caveats and unexplained dashes.  
**One-second glance:** “What this amount includes and what it does not.”

#### 4.8 Comparar

**Purpose:** Answer one weekly comparison question.  
**Hero:** One supported difference.  
**Primary action:** **“Ver comparación”** for the selected metric, or the contextual trial action when locked.

**Top to bottom:**

1. **“Comparar”**; settings gear.
2. Week selector; default to the latest completed week with data.
3. Segments **“Tus apps” / “Tu ciudad.”**
4. Metric selector, default **“Por km.”**
5. One comparison result.
6. Two aligned, full-width comparison rows.
7. A short scope statement.
8. **“De dónde salen estos datos.”**

**Tus apps example:**

- Hero: **“$1.20 más por km en DiDi.”**
- Rows: **“Uber · Tú — $6.80/km”** and **“DiDi · Tú — $8.00/km.”**
- Scope: **“Después de gasolina y desgaste. Son tus viajes de esta semana.”**
- Qualification: **“Manejaste en horarios distintos; esto no asegura que una app siempre pague más.”**

Distance and time definitions must match before calculating a difference. Fixed costs are not arbitrarily assigned to one platform.

**Tu ciudad:**

- Explicit selectors **“Ciudad”** and **“App.”**
- Rows **“Tú en DiDi”** and **“Promedio de DiDi en CDMX.”**
- Always show the same metric, week, expense definition, and platform on both sides.
- If population data are before costs, label them **“Antes de tus gastos”** and use an equivalent personal measure. Never compare personal expense-adjusted earnings with population gross earnings.
- Use **“Promedio”** for an arithmetic mean. Do not call it the “typical driver.”
- No city ranking from synthetic data; no blended personal result against one platform’s cohort.
- No monthly, daily, or hourly population inference from weekly observations.

**States:**

- **One personal app:** “Esta semana solo registraste Uber”; no invented second-app result.
- **No personal data:** “Necesitas una semana con datos para comparar.”
- **No valid city cohort:** “Todavía no tenemos una comparación confiable para esta app y ciudad.”
- **Metric unavailable:** “Falta distancia para comparar por km”; explicit choice of another metric, never automatic substitution.
- **Synthetic data:** Available only in a clearly labeled example, without a personal rank or personalized sales claim.
- **Locked:** Show availability first, then one benefit card.
- **Loading:** Keep filters; no changing metric while loading.
- **Offline:** Last available benchmark with its actual week, or “Conéctate para cargar la comparación.”
- **Error:** Personal results remain visible.

**Removed:** Six-row table, universal percentile hero, commission ranking, unsupported “earn more here” advice.  
**One-second glance:** “Who is being compared, on what measure, and for which week.”

#### 4.9 Comparison selectors and provenance

**Purpose:** Change scope without mixing unlike data.  
**Hero:** Current city, app, or metric.  
**Primary action:** Select the desired item.

**Layout:** One bottom sheet at a time: **“Elegir ciudad,” “Elegir app,”** or **“Qué quieres comparar.”** Plain-name rows; selection checkmark; close.

No silent CDMX default. Changing city does not alter the driver’s thresholds.

**“De dónde salen estos datos”** opens a read-only sheet containing week, platform, metric definition, source, sample size when actually available, and known exclusions.

**States:** Unsupported combination → explanatory row; missing metadata → comparison withheld where necessary; loading/error → scoped to benchmark retrieval; never independently paywalled.

**Removed:** A legend required to decode misleading column names.  
**One-second glance:** “These are the people and numbers behind the comparison.”

#### 4.10 Resumen del mes

**Purpose:** Prepare an accurate record for personal review or a contador.  
**Hero:** Recorded monthly result, explicitly estimated where applicable.  
**Primary action:** **“Preparar PDF.”**

**Top to bottom:**

1. Back; **“Resumen del mes.”**
2. Month selector.
3. **“Te quedan aprox.”** and amount, or **“Ingresos registrados”** when costs are incomplete.
4. One monthly breakdown: recorded income, running costs, fixed costs.
5. Platform totals as plain rows.
6. **“Datos incluidos”** with coverage.
7. **“Es un resumen de tus registros, no un comprobante fiscal.”**
8. **“Preparar PDF.”**

No IMSS coverage status, minimum-wage threshold, inferred withholding rates, or seven-paragraph legal explainer.

**States:** Empty → “No hay registros de este mes”; partial → prominent coverage notice; locked → readable report description; generating → actual progress state; failure → retry without losing month; first run → no fake example mixed into the report.

**Removed:** Coverage forecasting and tax estimation.  
**One-second glance:** “This month’s recorded result, and a report I can inspect before sharing.”

#### 4.11 Compartir resumen / PDF preview

**Purpose:** Show exactly what will leave the phone.  
**Hero:** The actual rendered export.  
**Primary action:** **“Compartir.”**

**Top to bottom:**

1. Close; **“Compartir semana”** or **“Revisar PDF.”**
2. Explicit date range.
3. Exact artifact preview.
4. For the optional image card: **“Mostrar montos”**, off initially.
5. **“Compartir”** opens the system chooser; WhatsApp is available through that chooser.

The image contains the period, one result if enabled, its estimation label, and a small Kompara attribution. No rank, streak, best-app claim, or unrelated monthly figure.

The PDF includes the actual breakdown, sources, costs, coverage, and limitations. It contains no passenger information.

**States:** Rendering, ready, no eligible data, export error, share canceled. Closing does not discard records. PDF access follows Premium; sharing the current free week’s simple image is free.

**Removed:** Different preview/export renderings and mandatory WhatsApp branding.  
**One-second glance:** “This is exactly the period and information I am about to share.”

#### 4.12 Tus costos

**Purpose:** Establish the personal cost basis.  
**Hero:** **“Gasolina y desgaste: $2.00/km.”**  
**Primary action:** **“Guardar costos.”**

**Top to bottom:**

1. Back; **“Tus costos.”**
2. Hero preview, initially **“Faltan tus datos.”**
3. Choice **“Gasolina / Eléctrico.”**
4. For gasoline: **“Precio por litro”**, **“Rendimiento de tu auto”**, with unit **“km por litro.”**
5. For EV: **“Precio por kWh”**, **“Consumo cada 100 km.”**
6. **“Desgaste por km.”** Helper: **“Mantenimiento y llantas.”**
7. Link **“Ayúdame a calcularlo.”**
8. Row **“Agregar renta y seguro”** opens fixed costs.
9. **“Estos costos se usarán desde hoy.”**
10. **“Guardar costos.”**

Examples are placeholders, not hidden defaults or asserted market prices. A driver who already knows their combined running cost can enter it through **“Ya sé mi costo por km”** instead of completing the estimator.

**States:** New, saved, edited, invalid number, unknown cost, saving, local-save error. Restore EV inputs correctly. A skipped or unknown cost does not become zero.

Cost changes apply prospectively by default. Do not silently rewrite two years of history.

**Removed:** Seven equally weighted fields, unexplained fixed-cost effects, and “ganancia neta de verdad.”  
**One-second glance:** “This is the cost Kompara subtracts from each kilometer.”

#### 4.13 Cost helper and fixed costs

**Purpose:** Help supply a missing cost without inventing it.  
**Hero:** The calculated cost or daily fixed expense.  
**Primary action:** **“Usar este costo”** or **“Guardar gastos.”**

**Running-cost helper:** Show one calculation at a time. For fuel: **“Precio por litro ÷ km por litro.”** For wear: **“Mantenimiento y llantas en ese periodo ÷ km recorridos.”** Let the driver return without guessing.

**Fixed-cost editor:** **“Renta o pago del auto”**, **“Seguro”**, payment frequency, and whether the expense applies every calendar day or only worked days where appropriate. Show the resulting deduction and its rule in plain text:

> “Descontaremos $150 cada día que registres trabajo.”

or:

> “Descontaremos $80 cada día del mes, aunque no manejes.”

Do not charge a daily cost once per platform or once per shift.

**States:** No information → “Puedes agregarlo después”; invalid or zero denominator → inline error; valid preview; save error. Never locked. Unknown is distinct from an explicit `$0`.

**Removed:** Fixed costs that have no explained relationship to the result.  
**One-second glance:** “This amount will be deducted on these days.”

#### 4.14 Tu semáforo

**Purpose:** Set the financial rule used across supported apps.  
**Hero:** A preview of the selected rule.  
**Primary action:** **“Guardar semáforo.”**

**Top to bottom:**

1. Back; **“Tu semáforo.”**
2. **“El color depende de lo que quieres ganar después de gasolina y desgaste.”**
3. **“Por kilómetro / Por hora.”**
4. Large stepper: **“Verde desde”**, value, minus/plus.
5. Secondary expandable row **“Cuándo sale rojo.”**
6. Red boundary stepper and **“Entre ambos sale amarillo.”**
7. Static preview using **“Conviene / Regular / No conviene.”**
8. **“Se usa en todas tus apps.”**
9. **“Guardar semáforo.”**

Use the same editor and values in the overlay. Changing the selected metric does not silently overwrite the other metric’s saved thresholds.

**States:** New initial values clearly labeled; existing values; edited; invalid boundary relationship; saving/error. Never locked.

Initial values are an editable starting point—not “the typical city value” unless that claim is substantiated.

**Removed:** Long introduction, sliders, four simultaneously visible thresholds, city-default authority, automatic saving on release.  
**One-second glance:** “What turns an offer green, and which unit decides.”

#### 4.15 Ajustes, Ayuda, privacy, and notifications

**Ajustes purpose:** Find occasional controls.  
**Hero:** Clear grouped navigation.  
**Primary action:** Open the chosen setting.

**Layout:**

- **“Tu manejo”** → Tus costos; Tu semáforo; Tu lector.
- **“Tu plan”** → Gratis / Premium / trial end date; Tu cuenta.
- **“Ayuda y privacidad”** → Ayuda; Tus datos; Avisos.

No Historial, referrals, or promotional reminder switches.

**Ayuda purpose:** Resolve a concrete problem.  
**Hero:** The selected question.  
**Primary action:** **“Revisar mi lector.”**

Four short expandable entries:

- **“No aparece el semáforo.”**
- **“¿Cómo calcula lo que me queda?”**
- **“¿Qué significan los colores?”**
- **“¿Qué guarda Kompara?”**

Secondary **“Ver ejemplo.”** Device-specific battery guidance appears inside the relevant recovery task, with **“Abrir ajustes de batería.”** No claim that an unverified “Ya lo configuré” proves success.

**Tus datos purpose:** Explain and control stored information.  
**Hero:** **“Tus viajes se calculan en este teléfono.”**  
**Primary action:** **“Ver datos guardados.”**

Show the distinction between transient screen processing, locally stored derived numbers, and account information. No blanket “nothing leaves” promise that ignores WhatsApp authentication or billing.

**Avisos purpose:** Manage real notifications.  
**Hero:** Reader-alert permission state.  
**Primary action:** **“Activar avisos”** when permission is missing.

Rows:

- **“Si el lector se apaga.”**
- **“Resumen del día.”**
- **“Resumen de la semana.”**

Summary notifications are optional; promotional sharing reminders are removed.

**States across these surfaces:** Available offline; device instructions unavailable → say so; denied notification permission → system-settings route; loading/error only where an account/network operation requires it; never locked.

**Removed:** Flat seven-item settings list, long FAQ paragraphs, nonexistent report button, duplicate notification asks.  
**One-second glance:** “Where the relevant setting or recovery action lives.”

#### 4.16 Tu cuenta and optional WhatsApp signup

**Purpose:** Associate Premium access with the driver, when requested.  
**Hero:** Account or verification status.  
**Primary action:** State-dependent **“Recibir código por WhatsApp,” “Confirmar código,”** or **“Guardar cambios.”**

**Phone screen:** Back; **“Activa tu prueba con WhatsApp”**; **“Te enviaremos un código de 6 dígitos. No te pedimos tarjeta.”**; `+52` number field; primary button.

**Code screen:** Back; **“Escribe el código”**; masked destination number; paste-friendly input; **“Cambiar número”** and timed **“Reenviar código”**; primary confirm.

**Account screen:** Back; **“Tu cuenta”**; WhatsApp number; plan status; **“Cerrar sesión”**; separated **“Eliminar cuenta.”**

No mandatory name, email, platform selection, or city detour. Reader state and local history remain available after logout or session expiry.

**Deletion dialog:**

> **“¿Eliminar tu cuenta?”**  
> “Se borrarán tu cuenta y los datos guardados en ella. Tu historial en este teléfono se conserva. Si tienes una suscripción en Google Play, debes cancelarla ahí.”

Actions: **“Eliminar cuenta”** and **“Cancelar.”** Show **“Administrar suscripción”** when relevant.

**States:** Signed out; sending; code expired/incorrect; resend cooldown; network error; signed in; expired session; deletion error. Errors preserve input and do not eject the driver from the local product.

**Removed:** Mandatory AUTH root and profile step.  
**One-second glance:** “Why my number is needed and whether verification worked.”

#### 4.17 Premium and Tu plan

**Purpose:** Explain a specific paid benefit and its terms.  
**Hero:** Trial duration or actual monthly price.  
**Primary action:** **“Empezar prueba gratis”** or **“Suscribirme por {precio}/mes.”**

**Top to bottom:**

1. Close.
2. Contextual title: **“Revisa todas tus semanas”**, **“Compara tu semana”**, or **“Prepara tu resumen del mes.”**
3. One accurate preview of that job.
4. Three benefits: full history, supported weekly comparisons, monthly report.
5. Actual trial/price information.
6. One primary action.
7. **“Restaurar compras.”**
8. Working **“Términos”** and **“Privacidad”** links.

**Tu plan:** Status, trial end or renewal date, price, **“Administrar suscripción”**, and restore. No sales page for an already subscribed driver.

**States:** Trial eligible; trial active; trial ended; subscribed; purchase pending; canceled purchase; restore result; unavailable Play; network error. Lack of comparison data is disclosed before this screen, not disguised as a purchase state.

**Removed:** `$200` uplift claim, placeholder legal copy, generic blur, and hidden post-payment import requirement.  
**One-second glance:** “What I receive, what it costs, and whether accepting this action creates a charge.”

The remaining surfaces—the chip, explanation, quick editor, reader interruption, first-run demonstration, and permission handoffs—are specified in full below.

### 5. The offer moment (the real product)

#### The collapsed chip

Use a **content-sized rectangle approximately 168dp wide and 96–112dp tall** at default text size, with the existing 16dp radius.

Three elements:

1. Visible verdict and a noninteractive supporting icon.
2. One 30sp rate, including its unit.
3. **“Tras gasolina y desgaste.”**

Example:

> **Conviene**  
> **$8.00/km**  
> Tras gasolina y desgaste

Use **“Conviene / Regular / No conviene”** consistently in the chip, example, history, and settings. The explanation states that these are financial judgments against the driver’s chosen rule. They do not certify route safety or tell the driver to accept.

Remove the emerald branding strip. It creates two competing green signals and consumes space. A small Kompara attribution can appear in the expanded panel.

The collapsed chip displays only the **selected deciding metric**. The second rate moves into the explanation. Do not silently switch from kilometers to hours when a field is missing.

#### Exact state contract

| State | Content | Treatment |
|---|---|---|
| Valid green | **“Conviene” / “$8.00/km” / “Tras gasolina y desgaste”** | Green; at or above the saved green boundary. |
| Valid yellow | **“Regular” / “$7.00/km” / “Tras gasolina y desgaste”** | Yellow with dark text; between boundaries. |
| Valid red | **“No conviene” / “$4.50/km” / “Tras gasolina y desgaste”** | Red; below the saved red boundary. |
| Missing fare | **“Falta el pago” / “Sin cálculo”** | Neutral slate; no financial verdict. |
| Missing required distance | **“Falta distancia” / “Sin cálculo”** | Neutral; never treat a missing pickup leg as zero. |
| Missing time, hourly metric selected | **“Falta el tiempo” / “Sin cálculo”** | Neutral; no automatic kilometer fallback. |
| Missing time, complete kilometer calculation | Valid kilometer verdict | Detail says **“No pudimos leer el tiempo.”** |
| Missing personal costs | **“Faltan tus costos” / “Sin cálculo neto”** | Neutral; tap offers cost setup for later. |
| Payment basis/commission unclear | **“Pago sin confirmar” / “Sin cálculo neto”** | Neutral; do not subtract an assumed zero commission. |
| Newly detected offer still parsing | **“Leyendo oferta…”** | Neutral, only if the delay is perceptible. Remove the previous offer’s numbers immediately. |
| Unrecognized offer layout | **“No pude leer esta oferta”** | Neutral; no repeated vibration or flashing. |
| No offer | Hidden | No persistent financial chip over navigation or unrelated apps. |
| Reader stopped | **“Lector apagado”** | Separate neutral status pill; never retain the last verdict. |

“Unknown” is a data state, not a fourth economic verdict.

These requirements change behavior as well as appearance. The current missing-input calculation and verdict rules must be corrected before the redesigned chip can be truthful.

#### Position and host-app coexistence

- Anchor to a left or right safe region, at least 8dp inside usable edges.
- Preserve a user’s chosen side and position.
- Avoid fare, offer details, accept/decline controls, host navigation, and Android gesture areas.
- Base safe placement on the observed supported layout; do not assume one coordinate works across host versions.
- If a safe expanded panel cannot fit, keep the chip compact and defer editing until the offer is gone.
- Dragging moves only Kompara’s window; release snaps to the nearest safe edge.
- A drag must not also trigger tap.
- The overlay’s touchable region must match its visible content; no invisible full-screen touch interception.
- No pulsing, countdown, per-offer beep, vibration, or celebratory animation.

At large font sizes, the surface grows. It must not shrink its critical text or obscure the host’s decision controls.

#### Tap: explain the current calculation

A tap opens a compact neutral panel anchored to the chip.

**Hero:** The estimated amount left.  
**Primary action:** **“Cerrar.”**  
**Secondary action:** **“Ajustar semáforo.”**

Example:

> **Por qué sale verde**  
> **Te quedarían $96**  
> Pago para ti: $120  
> Gasolina y desgaste: −$24  
> Distancia total: 12 km, incluida la recogida  
> Resultado: $8.00/km  
> Verde desde: $8.00/km  
>  
> “Renta y seguro se descuentan en tu resumen. Antes de impuestos.”

If the driver has selected hourly evaluation, show the offer’s pickup-plus-trip time and explain that this is **the offer’s estimated rate**, not their whole-shift earnings.

Do not show a long generic financial tutorial. Every line must explain this result.

The explanation is bound to the specific offer. If the offer disappears or changes, close it rather than silently replacing its contents while someone reads.

#### Long-press and threshold adjustment

Long-press is an optional shortcut to the same editor reached by **tap → Ajustar semáforo**. It is never the only discoverable route.

**Layout:**

1. **“Ajusta cuando estés estacionado.”**
2. **“Verde desde.”**
3. Large minus / value / plus controls.
4. Expandable **“Cuándo sale rojo.”**
5. **“Guardar.”**
6. **“Cancelar.”**

Use 48dp minimum stepper targets. Preserve existing increments and valid boundary ordering.

Changes remain drafts until Save. Saving updates the common rule across apps and shows **“Semáforo actualizado.”** Cancel leaves the previous rule intact.

The editor stays in Kompara’s overlay window; it never taps the host app or changes which offer is accepted. Do not claim a motion lock unless reliable motion detection actually exists. If a new offer arrives during editing, close the panel without applying unsaved changes and restore the compact view.

#### Reader degradation and interruption

Differentiate:

- **One unreadable offer:** neutral “No pude leer esta oferta”; no failure notification.
- **Repeated parsing failure:** reader status identifies the affected app; no repeated alert per frame.
- **Disabled/unsupported parser:** **“Por ahora no podemos leer DiDi.”**
- **Capture permission lost:** **“Lector apagado · Revisa el permiso.”**
- **Accessibility service lost:** the overlay may disappear entirely; use the actual available watchdog notification and Hoy status. Do not promise an overlay from a service that is no longer running.
- **No connectivity:** the local reader continues; lack of internet is not itself a reader failure.

One deduplicated notification per interruption:

> **“Kompara necesita atención”**  
> “No estamos leyendo las ofertas de DiDi. Revisa el lector cuando te estaciones.”

Tap opens **Tu lector**, which explains the next system step. It should not unexpectedly launch a capture prompt over a live offer.

Where Android requires an ongoing capture notification:

> **“Lector de Kompara activo”**  
> Action: **“Detener.”**

Use “Estado del lector” as the user-facing channel name.

#### After the trip and at shift end

**After each trip:** capture quietly. No earnings toast over navigation, no “good job,” no demand to rate the offer.

**At shift end:** do not require another manual start/stop ritual. Hoy exposes **“Ver mi día.”** An optional silent summary can appear after a conservative period of inactivity:

> **“Tu resumen de hoy”**  
> “Revisa tus ingresos y gastos.”

Do not display money on the lock screen by default. Do not announce that the shift ended with certainty merely because no new offer appeared.

The daily summary separates recorded trips, unconfirmed offers, and capture gaps. Expired offers never become moral judgments about “rejecting good work.”

### 6. Onboarding

**Target: four Kompara tasks, normally four app screens, plus the Android settings handoff.** A device requiring MediaProjection adds a contextual disclosure and system prompt. The interface must not claim “four taps” or hide those conditional steps inside a false progress count.

The four tasks are **Ver ejemplo → Tus costos → Permiso → Estado**.

#### Step 1 — See the value

**Single ask:** Decide whether to use this kind of assistance.

**Title:** **“Mira lo que te deja cada viaje.”**

Show one original, generic offer card labeled **“Ejemplo”**:

- Payment: `$120`.
- Total distance: `12 km`, including pickup.
- Example running costs: `$24`.
- Result: `$96`, or `$8.00/km`.

The real chip design appears beside it. Explain:

> “El color sigue el mínimo que tú eliges.”

Primary: **“Usar en mis viajes.”**  
Secondary: **“Ver otro ejemplo.”**

The secondary action cycles through the three verdicts without exposing a playground of controls. Demo changes never affect real settings.

**States:** Fully local; no account, loading dependency, or payment lock. If demo resources fail, show the worked calculation as text and retain the activation route.

**Aha:** The first screen shows why a `$120` offer does not mean `$120` left for the driver.

#### Step 2 — Make the calculation personal

**Single ask:** Supply running costs.

**Title:** **“¿Cuánto te cuesta manejar?”**

Use the reduced Tus costos editor from §4.12. Known-cost entry and the calculator are alternative routes to the same answer.

Primary: **“Guardar y continuar.”**  
Secondary: **“Lo haré después.”**

Skipping displays:

> “Podrás activar el lector. Para calcular lo que te queda, faltarán tus costos.”

No made-up personal costs. No green “net” verdict from a zero-cost default.

Fixed rent and insurance are deferred to the day-summary task. Initial threshold values are labeled **“Punto de partida; puedes cambiarlos”**, not silently attributed to CDMX.

**States:** Empty, valid, validation error, saving, skipped. No network or Premium requirement.

#### Step 3 — Prominent disclosure

**Single ask:** Consent to screen reading before opening Accessibility settings.

**Title:** **“Antes de activar el lector.”**

Suggested copy:

> **Qué lee**  
> “Kompara analiza la pantalla mientras tienes abierta una app compatible para encontrar el pago, la distancia y el tiempo de las ofertas.”
>
> **Qué guarda**  
> “Las imágenes y el texto se procesan en este teléfono. No se guardan ni se envían. Guardamos aquí solo cifras de los viajes para tus resúmenes.”
>
> **Qué hace**  
> “Calcula lo que te puede quedar y muestra el semáforo. No acepta, rechaza ni toca nada por ti.”
>
> **Tú decides**  
> “Las plataformas pueden restringir herramientas de terceros. Tú decides si usar Kompara. Puedes apagar el lector desde Hoy.”

List currently supported apps as plain text. Do not promise unsupported inDrive coverage merely because its name exists elsewhere.

Primary: **“Acepto. Activar lector.”**  
Secondary: **“Ahora no.”**

A privacy-policy link supplements this screen; it does not replace the disclosure.

Declining goes to Hoy with **“Lector apagado.”** The driver can revisit the example or settings. No claim of nonexistent manual trip entry, no punitive “limited” screen.

**States:** Not accepted, accepted, declined. A later activation attempt returns here if consent has not been given.

#### Step 4 — Grant permission and show actual state

**Single ask:** Complete the required Android permission.

**Title:** **“Activa el lector.”**

Before the system handoff:

1. **“Busca Kompara.”**
2. **“Activa el servicio.”**
3. **“Regresa aquí.”**

Primary: **“Abrir ajustes de accesibilidad.”**

Returning resumes the same screen. Once the service and applicable capture path are available:

> **“Lector preparado.”**  
> “La primera oferta confirmará la lectura.”

Primary becomes **“Ir a Hoy.”**

The first successful real host reading later changes the state to **“Lector funcionando.”** A demo proves the display and calculation, not host capture.

**States:** Waiting, permission granted, preparing, prepared, capture permission required, canceled, failed, unsupported app. Back and “Ahora no” remain possible.

#### DiDi / screen-capture consent

Follow actual device capability.

The canonical [Play-compliance document](../../docs/play-compliance.md) describes the newer screenshot path on API 30+ and MediaProjection fallback; §1 of the technical design still describes an older Uber/DiDi split. The UX must not institutionalize that documentation conflict.

When this device actually needs capture consent:

> **“Este teléfono necesita otro permiso.”**  
> “Android te pedirá permiso para capturar la pantalla mientras lees ofertas. Kompara analiza la imagen aquí y no la guarda ni la envía. Puedes detenerlo cuando quieras.”

Primary: **“Continuar.”**  
Secondary: **“Ahora no.”**

Then show the genuine Android prompt. Explain if the permission must be granted again after interruption. If the same capture path serves several apps, do not label it “only DiDi.”

On the path that needs no additional prompt, show the resulting readiness state immediately. No button that appears to do nothing.

#### Notifications and battery setup

Ask for notifications once, after the reader has been prepared:

> **“¿Te avisamos si el lector se apaga?”**

“Activar avisos” requests the real system permission. “Después” is valid.

Move battery instructions into contextual reader help. Where a known device restriction warrants an early suggestion, present **“Evita interrupciones”** as optional. Do not make every driver traverse three unverifiable OEM instructions.

#### Where signup sits

Signup follows an explicit choice to activate the Premium trial or link an account. It is absent from the free-reader setup.

Compared with the supplied GigU **phone → WhatsApp code → name** flow, Kompara should use **phone → WhatsApp code**, omitting name. City belongs in the comparison filter, where its effect is understandable.

The improvement is not merely one fewer field: **the driver sees and can use the local product before deciding to create an account.**

### 7. Monetization & paywall placement

**Proposed commercial change:** replace the current mixed entitlement/verification gates with one Premium subscription and a separate, explicitly activated **14-day no-card trial**.

#### Free should feel complete

Free includes:

- Reader and all supported live verdicts.
- Explanation, costs, thresholds, and recovery.
- Demo and help.
- Today and the current plus previous **calendar week**.
- Simple sharing of the current free period.
- Clear access to the driver’s data controls.

Define the history window by calendar dates. “Two most recent weeks with data” is harder to predict.

Premium includes:

- Full retained history and drill-ins.
- Personal cross-app weekly comparisons.
- Valid weekly city benchmarks where available.
- Monthly report and PDF preparation.

The live reader, missing-data explanations, and safety-related recovery never contain an upgrade prompt.

#### Where the lock sits

| Intent | Placement | Copy |
|---|---|---|
| Open an older week | On the selected old period | **“Revisa todas tus semanas.”** |
| Compare own platforms | After data availability is established | **“Compara lo que te dejó cada app.”** |
| Compare with city | After a valid cohort and matching personal metric exist | **“Compara tu semana con conductores de tu ciudad.”** |
| Prepare monthly PDF | Before report generation | **“Prepara tu resumen del mes.”** |

No locks beneath every free metric. No paywall on launch, reader activation, cost setup, or post-trip interruption.

#### Tease pattern

Use a readable preview of the task, not a blurred spreadsheet.

For an old week, show its date and explain access. For comparisons, show which apps/week have usable data. If using fictional values to explain the layout, mark the entire card **“Ejemplo”** and keep it separate from personal results.

**Do not charge for absence.** If city comparisons are unavailable, say so before purchase. Premium cannot make them exist.

Remove **“Importa una semana para desbloquear.”** Data eligibility and payment entitlement are different states. The current remote import flow is removed, so it cannot remain a hidden prerequisite.

#### Trial framing

The trial is an app-granted Premium entitlement, associated with the optional WhatsApp account. It does not initiate an automatically renewing Play subscription.

Copy:

> **“Prueba Premium 14 días.”**  
> “Sin tarjeta. No habrá ningún cobro al terminar.”  
> “Usaremos tu WhatsApp para activar la prueba.”

Primary: **“Empezar prueba gratis.”**

Start the trial when the driver explicitly chooses it and at least one useful paid result is available. Do not consume it during installation, permissions, or an empty week.

Show a date in Tu plan:

> **“Tu prueba termina el 28 de junio.”**

No running countdown, daily modal, or pressure on the driving screen.

After expiry:

> **“Terminó tu prueba. Tu lector sigue gratis.”**

#### Price surface

Use **$79 MXN/month as the proposed price hypothesis**, within the brief’s range. The purchase UI must display the actual localized Google Play price.

Separate purchase copy:

> **“Premium · {precio} al mes.”**  
> “Se renueva cada mes hasta que canceles en Google Play.”

Primary: **“Suscribirme por {precio}/mes.”**

This action opens Play purchase. Canceling returns to the requested free screen with its state preserved.

The commercial risk remains: if personal records are incomplete and real city benchmarks are sparse, history and reports may not justify the subscription. A more persuasive paywall does not solve that problem.

### 8. Scorecard

Scores are expert assessments of the documented current experience and the proposed design—not usability-study results.

| Dimension | Today | Proposal |
|---|---|---|
| **First-use clarity** | **3/10.** Value follows registration and multiple permission tasks, then readiness is overstated. | **8/10.** The worked example comes first, but cost knowledge and Android permission handoffs remain substantial friction. |
| **Glanceability** | **4/10.** The chip has large figures, but color-only meaning, two competing rates, and dense roots weaken comprehension. | **9/10.** One rate and a visible verdict dominate, with unknown states separated; mounted-phone testing is still required. |
| **IA simplicity** | **3/10.** Five equal tabs bury history and promote configuration into a destination. | **8/10.** Three task-based roots and a week-to-day timeline are clearer, though Comparar’s two scopes still need testing. |
| **Copy quality** | **3/10.** Jargon, unsupported gains, blame, and incorrect promises undermine otherwise natural Spanish. | **8/10.** Copy names the result, its scope, and the next action, but “Regular” and expense terminology require driver validation. |
| **Consistency** | **4/10.** Dates, verdict language, navigation, preview/export behavior, and metric treatment drift. | **9/10.** Shared period, state, calculation, and verdict patterns remove most inconsistency if enforced in implementation. |
| **Trust/transparency** | **2/10.** Net-income ambiguity, synthetic-looking authority, false readiness, and coverage assertions are foundational weaknesses. | **8/10.** Scope and uncertainty become visible, but capture reliability, lifecycle inference, and benchmark quality still limit trust. |
| **Delight** | **4/10.** Streaks and sharing add decoration while ordinary tasks remain awkward. | **7/10.** The intended delight is a quiet correct answer and an understandable day’s result; this is intentionally not an entertainment product. |
| **“Feels like Uber-class”** | **3/10.** It feels like a collection of feature screens rather than a disciplined operational tool. | **8/10.** The hierarchy and state model approach that bar, but real-device reliability, recovery, latency, and interaction polish must earn the remaining points. |

A Compose implementation that reproduces these layouts but keeps the current uncertain calculations and false readiness states would not earn the proposal’s scores.

### 9. Open questions

| Test with real drivers | What to observe | Default answer before testing |
|---|---|---|
| **1. Can the chip communicate the correct judgment in one second at 60cm?** Compare one rate against two, including yellow, negative earnings, neutral missing-data states, night conditions, and larger text. | Ask drivers to identify the judgment and unit after a brief exposure; specifically check whether unknown is mistaken for bad. Use parked or simulated sessions. | **One rate, visible “Conviene / Regular / No conviene,” and neutral unknown.** Add a second collapsed rate only if it improves decisions without increasing confusion or occlusion. |
| **2. Can a new driver reach a useful result without assistance?** Test the example, cost entry, Accessibility handoff, capture fallback, and return state on common low-cost phones. | Time to first understood example and first valid real reading; abandonment points; whether “prepared” is understood as different from “working.” | **Four first-run tasks, no signup, optional battery guidance.** Never trade a truthful no-cost state for fabricated net earnings. |
| **3. Do drivers understand what “te quedan” excludes?** Give an offer and a daily summary with gasoline, wear, rent, insurance, missing commission, and incomplete capture. | Ask them to explain the amount in their own words and predict which expense changes the chip versus the day. | **Offer contribution after running costs; period result after configured costs; explicit “antes de impuestos.”** Keep the scopes visibly different. |
| **4. Can drivers find yesterday, last week, and the reason for one verdict?** Test three-tab navigation without prompting. | Wrong turns, reliance on Settings, understanding of “Tus apps” versus “Tu ciudad,” and ability to return to the same period. | **Hoy / Ganancias / Comparar**, with reader status on Hoy and week → day → activity drill-down. |
| **5. Is Premium valuable with the data Kompara can actually produce?** Show real personal history, honest comparison availability, and the proposed $79 price with the separate trial. | Whether drivers can name a recurring benefit; whether they understand no automatic trial charge; whether they still want Premium when city data are unavailable. | **14-day no-card trial, then an explicit subscription.** Sell available history and comparison value; withhold unsupported city claims and keep the reader complete and free. |