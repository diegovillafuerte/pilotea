# Kompara v2 (fixture v3, round 6), Round 6 (Opus): re-score from scratch

**What I judged:** `docs/ux-elevation-spec.md` (22 oct 07:14), `design-system/ui_kits/android-app-v2/index.html` (07:14, 49 frames), `base.css` (07:11), and `rubric-uber-bar.md`.

**How:** every figure was re-run at full precision against fixture v3 ($2.8417/km, floors $8.00/$6.00, car cost $214.29/día from 28 sep, dated October records, the 12–18 oct Uber statement $5,062 · 34 viajes).

**Re-verified correct:**
- Shifts $648 + $592 = $1,240; B1 = $648 + $113.92 = $762.
- 5–11 oct: 31 + 29 = 60 viajes; $5,000 + $3,100 = $8,100.
- DiDi $2,645.09 ÷ 255.8 = $10.34; difference $1.21.
- $5,062 ÷ 34 = $148.88 → $149.
- E3 rows sum to $12,170.
- E5: $9,582 − $7,925 = $1,657.

**Fixed and verified in the mockups:**
- Standings now exist only on a statement-confirmed week (F2, F3, E1, H1, H3), and F5 covers everyone else.
- "Por km" is disabled for Uber, with the reason given.
- The standing says what it measures.
- H5b withholds the net and shows no per-app hours.
- E4 shows payments and the official route only.
- E2 labels the car cost as starting 28 sep.

**What's left** is mostly the spec drifting from the mockups again, plus a few smaller copy and arithmetic slips.

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **9** | Each root has one hero (B1, E1, F1). The Hoy observation card is gone. E3's IMSS entry is now a row without amounts. Tu día (D1) is dense, but the list is its job. |
| 2 | One primary action | **9** | One verb per screen, at the thumb: G4 "Quitar restricción de batería", F5 "Confirmar con mi resumen de Uber", H2b "Suscribirme por $79 al mes", H5c "Agregar la captura del Tablero". H5b gives two explicit choices. |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar, depth ≤ 2, selectors are inline words with ≥ 48dp hit areas, and every sheet and confirm (G6b, H2b) sits one level down. Comparar still stacks segment, week and chips before the answer. |
| 4 | Numbers discipline | **8** | Source parity holds, the shifts and splits reconcile, and cents are right. Residual: F4 shows "+12 %" for a 12.66 % rise (→ +13 %); E5's statutory base uses a factor and a tip removal that aren't in the fixture (R6-04); B1 and E1 still show 5+ numbers in the first viewport. |
| 5 | Copy | **8** | Tight es-MX `tú`, one vocabulary, no internal IDs, H1 says "unos $149". Residual: the per-hour reason blames the driver's resumen when it's the cohort that lacks hours (R6-02); H1/F3 sell "tu lugar" that is already free (R6-03); the standing sentence carries four qualifiers (R6-10). |
| 6 | States designed | **9** | 49 frames cover off, pausa, partial, next-morning, two kinds of gris, unconfirmed (F5), no-cohort (F4), launch vs post-gate IMSS (E4/E5), no-account trial (H1b), subscription offer (H2b), import review, reconciliation and recovery (H5/H5b/H5c), delete confirm (G6b). The open-week and undated-week rules are stated (G5, H5). Only Avisos, Elegir periodo, Ayuda, Tu perfil and generic loading/network-error remain spec-only. |
| 7 | Time-to-value | **8** | A true verdict on screen 1 (A1), the permission at step 3 with a capability-tested route (A3), the battery step only when detected (A4), costs after the cliff (A5), the account optional (A6). Still capped by the Android settings detour; nothing is device-measured. |
| 8 | Consistency and feel | **8** | The mockups read as one hand: tokens fixed (`--emerald-400`, nav contrast, 48dp text actions). The spec has drifted again in four places, two of which §9d says are fixed (R6-01). The fixture contradicts itself on when costs were confirmed (R6-05). |
| | **Total** | **68 / 80** | **PASS, exactly at the threshold.** Every criterion is ≥ 8 and the total is 68. There is no slack: any one criterion dropping (for example copy, if the 10-driver comprehension test fails on "aprox." or "gris") flips it back to NOT_YET. This is a pass on paper. "Uber-class in use and feel" still has to be shown on a mounted phone. |

---

## 2. Hard fails

- **Auto-action proposed: none.** C1–C6 are drag only (spec §6: "Tap does nothing… Long-press does nothing either"). A2: "Kompara solo lee: nunca acepta ni rechaza por ti."
- **Platform logos: none.** Platform names appear as plain text. A1 and G2 use a neutral sample; the host card in C1–C6 is "internal only".
- **A driving-time surface that fails the 1-second glance: none.** The chip carries glyph, colour and one rate. Gris states name what's missing (C4 "Sin lectura · faltó la tarifa"; C5 "Falta el tiempo · … sin semáforo"). C6 lasts 5 s and lets touches pass through.
- **A paywall that blanks the free product: none.** E2 keeps "Te quedaron esa semana · aprox. $4,310". F3 masks only "$•••". F4 and F5 never show a purchase prompt.
- **An onboarding screen with more than one ask: none.** A1–A7 each have one primary. A5's "Ya sé mi costo por km" is the same answer by another route.

---

## 3. Defects (ranked by damage)

**Counts:** 0 critical · 0 high · 5 medium · 7 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R6-01 | Spec §5.1, §5.9, §5.11, §9d vs mockups | medium | The spec still contradicts the mockups in places §9d says were fixed, so engineers building from the spec will bring back removed behaviour. | §5.1 item 3 still puts "**One** factual observation… 'Tu mejor hora hoy: 9 a 10 h'" on Hoy, but B1 removed it and §9d #13 says "Observation removed from Hoy". §5.9 line 263 still orders the chips "Por km · **Por hora · Por viaje**", but §9d #6 claims "one order (Por km · Por viaje · Por hora)". §5.11 still shows the H5b preview as "Te quedaron: aprox. **$4,190**, faltan km de 5 viajes", but H5b now reads "Te quedaron: no disponible" (§9d #10). §9d #15 says "(47 frames)"; there are 49. | Regenerate §5.1, §5.9 and §5.11 from the same source as the mockups. Make the equality check diff spec prose against mockup strings, not just numbers, so a "fixed" row in §9d can't sit next to unfixed text. |
| R6-02 | F1, F2, H1 vs fixture | medium | The reason given for "por hora" being unavailable blames the driver's resumen, when the resumen already has hours. What's missing is a per-hour **cohort**. | The fixture says the Uber statement for 12–18 oct has "**24 h en línea (según Uber)**", and the cohort is "per trip only". Yet H1 says "Por hora **cuando tu resumen traiga horas**", and F1 says "por hora necesita tus horas de cada app (**trae tu resumen**)", even though Uber's was already imported. | Tu ciudad and H1: "Por hora: aún no hay promedio por hora de Uber en CDMX." Tus apps: "Por hora: falta tu resumen de DiDi (el de Uber ya está)." Generate both from the availability registry: driver side × cohort side. |
| R6-03 | H1, F3 | medium | The paywall's second benefit sells something this driver already has for free. For this driver, Premium adds exactly one number. | H1: "**Tu lugar en cada métrica disponible**", while the only available metric is "por viaje", whose place is shown free in F3 ("Por viaje, te pagan más que al 78 %…"). F3's card: "Ve el promedio de tu ciudad **y tu lugar** en cada métrica… Para ti ahora: Uber en CDMX, por viaje." So the only new paid figure is the $126 average. | Sell only what the purchase reveals for *this* driver: "El promedio de Uber en CDMX ($•••) y cada métrica nueva cuando haya datos". Drop "tu lugar" from the benefits while the only standing is the free one. This is the "do not charge for absence" rule applied to overlap. |
| R6-04 | E5; spec §5.8 vs fixture | medium | The post-gate IMSS base applies a factor and a tip removal that the fixture doesn't define, and removes tips from reader estimates that never contain tips. | E5: "Te pagó $12,608 → base IMSS **$11,850** (**sin propinas**, con el factor de la regla)" and "$8,432 → **$7,925**", an implied factor of 0.9399 for both apps. The fixture box defines neither the factor nor any tip amount. The reader's "te pagó" is "lo que decía la oferta" (D3), which has no tips. | Put the rule in the fixture (factor, tip treatment, source) or tag E5's numbers "Ejemplo" until B-038 lands. Apply tip removal only to statement figures; for reader estimates, say "estimado sin propinas (el lector no las ve)". |
| R6-05 | Fixture box, B4, G1, G3 | medium | The fixture says costs were confirmed before B4 happens, and the screens disagree with each other about whether costs are typical or confirmed. | Fixture: "the driver confirmed them on **19 oct 7:30 (B4)**", but also "B2 and B4 are **martes 20 oct** 8:05 / 7:30". B4 (20 oct) still asks "Tus números usan costos típicos… ¿Es tu caso?". G1: "Tus costos · $2.84 por km · **confirmados el 19 oct**". G3 still labels "12 km por litro · **típico**" and "$24.50 por litro · **referencia**". | Pick one timeline: B4 on lunes 19 oct 7:30 asks, the driver confirms, and from then on G3 shows "12 km por litro · confirmado" and drops the "típico" labels. |
| R6-06 | F4; spec §5.9 | low | The self-benchmark rounds a 12.66 % rise down. | $3,372 ÷ 28 = $120.43; $3,100 ÷ 29 = $106.90; ratio 1.1266. Shown "**+12 %** por viaje contra tu semana anterior" in both F4 and the spec. | "+13 %". The equality check should cover percentages too. |
| R6-07 | F5 | low | The "unconfirmed week" state is drawn on the week the fixture says is confirmed. | F5's stepper shows "‹ **12 – 18 oct** ›" with "Falta confirmar esta semana…", while F2, F3 and the fixture say 12–18 oct is statement-confirmed. | Show F5 on "5 – 11 oct" (Uber $5,000 reader estimate, no statement). That week has a real H5b reconciliation path. |
| R6-08 | E1 | low | The week screen pairs a reader-sourced hero with a statement-sourced standing and only labels one of them. After an import, the driver can't tell which source the total uses. | Hero "Te quedaron esa semana · aprox. $4,980" (built from Uber $5,028 *estimated*). The row beneath it: "…**según tu resumen de Uber** · Por viaje, te pagan más que al 78 %" (built from $5,062). | Label the hero's source in the scope line ("…con lo que leyó el lector · tu resumen de Uber dice $5,062") or record the H5b choice for 12–18 oct ("Usaste el total del PDF") and recompute ($5,014). |
| R6-09 | H3 | low | The share card drops the waiting-time caveat and mixes a statement standing with a reader-estimated fact. | "Mis viajes pagan más que el 78 % en CDMX" has no "no cuenta cuánto esperaste". "Mejor día en Uber · viernes" comes from the fixture's "$1,020 **est.**", a reader estimate, while the standing comes from the statement. | Add a small line "por viaje · no cuenta la espera". Take "Mejor día" from the statement if it has days; otherwise label it "(según el lector)" or replace it with "Viajes en Uber 34". |
| R6-10 | F2, F3, E1 | low | The standing headline is overloaded and repeats itself, so it no longer reads at a glance. | F2/F3: "Por viaje, te pagan más que al 78 %… · Antes de gastos · 12 – 18 oct · según tu resumen de Uber · no cuenta cuánto esperaste entre viajes" (four qualifiers). E1: "Comparar esa semana · **por viaje**, antes de gastos · según tu resumen de Uber · **Por viaje**, te pagan más…". | Keep one sentence plus one muted line ("Antes de gastos · según tu resumen de Uber"). Move the period and the waiting caveat into "De dónde salen estos datos". Remove the duplicate "por viaje" in E1. |
| R6-11 | B1, E1 vs spec §0.2 / §4 | low | "≤ 3 numbers in the first viewport" is still not met. B1's caption redefines the budget instead. | B1: "$762 · 6 viajes · 4 h 50 min" plus "$9.49 · $2.20" ("the first viewport holds the hero and three rates"). E1: "$4,980 · 62 viajes · 41 h · −$1,500 · 78 %". | Change the law to "one hero plus at most one number per row" (that's what the screens do) or move trips and hours behind the hero tap. |
| R6-12 | Spec §5.13–§5.19 | low | A handful of everyday screens and generic states exist only as prose. | No frames for Avisos, Elegir periodo, Ayuda, Tu perfil or Entrar con WhatsApp, and no generic loading or network-error frame (shared contract §4 only). | Add five frames: Avisos with system-blocked, Elegir periodo with locked weeks, Ayuda, Tu perfil with city change, and a loading skeleton plus offline on Ganancias. |

**Round 5 defects, status:**

| R5 id | Status |
|---|---|
| R5-01 | Fixed: statement-only standings, F5 for everyone. |
| R5-02 | Fixed: "por km no disponible: Uber no reporta km". |
| R5-03 | Fixed in F2/F3/E1. The caveat is still missing on the share card (R6-09). |
| R5-04 | Fixed. |
| R5-05 | Fixed. |
| R5-06 | **Not fully fixed** (R6-01). §5.7 and Ayuda are fixed, but the chip order is still wrong at §5.9 line 263, and §5.1/§5.11 have new stale text. |
| R5-07 | Fixed (G5 open-week rule). |
| R5-08 | Fixed: fixture extended; B6 captioned as illustrative. |
| R5-09 | Fixed. |
| R5-10 | Fixed. |
| R5-11 | Fixed: undated weeks are never spread into months. |
| R5-12 | **Remains** (R6-11). |
| R5-13 | Fixed. |
| R5-14 | Fixed in the mockup (net withheld). **Remains in spec §5.11** (R6-01). |
| R5-15 to R5-18 | Fixed. |

**New defects introduced by the fixes:**
- R6-02: the per-hour reason, after source became a first-class field.
- R6-03: the paywall overlap, after the standing became free on a confirmed week.
- R6-04: the factor in the E5 rebuild.
- R6-05: the costs-confirmation timeline.
- R6-07: F5's week.

---

## 4. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo unos $762 de 6 viajes, y la última verde la tomé."
- **Ganancias (E1):** "La semana pasada me quedaron unos $4,980 ya con la renta."
- **Comparar (F1, Tus apps):** "DiDi me dejó $1.21 más por kilómetro que Uber."
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo, $7.16: entre mi mínimo y mi rojo; piénsale."
- **Chip rojo (C3):** "Rojo, $2.20: no llega."
- **Chip gris (C4 / C5):** "Gris: no la leyó (o le falta el tiempo); decido yo."

---

## 5. The one change that would raise the score the most

**Make the spec a generated artifact of the same source as the mockups, and fail the build on any spec↔mockup string or number difference.**

That means generating the §5 screen prose, the §6 state table, the §7 onboarding copy and the §9 fix tables' "fixed" claims from the fixture and the string catalogue, not editing them by hand. For three rounds running, the mockups were corrected while the spec kept or reintroduced the removed version, and §9d now claims fixes the text doesn't contain.

**Effect:**
- Clears R6-01 and R6-06.
- Clears R6-05 (the confirmation timeline lives in one place).
- Clears the §9d frame count.
- Lifts consistency and feel to 9 and adds a point of slack above the 68 threshold, so one weaker result from the driver tests doesn't flip the verdict.

The other lever, a 10-driver test on "aprox.", "gris", "por viaje" and "en turno", could raise copy to 9, but it needs drivers, not documents.

---

## 6. Positions I still hold against the synthesis

1. **Removing the observation from Hoy dropped a decided fork without saying so.**
   - F8/F9 converged on "one factual observation" as Hoy's reason to open.
   - It was word-only ("Tu mejor hora hoy: 9 a 10 h") and added no money figure.
   - It was cut to satisfy a numbers budget that B1 still exceeds (R6-11), and it doesn't appear in Tu día's mockup either, so it now lives nowhere.
   - Restore it as the row under the hero (its numbers are hours, not pesos), or say explicitly that F9 changed.
2. **"neto aprox." on every chip.** It still widens the overlay to about 168–184dp for a constant word. The spec routes this to test R11, which is the right venue. My default remains "≈" in the hero, or dropping "aprox." once costs are confirmed, which G1 says happened on 19 oct.
