# Kompara — Round 9: 67/80 — NOT_YET

## 1. Scorecard

| Criterion | Score (1–10) | Justification |
|---|---:|---|
| One job per screen | 9 | B1, E1 and F1 have clear principal answers; recovery states preserve that hierarchy. |
| One primary action | 9 | B2 “Encender lector”, H5e “Reintentar” and H2c’s payment repair each provide one appropriate primary. |
| Navigation that disappears | 9 | Three coherent tabs, shared periods and Cuenta behind the avatar; F4 now uses selector pills. |
| Numbers discipline | 7 | Corrected amounts reconcile, but E1/E3 still expose excessive numerical detail, and E1/E7b’s charts contradict their daily records. |
| Copy | 8 | H2/H2b now explain immediate billing honestly; H5b still assigns missing trips to dates its source cannot establish. |
| States are designed | 9 | E7b, H2d, H3 and H5e now show the promised offline, pending-access, privacy and upload-failure states. |
| Time-to-value | 8 | A1 demonstrates value before permission or identity; A3 anticipates “control total”; device timing remains unmeasured. |
| Consistency & feel | 8 | Default chips, selectors and green tokens are repaired; some touch targets, chip extremes and governing instructions remain inconsistent. |

**Total: 67/80 — NOT_YET.** Numbers discipline remains below 8.

Reviewed all **61 frames**, the specification, CSS, fixture, checker and both Round 8 reviews. The requested command passes: **`checked 95 figures in 33 frames, 23 spec strings, 1 date rule, 42 stale patterns → OK`**.

Independent arithmetic confirms October **$12,169.280595 → $12,169**, separately rounded rows **$12,170**, cohort mean **162,440 ÷ 1,240 = $131**, full-precision percentile **77.647% → 78%**, and H5b’s known costs **$427.955 → $428**. Browser rendering was unavailable; geometry findings below use the supplied CSS and repository Inter font, not screenshots or mounted-device testing.

## 2. Hard fails

**None established from the supplied design.**

- **Auto-action: none.** A2 says “Kompara solo lee: nunca acepta ni rechaza por ti”; §6 specifies drag only.
- **Platform logos: none.** Platform names are text; C1–C6 use a generic host.
- **Driving-time surface demonstrably failing the one-second glance: none established.** C1–C5’s displayed examples fit their text column; valid extremes still need correction and timed testing.
- **Paywall blanking the free product: none.** E2 retains the total; F3 retains the personal figure and standing; F4/F5 request no purchase for missing data.
- **Onboarding with multiple asks: none.** A1–A7 separate decisions; A3’s Android dialog is an illustration.

## 3. Round 8 defects: status

| Round 8 defect id | Status | Evidence |
|---|---|---|
| R8-01 | partly | `width:200px` and `15px/600` repair C1–C5’s displayed examples; §6’s required negative rate still exceeds the 156px text column. |
| R8-02 | fixed | F2 shows “Promedio… $131/viaje”; `sum_driver_per_trip:162440` and `drivers:1240` derive the arithmetic mean. |
| R8-03 | partly | E1’s card now says “Tu lugar entre los de Uber en CDMX”; F4 removes counts. E1/E3’s numerical breakdown rows still fit inside the first viewport. |
| R8-04 | fixed | H3’s enabled “Ocultar montos y horas” now replaces hours with “Semana · confirmada con mi resumen”. |
| R8-05 | fixed | H5/H5d/E3b consistently show 14–20 September, `$5,340` and `29 viajes`; H5/H5d agree on `26 h`. |
| R8-06 | fixed | H5b and its fixture now reference 28 September–4 October: `14 · 20` trips, `150.6` km and `−$428`. The stale specification example is listed separately below. |
| R8-07 | fixed | E3 shows `$12,169`, derived by summing unrounded amounts once. |
| R8-08 | fixed | §6’s missing-time row now specifies “sin semáforo · ve el detalle al estacionarte”, without a rate. |
| R8-09 | partly | Assertions now inspect named frames and exclude captions/fixture prose; daily-chart geometry and B4’s contradictory Sunday narrative remain unchecked. |
| R8-10 | partly | F4 selectors and H5 “Corregir” have 48px treatments; B1/B3/B6 retain inline actions without those bounds. |
| R8-11 | fixed | E7b actually shows “Sin conexión”, the local-data explanation and “Reintentar”. |
| R8-12 | fixed | H2d says “Tu acceso actual sigue igual (Premium hasta el 9 de noviembre)”. |
| R8-13 | partly | H5e now draws the retained-file failure state; the mixed reader/import month remains prose, explicitly declined in §9f. |
| R8-14 | partly | §5.1 now prescribes colour/outcome/time; §5.4 still lists week confirmation as an offer outcome, and §5.9 still prescribes E1’s removed `$5,062`. |
| R8-15 | fixed | E6 removes locks from “Sin registros del lector” and the imported week without daily detail. |
| R8-16 | fixed | E3b says “sin kilómetros no podemos calcular cuánto restar de gasolina y desgaste”. |
| R8-17 | fixed | F2/F3/F4 now include “Solo cuentan tus semanas confirmadas con resumen”. |
| R8-18 | fixed | G8 says “resuelve el permiso o la restricción de batería que te indique”. |
| R8-19 | partly | E4 removes the unverified DOF citation; E5’s precise configured source destination and explainer remain unshown behind the legal gate. |
| R8-20 | fixed | E2 is dated “lunes 2 nov” and labelled “hace 4 semanas”. |
| R8-21 | partly | G7’s switch count and the appendix are repaired; H2b’s caption and §9f still contradict billing/navigation. |
| R8-22 | partly | Frame labels use “aprox.”; active §4/§5.5 instructions retain “Estimado de”, and the opening fixture paragraph retains “est.” |

Additional Opus fixes also hold: E3b says “Este pago está en tu mes” and names the omitted `$643`; E5 removes duplicate headlines and shares the tip note; H2 says “Suscribirme ahora”; H2b offers “Mejor avísame el 9 de noviembre”; H1 says “le compartes”; tokens, README and design principles agree on **#187a36**.

## 4. Remaining defects (ranked by damage)

| Id | Screen id or spec § | Severity | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R9-01 · remains | E1/E3/E8; §4 | high | The root-screen numerical budget is still exceeded. | E1 exposes `$4,980`, `62`, `−$1,500`, then Friday’s `14 viajes / $1,380`; E3 exposes its hero/count plus weekly amounts/counts. CSS-derived positions place E3’s first weekly row around y583 and E1’s Friday row around y680, above navigation at y780. E8 repeats `$214` and adds `−$1,500` inside the deduction row. | Put day/week numerical breakdowns behind a detail action or collapsed section; keep E8’s actual deduction once and move its allocation explanation into calculation detail. |
| R9-02 · newly exposed | E1/E7b/B4; fixture | medium | The new daily fixture contradicts both the charts and the previous-day story. | Chart heights are `64/74/68/81/100/83/0%`; fixture-derived proportions are approximately `59/69/66/75/100/65/36%`. Sunday contains `4 trips / $600 / 35.7 km`, while B4 says “El domingo no registramos viajes · última lectura el sábado 18:00”. | Generate charts and readiness history from the same dated records; retain one truthful Sunday and an evidenced last-read timestamp. |
| R9-03 · partly repaired | C1–C5; §6; CSS | medium | The fixed chip still fails valid values beyond the displayed examples. | The text column is `200−14−30 = 156px`; the second line now measures about 148px, but glyph plus `−$1.20/km` or `$12.00/km` needs about 159px, and `−$12.00/km` about 177px. “Pago sin confirmar” also exceeds the column. | Define width growth/reflow for supported values and font scales; keep glyph, deciding amount and unit together; draw negative, two-digit and longer neutral states. |
| R9-04 · newly exposed | H5b; §5.11 | medium | Reconciliation assigns the six unobserved trips to dates an aggregate statement cannot establish. | “faltan los km de 6 viajes **(28 – 30 sep, antes de instalar Kompara)**”; the records establish only 20 statement trips versus 14 reader trips, without dated statement trips. | Say “Faltan los km de 6 viajes. El lector está activo desde el 1 de octubre.” Keep the net withheld. |
| R9-05 · residual drift | §5.11 Rules | medium | The active reconciliation example combines new costs with obsolete coverage. | “**−$428 (404 km de 31 viajes)**”; 404km costs approximately `$1,148`, and 31 plus six does not equal the statement’s 20 trips. | Replace this example with H5b’s `150.6 km de 14 viajes`, consistently throughout the section. |
| R9-06 · remains | §5.4 versus §4 | medium | Week confirmation is still prescribed as an individual offer outcome. | §5.4 lists `outcome "La tomaste · aprox." / "Semana confirmada con tu resumen" / "Se fue sin tomar"`; §4 explicitly makes confirmation week-level. | Keep the offer outcome unchanged and describe week confirmation as separate context. |
| R9-07 · remains | §§5.7/5.9 | medium | Active screen instructions still prescribe removed figures and controls. | §5.7 retains “158 viajes tomados · **106 h en turno**” and says rounded rows “**sum to the hero**”; §5.9 retains E1’s `$5,062` and selectors “**as tappable words inside the sentence**”. | Remove the obsolete hours/source figure and inline-selector instruction; preserve the explicit one-peso rounding difference. |
| R9-08 · remains | B1/B3/B6; scope actions | medium | Several navigation actions still lack the shared 48dp target treatment. | B1’s “Cómo se calculó” lacks `.scopeline`; B1/B6 “Ver tu día ›” are plain text anchors; B3 “Elegir mi ciudad” is a span in 13px prose. | Use the existing full-row scope control and ≥48dp buttons/rows consistently. |
| R9-09 · remains | §5.7; E3b; §9f | medium | The mixed month’s incomplete net remains unreviewable as a screen. | E3b contains only an import; the mixed-month hero “Te quedan… $X · sin 14 – 20 sep (sin km)” exists only in prose. | Add one explicitly labelled alternate fixture/frame containing dated reader earnings plus a contained import without km, showing payment inclusion and net exclusion. |
| R9-10 · new wording | H5e/H5c | low | The upload flow makes an ambiguous persistence promise. | H5e allows “el servidor no respondió” but says “**Nada se guarda hasta que revises lo leído**”; H5c uses the same unqualified promise. | Specify “No añadimos esta semana a Ganancias hasta que revises y guardes”; do not imply that an uploaded file was never stored. |
| R9-11 · remains | H2b caption; §9f | low | Reference annotations still reverse the final billing and navigation rules. | H2b’s caption says charging starts “**after the free access, never before**”, while the screen says “Google Play cobra hoy”; §9f calls E4/E5 “**no bottom bar**”, although both retain it. | Correct both annotations to the implemented design contract. |
| R9-12 · remains | §4/§5.5; opening fixture | low | The single approximation vocabulary is not consistently specified. | “**Estimado de viajes tomados**”, “**Estimado de 9 viajes**”, `$5,028 est.` and `$3,372 est.` remain. | Reuse the current frame wording and “aprox.” in active instructions. |
| R9-13 · remains | §12; fixture/checker | low | Coverage claims still exceed the supplied fixture and checks. | §12 says “**every displayed figure (47 today)**” and “**22 known stale patterns**”; B6 explicitly says “not in the fixture” despite showing `$380`, and G9’s `$7.70/km` lacks a fixture input. | Document the actual 95 assertions/33 frames and exclusions; add inputs for displayed financial examples and check chart/narrative consistency. |
| R9-14 · remains, gated | E5; §5.8 | low | The post-gate legal reference and explainer are not yet reviewable destinations. | “CONASAMI, tabla de salarios mínimos 2026 (**conasami.gob.mx**)”; “¿Cómo funciona?” has no supplied sheet. | Supply the exact verified configured destinations and explainer before enabling E5; retain E4 meanwhile. |
| R9-15 · new wording | E4 | low | The launch screen exposes an unfinished release task to drivers. | “**la regla oficial se enlaza cuando esté confirmada**”. | Remove that release-status sentence and retain the official IMSS route. |
| R9-16 · incomplete presentation | E3; D3 | low | The promised month-specific rounding explanation is specified but not shown. | E3’s caption says “Cómo se calculó names that peso”; the supplied D3 is “Cómo se calculó · **hoy**” and contains only the general rounding rule. | Draw the month variant with the `$12,169` total, `$12,170` rounded-row sum and a short explanation. |
| R9-17 · new | H5e; §4 | low | Upload failure reuses the profit-verdict badge. | H5e uses `<div class="vd y">!</div>`; §4 reserves verdict colours for verdicts. | Use the existing amber attention treatment. |

**Smallest set needed to reach the bar:** enforce the first-viewport budget on E1/E3/E8; regenerate E1/E7b/B4 from one truthful daily fixture; correct the contradictory reconciliation, rounding and offer-confirmation instructions. These address the remaining numerical-discipline deficit; the other residuals remain explicitly tracked above.

## 5. What a driver on a windshield mount gets from each tab root in one second

**Hoy — B1:** “Está leyendo; hoy llevo unos $762.”  
**Ganancias — E1:** “La semana pasada me quedaron unos $4,980, ya con lo del coche.”  
**Comparar — F1:** “En mis viajes de esa semana, DiDi me dejó $1.21 más por kilómetro.”

**Verde — C1:** “Llega a mi mínimo: unos $9.49 por kilómetro.”  
**Amarillo — C2:** “Está entre mi verde y mi rojo: $7.16 por kilómetro.”  
**Rojo — C3:** “Está abajo de mi rojo: $2.20 por kilómetro.”  
**Gris — C4/C5:** “Falta información; todavía no tengo semáforo para este viaje.”

## 6. What only evidence can settle

- **One-second comprehension:** timed exposures at 60cm, day/night and with colour-vision differences; include negative values, long neutral states and enlarged fonts.
- **Value of the second rate:** compare one-rate and two-rate chips on decision accuracy and response time.
- **Reader reliability:** replay supported host layouts and full shifts, measuring latency, stale values, covered controls, permission loss and pause/resume.
- **Money comprehension:** ask drivers to explain B1/E1/E3/F2, including approximate offer income, statement payments, costs and incomplete periods.
- **Install-to-value:** time fresh stock/OEM installs through “preparado”, including settings return and skipped signup.
- **Typical-cost safety:** measure verdict changes when drivers replace defaults with actual costs.
- **Accounting correctness:** replay cancellations, duplicate imports, corrections and month boundaries against exact dated-ledger expectations.
- **Billing continuity:** test pending purchases, grace periods, cancellation and offline access with unused promotional days.
- **Legal and disclosure validity:** validate B-038’s sources and calculations, accessibility disclosures and actual data flows before enabling their dependent claims.
- **Weekly-import and Premium demand:** observe repeated successful imports and willingness to pay for the comparison actually available.
- **Native usability:** verify Compose target bounds, focus order, font scaling and tab findability on representative phones.

## 7. Verdict

This version does **not yet** meet “comparable in use and feel to the Uber Driver app” for its job: **67/80 — NOT_YET**, with substantial Round 8 repairs but a remaining numerical-discipline failure.  
The single biggest residual risk is that a polished, checker-approved financial presentation still disagrees with its dated records and reveals more numerical detail than its glance contract allows.  
Build the capture → readiness → chip → dated-ledger slice first, using the corrected fixture and accounting rules, then validate it on mounted phones before expanding the paid surfaces.