# Run 6 — interactive prototype (Round 21b kit), 2026-10-28

Same six personas and models as run 5, same harness and brief; the first-day note points to the new Android stand-in (A3s). Round 21 fixed the limits editor ($0.50 steps, fields, save-and-return with a snackbar), merged the permission screen, "Llevas" for open periods, position words instead of colour words, the period buttons and one cost basis.

```
6 judges · logs from 6
| task | self success (yes/partial/no) | reached the answer screen (log) | mean taps (log) | mean misses (log) | mean time (s) | mean SEQ |
|---|---|---|---|---|---|---|
| First day setup | 6/0/0 | 6/6 | 4.3 | 0.0 | 33 | 5.50 |
| Hoy: kept today + reader | 6/0/0 | 6/6 | 0.0 | 0.0 | 8 | 6.67 |
| Offer: worth it? | 5/1/0 | 6/6 | 0.0 | 0.0 | 8 | 5.83 |
| Every offer today | 6/0/0 | 6/6 | 1.0 | 0.0 | 20 | 6.00 |
| Last week after car | 6/0/0 | 6/6 | 2.5 | 0.0 | 26 | 5.83 |
| Which app paid more | 6/0/0 | 6/6 | 3.2 | 0.0 | 23 | 5.67 |
| Change tu mínimo | 6/0/0 | 6/6 | 4.0 | 0.0 | 29 | 6.00 |
| Overlay stopped | 6/0/0 | 4/6 | 4.2 | 0.0 | 40 | 5.83 |
| Last trip detail | 6/0/0 | 6/6 | 1.0 | 0.0 | 15 | 6.00 |

mean SEQ 5.93 · ease vs Uber 7.0/10 · understand every screen 7.2/10 · lowest task 5.50
matched task 1: Kompara 6.67 vs Uber (self-estimated) 6.17 → diff +0.50
matched task 4: Kompara 5.83 vs Uber (self-estimated) 4.67 → diff +1.17
matched task 9: Kompara 6.00 vs Uber (self-estimated) 4.67 → diff +1.33
GATE (mean >= 6.0, vs Uber >= 7.5, no task < 5.5): NOT MET

--- judge 1 (opus) · vs Uber 7 · understood 7 · install: Yes, I'd try it because it takes off my weekly car cuenta and the gas and tells me right away whether a trip is worth it, but 'control total' and the chance tha
- Ganancias, semana pasada | 'Leímos $5,028 · tu resumen dice $5,062' sits under the $4,980 net while the breakdown says trips were $8,400; three big numbers with no common basis, so a driver who distrusts numbers stops trusting the app | fix: Put both figures on the same basis and label them ('Uber dice que cobraste $X; nosotros leímos $Y'), matching the $8,400 gross, or move the reconciliation inside the 'Cómo se calcu
- Accessibility setup (onboarding and 'Volver a dar el permiso') | The 3 steps are shown only before Android Ajustes opens and the 'control total' warning is scary; a driver who avoids settings gets lost in Ajustes | fix: Open the Kompara toggle directly if possible, or show a floating card or notification with the steps over Ajustes; add '¿No lo encuentras?' help on return
- Comparar > Tus apps | Shows a DiDi $/km for an Uber-only driver, so she doubts all the data | fix: When only one app has trips, show 'Solo manejaste Uber esta semana' and point to the 'Tu ciudad' comparison instead of a two-app bar chart

--- judge 2 (sonnet) · vs Uber 7 · understood 8 · install: Yes: the traffic-light verdict is what I'd use all day and the net numbers answer what I actually ask myself, as long as the permission step doesn't risk a ban 
- Hoy header line 'tu meta $8 · tu mínimo $6', and the Semáforo page behind it | The limits line does not look like the way to edit limits, so I went through the Semáforo status page and a second page. 'Why it stopped' and 'change my limits' are mixed on the same screens. | fix: Make the limits line look like a button or add an 'Editar' link that jumps to the stepper screen; put a 'Volver a dar el permiso' shortcut on the Semáforo page.
- Ganancias week view and Comparar | The week total is mixed with the 'Uber reporta $34 más' reconciliation, which adds three extra figures ($4,980, $5,028, $5,062). There is no Uber vs DiDi split on Ganancias; it lives under Comparar, so I took a detour. | fix: Add a one-line 'Uber $X/km · DiDi $Y/km' under the week total that links to Comparar; collapse the Uber reconciliation into its own 'Cuadrar con Uber' row.
- Per-trip sheet and the offers list | The trip sheet's headline $114 says 'antes de lo del coche', so it is not my real take for the trip. The list says 'ofertas' where a driver thinks 'viajes', and 'neto por km' is tiny grey text. | fix: Show the per-trip figure as the headline with the car share beside it as 'tu parte del coche hoy'; label the list 'Ofertas de hoy · $/km neto' in clearer type.

--- judge 3 (opus) · vs Uber 8 · understood 8 · install: Yes: the shapes and words tell me at a glance, without depending on red and green, whether a trip covers my rent and gas.
- Ganancias, last week, line 'Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062' | Numbers match nothing else on the screen ($8,400 trips, $4,980 net); unclear it is Uber-only and whether before or after costs | fix: Label it: 'Solo Uber, antes de gastos: leímos $5,028 · tu resumen de Uber dice $5,062 (+$34)'
- Trip detail sheet 'Te deja antes de lo del coche $114' | For a renter the per-trip headline excludes rent while Hoy's headline includes it; two meanings of 'te deja' | fix: Add a line 'Con tu renta del día repartida: ~$X' or label the big number 'Antes de tu renta'
- Ofertas de hoy list | Only net $/km ('neto por km'); the offered fare is missing, so unclear whether the figure is what it paid or what it leaves | fix: Show fare and km under each row (e.g. '$148 · 12 km') and say 'te deja por km'

--- judge 4 (sonnet) · vs Uber 7 · understood 6 · install: Yes, probably: it shows what a trip really leaves me and which app pays more per km, but I'd want bigger text, simpler money labels, and reassurance that Uber a
- Offer card (semáforo) and offer rows on Hoy | Yellow verdict reads «Entre tu meta y tu mínimo», which needs two comparisons and gives no clear take-it-or-not; text is small for a driver with reading glasses | fix: Use a one-or-two-word verdict at glance size (e.g. «Regular · $7.16/km») with a short hint like «arriba de tu mínimo»; give list rows a plain Tomar/Dudar/No verdict
- Hoy, Ganancias, and offer detail sheet (money numbers) | Similar figures mean different things ($548 after car, $114 before car, $4,980 after car, plus «Leímos $5,028 · tu resumen dice $5,062» next to «Uber reporta $34 más»); I can't tell what I really keep or which number to  | fix: Use one label everywhere («Te quedó (ya con coche)» vs «Este viaje deja (sin coche)»); move the Uber reconciliation behind «Ver detalle» or reword it as one plain sentence
- Permission-lost recovery and first-day permission screens | The K in the status bar is easy to miss; recovery repeats the whole first-day text; the example dialog's greyed Rechazar/Permitir looks tappable; the battery fix is a small secondary link; fine print is small and light g | fix: Show a red banner on Hoy when the semáforo is off; do permission then battery «Sin restricciones» on one screen; label the example box «Así se verá en Android, no lo toques»; use 1

--- judge 5 (sonnet) · vs Uber 7 · understood 8 · install: Yes, because it shows my real take-home and gives each offer a clear colour, as long as the yellow tells me what to do.
- Offer chip / overlay, yellow case ($7.16/km neto, Entre tu meta y tu mínimo) | The yellow verdict gives no decision, so the driver compares three numbers while driving and the big gross fare competes with the net. | fix: Add a one-word plain verdict on the chip (for example 'Te alcanza') with a small '+$1.16 sobre tu mínimo' and keep the numbers secondary.
- Ganancias week screen vs Comparar tab | Which app pays more per km is only under the last tab 'Comparar' and there is no pointer from the Ganancias week screen, which is where a driver looks first. | fix: Add a row on Ganancias week screen 'Uber vs DiDi esa semana' that opens Comparar > Tus apps, or rename the tab 'Uber vs DiDi'.
- Ganancias > Semana anterior, row 'Uber reporta $34 más / Leímos $5,028 · tu resumen dice $5,062' | Three money figures sit under the headline $4,980 in technical wording, making the answer feel unreliable. | fix: Collapse to one plain note ('Tu resumen de Uber dice $34 más. Toca para ver por qué') and hide the extra figures until tapped.

--- judge 6 (haiku) · vs Uber 6 · understood 6 · install: Probably not yet: the Hoy screen and the semáforo are clear and useful, but the weekly totals do not reconcile with Uber's own $5,062 on the same screen, so I w
- Ganancias > Semana anterior (12-18 oct) and its Cómo se calculó sheet | Two gross totals on one screen: 62 viajes at $8,400 before costs versus Leímos $5,028 and tu resumen dice $5,062. The roughly $3,300 gap is never explained, so the $4,980 net cannot be checked against Uber. For a driver  | fix: Use one gross total everywhere. If the read total and Uber's total differ, name what is missing (trips not read, or DiDi trips not included) before showing a net.
- Ganancias week view, Uber reporta $34 más de lo que leímos row | The row has a chevron but opens a dead screen (Esta pantalla no está en esta prueba). Ganancias also has no Uber/DiDi split, so answering which app paid more took a dead end before I found Comparar. | fix: Make the row open the reconciliation, or drop the chevron and explain the $34 inline. Add Uber $X · DiDi $Y under the week total, linked to Comparar.
- Hoy trip list, trip sheet Oferta de las 14:32, Ganancias breakdown, Comparar | Neto, te deja, te quedaron and ya restamos el coche mean different things. Per-km and per-trip figures leave out the car, while day and week totals include it. Te deja antes de lo del coche sits next to Te quedaron, so I | fix: Put antes del coche or después del coche next to every money figure. On the trip sheet, show the car as a daily share (for example $214 del día ÷ 6 viajes ≈ $36) so trip and day nu
```

## Full judge reports

### Judge 1 (opus)

## Session report: Rosa (52, rents her car and pays a weekly cuenta, Uber only, low comfort with apps)

**Tarea 8: first day, get the semáforo working**
- Path: Empezar → Acepto → Abrir ajustes → (pantalla de sustitución) Ya lo activé → Abrir Uber para probar. That is 5 taps.
- Success: yes. The semáforo showed green on the Uber offer: $9.49/km neto, "Arriba de tu meta".
- Where I hesitated:
  - "Uber y DiDi ... podrían no permitir apps como esta" scared me, because if Uber blocks me I can't pay the cuenta.
  - The sample "¿Permitir que Kompara tenga control total de tu dispositivo?" scared me too.
  - In the imagined step, once I'm in Android's Ajustes the three instructions are gone, and I would have to remember them by heart. I avoid Ajustes, so I would fumble there for a while.
  - On the "Listo" screen I saw "Usamos costos típicos: $2.84 por km". I couldn't tell if that fits my car or includes my cuenta. I didn't touch it.
- Ease: 4.

**Tarea 1: how much today, and is it reading my offers?**
- Path: none needed, it's on Hoy. 0 taps.
- Success: yes. Answer: about $548 today, from 6 trips, after gas, wear and car. The green dot says "Semáforo funcionando".
- Where I hesitated: "aprox." and "ya restamos ... coche" mean I can't match it to what Uber shows me.
- Ease: 6.

**Tarea 2: one glance at an Uber offer**
- Path: 0 taps.
- Success: yes. Answer: more or less. It's yellow, $7.16/km neto, between my goal of $8 and my minimum of $6. I'd take it if nothing better comes.
- Where I hesitated: nowhere. The colour and the arrow or diamond read at a glance. The small limit bars I wouldn't read while driving.
- Ease: 6.

**Tarea 3: all of today's offers, with what each paid per km**
- Path: Ver todas las ofertas de hoy → scroll. 2 taps.
- Success: yes. Answer: 10 offers.

| Hora | Neto por km | Qué pasó |
|---|---|---|
| 14:32 | $9.49 | La tomé |
| 12:10 | $2.20 | Se fue |
| 11:48 | sin lectura | — |
| 11:20 | $9.81 | La tomé |
| 10:41 | $9.41 | La tomé |
| 10:02 | $10.46 | La tomé |
| 9:51 | $9.16 | Se fue |
| 9:24 | $10.75 | La tomé |
| 8:40 | $9.20 | Se fue |
| 8:15 | $10.00 | La tomé |

- Where I hesitated: the task says "lo que pagó", but the list only shows "neto por km". To see what Uber actually paid I would have to open each offer.
- Ease: 6.

**Tarea 4: what I kept last week after all costs, car included**
- Path: Ganancias → Semana anterior → the "62 viajes tomados" row, to check the math. 3 taps.
- Success: yes. Answer: $4,980 for 12–18 oct. The breakdown sheet shows $8,400 − $1,920 (gas and wear) − $1,500 (car) = $4,980. The $1,500 a week matches my cuenta, which made me trust it.
- Where I hesitated: right under $4,980 it says "Leímos $5,028 · tu resumen dice $5,062". But the breakdown says the trips were $8,400. What is $5,028? I see three big numbers and only one is explained, and it shook my trust.
- Ease: 5.

**Tarea 5: which app paid more per km last week**
- Path: Comparar. 1 tap.
- Success: yes. Answer: DiDi, $10.34/km against Uber's $9.13/km, so $1.21 more per km.
- Where I hesitated: I only drive Uber, so a DiDi number I never earned makes me doubt the whole app. If it were my real phone, I'd think it was reading someone else's data.
- Ease: 6.

**Tarea 6: set my minimum to $6.50/km**
- Path: the "tu meta $8 · tu mínimo $6" row on Hoy → Tu meta y tu mínimo → "+" next to Tu mínimo → Guardar. 4 taps.
- Success: yes. The app showed «Guardado: tu mínimo ahora es $6.50/km».
- Where I hesitated: hardly anywhere. I liked that I didn't have to go through the gear icon.
- Ease: 6.

**Tarea 7: the semáforo stopped showing during my shift**
- Path: Abrir Kompara → Volver a dar el permiso → Abrir ajustes (prototype dead end: «Esta pantalla no está en esta prueba») → back → Que no vuelva a pasar. 5 taps.
- Success: yes. Answer: Android turned off the Accesibilidad permission, for example to save battery. The fix is to give the permission again. To stop it happening again, go to Ajustes › Aplicaciones › Kompara › Batería and choose "Sin restricciones".
- Where I hesitated: I didn't notice the small "K" in the status bar while I was in Uber, so I only found out by opening the app. The battery path is tiny grey text.
- Ease: 6.

**Tarea 9: what my last trip left me**
- Path: tap "La tomaste" (14:32) on Hoy. 1 tap.
- Success: yes. Answer: about $114. That's the $148 fare minus $34 for gas and wear, before the car cost, which comes off the day ($214 a day).
- Where I hesitated: I'd prefer to see what it left me after the cuenta, but the note explains why it doesn't.
- Ease: 6.

**(a) The three biggest problems**
1. **Ganancias, semana pasada.** "Uber reporta $34 más de lo que leímos · Leímos $5,028 · tu resumen dice $5,062" sits right under "$4,980", while the breakdown says $8,400 before costs. Nothing says what $5,028 is. Fix: put both figures on the same basis and name it, for example "Uber dice que cobraste $X, nosotros leímos $Y". It should match the $8,400 in the breakdown, or move inside the "Cómo se calculó" sheet.
2. **Setting up Accesibilidad (first day, and again when it switches off).** The three steps only appear before Android's Ajustes open, and the "control total" warning is scary. A driver who avoids Ajustes gets lost there. Fix: open the Kompara toggle directly if possible, or keep a small floating card or notification with the steps over Ajustes. Add a "¿No lo encuentras?" help link on return.
3. **Comparar › Tus apps for an Uber-only driver.** It shows a DiDi $/km she never earned, which makes her doubt every number. Fix: when only one app has trips, don't draw a comparison. Say "Solo manejaste Uber esta semana" and offer the "Tu ciudad" comparison instead.

**(b) Ease compared with the Uber Driver app:** 7/10.

**(c) Would I understand every screen without help?** 7/10.

**(d) The same tasks in my Uber Driver app:** today's earnings 6, last week's earnings 5, last trip's detail 4.

**(e) Would I install it?** Yes, I'd try it, because it takes off my cuenta and the gas and tells me right away whether a trip is worth it. But "control total" and the chance that Uber won't allow it make me nervous.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Ya lo activé (imagined Android settings) > Abrir Uber para probar","taps":5,"success":"yes","answer":"Semáforo on: green $9.49/km neto 'Arriba de tu meta' over the Uber offer","ease":4,"stuck":"Warning that Uber/DiDi may not allow the app and the 'control total' prompt scared her; the 3 steps vanish once inside Android Ajustes, so she'd fumble there; unsure whether 'costos típicos $2.84/km' fits her rented car, left it alone"},"1":{"path":"none (read Hoy)","taps":0,"success":"yes","answer":"About $548 today (6 trips, after gas, wear and car); green dot 'Semáforo funcionando'","ease":6,"stuck":"'aprox.' and the net figure can't be matched to what Uber shows"},"2":{"path":"none (one glance)","taps":0,"success":"yes","answer":"So-so: yellow, $7.16/km neto, between goal $8 and minimum $6; take it if nothing better","ease":6,"stuck":"none; small limit bars not readable at a glance"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":2,"success":"yes","answer":"10 offers: 14:32 $9.49, 12:10 $2.20, 11:48 sin lectura, 11:20 $9.81, 10:41 $9.41, 10:02 $10.46, 9:51 $9.16, 9:24 $10.75, 8:40 $9.20, 8:15 $10.00 (neto/km)","ease":6,"stuck":"List shows only net per km, not what Uber paid; must open each offer to see the fare"},"4":{"path":"Ganancias > Semana anterior > '62 viajes tomados' row (breakdown)","taps":3,"success":"yes","answer":"$4,980 (12-18 oct): $8,400 - $1,920 gas/wear - $1,500 car","ease":5,"stuck":"'Leímos $5,028 · tu resumen dice $5,062' under $4,980 contradicts the $8,400 in the breakdown; unexplained numbers hurt trust"},"5":{"path":"Comparar","taps":1,"success":"yes","answer":"DiDi: $10.34/km vs Uber $9.13/km (+$1.21)","ease":6,"stuck":"She only drives Uber, so a DiDi figure makes her doubt the data"},"6":{"path":"'tu meta $8 · tu mínimo $6' row on Hoy > Tu meta y tu mínimo > + on Tu mínimo > Guardar","taps":4,"success":"yes","answer":"Minimum saved at $6.50/km (confirmation shown)","ease":6,"stuck":"none"},"7":{"path":"Abrir Kompara > Volver a dar el permiso > Abrir ajustes (not in prototype) > back > Que no vuelva a pasar","taps":5,"success":"yes","answer":"Android turned off the Accesibilidad permission (e.g. battery saving); give the permission again, and set Ajustes > Aplicaciones > Kompara > Batería to 'Sin restricciones'","ease":6,"stuck":"Missed the small K icon in the status bar while in Uber; only found out by opening the app; battery path is tiny grey text"},"9":{"path":"La tomaste (14:32) on Hoy","taps":1,"success":"yes","answer":"About $114 before car cost ($148 fare - $34 gas/wear); car $214/day comes off the day","ease":6,"stuck":"Would prefer to see it after the car cost, but the note explains"}},"problems":[{"screen":"Ganancias, semana pasada","problem":"'Leímos $5,028 · tu resumen dice $5,062' sits under the $4,980 net while the breakdown says trips were $8,400; three big numbers with no common basis, so a driver who distrusts numbers stops trusting the app","fix":"Put both figures on the same basis and label them ('Uber dice que cobraste $X; nosotros leímos $Y'), matching the $8,400 gross, or move the reconciliation inside the 'Cómo se calculó' sheet"},{"screen":"Accessibility setup (onboarding and 'Volver a dar el permiso')","problem":"The 3 steps are shown only before Android Ajustes opens and the 'control total' warning is scary; a driver who avoids settings gets lost in Ajustes","fix":"Open the Kompara toggle directly if possible, or show a floating card or notification with the steps over Ajustes; add '¿No lo encuentras?' help on return"},{"screen":"Comparar > Tus apps","problem":"Shows a DiDi $/km for an Uber-only driver, so she doubts all the data","fix":"When only one app has trips, show 'Solo manejaste Uber esta semana' and point to the 'Tu ciudad' comparison instead of a two-app bar chart"}],"ease_vs_uber":7,"understand_all":7,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes, I'd try it because it takes off my weekly car cuenta and the gas and tells me right away whether a trip is worth it, but 'control total' and the chance that Uber won't allow it make me nervous."}
```

### Judge 2 (sonnet)

**SESSION REPORT — Luis, 28, drives Uber and DiDi, impatient, skims**

Tasks ran in the order 8, 1, 2, 3, 4, 5, 6, 7, 9. Taps count every action, including wrong ones. Back gestures and swipes are counted as actions. Scrolls are noted separately.

**Task 8 — Make the semáforo work from the welcome screen**
- Path: Empezar → Acepto → Abrir ajustes → (Ajustes de Android stand-in) Ya lo activé.
- Taps: 4. Success: yes.
- Answer: The Hoy screen showed "Semáforo preparado · permiso listo" and "Listo. Abre tu app de viajes".
- Hesitation: I skimmed the privacy screen and hit Acepto without reading it. The "control total" warning is explained with a fake example dialog, which is reassuring. The imagined Android steps looked doable: find Kompara, flip the switch, tap Permitir. The status dot on "Semáforo preparado" is an empty circle, which reads as "not done" even though the line under it says "permiso listo".
- Ease: 6.

**Task 1 — Today's earnings and whether the app is reading offers**
- Path: nothing to tap, the answer was on the Hoy screen.
- Taps: 0. Success: yes.
- Answer: About $548 so far today, net. The line "Semáforo funcionando" has a green dot, so it is reading.
- Hesitation: None. "aprox." made me wonder how exact the number is.
- Ease: 7.

**Task 2 — One look at an Uber offer: worth it and why**
- Path: none, one look at the screen.
- Taps: 0. Success: yes.
- Answer: Yellow diamond, $7.16/km net, "Entre tu meta y tu mínimo". It is above my $6 floor but below my $8 goal, so it's a so-so trip. Decent but not great, take it only if I'm slow.
- Hesitation: Yellow doesn't say take or skip. I still had to decide myself. The yellow chip sits over the lower map, close to the offer card.
- Ease: 6.

**Task 3 — All of today's offers with net $/km**
- Path: Ver todas las ofertas de hoy, then scroll down once.
- Taps: 1 (plus 1 scroll). Success: yes.
- Answer: 10 offers today with net $/km each. Examples: 14:32 $9.49 taken, 12:10 $2.20 skipped, 11:48 unreadable, 11:20 $9.81 taken, 10:41 $9.41 taken, 10:02 $10.46 taken, 9:51 $9.16 skipped, 9:24 $10.75 taken, 8:40 $9.20 skipped, 8:15 $10.00 taken.
- Hesitation: The "neto por km" label is small and grey. Only 6 of the 10 offers fit on the first screen, so I had to scroll.
- Ease: 6.

**Task 4 — Last week's net after all costs, including the car**
- Path: Ganancias → Semana anterior.
- Taps: 2. Success: yes.
- Answer: $4,980 approx. for 12–18 oct, after gasoline, wear and the car.
- Hesitation: The row "Uber reporta $34 más de lo que leímos · Leímos $5,028 · tu resumen dice $5,062" put three other numbers next to $4,980. I wasn't sure which one was mine. I took the big one. The tab is where I expect it, like Uber's Ganancias.
- Ease: 6.

**Task 5 — Which app paid more per km last week**
- Path: Ganancias → Semana anterior → the "62 viajes tomados" row (a breakdown sheet with no Uber/DiDi split) → back → Comparar.
- Taps: 5 (4 taps and 1 back gesture). Success: yes.
- Answer: DiDi paid more, $10.34/km against Uber's $9.13/km, $1.21 more per km, for 12–18 oct.
- Hesitation: I started in Ganancias, where Uber keeps earnings, and the Uber/DiDi split isn't there. I tried the main-number row first and got a cost breakdown. Then "Comparar" seemed the obvious place for comparing. The week I'd picked carried over, which helped. The caveat "Manejaste cada app a distintas horas" has a chevron, but I didn't open it.
- Ease: 5.

**Task 6 — Change the minimum to $6.50/km**
- Path: the "tu meta $8 · tu mínimo $6" line on Hoy → Semáforo page → Tu meta y tu mínimo → "+" on the mínimo row (tapxy) → Guardar.
- Taps: 4. Success: yes. The app confirmed «Guardado: tu mínimo ahora es $6.50/km».
- Hesitation: The line on Hoy doesn't look tappable, and I landed on a status page first. The "+" steps by $0.50, so one tap did it. The tip "Toca la cifra para escribirla" is small. Guardar is greyed until you change something.
- Ease: 6.

**Task 7 — Semáforo stopped showing: why, and what to do**
- Path: swipe down from the top (a green "K" icon sat in the status bar) → tapped the notification "Se apagó el semáforo".
- Taps: 2. Success: yes.
- Answer: Android turned off Kompara's accessibility permission, for example from battery saving. Without it Kompara can't see offers. The fix is the big button "Volver a dar el permiso". "Que no vuelva a pasar" is the follow-up.
- Hesitation: The notification says "ayer 20:31", so it had been off since last night, not during my shift. I never opened "Que no vuelva a pasar", so I don't know what it does. I stopped at the answer and did not tap "Volver a dar el permiso".
- Ease: 6.

**Task 9 — What my last trip left me**
- Path: the first "La tomaste" row in Últimas ofertas, which opened a sheet.
- Taps: 1. Success: yes.
- Answer: The 14:32 trip left about $114, before the car cost: $148 fare minus $34 gasoline and wear. That is $9.49/km net and $285/h over 24 min. The car ($214/day) comes off the whole day, not each trip.
- Hesitation: The headline says "antes de lo del coche", so $114 isn't the final take, and I'd have to do my own subtraction. The list says "ofertas" while the task asks about "viajes", but the top "La tomaste" row was obviously the last one.
- Ease: 6.

**(a) Three biggest usability problems**

1. **Hoy header and Semáforo page (tasks 6 and 7).**
   - Problem: The line "tu meta $8 · tu mínimo $6" and the gear don't look like the way to edit limits. I went through the Semáforo status page and then a second page. The screen for "why it stopped" and the screen for "change my limits" are mixed together.
   - Fix: Make the limits line look like a button, or add an "Editar" link on it that jumps straight to the stepper screen. Put a "Volver a dar el permiso" shortcut directly on the Semáforo page.

2. **Ganancias week view (tasks 4 and 5).**
   - Problem: The week total is mixed with the line about what Uber reports, and there is no Uber vs DiDi split there. The comparison lives under a different tab. On the "Uber reporta $34 más" row, three figures ($4,980, $5,028, $5,062) sit close together.
   - Fix: Add a small "Uber $X/km · DiDi $Y/km" line under the week total that links to Comparar. Move the Uber reconciliation into a collapsed "Cuadrar con Uber" row so the net number stands alone.

3. **Per-trip sheet and the net label (tasks 3 and 9).**
   - Problem: The trip sheet says "antes de lo del coche", so $114 isn't my real take for that trip. The list says "ofertas" where a driver thinks "viajes". "neto por km" is tiny grey text.
   - Fix: Show the per-trip figure as the headline, with the car share beside it as "tu parte del coche hoy". Label the list "Ofertas de hoy · $/km neto" in clearer type.

**(b) Ease vs the Uber Driver app:** 7/10. Hoy, Ganancias and Comparar are easy to read, and the numbers are big. The semáforo itself is clearer than anything Uber shows. It loses points because I had to hunt for settings and for the Uber/DiDi split, where Uber has one fixed earnings page.

**(c) Would I understand every screen without help:** 8/10. Most screens are plain. The ones I'd stumble on are the Hoy limits line, the "Uber reporta $34 más" reconciliation row, and "Que no vuelva a pasar".

**(d) Same tasks in my Uber Driver app:** Task 1, today's earnings: 7. Task 4, last week's earnings: 6. Task 9, last trip's detail: 6.

**(e) Install:** Yes. The traffic-light verdict is the part I would use all day, and the net numbers answer what I actually ask myself, as long as the permission step doesn't make me worry about Uber or DiDi banning me.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (Ajustes de Android) Ya lo activé","taps":4,"success":"yes","answer":"Hoy shows 'Semáforo preparado · permiso listo' and 'Listo. Abre tu app de viajes'","ease":6,"stuck":"Skimmed the privacy screen. The empty circle next to 'Semáforo preparado' looks not done even though the line below says permiso listo."},"1":{"path":"none, answer on the Hoy screen","taps":0,"success":"yes","answer":"About $548 today net; Semáforo funcionando with a green dot, so it is reading offers","ease":7,"stuck":"none; 'aprox.' makes me wonder how exact it is"},"2":{"path":"none, one look at the offer screen","taps":0,"success":"yes","answer":"Yellow diamond, $7.16/km net, between goal $8 and minimum $6: so-so, above my floor but not great","ease":6,"stuck":"Yellow does not say take or skip; I still have to decide myself."},"3":{"path":"Ver todas las ofertas de hoy, then scroll down once","taps":1,"success":"yes","answer":"10 offers today with net $/km each: 9.49, 2.20, unreadable, 9.81, 9.41, 10.46, 9.16, 10.75, 9.20, 10.00","ease":6,"stuck":"Only 6 of 10 offers fit on screen so I had to scroll; 'neto por km' label is tiny grey text."},"4":{"path":"Ganancias > Semana anterior","taps":2,"success":"yes","answer":"$4,980 approx. for 12-18 oct after gasoline, wear and the car","ease":6,"stuck":"The row 'Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062' puts three other numbers next to my $4,980; I wasn't sure which one was mine."},"5":{"path":"Ganancias > Semana anterior > '62 viajes tomados' row (cost breakdown sheet, wrong) > back > Comparar","taps":5,"success":"yes","answer":"DiDi paid more: $10.34/km vs Uber $9.13/km, $1.21 more per km, week of 12-18 oct","ease":5,"stuck":"Started in Ganancias where Uber keeps earnings, but the app split is not there. Tried the main-number row first and got a cost sheet. Comparar was the next obvious guess."},"6":{"path":"Hoy 'tu meta $8 · tu mínimo $6' line > Semáforo page > Tu meta y tu mínimo > '+' on mínimo (tapxy) > Guardar","taps":4,"success":"yes","answer":"Minimum changed to $6.50/km; app confirmed 'Guardado: tu mínimo ahora es $6.50/km'","ease":6,"stuck":"The limits line on Hoy does not look tappable and led to a status page first; the '+' is an icon with no text; the 'Toca la cifra para escribirla' tip is small."},"7":{"path":"swipe down from top > tap notification 'Se apagó el semáforo'","taps":2,"success":"yes","answer":"Android turned off Kompara's accessibility permission (e.g. from battery saving), so Kompara cannot see offers. Fix: 'Volver a dar el permiso'; 'Que no vuelva a pasar' is the follow-up.","ease":6,"stuck":"Notification says 'ayer 20:31' though the task said it stopped during the shift; never opened 'Que no vuelva a pasar'; did not tap the fix button."},"9":{"path":"Hoy > first 'La tomaste' row (14:32) in Últimas ofertas","taps":1,"success":"yes","answer":"About $114 before the car cost ($148 fare - $34 gasoline and wear), $9.49/km net, $285/h, 24 min; the car $214/day is taken off the day, not per trip","ease":6,"stuck":"Headline says 'antes de lo del coche' so $114 is not the full take; list says 'ofertas' while the task asks about 'viajes'."}},"problems":[{"screen":"Hoy header line 'tu meta $8 · tu mínimo $6', and the Semáforo page behind it","problem":"The limits line does not look like the way to edit limits, so I went through the Semáforo status page and a second page. 'Why it stopped' and 'change my limits' are mixed on the same screens.","fix":"Make the limits line look like a button or add an 'Editar' link that jumps to the stepper screen; put a 'Volver a dar el permiso' shortcut on the Semáforo page."},{"screen":"Ganancias week view and Comparar","problem":"The week total is mixed with the 'Uber reporta $34 más' reconciliation, which adds three extra figures ($4,980, $5,028, $5,062). There is no Uber vs DiDi split on Ganancias; it lives under Comparar, so I took a detour.","fix":"Add a one-line 'Uber $X/km · DiDi $Y/km' under the week total that links to Comparar; collapse the Uber reconciliation into its own 'Cuadrar con Uber' row."},{"screen":"Per-trip sheet and the offers list","problem":"The trip sheet's headline $114 says 'antes de lo del coche', so it is not my real take for the trip. The list says 'ofertas' where a driver thinks 'viajes', and 'neto por km' is tiny grey text.","fix":"Show the per-trip figure as the headline with the car share beside it as 'tu parte del coche hoy'; label the list 'Ofertas de hoy · $/km neto' in clearer type."}],"ease_vs_uber":7,"understand_all":8,"uber_seq":{"1":7,"4":6,"9":6},"install":"Yes: the traffic-light verdict is what I'd use all day and the net numbers answer what I actually ask myself, as long as the permission step doesn't risk a ban from Uber or DiDi."}
```

### Judge 3 (opus)

**Session report: Martha, 41. She rents her car, drives Uber and DiDi, has medium comfort with phones and is red-green colour-blind (she goes by shapes, positions and words, never colour).**

**Task 8: first day, make the semáforo work**
- **Path:** Empezar → Acepto (data screen) → Abrir ajustes → *(in the Ajustes de Android stand-in I imagined following the 3 steps)* → Ya lo activé → lands on Hoy («Semáforo preparado ✓ permiso listo») → Abrir Uber para probar. The test offer showed the chip with ▲ «$9.49 /km neto · Arriba de tu meta».
- **Taps:** 5
- **Success:** yes
- **Answer:** The semáforo works. Hoy says «permiso listo», and over the Uber offer I saw the ▲ chip with its words.
- **Hesitation:** On the data screen, «Uber y DiDi … podrían no permitir apps como esta» scared me a little: could they block my account? «Control total de tu dispositivo» also sounds scary, but the screen explains it ahead of time and says Kompara never accepts trips for me. I'd manage the 3 numbered steps, though I might hunt for whether Kompara is under «Apps instaladas» or «Apps descargadas».
- **Ease:** 6

**Task 1: how much today, and is it reading offers?**
- **Path:** none. It was all on Hoy.
- **Taps:** 0
- **Success:** yes
- **Answer:** About $548 so far today, after gas, wear and car (6 trips). It is reading: «Semáforo funcionando».
- **Hesitation:** The little dot next to «Semáforo funcionando» means nothing to me because it's only colour, but the words say it.
- **Ease:** 7

**Task 2: one-glance offer**
- **Path:** a single look at the chip
- **Taps:** 0
- **Success:** yes
- **Answer:** «Más o menos» (so-so). The diamond and «Entre tu meta y tu mínimo» say $7.16/km neto: below my $8 goal, above my $6 minimum. I'd take it only if nothing better comes.
- **Hesitation:** none. The diamond shape and the words carry the meaning without colour, and the little marker sits between the two lines.
- **Ease:** 7

**Task 3: all of today's offers, with net per km**
- **Path:** Ver todas las ofertas de hoy → scroll down
- **Taps:** 1 (+1 scroll)
- **Success:** yes
- **Answer:** 10 offers, net per km:

| Time | Result | Net per km |
|---|---|---|
| 14:32 | taken | $9.49 |
| 12:10 | gone without taking it | $2.20 |
| 11:48 | no reading | – |
| 11:20 | taken | $9.81 |
| 10:41 | taken | $9.41 |
| 10:02 | taken | $10.46 |
| 9:51 | gone without taking it | $9.16 |
| 9:24 | taken | $10.75 |
| 8:40 | gone without taking it | $9.20 |
| 8:15 | taken | $10.00 |

- **Hesitation:** The column says «neto por km», so I wasn't sure it's "what it paid" or what it leaves me after costs. The fare itself isn't in the list. The row icons work for me because the fill sits at the top for «arriba» and at the bottom for «abajo».
- **Ease:** 6

**Task 4: last week, after all costs including the car**
- **Path:** Ganancias → Semana anterior → «62 viajes tomados» (to check that my rent was counted)
- **Taps:** 3
- **Success:** yes
- **Answer:** About $4,980 for Oct 12–18. That is $8,400 − $1,920 gas and wear − $1,500 car per week.
- **Hesitation:** The line «Uber reporta $34 más de lo que leímos · Leímos $5,028 · tu resumen dice $5,062» confused me. $5,028 of what, if the trips total $8,400? I guessed it's Uber only, but it doesn't say. I also had to open the breakdown to be sure the car meant my rent.
- **Ease:** 6

**Task 5: which app paid more per km last week**
- **Path:** Comparar. It was already on last week.
- **Taps:** 1
- **Success:** yes
- **Answer:** DiDi: $10.34/km against Uber's $9.13/km, so $1.21 more.
- **Hesitation:** «Manejaste cada app a distintas horas» is a bit unclear, but it didn't get in the way. The bars are black and grey, not colours, which is good for me.
- **Ease:** 7

**Task 6: change my minimum to $6.50/km**
- **Path:** «tu meta $8 · tu mínimo $6» row on Hoy → Tu meta y tu mínimo → + on Tu mínimo → Guardar → «Guardado: tu mínimo ahora es $6.50/km»
- **Taps:** 4
- **Success:** yes
- **Answer:** The minimum is now $6.50/km.
- **Hesitation:** none. The ▲ and ▼ next to «meta» and «mínimo» tell them apart without colour.
- **Ease:** 7

**Task 7: the semáforo stopped during my shift**
- **Path:** I saw the K in the status bar → swipe down from the top → notification «Se apagó el semáforo» → Hoy explained it → Volver a dar el permiso → Abrir ajustes → back → Que no vuelva a pasar
- **Taps:** 6
- **Success:** yes
- **Answer:** Android switched off Kompara's accessibility permission yesterday at 20:31, for example to save battery. The fix is to give the permission again: Ajustes → Kompara → activar → Permitir. To stop it happening again, set Ajustes › Aplicaciones › Kompara › Batería to «Sin restricciones».
- **Hesitation:** In the re-grant flow, «Abrir ajustes» only showed «Esta pantalla no está en esta prueba», so «Ya lo activé» never appeared. That's a gap in the prototype, not the design. The notification itself was very clear.
- **Ease:** 6

**Task 9: what my last trip left me**
- **Path:** «La tomaste 14:32» on Hoy
- **Taps:** 1
- **Success:** yes
- **Answer:** About $114: a $148 fare minus $34 gas and wear ($9.49/km, $285/h, 24 min). That is before the car cost.
- **Hesitation:** Because I rent, «antes de lo del coche» made me stop. The trip shows $114 before rent, while Hoy's $548 includes rent, so the two "te deja" numbers work differently.
- **Ease:** 7

**(a) The three biggest problems**

1. **Ganancias, last week, the line «Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062».**
   - **Problem:** The numbers don't match anything else on the screen ($8,400 trips, $4,980 net). It isn't clear it's Uber only, or whether it's before or after costs.
   - **Fix:** Label it, for example «Solo Uber, antes de gastos: leímos $5,028 · tu resumen de Uber dice $5,062 (+$34)».
2. **Trip detail sheet, «Te deja antes de lo del coche $114».**
   - **Problem:** For someone who rents, the big number per trip leaves out the rent, but Hoy's big number includes it. Two different meanings of "te deja" side by side.
   - **Fix:** Add a second line under it, such as «Con tu renta del día repartida: ~$X». Or make the label unmistakable: «Antes de tu renta».
3. **Ofertas de hoy list.**
   - **Problem:** It shows only net $/km («neto por km»). The fare the platform offered isn't there, so I had to guess whether the figure is what the trip paid or what it leaves me, and I'd have to open each row for the fare.
   - **Fix:** Put the fare under each $/km (e.g. «$148 · 12 km»), and say «te deja por km» instead of only «neto por km».

Minor: the dot by «Semáforo funcionando» relies on colour alone. The words save it, but a ✓ or ⊘ shape would match the rest of the app.

**(b) Ease compared with the Uber Driver app: 8/10.** It's as simple as Uber's earnings screen, and it answers "after costs", which Uber never does.

**(c) Would I understand every screen without help? 8/10.** Only the Ganancias reconciliation line and the «antes de lo del coche» wording needed thinking.

**(d) Tasks 1, 4 and 9 in my Uber Driver app:** today's earnings 6, last week's earnings 5 (gross only, never after costs), last trip's detail 4 (buried in the activity list).

**(e)** Yes, I'd install it: the shapes and words tell me at a glance, without depending on red and green, whether a trip covers my rent and gas.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (imagined 3 steps in Ajustes de Android) > Ya lo activé > Hoy shows 'Semáforo preparado, permiso listo' > Abrir Uber para probar (chip ▲ $9.49/km neto Arriba de tu meta appeared)","taps":5,"success":"yes","answer":"Semáforo working: Hoy says permiso listo and the ▲ chip appeared over the Uber test offer","ease":6,"stuck":"Data screen warning that Uber/DiDi 'podrían no permitir apps como esta' and the 'control total' prompt felt scary; imagined settings step might need some searching between 'Apps instaladas' and 'Apps descargadas'"},"1":{"path":"none, read Hoy","taps":0,"success":"yes","answer":"About $548 today after gas, wear and car (6 trips); 'Semáforo funcionando' so it is reading offers","ease":7,"stuck":"Status dot is colour-only, but the words were enough"},"2":{"path":"single glance at chip","taps":0,"success":"yes","answer":"So-so: diamond, $7.16/km neto, between my $8 goal and $6 minimum; take it only if nothing better","ease":7,"stuck":"none; diamond shape plus words work without colour"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: 14:32 $9.49 taken, 12:10 $2.20 skipped, 11:48 no reading, 11:20 $9.81, 10:41 $9.41, 10:02 $10.46, 9:51 $9.16 skipped, 9:24 $10.75, 8:40 $9.20 skipped, 8:15 $10.00 (net per km)","ease":6,"stuck":"Unsure if 'neto por km' is what the trip paid or what it leaves me; fare not shown in list"},"4":{"path":"Ganancias > Semana anterior > 62 viajes tomados (breakdown sheet)","taps":3,"success":"yes","answer":"About $4,980 for 12-18 Oct ($8,400 - $1,920 gas/wear - $1,500 car)","ease":6,"stuck":"'Uber reporta $34 más / Leímos $5,028 · tu resumen dice $5,062' did not match $8,400 or $4,980, unclear it is Uber-only; opened breakdown to confirm rent was included"},"5":{"path":"Comparar (already on last week)","taps":1,"success":"yes","answer":"DiDi: $10.34/km vs Uber $9.13/km ($1.21 more)","ease":7,"stuck":"'Manejaste cada app a distintas horas' slightly unclear, not blocking"},"6":{"path":"limits row on Hoy > Tu meta y tu mínimo > + on Tu mínimo > Guardar","taps":4,"success":"yes","answer":"Minimum saved as $6.50/km (confirmation shown)","ease":7,"stuck":"none"},"7":{"path":"swipe down from top > notification 'Se apagó el semáforo' > Hoy explains > Volver a dar el permiso > Abrir ajustes (prototype: screen not in test) > back > Que no vuelva a pasar","taps":6,"success":"yes","answer":"Android turned off Kompara's accessibility permission (e.g. battery saving) yesterday 20:31; re-grant the permission in Ajustes and set Batería to 'Sin restricciones' so it doesn't happen again","ease":6,"stuck":"Re-grant 'Abrir ajustes' hit a prototype gap ('Esta pantalla no está en esta prueba'), not a design problem"},"9":{"path":"La tomaste 14:32 on Hoy","taps":1,"success":"yes","answer":"About $114 before car cost ($148 fare - $34 gas/wear), $9.49/km, 24 min","ease":7,"stuck":"'antes de lo del coche': as a renter I want the after-rent figure; Hoy total includes rent but the trip figure does not"}},"problems":[{"screen":"Ganancias, last week, line 'Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062'","problem":"Numbers match nothing else on the screen ($8,400 trips, $4,980 net); unclear it is Uber-only and whether before or after costs","fix":"Label it: 'Solo Uber, antes de gastos: leímos $5,028 · tu resumen de Uber dice $5,062 (+$34)'"},{"screen":"Trip detail sheet 'Te deja antes de lo del coche $114'","problem":"For a renter the per-trip headline excludes rent while Hoy's headline includes it; two meanings of 'te deja'","fix":"Add a line 'Con tu renta del día repartida: ~$X' or label the big number 'Antes de tu renta'"},{"screen":"Ofertas de hoy list","problem":"Only net $/km ('neto por km'); the offered fare is missing, so unclear whether the figure is what it paid or what it leaves","fix":"Show fare and km under each row (e.g. '$148 · 12 km') and say 'te deja por km'"}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes: the shapes and words tell me at a glance, without depending on red and green, whether a trip covers my rent and gas."}
```

The session log is at /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk6/j3/log.json and the screenshots are in /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk6/j3.

### Judge 4 (sonnet)

Session report: persona Jorge, 60, cautious, large text and reading glasses.

All nine tasks were done through the tool only. A gesture is a scroll, swipe or back, and I list gestures separately from taps.

**Task 8: Make the semáforo work from the welcome screen**
- Path: Empezar, then Acepto (after reading the whole "Lo que hace Kompara" screen), then Abrir ajustes, then (stand-in Android screen, imagined the 3 steps) Ya lo activé.
- Taps: 4. Success: yes.
- Answer: Hoy now says «Semáforo preparado · permiso listo» and «Listo. Abre tu app de viajes».
- Hesitation:
  - The line «Uber y DiDi… podrían no permitir apps como esta» made me pause. I drive DiDi mostly and I'm afraid of a ban.
  - The small grey text («Puede estar en Apps instaladas…», and the fake «Rechazar / Permitir» in the example box) is hard to read with glasses.
  - The greyed example buttons look tappable.
  - The final button says «Abrir Uber para probar», but I'm mostly on DiDi.
- Ease: 5.

**Task 1: How much today, and is the app reading offers?**
- Path: none (Hoy is the opening screen).
- Taps: 0. Success: yes.
- Answer: about $548 after gasoline, wear and car, from 6 trips. «Semáforo funcionando» with a green dot, last reading at 14:32.
- Hesitation: «aprox.» made me wonder how exact it is. The green dot is small, so I relied on the words.
- Ease: 6.

**Task 2: Uber offer, one glance**
- Path: looked once.
- Taps: 0. Success: yes.
- Answer: yellow diamond, $7.16 per km neto, «Entre tu meta y tu mínimo». It is above my minimum of $6 but under my goal of $8, so it is a so-so trip I could take, not a great one. I did not get a clear take-it-or-not from it.
- Hesitation: «Entre tu meta y tu mínimo» is a long phrase to parse while driving. The yellow doesn't tell me what to do.
- Ease: 5.

**Task 3: All of today's offers with $/km**
- Path: Ver todas las ofertas de hoy, then scroll down (1 gesture).
- Taps: 1. Success: yes.
- Answer: 10 offers.

| Time | Outcome | $/km neto |
|---|---|---|
| 14:32 | La tomaste | $9.49 |
| 12:10 | Se fue sin tomar | $2.20 |
| 11:48 | Sin lectura | none |
| 11:20 | La tomaste | $9.81 |
| 10:41 | La tomaste | $9.41 |
| 10:02 | La tomaste | $10.46 |
| 9:51 | Se fue sin tomar | $9.16 |
| 9:24 | La tomaste | $10.75 |
| 8:40 | Se fue sin tomar | $9.20 |
| 8:15 | La tomaste | $10.00 |

- Hesitation: the header says 10 offers but only 6 show until I scroll. The column heading «neto por km» is small.
- Ease: 6.

**Task 4: Last week, after all costs including the car**
- Path: Ganancias, then Semana anterior.
- Taps: 2. Success: yes.
- Answer: $4,980 («Te quedaron esa semana · aprox.»), 12–18 oct, 62 trips, with gasoline, wear and car already subtracted.
- Hesitation: the row «Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062» confused me. Those figures don't match my $4,980, and I couldn't tell which number to trust.
- Ease: 6.

**Task 5: Which app paid more per km last week**
- Path: Comparar (my guess from the word). It already showed last week, 12–18 oct.
- Taps: 1. Success: yes.
- Answer: DiDi, $10.34 per km against Uber $9.13, so DiDi paid $1.21 more.
- Hesitation: I guessed the tab. The caveat «Manejaste cada app a distintas horas» made me doubt the comparison a little.
- Ease: 6.

**Task 6: Set my minimum to $6.50 per km**
- Path: tapped the «tu meta $8 · tu mínimo $6» line on Hoy, then Tu meta y tu mínimo, then the "+" on mínimo once ($0.50 step, no text on the icon), then Guardar. The note said «Guardado: tu mínimo ahora es $6.50/km».
- Taps: 4. Success: yes.
- Hesitation: I wasn't sure where settings live. I nearly tried the gear icon. The "+" has no words. The Guardar button was grey until I changed something.
- Ease: 5.

**Task 7: The semáforo stopped showing during my shift**
- Path: saw a «K» in the status bar of the Uber screen, swiped down from the top (1 gesture), tapped «Se apagó el semáforo», then Volver a dar el permiso, then Abrir ajustes. That gave the note «Esta pantalla no está en esta prueba». I went back (1 gesture) and tapped Que no vuelva a pasar.
- Taps: 4. Success: yes.
- Answer: Android turned off the Accessibility permission, maybe from battery saving, since «Android a veces lo apaga». Fix: give the permission again (find Kompara, switch it on, tap Permitir). To prevent a repeat, set Kompara's battery to «Sin restricciones» (Ajustes › Aplicaciones › Kompara › Batería).
- Hesitation:
  - I only noticed the problem because of the tiny «K».
  - «Volver a dar el permiso» repeated the whole first-day screen.
  - The battery fix is a small link («Que no vuelva a pasar») under the main button, and I might have missed it.
  - Abrir ajustes showed a «not in this test» message, so I couldn't actually restore it.
  - On the Semáforo screen, «Ofertas sin semáforo · ninguna hoy» contradicts the «Sin lectura 11:48» offer on Hoy.
- Ease: 5.

**Task 9: What my last trip left me**
- Path: tapped the top «La tomaste» row (14:32) on Hoy. A sheet opened.
- Taps: 1. Success: yes.
- Answer: $114 «antes de lo del coche»: the $148 fare minus $34 for gasoline and wear (12 km × $2.84). The car cost ($214 a day) comes off the day, not each trip.
- Hesitation: the list says «Últimas ofertas», not trips, so I assumed the last "La tomaste" row. «Te deja antes de lo del coche» means this $114 isn't what I really keep. The $148 is labelled «el pago final puede cambiar».
- Ease: 5.

**(a) Three biggest usability problems**
1. Offer card and Hoy rows (task 2). A yellow diamond with «Entre tu meta y tu mínimo» takes two comparisons to read. The text is small, and a cautious reader doesn't get a clear take-it-or-not. Fix: a one-or-two-word verdict at glance size, such as «Regular · $7.16/km», plus a short line like «arriba de tu mínimo». The list rows could carry a plain Tomar/Dudar/No verdict.
2. Money screens (tasks 1, 4, 9). Near-identical figures mean different things: $548 «aprox.» (after car), $114 (before car), $4,980 (after car), and «Leímos $5,028 · tu resumen dice $5,062» next to «Uber reporta $34 más». I can't tell what I really keep or which figure to trust. Fix: use one label everywhere, such as «Te quedó (ya con coche)» and «Este viaje deja (sin coche)». Move the Uber reconciliation behind «Ver detalle», or reword it as one plain sentence.
3. Permission-lost and onboarding flow (tasks 7 and 8).
   - The «K» in the status bar is easy to miss.
   - The recovery screen repeats the whole first-day text, and its example dialog has greyed «Rechazar / Permitir» that look tappable.
   - The battery step is a secondary link, and the fine print is small and light grey.
   - The Android settings step dead-ended at «Esta pantalla no está en esta prueba».
   - Fix: let one screen do both steps (permission, then battery «Sin restricciones»), label the example box «Así se verá en Android, no lo toques», and use text of 16sp or larger in dark grey that follows the phone's font size. Also show a red banner on Hoy when the semáforo is off.

Smaller issues: the button says «Abrir Uber» but I'm mostly on DiDi, and «ninguna hoy» contradicts the «Sin lectura» row.

**(b) Ease against the Uber Driver app:** 7 out of 10. The main numbers are clear and the tabs behave as expected. Uber's Ganancias screen is simpler, with one big number, whereas Kompara asks me to read much more and some labels were hard to follow.

**(c) Would I understand every screen without help:** 6 out of 10.

**(d) Same tasks in my Uber Driver app:** task 1 (today's earnings) 6, task 4 (last week's earnings) 5, task 9 (last trip's detail) 4.

**(e) Install:** Yes, probably. It shows what a trip really leaves me and what each app pays per km, but I'd want bigger text and simpler money labels, and I'd first want to be sure Uber and DiDi won't ban me.

The session log is saved at /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk6/j4/log.json.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (Ajustes de Android, imagined the 3 steps) Ya lo activé","taps":4,"success":"yes","answer":"Hoy shows «Semáforo preparado · permiso listo» and «Listo. Abre tu app de viajes»; the semáforo is working","ease":5,"stuck":"Fine print is small and grey; the example Android box with greyed Rechazar/Permitir looks tappable; the warning that Uber and DiDi may not allow apps like this worried me; the button says Abrir Uber but I drive mostly DiDi"},"1":{"path":"Opening screen (Hoy), no taps","taps":0,"success":"yes","answer":"About $548 today after gasoline, wear and car, 6 trips; «Semáforo funcionando» with a green dot, last reading 14:32","ease":6,"stuck":"«aprox.» made me unsure how exact it is; the status dot is small, so I relied on the words"},"2":{"path":"One glance at the offer card","taps":0,"success":"yes","answer":"Yellow diamond, $7.16 per km neto, between my goal $8 and my minimum $6: so-so, above my minimum, I could take it but it is not great","ease":5,"stuck":"«Entre tu meta y tu mínimo» is long to read at a glance and yellow doesn't say take or skip"},"3":{"path":"Ver todas las ofertas de hoy > scroll down (1 gesture)","taps":1,"success":"yes","answer":"10 offers today with $/km neto: 9.49, 2.20, none (sin lectura), 9.81, 9.41, 10.46, 9.16, 10.75, 9.20, 10.00","ease":6,"stuck":"Header says 10 offers but only 6 are visible until I scroll; «neto por km» heading is small"},"4":{"path":"Ganancias > Semana anterior","taps":2,"success":"yes","answer":"$4,980 left last week (12–18 oct), 62 trips, after gasoline, wear and car","ease":6,"stuck":"The row «Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062» confused me; the numbers don't match $4,980"},"5":{"path":"Comparar (guessed from the word); already on last week","taps":1,"success":"yes","answer":"DiDi paid more: $10.34/km vs Uber $9.13/km, $1.21 more, week 12–18 oct","ease":6,"stuck":"Had to guess the tab name; the caveat «Manejaste cada app a distintas horas» raised some doubt"},"6":{"path":"Tapped the «tu meta $8 · tu mínimo $6» line on Hoy > Tu meta y tu mínimo > + on mínimo once > Guardar","taps":4,"success":"yes","answer":"Saved: «tu mínimo ahora es $6.50/km»","ease":5,"stuck":"Not obvious where limits live; nearly tried the gear; the + button has no words; Guardar greyed until a change"},"7":{"path":"Saw K in the status bar > swipe down from top (1 gesture) > Se apagó el semáforo > Volver a dar el permiso > Abrir ajustes (prototype note: «Esta pantalla no está en esta prueba») > back (1 gesture) > Que no vuelva a pasar","taps":4,"success":"yes","answer":"Android turned off the Accessibility permission (e.g. battery saving). Give the permission again (find Kompara, switch on, tap Permitir) and set Kompara's battery to Sin restricciones so it doesn't happen again","ease":5,"stuck":"Only noticed via a tiny K; Volver a dar el permiso repeats the whole first-day text; battery fix is a small secondary link; settings step dead-ended; Semáforo screen says «ninguna hoy» for offers sin semáforo but Hoy listed a Sin lectura offer"},"9":{"path":"Tapped the 14:32 La tomaste row on Hoy; read the detail sheet","taps":1,"success":"yes","answer":"$114 before the car cost: $148 fare minus $34 gasoline and wear; car ($214 a day) is taken from the day, not per trip","ease":5,"stuck":"List says Últimas ofertas, not trips, so I assumed the last La tomaste; «antes de lo del coche» means it isn't what I truly keep; «el pago final puede cambiar»"}},"problems":[{"screen":"Offer card (semáforo) and offer rows on Hoy","problem":"Yellow verdict reads «Entre tu meta y tu mínimo», which needs two comparisons and gives no clear take-it-or-not; text is small for a driver with reading glasses","fix":"Use a one-or-two-word verdict at glance size (e.g. «Regular · $7.16/km») with a short hint like «arriba de tu mínimo»; give list rows a plain Tomar/Dudar/No verdict"},{"screen":"Hoy, Ganancias, and offer detail sheet (money numbers)","problem":"Similar figures mean different things ($548 after car, $114 before car, $4,980 after car, plus «Leímos $5,028 · tu resumen dice $5,062» next to «Uber reporta $34 más»); I can't tell what I really keep or which number to trust","fix":"Use one label everywhere («Te quedó (ya con coche)» vs «Este viaje deja (sin coche)»); move the Uber reconciliation behind «Ver detalle» or reword it as one plain sentence"},{"screen":"Permission-lost recovery and first-day permission screens","problem":"The K in the status bar is easy to miss; recovery repeats the whole first-day text; the example dialog's greyed Rechazar/Permitir looks tappable; the battery fix is a small secondary link; fine print is small and light grey; Abrir ajustes dead-ended","fix":"Show a red banner on Hoy when the semáforo is off; do permission then battery «Sin restricciones» on one screen; label the example box «Así se verá en Android, no lo toques»; use 16sp-or-larger dark text that follows the phone's font size"}],"ease_vs_uber":7,"understand_all":6,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes, probably: it shows what a trip really leaves me and which app pays more per km, but I'd want bigger text, simpler money labels, and reassurance that Uber and DiDi won't ban me."}
```

### Judge 5 (sonnet)

SESSION REPORT — Persona: Ana, 34, rents her car, drives Uber, practical, wants the number and the next step. All tasks were done through the tool. I did them in order 8, 1–7, 9. I counted a tap as any tap, tapxy, back or swipe. Scrolls are noted separately.

**Task 8 — first day, make the semáforo work**
- Path: Empezar → Acepto → Abrir ajustes → "Ya lo activé" (imagined the Android steps).
- Taps: 4. Success: yes.
- Answer: it ended on Hoy with "Semáforo preparado ✓ permiso listo" and "Listo. Abre tu app de viajes".
- Hesitation: the "Lo que hace Kompara" screen is a wall of text. As Ana I'd skip it and tap Acepto, so the Uber/DiDi "podrían no permitir apps como esta" warning would go unread. The Accesibilidad screen was clear: three steps plus a mock of the Android "control total" question. The risky part is the imagined Android step. "Semáforo preparado" is not quite "working", so I only half-trusted it until the first real offer.
- Ease: 6.

**Task 1 — how much today, is it reading my offers**
- Path: none, the answer was on screen.
- Taps: 0. Success: yes.
- Answer: about $548 today (6 trips, after gas, wear and car). "Semáforo funcionando" with a green dot means it is reading.
- Hesitation: none. The word "aprox." made me wonder how exact $548 is.
- Ease: 7.

**Task 2 — offer arrives, worth it in one second**
- Path: one glance at the screen.
- Taps: 0. Success: partial.
- Answer: yellow ◆ "$7.16/km neto, Entre tu meta y tu mínimo". It's above my $6 minimum but under my $8 goal. So it's okay but not great, and I'd probably take it if I were slow.
- Hesitation: the chip says "between" but not what to do. Yellow means "you decide", and I wanted a yes or no. I also had to remember my own two numbers. The big $120 on the card looks better than $7.16 net, which pulls the eye.
- Ease: 5.

**Task 3 — all of today's offers with $/km**
- Path: Ver todas las ofertas de hoy → scroll down once.
- Taps: 1 (plus 1 scroll). Success: yes.
- Answer: 10 offers. 6 taken: $9.49, $9.81, $9.41, $10.46, $10.75 and $10.00 per km. 3 left without taking: $2.20, $9.16 and $9.20. 1 "Sin lectura" (11:48) with no number.
- Hesitation: the list is 4 rows longer than the screen, with no hint to scroll. "neto por km" is small grey text and is net of costs, not what the app paid. I accepted that.
- Ease: 6.

**Task 4 — last week's take-home after all costs including the car**
- Path: Ganancias tab → Semana anterior.
- Taps: 2. Success: yes.
- Answer: $4,980 for 12–18 Oct, "ya restamos gasolina, desgaste y coche".
- Hesitation: the line "Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062" is jargon. It made me doubt which number was mine, since $5,028 and $5,062 sit right under $4,980. I went with the big one.
- Ease: 6.

**Task 5 — which app paid more per km last week**
- Path: Ganancias → Semana anterior → "62 viajes tomados" (cost breakdown sheet, no help) → back → Comparar tab (Tus apps).
- Taps: 5. Success: yes.
- Answer: DiDi, $10.34/km against Uber $9.13/km (DiDi $1.21 more per km), week 12–18 Oct.
- Hesitation: I went to Ganancias first because it holds my money. Nothing there points to Uber vs DiDi. "Comparar" is the last tab and sounds like comparing against other drivers, so I tried it last. Once there, "DiDi te dejó $1.21 más por km" is excellent.
- Ease: 5.

**Task 6 — set minimum to $6.50/km**
- Path: tapped the "tu meta $8 · tu mínimo $6" line → "Tu meta y tu mínimo" → "+" on the mínimo row → Guardar.
- Taps: 4. Success: yes.
- Answer: the message «Guardado: tu mínimo ahora es $6.50/km» appeared. The status screen then shows $8.00/km · $6.50/km.
- Hesitation: I chose the line because it shows my numbers. I did not think of the gear icon. The "+" icons have no label and I hadn't scrolled the screen to check for a separate section. Steps of $0.50 happened to land exactly on 6.50. If I had wanted $6.60 I would have had to find "Toca la cifra". Guardar was greyed out until I changed something, which is a good hint.
- Ease: 6.

**Task 7 — semáforo stopped during my shift: why and what to do**
- Path: swipe down from the top (a "K" icon was in the status bar) → tapped "Se apagó el semáforo" → read the Hoy screen → "Volver a dar el permiso".
- Taps: 3. Success: yes.
- Answer: Android switched off the Accesibilidad permission, sometimes when saving battery. Fix: give the permission again. The button takes me to the same three-step guide as the first day. The notification even says "Abre Kompara al estacionarte".
- Hesitation: in the Uber screen there was no sign anything was wrong. Only the small K in the status bar gave it away. I'd have noticed hours later by not seeing the semáforo. The word "Accesibilidad" scared me for a second. I did not tap "Que no vuelva a pasar" and don't know what it does.
- Ease: 6.

**Task 9 — what my last trip left me**
- Path: tapped the "La tomaste" row (14:32).
- Taps: 1. Success: yes.
- Answer: $114 for the 14:32 trip. That is the $148 fare minus $34 for gas and wear. It is $9.49/km, $285/h, 24 min. The car cost ($214 a day) is not taken off per trip.
- Hesitation: "Te deja antes de lo del coche" gave me the number but not the whole picture. The car note is small grey text. A driver wants one figure, and here there are two ideas. Row-tapping is easy to try. There is no chevron to say the row is tappable.
- Ease: 6.

**(a) Three biggest usability problems**
1. Screen: Offer card / chip, yellow case ("$7.16/km neto, Entre tu meta y tu mínimo").
   - Problem: the yellow verdict gives no decision. Ana has to compare three numbers in her head while driving.
   - Fix: add a one-word plain verdict on the chip, for example "Te alcanza" for yellow with a small "+$1.16 sobre tu mínimo". Keep the numbers secondary.
2. Screen: Navigation from Hoy to the week comparison (Ganancias vs Comparar).
   - Problem: "which app pays more" lives under the last tab, "Comparar". The Ganancias week screen, which looks like the natural place, has no link to it. I wasted 3 taps.
   - Fix: add a row on the Ganancias week screen, "Uber vs DiDi esa semana →", that opens Comparar > Tus apps. Or rename the tab "Uber vs DiDi".
3. Screen: Ganancias > Semana anterior, the "Uber reporta $34 más de lo que leímos / Leímos $5,028 · tu resumen dice $5,062" row.
   - Problem: three money figures sit under the headline $4,980 with technical wording. It makes the answer feel unreliable.
   - Fix: collapse it into a single plain note, for example "Tu resumen de Uber dice $34 más. Toca para ver por qué", and hide the two extra figures until tapped.

Smaller items:
- The Accesibilidad permission has no visible alert while on the Uber screen, only a tiny K.
- The onboarding "Lo que hace Kompara" text is dense.
- The "Sin lectura" row has no number.

**(b) Ease compared with Uber Driver: 7/10.**
- Kompara is clearer on the number I care about. Its first screen is the answer, and the cost breakdown is honest.
- It loses on tab labels and the "Comparar" hunt.
- The two Uber reference screenshots are just a weekly total and a "Estadísticas" button. They show less, but they are simpler.

**(c) Would I understand every screen without help: 8/10.** The unclear bits are the reconciliation row, "Que no vuelva a pasar", and what yellow wants me to do.

**(d) Same tasks in Uber Driver: task 1 (today's earnings) 6, task 4 (last week's earnings) 5, task 9 (last trip's detail) 4.**
- Today's total needs a tap into Ganancias.
- Last week has arrows but only shows gross.
- A trip's detail is buried in the activity list.

**(e) Install:** Yes. It gives me my real take-home and a clear colour on each offer. I just want the yellow to tell me what to do.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Ya lo activé","taps":4,"success":"yes","answer":"Ends on Hoy with 'Semáforo preparado, permiso listo' and 'Listo. Abre tu app de viajes'","ease":6,"stuck":"No stuck point. I skimmed the long 'Lo que hace Kompara' text and tapped Acepto. 'Preparado' is not quite 'working' until the first real offer. The imagined Android step is the risk."},"1":{"path":"none (on screen)","taps":0,"success":"yes","answer":"About $548 today, 6 trips, after gas, wear and car. Semáforo funcionando (green dot), so it is reading offers.","ease":7,"stuck":"None. 'aprox.' makes me wonder how exact it is."},"2":{"path":"glance at the offer card","taps":0,"success":"partial","answer":"Yellow, $7.16/km neto, between goal and minimum: above my $6 minimum, under my $8 goal. Okay but not great, I'd probably take it if slow.","ease":5,"stuck":"The chip never says what to do. I had to compare 3 numbers while driving. The $120 on the card draws the eye more than the net $7.16."},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: taken $9.49, $9.81, $9.41, $10.46, $10.75, $10.00 per km. Not taken: $2.20, $9.16, $9.20. One 'Sin lectura' (11:48) with no figure.","ease":6,"stuck":"The list continues below the fold with no hint to scroll. 'neto por km' is tiny grey text and is net of costs, not what the app paid."},"4":{"path":"Ganancias tab > Semana anterior","taps":2,"success":"yes","answer":"$4,980 for 12-18 Oct after gas, wear and car.","ease":6,"stuck":"The line 'Uber reporta $34 más / Leímos $5,028 · tu resumen dice $5,062' is jargon and made me doubt which figure was mine."},"5":{"path":"Ganancias > Semana anterior > 62 viajes tomados (cost sheet, no help) > back > Comparar tab","taps":5,"success":"yes","answer":"DiDi paid more: $10.34/km vs Uber $9.13/km (DiDi +$1.21/km), week 12-18 Oct.","ease":5,"stuck":"Went to Ganancias first because it holds my money. Nothing there points to the Uber vs DiDi comparison, and 'Comparar' sounds like comparing with other drivers. 3 taps wasted."},"6":{"path":"Hoy: tapped 'tu meta $8 · tu mínimo $6' > Tu meta y tu mínimo > + on mínimo > Guardar","taps":4,"success":"yes","answer":"Saved: «Guardado: tu mínimo ahora es $6.50/km»; status now shows $8.00/km · $6.50/km.","ease":6,"stuck":"Found it by tapping the line that shows my numbers, not the gear icon. The + buttons have no label. Steps of $0.50 happened to hit 6.50; for another value I'd need 'Toca la cifra'."},"7":{"path":"swipe down from top (saw K icon) > tap 'Se apagó el semáforo' notification > Hoy off-state screen > Volver a dar el permiso","taps":3,"success":"yes","answer":"Android switched off the Accesibilidad permission (sometimes when saving battery). Fix: tap 'Volver a dar el permiso' and redo the three-step guide; open Kompara when parked.","ease":6,"stuck":"No visible alert on the Uber screen, only a tiny K in the status bar, so I'd notice late. 'Accesibilidad' sounds scary. Didn't know what 'Que no vuelva a pasar' does."},"9":{"path":"Hoy > tap the 'La tomaste' 14:32 row","taps":1,"success":"yes","answer":"$114 for the 14:32 trip: $148 fare minus $34 gas and wear, $9.49/km, $285/h, 24 min. Car cost ($214/day) is not taken off per trip.","ease":6,"stuck":"'Te deja antes de lo del coche' gives me a number but not the final one. The car note is small grey text. Rows give no chevron to say they are tappable."}},"problems":[{"screen":"Offer chip / overlay, yellow case ($7.16/km neto, Entre tu meta y tu mínimo)","problem":"The yellow verdict gives no decision, so the driver compares three numbers while driving and the big gross fare competes with the net.","fix":"Add a one-word plain verdict on the chip (for example 'Te alcanza') with a small '+$1.16 sobre tu mínimo' and keep the numbers secondary."},{"screen":"Ganancias week screen vs Comparar tab","problem":"Which app pays more per km is only under the last tab 'Comparar' and there is no pointer from the Ganancias week screen, which is where a driver looks first.","fix":"Add a row on Ganancias week screen 'Uber vs DiDi esa semana' that opens Comparar > Tus apps, or rename the tab 'Uber vs DiDi'."},{"screen":"Ganancias > Semana anterior, row 'Uber reporta $34 más / Leímos $5,028 · tu resumen dice $5,062'","problem":"Three money figures sit under the headline $4,980 in technical wording, making the answer feel unreliable.","fix":"Collapse to one plain note ('Tu resumen de Uber dice $34 más. Toca para ver por qué') and hide the extra figures until tapped."}],"ease_vs_uber":7,"understand_all":8,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes, because it shows my real take-home and gives each offer a clear colour, as long as the yellow tells me what to do."}
```

### Judge 6 (haiku)

**Session report: Diego (23, drives Uber, DiDi and inDrive)**

I read only the task file, the screenshots the tool wrote, and the two Uber reference images. I did not open log.json or the tool code. Taps exclude facilitator buttons and scrolls. Total app taps: 20.

**Task 8 (first day, semáforo)**
- Path: Empezar > Acepto > Abrir ajustes > (substitute Ajustes screen) Ya lo activé. Taps: 4. Success: yes.
- Answer: I imagined the three steps (Kompara under Apps instaladas, switch on, Permitir). The app then showed "Semáforo preparado · permiso listo" and "Listo. Abre tu app de viajes".
- Hesitation: the "control total de tu dispositivo" prompt and the line saying Uber and DiDi may not allow apps like this both scared me before I had tried anything. "Usamos costos típicos $2.84 por km" made me ask where that number comes from. Ease: 6.

**Task 1 (today, reading offers)**
- Path: none, read on the Hoy screen. Taps: 0. Success: yes.
- Answer: $548 aprox., 6 trips, "Semáforo funcionando". Ease: 7. I doubted the "aprox." and whether the $548 already has the car subtracted.

**Task 2 (one glance at an offer)**
- Path: none, one look at the offer card. Taps: 0. Success: yes.
- Answer: Yes, but mediocre. $7.16/km net, yellow (between my $8 goal and $6 minimum). $120 for 12 km with 2 km pickup, roughly 27 min. I would take it only if nothing better comes.
- Hesitation: the chip does not say whether the net includes the pickup km, so I had to do the math. Ease: 6.

**Task 3 (all offers today with $/km)**
- Path: Ver todas las ofertas de hoy > scroll down twice. Taps: 1. Success: yes.
- Answer: 10 offers. 14:32 $9.49 (taken); 12:10 $2.20 (not taken); 11:48 no reading; 11:20 $9.81 (taken); 10:41 $9.41 (taken); 10:02 $10.46 (taken); 9:51 $9.16 (not taken); 9:24 $10.75 (taken); 8:40 $9.20 (not taken); 8:15 $10.00 (taken).
- Hesitation: the column says "neto por km", not "lo que pagó", so I assumed they are the same. The 11:48 offer has no number and the screen does not say what to do about it. Ease: 6.

**Task 4 (last week's net after all costs)**
- Path: Ganancias tab > Semana anterior > Por día (checked). Taps: 3. Success: yes.
- Answer: $4,980 aprox. for 12–18 oct, "Te quedaron esa semana", after gasoline, wear and car.
- Hesitation: Ganancias opens on the current week ($548), so I had to find the arrow. The same screen then shows "Leímos $5,028" and "tu resumen dice $5,062", and the breakdown starts from $8,400 before costs. I could not tell which base the $4,980 comes from. Ease: 6.

**Task 5 (Uber or DiDi, more per km last week)**
- Path: Ganancias > Semana anterior > "62 viajes tomados" (sheet) > back gesture > "Uber reporta" row (dead: "Esta pantalla no está en esta prueba") > Comparar. Taps: 5. Success: yes. One mistyped call tapped nothing and is not counted.
- Answer: DiDi, $10.34/km vs Uber $9.13/km, so DiDi paid $1.21 more per km (after gasoline and wear).
- Hesitation: Ganancias has no per-app split, and the chevron row leads nowhere. I found the answer only in Comparar, which I should have tried first. "Por km" does not say gross or net. Ease: 5.

**Task 6 (change minimum to $6.50/km)**
- Path: Hoy "tu meta $8 · tu mínimo $6" > Semáforo "Tu meta y tu mínimo" > plus button on the minimum (one $0.50 step) > Guardar. Taps: 4. Success: yes.
- Answer: "Guardado: tu mínimo ahora es $6.50/km", goal still $8.00.
- Hesitation: a detour through the Semáforo screen to reach the thresholds. The hint says to tap the figure to type, but I did not try typing; the plus button was faster. Ease: 6.

**Task 7 (semáforo stopped during my shift)**
- Path: swipe down from top > tap "Se apagó el semáforo" > Hoy > "Que no vuelva a pasar". Taps: 2. Success: yes.
- Answer: Android removed Kompara's Accessibility permission, often when saving battery. Fix: "Volver a dar el permiso", and set Kompara's battery to Sin restricciones.
- Hesitation: "Android a veces lo apaga" on the Hoy screen does not say whether I caused it. Once I opened the notification the fix was clear. Ease: 6.

**Task 9 (last trip)**
- Path: Hoy > tap "La tomaste 14:32". Taps: 1. Success: yes.
- Answer: about $114 (fare $148 minus gasoline and wear $34). Also $9.49/km, $285/h, 24 min. The car is not subtracted per trip.
- Hesitation: "Te deja antes de lo del coche" is not what I keep, and the trip figure and the $548 day total use different bases. Ease: 6.

**(a) Three biggest problems**
1. Ganancias, last week and its "Cómo se calculó" sheet: two gross totals on one screen. The breakdown says $8,400 before costs for 62 trips, while the same screen says "Leímos $5,028" and "tu resumen dice $5,062". The roughly $3,300 gap is never explained, so the $4,980 cannot be checked. Fix: one gross total everywhere, and when the read total and Uber's differ, name what is missing before showing a net.
2. Ganancias week view, "Uber reporta $34 más de lo que leímos": the row has a chevron but opens a dead screen. There is also no Uber/DiDi split on Ganancias. Fix: make the row open the reconciliation (or drop the chevron and explain the $34 inline), and add "Uber $X · DiDi $Y" under the week total, linked to Comparar.
3. Trip sheet, Hoy, Ganancias breakdown: "neto", "te deja", "te quedaron" and "ya restamos… coche" mean different things. Per-km and per-trip figures leave out the car, while day and week totals include it. Fix: label every money figure "antes del coche" or "después del coche", and show the car as a daily share on the trip sheet (for example "$214 del día ÷ 6 viajes ≈ $36").

Other things I noticed: the onboarding consent text may scare drivers away before first use. Hoy says "Ofertas sin semáforo · ninguna hoy" while the 11:48 offer is listed as "Sin lectura" today. The status-bar clock goes 14:50, then 14:40, then 8:01, and at 8:01 the app says "Aún no registramos viajes hoy" after showing $548 earlier.

**(b) Ease compared with Uber Driver: 6/10.** Uber's earnings screen is one total, a day chart and a few rows. Kompara's Hoy and offer screens are clear, but the money screens carry more numbers with inconsistent labels.

**(c) Understanding every screen without help: 6/10.** I understood each screen's words, but the totals do not reconcile and some labels mean different things.

**(d) Same tasks in Uber Driver:** task 1 (today's earnings) 6/7; task 4 (last week after all expenses including the car) 2/7, because Uber does not show a net after costs; task 9 (last trip) 6/7, since the trip payout is shown directly.

**(e)** Probably not yet: the Hoy screen and the semáforo are clear and useful, but the weekly totals do not reconcile with Uber's own $5,062 on the same screen, so I would not trust the net until that gap is explained.

The prototype session is stopped.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > (substitute Ajustes screen) Ya lo activé","taps":4,"success":"yes","answer":"Imagined the 3 steps (find Kompara under Apps instaladas, switch on, Permitir); app shows Semáforo preparado, permiso listo, and Listo. Abre tu app de viajes","ease":6,"stuck":"Hesitated at the control total de tu dispositivo prompt and at the consent line saying Uber and DiDi may not allow apps like this, which scares before first use. Usamos costos típicos $2.84 por km made me ask where the number comes from."},"1":{"path":"None; read the Hoy screen on open","taps":0,"success":"yes","answer":"$548 aprox. today, 6 trips taken; Semáforo funcionando, so it is reading offers","ease":7,"stuck":"No real stuck point. Doubted the aprox. and whether $548 already has the car subtracted (the text says it does)."},"2":{"path":"None; one glance at the offer card","taps":0,"success":"yes","answer":"Yes but mediocre: $7.16/km net, yellow (between my goal $8 and minimum $6). $120 for 12 km (2 km pickup), roughly 27 min. I would take it only if nothing better comes.","ease":6,"stuck":"Color and words read fast, but the chip does not say whether the net includes the 2 km pickup; I had to do the math. The big Aceptar button sits right under the verdict."},"3":{"path":"Hoy > Ver todas las ofertas de hoy > scroll down x2","taps":1,"success":"yes","answer":"10 offers. 14:32 $9.49 (taken); 12:10 $2.20 (not taken); 11:48 no reading; 11:20 $9.81 (taken); 10:41 $9.41 (taken); 10:02 $10.46 (taken); 9:51 $9.16 (not taken); 9:24 $10.75 (taken); 8:40 $9.20 (not taken); 8:15 $10.00 (taken)","ease":6,"stuck":"Column says neto por km, not lo que pagó, so I had to assume they are the same. The 11:48 offer has no number and the screen does not say what to do about it."},"4":{"path":"Ganancias tab > Semana anterior > Por día (to check)","taps":3,"success":"yes","answer":"$4,980 aprox. for 12-18 oct, Te quedaron esa semana, after gasoline, wear and car","ease":6,"stuck":"Ganancias opens on the current week ($548), not last week; I had to find the Semana anterior arrow. The same screen then shows Leímos $5,028 and tu resumen dice $5,062, and the breakdown starts from $8,400 before costs. I could not tell which base the $4,980 comes from."},"5":{"path":"Ganancias tab > Semana anterior > 62 viajes row (sheet) > back gesture > Uber reporta row (dead: Esta pantalla no está en esta prueba) > Comparar","taps":5,"success":"yes","answer":"DiDi. $10.34/km vs Uber $9.13/km, so DiDi paid $1.21 more per km (12-18 oct, after gasoline and wear)","ease":5,"stuck":"Ganancias has no per-app split, and the chevron row leads nowhere. Found it only in Comparar, which I should have tried first. Por km does not say gross or net; the footnote says gasoline and wear are subtracted."},"6":{"path":"Hoy tu meta $8 · tu mínimo $6 > Semáforo Tu meta y tu mínimo > plus button on minimum (one $0.50 step) > Guardar","taps":4,"success":"yes","answer":"Saved: minimum is now $6.50/km (goal still $8.00)","ease":6,"stuck":"Needed a detour through the Semáforo screen to reach the thresholds. The hint says tap the figure to type, but I did not try typing; the plus button was faster."},"7":{"path":"Swipe down from top > tap Se apagó el semáforo > Hoy > Que no vuelva a pasar (battery screen)","taps":2,"success":"yes","answer":"Android removed Kompara's Accessibility permission, often when saving battery. Fix: Volver a dar el permiso, and set Kompara's battery to Sin restricciones so it does not happen again","ease":6,"stuck":"Android a veces lo apaga on the Hoy screen does not say whether I caused it. Once I opened the notification the fix was clear in two taps."},"9":{"path":"Hoy > tap La tomaste 14:32","taps":1,"success":"yes","answer":"About $114 (fare $148 minus gasoline and wear $34). Also $9.49/km, $285/h, 24 min. The car is not subtracted per trip.","ease":6,"stuck":"Te deja antes de lo del coche is not what I keep, and the trip figure and the $548 day total use different bases."}},"problems":[{"screen":"Ganancias > Semana anterior (12-18 oct) and its Cómo se calculó sheet","problem":"Two gross totals on one screen: 62 viajes at $8,400 before costs versus Leímos $5,028 and tu resumen dice $5,062. The roughly $3,300 gap is never explained, so the $4,980 net cannot be checked against Uber. For a driver who compares everything, this kills trust.","fix":"Use one gross total everywhere. If the read total and Uber's total differ, name what is missing (trips not read, or DiDi trips not included) before showing a net."},{"screen":"Ganancias week view, Uber reporta $34 más de lo que leímos row","problem":"The row has a chevron but opens a dead screen (Esta pantalla no está en esta prueba). Ganancias also has no Uber/DiDi split, so answering which app paid more took a dead end before I found Comparar.","fix":"Make the row open the reconciliation, or drop the chevron and explain the $34 inline. Add Uber $X · DiDi $Y under the week total, linked to Comparar."},{"screen":"Hoy trip list, trip sheet Oferta de las 14:32, Ganancias breakdown, Comparar","problem":"Neto, te deja, te quedaron and ya restamos el coche mean different things. Per-km and per-trip figures leave out the car, while day and week totals include it. Te deja antes de lo del coche sits next to Te quedaron, so I could not tell what I keep per trip.","fix":"Put antes del coche or después del coche next to every money figure. On the trip sheet, show the car as a daily share (for example $214 del día ÷ 6 viajes ≈ $36) so trip and day numbers reconcile."}],"ease_vs_uber":6,"understand_all":6,"uber_seq":{"1":6,"4":2,"9":6},"install":"Probably not yet: the Hoy screen and the semáforo are clear and useful, but the weekly totals do not reconcile with Uber's own $5,062 on the same screen, so I would not trust the net until that gap is explained."}
```
