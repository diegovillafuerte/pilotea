# Kompara v2 (fixture v3), Round 5 (Opus): re-score from scratch

**Judged:** `docs/ux-elevation-spec.md` (22:53), `design-system/ui_kits/android-app-v2/index.html` (22:54, 46 frames), `rubric-uber-bar.md`. I re-ran every figure against fixture v3 ($2.8417/km, verde ≥ $8.00, rojo < $6.00, car cost $214.29/día from 28 sep, dated October records) at full precision.

**Verified and now right:**
- **October rows.** The month hero equals its rows: $1,417 + $4,747 + $4,980 + $1,026 = $12,170. The October income per app adds up: Uber $12,608 + DiDi $8,432 = $21,040, which equals the dated weekly income.
- **Chip examples.** C1–C3, D2 and D3 compute.
- **Weekly IMSS / car / timeline.** Uber's IMSS payment (E4/E5) is consistent with the fixture. The car cost starts 28 sep. The pause holds no offers.
- **Standing metric.** The per-app $/h is gone from standings.
- **IMSS launch.** E4 ships without bars.
- **Data flows.** The disclosure and Tus datos name every flow.
- **New frames.** B6, E5, F5, G6, H1b and H5b exist.

What remains is narrower, but it is still about where the numbers come from (estimate vs statement), plus one sales promise with no data behind it.

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **9** | Every root has one hero. E1's standing is now a labelled door row ("Comparar esa semana · por viaje, antes de gastos"), and F2 states the standing once. Tu día (D1) is dense, but its list is the job. |
| 2 | One primary action | **9** | One verb at the thumb everywhere: A5 "Usar 12 km/L (típico)", E4 "Consultar mi vigencia en el IMSS", F5 "Verificar mis datos", H1b "Mandar código". H5b now has two explicit choices instead of a question answered by "Guardar". |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar, depth ≤ 2, selectors inline in the sentence, the spec's IA map is cleaned. Comparar still stacks segment + week + chips (one of them disabled with an explanation) before the answer. |
| 4 | Numbers discipline | **7** | The free standing ranks the reader's **estimated** offer fares against a **statement** cohort, which F5 itself says needs verification (R5-01). F3/H1 sell an Uber "por km" comparison the data can't produce (R5-02). Shift subtotals are off by $8 (R5-04). The 5–11 oct split doesn't reconcile (R5-05). D1/B1/B6 show numbers that aren't in the fixture (R5-08). |
| 5 | Copy | **8** | Tight es-MX with one vocabulary. But: an internal ticket ID reaches drivers in E5 ("Base confirmada por la ley (**B-038**)"); the H1 hook says "te **pagaron** $148" about an estimate; "Le ganas al 78 %" on a per-trip metric reads as overall superiority; G5 contradicts itself. |
| 6 | States designed | **8** | 46 frames cover off, pausa, partial, next-morning, gris (two kinds), no-cohort, unverified, launch vs post-gate IMSS, no-account trial, pre-install import and reconciliation. Undefined: the open week's car cost in the week view, a month containing an imported week with its net withheld. Not mocked: Avisos, Elegir periodo, Ayuda. |
| 7 | Time-to-value | **8** | A true verdict on screen 1 (A1), the permission at step 3 with a capability-tested route (A3), the battery step only when detected (A4), costs after the cliff (A5), the account optional (A6). Capped by the Android settings detour; nothing here is device-verified. |
| 8 | Consistency and feel | **8** | The mocks read as one hand. The spec is mostly regenerated, but §5.7 still says "$13,920 · 186 viajes · 123 h" and §5.19 still says "Sin color" (R5-06). H5b brings back per-app reader hours that F1/F2 say don't exist (R5-05). |
| | **Total** | **66 / 80** | **NOT_YET.** Numbers discipline is 7 (< 8) and the total is < 68. Fixing R5-01, R5-02 and R5-04 to R5-08 lifts numbers to 8 (67). The 68th point has to be earned with evidence (driver comprehension tests for copy, device runs for time-to-value), not with more document passes. |

---

## 2. Hard fails

- **Auto-action proposed: none.** Spec §6: "Drag only… Tap does nothing… Long-press does nothing either." A2: "nunca acepta ni rechaza por ti".
- **Platform logos: none.** Platform names appear as plain text only. The A1/G2 samples are neutral ("no countdown, no accept button, no rating"); the host card in C1–C6 is "internal only".
- **A driving-time surface that fails the 1-second glance: none.** C5 now reads "? Falta el tiempo · por km: $9.49 · sin semáforo", so the uncoloured rate can no longer pass for a verdict. C6 is 5 s and lets touches pass through.
- **A paywall that blanks the free product: none.** E2 keeps "Te quedaron esa semana · aprox. $4,310". F3 masks only the city average. F4 and F5 show no purchase prompt.
- **An onboarding screen with more than one ask: none.** A1–A7 each have one primary. A5's "Ya sé mi costo por km" is the same answer by another route.

---

## 3. Defects (ranked by damage)

**Counts:** 0 critical · 2 high · 9 medium · 7 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R5-01 | F2, F3, E1, H3, H1 vs F5; fixture | **high** | The free standing, the share card and the paywall hook rank the reader's **estimated** fares against a cohort built from **statements**. The app's own verification screen says such a comparison needs a confirmed statement week. | The fixture says the cohort is "statement data, before expenses… $126 → the driver's $148 ranks at the 78th percentile". The driver's $148 is "Estimado de … viajes tomados (lo que decía la oferta)" (D3). Taken-then-cancelled trips, tolls and adjustments are invisible to the reader. Yet F5 says: "Para compararte con conductores de CDMX necesitamos una semana tuya confirmada con el resumen de Uber… Es un requisito de los datos". F3 (free) still shows "Le ganas al 78 %…", and H3 shares "Top 22 % por viaje en CDMX". | Apply F5's rule to every standing: compute the rank only from a **statement-confirmed** week. Until then, show the reader value with "según el lector" and the line "Confirma una semana con tu PDF para ver tu lugar", and suppress the share-card standing. The same verification step unlocks it for free and paid drivers alike. |
| R5-02 | F3, H1 vs fixture, H5, comparison spec | **high** | The paywall and the lock card sell an Uber **por km** city comparison that the data can't produce. That breaks "Nobody is charged for absent data". | F3: "Disponible para ti ahora: **Uber en CDMX, por km y por viaje**." H1: "Disponible para ti ahora: **Uber, por km y por viaje**." The fixture's cohort is per-trip only. Uber statements carry no km (H5: "Kilómetros · No venía en tu archivo"; `comparar-redesign-spec.md` §2: "Uber `earnings_per_km` → '—' (Uber no reporta km)"). | Generate the availability text from the cohort registry: "Disponible para ti ahora: Uber en CDMX, por viaje." Disable the "Por km" chip in Tu ciudad for Uber with "Uber no reporta km en sus resúmenes". |
| R5-03 | F2, F3, E1, H3 | medium | A per-trip rank is phrased as overall superiority. Per-trip rewards long or rare trips and ignores waiting time, so a slow day can "beat 78 %". | "**Le ganas** al 78 % de los conductores de Uber en CDMX · Por viaje, antes de gastos"; H3: "Top 22 % por viaje en CDMX". | Make the sentence say what is measured: "**Por viaje, te pagan más que al 78 %** de los conductores de Uber en CDMX". Add one provenance line: "No cuenta cuánto esperaste entre viajes." On the card: "Mis viajes pagan más que el 78 % en CDMX". |
| R5-04 | Fixture, D1, B1, B5, spec header | medium | The shift subtotals don't follow the fixture's own full-precision rule, and B1/B5 inherit the error. | Shift 1: "$831, 64.3 km → **$640**". At $2.8417/km that is $831 − $182.72 = **$648**. Shift 2: "$759, 58.9 km → **$600**", actually **$592**. The day total ($1,240) only works because the two errors cancel. B1 "$754" should be **$762** ($648 + $114). B5 "$640" should be **$648**. | Recompute the subtotals from the engine (D1: $648 / $592; B1: $762; B5: $648). This is exactly what the promised CI equality check would have caught, so it should actually run before the next "fixed" claim. |
| R5-05 | H5b vs F1, F2, fixture; F4 | medium | Reconciliation brings back the per-app reader hours the redesign just declared unmeasurable, and the week it shows doesn't reconcile with the fixture. | H5b, Lector column for Uber alone: "Horas · **26 h en turno**". Fixture and F1: "Hours per app are not measurable by the reader when both apps run". 5–11 oct per fixture: "**60 viajes $8,100**". H5b has Uber "31 viajes · $4,790 est.", F4 has DiDi "Semana anterior · **30 viajes** · $107/viaje" (≈ $3,210). That's 61 trips and $8,000. | Show reader hours only for the week as a whole ("41 h en turno · ambas apps"). In H5b, use "Horas · — (el lector no separa por app) · 26 h en línea (según Uber)". Regenerate the 5–11 split so Uber + DiDi equals 60 trips and $8,100. |
| R5-06 | Spec §5.7, §5.19, §5.9, §5.1 vs mocks | medium | The spec still contradicts the mocks, even though §9c #5 says everything was regenerated. | §5.7: "hero 'Te quedan en octubre **$13,920**' + '**186** viajes · **123 h**'" vs E3 "$12,170 · 158 viajes · 106 h". §5.19 Ayuda: "**Sin color**: no pudimos leer la oferta" (§4 vocabulary: never "sin color"). §5.9 orders the chips "Por km · Por hora · Por viaje" in one line and "Por km · Por viaje · Por hora" in the next, while the mocks use the second order. §5.1 is titled "[B1–B4]" although B5 and B6 exist. | Regenerate §5.7 from the fixture. Change Ayuda to "Gris: no pudimos leer la oferta." Pick one chip order. Retitle §5.1 "[B1–B6]". Add the spec's prose to the CI diff. |
| R5-07 | E1, G5, spec §5.6 | medium | There's no rule for the car cost in the **current** week's view, so the week and the month can show the same day two ways. | G5: "En tu semana verás una línea 'Lo del coche −$1,500'. En el mes se reparte por día ($214 al día), incluida la semana en curso." On Monday 19 oct, a once-per-week deduction makes the open week read "Te quedan esta semana −$260", while E3's row reads "19 oct · hoy · lo del coche de 1 día · $1,026". | State one rule for the open week: allocate by day until the week closes ("Lo del coche · $214 por día hasta el domingo"), then post the weekly line. Mock the current-week view (E1 "en curso"). |
| R5-08 | D1, B1, B6, H3, E2 vs fixture box | medium | Several displayed numbers have no source in the fixture, despite the claim "every number on this page comes from it". | The fixture lists four offers (one of them "se fue sin tomar", a rojo) and only two of the nine taken offers. Yet D1 shows "Todas · **23** · Verdes sin tomar · **2**", and B1's observation "Tu mejor hora hoy: 9 a 10 h" isn't derivable. B6 shows "$380 · 3 viajes · 2 h 20 min · ✓ $8.60/km · 10:05" on no defined day. H3 shows "Mejor día **en Uber** · viernes" (the fixture only has the two-app "Viernes 16 · $1,380"). E2 shows "$4,310 · 55 viajes". | Extend the fixture to every offer of 19 oct (23 rows, with outcome and hour), B6's day, Uber's per-day figures and the August week. Or caption the frames that use out-of-fixture numbers as illustrative. |
| R5-09 | E5 | medium | An internal ticket ID is shown to drivers in a legal-adjacent line. | "Base confirmada por la ley **(B-038)** · fuente y año: STPS/CONASAMI 2026." | "Base que marca la ley para plataformas · fuente y año: STPS/CONASAMI 2026." Keep "B-038" in the spec only. |
| R5-10 | H1, F2, F3 | medium | Estimates are presented as money paid on the selling surfaces, while the rest of the app labels them as estimates. | H1 hook: "Por viaje **te pagaron** $148 en Uber." F2/F3: "Tú en Uber · Antes de gastos · $148 /viaje" with no source label. F5 says "según el lector" and D3 says "lo que decía la oferta". | H1: "Tus viajes de Uber: unos $148 cada uno, antes de gastos. ¿Cómo vas contra CDMX?" F2/F3 row label: "Tú en Uber · antes de gastos · **estimado**". |
| R5-11 | H5, spec §5.7 | medium | A month that contains an imported week whose net is withheld has no defined hero. | H5: "Te quedaron · **No disponible** · falta distancia… esa semana muestra lo que te pagó la app". Spec §5.7: an imported week is "counted in the month only for the days it covers". So "Te quedan en septiembre" would either silently drop a week or mix scopes. | Month rule: when any week lacks a net, the hero becomes "Te quedan · aprox. $X **sin 14–20 sep**" plus a row "14 – 20 sep · importada · te pagó $5,340 · sin km". Mock the September month. |
| R5-12 | B1, E1 vs spec §0.2, §4 | low | "≤ 3 numbers in the first viewport" is still exceeded on two roots, and §10 rationalises it ("one number per element"). | B1: "$754 · 6 viajes · 4 h 50 min · 9 a 10 h · $9.49 · $2.20". E1: "$4,980 · 62 viajes · 41 h · −$1,500 · 78 %…". | Either amend the law to "one number per element" or hide trips and hours behind the hero tap on B1 and E1. |
| R5-13 | F1, spec §5.9 | low | Cent-level rounding misses the full-precision rule. | DiDi: $2,645.09 ÷ 255.8 km = **$10.34**/km (shown "$10.33"); the difference is $10.34 − $9.13 = **$1.21** (shown "$1.20"). | $10.34 and "$1.21 más por km en DiDi". |
| R5-14 | H5b | low | The merged-week preview rounds off the display rule and carries a known upward bias. | "Te quedaron: aprox. **$4,190**". $5,340 − 404 km × $2.8417 = **$4,192**. Costs cover 31 of the 36 trips, so the net is overstated by about 5 × 13 km × $2.84 ≈ $185. | "Te quedaron: aprox. $4,007 (estimamos el km de 5 viajes con tu promedio de 13 km)", or keep $4,192 with "sin costos de 5 viajes". |
| R5-15 | H6 | low | The first-week invitation fires on the third week of data. | "**Tu primera semana** está lista · 62 viajes · 12 – 18 oct", but the fixture already has 1–4 oct and a complete 5–11 oct. | "Tu semana está lista" (invite on the first eligible week = 5–11 oct). |
| R5-16 | G5 | low | The car-cost sheet contradicts itself. | "Lo restamos **una vez por semana** de tu semana **y tu mes**" … then "En el mes **se reparte por día**". | "En tu semana, una línea de −$1,500. En el mes, $214 por día." |
| R5-17 | G6 vs A2 | low | Tus datos omits one flow that the disclosure lists, and the always-on counters have no off switch. | A2: "…un archivo que subas **o un reporte de lectura**." G6 "Sale del teléfono" omits the report. Counters: "Conteos anónimos de lectura" with no control. | Add "Si nos reportas una lectura: la hora y las cifras de esa oferta." Add a switch "Enviar conteos anónimos de lectura" (default on, disclosed). |
| R5-18 | G2 | low | The sample label doesn't match the sample. | "Ejemplo · **viaje largo** · $148 · 12 km en total (2 para recoger)". | "Ejemplo · viaje de 12 km". |

**Round 4 defects, status:**

| R4 id | Status |
|---|---|
| R4-01 | Fixed for hours. The same problem moved to fares (R5-01), and per-app hours reappear in H5b (R5-05). |
| R4-02, R4-03, R4-07, R4-08, R4-13, R4-14, R4-15, R4-16 to R4-21, R4-23, R4-24 | Fixed. |
| R4-04 | Fixed; G6 has a small omission (R5-17). |
| R4-05, R4-06 | Mostly fixed. §5.7 and §5.19 remain (R5-06). |
| R4-09 | Timeline fixed. Subtotal arithmetic introduced (R5-04). |
| R4-10 | Partly fixed (R5-12). |
| R4-11 | Fixed; residual bias (R5-14). |
| R4-12 | Fixed (G6, H1b, E4); Avisos and Elegir periodo still unmocked. |
| R4-22 | Now an open test (R11) in the spec. |
| R4-25 | Rule fixed; H6 copy wrong (R5-15). |

---

## 4. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo unos $754 de 6 viajes."
- **Ganancias (E1):** "La semana pasada me quedaron unos $4,980 ya con la renta."
- **Comparar (F1, Tus apps):** "DiDi me deja como $1.20 más por kilómetro que Uber." Under Tu ciudad (F3) they read "le gano al 78 %" and take it as "soy mejor que casi todos", although it is only per trip (R5-03) and from estimates (R5-01).
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo, $7.16: entre mi mínimo y mi rojo, piénsale."
- **Chip rojo (C3):** "Rojo, $2.20: no llega."
- **Chip gris (C4 / C5):** "Gris: no la pudo leer (o le falta el tiempo), decido yo." C5's "por km: $9.49 · sin semáforo" no longer reads as a yes.

---

## 5. The one change that would raise the score the most

**Make *source* a first-class field and enforce it everywhere, then run the promised equality check for real.**

- **The source field.** Every figure the fixture emits carries its source: reader estimate, app statement, or imported file.
- **The comparison rule.** A rank, comparison or "disponible" promise may only pair figures of the same source and the same basis. A city standing therefore needs a statement-confirmed week, exactly as F5 already says; for Uber, "por km" can never be offered against a statement cohort.
- **The equality check.** Run it over the mockups and the spec prose before declaring the next version fixed.

**What it fixes:** the two highs (R5-01, R5-02) and the arithmetic and coverage gaps (R5-04, R5-05, R5-06, R5-08, R5-13, R5-14).

**Score effect:** numbers discipline 7 → 8, total ≈ 67. The last point to 68 can't come from another document pass. It needs the 10-driver comprehension test ("te quedan · aprox.", "gris", "por viaje", "en turno") to earn copy 9, or a measured median install → "preparado" ≤ 75 s on Xiaomi and Samsung to earn time-to-value 9.

---

## 6. Positions I still hold against the synthesis

1. **"neto aprox." on every chip.** It still widens the overlay to "~168–184dp" for a constant word. The spec now sends this to test R11, which is the right venue. My default is "≈" in the hero, or dropping "aprox." once costs are confirmed.
2. **The standing headline should be gated like the paid detail, not freer than it.** The synthesis kept the free "Le ganas al 78 %" as the viral hook. I agree it should stay free, but only after one statement-confirmed week. A free, shareable rank built on estimates is the one claim that a WhatsApp group can disprove with their own PDFs (R5-01). Everything else from my earlier rounds has been adopted or moved to an explicit test.
