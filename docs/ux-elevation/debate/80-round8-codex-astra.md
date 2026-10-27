# Kompara — Round 8: 65/80 — NOT_YET

## 1. Scorecard

| Criterion | Score (1–10) | Justification |
|---|---:|---|
| One job per screen | 9 | B1, E1 and F1 retain unmistakable principal answers; supporting content remains subordinate. |
| One primary action | 9 | G4, H2c and H5c give the problem one appropriate primary action; healthy roots need none. |
| Navigation that disappears | 9 | Three coherent tabs, shared periods and settings behind Cuenta; F4’s selectors remain inconsistent. |
| Numbers discipline | 7 | E5 is corrected, but E3 rounds incorrectly, imports disagree across screens, and E1/E3 still exceed the numerical budget. |
| Copy | 8 | B4 and E8 now state the evidence honestly; G8’s recovery instructions and several governing strings still disagree with the UI. |
| States are designed | 8 | H5d, H2d and E3b add concrete states; E7b does not show its promised offline state, and H3 contradicts its privacy switch. |
| Time-to-value | 8 | A1 demonstrates value immediately; A3 anticipates “control total”; account creation remains optional. |
| Consistency & feel | 7 | Green contrast and most named controls are repaired, but fixed-width chips exceed their text budget, F4 retains inline selectors, and specification conflicts remain. |

**Total: 65/80 — NOT_YET.** Numbers discipline and consistency remain below 8.

Reviewed the specification, all **60 frames**, CSS, fixture, checker, rubric and both Round 7 reviews. The requested command passes: **`checked 47 derived figures, 22 stale patterns → OK`**. Independent calculation nevertheless finds **October $12,169**, inconsistent import records and an unsupported mean; the checker’s global string-presence assertions miss these.

No fresh browser rendering or mounted-device test was completed: browser access was unavailable. Chip sizing findings below use the actual CSS and measurements of the repository’s Inter font.

## 2. Hard fails

- **Auto-action proposed: none.** A2: “Kompara solo lee: nunca acepta ni rechaza por ti”; §6 permits dragging only.
- **Platform logos: none.** Platforms appear as text; C1–C6 use an explicitly generic host.
- **Driving-time surface demonstrably failing the one-second glance: none established.** C1–C5 preserve distinct verdict cues, but the new wrapping defect requires correction and timed validation.
- **Paywall blanking the free product: none.** E2 preserves the total; F3 preserves the personal figure and standing; F4/F5 do not demand payment for missing data.
- **Onboarding screen with more than one ask: none.** A1–A7 separate decisions; alternatives answer the same question.

## 3. Round 7 defects: status

| Round 7 defect id | Status | Evidence |
|---|---|---|
| R7-01 | fixed | §4: “an aggregate statement never cancels a trip”; D2 retains cancellation and undo. |
| R7-02 | partly | B1 removes offer rates and F1 removes provenance counts; E1 still contains `$4,980`, `62 viajes`, `−$1,500` and `78 %`. |
| R7-03 | partly | Status pills and segmented options now reach 48px; F4 still uses `<u>DiDi</u>` and `<u>CDMX</u>`. |
| R7-04 | fixed | `--verde:#187a36` replaces the insufficient-contrast green. |
| R7-05 | fixed | E5 now shows `$11,852`, `$7,926` and “faltan $1,656.” |
| R7-06 | fixed | H5: “Este pago sí cuenta en septiembre, sin repartirse por día.” |
| R7-07 | partly | H5d, H2d and E3b exist; upload/file failures and the mixed reader/import month remain descriptions, and the correction example changes periods. |
| R7-08 | fixed | B4: “El domingo no registramos viajes.” |
| R7-09 | fixed | E8: “Al cerrar la semana, confírmala con tu resumen para ver tu lugar.” |
| R7-10 | fixed | E1 restores “Antes de gastos · según tu resumen de Uber.” |
| R7-11 | fixed | F5 removes the unsupported previous-week cohort count. |
| R7-12 | fixed | H2c now has the bottom primary “Arreglar el pago en Google Play.” |
| R7-13 | fixed | §5.13 says “confirmados el 19 oct” and separately identifies the earlier unconfirmed state. |
| R7-14 | fixed | §5.1 removes B1’s old hours and distinguishes Monday B4 from Tuesday B2. |
| R7-15 | fixed | §5.9 consistently orders “Por km · Por viaje · Por hora.” |
| R7-16 | fixed | §7 and the HTML legend define gris as insufficient information, rather than only failed reading. |
| R7-17 | partly | G6 adds “solo cuentan tus semanas confirmadas con resumen”; F2/F3’s opt-ins still omit that qualification. |
| R7-18 | fixed | C3’s caption now calculates `$27.05` using `$2.8417`. |
| R7-19 | partly | E5 names “CONASAMI, tabla de salarios mínimos 2026,” but supplies only the domain; the precise supporting destination and explainer remain unshown. |
| R7-20 | partly | A fixture and executable checker now exist; they do not establish frame-specific equality, the advertised mean or all dated observations. |
| R7-21 | fixed | H3’s caption now quotes “Por viaje, Uber me pagó más…” consistently with the card. |
| R7-22 | partly | G8 removes the all-checkmarks instruction but says to resolve what appears “en rojo”; G4’s battery restriction is amber. |

## 4. Remaining defects (ranked by damage)

| Id | Screen id or spec § | Severity | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R8-01 · new | C1–C5; `base.css` | high | The new fixed width breaks the intended chip anatomy at the specified typography. | `.chip{width:184px}` with body padding `14px` left and `30px` right leaves **140px**; “neto aprox. · $285/h” measures about **159px** in repository Inter at 16px/700, and the C1 first row also exceeds 140px. | Give the chip a shared width that fits its measured contents, or rearrange the rows without reducing legibility; reserve the K slot explicitly and verify every state, negative amounts and enlarged text. |
| R8-02 · newly exposed | F2/H1; fixture/checker | high | The fixture validates the paid “Promedio” using a median. | Checker: `('cohort median', pesos(dec['50']), False)`; F2: “Promedio de Uber en CDMX · $126/viaje.” Only deciles are supplied. | Provide observations or sufficient statistics that derive the mean; define and test the percentile estimator separately. The interpolated 78% is reproducible, but the mean is not established. |
| R8-03 · remains | E1/E3/F4; §4 | high | The root-screen budget is still exceeded despite the new rule. | E1: `$4,980`, `62`, `−$1,500`, `78 %`; E3 adds trips, hours and weekly amounts; F4 adds `28/29 viajes` to its two rates and percentage difference. | Retain the answer plus two supporting measures: move E1’s extra count, E3’s additional breakdown measures and F4’s trip counts into their existing detail routes. Preserve the timeline and comparison entry points. |
| R8-04 · new | H3 | medium | The default share preview exposes hours that its enabled privacy control promises to hide. | “Ocultar montos y horas” uses the ON `.sw`, while the exported preview shows “Horas en línea · **24 h**”; its subtitle says those hours do not appear. | Remove or mask hours in the default preview and export; demonstrate the same renderer with the switch off. |
| R8-05 · new | H5 → H5d → E3b | medium | The purported import journey changes its record between review, correction and the resulting month. | H5: `14–20 sep · $5,340 · 36 viajes`; E3b and fixture: same period/payment, **29 viajes**; H5d edits **12–18 oct · $5,062 · 34 viajes**. | Generate the three frames from one import record, or explicitly identify separate scenarios instead of presenting H5d as H5’s continuation. |
| R8-06 · newly exposed | H5b; fixture | medium | Reconciliation does not match its underlying week. | H5b: `5–11 oct · 31 viajes · 404 km`; fixture for that week: **405 km**; `h5b_partial_import.uber_week` points to **12 October**. | Bind the reconciliation to the correct week and reader coverage; regenerate costs. At 405km, known costs round to **$1,151**, not $1,148. |
| R8-07 · newly exposed | E3; §5.7; checker | medium | October’s hero violates the full-precision accounting rule. | Raw inputs produce **$12,169.280595 → $12,169**; E3 shows `$12,170`; the checker uses `pesos(sum(r(x) for x in oct_rows))`. | Sum unrounded amounts and round the aggregate once; retain correctly rounded rows and explain a possible rounding difference. |
| R8-08 · new drift | §6 versus C5/§4 | medium | The governing state table still orders a peso rate on a grey chip. | “Hour selected, time missing” specifies `$9.49/km neto aprox.` plus “sin semáforo”; C5 and §4 explicitly prohibit that rate. | Replace the table row with C5’s no-rate state and keep the computable rate exclusively in detail. |
| R8-09 · remains | Fixture/checker; B1/E1/H5 | medium | Passing the checker does not establish the claimed correspondence between displayed figures and raw records. | Assertions use `sv in idx`, including headers/captions; no dated inputs support “Tu mejor hora hoy: 9 a 10 h,” Friday’s `14 viajes/$1,380`, or H5’s `26 h/24 %`. The `$600\b` stale literal contains a backspace. | Assert identified screen fields against period/source records; supply the missing inputs, test conflicting occurrences and repair the stale expression. Describe the checker’s coverage accurately. |
| R8-10 · remains | F4; inline actions; §5.9 | medium | The 48dp contract is not consistently represented in the markup. | F4 retains underlined selectors; H5’s “Corregir” and several “Cómo se calculó” actions remain inline spans without defined control boxes. | Reuse the 48dp selector pills on F4 and define full-row or explicit ≥48dp targets for inline actions; synchronize §5.9. |
| R8-11 · new | E7b | medium | The added offline frame does not contain the state its caption claims. | Actual city card repeats E1’s `78 %` standing; no “Sin conexión” or retry appears, although the caption promises both. | Show the unavailable-city card and retry, or explicitly show a dated cached result and specify that alternative consistently. |
| R8-12 · new | H2 → H2b → H2d | medium | Pending-payment copy does not preserve the active promotional entitlement demonstrated immediately before purchase. | H2 has Premium through 9 November; H2b banks 21 unused days; H2d says “Premium se activa solo cuando termine” and describes only free access while waiting. | Show the pending variant with existing trial/Premium access retained until its actual expiry; reserve activation-pending copy for drivers without existing access. |
| R8-13 · remains | H5c; §5.7 | medium | Two consequential recovery/accounting cases remain unreviewable. | Failed upload/unreadable file exist only as H5c caption variants; E3b covers an import-only month, while the mixed month excluding an import’s unavailable net remains prose. | Add the retained-input failure layouts and one mixed-month example showing payment inclusion, withheld net and the exclusion beside the hero. |
| R8-14 · remains/new drift | §§4/5.1/5.4/5.9 | medium | Several active instructions still prescribe the superseded product. | §4: B1 “hero + 3 offer rates”; §5.1 still specifies `$9.49/km neto` rows; §5.4 lists “Semana confirmada con tu resumen” as an offer outcome; §5.9 retains E1’s removed `$5,062` source line. | Make these instructions match B1, E1 and the week-level confirmation model; validate governing strings as well as numbers. |
| R8-15 | E6 | low | Locks imply purchasable detail where the screen says none exists. | Lock icons accompany “Sin registros del lector” and the imported “14–20 sep · sin fechas por día”; §4 says “no lock on absent data.” | Remove those locks; use them only where a real paid breakdown exists, with “total siempre visible.” |
| R8-16 · new | E3b | low | The explanation confuses unavailable expense calculation with absent expenses. | “Ese resumen no trae km, así que **no hay gasolina ni desgaste que restar**.” | Say “Sin kilómetros no podemos calcular cuánto restar de gasolina y desgaste.” |
| R8-17 · remains | F2/F3 versus G6 | low | Contribution eligibility is still explained inconsistently. | F2/F3 show only “Sumar mis totales, sin mi nombre, al promedio de CDMX”; G6 adds the confirmed-statement requirement. | Reuse the eligibility sentence beside every opt-in or in its explicit confirmation. |
| R8-18 · remains | G8 versus G4 | low | Help points to the wrong visual signal for battery recovery. | G8: “resuelve lo que aparezca en rojo”; G4: amber `!` beside “Batería: restringida.” | Say “Resuelve el permiso o la restricción de batería que te indique.” |
| R8-19 · remains | E4/E5; §5.8 | low | The dated minimum still lacks a precise reviewable source destination. | “CONASAMI, tabla de salarios mínimos 2026 (conasami.gob.mx)” and an unshown “¿Cómo funciona?” explainer. | Supply the exact dated official destination in the configured citations and show the explainer before E5 ships; retain E4 until validation. |
| R8-20 · new | E2; §5.6 | low | The stated first expiry date is wrong. | The caption calls **9 November** the first Monday when 5–11 October leaves the current-plus-three-prior-week window; it leaves on **2 November**. | Keep the valid locked view on 9 November without calling it the first, or date it 2 November and update the relative label. |
| R8-21 · new drift | H2b; G7; §9f; appendix | low | Reference annotations still contradict the final frames. | H2b’s caption says charging starts “after the free access”; G7 says four switches but shows five; §9f claims E4/E5/F screens lack the bottom bar; the appendix omits H5d. | Correct these annotations against the actual frames and current billing rule. |
| R8-22 · remains | B1/B5/B6/D1/D3/H5b; §4 | low | The promised single approximation vocabulary was not applied consistently. | “Estimado de viajes tomados,” “Estimado de 9 viajes” and `$5,000 est.` remain alongside “aprox.” | Standardize approximation labels while retaining the explanation that these are offer-based figures. |

**Smallest finishing set needed to reach the bar:**

1. Repair the fixture’s aggregation, cohort statistic and import identities; make checks specific to screens and records.
2. Enforce the numerical budget on the remaining roots.
3. Repair chip sizing, remaining touch targets and contradictory governing instructions.
4. Make the share, offline, pending-entitlement, import-failure and mixed-month states internally consistent and reviewable.

These address the missing points in numbers, consistency and states; fixing captions alone would not.

## 5. What a driver on a windshield mount gets from each tab root in one second

**Hoy — B1:** “Está leyendo; llevo unos $762 hoy.”  
**Ganancias — E1:** “La semana pasada me quedaron como $4,980, ya con lo del coche.”  
**Comparar — F1:** “En mis viajes de esa semana, DiDi me dejó $1.21 más por kilómetro.”

**Verde — C1:** “Llega a mi mínimo: unos $9.49 por kilómetro.”  
**Amarillo — C2:** “Está entre mi verde y mi rojo: $7.16 por kilómetro.”  
**Rojo — C3:** “Está abajo de mi límite rojo: $2.20 por kilómetro.”  
**Gris — C4/C5:** “Le falta información; este viaje todavía no tiene semáforo.”

## 6. What only evidence can settle

- **One-second comprehension:** test corrected C1–C5 at 60cm, day/night and with colour-vision differences; measure floor decisions and false rejection of gris.
- **Value of the second rate:** compare one-rate and two-rate variants under the same timed exposures.
- **Money/source comprehension:** ask drivers to explain B1/E1/E3/F2, including offer estimates, statement payments, expenses and waiting time.
- **Install→preparado time:** measure fresh stock/OEM installs, settings return, conditional battery recovery and skipped signup.
- **Reader reliability and placement:** replay real supported layouts, permission loss, process death, repeated failures and pause/resume; measure latency, stale values and covered controls.
- **Accounting correctness:** replay cancellation/undo, duplicate imports, differing trip coverage, corrections and month boundaries against exact ledger totals.
- **Benchmark validity:** reproduce the mean and percentile from eligible records, then assess cohort coverage and selection bias.
- **Billing continuity:** test pending, grace, hold, cancellation and offline access with unused trial/referral days; compare every state with the actual entitlement.
- **IMSS and disclosure validity:** validate statutory inputs and configured citations, plus accessibility/data-flow disclosures, before enabling E5.
- **Weekly-import and Premium demand:** observe successful weekly confirmations and repeat use before testing willingness to pay for the available comparison.
- **Native accessibility:** verify target bounds, focus order, font scaling and scrolling in Compose; CSS dimensions alone do not establish device behavior.

## 7. Verdict

This version does **not yet** meet “comparable in use and feel to the Uber Driver app” for its job: **65/80 — NOT_YET**, with most Round 7 defects fixed but material numerical and consistency defects remaining.  
The single biggest residual risk is that a polished, apparently verified financial answer can still disagree with its underlying period, source or calculation.  
Build the capture → readiness → chip → dated-ledger slice first, with corrected fixture assertions, cancellation/undo and statement reconciliation, then validate it on mounted phones before expanding the paid surfaces.