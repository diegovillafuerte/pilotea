# Run 8 — the bounded readiness run (Round 23b kit), 2026-10-28

Instrument corrected per Codex `311` / Opus `312`: personas match the fixture (Uber and DiDi, mostly Uber; car cost entered, $1,500/week); framing sentence after task 8; no instruction to invent difficulty; task 2 shows only the screenshot (no text) and asks «¿qué te dice esta oferta?» then «¿la tomarías?»; task 3 asks «con lo que te deja cada una por km»; task 7 «Empiezas tu turno temprano…». Not strictly comparable to runs 5–7.

```
6 judges · logs from 6
| task | self success (yes/partial/no) | reached the answer screen (log) | mean taps (log) | mean misses (log) | mean time (s) | mean SEQ |
|---|---|---|---|---|---|---|
| First day setup | 6/0/0 | 6/6 | 4.5 | 0.0 | 36 | 5.67 |
| Hoy: kept today + reader | 6/0/0 | 6/6 | 0.0 | 0.0 | 8 | 6.83 |
| Offer: worth it? | 6/0/0 | 6/6 | 0.0 | 0.0 | 11 | 6.33 |
| Every offer today | 6/0/0 | 6/6 | 1.0 | 0.0 | 21 | 6.33 |
| Last week after car | 6/0/0 | 6/6 | 2.3 | 0.0 | 25 | 6.17 |
| Which app paid more | 6/0/0 | 6/6 | 3.0 | 0.0 | 21 | 6.33 |
| Change tu mínimo | 6/0/0 | 6/6 | 4.0 | 0.0 | 29 | 6.17 |
| Overlay stopped | 6/0/0 | 6/6 | 4.5 | 0.0 | 46 | 5.67 |
| Last trip detail | 6/0/0 | 6/6 | 1.0 | 0.0 | 23 | 6.00 |

mean SEQ 6.17 · ease vs Uber 7.5/10 · understand every screen 7.5/10 · lowest task 5.67
matched task 1: Kompara 6.83 vs Uber (self-estimated) 5.17 → diff +1.67
matched task 4: Kompara 6.17 vs Uber (self-estimated) 4.17 → diff +2.00
matched task 9: Kompara 6.00 vs Uber (self-estimated) 4.50 → diff +1.50
GATE (mean >= 6.0, vs Uber >= 7.5, no task < 5.5): PASS
PLAN RULE: 1 overall 6.17 → PASS · 2 matched vs Uber → PASS · 3 per-task flags: none · 5 money consistency → FAIL — Hoy and Ganancias at 14:40 on 19 oct show Llevas hoy $548 (6 trips), but the next-morning card says Ayer, lunes 19 oct, 

--- judge 1 (opus) · vs Uber 8 · understood 8 · install: Yes, because it tells me what I really keep after my $1,500 car payment and the colours are readable at a glance, though the 'control total' permission and the 
- Onboarding: 'Enciende Kompara en Accesibilidad' (and the consent screen before it) | '¿Permitir que Kompara tenga control total de tu dispositivo?' plus 'Uber y DiDi podrían no permitir apps como esta' frightens a distrustful, low-comfort driver; the real Android Settings step is the hardest part | fix: Show pictures of the real Android screens (where 'Apps descargadas' is, what the switch looks like) and add 'lo puedes apagar cuando quieras'
- Hoy and Ganancias main figure ('Llevas hoy · aprox. $548', 'Te quedaron esa semana · aprox. $4,980') | Every money figure says 'aprox.' and the calculation is hidden behind an unlabeled arrow row, so a driver who distrusts numbers may never find the breakdown | fix: Label the row 'Ver cómo se calculó' or show earned − gas/wear − car directly under the big number
- Uber app when the semáforo is off | Only a tiny K in the status bar; nothing tells the driver the semáforo is off or what to do unless she thinks to open Kompara | fix: A clear persistent notification 'Semáforo apagado: toca para arreglar' or a small red chip over the trip app

--- judge 2 (sonnet) · vs Uber 8 · understood 8 · install: Yes, because it tells me before I accept whether an offer clears my own minimum and shows what I keep after gas, wear and the car, instead of just the gross far
- Trip detail sheet (tap on a taken offer) | The big $114 has its 'antes de lo del coche' qualifier in small grey text above it, while Hoy and Ganancias show figures that already include the car. Two bases for money, and the qualifier is easy to miss. | fix: Put the qualifier on the same line as the number, in bold ('$114 antes del coche').
- Hoy, the 'tu meta $8 · tu mínimo $6' line | Tapping it opens the Semáforo status screen, not the limits editor, so an extra hop; I expected to edit the limits directly from the line that shows them. | fix: Open the limits editor directly from that line, or add a 'Cambiar' link on it.
- Semáforo status screen versus the Hoy list | The status screen says 'Ofertas sin semáforo · ninguna hoy' while the Hoy list shows 'Sin lectura, 11:48, No pudimos leer la tarifa' for today; the counts disagree. | fix: Show '1 hoy' there and make it tappable to that offer.

--- judge 3 (opus) · vs Uber 8 · understood 8 · install: Yes: I pay $1,500 a week for the car and this tells me what I really keep, and the semáforo works with shapes and words so I don't need to tell red from green.
- Uber screen while the semáforo is off (task 7) | The only sign is a tiny K in the status bar; the warning is a notification from last night, so a driver can start a shift without noticing | fix: When Uber/DiDi opens and the permission is off, show a new heads-up alert: 'Semáforo apagado, toca para encenderlo'
- Hoy and Tu día offer lists | No Uber/DiDi tag on each offer; for a red-green colour-blind driver the small colour tiles mean nothing, and only the words and the top/bottom fill position help | fix: Add an Uber/DiDi tag to each row and use the ▲◆▼ shapes in the list icons, as on the semáforo
- Trip detail and Por app sheet vs Hoy/Ganancias | 'Te deja' sometimes means before the car cost ($114, per-app $/km) and sometimes after it ($548, $4,980); it's labelled only in small grey text, so I read it twice | fix: Use one fixed label for each base (e.g. 'sin coche' / 'con coche') in the same visible place on every screen

--- judge 4 (sonnet) · vs Uber 7 · understood 7 · install: Yes, because it shows what I actually keep after gasolina, desgaste and the car, which Uber's Ganancias does not, though I would first ask someone I trust about
- Uber map screen when the semáforo is off (Task 7) | Nothing on screen says why the semáforo is missing; only a tiny K in the status bar hints at it, easy to miss with reading glasses. | fix: One vibration and sound when it turns off, plus a pinned notification 'Semáforo apagado, toca para encenderlo'.
- Semáforo status screen vs Hoy offer list | The Semáforo screen says 'Ofertas sin semáforo · ninguna hoy' while Hoy lists a 'Sin lectura' offer at 11:48 today. | fix: Show the real count ('1 hoy') and link it to that offer.
- Many screens (offer rows, trip sheet, Por app sheet) | Second-line and note text is small and light grey and hard to read with glasses. | fix: Darker, larger secondary text (at least phone body size) or respect Android's large-text setting.

--- judge 5 (sonnet) · vs Uber 8 · understood 8 · install: Yes, because it tells me what an offer leaves me per km before I accept and what I kept after the car.
- Semáforo status page (from Hoy) vs the Hoy offers list | Says 'Ofertas sin semáforo · ninguna hoy' while the day list has a 'Sin lectura' offer at 11:48 | fix: Count unread offers in that line (or reword it) so both screens agree
- Last-trip detail sheet (tap La tomaste) | Headline '$114 · Te deja antes de lo del coche' makes a driver who already entered the car cost doubt whether it is the real number | fix: Lead with '$114 de este viaje. El coche ($214) se resta por día.'
- First screens: 'Lo que hace Kompara' and the permission steps | Dense jargon (conteos de lectura, aporte al promedio) and the Android prompt 'control total de tu dispositivo' sounds scary | fix: 2-3 plain lines ending with 'Solo lee la oferta. Nunca toca nada.', with the reassurance placed next to the scary phrase

--- judge 6 (haiku) · vs Uber 6 · understood 6 · install: Yes, I would install it to test on a real shift, because the net-per-km math matches the trip detail ($148 less $34 gas and wear gives $114 for 12 km), though t
- Ganancias > Semana anterior > Por app sheet | Per-app $/km (DiDi 10.34, Uber 9.13) excludes the car, while the weekly total above says it subtracts gas, wear and car. The exclusion is only a grey footnote. | fix: Label per-km figures as before car (antes del coche) and use the same wording as the weekly total.
- Uber overlay chip ($7.16 /km neto, Cumple tu mínimo) | Neto does not say what was subtracted; the user had to open a trip detail to learn gas and wear are included. | fix: Add a short caption on the chip or a one-time tip on the first offer, e.g. neto: ya restamos gasolina y desgaste.
- Notification Se apagó el semáforo and the Uber screen with no chip | When the Accessibility permission is lost, the only cue on Uber is a tiny K in the status bar; the fix is only reachable by pulling down the shade. | fix: Make the permission-loss notification high priority with a Volver a dar el permiso action button directly in it.
```

## Opus's readiness criteria (`312` §6)

1. 54/54 answer screens, misses ≤ 0.2 — **met** (54/54, 0.0). 2. No task under 5.5 — **met** (lowest 5.67). 3. No defect in the top three of ≥ 4 judges in both runs 7 and 8 — **not met**: the trip's money vs the day's (run 7: j1 j2 j3 j5; run 8: j2 j3 j5 j6 — now about the qualifier's placement, small grey text away from the $114). 4. No money contradiction true of the fixture — **met** (one report, cross-moment $548 at 14:40 vs $1,026 at day's end, consistent). 5. Kompara ≥ the judges' Uber estimates on tasks 1, 4, 9 — **met** (+1.67 / +2.00 / +1.50). 6. B1's $548 read as after the car by 6/6 — **met**.

## Full judge reports

### Judge 1 (opus)

**Session report: Rosa, 52, rents her car (pays $1,500 a week), mostly Uber, low comfort with apps**

Total taps: 22, with no wrong taps (plus one scroll). I finished all 9 tasks and gave up on none.

**Task 8 (first day, get the semáforo working)**
- **Path:** Empezar → Acepto → Abrir ajustes → (imagined: find Kompara, turn it on, Permitir) Ya lo activé → Abrir Uber para probar. The green semáforo showed on the Uber offer: $9.49/km neto, "Arriba de tu meta".
- **Taps:** 5. **Success:** yes. **Ease:** 5.
- **Where I hesitated:**
  - The consent screen says "Uber y DiDi … podrían no permitir apps como esta". That worried me.
  - The warning "¿Permitir que Kompara tenga control total de tu dispositivo?" is scary. The app had warned me it was coming, which helped.
  - The imagined Android Settings step is the hardest part for someone like me.
  - On the "Listo" screen I wondered whether I also had to use "Elegir mi ciudad" or the "Usamos costos típicos $2.84" line. I left them alone.

**Task 1 (how much today, is it reading offers)**
- **Path:** none, everything was on Hoy. **Taps:** 0. **Success:** yes. **Ease:** 6.
- **Answer:** about $548 today from 6 trips, after gas, wear and car. Green dot with "Semáforo funcionando".
- **Where I hesitated:** "aprox." makes me distrust the number. The screen doesn't show me how it was worked out.

**Task 2 (one glance at an Uber offer)**
- **Path:** one glance. **Taps:** 0. **Success:** yes. **Ease:** 7.
- **Answer:** yellow diamond, $7.16/km neto, "Cumple tu mínimo". That is above my $6 minimum and below my $8 goal.
- **Would I take it?** Yes. It covers my minimum, so in the middle of the shift it's acceptable, though not a great trip.

**Task 3 (all of today's offers with $/km)**
- **Path:** Ver todas las ofertas de hoy, then scroll. **Taps:** 1 (plus one scroll). **Success:** yes. **Ease:** 7.
- **Answer:** "Ofertas de hoy · 10".
  - Taken: 14:32 $9.49, 11:20 $9.81, 10:41 $9.41, 10:02 $10.46, 9:24 $10.75, 8:15 $10.00.
  - Left untaken: 12:10 $2.20, 9:51 $9.16, 8:40 $9.20.
  - Not read: 11:48, "No pudimos leer la tarifa".
- **Note:** no row says whether the offer came from Uber or DiDi.

**Task 4 (last week, after all costs including the car)**
- **Path:** Ganancias → Semana anterior → tapped the row "ya restamos gasolina, desgaste y coche" to check. **Taps:** 3. **Success:** yes. **Ease:** 6.
- **Answer:** about $4,980. The breakdown is $8,400 earned (Uber $5,028, DiDi $3,372), minus $1,920 for gas and wear (675.8 km), minus $1,500 for the car.
- **Where I hesitated:** I wanted proof that my $1,500 had been subtracted. The row doesn't say it opens the breakdown; I only guessed from the arrow.

**Task 5 (which app paid more per km last week)**
- **Path:** Ganancias → Semana anterior → Por app. **Taps:** 3. **Success:** yes. **Ease:** 7.
- **Answer:** DiDi, $10.34/km over 28 trips, against Uber's $9.13/km over 34 trips. That's $1.21 more per km.
- **Note:** this surprised me because I drive mostly Uber. The app adds "Manejaste cada app a distintas horas".

**Task 6 (set the minimum to $6.50/km)**
- **Path:** tapped "tu meta $8 · tu mínimo $6" on Hoy → Tu meta y tu mínimo → the + next to the minimum (once) → Guardar. **Taps:** 4. **Success:** yes. **Ease:** 7.
- **Answer:** confirmed by the message «Guardado: tu mínimo ahora es $6.50/km».

**Task 7 (no semáforo early in the shift)**
- **Path:** in Uber the only sign was a small "K" at the top → Abrir Kompara → Volver a dar el permiso → Abrir ajustes → Ya lo activé → Que no se vuelva a apagar. **Taps:** 5. **Success:** yes. **Ease:** 6.
- **Why:** "Semáforo apagado · desde ayer 20:31 / Falta el permiso de Accesibilidad". Android sometimes turns it off, for example to save battery.
- **What to do:** give the permission again (Accessibility → Kompara → switch on → Permitir). So it doesn't happen again: Aplicaciones → Kompara → Batería → Sin restricciones.
- **Where I hesitated:** the Uber screen gives no clear warning, and I didn't know what the tiny K meant. The battery steps are the kind of settings I avoid.

**Task 9 (what my last trip left me)**
- **Path:** tapped "La tomaste" (14:32) on Hoy. **Taps:** 1. **Success:** yes. **Ease:** 6.
- **Answer:** about $114 before the car cost: $148 fare minus $34 for gas and wear (12 km × $2.84). That works out to $9.49/km and $285/h over 24 min. The car comes off per day ($214), not per trip.
- **Where I hesitated:** I had to read "antes de lo del coche" twice. The list row only shows $/km, not pesos.

**(a) Problems I actually hit**
1. **Accessibility screen in onboarding.** "Control total de tu dispositivo", plus "Uber y DiDi podrían no permitir apps como esta" on the screen before, is frightening for someone who distrusts apps. The Settings step itself is hard. **Fix:** show pictures of the real Android screens (where "Apps descargadas" is and what the switch looks like), and add a line saying "lo puedes apagar cuando quieras".
2. **Every money figure says "aprox." and the explanation is hidden.** The working is only reachable through an unlabeled arrow row ("ya restamos gasolina, desgaste y coche"). **Fix:** label it "Ver cómo se calculó", or show the three lines (earned − gas/wear − car) right under the big number.
3. **The semáforo is off and Uber shows nothing clear.** I only saw a tiny K in the status bar. If I hadn't thought of opening Kompara myself, I wouldn't have known. **Fix:** a clear, persistent notification such as "Semáforo apagado, toca para arreglar", or a small red chip over the trip app.

**(b) Ease compared with Uber Driver:** 8/10.

**(c) Would I understand every screen without help:** 8/10. "Neto", "antes de lo del coche" and "Cuadrar con tu resumen de Uber" need a second read.

**(d) The same tasks in Uber Driver:** today's earnings 6, last week's earnings 5, last trip's detail 4.

**(e) Would I install it?** Yes, because it finally tells me what I keep after my $1,500 car payment and the colours are readable at a glance. The "control total" permission and the warning that Uber might not allow it still make me nervous.

**(f) Contradicting money figures:** no. I checked the sums:
- $5,028 + $3,372 = $8,400.
- $8,400 − $1,920 − $1,500 = $4,980.
- $10.34 − $9.13 = $1.21.
- $148 − $34 = $114.
- $214 a day × 7 ≈ $1,500.
- Hoy $548 equals the current week's $548.
- On the 19th I had $548 at 14:40, and the next morning the app said that day ended at $1,026. Those fit, because the day kept going.

The session log is at /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk8/j1/log.json.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (imagined: find Kompara, turn on switch, Permitir) Ya lo activé > Abrir Uber para probar (green semáforo $9.49/km on the Uber offer)","taps":5,"success":"yes","answer":"Semáforo working: Hoy shows 'Semáforo preparado / permiso listo' and the test Uber offer showed the green chip $9.49/km neto 'Arriba de tu meta'","ease":5,"stuck":"Consent text 'Uber y DiDi podrían no permitir apps como esta' worried me; the 'control total de tu dispositivo' warning is scary even though the app explained it; the imagined Android Settings step is the hard part for a low-comfort user; wondered whether 'Elegir mi ciudad' / 'costos típicos $2.84' were required"},"1":{"path":"None, read Hoy","taps":0,"success":"yes","answer":"About $548 today, 6 trips, after gas, wear and car; green dot 'Semáforo funcionando'","ease":6,"stuck":"'aprox.' makes me distrust the figure; no visible breakdown on the screen"},"2":{"path":"One glance at the Uber offer","taps":0,"success":"yes","answer":"Yellow diamond $7.16/km neto 'Cumple tu mínimo' (above my $6 minimum, below my $8 goal). Yes I'd take it: covers my minimum, OK mid-shift though not great","ease":7,"stuck":"None"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: taken 14:32 $9.49, 11:20 $9.81, 10:41 $9.41, 10:02 $10.46, 9:24 $10.75, 8:15 $10.00; not taken 12:10 $2.20, 9:51 $9.16, 8:40 $9.20; not read 11:48","ease":7,"stuck":"No hesitation; the rows don't say if each offer was Uber or DiDi"},"4":{"path":"Ganancias > Semana anterior > tapped 'ya restamos gasolina, desgaste y coche' row (breakdown sheet)","taps":3,"success":"yes","answer":"About $4,980 (12–18 oct): $8,400 earned (Uber $5,028, DiDi $3,372) − $1,920 gas and wear − $1,500 car","ease":6,"stuck":"Wanted proof my $1,500 was subtracted; the row doesn't say it opens the calculation, I guessed from the arrow"},"5":{"path":"Ganancias > Semana anterior > Por app","taps":3,"success":"yes","answer":"DiDi: $10.34/km (28 trips) vs Uber $9.13/km (34 trips), DiDi $1.21 more per km","ease":7,"stuck":"None; surprised because I drive mostly Uber"},"6":{"path":"Hoy: tapped 'tu meta $8 · tu mínimo $6' > Tu meta y tu mínimo > + on minimum > Guardar","taps":4,"success":"yes","answer":"Saved: «Guardado: tu mínimo ahora es $6.50/km»","ease":7,"stuck":"None"},"7":{"path":"Uber screen (no semáforo, only small K at top) > Abrir Kompara > Volver a dar el permiso > Abrir ajustes > Ya lo activé > Que no se vuelva a apagar","taps":5,"success":"yes","answer":"Android turned off Kompara's Accessibility permission (e.g. battery saving) since yesterday 20:31. Fix: give the permission again (Accessibility > Kompara > switch > Permitir) and set Aplicaciones > Kompara > Batería > Sin restricciones so it doesn't turn off again","ease":6,"stuck":"In Uber nothing clear warned me, only a tiny K I didn't understand; I opened Kompara on my own idea. Battery settings steps are intimidating"},"9":{"path":"Hoy: tapped 'La tomaste' (14:32)","taps":1,"success":"yes","answer":"About $114 before the car cost ($148 fare − $34 gas and wear, 12 km × $2.84), $9.49/km, 24 min; the car is subtracted per day ($214)","ease":6,"stuck":"Had to reread 'antes de lo del coche'; the list row shows only $/km, not pesos"}},"problems":[{"screen":"Onboarding: 'Enciende Kompara en Accesibilidad' (and the consent screen before it)","problem":"'¿Permitir que Kompara tenga control total de tu dispositivo?' plus 'Uber y DiDi podrían no permitir apps como esta' frightens a distrustful, low-comfort driver; the real Android Settings step is the hardest part","fix":"Show pictures of the real Android screens (where 'Apps descargadas' is, what the switch looks like) and add 'lo puedes apagar cuando quieras'"},{"screen":"Hoy and Ganancias main figure ('Llevas hoy · aprox. $548', 'Te quedaron esa semana · aprox. $4,980')","problem":"Every money figure says 'aprox.' and the calculation is hidden behind an unlabeled arrow row, so a driver who distrusts numbers may never find the breakdown","fix":"Label the row 'Ver cómo se calculó' or show earned − gas/wear − car directly under the big number"},{"screen":"Uber app when the semáforo is off","problem":"Only a tiny K in the status bar; nothing tells the driver the semáforo is off or what to do unless she thinks to open Kompara","fix":"A clear persistent notification 'Semáforo apagado: toca para arreglar' or a small red chip over the trip app"}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes, because it tells me what I really keep after my $1,500 car payment and the colours are readable at a glance, though the 'control total' permission and the warning that Uber might not allow it make me nervous.","money_inconsistency":{"any":false,"which":"None. Checked: 5,028+3,372=8,400; 8,400−1,920−1,500=4,980; 10.34−9.13=1.21; 148−34=114; $214/day ≈ $1,500/week; Hoy $548 = current week $548; $548 at 14:40 on 19 oct vs $1,026 for the whole day is consistent"}}}
```

### Judge 2 (sonnet)

## Session report: Luis, 28, Uber/DiDi, car cost already entered

I did Task 8 first, then 1 to 7, then 9. Everything went through the tool. I opened only the screenshots and the two Uber reference images. A scroll or a swipe counts as one tap.

**Task 8 (first day, make the semáforo work)**
- Path: Empezar, Acepto, Abrir ajustes, then "Ya lo activé" on the Android placeholder screen (4 taps). It ended on Hoy: "Semáforo preparado, permiso listo".
- Success: yes.
- Answer: accept, then turn on Kompara in Accesibilidad.
- Hesitation: none. I skimmed the privacy screen and went straight to Acepto.
- The "control total" example prompt looked a bit scary, but the text next to it said Kompara never touches buttons or accepts trips. The three numbered steps were clear.
- Ease: 6. The imagined Android step is the part I can't judge.

**Task 1 (today's earnings, is it reading?)**
- Path: none, the answer was on the first screen (0 taps).
- Success: yes.
- Answer: $548 so far today, about, with gas, wear and car already subtracted, from 6 trips. "Semáforo funcionando", with goal $8 and minimum $6.
- Hesitation: none.
- Ease: 7.

**Task 2 (glance at the Uber offer)**
- Path: one look, 0 taps.
- Success: yes.
- Answer: yellow, "$7.16/km neto, Cumple tu mínimo". That is above my $6 minimum and below my $8 goal.
- I would take it, since it clears my minimum. It is not a great one, so if I were busy I'd still take it.
- Hesitation: none.
- Ease: 6. I had to remember what yellow means against my two limits, but the words on the chip said it.

**Task 3 (all of today's offers with $/km)**
- Path: "Ver todas las ofertas de hoy", then scroll down (2 taps).
- Success: yes.
- Answer: 10 offers today:

| Time | Result | $/km |
|---|---|---|
| 14:32 | taken | 9.49 |
| 12:10 | skipped, below minimum | 2.20 |
| 11:48 | no reading | none |
| 11:20 | taken | 9.81 |
| 10:41 | taken | 9.41 |
| 10:02 | taken | 10.46 |
| 9:51 | skipped | 9.16 |
| 9:24 | taken | 10.75 |
| 8:40 | skipped | 9.20 |
| 8:15 | taken | 10.00 |

- Hesitation: I scrolled to confirm the list really had 10 rows.
- Ease: 6.

**Task 4 (last week's net including the car)**
- Path: Ganancias tab, then "Semana anterior" (2 taps).
- Success: yes.
- Answer: $4,980 for 12 to 18 Oct, about, 62 trips, "ya restamos gasolina, desgaste y coche". Friday was the best day at $1,166.
- Hesitation: none. The tab is where Uber puts earnings.
- Ease: 6.

**Task 5 (which app paid more per km last week)**
- Path: Ganancias, Semana anterior, "Por app" (3 taps).
- Success: yes.
- Answer: DiDi, $10.34/km over 28 trips, against Uber at $9.13/km over 34 trips. DiDi paid $1.21 more per km.
- Hesitation: none. The row's subtitle already hints at the answer.
- Ease: 6.

**Task 6 (set the minimum to $6.50)**
- Path: the "tu meta $8 · tu mínimo $6" line on Hoy, "Tu meta y tu mínimo", "+" on the minimum row (tapxy), Guardar (4 taps).
- Success: yes. The message said "Guardado: tu mínimo ahora es $6.50/km", and Hoy then showed $6.50.
- Hesitation: the first tap on the limits line did not open the editor. It opened a Semáforo status screen, and I needed one more tap on "Tu meta y tu mínimo".
- The "+" icons have no text, so I had to look at the screenshot to pick the minimum row's button.
- One "+" gave exactly $6.50.
- Ease: 6.

**Task 7 (no semáforo on offers: why, and what to do)**
- Path: swipe down from the top, tap the "Se apagó el semáforo" notification, "Volver a dar el permiso", "Abrir ajustes", "Ya lo activé" (5 taps).
- Success: yes. It ended on "Semáforo preparado, permiso listo".
- Answer: Android turned off the Accesibilidad permission, the notification says since yesterday 20:31, for example when saving battery. The fix is to turn Kompara back on in Accesibilidad. There is also a "Que no se vuelva a apagar" button, which I did not open.
- Hesitation: the Uber screen showed nothing wrong, so I guessed the notification shade from the "K" in the status bar. That was lucky. The notification explained everything and tapping it went to the fix.
- Ease: 6.

**Task 9 (what my last trip left me)**
- Path: tap "La tomaste", the 14:32 row on Hoy (1 tap).
- Success: yes.
- Answer: about $114 before the car cost, on a $148 fare minus $34 for gas and wear. That is $9.49/km and $285/h over 24 minutes.
- Hesitation: the big $114 sits under a small grey line, "Te deja antes de lo del coche". Hoy says $548 is "ya con lo del coche". Two money bases are on screen, and a skimmer could read $114 as what he kept.
- The line "Se resta por día ($214), no por viaje" explains it, but I had to read to find it.
- Ease: 6.

### (a) Usability problems I actually hit
1. **Trip detail sheet, task 9.** The big "$114" has its "antes de lo del coche" qualifier in small grey text above it, while Hoy and Ganancias show net figures that already include the car.
   - Problem: two different bases for money, and the qualifier is easy to miss.
   - Fix: put the qualifier on the same line as the number ("$114 antes del coche"), in bold.
2. **Hoy, the "tu meta $8 · tu mínimo $6" line, task 6.** Tapping it opens the Semáforo status screen, not the limits editor, so there is an extra hop.
   - Problem: I expected to edit my limits directly from the line that shows them.
   - Fix: open the limits editor directly from that line, or add a "Cambiar" link on it.
3. **Semáforo status screen versus the Hoy list.** The status screen says "Ofertas sin semáforo · ninguna hoy". The Hoy list shows "Sin lectura, 11:48, No pudimos leer la tarifa" for today.
   - Problem: the counts disagree.
   - Fix: show "1 hoy" there and make it tappable to that offer.

### (b) Ease compared with the Uber Driver app
8/10. Uber's screens are simpler: the home screen is only a map and a GO button, and Earnings is a weekly total with a bar chart. Kompara gives more, for example net per km and what the car costs, in about the same number of taps.

### (c) Would I understand every screen without help?
8/10. I'd need a moment on "aprox.", on the two bases for money, and on the "control total" prompt.

### (d) The same tasks in my Uber Driver app
- Today's earnings: 5. It is a weekly total with a chart, and I'd have to tap a day.
- Last week's earnings: 4. It would show only gross, with no gas or car, so I'd work out the net myself.
- Last trip's detail: 5.

### (e) Would I install it?
Yes. It tells me before I accept whether an offer clears my own minimum, and shows what I keep after gas, wear and the car, instead of just the gross fare.

### (f) Did any two money figures contradict each other?
No. The figures I checked fit together: $214 a day for the car is about $1,500 a week, the 6 taken offers match the "6 viajes", and $114 matches $148 minus $34. The only contradiction I found is the offer count in problem 3, which is not a money figure.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Ya lo activé (Android placeholder)","taps":4,"success":"yes","answer":"Accept, then enable Kompara in Accesibilidad; ended on Hoy 'Semáforo preparado, permiso listo'","ease":6,"stuck":"No stuck point. I skimmed the privacy text, and the 'control total' example prompt looked a bit scary. The imagined Android step is unverifiable."},"1":{"path":"none, answer on first screen","taps":0,"success":"yes","answer":"$548 so far today (aprox., after gas, wear and car, 6 trips); semáforo 'funcionando', meta $8 / mínimo $6","ease":7,"stuck":"none"},"2":{"path":"one glance at the Uber screen","taps":0,"success":"yes","answer":"Yellow: $7.16/km neto, 'Cumple tu mínimo' (above $6 minimum, below $8 goal). I would take it because it clears my minimum; not a great one.","ease":6,"stuck":"I had to recall what yellow means against my two limits; the text on the chip answered it."},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":2,"success":"yes","answer":"10 offers: 14:32 9.49 taken; 12:10 2.20 skipped (below minimum); 11:48 no reading; 11:20 9.81 taken; 10:41 9.41 taken; 10:02 10.46 taken; 9:51 9.16 skipped; 9:24 10.75 taken; 8:40 9.20 skipped; 8:15 10.00 taken","ease":6,"stuck":"Scrolled to confirm the list really had 10 rows."},"4":{"path":"Ganancias tab > Semana anterior","taps":2,"success":"yes","answer":"$4,980 aprox. for 12-18 oct, 62 trips, after gas, wear and car","ease":6,"stuck":"none"},"5":{"path":"Ganancias > Semana anterior > Por app","taps":3,"success":"yes","answer":"DiDi paid more per km: $10.34/km (28 trips) vs Uber $9.13/km (34 trips), +$1.21","ease":6,"stuck":"none"},"6":{"path":"tu meta/tu mínimo line on Hoy > Tu meta y tu mínimo > + on minimum (tapxy) > Guardar","taps":4,"success":"yes","answer":"Minimum set to $6.50/km; confirmed 'Guardado: tu mínimo ahora es $6.50/km', and Hoy shows $6.50","ease":6,"stuck":"Tapping the limits line on Hoy opened a Semáforo status screen, not the editor, so an extra tap. The '+' icons have no text, so I had to look at the screenshot to pick the minimum row's."},"7":{"path":"swipe-down-top > tap 'Se apagó el semáforo' notification > Volver a dar el permiso > Abrir ajustes > Ya lo activé","taps":5,"success":"yes","answer":"Android turned off the Accesibilidad permission (notification says since yesterday 20:31, e.g. battery saving); fix is to re-enable Kompara in Accesibilidad, then semáforo returns. 'Que no se vuelva a apagar' also exists but was not opened.","ease":6,"stuck":"The Uber screen showed no clue; I guessed the notification shade from the K in the status bar, which worked."},"9":{"path":"Hoy > tap 'La tomaste' (14:32)","taps":1,"success":"yes","answer":"About $114 before the car cost (fare $148 minus $34 gas and wear), $9.49/km, $285/h, 24 min","ease":6,"stuck":"The big $114 is qualified only by small grey 'antes de lo del coche', while Hoy figures are already after the car, so it is easy to misread as what I kept."}},"problems":[{"screen":"Trip detail sheet (tap on a taken offer)","problem":"The big $114 has its 'antes de lo del coche' qualifier in small grey text above it, while Hoy and Ganancias show figures that already include the car. Two bases for money, and the qualifier is easy to miss.","fix":"Put the qualifier on the same line as the number, in bold ('$114 antes del coche')."},{"screen":"Hoy, the 'tu meta $8 · tu mínimo $6' line","problem":"Tapping it opens the Semáforo status screen, not the limits editor, so an extra hop; I expected to edit the limits directly from the line that shows them.","fix":"Open the limits editor directly from that line, or add a 'Cambiar' link on it."},{"screen":"Semáforo status screen versus the Hoy list","problem":"The status screen says 'Ofertas sin semáforo · ninguna hoy' while the Hoy list shows 'Sin lectura, 11:48, No pudimos leer la tarifa' for today; the counts disagree.","fix":"Show '1 hoy' there and make it tappable to that offer."}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":5,"4":4,"9":5},"install":"Yes, because it tells me before I accept whether an offer clears my own minimum and shows what I keep after gas, wear and the car, instead of just the gross fare.","money_inconsistency":{"any":false,"which":"No money figures contradicted each other: $214/day for the car is about $1,500/week, 6 taken offers match '6 viajes', and $114 matches $148 minus $34. The only contradiction is an offer count, not a money figure: the Semáforo status screen says 'ninguna hoy' for offers without semáforo while Hoy lists a 'Sin lectura' offer at 11:48."}}
```

### Judge 3 (opus)

**Session report: Martha, 41. She rents her car, drives mostly Uber plus some DiDi, has entered her car cost of $1,500 a week, and cannot tell red from green.**

I worked only through the tool. Tasks ran in order: 8 first, then 1–7, then 9. Log saved at /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk8/j3/log.json

**Task 8: get the semáforo working on day one**
- **Path:** Empezar → Acepto → Abrir ajustes → (imagined in Android Settings: find Kompara, turn it on, accept «control total») → Ya lo activé → Abrir Uber para probar.
- **Taps:** 5. **Success:** yes. **Ease:** 6.
- **Result:** Hoy showed "Semáforo preparado · permiso listo". On the Uber test offer the chip appeared: ▲ $9.49/km neto, "Arriba de tu meta".
- **What helped:** the numbered steps, the "Apps instaladas / Apps descargadas" hint, and the preview of the scary «control total» dialog. With those, I'm confident I would manage the real Android step.
- **Hesitation:** the legend uses colour, but each line also has a shape (▲ ◆ ▼) and words, so I could follow it.

**Task 1: how much today, and is it reading my offers?**
- **Path:** none, it was all on Hoy. **Taps:** 0. **Success:** yes. **Ease:** 7.
- **Answer:** about $548 today, from 6 trips, with gas, wear and car already taken off. The header says "Semáforo funcionando", so yes, it is reading.
- I can't see the dot's colour, but the words are enough.

**Task 2: one glance at an Uber offer**
- **Taps:** 0. **Success:** yes. **Ease:** 7.
- **What it told me:** a diamond, $7.16/km neto, "Cumple tu mínimo". It's above my $6 minimum and below my $8 goal. The fare was $120.
- **Would I take it?** Yes, midday. It covers my minimum after costs. It isn't great, but it's worth it.
- The diamond shape and the words carry the meaning. The yellow also looks clearly different to me from the ▲ chip's colour.

**Task 3: all of today's offers, with what each leaves per km**
- **Path:** Ver todas las ofertas de hoy → scroll. **Taps:** 1 plus 1 scroll. **Success:** yes. **Ease:** 7.
- **Answer:** "Ofertas de hoy · 10", each with $/km:

| Time | Status | $/km |
|---|---|---|
| 14:32 | Taken | 9.49 |
| 12:10 | Not taken, below minimum | 2.20 |
| 11:48 | Not read | – |
| 11:20 | Taken | 9.81 |
| 10:41 | Taken | 9.41 |
| 10:02 | Taken | 10.46 |
| 9:51 | Not taken | 9.16 |
| 9:24 | Taken | 10.75 |
| 8:40 | Not taken | 9.20 |
| 8:15 | Taken | 10.00 |

- Six taken, which matches "6 viajes". The list doesn't say which offer was Uber and which was DiDi.

**Task 4: what I kept last week after all costs, car included**
- **Path:** Ganancias → Semana anterior → tapped the "62 viajes tomados" row to check.
- **Taps:** 3. **Success:** yes. **Ease:** 6.
- **Answer:** $4,980 for 12–18 Oct. The breakdown: $8,400 from 62 trips (Uber $5,028, DiDi $3,372), minus $1,920 gas and wear for 675.8 km, minus $1,500 car. The sum checks out.
- **Hesitation:** Ganancias opens on the current week, so I needed one extra tap. I tapped the breakdown only to confirm the car was included.

**Task 5: which app paid more per km last week**
- **Path:** Ganancias → Semana anterior → Por app. **Taps:** 3. **Success:** yes. **Ease:** 7.
- **Answer:** DiDi, $10.34/km over 28 trips, against Uber's $9.13/km over 34 trips. That's $1.21 more per km. The sheet says the car cost isn't included in these per-km figures.
- The answer was already hinted on the row ("DiDi te dejó $1.21 más por km").

**Task 6: change my minimum to $6.50/km**
- **Path:** the "tu meta $8 · tu mínimo $6" row on Hoy → Tu meta y tu mínimo → + on Tu mínimo → Guardar.
- **Taps:** 4. **Success:** yes. **Ease:** 7.
- **Confirmation:** «Guardado: tu mínimo ahora es $6.50/km». Hoy now shows tu mínimo $6.50.

**Task 7: early shift, no semáforo on my offers**
- **Path:** On the Uber map at 8:01 the only clue was a small "K" in the status bar. I pulled down the notifications → tapped "Se apagó el semáforo" → Volver a dar el permiso.
- **Taps:** 3. **Success:** yes. **Ease:** 5.
- **Why:** the Accessibility permission is off. Android sometimes switches it off, for example when saving battery. It has been off since 20:31 last night.
- **What to do:** tap "Volver a dar el permiso" and repeat the Settings steps. There is also "Que no se vuelva a apagar" so it doesn't happen again.
- **Hesitation:** nothing on the Uber screen said the semáforo was off. I had to think to check the notifications, and that notification was from last night.

**Task 9: what my last trip left me**
- **Path:** tapped the top "La tomaste 14:32" row on Hoy. **Taps:** 1. **Success:** yes. **Ease:** 6.
- **Answer:** about $114 before the car cost. That's the $148 fare minus $34 gas and wear (12 km × $2.84), which is $9.49/km or $285/h. The sheet also says the car is subtracted per day ($214), not per trip.
- **Hesitation:** briefly, because the row says "La tomaste" rather than "último viaje". It was the first row, so it was clear enough.

**End questions**

(a) Usability problems I actually hit:
1. **Uber screen while the semáforo is off (task 7).** The only sign is a tiny "K" in the status bar, and the warning sits in the notification shade from last night. I could easily start a whole shift without noticing. **Fix:** when Uber or DiDi opens and the permission is off, show a new heads-up alert: "Semáforo apagado, toca para encenderlo".
2. **Hoy and Tu día offer lists.** They don't show which app (Uber or DiDi) each offer came from. The small colour tiles mean nothing to me; I relied only on the words and on whether the fill sits at the top or bottom of the tile. **Fix:** add an Uber/DiDi tag to each row, and use the same ▲◆▼ shapes in the list icons as on the semáforo.
3. **Trip detail and the Por app sheet versus Hoy and Ganancias.** "Te deja" sometimes means before the car cost ($114 on the trip, the per-app $/km) and sometimes means after it ($548, $4,980). It's labelled, but only in small grey text, and I had to read it twice. **Fix:** use one fixed label for each base, such as "sin coche" and "con coche", in the same visible place on every screen.

(b) Ease compared with the Uber Driver app: 8/10.

(c) Would I understand every screen without help: 8/10.

(d) The same tasks in the Uber Driver app:
- Task 1, today's earnings: 6.
- Task 4, last week: 5. Uber only shows what it paid me; it never takes off gas or the car.
- Task 9, last trip's detail: 4.

(e) Yes, I'd install it. I pay $1,500 a week for the car and this tells me what I really keep, and the semáforo works with shapes and words, so I don't need to tell red from green.

(f) No, no two money figures contradicted each other. The sums check: $8,400 − $1,920 − $1,500 = $4,980, Uber $5,028 + DiDi $3,372 = $8,400, and $148 − $34 = $114. The per-app $/km is consistent with the gross and the km. Hoy's $548 matches "Llevas esta semana" $548 on a Monday. Later, "Ayer lunes 19 oct te quedaron $1,026" comes from the end of that same day, so it doesn't conflict with the $548 at 14:40.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (imagined: find Kompara, switch on, accept control total) > Ya lo activé > Abrir Uber para probar (chip ▲ $9.49/km Arriba de tu meta appeared)","taps":5,"success":"yes","answer":"Semáforo preparado / permiso listo; chip shows on the Uber offer","ease":6,"stuck":"No real block; the numbered steps, the Apps instaladas hint and the preview of the 'control total' dialog made the imagined Settings step feel doable. The legend uses colour, but each line also has a shape and words."},"1":{"path":"None, read on Hoy","taps":0,"success":"yes","answer":"About $548 today net (gas, wear and car taken off), 6 trips; 'Semáforo funcionando' so it is reading","ease":7,"stuck":"None; relied on the words, not the dot colour"},"2":{"path":"One glance at the Uber offer screenshot","taps":0,"success":"yes","answer":"Diamond, $7.16/km neto, 'Cumple tu mínimo': above my $6 minimum, below my $8 goal. Yes, I'd take it midday because it covers my minimum after costs","ease":7,"stuck":"None; shape plus words carry it without colour"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers with $/km each (9.49, 2.20, no reading, 9.81, 9.41, 10.46, 9.16, 10.75, 9.20, 10.00); 6 taken","ease":7,"stuck":"None; the list doesn't say which offers were Uber and which DiDi"},"4":{"path":"Ganancias > Semana anterior > tapped '62 viajes tomados' to check the breakdown","taps":3,"success":"yes","answer":"$4,980 (12-18 Oct): $8,400 gross - $1,920 gas/wear - $1,500 car","ease":6,"stuck":"Ganancias opens on the current week, so one extra tap; I opened the breakdown only to confirm the car was included"},"5":{"path":"Ganancias > Semana anterior > Por app","taps":3,"success":"yes","answer":"DiDi: $10.34/km (28 trips) vs Uber $9.13/km (34 trips), $1.21 more per km","ease":7,"stuck":"None; the hint was already on the row"},"6":{"path":"Hoy row 'tu meta $8 · tu mínimo $6' > Tu meta y tu mínimo > + on Tu mínimo > Guardar","taps":4,"success":"yes","answer":"Minimum saved at $6.50/km (confirmation message, Hoy shows $6.50)","ease":7,"stuck":"None"},"7":{"path":"Uber map showed only a small K in the status bar > swipe down notifications > tap 'Se apagó el semáforo' > Volver a dar el permiso","taps":3,"success":"yes","answer":"Android switched off the Accessibility permission (e.g. battery saving) at 20:31 last night; give the permission again via Volver a dar el permiso, and use 'Que no se vuelva a apagar' to stop it happening again","ease":5,"stuck":"Nothing on the Uber screen said the semáforo was off; I had to think to check the notifications, and that notification was from last night"},"9":{"path":"Tap top row 'La tomaste 14:32' on Hoy","taps":1,"success":"yes","answer":"About $114 before the car cost ($148 fare - $34 gas/wear), $9.49/km, $285/h; the car is taken off per day ($214)","ease":6,"stuck":"Brief: the row says 'La tomaste', not 'último viaje', but it was the first row"}},"problems":[{"screen":"Uber screen while the semáforo is off (task 7)","problem":"The only sign is a tiny K in the status bar; the warning is a notification from last night, so a driver can start a shift without noticing","fix":"When Uber/DiDi opens and the permission is off, show a new heads-up alert: 'Semáforo apagado, toca para encenderlo'"},{"screen":"Hoy and Tu día offer lists","problem":"No Uber/DiDi tag on each offer; for a red-green colour-blind driver the small colour tiles mean nothing, and only the words and the top/bottom fill position help","fix":"Add an Uber/DiDi tag to each row and use the ▲◆▼ shapes in the list icons, as on the semáforo"},{"screen":"Trip detail and Por app sheet vs Hoy/Ganancias","problem":"'Te deja' sometimes means before the car cost ($114, per-app $/km) and sometimes after it ($548, $4,980); it's labelled only in small grey text, so I read it twice","fix":"Use one fixed label for each base (e.g. 'sin coche' / 'con coche') in the same visible place on every screen"}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes: I pay $1,500 a week for the car and this tells me what I really keep, and the semáforo works with shapes and words so I don't need to tell red from green.","money_inconsistency":{"any":false,"which":"None found. Sums check ($8,400 - $1,920 - $1,500 = $4,980; Uber $5,028 + DiDi $3,372 = $8,400; $148 - $34 = $114; per-app $/km matches gross and km). Hoy $548 = week $548 on Monday. 'Ayer lunes 19 oct $1,026' is end of day vs $548 at 14:40, so no conflict."}}
```

### Judge 4 (sonnet)

**Session report: Jorge, 60 (older car, large text, reading glasses, cautious)**

I did the tasks in the order 8, 1, 2, 3, 4, 5, 6, 7, 9. I started each with `task start` and ended it with `task done`. I never needed `task stuck`. My tap counts include wrong taps, and gestures (swipe, scroll) are noted separately.

**Task 8: from the welcome screen, make the semáforo work**
- Path: Empezar, Acepto, Abrir ajustes, then "Ya lo activé" on the substitute Android Settings screen.
- Taps: 4.
- Success: yes.
- Answer: the Hoy screen changed to "Semáforo preparado, permiso listo" with "Listo. Abre tu app de viajes".
- Hesitation: the first screen has a button "Recuperar mis datos" and I didn't know what data it meant. The privacy screen was dense, especially "Manda conteos de lectura ... tu aporte al promedio, archivos o reportes". I read it twice. The Android prompt "¿Permitir que Kompara tenga control total de tu dispositivo?" sounds frightening. The example box on the screen and the line "nunca toca botones ni acepta viajes por ti" calmed me.
- After "Ya lo activé" the status shows an empty ring (○) instead of the green dot, so I can't tell it is working until the first offer arrives.
- Ease: 5.

**Task 1: today's earnings, and is the app reading offers**
- Path: none, the answer was on Hoy.
- Taps: 0.
- Success: yes.
- Answer: about $548 so far today (6 trips, after gasolina, desgaste and coche). "Semáforo funcionando" with the green dot, and Uber shows "Lectura comprobada · 14:32".
- Hesitation: none.
- Ease: 7.

**Task 2: the Uber offer**
- Path: one look at the screenshot.
- Taps: 0.
- Success: yes.
- Answer: the chip said "$7.16/km neto, Cumple tu mínimo" (yellow diamond). That is above my $6 minimum and below my $8 goal. The offer was $120.00 for 12 km (2 km to pick up, 10 km trip, 5 + 22 min). I would take it, since it clears my minimum, but it is a middling offer, not a good one.
- Hesitation: the big number and the colour read at a glance. The small lines "tu meta $8 / tu mínimo $6" with the bars were too small to read in one look.
- Ease: 6.

**Task 3: all of today's offers with their $/km**
- Path: Ver todas las ofertas de hoy, then scroll down once.
- Taps: 1 tap plus 1 scroll.
- Success: yes.
- Answer: "Ofertas de hoy · 10":

| Time | Outcome | Net per km |
|---|---|---|
| 14:32 | La tomaste | $9.49 |
| 12:10 | Se fue sin tomar | $2.20 |
| 11:48 | Sin lectura | (no figure) |
| 11:20 | La tomaste | $9.81 |
| 10:41 | La tomaste | $9.41 |
| 10:02 | La tomaste | $10.46 |
| 9:51 | Se fue sin tomar | $9.16 |
| 9:24 | La tomaste | $10.75 |
| 8:40 | Se fue sin tomar | $9.20 |
| 8:15 | La tomaste | $10.00 |

- Hesitation: only 6 rows fit on the first screen. I counted the heading "· 10" against the rows to be sure I had them all. The column title "te deja por km" is small and grey.
- Ease: 6.

**Task 4: last week's net after all expenses, including the car**
- Path: Ganancias, Semana anterior.
- Taps: 2.
- Success: yes.
- Answer: $4,980 (aprox.) for 12–18 oct, "semana pasada", 62 trips, "ya restamos gasolina, desgaste y coche".
- Hesitation: the tab opens on the current week (19–25 oct, "en curso", $548). I had to notice "Semana anterior".
- Ease: 6.

**Task 5: which app paid more per km last week**
- Path: Ganancias, Semana anterior, Por app.
- Taps: 3.
- Success: yes.
- Answer: DiDi paid more. DiDi $10.34/km (28 trips) against Uber $9.13/km (34 trips), a difference of $1.21.
- Hesitation: the row subtitle already said "DiDi te dejó $1.21 más por km", but I opened it to see both figures. The line "Manejaste cada app a distintas horas" is not explained. "Lo del coche no entra" took a re-read.
- Ease: 6.

**Task 6: change my minimum to $6.50 per km**
- Path: the "tu meta $8 · tu mínimo $6" status row on Hoy, then "Tu meta y tu mínimo", then "+" on the minimum, then Guardar.
- Taps: 4.
- Success: yes.
- Answer: the screen confirmed «Guardado: tu mínimo ahora es $6.50/km», and the row now reads $8.00/km · $6.50/km.
- Hesitation: my first tap went to the status row on Hoy and only opened the Semáforo status screen, so I needed a second hop. I also saw the gear icon and wasn't sure which was right. The "+" has no label; I guessed it, and the caption "de $0.50 en $0.50" confirmed the step.
- Ease: 6.

**Task 7: no semáforo at the start of my shift**
- Path: swipe down from the top (I noticed a small K at the top left of the Uber screen), tap the notification "Se apagó el semáforo", then Volver a dar el permiso, Abrir ajustes, Ya lo activé.
- Taps: 4 taps plus 1 swipe (5 actions).
- Success: yes.
- Answer: the Accesibilidad permission was off ("Android a veces lo apaga, por ejemplo al ahorrar batería") since yesterday at 20:31, so Kompara can't see my offers. The fix is Volver a dar el permiso, find Kompara in the list, switch it on and tap Permitir. Afterwards it showed "Semáforo preparado, permiso listo. En tu próxima oferta verás el semáforo".
- Hesitation: the Uber screen gave no hint at all. I found the cause only because I noticed the tiny K. The notification text was clear and safe: "Abre Kompara al estacionarte". I did not tap "Que no se vuelva a apagar" because I don't know what it does.
- Ease: 5.

**Task 9: what my last trip left me**
- Path: tap "La tomaste" (the 14:32 row).
- Taps: 1.
- Success: yes.
- Answer: about $114 before the car cost, which is the $148 fare minus $34 for gasolina and desgaste (12 km × $2.84). That is $9.49/km net, $285/h, 24 min. The car ($214 a day) is subtracted per day, not per trip.
- Hesitation: none. The row title "La tomaste" reads like a status, not a trip, but it was the natural thing to tap.
- Ease: 6.

**(a) Usability problems I actually hit**
1. Task 7, the Uber screen when the semáforo is off: nothing says why. The only sign is a tiny K in the status bar, and a driver with reading glasses can miss it. Fix: when it turns off, give one vibration and sound and keep a pinned notification ("Semáforo apagado, toca para encenderlo").
2. Task 6, the Semáforo screen: it says "Ofertas sin semáforo · ninguna hoy". But Hoy lists an offer at 11:48 "Sin lectura · No pudimos leer la tarifa". Fix: show the real count ("1 hoy") and link to that offer.
3. Across many screens, the second-line text is small and light grey. Examples: "14:32 · Arriba de tu meta", "el pago final puede cambiar", "Se resta por día ($214), no por viaje", and the "Ya restamos gasolina y desgaste. Lo del coche no entra..." note. It is hard to read with glasses. Fix: make it darker and bigger (at least phone body size), or follow Android's large-text setting.

**(b)** Compared with the Uber Driver app, this one is easy to use: 7 out of 10. Uber's Ganancias screen shows one MXN total with a bar chart, then Tarifas, Incentivo and Propina folded away, and it never subtracts costs. Kompara gives me the net directly.

**(c)** I would understand every screen without help: 7 out of 10. The dense privacy text, "Recuperar mis datos", "Manejaste cada app a distintas horas" and the "Que no se vuelva a apagar" button would need explaining.

**(d)** Doing the same in my Uber Driver app:

| Task | Ease (1–7) |
|---|---|
| 1, today's earnings | 5 |
| 4, last week's earnings | 4 |
| 9, last trip's detail | 4 |

**(e)** I would install it: it shows what I actually keep after gasolina, desgaste and the car, which Uber doesn't. I would still ask someone I trust about the "control total" permission and the warning that Uber and DiDi might not allow apps like this.

**(f)** No two money figures contradicted each other. The numbers I could check were consistent:
- $148 − $34 = $114.
- $120 offer = $7.16/km.
- 28 + 34 = 62 trips.
- $10.34 − $9.13 = $1.21.
- $214 × 7 ≈ $1,500.
- 6 trips taken and 2 above-goal offers that went untaken both match the offer list.

The one contradiction I saw is not a money figure: it is the "Ofertas sin semáforo · ninguna hoy" line from problem 2.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Ya lo activé (Android Settings substitute)","taps":4,"success":"yes","answer":"Hoy shows Semáforo preparado, permiso listo; Listo. Abre tu app de viajes","ease":5,"stuck":"Dense privacy paragraph (conteos de lectura, tu aporte al promedio), unexplained 'Recuperar mis datos', scary 'control total' wording (softened by the example and 'nunca toca botones'); empty ring status after activation does not confirm it is working"},"1":{"path":"Hoy (no taps)","taps":0,"success":"yes","answer":"About $548 so far today (6 trips, after gasolina, desgaste, coche); Semáforo funcionando, Uber lectura comprobada 14:32","ease":7,"stuck":"none"},"2":{"path":"Single look at Uber screenshot","taps":0,"success":"yes","answer":"$7.16/km neto, Cumple tu mínimo (yellow diamond): above my $6 minimum, below my $8 goal; $120 for 12 km. Yes I would take it, it clears my minimum but is not a great offer.","ease":6,"stuck":"Small 'tu meta / tu mínimo' lines with bars unreadable at a glance; headline sufficed"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: 14:32 $9.49 taken; 12:10 $2.20 not taken; 11:48 sin lectura; 11:20 $9.81 taken; 10:41 $9.41 taken; 10:02 $10.46 taken; 9:51 $9.16 not taken; 9:24 $10.75 taken; 8:40 $9.20 not taken; 8:15 $10.00 taken","ease":6,"stuck":"Only 6 rows fit on the first screen, had to scroll and count against '· 10'; small grey 'te deja por km' header"},"4":{"path":"Ganancias > Semana anterior","taps":2,"success":"yes","answer":"$4,980 aprox. for 12-18 oct (62 trips), after gasolina, desgaste y coche","ease":6,"stuck":"Opens on the current week ($548); had to notice Semana anterior"},"5":{"path":"Ganancias > Semana anterior > Por app","taps":3,"success":"yes","answer":"DiDi paid more per km: $10.34/km (28 trips) vs Uber $9.13/km (34 trips), $1.21 more","ease":6,"stuck":"'Manejaste cada app a distintas horas' unexplained; 'Lo del coche no entra' needed a re-read; answer was already in the row subtitle but I opened it to see both figures"},"6":{"path":"Status row 'tu meta $8 · tu mínimo $6' on Hoy > Tu meta y tu mínimo > + on Tu mínimo > Guardar","taps":4,"success":"yes","answer":"Saved: tu mínimo ahora es $6.50/km (row shows $8.00/km · $6.50/km)","ease":6,"stuck":"First tap on the Hoy status row led to a status screen, needed a second hop; unsure between that row and the gear icon; unlabeled + button"},"7":{"path":"swipe down from top > notification 'Se apagó el semáforo' > Volver a dar el permiso > Abrir ajustes > Ya lo activé","taps":5,"success":"yes","answer":"The Accesibilidad permission was off (Android sometimes turns it off, e.g. battery saving) since yesterday 20:31; fix: Volver a dar el permiso, find Kompara, switch on, Permitir; app then shows Semáforo preparado, permiso listo (4 taps plus 1 swipe)","ease":5,"stuck":"Uber screen gave no hint why; only a tiny K in the status bar led me to the notification; unsure what 'Que no se vuelva a apagar' does"},"9":{"path":"Tap La tomaste (14:32) on Hoy","taps":1,"success":"yes","answer":"About $114 before the car cost: $148 fare minus $34 gasolina y desgaste; $9.49/km net, $285/h, 24 min; car ($214/day) is subtracted per day, not per trip","ease":6,"stuck":"none; 'La tomaste' reads like a status label rather than a trip"}},"problems":[{"screen":"Uber map screen when the semáforo is off (Task 7)","problem":"Nothing on screen says why the semáforo is missing; only a tiny K in the status bar hints at it, easy to miss with reading glasses.","fix":"One vibration and sound when it turns off, plus a pinned notification 'Semáforo apagado, toca para encenderlo'."},{"screen":"Semáforo status screen vs Hoy offer list","problem":"The Semáforo screen says 'Ofertas sin semáforo · ninguna hoy' while Hoy lists a 'Sin lectura' offer at 11:48 today.","fix":"Show the real count ('1 hoy') and link it to that offer."},{"screen":"Many screens (offer rows, trip sheet, Por app sheet)","problem":"Second-line and note text is small and light grey and hard to read with glasses.","fix":"Darker, larger secondary text (at least phone body size) or respect Android's large-text setting."}],"ease_vs_uber":7,"understand_all":7,"uber_seq":{"1":5,"4":4,"9":4},"install":"Yes, because it shows what I actually keep after gasolina, desgaste and the car, which Uber's Ganancias does not, though I would first ask someone I trust about the 'control total' permission and the warning that Uber and DiDi might not allow such apps.","money_inconsistency":{"any":false,"which":"No money figures contradicted. Non-money count mismatch only: Semáforo screen says 'Ofertas sin semáforo · ninguna hoy' while Hoy lists a 'Sin lectura' offer at 11:48."}}
```

### Judge 5 (sonnet)

**SESSION REPORT: Ana (j5)**

I did the tasks in the order 8, 1, 2, 3, 4, 5, 6, 7, 9, with `task start` and `task done` around each. I counted taps only, so scrolls and the one swipe are noted separately.

**Task 8: make the semáforo work from the welcome screen**
- Path: «Empezar», «Acepto», «Abrir ajustes», «Ya lo activé». That is 4 taps.
- Success: yes. The screen ended on «Semáforo preparado · permiso listo» and «Listo. Abre tu app de viajes».
- Answer: the semáforo is ready and will show on my first offer.
- Hesitation: the «Lo que hace Kompara» screen is dense. I skimmed it and accepted. «control total de tu dispositivo» sounds alarming. The note under it («nunca toca botones…») calmed me, and the 1-2-3 steps were clear.
- Ease: 6.

**Task 1: today's earnings, and is the app reading offers?**
- Path: none. Hoy was already on screen, so 0 taps.
- Success: yes.
- Answer: about $548 so far today from 6 trips, after gas, wear and car. «Semáforo funcionando» with a green dot means it is reading. Uber's last reading was at 14:32.
- Hesitation: none.
- Ease: 7.

**Task 2: the Uber offer, glanced at once**
- Path: none, 0 taps.
- Success: yes.
- Answer: the chip said $7.16/km neto with a yellow diamond and «Cumple tu mínimo». My goal is $8 and my minimum is $6. The offer was $120 for 12 km in about 27 min.
- Would I take it? Yes. It is above my minimum but under my goal, so it is acceptable, not great.
- Hesitation: at a glance I read the big number, the colour and the words. The thin lines next to «tu meta» and «tu mínimo» meant nothing to me.
- Ease: 6.

**Task 3: all of today's offers with $/km**
- Path: «Ver todas las ofertas de hoy», then scroll down once. That is 1 tap and 1 scroll.
- Success: yes.
- Answer: 10 offers.

| Time | Result | $/km |
|---|---|---|
| 14:32 | taken | 9.49 |
| 12:10 | left, below minimum | 2.20 |
| 11:48 | no reading | none |
| 11:20 | taken | 9.81 |
| 10:41 | taken | 9.41 |
| 10:02 | taken | 10.46 |
| 9:51 | left | 9.16 |
| 9:24 | taken | 10.75 |
| 8:40 | left | 9.20 |
| 8:15 | taken | 10.00 |

- Hesitation: only 6 rows fit on screen, so I had to scroll to see the other 4. The «Sin lectura» row has no $/km, which is expected.
- Ease: 6.

**Task 4: last week's take-home after all costs including the car**
- Path: «Ganancias», «Semana anterior». That is 2 taps.
- Success: yes.
- Answer: about $4,980 for 12–18 oct, from 62 trips, after gas, wear and car.
- Hesitation: none. The label «Te quedaron esa semana · aprox.» matched my question.
- Ease: 7.

**Task 5: which app paid more per km last week**
- Path: «Ganancias», «Semana anterior», «Por app». That is 3 taps.
- Success: yes.
- Answer: DiDi, at $10.34/km against Uber's $9.13/km, so $1.21 more. These are net of gas and wear, and the car is left out because it costs the same on both apps. I drove each app at different hours.
- Hesitation: the row preview already said «DiDi te dejó $1.21 más por km». I opened the sheet to see both numbers. The wording is «te deja» (what I keep), not «te pagó», which is fine for me.
- Ease: 6.

**Task 6: set my minimum to $6.50/km**
- Path: the «tu meta $8 · tu mínimo $6» row on Hoy, then «Tu meta y tu mínimo», then the «+» under «Tu mínimo» (by coordinates), then «Guardar». That is 4 taps.
- Success: yes. The note said «Guardado: tu mínimo ahora es $6.50/km» and the Semáforo page then showed «$8.00/km · $6.50/km».
- Hesitation: I expected the Hoy row to open the editor directly. It opened a status page first. The «+» and «−» buttons are bare circles with no label, so I had to find them by position. The «de $0.50 en $0.50» wording is odd.
- Ease: 5.

**Task 7: no semáforo on my offers, why and what to do**
- Path: swipe down from the top (the status bar showed a small K), «Se apagó el semáforo» notification, «Volver a dar el permiso», «Abrir ajustes», «Ya lo activé», «Que no se vuelva a apagar». That is 5 taps and 1 swipe.
- Success: yes.
- Answer: Android turned off Kompara's Accessibility permission last night at 20:31. Android sometimes does this, for example when saving battery. Without it Kompara can't see offers. The fix is to give the permission again, which brought back «Semáforo preparado». To stop it recurring, set Kompara's battery to «Sin restricciones».
- Hesitation: on the bare Uber map there is nothing on screen that says why the semáforo is missing. I only found out because I noticed the K and pulled down the notifications. Everything after that was clear.
- Ease: 6.

**Task 9: what my last trip left me**
- Path: «La tomaste» (the 14:32 row on Hoy). That is 1 tap.
- Success: yes.
- Answer: about $114 for the 14:32 trip. That is the $148 fare minus $34 for gas and wear (12 km × $2.84). It is $9.49/km, $285/h, and 24 min.
- Hesitation: the headline says «antes de lo del coche». I had already entered my car cost, so for a moment I wondered whether $114 was my real number. The lines below it explain that the car is subtracted per day ($214), not per trip.
- Ease: 6.

**(a) Usability problems I actually hit**
1. **Semáforo status page vs the Hoy list.** The status page says «Ofertas sin semáforo · ninguna hoy», but the day list has a «Sin lectura» offer at 11:48.
   - Fix: count unread offers in that line, or reword it, so the two screens agree.
2. **Last-trip sheet.** The headline «$114 · Te deja antes de lo del coche» makes me doubt whether that is the real number.
   - Fix: lead with «$114 de este viaje. El coche ($214) se resta por día.»
3. **First screens.** «Lo que hace Kompara» uses jargon («conteos de lectura», «aporte al promedio»). The Android prompt «control total de tu dispositivo» also sounds scary.
   - Fix: 2–3 plain lines ending with «Solo lee la oferta. Nunca toca nada.», and put the reassurance next to the scary phrase.

**(b) Ease compared with the Uber Driver app:** 8 out of 10. Uber's Earnings screen shows the week's total, online hours, requests and points. It does not show per-km or what I keep. Kompara answers my questions directly.

**(c) Would I understand every screen without help:** 8 out of 10. Words like «neto», «aprox.» and «Sin lectura» took a second to read.

**(d) The same three tasks in my Uber Driver app**
- Task 1, today's earnings: 5. The Earnings screen is weekly, and I would have to tap a day bar.
- Task 4, last week after all expenses: 3. I could find last week's total, but it is before costs and the car.
- Task 9, last trip's detail: 5. The trip shows the fare, not what I keep.

**(e) Install:** yes, because it tells me what an offer leaves me per km before I accept, and what I kept after the car.

**(f) Money figures that contradict each other:** no.
- The numbers I checked agree: $214/day is $1,500/week divided by 7, $148 − $34 = $114, and 28 + 34 DiDi and Uber trips = 62.
- The only mismatch is the non-money count in problem 1.

Prototype stopped; the log is at `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk8/j5/log.json`.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Ya lo activé","taps":4,"success":"yes","answer":"Semáforo ready: Semáforo preparado, permiso listo, shows on my first offer","ease":6,"stuck":"Dense consent text with jargon; 'control total de tu dispositivo' sounds alarming until the note under it; otherwise clear steps"},"1":{"path":"none, Hoy already on screen","taps":0,"success":"yes","answer":"About $548 today so far from 6 trips (after gas, wear and car); Semáforo funcionando, last Uber reading 14:32","ease":7,"stuck":"none"},"2":{"path":"none, glanced at Uber capture","taps":0,"success":"yes","answer":"$7.16/km neto, yellow Cumple tu mínimo (goal $8, minimum $6); $120 for 12 km, about 27 min; I would take it, above my minimum but under my goal","ease":6,"stuck":"Thin lines next to tu meta/tu mínimo mean nothing at a glance; number, colour and words were enough"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers today: 9.49, 2.20, no reading, 9.81, 9.41, 10.46, 9.16, 10.75, 9.20, 10.00 $/km (14:32, 12:10, 11:48, 11:20, 10:41, 10:02, 9:51, 9:24, 8:40, 8:15)","ease":6,"stuck":"Only 6 rows fit; had to scroll to see the other 4"},"4":{"path":"Ganancias > Semana anterior","taps":2,"success":"yes","answer":"About $4,980 for 12-18 oct (62 trips), after gas, wear and car","ease":7,"stuck":"none"},"5":{"path":"Ganancias > Semana anterior > Por app","taps":3,"success":"yes","answer":"DiDi: $10.34/km vs Uber $9.13/km (net of gas and wear, car excluded), $1.21 more per km; I drove each app at different hours","ease":6,"stuck":"Row preview already said DiDi +$1.21; opened the sheet to see both numbers; wording is 'te deja' not 'te pagó'"},"6":{"path":"tu meta/tu mínimo row on Hoy > Tu meta y tu mínimo > + under Tu mínimo (tapxy) > Guardar","taps":4,"success":"yes","answer":"Minimum saved at $6.50/km (goal stays $8.00); note 'Guardado: tu mínimo ahora es $6.50/km'","ease":5,"stuck":"Expected the Hoy row to open the editor directly but got a status page first; + and - are unlabelled circles; 'de $0.50 en $0.50' is odd wording"},"7":{"path":"swipe down from top > Se apagó el semáforo notification > Volver a dar el permiso > Abrir ajustes > Ya lo activé > Que no se vuelva a apagar","taps":5,"success":"yes","answer":"Android turned off the Accessibility permission last night 20:31 (e.g. battery saving), so Kompara can't see offers; give the permission again (done, Semáforo preparado); to stop it recurring set Kompara's battery to Sin restricciones","ease":6,"stuck":"On the bare Uber map nothing says why the semáforo is missing; I only found out by noticing the K and pulling down the notifications"},"9":{"path":"La tomaste (14:32 row on Hoy)","taps":1,"success":"yes","answer":"About $114 for the 14:32 trip: $148 fare minus $34 gas and wear (12 km x $2.84); $9.49/km, $285/h, 24 min; the car ($214/day) is subtracted per day, not per trip","ease":6,"stuck":"Headline 'antes de lo del coche' made me wonder if $114 was my real number since I already entered the car cost; the lines below explain it"}},"problems":[{"screen":"Semáforo status page (from Hoy) vs the Hoy offers list","problem":"Says 'Ofertas sin semáforo · ninguna hoy' while the day list has a 'Sin lectura' offer at 11:48","fix":"Count unread offers in that line (or reword it) so both screens agree"},{"screen":"Last-trip detail sheet (tap La tomaste)","problem":"Headline '$114 · Te deja antes de lo del coche' makes a driver who already entered the car cost doubt whether it is the real number","fix":"Lead with '$114 de este viaje. El coche ($214) se resta por día.'"},{"screen":"First screens: 'Lo que hace Kompara' and the permission steps","problem":"Dense jargon (conteos de lectura, aporte al promedio) and the Android prompt 'control total de tu dispositivo' sounds scary","fix":"2-3 plain lines ending with 'Solo lee la oferta. Nunca toca nada.', with the reassurance placed next to the scary phrase"}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":5,"4":3,"9":5},"install":"Yes, because it tells me what an offer leaves me per km before I accept and what I kept after the car.","money_inconsistency":{"any":false,"which":"No money figures contradicted each other ($214/day = $1,500/7; $148-$34=$114; 28+34=62 trips). Only mismatch is non-money: 'Ofertas sin semáforo · ninguna hoy' on the Semáforo page vs a 'Sin lectura' offer at 11:48 on Hoy."}}
```

### Judge 6 (haiku)

## Session report: Diego, Kompara prototype (walk8/j6)

Prototype started, all nine tasks run in the order 8, 1–7, 9, then `log` (saved to `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk8/j6/log.json`, not read) and `stop`. Taps are tap/tapxy commands only. Scrolls and swipes are listed in the paths.

**Task 8 (first day, traffic light working)**
- Path: Empezar, Acepto, Abrir ajustes, then imagined the accessibility steps and tapped Ya lo activé, then Abrir Uber para probar to check it.
- Taps: 5. Success: yes. The Hoy screen reads "Semáforo preparado / permiso listo", and a live Uber offer showed the chip ($9.49/km, arriba de tu meta).
- Ease: 6. The "control total de tu dispositivo" wording made me hesitate, but the app explained it.

**Task 1 (how much today, is it reading offers)**
- Path: Hoy, already on screen at task start.
- Taps: 0. Success: yes. About $548 so far today, 6 trips taken, net of gas, wear and car. "Semáforo funcionando" shows it is reading.
- Ease: 7.

**Task 2 (glance at an Uber offer, would you take it)**
- Path: the Uber offer at task start (one look).
- Taps: 0. Success: yes. $120 total, 5 min (2 km) pickup, 22 min (10 km) trip. Yellow diamond "Cumple tu mínimo", $7.16/km neto, against meta $8 and mínimo $6. I would take it: it clears my minimum and the pickup is short, though it misses the $8 goal.
- Ease: 6. I had to recall from the welcome screen that "neto" means gas and wear already subtracted; the chip itself does not say so.

**Task 3 (today's offers with $/km)**
- Path: Hoy, tap Ver todas las ofertas de hoy, scroll down once.
- Taps: 1. Success: yes. 10 offers on 19 oct:
  - 14:32 $9.49 (taken)
  - 12:10 $2.20 (left, below minimum)
  - 11:48 no reading
  - 11:20 $9.81 (taken)
  - 10:41 $9.41 (taken)
  - 10:02 $10.46 (taken)
  - 9:51 $9.16 (left)
  - 9:24 $10.75 (taken)
  - 8:40 $9.20 (left)
  - 8:15 $10.00 (taken)
  
  The counts match the header (6 taken, 2 above-goal left).
- Ease: 6. The list needed scrolling, and the "te deja por km" column is small grey text.

**Task 4 (last week's net after all costs, car included)**
- Path: Ganancias (tab), tap Semana anterior.
- Taps: 2. Success: yes. Te quedaron about $4,980 for 12–18 oct, 62 trips, after gas, wear and car.
- Ease: 6. The "Semana anterior" link was small.

**Task 5 (Uber or DiDi, better per km last week)**
- Path: Ganancias, Semana anterior, Por app.
- Taps: 3. Success: yes. DiDi $10.34/km (28 trips) against Uber $9.13/km (34 trips), so DiDi paid $1.21/km more.
- Ease: 6. The sheet notes "Manejaste cada app a distintas horas" and "Lo del coche no entra", which conflicts with the weekly total's "ya restamos ... coche".

**Task 6 (set minimum to $6.50/km)**
- Path: Hoy, tap Semáforo funcionando, tap Tu meta y tu mínimo, tapxy the plus on the minimum row (one $0.50 step), tap Guardar.
- Taps: 4. Success: yes. Confirmation "Guardado: tu mínimo ahora es $6.50/km", and Hoy now shows "tu mínimo $6.50".
- Ease: 6. "Toca la cifra para escribirla" suggests typing, but the steppers are $0.50 and I used them.

**Task 7 (semáforo missing on an early shift)**
- Path: swipe down from the top edge, tap Se apagó el semáforo, tap Volver a dar el permiso (which opens the accessibility steps).
- Taps: 2 (plus 1 swipe). Success: yes. Cause: "Se apagó el semáforo desde ayer 20:31, falta el permiso de Accesibilidad", and Android sometimes turns it off, for example while saving battery. Fix: turn Kompara back on in Accesibilidad, then Permitir.
- Ease: 6. The Uber screen gave no clue beyond a tiny green K in the status bar; I only found the cause by pulling down the notifications. I did not open "Que no se vuelva a apagar".

**Task 9 (last trip's detail)**
- Path: Hoy, tap the 14:32 row.
- Taps: 1. Success: yes. Fare $148, gas and wear −$34, so $114 before car, $9.49/km, 24 min, $285/h. The arithmetic checks out.
- Ease: 6. The list shows only $/km, so the peso amount needs the detail sheet.

**(a) Usability problems I actually hit**
1. Ganancias > Semana anterior > Por app sheet. Per-app $/km (DiDi $10.34, Uber $9.13) excludes the car, but the weekly total above says "ya restamos gasolina, desgaste y coche". The exclusion appears only as a grey footnote. Fix: label per-km figures "antes del coche" and use the same wording as the weekly total.
2. Uber overlay chip "$7.16 /km neto". "Neto" does not say what was subtracted; I had to open a trip detail to see gas and wear. Fix: a short caption on the chip or a one-time tip on the first offer, such as "neto: ya restamos gasolina y desgaste".
3. Notification "Se apagó el semáforo" and the Uber screen with no chip. The only cue on Uber is a small K in the status bar, and the fix only shows once the shade is pulled down. Fix: make the permission-loss notification high priority with a "Volver a dar el permiso" action button directly in it.

**(b) Ease compared with Uber Driver (1–10):** 6. Kompara has more concepts to learn (semáforo, meta, mínimo, neto, antes del coche); Uber's home screen is simpler, with one GO button.

**(c) Understand every screen without help (1–10):** 6. Terms such as "neto", "Te deja antes de lo del coche" and "Sin lectura" needed a second look.

**(d) Same tasks in Uber Driver (1–7):** Task 1 (today's earnings): 4, the reference shows only the weekly total and bars. Task 4 (last week): 4, no week selector is visible in the reference. Task 9 (last trip): 5, trip detail is probably a couple of taps away, but I cannot confirm it from the reference.

**(e) Install?** Yes, I would install it to test it on a real shift, because the net-per-km math matches the trip detail ($148 less $34 gas and wear gives $114 for 12 km), though the accessibility permission and the warning that Uber may not allow it make me hesitate.

**(f) Money figures that seemed to conflict:** yes. On 19 oct, Hoy and Ganancias at 14:40 showed "Llevas hoy" $548 with 6 trips. The next morning's card said "Ayer, lunes 19 oct, te quedaron $1,026 aprox." for the same day, with no note that the first was a partial snapshot. Trips after 14:40 may explain it, but the app does not say so. Also, the car cost is treated differently in the weekly total and in the per-app sheet, as noted in task 5.

Session log: `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk8/j6/log.json`

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (imagined accessibility steps) Ya lo activé > Abrir Uber para probar (verified chip on a live offer)","taps":5,"success":"yes","answer":"Yes, it works: Semáforo preparado with permiso listo, and a live Uber offer showed the chip (9.49 per km neto, arriba de tu meta).","ease":6,"stuck":"The control total de tu dispositivo wording made me hesitate; the app explained it, so none real."},"1":{"path":"Hoy (task start, no taps)","taps":0,"success":"yes","answer":"About $548 so far today, 6 trips taken, net of gas, wear and car. Semáforo funcionando shows it is reading offers.","ease":7,"stuck":""},"2":{"path":"Uber offer at task start (one look)","taps":0,"success":"yes","answer":"Total $120, 5 min (2 km) pickup, 22 min (10 km) trip. Yellow diamond Cumple tu mínimo, $7.16/km neto, below meta $8 and above mínimo $6. I would take it: it clears my minimum and the pickup is short.","ease":6,"stuck":"I had to recall from the welcome screen that neto means gas and wear already subtracted; the chip does not say so."},"3":{"path":"Hoy > Ver todas las ofertas de hoy > scroll down once","taps":1,"success":"yes","answer":"10 offers on 19 oct: 14:32 9.49 taken; 12:10 2.20 left; 11:48 no reading; 11:20 9.81 taken; 10:41 9.41 taken; 10:02 10.46 taken; 9:51 9.16 left; 9:24 10.75 taken; 8:40 9.20 left; 8:15 10.00 taken.","ease":6,"stuck":"The list needed scrolling to see all 10; the te deja por km column is small grey text."},"4":{"path":"Ganancias (tab) > Semana anterior","taps":2,"success":"yes","answer":"Te quedaron about $4,980 for 12-18 oct, 62 trips, after gas, wear and car.","ease":6,"stuck":"Finding Semana anterior took a moment; it is a small link."},"5":{"path":"Ganancias (tab) > Semana anterior > Por app","taps":3,"success":"yes","answer":"DiDi paid more per km: 10.34/km (28 trips) vs Uber 9.13/km (34 trips), a difference of 1.21/km, for 12-18 oct.","ease":6,"stuck":"Por app is visually secondary; the note Lo del coche no entra conflicts with the weekly total that says it subtracts the car, which made me doubt the comparison."},"6":{"path":"Hoy > Semáforo funcionando row > Tu meta y tu mínimo > tapxy plus on minimum row > Guardar","taps":4,"success":"yes","answer":"Minimum saved as $6.50/km; confirmation shown and Hoy now reads tu mínimo $6.50.","ease":6,"stuck":"Toca la cifra para escribirla suggests typing, but I used the $0.50 steppers instead."},"7":{"path":"swipe down from top edge > tap Se apagó el semáforo notification > tap Volver a dar el permiso","taps":2,"success":"yes","answer":"Cause: the Accessibility permission is missing (Android sometimes turns it off, for example to save battery), so the semáforo has been off since yesterday 20:31. Fix: re-enable Kompara in Accesibilidad and tap Permitir.","ease":6,"stuck":"The Uber screen showed no sign the semáforo was off, only a small K in the status bar; I had to pull down the notifications to find out. I did not open Que no se vuelva a apagar."},"9":{"path":"Hoy > tap the 14:32 offer row","taps":1,"success":"yes","answer":"Last trip (14:32, taken): fare $148, minus $34 gas and wear, so $114 before car; $9.49/km, 24 min, $285/h.","ease":6,"stuck":"The Hoy list shows only $/km, so the peso amount needs the detail sheet."}},"problems":[{"screen":"Ganancias > Semana anterior > Por app sheet","problem":"Per-app $/km (DiDi 10.34, Uber 9.13) excludes the car, while the weekly total above says it subtracts gas, wear and car. The exclusion is only a grey footnote.","fix":"Label per-km figures as before car (antes del coche) and use the same wording as the weekly total."},{"screen":"Uber overlay chip ($7.16 /km neto, Cumple tu mínimo)","problem":"Neto does not say what was subtracted; the user had to open a trip detail to learn gas and wear are included.","fix":"Add a short caption on the chip or a one-time tip on the first offer, e.g. neto: ya restamos gasolina y desgaste."},{"screen":"Notification Se apagó el semáforo and the Uber screen with no chip","problem":"When the Accessibility permission is lost, the only cue on Uber is a tiny K in the status bar; the fix is only reachable by pulling down the shade.","fix":"Make the permission-loss notification high priority with a Volver a dar el permiso action button directly in it."}],"ease_vs_uber":6,"understand_all":6,"uber_seq":{"1":4,"4":4,"9":5},"install":"Yes, I would install it to test on a real shift, because the net-per-km math matches the trip detail ($148 less $34 gas and wear gives $114 for 12 km), though the accessibility permission and the warning that Uber may not allow it make me hesitate.","money_inconsistency":{"any":true,"which":"Hoy and Ganancias at 14:40 on 19 oct show Llevas hoy $548 (6 trips), but the next-morning card says Ayer, lunes 19 oct, te quedaron $1,026 for the same day with no note that the first was partial. Also, Por app (12-18 oct) says the per-km figures exclude the car (Lo del coche no entra), while the weekly total for the same week says it subtracts the car."}}
```
