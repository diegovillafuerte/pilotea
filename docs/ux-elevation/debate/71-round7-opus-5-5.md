# Kompara v2, Round 7 (confirmation), Opus: re-score from scratch

**Judged:** `docs/ux-elevation-spec.md` (22 oct 07:30, fix log §9e), `design-system/ui_kits/android-app-v2/index.html` (07:27, 56 frames), `base.css` (07:27), `rubric-uber-bar.md`. I re-ran every displayed figure against the fixture at full precision ($2.8417/km).

**Re-verified correct:**

| Check | Result |
|---|---|
| C1 verde | $148 − 34.10 = $113.90 → $9.49/km · $285/h |
| C2 amarillo | $85.90 → $7.16/km · $191/h |
| C3 rojo | $27.05 → $2.20/km · $54/h |
| B1 (14:40) | $648.28 + $113.90 = $762 |
| E8 (semana en curso) | $762 − $214.29 = $548 |
| F5 | $5,000 ÷ 31 = $161/viaje |
| F4 | $120.43 ÷ $106.90 = +12.66 % → "+13 %" |
| F1 | $10.34 − $9.13 = $1.21 |
| F2 | $5,062 ÷ 34 = $149/viaje |
| E3 rows | Sum to $12,170 |
| H2c grace date | Follows H2b's 10 nov start |

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **9** | Each root has one hero: B1 (now with a word-only observation row), E1, F1. E8 states "Tu lugar se calcula al cerrar la semana" instead of a second hero. Tu día (D1) is dense by design. |
| 2 | One primary action | **9** | One verb per screen, at the thumb: G4 "Quitar restricción de batería", F5 "Confirmar con mi resumen de Uber", H2c "Arreglar el pago en Google Play", H5c "Agregar la captura del Tablero" (secondary "Revisar lo leído", never a save). |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar, depth ≤ 2; E6 "Elegir semana" groups by month with the lock rule shown. G7–G9 make Cuenta's rows real. Comparar still stacks segment + week + chips. |
| 4 | Numbers discipline | **8** | Source, basis and period now hold on every comparison, and the fixture reconciles almost everywhere. Residual: E5 prints "× factor 0.94" but shows $11,850 / $7,925 / $1,657, while 0.94 gives $11,852 / $7,926 / $1,656. F5 cites a 5–11 oct cohort size the fixture doesn't define. B1's first viewport carries 5 figures against the rubric's ≤ 3. |
| 5 | Copy | **8** | Tight es-MX `tú`. The reasons are now true ("por hora: falta tu resumen de DiDi (el de Uber ya está)"), and the paywall is honest ("hoy es el único dato nuevo para ti"). Residual: the share card stacks five qualifiers and says "por viaje" twice (H3). E7 says the local numbers are waiting on the network. |
| 6 | States designed | **9** | 56 frames, now including the open week (E8), Elegir periodo (E6), loading/offline (E7), Avisos with system-blocked (G7), Ayuda (G8), city change (G9), payment attention (H2c), subscription offer (H2b), import recovery (H5c) and operable cancellation (D2 "¿Se canceló el viaje? Quitar de mi día"). |
| 7 | Time-to-value | **8** | A true verdict on screen 1 (A1), the permission at step 3 with a capability-tested route (A3), the battery step only when detected (A4), costs after the cliff (A5), the account optional and last (A6). The Android settings detour can't be designed away, and no timing is measured yet. |
| 8 | Consistency and feel | **8** | The mockups read as one hand. Two things hold this at 8: the spec still lags the mockups in five places (R7-01), and two tappable controls are 44 px against the spec's own "≥ 48dp" contract (R7-04). |
| | **Total** | **68 / 80** | **PASS.** Every criterion is ≥ 8 and the total is 68. The margin is zero. This is a pass for the design as specified; it is not yet proof of "comparable in use and feel", which needs the device and driver evidence in §6. |

---

## 2. Hard fails

- **Auto-action proposed: none.** Spec §6: "Drag only… Tap does nothing… Long-press does nothing either." A2: "Kompara solo lee: nunca acepta ni rechaza por ti."
- **Platform logos: none.** Platform names appear as plain text. A1 and G2 use a neutral sample ("no countdown, no accept button, no rating"). The host card in C1–C6 is "internal only".
- **Driving-time surface failing the 1-second glance: none.** The chip carries glyph + colour + one rate. Gris names what's missing: C4 "Sin lectura · faltó la tarifa"; C5 "Falta el tiempo · $9.49/km neto aprox. · sin semáforo". C6 lasts 5 s and lets touches pass through.
- **Paywall blanking the free product: none.** E2 keeps "Te quedaron esa semana · aprox. $4,310". F3 masks only the average ("$•••"). F4 and F5 have no purchase prompt.
- **Onboarding screen with more than one ask: none.** A1–A7 each have one primary. A5's "Ya sé mi costo por km" is the same answer by another route.

---

## 3. Round 6 defects: status

| R6 id | Status | Evidence |
|---|---|---|
| R6-01 (spec drift) | **partly** | Fixed: §5.1 now matches B1's observation row; §5.11 no longer says "$4,190"; the frame count is right. Still wrong: §5.9 line 263 orders the chips "Por km · **Por hora · Por viaje**", while §9e #1 says "Regenerated". §5.1 item 2 still has "6 viajes · **4 h 50 min en turno**", which B1 removed. New drift is in R7-01. |
| R6-02 (per-hour reason) | fixed | F1: "por hora: falta tu resumen de DiDi (el de Uber ya está)". F2/F3/H1: "por hora: aún no hay promedio por hora en CDMX". |
| R6-03 (paywall sells the free rank) | fixed | H1: "El promedio de Uber en CDMX, cada semana · … hoy es el único dato nuevo para ti". F3: "Ve el promedio de tu ciudad · Para ti ahora: el promedio por viaje de Uber en CDMX ($•••)". |
| R6-04 (E5 factor and tips) | **partly** | The labels are fixed: "× factor 0.94 (ejemplo)", "sin propinas: el lector no las ve". The arithmetic still doesn't match the printed factor (R7-02). |
| R6-05 (cost-confirmation timeline) | fixed | B4 is "lunes 19 oct 7:30" with "El domingo no manejaste · última lectura el sábado 18:00". G3: "12 km por litro · confirmado". G1: "confirmados el 19 oct". |
| R6-06 (+12 %) | fixed | F4: "+13 % por viaje contra tu semana anterior". |
| R6-07 (F5 on the confirmed week) | fixed | F5's stepper reads "‹ 5 – 11 oct ›", with "Tú en Uber · según el lector · estimado · $161 /viaje". |
| R6-08 (E1 hero source) | fixed in the mockup | E1: "Con lo que leyó el lector · tu resumen de Uber dice $5,062 · …". Spec §5.6 doesn't carry it (R7-01). |
| R6-09 (share-card caveat and mixed sources) | fixed | H3: "…no cuenta la espera · según mi resumen de Uber"; the facts are "Viajes en Uber 34 · Horas en línea 24 h", both from the statement. |
| R6-10 (overloaded standing) | **partly** | F2 and E1 are fixed (one sentence + one muted line; no duplicate). H3 now carries five qualifiers and says "por viaje" twice, and spec §5.6 still has the duplicate (R7-03, R7-01). |
| R6-11 (numbers law) | fixed by restating the law | §0.2 and §4 now say "at most one number per row". B1 complies: "$762 · 6 viajes tomados", then the word-only "Tu mejor hora hoy: 9 a 10 h". The rubric's stricter "≤ 3" is noted under Numbers. |
| R6-12 (prose-only screens) | fixed | E6, E7, G7, G8, G9 added. |

---

## 4. Remaining defects (ranked by damage)

**Counts:** 0 critical · 0 high · 1 medium · 9 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R7-01 | Spec §5.1, §5.6, §5.9, appendix vs B1, E1, E8, B4 | medium | The spec is still edited by hand behind the mockups. For the fourth round, a "regenerated" row in the fix log sits beside text that wasn't regenerated. | §5.9 line 263 says "metric chips **Por km · Por hora · Por viaje**", while §9d #6 and §9e #1 both claim one order. §5.1 item 2 says "6 viajes · **4 h 50 min en turno**", but B1 shows "6 viajes tomados" (removed as unsupported). §5.6's scope line is "Después de gasolina, desgaste y lo del coche · aprox." without E1's "Con lo que leyó el lector · tu resumen de Uber dice $5,062", and its card still reads "por viaje, antes de gastos · … · Por viaje, te pagan más…". **E8** (semana en curso: "$548", "$214 por día hasta el domingo", "Tu lugar se calcula al cerrar la semana") has no §5 entry; it appears only in §9e and the appendix. The appendix still says "B4 Hoy next morning (costs typical)" although B4 is now "lunes 19 oct 7:30". | Generate §5 prose and the appendix from the mockup strings and the fixture. Extend the CI equality check from numbers to strings. Until then, fix these five spots by hand and add a §5.6b for E8. |
| R7-02 | E5, spec §5.8 | low | The post-gate example doesn't reproduce its own printed factor. | "Te pagó $12,608 × factor 0.94 (ejemplo) → base IMSS **$11,850**" (12,608 × 0.94 = **$11,852**). "$8,432 × factor 0.94 → **$7,925**" (= **$7,926**). "faltan **$1,657**" (= **$1,656**). | Either print the factor used (0.9399) or recompute with 0.94: $11,852 / $7,926 / "faltan $1,656". |
| R7-03 | H3 | low | The share card is overloaded and repeats itself. It also broadcasts the driver's hours with no way to hide them. | "12 – 18 oct · **por viaje**, antes de gastos" above "**Por viaje**, Uber me pagó más que al 78 % en CDMX · de los conductores comparados · no cuenta la espera · según mi resumen de Uber"; facts "Horas en línea 24 h". The only switch is "Ocultar montos · Tu ganancia no sale en la tarjeta". | Header: "12 – 18 oct · Uber · CDMX". Sentence: "Por viaje, Uber me pagó más que al 78 % de los conductores comparados". One muted line: "antes de gastos · sin contar la espera · según mi resumen". Rename the switch "Ocultar montos y horas", or drop the hours fact. |
| R7-04 | `base.css` (Hoy status pill, Semana/Mes segmented) vs spec §4 | low | Two tappable controls are below the spec's own touch-target contract. | `.topbar .pill{height:44px}` is the reader status pill, which opens Lector. `.seg div{padding:12px 0}` with 14 px text gives ≈ 44 px Semana/Mes segments. Spec §4: "interactive targets ≥ 48dp". | Keep the visuals and extend the touch area to 48 dp (Compose `minimumInteractiveComponentSize`), or set the heights to 48. |
| R7-05 | F5 vs fixture | low | F5 cites a cohort size for a week the fixture doesn't define. | F5 provenance: "5 – 11 oct · **1,240 conductores**". The fixture defines the cohort only for "CDMX · Uber · **12 – 18 oct** · 1,240 conductores". | Add the 5–11 oct cohort size to the fixture, or omit the size in F5 (there's no standing on an unconfirmed week anyway). |
| R7-06 | E7 | low | The offline state says local numbers are waiting on the network, which contradicts "local screens work offline" (§4). | "Cargando tus números…" together with "**Sin conexión** · Tus números de esta semana están en tu teléfono y aparecen en un momento." | Split it into two frames: a skeleton (local load, no network wording), and a loaded week with only the city block reading "Sin conexión · La comparación con tu ciudad la cargamos cuando tengas datos". |
| R7-07 | E3 vs fixture timeline | low | The month screen's clock shows a moment when today's figures can't exist yet. | E3's status bar reads **9:41**, but the row shows "19 oct · hoy · **9 viajes** · … $1,026", the end-of-day total. The shifts run to 18:00, and E8 at 14:40 shows 6 trips. | Set E3's status bar to after 18:00 (e.g. 21:10, as D1), or show the 9:41 state (fewer trips). |
| R7-08 | E4, spec §5.8 | low | A specific legal citation is hard-coded into the launch-state screen. | "(regla: dof.gob.mx, **nota 5777156** · imss.gob.mx/vigencia)"; E5: "conasami.gob.mx". | Serve the citations from remote config with the reference amount, and verify them in the B-038 legal check before release. |
| R7-09 | E6 | low | The selected week in the period sheet uses the verdict/confirmed glyph. | "12 – 18 oct **✓**". In this product ✓ means verde (C1) and "confirmado". | Use a filled radio or row highlight for the selection. Reserve ✓ for verdicts and confirmations. |
| R7-10 | B1 vs rubric criterion 4 | low | The spec's restated law passes, but the rubric's stricter budget is still exceeded on the busiest root. | B1's first viewport: "$762 · 6 viajes tomados" + "9 a 10 h" + "$9.49" + "$2.20" (5 figures; the rubric's 10 is "≤ 3 numbers in the first viewport"). | Accept it consciously (the three offer rows are the readiness proof), or show two offers above the fold. |

---

## 5. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo unos $762 de 6 viajes, y mi mejor hora fue de 9 a 10."
- **Ganancias (E1):** "La semana pasada me quedaron unos $4,980 ya con la renta; mi resumen de Uber dice un poquito más."
- **Comparar (F1, Tus apps):** "DiDi me dejó $1.21 más por kilómetro que Uber."
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo, $7.16: entre mi mínimo y mi rojo; piénsale."
- **Chip rojo (C3):** "Rojo, $2.20: no llega."
- **Chip gris (C4 / C5):** "Gris: le falta un dato (la tarifa o el tiempo); decido yo."

---

## 6. What only evidence can settle

| Claim | Test that settles it |
|---|---|
| The chip is read correctly in one second | 1-second exposures at 60 cm on a mount, day and night, 10–12 CDMX drivers including ≥ 3 with colour-vision deficiency. Pass if take/pass decisions are accurate against their stated floor and gris is never read as "no" (R1). |
| "neto aprox." earns its width; the second rate earns its line | The same test, chip variants with and without "aprox." and with one vs two rates. Measure decision accuracy and time (R11, R3). |
| The reader is fast and correct | On-device runs of the silent screenshot + OCR lane on Uber and DiDi offer cards (TD-040): parse→chip ≤ 150 ms, the fare/km/time read rate, and the chip never covering the host's buttons on real layouts. |
| Install → "preparado" is quick | Closed beta on Xiaomi, Samsung and Motorola: median ≤ 75 s, plus how often the direct accessibility route works per OEM. |
| The reader survives a shift | A full-shift soak on restricted-battery phones: the share of shifts with no "Lector detenido" incident after A4/G4. |
| Drivers understand the money words | A 10-driver comprehension test of "Te quedan · aprox.", "gris", "por viaje", "en turno", "según el lector / según tu resumen": can they explain each in their own words? |
| Typical costs are safe to start with | Beta A/B: verdict flip rate when real costs replace typical ones; < 5 % means safe (R2). |
| Drivers will confirm a week | The share of drivers who import a PDF to unlock their standing. The free hook now depends on it. |
| Premium is worth the price | A price test at $59 / $79 / $99 when Premium adds one city average + history detail + the PDF ("hoy es el único dato nuevo para ti"). |
| The IMSS claims are legally right | Legal memo (B-038): the statutory income basis, factor and tip treatment, the 2026 minimum ($9,582), and the DOF citation. E5 ships only after this. |
| Play accepts the disclosure | A pre-flight of the A2 disclosure, the accessibility declaration, the data-safety form (anonymous counters, weekly totals, opt-in, imports) and the out-of-app deletion route. |
| Three tabs are findable | Tree test and first click: "¿Dónde ves si te pagan más que a otros de tu ciudad?", "¿Dónde ves lo del IMSS?" (R9). |

---

## 7. Verdict

Yes: as specified, this version meets "comparable in use and feel to the Uber Driver app" for its job, with **PASS at 68/80** and no hard fails, no critical or high defects, and integrity rules (source, basis, period) that hold on every screen I checked. The single biggest residual risk is that none of the driving-time promises are proven yet: the 1-second chip read, OCR speed and accuracy on real offer cards, and OEM survival through a shift. Behind that sit a Premium offer that, for a typical driver today, adds one city average, and a spec that still has to be generated rather than hand-edited. Build the chip and the readiness model first, on device: the engine changes (gris not red, no unit swap, preparado/funcionando), the silent-screenshot lane and the C1–C6 states, run through the 1-second mounted test before any Ganancias or Comparar screen is built.
