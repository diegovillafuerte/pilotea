# Kompara v2 (fixed), Round 4 (Opus): re-score from scratch

**What I judged:**
- `docs/ux-elevation-spec.md` (mtime 22:35)
- `design-system/ui_kits/android-app-v2/index.html` (mtime 22:28, 40 frames)
- `rubric-uber-bar.md`

I gave no credit for intentions. Every arithmetic claim was re-run against the golden fixture printed at the top of the mockups:
- costs $2.84/km; verde ≥ $8.00/km, rojo < $6.00/km
- lo del coche $1,500/semana
- week 12–18 oct: te pagó la app $8,400, 676 km, $6,480 → $4,980
- Uber 34 viajes / 420 km / 24 h; DiDi 28 / 256 / 17 h

**What is now genuinely good:**
- The chip examples compute: C1–C3, A1, G2, D2, D3, F1, F2, F4 all reproduce.
- Tu ciudad money is on one basis.
- The disclosure names retained figures and the opt-in.
- The overlay editor is gone.
- "Funcionando" decays.
- "En pausa" exists.
- Own totals are never masked.
- IMSS is dated and the official route is the primary action.
- Emoji are gone.
- The cost question comes after the permission.

The remaining defects are fewer, but they are the same *class* that sank Round 3: numbers that disagree with the fixture, and a spec that still says what the mockups stopped saying.

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **8** | B1, D1, F1, E4 each have one clear hero. E1 still carries a second hero (the "Le ganas al 78 % en Uber · CDMX" card) on top of the car line, chart, days, share and import. F2 states the same standing twice ("78 %" + "Top 22 %"). |
| 2 | One primary action | **9** | A5 "Usar 12 km/L (típico)", G2 "Guardar", E4 "Consultar mi vigencia en el IMSS", H1 "Empezar mi prueba de 14 días" each have one verb at the thumb. Only H5 is muddy: it asks "¿Agregamos los del archivo…?" but answers with "Guardar / Descartar". |
| 3 | Navigation that disappears | **8** | 3 tabs + avatar, depth ≤ 2, selectors inline in F2. Comparar still stacks segment + week stepper + metric chips before any answer (F1–F4). The spec's IA map still lists a removed "threshold editor" (§3) and the old screen name "IMSS: revisar mi mes". |
| 4 | Numbers discipline | **7** | The offer math is now correct. But: E3/E4 "Uber te pagó $5,200" for October contradicts the fixture (≥ $10,368 from F2 + H5). G5's "Se resta desde lunes 19 oct" contradicts E1's −$1,500 for 12–18 oct. F2's "$210/h" rests on per-app hours the reader can't measure (R4-01). B1 and F2 put 7 numbers in the first viewport. |
| 5 | Copy | **8** | Strong es-MX tú and one money vocabulary. Residual drift: "sin color" printed on the C5 chip; "límite" (A1) vs "mínimo"; "Horas en línea" (H5) vs "en turno"; "Te quedarían" for a taken trip (D2). A6's "solo para enviarte el código" is inaccurate. |
| 6 | States designed | **8** | B1–B5, C1–C6, D1–D3, E1–E4, F1–F4, G1–G5, H1–H6 are well covered. Not designed: the IMSS launch state (no bars until B-038), Tus datos (the opt-in and deletion surface), the no-account trial path, Avisos, Elegir periodo. |
| 7 | Time-to-value | **8** | A correct simulated verdict on screen 1 (A1 $9.49 ≥ $8.00), the permission at step 3 with a deep link (A3), costs after the cliff (A5), the account optional and last. Still capped by the Android settings detour and an unverified deep link. |
| 8 | Consistency and feel | **7** | The mockups now read as one hand (vector icons, one editor model, bounded C6). But the build document disagrees with them: spec §7 A1 still has "$148 · 16 km · 22 min · Recoger a 2 km" (the false-verde demo); §3 has "Overlay: chip · threshold editor"; §5.1 has "$1,240" with "6 viajes"; §6/§9/§5.19 have "sin color". The fixture also disagrees with itself (R4-09). |
| | **Total** | **63 / 80** | **NOT_YET.** Two criteria are below 8 (numbers 7, consistency 7), and 63 < 68. Fixing R4-01…R4-05 lifts both to 8 (≈ 65). Passing needs 9s that only driver tests (copy) and device runs (time-to-value, navigation) can earn. |

---

## 2. Hard fails

- **Auto-action proposed: none.** C1–C6 are "drag only"; spec §6: "Tap does nothing … Long-press does nothing either. The overlay threshold editor is removed." A2: "nunca acepta ni rechaza por ti".
- **Platform logos: none.** Platform names appear as plain text. A1 and G2 use a neutral sample ("no countdown, no accept button, no rating"). The host card in C1–C6 is labelled "internal only".
- **A driving-time surface fails the 1-second glance: none.** The closest is C5: a grey chip whose second line shows a coloured-looking good number ("$9.49/km neto aprox. · sin color"). A tired driver may read $9.49 as "sí". See R4-06 and §4.
- **A paywall blanks the free product: none.** E2 keeps "Te quedaron esa semana $4,620" visible and masks only the days. F3 masks only the city average. F4 has no paywall when no cohort exists.
- **An onboarding screen with more than one ask: none.** A1–A7 each have one primary. A5's text button "Ya sé mi costo por km" is an alternative route to the same answer, not a second ask.

---

## 3. Defects (ranked by damage)

**Counts:** 1 critical · 4 high · 10 medium · 10 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R4-01 | F2, F3, E1, H1, H3; spec §5.9 | **critical** | The free standing, the share card and the paywall hook all rank a per-app **$/h** whose denominator the reader can't measure, against a population whose hours come from a different source. That makes it like-for-like on money and not like-for-like on time. | F2: "Tú en Uber · Antes de gastos · **24 h en Uber** · $210 /h" vs "Promedio de Uber en CDMX · Antes de gastos · 1,240 conductores · $170 /h", leading to "Le ganas al 78 %". The fixture splits 41 h into "Uber 24 h, DiDi 17 h", so a multi-app driver is assumed never to run both apps at once. But the reader only sees which app shows an offer, and the comparison spec folds hours as "hours = `max` (replicated per platform-row)" (`comparar-redesign-spec.md` §2). The population's hours come from statements: H5 shows Uber's "**Horas en línea** 26 h". With both apps open all week, Uber's hours are 41 h, which gives $123/h and puts the driver below the average, not in the top 22 %. The rank can flip on an unmeasurable split, and H3 ("Top 22 % por hora en Uber"), E1 and the H1 hero ("Por hora te pagaron $210 en Uber") broadcast it. | Rank per-hour only when the driver's hours come from the **same source** as the population (an imported statement for that app and week). Otherwise default Tu ciudad and the share card to **"Por viaje"** (no hour denominator; `earnings_per_trip` has a benchmark for both apps). Show "Por hora" with "Necesita tus horas de la app: trae tu resumen". Add an "Hours: source" field to the fixture. |
| R4-02 | E3, E4 (vs F2, H5) | **high** | The IMSS figure for Uber contradicts the golden fixture by about $5,000+, so "Faltan $4,382" is false by the app's own data. | E3/E4: "Uber te pagó **$5,200** · estimado · Faltan $4,382 para $9,582". But F2's caption gives Uber's 12–18 oct share as **$5,028**, and H5 imports "5 – 11 oct · Uber · Te pagó la app **$5,340**". That's ≥ $10,368 in October before Oct 1–4 and Oct 19, already above $9,582. | Recompute E3/E4 from the fixture (Uber ≈ $10,400+ "estimado", DiDi accordingly). The CI check must cover IMSS cards, not only chips. |
| R4-03 | G5 vs E1, E3 | **high** | "Lo del coche" starts *after* the week it is deducted from, so the headline $4,980 breaks the app's own rule. | G5: "Cada semana $1,500 · **Se resta desde lunes 19 oct**". E1 (12–18 oct): "Lo del coche … −$1,500" and "Te quedaron esa semana **$4,980**". E3 deducts it from "5 – 11 oct · $4,620" and "28 sep – 4 oct · $3,080" too (scope "…y lo del coche"). | Either date G5 "Se resta desde lunes 28 sep", or stop deducting before 19 oct (then E1 = $6,480 with the line "Lo del coche: empieza el 19 oct"). Add the start date to "Cómo se calculó". |
| R4-04 | A2; spec §5.20 | **high** | The prominent disclosure's "Nada, a menos que…" is the blanket claim the spec itself forbids. It omits derived data the design sends. | A2: "**Qué sale del teléfono. Nada**, a menos que tú crees tu cuenta (tus totales por semana) o elijas sumar tus totales…". Not mentioned: (a) parser "Breakage telemetry: anonymized parse success/failure counters per host-app version" (`android-technical-design.md` §1, item 3; risk register "telemetry"); (b) D2's "¿Se leyó mal? Avísanos" (derived numbers to support); (c) Importar (a chosen file). Spec §5.20: "No blanket 'nothing leaves' claim…". | "**Qué sale del teléfono.** Conteos sin datos tuyos de qué tan bien leemos cada app. Y, solo si tú lo eliges: tus totales por semana (con tu cuenta), tu aporte al promedio de tu ciudad, o un archivo que tú subas." Or drop the telemetry. Mirror this in Tus datos and the Data-safety form. |
| R4-05 | Spec §7, §3, §5.1, §5.8, §8, §9 | **high** | The build document still contains the content the Round 3 fixes removed from the mockups. Building from the spec reintroduces R3-D01, D08, D14 and D20. | §7 A1: "**$148 · 16 km · 22 min · Recoger a 2 km**" (by the fixture's costs that is $5.38–$6.41/km, not the verde shown). §3 map: "Overlay: chip · **threshold editor** · …". §5.1: "Hero: 'Te quedan hoy' `$1,240`" + "6 viajes · 4 h 50 min" (B1 shows $754). §3 map and §5.8 heading: "IMSS: **revisar mi mes**" vs the rule "one name everywhere ('IMSS del mes')". §8: "Reader, chip, **editor**, …". §9 F2/F4 and §6 shape cue: "**sin color**". §9b claims all fixed. | Regenerate spec examples from the same fixture as the mockups (A1 = "$148 · 12 km en total (2 para recoger) · 24 min"). Delete "threshold editor" and "editor". Rename to "IMSS del mes". Set §5.1's hero to $754 at 14:40. Apply the gris vocabulary (R4-06). |
| R4-06 | C5 (chip), spec §4 vs §6, §5.19, §9 | medium | The "never" word is printed on a driving surface. Round 3 D20 is not fixed, although §9b #14 says it is. | Spec §4: Verdict states "**gris** … Never: … '**sin color**'". C5 chip line 2: "$9.49/km neto aprox. · **sin color**". Spec §6: "? sin color". Ayuda §5.19: "**Sin color**: no pudimos leer la oferta". A1 says "Gris: no se pudo leer". | C5 row 2: "por km: $9.49 · sin semáforo". Ayuda: "Gris: no pudimos leer la oferta." Grep the spec for "sin color" and remove every hit. |
| R4-07 | E3, E4, H6 vs spec §5.8, R12 | medium | Only the post-legal IMSS state is mocked. The state that will actually ship first is undesigned. | Spec §5.8: "until legal (B-038) confirms the income basis, the screen shows payments and the official route with **no bars, no 'faltan'** and no threshold notification". E3/E4 show `.bar` elements, "Faltan $4,382 para $9,582" and "Arriba de $9,582 según tus registros". | Add an E4-launch frame: per-app "Te pagó (estimado)" rows, the dated reference line, "Consultar mi vigencia en el IMSS" as primary, and no bars or "faltan". E3's card shows totals only. |
| R4-08 | E1 | medium | The standing card on Ganancias has lost its metric and basis. This is the overclaim fixed on H3 but not here. | E1: "Comparar esa semana · **Le ganas al 78 % en Uber · CDMX** ›". F2 says "Por hora, antes de gastos"; H3 says "Top 22 % **por hora** en Uber". | "Le ganas al 78 % por hora en Uber · CDMX (antes de gastos)", or, after R4-01, "por viaje". |
| R4-09 | B1, B5, D1, D3 | medium | The fixture's own day contradicts itself: offers are "read" while the reader was paused or off, and the same offers carry different times on different screens. | B5: "Lo pausaste a las **12:40** … No leemos ofertas". D3: "lector **apagado** 12:30–14:20" (a pause relabelled as off, at a different time). B1 lists "✕ $2.20/km · **14:18** · Se fue sin tomar" and "? Sin lectura · **14:05**", both inside that window and outside any shift, against spec §5.3 "Every offer falls inside a shift". D1 shows the same rojo and gris at **15:10** and **15:22**. | One timeline: pause 12:40–14:20 labelled "en pausa" in D3, and no offers inside it. B1's last offers are 14:32, then any earlier ones from 08:00–12:30. |
| R4-10 | B1, F2, E1 vs spec §0.2, §4 | medium | ">3 numbers in the first viewport" is still violated, and the spec now rationalises it ("one number per element", §10). | B1: "$754 · 6 viajes · 4 h 50 min · 9 a 10 h · $210 · $9.49 · $2.20". F2: "78 % · 12 – 18 oct · Top 22 % · 24 h · $210 · 1,240 conductores · $170". E1: "$4,980 · 62 viajes · 41 h · −$1,500 · 78 % …". "Le ganas al 78 %" and "Top 22 %" are the same fact twice. | Drop "Top 22 %" (keep the sentence plus the 20-person bar). Move "24 h", "1,240 conductores" and the period into the provenance sheet. Make B1's observation word-only ("Tu mejor hora hoy: 9 a 10 h"). |
| R4-11 | H5 | medium | The reconciliation asks a question it can't answer on this screen, and the merged week's net is undefined. | "¿Agregamos los del archivo y nos quedamos con el total del PDF para 'te pagó la app'?" followed only by "Guardar · Descartar". The PDF covers **36** Uber trips with "Kilómetros: No venía en tu archivo"; the reader covers **31** trips with km. The spec forbids mixing coverage for *rates*, but "Te quedan" for 5–11 oct (E3 "$4,620") still needs costs for 36 trips. | Two explicit buttons: "Usar el total del PDF" / "Quedarme con lo del lector". After a PDF merge, show the week as "Te pagó la app $5,340 (PDF) · costos de 31 viajes registrados · faltan 5 sin km". Withhold "Te quedan" or label it "aprox., faltan km de 5 viajes". |
| R4-12 | Spec §5.20, §5.12, §5.16; F2/F3 | medium | The surfaces that carry Play-policy weight are still unmocked. | No frame for Tus datos (the contribution switch, "Borrar datos de este teléfono", "Eliminar mi cuenta", the external deletion route). No frame for the no-account trial ("Activa tu prueba con tu WhatsApp"), Avisos or Elegir periodo. For a city **with** a cohort (F2/F3), the opt-in "Sumar mis totales…" appears nowhere; it exists only in F4 (no cohort). | Add G6 Tus datos, H1b "Activa tu prueba con tu WhatsApp", and the opt-in row under F2/F3's provenance link. |
| R4-13 | A4, G4 | medium | The battery step detects one setting and fixes a different one, then promises success. | A4: "Tu teléfono tiene **restricciones de batería activas** para Kompara. Activa **Inicio automático** y listo." `isBackgroundRestricted()` reports Android's background restriction, not MIUI autostart. G4: "**Batería sin restricciones** · Xiaomi · **restricción detectada** · Revisar" (the label reads as the healthy state). | A4: "Tu teléfono está limitando a Kompara en segundo plano. Quita la restricción de batería." Deep-link to that setting, and put autostart in a second, optional step of Lector › Batería. G4 row: "Batería: restringida · Revisar". |
| R4-14 | A6 | medium | The privacy claim about the phone number is false by the design's own uses. | "Usamos tu número **solo** para enviarte el código por WhatsApp." The number is the account identity (backup), the trial's anti-abuse key (spec §8 "tied to the verified WhatsApp number") and the referral key. | "Tu número es tu cuenta: lo usamos para enviarte el código, guardar tus totales y tu prueba. No lo mostramos a otros conductores." |
| R4-15 | E3 | medium | The month in progress claims "…y lo del coche" while the current week's share hasn't posted. | E3: "Te quedan en octubre **$13,920** … Después de gasolina, desgaste **y lo del coche**", which equals $3,080 + $4,620 + $4,980 + today's **$1,240** (no car share for 19 oct). | Post the car cost daily within the month view by the same days rule ($214/día) or label the line "Lo del coche de esta semana se resta al cerrarla". |
| R4-16 | A1 | low | New vocabulary for the floors. | "Amarillo: entre tus **dos límites**. Rojo: abajo de tu **límite rojo**." vs §4 "tu mínimo" and G2 "Rojo por debajo de". | "Amarillo: entre tu mínimo y tu rojo. Rojo: abajo de tu rojo." |
| R4-17 | H5 | low | Hours vocabulary drift (it is also evidence for R4-01). | "**Horas en línea** 26 h" vs §4 "**en turno** (online), defined once". | Label it as the source's own measure: "Horas en línea (según Uber) 26 h". |
| R4-18 | D2 | low | Conditional tense for a trip the driver took. | "**Te quedarían** $114" next to "La tomaste". | "Te quedan aprox. $114" (taken); keep "Te quedarían" only for "Se fue sin tomar". |
| R4-19 | B2 vs B4 | low | The empty-today hero has two labels. | B2: "**Te quedan hoy** · Aún no hay viajes". B4: "**Hoy** · Aún no hay viajes". | One label: "Te quedan hoy · Aún no hay viajes". |
| R4-20 | H3 | low | The share card mixes scopes and drops the basis. | "Top 22 % por hora en Uber · CDMX" + "**Mejor día · viernes**" (both apps) + "Viajes en Uber 34"; no "antes de gastos". | "Mejor día en Uber · viernes", and append "· antes de gastos" (or switch to "por viaje" per R4-01). |
| R4-21 | F4 vs G1, F2 | low | The golden fixture isn't one driver. | F4: "Aún juntamos conductores de **DiDi en Mérida**" for a driver whose city is "Ciudad: CDMX" (G1) and who is ranked in CDMX (F2). | Make F4 "DiDi en CDMX" (no cohort for DiDi in CDMX), or caption it as a second fixture. |
| R4-22 | C1–C5; spec §6 | low | The chip widened to "~168–184dp" to fit a constant "aprox." on every offer. | Row 2 always reads "neto aprox. · $285/h". | Put "≈" in row 1 ("≈ $9.49 /km") and keep "neto · $285/h" in row 2. That's the same honesty in about 30dp less over the host card. |
| R4-23 | A3 | low | The deep link is promised unconditionally. | "Te llevamos **directo** al interruptor de Kompara." Spec §7 says the link must be verified per OEM. | "Te llevamos a los ajustes de accesibilidad." If the link lands on the list, show the third step "Busca Kompara". |
| R4-24 | E2 vs E3 | low | Copy-pasted figures in a fixture claimed to be unique. | E2 (17 – 23 ago): "$4,620 · **58 viajes**"; E3 (5 – 11 oct): "58 viajes · $4,620". | Generate E2 from the fixture's August week. |
| R4-25 | H6 vs spec §8 | low | The week-close notification carries a Premium invite with no "first week only" rule visible. | "Tu semana está lista · … y, si quieres, **probar Premium 14 días gratis**." Spec §8: "The **first** week-close notification invites it". | Invite only on the first eligible week-close; later weeks say "Toca para verla." |

**Round 3 defects not actually fixed:**

| R3 id | Status |
|---|---|
| D20 (gris vs sin color) | Not fixed (R4-06). |
| D01 (false demo math) | Fixed in the mockups but still in the spec text (R4-05). |
| D08 (overlay editor) | Removed from the product but still on the spec's IA map (R4-05). |
| D14 (IMSS names) | Fixed in the mockups but not in the spec map or heading (R4-05). |
| D15 (>3 numbers) | Partly fixed (R4-10). |
| D25 (missing states) | Partly fixed (R4-12). |
| D23 (standing loses its metric) | Fixed on H3 but not on E1 (R4-08). |
| D16 (hours) | Defined, but per-app hours are now the critical defect (R4-01). |

**New defects introduced by the fixes:** R4-01 (per-app hours, created by the gross-vs-gross rebuild), R4-02, R4-03, R4-09 (fixture inconsistencies), R4-11 (reconciliation), R4-13 (detected-restriction copy), R4-14 (number-use claim), R4-16 ("límite"), R4-22 ("aprox." widening the chip).

---

## 4. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo $754 limpios y la última verde la agarré."
- **Ganancias (E1):** "La semana pasada me quedaron $4,980 ya con la renta." Under G5's own start date, that's $1,500 too low (R4-03).
- **Comparar (F1, default "Tus apps"):** "DiDi me dejó $1.20 más por kilómetro que Uber."
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo: anda entre mi mínimo y mi rojo, piénsale."
- **Chip rojo (C3):** "Rojo: $2.20 el kilómetro, no llega."
- **Chip gris (C4 / C5):** "Gris: no la pudo leer, decido yo." In C5 a tired driver may read "Gris… pero dice $9.49, entonces sí", because the uncoloured rate is the only number on the chip (R4-06).

---

## 5. The one change that would raise the score the most

**Make the golden fixture executable and the single source for the spec as well as the mockups, and add *source* and *basis* to every number it emits.** That means one machine-readable driver: one city, one week plus the October weeks, every offer with its timestamp, app, shift and reader state, and the car cost with its start date. It runs through the real engine, period folds and IMSS rule, and generates both the mockup values and the spec's example strings. Each value carries:
- **basis** (net / before costs / before or after the car)
- **hours source** (reader "en turno" vs the app's statement)
- **period**

A CI diff fails when two screens show the same period differently or when a comparison pairs different sources.

**What it fixes:** R4-02, R4-03, R4-05, R4-09, R4-15, R4-21, R4-24. It also *forces* the R4-01 decision, because a per-app "$/h" would carry hours-source "reader-split" next to a population with source "statement", and the check would refuse to pair them.

**Score effect:** numbers 7 → 8 and consistency 7 → 8, to about 65. The last three points to 68 have to come from evidence, not documents: the 1-second chip test and a comprehension test on "te quedan / neto / gris / en turno" (copy 9), and median install→preparado ≤ 75 s on real Xiaomi and Samsung phones (time-to-value 9).

---

## 6. Positions I still hold against the synthesis

1. **"neto aprox." on every chip (§6) is the wrong place for "aprox."**
   - The uncertainty is in the cost profile, not in the offer's arithmetic, and it is already disclosed where costs live ("Con costos típicos", "Cómo se calculó").
   - On the chip it is a constant word that widened the overlay to 184dp over someone else's offer card. That's the occlusion argument we used against Codex's 168dp three-line chip in Round 2.
   - Use "≈" in the hero, or drop "aprox." once costs are confirmed.
2. **Inert tap (F3) is now coherent, since the editor is gone, but it is untested.**
   - The question the colour raises ("¿por qué amarillo?") still has no answer until after the trip.
   - Add a tap → three-line "por qué" arm to the R1 1-second test, scored on decision accuracy and on accidental-tap rate, before locking "drag only" for good.
   - Everything else I argued in Round 3 was adopted: the editor was removed, costs come after the permission, the city is proposed visibly and the C6 pill is bounded.
