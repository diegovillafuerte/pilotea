# Run 5 — interactive prototype (Round 20c kit), 2026-10-28

Six persona judges (Opus ×2, Sonnet ×3, Haiku ×1) used the clickable prototype through a participant harness (`/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/tool/pt.mjs`: look, tap by visible text or coordinates, scroll, back, swipe down, task start/done/stuck), in participant mode, tasks in the plan's order (8, then 1–7, 9). Self-report per task (path, success, SEQ) plus the prototype's own log (taps, misses, time, end screen). Prompt: `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/JUDGE5.md`; tasks `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/TASKS.md`. Comparability: runs 1–4 were static-frame walkthroughs; run 5 is the first on the working prototype.

```
6 judges · logs from 6
| task | self success (yes/partial/no) | reached the answer screen (log) | mean taps (log) | mean misses (log) | mean time (s) | mean SEQ |
|---|---|---|---|---|---|---|
| First day setup | 5/1/0 | 6/6 | 4.7 | 0.2 | 34 | 5.00 |
| Hoy: kept today + reader | 6/0/0 | 6/6 | 0.2 | 0.0 | 10 | 6.17 |
| Offer: worth it? | 6/0/0 | 6/6 | 0.0 | 0.0 | 9 | 5.67 |
| Every offer today | 6/0/0 | 6/6 | 1.0 | 0.0 | 16 | 6.00 |
| Last week after car | 6/0/0 | 6/6 | 2.3 | 0.0 | 22 | 5.83 |
| Which app paid more | 6/0/0 | 6/6 | 1.0 | 0.0 | 12 | 6.50 |
| Change tu mínimo | 6/0/0 | 6/6 | 9.7 | 0.0 | 51 | 5.00 |
| Overlay stopped | 6/0/0 | 6/6 | 2.7 | 0.0 | 27 | 6.17 |
| Last trip detail | 6/0/0 | 6/6 | 1.0 | 0.0 | 14 | 6.17 |

mean SEQ 5.83 · ease vs Uber 7.0/10 · understand every screen 7.0/10 · lowest task 5.00
matched task 1: Kompara 6.17 vs Uber (self-estimated) 5.83 → diff +0.33
matched task 4: Kompara 5.83 vs Uber (self-estimated) 4.67 → diff +1.17
matched task 9: Kompara 6.17 vs Uber (self-estimated) 4.67 → diff +1.50
GATE (mean >= 6.0, vs Uber >= 7.5, no task < 5.5): NOT MET

--- judge 1 (opus) · vs Uber 7 · understood 7 · install: Probably yes, because it takes out my weekly cuenta and gas and shows what I really keep, but the 'control total' warning and 'Uber podría no permitirlo' make m
- Onboarding 'Enciende Kompara en Accesibilidad' and the next 'Toca Kompara y activa el interruptor' screen | 'control total' and 'Uber o DiDi podrían no permitirlo' scare a driver who could lose her account; two screens in a row with the same 'Abrir ajustes' button; the example switch looks real but does nothing | fix: Merge into one screen, make the example clearly a picture (greyed out, 'Ejemplo' tag, no real-looking switch), add one plain line on what Uber can see or do, and say how to stop th
- Tu meta y tu mínimo | Guardar gives no confirmation and stays on the same screen; 10-cent +/- steps mean 5 taps for 50 cents | fix: After saving show 'Listo: tu mínimo ahora es $6.50' and return to Semáforo; use 50-cent steps or make tap-to-type more visible
- Offer detail ('Te deja $114') vs Hoy/Ganancias ('Te quedan'), plus 'Tu resumen de Uber dice $34 más' | The trip number excludes the car, the day/week numbers include it; 'Lo del coche' doesn't sound like a renter's weekly cuenta; '$34 más' doesn't say more than what | fix: Label the trip 'Te deja $114 antes de tu cuenta del coche', call it 'tu cuenta / renta del coche', and reword to 'Uber te pagó $34 más de lo que leímos; el pago final es el de Uber

--- judge 2 (sonnet) · vs Uber 8 · understood 8 · install: Yes, because it tells me per km, net of gas and car, whether an offer is worth taking and what I really kept, which Uber doesn't show, as long as the permission
- Tu meta y tu mínimo (after typing the number and tapping Guardar) | No confirmation after saving: the button greys out, the screen stays put, and I had to go back to see that $6.50 stuck. The +/- buttons move only $0.10. | fix: Close the screen on save or show a short 'Guardado: mínimo $6.50' message; make the +/- steps bigger or hint that tapping the number types it.
- Enciende Kompara en Accesibilidad and Toca Kompara y activa el interruptor | Two near-identical setup screens with the same 'Abrir ajustes' button, a dashed mock that looks tappable, a vague 'busca Kompara', and scary 'control total' wording make the permission step confusing. | fix: One numbered step screen with one button; put the 'solo lee, no toca nada' reassurance beside the Permitir mock; show a clear 'Permiso listo' state on return.
- Hoy, Ganancias, Comparar and the offer list (money labels) | 'Te quedan hoy · aprox.' reads like what's left to earn where Uber says 'Ganancias'; 'Tu resumen de Uber dice $34 más' and 'A distintas horas en cada app' are unexplained; 'neto por km' isn't what I asked for ('lo que pa | fix: Use 'Ganancias netas de hoy'; explain the Uber line in plain words (e.g. 'Uber reporta $34 más porque no resta tus gastos'); explain or drop 'A distintas horas'; offer a gross/net 

--- judge 3 (opus) · vs Uber 8 · understood 8 · install: Yes, because it tells me with shapes and words whether a trip beats my own limits and takes my car rent off, which Uber never does, but it should stop saying ve
- Tu día banner, Tu meta y tu mínimo labels, offer list icons | Colour words (ofertas verdes, verde desde, rojo abajo de) and colour-only list icons are meaningless with red-green colour blindness | fix: Use the chip's shapes and words everywhere: 2 ofertas arriba de tu meta se fueron sin tomar; Tu meta ▲ desde / Tu mínimo ▼ abajo de; put ▲◆▼ in the list icons
- Tu meta y tu mínimo (editor) | Guardar gives no confirmation (just turns grey) and $0.10 steps need many taps | fix: Show a toast Guardado: tu mínimo ahora es $6.50 and return to Semáforo; open a number keyboard on tapping the amount or allow bigger steps
- Onboarding: Enciende Kompara en Accesibilidad > Toca Kompara y activa el interruptor | Two Abrir ajustes buttons in a row; the first opens another explainer, so the tap seems to fail | fix: Go straight to Android settings, or rename the first button Siguiente / merge the two screens

--- judge 4 (sonnet) · vs Uber 6 · understood 6 · install: Probably yes for the traffic light and the real weekly net after the car, but only if someone I trust helps with the permission setup and I'm sure it won't get 
- Permission steps (Enciende Kompara en Accesibilidad and the drawn Android list) | A drawn Android dialog with grey buttons that look tappable, a second Abrir ajustes button, 'control total' wording, and no clear 'it worked' when I return | fix: One real button per step; label the picture as a non-tappable example; say 'Android lo dice así, pero Kompara solo lee'; show a big 'Listo, el semáforo está encendido' on return; e
- Offer overlay chip (yellow, $7.16/km neto) | Verdict is 'Entre tu meta y tu mínimo' in small grey text on black; no plain take-it or skip-it word; hard to read at a glance with reading glasses while driving | fix: Put one big plain word on the chip (Regular or Aceptable), enlarge the meta and mínimo lines with higher contrast, and follow the phone's large-text setting
- Tu meta y tu mínimo | Typing is hidden behind a dotted-underline figure, +/- moves only $0.10 (10 taps total for a $0.50 change), and Guardar greys out with no confirmation | fix: Make the figure look like an input box, use $0.50 steps on the buttons, and show a clear 'Guardado' message after saving

--- judge 5 (sonnet) · vs Uber 7 · understood 7 · install: Yes, because it shows me at a glance what a trip really leaves me per km and what I keep after costs, which Uber doesn't, though the 'control total' permission 
- Tu meta y tu mínimo (reached from the Hoy status row, then the Semáforo screen) | Setting is hidden two screens deep and Guardar gives no confirmation (button just greys out), so I could not tell if the new minimum saved | fix: Show a 'Guardado' note and return to the previous screen; add a clear 'Cambiar mi mínimo' shortcut on Hoy and the Semáforo tab
- Yellow chip on the Uber offer | Shows $7.16 against meta and mínimo but never says what to do; 'meta', 'mínimo' and 'neto' make me do the math while driving | fix: Add a plain verb to the verdict phrase such as 'Aceptable' or 'Solo si no hay algo mejor'; say 'lo que te deja por km' instead of 'neto'
- Onboarding 'Enciende Kompara en Accesibilidad' and the settings preview | Two 'Abrir ajustes' buttons, the 'Así se verá' mock dialog looks like real buttons, and on return it still says 'Semáforo apagado' with no 'Permiso listo'; 'control total' and the Uber/DiDi warning are scary | fix: Label the mock 'Ejemplo, no toques aquí' without button styling, keep one button, and on return show a green 'Permiso listo' with a sample offer

--- judge 6 (haiku) · vs Uber 6 · understood 6 · install: Yes, I would install it to check it against my own numbers, but only after the stale minimum and the cost-basis inconsistencies are fixed.
- Semaforo > Tu meta y tu minimo (after Guardar), and the Hoy header | Saved minimum $6.50 shows on the Semaforo screen but Hoy still says tu minimo $6, and Guardar gives no confirmation, so I could not tell whether the change stuck | fix: Use one source for the thresholds, refresh Hoy after saving, and show a Guardado: minimo $6.50/km confirmation
- Comparar (Tus apps), Ganancias and the offer sheet | Net figures use different cost bases: Comparar says Ya restamos gasolina y desgaste (no coche) while Ganancias includes coche, and the per-km comparison is at different hours, so the $1.21 DiDi gap is not like-for-like | fix: Use one net definition on every net figure with the cost list shown inline, and show a caveat or a same-hours comparison when the apps' hours differ
- Hoy and Ganancias headline money; the offer list | Te quedan hoy / esta semana aprox reads as pending money; the previous-week arrow is unlabelled; neto por km in the offer list is not what the task asked for | fix: Use Ganaste hoy (neto, aprox), label the arrows Semana anterior / Semana siguiente, and show gross and net per km side by side in the offer list
```

## Full judge reports

### Judge 1 (opus)

**Session report: Rosa, 52, rents her car and pays a weekly "cuenta", drives Uber only, ten years on the road, low comfort with apps**

**Task 8: first day, get the semáforo working**
- **Path:** Empezar → Acepto → Abrir ajustes → tapped the switch inside the «Así se verá» picture (nothing happened) → Abrir ajustes again → the note «Esta pantalla no está en esta prueba» appeared. I then imagined the Android part: find Kompara, turn on the switch, accept «control total», come back.
- **Taps:** 5, including 1 wrong one.
- **Success:** yes, with the Android step imagined.
- **Where I hesitated:**
  - «Uber o DiDi podrían no permitirlo» scared me. If Uber blocks me, I can't pay the car owner.
  - «¿Permitir que Kompara tenga control total de tu dispositivo?» sounds dangerous. Only the line «Kompara solo lee» kept me going.
  - The next screen showed a second «Abrir ajustes» button.
  - The switch in the example picture looked real, so I tapped it.
  - Android's own Accessibility menus are hard for me, and I'd need my nephew for that part.
- **Ease:** 4

**Task 1: how much today, and is it reading offers?**
- **Path:** read Hoy, then tapped «6 viajes tomados» to check the number.
- **Taps:** 1
- **Success:** yes
- **Answer:** I have about $548 left today. That is $979 from 6 trips, minus $217 for gas and wear, minus $214 for the car ($1,500 ÷ 7 days). The green dot says «Semáforo funcionando».
- **Where I hesitated:** «aprox.» makes me doubt the number. The breakdown fixed that because I could add it up myself, and the $1,500 matches my cuenta.
- **Ease:** 6

**Task 2: one glance at an Uber offer**
- **Taps:** 0
- **Success:** yes
- **Answer:** It's yellow with the diamond: $7.16 per km «neto», between my meta of $8 and my mínimo of $6. So-so; I'd take it if nothing better comes.
- **Where I hesitated:** I didn't read the small «tu meta / tu mínimo» lines or the bars under the number. The colour and the big number told me enough.
- **Ease:** 6

**Task 3: all of today's offers with $/km**
- **Path:** Ver todas las ofertas de hoy → scrolled down once.
- **Taps:** 1
- **Success:** yes
- **Answer:** 10 offers, from newest to oldest:

| Time | Status | $/km |
|---|---|---|
| 14:32 | La tomaste | $9.49 |
| 12:10 | Se fue sin tomar | $2.20 |
| 11:48 | Sin lectura («Faltó la tarifa») | none |
| 11:20 | La tomaste | $9.81 |
| 10:41 | La tomaste | $9.41 |
| 10:02 | La tomaste | $10.46 |
| 9:51 | Se fue sin tomar | $9.16 |
| 9:24 | La tomaste | $10.75 |
| 8:40 | Se fue sin tomar | $9.20 |
| 8:15 | La tomaste | $10.00 |

- **Where I hesitated:** «Se fue sin tomar» reads a bit strangely. The «Sin lectura» row has no number and no explanation of why.
- **Ease:** 6

**Task 4: last week after all costs, including the car**
- **Path:** Ganancias → the small ‹ arrow next to the dates → «62 viajes tomados» to check.
- **Taps:** 3
- **Success:** yes
- **Answer:** $4,980. That is $8,400, minus $1,920 for gas and wear, minus $1,500 for the car.
- **Where I hesitated:**
  - Ganancias first shows this week, which is $548, the same as today. For a moment I thought it was broken.
  - The ‹ arrow is small.
  - «Tu resumen de Uber dice $34 más» confused me: $34 more than what? Which number is the real one?
- **Ease:** 6

**Task 5: Uber or DiDi, which paid more per km last week?**
- **Path:** Comparar.
- **Taps:** 1
- **Success:** yes
- **Answer:** DiDi, at $10.34/km against $9.13/km for Uber ($1.21 more).
- **Where I hesitated:** I only drive Uber, so where do these DiDi numbers come from? That made me trust the screen less. It was easy to find, though.
- **Ease:** 6

**Task 6: change my mínimo to $6.50**
- **Path:** tapped «tu meta $8 · tu mínimo $6» on Hoy → Tu meta y tu mínimo → the + next to the mínimo 5 times → Guardar → back arrow to check.
- **Taps:** 9
- **Success:** yes. The Semáforo screen shows $8.00/km · $6.50/km.
- **Where I hesitated:**
  - I avoid the gear icon, so I'm glad tapping the number worked.
  - The + only moves 10 cents, so I had to count five taps.
  - After Guardar nothing told me it was saved. The button just went grey and I stayed on the same screen, so I had to go back to check.
- **Ease:** 5

**Task 7: the semáforo stopped showing during my shift**
- **Path:** Abrir Kompara → Volver a dar el permiso. This led to the same «Enciende Kompara en Accesibilidad» screen as on day one.
- **Taps:** 2
- **Success:** yes
- **Answer:** My phone removed the Accessibility permission at 20:31 yesterday, so Kompara can't see my offers. I need to give it again: Abrir ajustes, turn Kompara on, Permitir.
- **Where I hesitated:** Why did my phone remove it? Will it happen again tomorrow? Nothing tells me how to stop it.
- **Ease:** 6

**Task 9: what did my last trip leave me?**
- **Path:** tapped «La tomaste» at 14:32 in Últimas ofertas.
- **Taps:** 1
- **Success:** yes
- **Answer:** About $114. That is the $148 fare minus $34 for gas and wear ($9.49/km, $285 per hour, 24 min).
- **Where I hesitated:** «Lo del coche se resta por día, no por viaje». So this $114 does not take out my cuenta, while Hoy says «ya restamos ... coche». The two «te deja / te quedan» numbers mean different things.
- **Ease:** 6

Total taps across all tasks: 23, with 1 wrong.

**(a) The three biggest problems**

1. **The Accessibility permission screens (first day).**
   - *Problem:* «control total de tu dispositivo» and «Uber o DiDi podrían no permitirlo» scare a driver who could lose the car if Uber blocks her. On top of that:
     - there are two screens in a row with the same «Abrir ajustes» button;
     - the example picture has a switch that looks real but does nothing.
   - *Fix:*
     - Put it all on one screen.
     - Make the example clearly a picture: grey it out, put an «Ejemplo» tag on it, and don't use a real-looking switch.
     - Add one plain sentence about what Uber can see or do to her account.
     - Say what to do if the phone removes the permission again, for example the battery-saving setting.

2. **The meta and mínimo screen.**
   - *Problem:* Guardar gives no confirmation; it just turns grey and leaves you on the same screen. The + and − buttons move only 10 cents, so 50 cents takes five taps.
   - *Fix:*
     - After saving, show «Listo: tu mínimo ahora es $6.50» and go back to the Semáforo screen.
     - Make the steps 50 cents, or make «toca la cifra para escribirla» more visible.

3. **Car cost and «te deja» wording (trip detail versus Hoy and Ganancias).**
   - *Problem:* «Te deja $114» on a trip leaves out the car, but «Te quedan $548» on Hoy includes it. «Lo del coche» doesn't sound like my weekly cuenta. «Tu resumen de Uber dice $34 más» doesn't say $34 more than what.
   - *Fix:*
     - On the trip, write «Te deja $114 antes de tu cuenta del coche».
     - Call it «tu cuenta / renta del coche», since that's what renters say.
     - Change the Uber line to «Uber te pagó $34 más de lo que leímos; el pago final es el de Uber».

**(b) Ease compared with the Uber Driver app:** 7/10.

**(c) Would I understand every screen without help?** 7/10. Day one and the Android settings would need help. The rest was fine once I tapped the numbers to see how they were calculated.

**(d) The same tasks in my Uber Driver app:**
- Task 1, today's earnings: 6
- Task 4, last week's earnings: 5
- Task 9, last trip's detail: 4. It's buried under Ganancias, then Actividad.

**(e) Would I install it?** Probably yes, because it takes out my weekly cuenta and gas and shows what I really keep; but the «control total» warning and the line about Uber maybe not allowing it make me afraid of losing my account.

The session log is at /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j1/log.json. Screenshots shot-001.png to shot-044.png are in /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j1.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > tapped switch in the 'Así se verá' picture (no effect) > Abrir ajustes (prototype note: screen not in test) > imagined Android: find Kompara, turn on, accept 'control total', return","taps":5,"success":"yes","answer":"Gave the Accessibility permission via Abrir ajustes (Android step imagined)","ease":4,"stuck":"'Uber o DiDi podrían no permitirlo' and 'control total de tu dispositivo' are scary for a renter who can't risk her Uber account; two consecutive 'Abrir ajustes' screens; the example switch looks real and I tapped it; Android Accessibility menus are hard without help"},"1":{"path":"Read Hoy > tapped '6 viajes tomados' row > 'Cómo se calculó · hoy' sheet","taps":1,"success":"yes","answer":"About $548 left today ($979 from 6 trips - $217 gas/wear - $214 car); semáforo funcionando (green dot)","ease":6,"stuck":"'aprox.' made me doubt it until the breakdown let me check the sum"},"2":{"path":"Single glance at the overlay, no taps","taps":0,"success":"yes","answer":"Yellow/diamond: $7.16/km neto, between my meta $8 and mínimo $6 - so-so, take it only if nothing better","ease":6,"stuck":"Did not read the small meta/mínimo bars; colour plus big number was enough"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: 9.49, 2.20, sin lectura (faltó la tarifa), 9.81, 9.41, 10.46, 9.16, 10.75, 9.20, 10.00 $/km","ease":6,"stuck":"'Se fue sin tomar' wording is odd; 'Sin lectura' row has no number or reason"},"4":{"path":"Ganancias > previous-week arrow (tapxy) > '62 viajes tomados' breakdown","taps":3,"success":"yes","answer":"$4,980 ($8,400 - $1,920 gas/wear - $1,500 car)","ease":6,"stuck":"Ganancias opens on the current week showing $548 like today; small arrow; 'Tu resumen de Uber dice $34 más' unclear (more than what?)"},"5":{"path":"Comparar","taps":1,"success":"yes","answer":"DiDi: $10.34/km vs Uber $9.13/km ($1.21 more)","ease":6,"stuck":"I only drive Uber, so DiDi numbers made me distrust the screen"},"6":{"path":"Hoy 'tu meta $8 · tu mínimo $6' > Tu meta y tu mínimo > + x5 on mínimo > Guardar > back arrow to verify","taps":9,"success":"yes","answer":"Mínimo now $6.50/km (Semáforo screen shows $8.00/km · $6.50/km)","ease":5,"stuck":"No confirmation after Guardar (button just greys out, stays on screen); 10-cent steps need 5 taps"},"7":{"path":"Abrir Kompara > Volver a dar el permiso > (same Accessibility screen as onboarding)","taps":2,"success":"yes","answer":"Phone removed the Accessibility permission since yesterday 20:31; give it again in Android settings (Abrir ajustes, turn on Kompara, Permitir)","ease":6,"stuck":"No reason why the phone removed it or how to stop it happening again"},"9":{"path":"Hoy > 'La tomaste' 14:32 row > offer detail sheet","taps":1,"success":"yes","answer":"About $114 ($148 fare - $34 gas/wear; car not subtracted per trip)","ease":6,"stuck":"'Te deja' on a trip excludes the car while Hoy's 'te quedan' includes it - confusing for a renter whose cuenta matters most"}},"problems":[{"screen":"Onboarding 'Enciende Kompara en Accesibilidad' and the next 'Toca Kompara y activa el interruptor' screen","problem":"'control total' and 'Uber o DiDi podrían no permitirlo' scare a driver who could lose her account; two screens in a row with the same 'Abrir ajustes' button; the example switch looks real but does nothing","fix":"Merge into one screen, make the example clearly a picture (greyed out, 'Ejemplo' tag, no real-looking switch), add one plain line on what Uber can see or do, and say how to stop the phone removing the permission (e.g. battery saving)"},{"screen":"Tu meta y tu mínimo","problem":"Guardar gives no confirmation and stays on the same screen; 10-cent +/- steps mean 5 taps for 50 cents","fix":"After saving show 'Listo: tu mínimo ahora es $6.50' and return to Semáforo; use 50-cent steps or make tap-to-type more visible"},{"screen":"Offer detail ('Te deja $114') vs Hoy/Ganancias ('Te quedan'), plus 'Tu resumen de Uber dice $34 más'","problem":"The trip number excludes the car, the day/week numbers include it; 'Lo del coche' doesn't sound like a renter's weekly cuenta; '$34 más' doesn't say more than what","fix":"Label the trip 'Te deja $114 antes de tu cuenta del coche', call it 'tu cuenta / renta del coche', and reword to 'Uber te pagó $34 más de lo que leímos; el pago final es el de Uber'"}],"ease_vs_uber":7,"understand_all":7,"uber_seq":{"1":6,"4":5,"9":4},"install":"Probably yes, because it takes out my weekly cuenta and gas and shows what I really keep, but the 'control total' warning and 'Uber podría no permitirlo' make me afraid of losing my account."}
```

### Judge 2 (sonnet)

Session report for Luis, 28, who owns his car, drives Uber and DiDi, and is impatient and fast with phones. The prototype ran from `{DIR}` = `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j2`. A "tap" below counts every action, including scrolls, swipes and wrong taps.

## Task 8: first day, make the semáforo work
- **Path:** Empezar, then Acepto on "Lo que hace Kompara" (I skimmed it and read only the bold verbs "Lee / Guarda / Manda"), then Abrir ajustes on "Enciende Kompara en Accesibilidad". The next screen, "Toca Kompara y activa el interruptor", showed a dashed mock of Android's list with a Kompara switch. I tapped Abrir ajustes again and got «Esta pantalla no está en esta prueba», then used Android back, which returned me to the instructions screen.
- **Taps:** 5.
- **Success:** yes. I knew what to do: open settings, find Kompara, flip the switch, tap Permitir, come back and see "Permiso listo". The real Android part was imagined, as the facilitator said.
- **Answer:** Abrir ajustes, then Kompara, then the switch on, then Permitir, then back to the app.
- **Hesitation:**
  - The two screens in a row look almost the same, both with a big black "Abrir ajustes", and I tapped the second one by reflex.
  - "Si ves una lista, busca Kompara" is vague. The "Así se verá" mock looks tappable but isn't.
  - "Control total de tu dispositivo" is scary, even though the line above it says Kompara only reads.
- **Ease:** 5.

## Task 1: how much today, and is the app reading?
- **Path:** none. The screen I landed on already showed the answer.
- **Taps:** 0.
- **Success:** yes.
- **Answer:** About $548 left today after gas, wear and car, from 6 trips taken. The app is reading: "Semáforo funcionando" with a green dot, goal $8 and minimum $6.
- **Hesitation:** "Te quedan hoy" made me think for a second of what's left to earn. Uber would say "Ganancias". The "ya restamos…" line told me it was net.
- **Ease:** 6.

## Task 2: Uber offer, worth it in one second?
- **Path:** none, one look.
- **Taps:** 0.
- **Success:** yes.
- **Answer:** Yellow diamond, $7.16/km net, "Entre tu meta y tu mínimo". It's not a clear yes. It clears my $6 minimum but misses my $8 goal. I'd take it only if I'm slow.
- **Hesitation:** The verdict is a phrase I have to read, and it doesn't say "take it" or "skip it". I also have to compare against two limits. The color and the $7.16 are fast to read.
- **Ease:** 6.

## Task 3: all of today's offers with $/km
- **Path:** Ver todas las ofertas de hoy, then scroll down.
- **Taps:** 2.
- **Success:** yes.
- **Answer:** 10 offers today. In order from latest, net $/km:

  | Time | Result | $/km net |
  |---|---|---|
  | 14:32 | taken | $9.49 |
  | 12:10 | skipped | $2.20 |
  | 11:48 | no reading | none |
  | 11:20 | taken | $9.81 |
  | 10:41 | taken | $9.41 |
  | 10:02 | taken | $10.46 |
  | 9:51 | skipped | $9.16 |
  | 9:24 | taken | $10.75 |
  | 8:40 | skipped | $9.20 |
  | 8:15 | taken | $10.00 |

- **Hesitation:** The list is cut off at the bottom, so I had to scroll to see all 10. The header says "neto por km" while I asked for what it paid per km, so I wasn't sure the number is the same thing. The "Sin lectura" row has no figure.
- **Ease:** 6.

## Task 4: last week after all expenses, car included
- **Path:** Ganancias tab, then the left chevron beside "19 – 25 oct · en curso".
- **Taps:** 2.
- **Success:** yes.
- **Answer:** $4,980 approx. for 12–18 Oct, from 62 trips, after gas, wear and car.
- **Hesitation:**
  - I first landed on the current week ($548) and had to guess that the small "<" goes back a week.
  - "Tu resumen de Uber dice $34 más" is unexplained. $34 more than what?
  - $1,166 floats over the Friday bar and I wondered if it was the answer.
- **Ease:** 6.

## Task 5: which app paid more per km last week
- **Path:** Comparar tab. The screen opened on "Tus apps", last week.
- **Taps:** 1.
- **Success:** yes.
- **Answer:** DiDi, $10.34/km against Uber $9.13/km, a difference of $1.21.
- **Hesitation:** "A distintas horas en cada app" looks like a caveat and I'd skip it. The bars made the answer instant.
- **Ease:** 7.

## Task 6: set minimum to $6.50/km
- **Path:**
  1. Tapped the "tu meta $8 · tu mínimo $6" row on Hoy, which opened the Semáforo screen.
  2. Tapped "Tu meta y tu mínimo".
  3. Tapped the dotted $6.00.
  4. Typed 6, ., 5, 0 on the keypad.
  5. Tapped Listo, then Guardar.
  6. Tapped back to check, and the Semáforo screen showed "$8.00/km · $6.50/km".
- **Taps:** 10.
- **Success:** yes.
- **Answer:** Minimum now $6.50.
- **Hesitation:**
  - After Guardar nothing happened except the button turning grey. There was no message and it stayed on the same screen, so I only trusted it after going back.
  - The − and + buttons move only $0.10, so 5 taps. Typing was faster, but I only found it because of the hint line "Toca la cifra para escribirla".
  - The gear icon felt like the natural place to look, but I didn't need it.
- **Ease:** 5.

## Task 7: semáforo stopped showing, why and what to do
- **Path:**
  1. Saw a small "K" in the status bar on the Uber map.
  2. Swiped down from the top.
  3. Tapped the notification "Se apagó el semáforo".
  4. It opened Hoy with "Falta el permiso de Accesibilidad" and "Tu teléfono lo quitó".
  5. Tapped "Volver a dar el permiso", which opened the same setup steps as task 8.
- **Taps:** 3.
- **Success:** yes.
- **Answer:** Android removed Kompara's Accessibility permission, off since yesterday 20:31. Tap Volver a dar el permiso, turn Kompara back on in Accessibility, and tap Permitir.
- **Hesitation:**
  - "Abre Kompara al estacionarte" is good advice, but if I hadn't swiped down I wouldn't have known. The "K" is tiny.
  - It doesn't say why the phone removed it, or how to stop that happening again.
  - Hoy shows only "Ayer te quedaron…" and no hint that today's trips are being missed.
- **Ease:** 6.

## Task 9: what my last trip left me
- **Path:** tapped the top row, "La tomaste · 14:32", in "Últimas ofertas" on Hoy.
- **Taps:** 1.
- **Success:** yes.
- **Answer:** About $114. That is the $148 offer minus $34 for gas and wear. The sheet also shows $9.49/km and $285/h.
- **Hesitation:** The sheet is titled "Oferta de las 14:32", not a trip, and it says "el pago final puede cambiar". So it's an estimate of the offer, not what actually landed. The car cost is not taken off per trip.
- **Ease:** 6.

## (a) Three biggest usability problems
1. **Guardar gives no feedback** (the "Tu meta y tu mínimo" screen).
   - Problem: after tapping Guardar the screen stays put and the button only greys out, so I had to back out to confirm the change.
   - Fix: close the screen on save, or show a short message such as "Guardado: mínimo $6.50", and make the + and − steps bigger.
2. **The permission setup is confusing** (Enciende Kompara en Accesibilidad, then Toca Kompara y activa el interruptor).
   - Problem: two near-identical screens with the same black "Abrir ajustes", a dashed "Así se verá" mock that looks tappable, and the frightening "control total" wording.
   - Fix: make it one step-by-step screen with numbered steps and one button. Put the reassurance ("solo lee, no toca nada") right beside the Permitir mock. Show a clear "Permiso listo" state on return.
3. **Money labels differ from what drivers expect** (Hoy, Ganancias, Comparar).
   - Problem: "Te quedan hoy · aprox." reads like what's left to earn, where Uber says "Ganancias". "Tu resumen de Uber dice $34 más" and "A distintas horas en cada app" are unexplained. "Neto por km" in the offer list isn't what I asked for ("lo que pagó por km").
   - Fix: use "Ganancias netas de hoy". Spell out the Uber line, for example "Uber reporta $34 más porque no resta tus gastos". Turn "A distintas horas" into a plain sentence. Offer a gross/net toggle on the offer list.

Also worth fixing: the chip verdict "Entre tu meta y tu mínimo" doesn't say take it or skip it, and the "K" status icon is too small for a driver to notice.

## (b) Ease versus the Uber Driver app
8 out of 10. The tabs sit where I expect (Hoy, Ganancias, Comparar), and numbers are big. Uber's Ganancias is gross only and has no per-km or per-app view, so Kompara answers tasks 3 to 5 faster than Uber does.

## (c) Would I understand every screen without help
8 out of 10. The only places I'd get lost are the Android permission steps, "Tu resumen de Uber dice $34 más", and "A distintas horas".

## (d) Same tasks in the Uber Driver app
- Task 1 (today's earnings): 6
- Task 4 (last week's earnings): 5. Gross only, and I'd have to flip the arrows to the right week.
- Task 9 (last trip's detail): 5

## (e) Would I install it
Yes, because it tells me per km, net of gas and car, whether an offer is worth taking and what I really kept. Uber doesn't show either. I'd want the permission step to be painless, and I'd worry a bit about my Uber/DiDi accounts.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes (instructions) > Abrir ajustes (settings mock, got 'no está en esta prueba') > back; Android steps imagined","taps":5,"success":"yes","answer":"Abrir ajustes, find Kompara in Accesibilidad, switch on, tap Permitir, return to the app and see 'Permiso listo'","ease":5,"stuck":"Two near-identical screens with the same 'Abrir ajustes' button, so I tapped the second by reflex. 'Si ves una lista, busca Kompara' is vague, the dashed mock looks tappable, and 'control total' is scary."},"1":{"path":"none, Hoy was already showing","taps":0,"success":"yes","answer":"About $548 net today from 6 trips; semáforo funcionando (green), goal $8 and minimum $6","ease":6,"stuck":"'Te quedan hoy' briefly read as what's left to earn; Uber says 'Ganancias'."},"2":{"path":"one look at the Uber offer with the chip","taps":0,"success":"yes","answer":"Yellow, $7.16/km net, between my goal $8 and minimum $6: borderline, take only if slow","ease":6,"stuck":"Verdict is a phrase I must read and doesn't say take it or skip it; compares against two limits."},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":2,"success":"yes","answer":"10 offers today: 14:32 $9.49 taken, 12:10 $2.20 skipped, 11:48 no reading, 11:20 $9.81 taken, 10:41 $9.41 taken, 10:02 $10.46 taken, 9:51 $9.16 skipped, 9:24 $10.75 taken, 8:40 $9.20 skipped, 8:15 $10.00 taken","ease":6,"stuck":"List cut off at the bottom so I had to scroll; figures are 'neto por km', not what the offer paid per km; the 'Sin lectura' row has no figure."},"4":{"path":"Ganancias tab > left chevron beside '19-25 oct en curso'","taps":2,"success":"yes","answer":"$4,980 approx. net for 12-18 Oct (62 trips, after gas, wear and car)","ease":6,"stuck":"Landed on the current week and had to guess the small '<' goes back a week; 'Tu resumen de Uber dice $34 más' is unexplained; the $1,166 over the Friday bar is distracting."},"5":{"path":"Comparar tab (Tus apps, last week)","taps":1,"success":"yes","answer":"DiDi paid more: $10.34/km vs Uber $9.13/km (+$1.21)","ease":7,"stuck":"'A distintas horas en cada app' is unclear."},"6":{"path":"tap limits row on Hoy > Tu meta y tu mínimo > tap $6.00 > keypad 6 . 5 0 > Listo > Guardar > back to verify","taps":10,"success":"yes","answer":"Minimum is now $6.50/km (Semáforo screen shows $8.00/km · $6.50/km)","ease":5,"stuck":"Guardar gave no message, only greyed out and stayed put, so I had to go back to confirm; +/- move only $0.10; typing is found only through a small hint."},"7":{"path":"saw K in status bar > swipe down from top > tap notification 'Se apagó el semáforo' > Hoy 'Falta el permiso de Accesibilidad' > Volver a dar el permiso","taps":3,"success":"yes","answer":"Android removed Kompara's Accessibility permission (off since yesterday 20:31); tap Volver a dar el permiso, turn Kompara back on in Accesibilidad, tap Permitir","ease":6,"stuck":"Only found it via the tiny K icon and the notification; it doesn't say why the phone removed the permission or how to prevent it; Hoy has no hint that today's trips are being missed."},"9":{"path":"tap top row 'La tomaste 14:32' in Últimas ofertas","taps":1,"success":"yes","answer":"About $114 (offer $148 minus $34 gas and wear), $9.49/km, $285/h","ease":6,"stuck":"Sheet is titled 'Oferta' not trip and says the final pay can change; car cost isn't subtracted per trip."}},"problems":[{"screen":"Tu meta y tu mínimo (after typing the number and tapping Guardar)","problem":"No confirmation after saving: the button greys out, the screen stays put, and I had to go back to see that $6.50 stuck. The +/- buttons move only $0.10.","fix":"Close the screen on save or show a short 'Guardado: mínimo $6.50' message; make the +/- steps bigger or hint that tapping the number types it."},{"screen":"Enciende Kompara en Accesibilidad and Toca Kompara y activa el interruptor","problem":"Two near-identical setup screens with the same 'Abrir ajustes' button, a dashed mock that looks tappable, a vague 'busca Kompara', and scary 'control total' wording make the permission step confusing.","fix":"One numbered step screen with one button; put the 'solo lee, no toca nada' reassurance beside the Permitir mock; show a clear 'Permiso listo' state on return."},{"screen":"Hoy, Ganancias, Comparar and the offer list (money labels)","problem":"'Te quedan hoy · aprox.' reads like what's left to earn where Uber says 'Ganancias'; 'Tu resumen de Uber dice $34 más' and 'A distintas horas en cada app' are unexplained; 'neto por km' isn't what I asked for ('lo que pagó por km').","fix":"Use 'Ganancias netas de hoy'; explain the Uber line in plain words (e.g. 'Uber reporta $34 más porque no resta tus gastos'); explain or drop 'A distintas horas'; offer a gross/net toggle on the offer list."}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":6,"4":5,"9":5},"install":"Yes, because it tells me per km, net of gas and car, whether an offer is worth taking and what I really kept, which Uber doesn't show, as long as the permission setup is painless and doesn't risk my Uber/DiDi accounts."}
```

### Judge 3 (opus)

**Martha's session report: Kompara prototype, walk 5 (j3)**

I'm Martha, 41. I drive Uber and DiDi in a rented car and I can't tell red from green. I relied only on shapes (▲ ◆ ▼), position and words.

**Task 8: first day, get the semáforo working**
- **Path:** Empezar → Acepto → Abrir ajustes → Abrir ajustes again (the second one showed «Esta pantalla no está en esta prueba»). Then the Android step I had to imagine: find Kompara, turn on the switch, Permitir, come back.
- **Taps:** 4. **Success:** yes. **Ease:** 6.
- **Answer:** the semáforo is turned on through Accesibilidad.
- **Where I hesitated:** the first «Abrir ajustes» didn't open settings. It opened another explanation screen with the same «Abrir ajustes» button, so I thought my tap hadn't worked. On the welcome screen the legend has shapes and words, so I understood the semáforo without the colours. The «control total» warning would still scare me, but the app warned me first.

**Task 1: today's earnings, and is it reading offers?**
- **Path:** nothing, it was all on Hoy. **Taps:** 0. **Success:** yes. **Ease:** 7.
- **Answer:** $548 so far today (approximate, after gas, wear and car), 6 trips taken. «Semáforo funcionando» means it's reading.
- **Where I hesitated:** for a second I read «Te quedan hoy» as "what's still left to earn today". The status dot only shows a colour, but the words carry it.

**Task 2: one glance at an Uber offer**
- **Path:** none. **Taps:** 0. **Success:** yes. **Ease:** 6.
- **Answer:** "So-so. $7.16/km net, with the diamond and «Entre tu meta y tu mínimo». It's above my $6 minimum but below my $8 goal. I'd take it if nothing better comes."
- **Where I hesitated:** the diamond plus the words are clear at a glance. The little marker on the bars is too small to read while driving.

**Task 3: all of today's offers with $/km**
- **Path:** «Ver todas las ofertas de hoy», then one scroll. **Taps:** 1. **Success:** yes. **Ease:** 6.
- **Answer:** 10 offers: $9.49, $2.20, one «Sin lectura», $9.81, $9.41, $10.46, $9.16, $10.75, $9.20, $10.00 per km.
- **Where I hesitated:** «2 ofertas verdes se fueron sin tomar» means nothing to me because I can't see green. The list icons differ mostly by colour. I read the words «Arriba de tu meta» / «Abajo de tu mínimo» instead.

**Task 4: last week, after all costs including the car**
- **Path:** Ganancias → ‹ arrow (previous week) → «62 viajes tomados» row to see the breakdown. **Taps:** 3. **Success:** yes. **Ease:** 6.
- **Answer:** $4,980. That is $8,400 in fares, minus $1,920 for gas and wear, minus $1,500 car rent.
- **Where I hesitated:** «ya restamos… coche» didn't tell me whether my rent was included. I had to open the breakdown to check. The previous-week arrow is small.

**Task 5: Uber or DiDi, who paid more per km last week**
- **Path:** Comparar. **Taps:** 1. **Success:** yes. **Ease:** 7.
- **Answer:** DiDi, at $10.34/km against Uber's $9.13/km, so $1.21 more.
- **Where I hesitated:** «A distintas horas en cada app» was a little unclear, but it didn't stop me.

**Task 6: change my minimum to $6.50**
- **Path:** «tu meta $8 · tu mínimo $6» row on Hoy → «Tu meta y tu mínimo» → + five times → Guardar → back arrow to check it saved.
- **Taps:** 9. **Success:** yes, the Semáforo screen then showed $6.50/km. **Ease:** 5.
- **Where I hesitated:** the labels «verde desde» and «rojo abajo de» mean nothing to me. It took five taps at $0.10 each. After Guardar no message appeared; the button just went grey, so I wasn't sure it had saved and went back to check.

**Task 7: the semáforo stopped appearing**
- **Path:** Abrir Kompara → «Volver a dar el permiso». **Taps:** 2. **Success:** yes. **Ease:** 7.
- **Answer:** the phone took away the Accessibility permission (semáforo off since yesterday 20:31). Fix: Abrir ajustes → Kompara → turn on the switch → Permitir.
- **Where I hesitated:** inside Uber the only clue was a tiny K in the status bar, so I opened the app. It doesn't say why the phone removed the permission or how to stop it happening again.

**Task 9: what my last trip left me**
- **Path:** the most recent «La tomaste» (14:32). **Taps:** 1. **Success:** yes. **Ease:** 7.
- **Answer:** about $114: $148 fare minus $34 gas and wear. The car is taken off per day, not per trip.

**(a) The three biggest problems**
1. **Colour words and colour-only icons** (Tu día, the meta/mínimo screen, offer lists).
   - **Problem:** «ofertas verdes», «verde desde» and «rojo abajo de» are meaningless if you can't see colour, and the list icons differ mainly by colour.
   - **Fix:** use the chip's shapes and words everywhere, for example «2 ofertas arriba de tu meta se fueron sin tomar», «Tu meta · ▲ desde $8», «Tu mínimo · ▼ abajo de $6», and put ▲ ◆ ▼ in the list icons.
2. **No confirmation after saving meta/mínimo, and slow steps.**
   - **Problem:** Guardar just turns grey. Five taps at $0.10 each.
   - **Fix:** show «Guardado: tu mínimo ahora es $6.50» and return to Semáforo. Open a number keyboard when you tap the amount, or allow bigger steps.
3. **Two «Abrir ajustes» buttons in a row in onboarding.**
   - **Problem:** the first one opens another explanation screen with the same button, so it looks like the tap failed.
   - **Fix:** go straight to Android settings, or rename the first button «Siguiente», or merge the two screens.

**(b) Ease compared with the Uber Driver app:** 8/10.

**(c) Would I understand every screen without help?** 8/10.

**(d) The same tasks in my Uber Driver app:** today's earnings 6, last week 5, last trip's detail 4.

**(e) Would I install it?** Yes. It tells me with shapes and words whether a trip beats my own limits and takes my car rent off, which Uber never does, but it should stop saying "verde" and "rojo".

The session log is at /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j3/log.json and the screenshots are in /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j3.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Abrir ajustes (prototype end; imagined Android: find Kompara, toggle, Permitir, return)","taps":4,"success":"yes","answer":"Semáforo is turned on through Accesibilidad: open settings, turn on Kompara, accept control total, come back","ease":6,"stuck":"First Abrir ajustes opened another explainer with the same button, felt like the tap failed; control total wording is scary even with the warning"},"1":{"path":"none (all on Hoy)","taps":0,"success":"yes","answer":"$548 so far today (approx, after gas, wear and car), 6 trips; Semáforo funcionando = it is reading offers","ease":7,"stuck":"Briefly read Te quedan hoy as still left to earn; status dot is colour-only but the words carry it"},"2":{"path":"none (one glance at chip)","taps":0,"success":"yes","answer":"So-so: $7.16/km net, diamond, Entre tu meta y tu mínimo: above my $6 minimum, below my $8 goal; ok if nothing better","ease":6,"stuck":"Diamond plus words readable; small bar marker unreadable at a glance"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: 9.49, 2.20, sin lectura, 9.81, 9.41, 10.46, 9.16, 10.75, 9.20, 10.00 $/km","ease":6,"stuck":"2 ofertas verdes and colour-filled list icons mean nothing to me; relied on the words Arriba de tu meta / Abajo de tu mínimo"},"4":{"path":"Ganancias > previous-week arrow > 62 viajes tomados (breakdown sheet)","taps":3,"success":"yes","answer":"$4,980 (fares $8,400 - gas and wear $1,920 - car rent $1,500)","ease":6,"stuck":"Not sure ya restamos coche included my rent until I opened the breakdown; small arrow"},"5":{"path":"Comparar","taps":1,"success":"yes","answer":"DiDi: $10.34/km vs Uber $9.13/km ($1.21 more)","ease":7,"stuck":"A distintas horas en cada app slightly unclear"},"6":{"path":"tu meta/tu mínimo row on Hoy > Tu meta y tu mínimo > + x5 > Guardar > back arrow to check","taps":9,"success":"yes","answer":"Minimum set to $6.50/km (Semáforo screen shows $8.00 · $6.50)","ease":5,"stuck":"Labels verde desde / rojo abajo de are colour words; 5 taps at $0.10; no confirmation after Guardar, button just greyed, had to go back to verify"},"7":{"path":"Abrir Kompara > Volver a dar el permiso","taps":2,"success":"yes","answer":"Phone removed the Accessibility permission (off since yesterday 20:31); re-grant: Abrir ajustes > Kompara > switch on > Permitir","ease":7,"stuck":"Only clue inside Uber was a tiny K in the status bar; app does not say why the phone removed it or how to prevent it"},"9":{"path":"La tomaste (14:32) on Hoy","taps":1,"success":"yes","answer":"About $114 ($148 fare - $34 gas and wear; car taken off per day)","ease":7,"stuck":"none"}},"problems":[{"screen":"Tu día banner, Tu meta y tu mínimo labels, offer list icons","problem":"Colour words (ofertas verdes, verde desde, rojo abajo de) and colour-only list icons are meaningless with red-green colour blindness","fix":"Use the chip's shapes and words everywhere: 2 ofertas arriba de tu meta se fueron sin tomar; Tu meta ▲ desde / Tu mínimo ▼ abajo de; put ▲◆▼ in the list icons"},{"screen":"Tu meta y tu mínimo (editor)","problem":"Guardar gives no confirmation (just turns grey) and $0.10 steps need many taps","fix":"Show a toast Guardado: tu mínimo ahora es $6.50 and return to Semáforo; open a number keyboard on tapping the amount or allow bigger steps"},{"screen":"Onboarding: Enciende Kompara en Accesibilidad > Toca Kompara y activa el interruptor","problem":"Two Abrir ajustes buttons in a row; the first opens another explainer, so the tap seems to fail","fix":"Go straight to Android settings, or rename the first button Siguiente / merge the two screens"}],"ease_vs_uber":8,"understand_all":8,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes, because it tells me with shapes and words whether a trip beats my own limits and takes my car rent off, which Uber never does, but it should stop saying verde and rojo."}
```

### Judge 4 (sonnet)

**Session report: Jorge, 60, DiDi mostly, large text and reading glasses, reads every word before tapping**

I used a mobile phone prototype only through the tool. I counted every tap, including wrong ones. I also counted a scroll or swipe as one action. Session log: `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j4/log.json`

**Task 8: first day, make the semáforo work**
- Path: Empezar, Acepto, Abrir ajustes, Abrir ajustes (again), back. That is 5 taps.
- Success: yes, counting the imagined Android step the facilitator allowed.
- Answer: I accepted the "Lo que hace Kompara" screen. I followed the three steps: find Kompara in the list, turn on the switch, tap Permitir and come back.
- Where I hesitated:
  - On the privacy screen I read "Uber o DiDi podrían no permitirlo". That worried me, because I don't want my account in trouble.
  - On "Enciende Kompara en Accesibilidad", the box showing "¿Permitir… control total de tu dispositivo?" with grey Rechazar and Permitir looks tappable but isn't.
  - Step 1 has an arrow that looks tappable, and I wasn't sure it was just text.
  - The next screen is a drawing of the Android list, called "Así se verá", and it has its own second "Abrir ajustes" button. I tapped it and got "Esta pantalla no está en esta prueba".
  - When I went back the screen still said "Semáforo apagado". I never saw "Permiso listo" or any proof it worked.
  - "Control total" sounds scary for an app that "solo lee".
- Ease: 5.

**Task 1: today's earnings, is the app reading offers**
- Path: no taps.
- Success: yes.
- Answer: $548 so far today. The line says "Te quedan hoy · aprox." and "6 viajes tomados", with gas, wear and car already subtracted. Under the title the app says "Semáforo funcionando" in green, so it is reading.
- Where I hesitated: "Te quedan hoy" could mean money still left to earn. I only understood it after reading the line below it.
- Ease: 6.

**Task 2: Uber offer, in one glance**
- Path: no taps.
- Success: yes.
- Answer: Yellow diamond, $7.16 per km net. It is "Entre tu meta y tu mínimo", so it is acceptable but not good. It is above my $6 floor and below my $8 goal. I would take it only if I have nothing better.
- Where I hesitated: "Entre tu meta y tu mínimo" and the "tu meta $8 / tu mínimo $6" lines are small grey text on black, hard with reading glasses while driving. Yellow does not tell me "take it" or "skip it". The big Aceptar button sits right there asking me to decide.
- Ease: 5.

**Task 3: all offers today, with pay per km**
- Path: "Ver todas las ofertas de hoy", then scroll down. That is 2 actions.
- Success: yes.
- Answer: There are 10 offers, with $/km net:

| Time | Result | $/km net |
|---|---|---|
| 14:32 | Tomada | $9.49 |
| 12:10 | No tomada (red) | $2.20 |
| 11:48 | Sin lectura | none |
| 11:20 | Tomada | $9.81 |
| 10:41 | Tomada | $9.41 |
| 10:02 | Tomada | $10.46 |
| 9:51 | No tomada | $9.16 |
| 9:24 | Tomada | $10.75 |
| 8:40 | No tomada | $9.20 |
| 8:15 | Tomada | $10.00 |

- Where I hesitated: I had to scroll to see all 10. "Sin lectura" has no number, which is fine. The "2 ofertas verdes se fueron sin tomar" row is useful, but I did not need it.
- Ease: 6.

**Task 4: last week's net, car included**
- Path: Ganancias tab, then the small "<" arrow for the previous week. That is 2 taps.
- Success: yes.
- Answer: $4,980 for 12–18 Oct, "Te quedaron esa semana · aprox.", with gas, wear and car already subtracted, from 62 trips.
- Where I hesitated:
  - The "<" arrow is tiny for my fingers.
  - I thought the $1,166 above the tallest bar might be the total, but it is just Friday.
  - "Tu resumen de Uber dice $34 más" confused me. I did not know whether my number was wrong.
  - The current week showed "Cada día se restan $214 del coche", but the last-week screen does not repeat it. I trusted the line "ya restamos… coche".
- Ease: 6.

**Task 5: Uber or DiDi, which paid more per km last week**
- Path: Comparar. That is 1 tap.
- Success: yes.
- Answer: DiDi paid more. DiDi was $10.34 per km and Uber $9.13 per km, so DiDi paid $1.21 more per km. That is for 12–18 Oct, net of gas and wear.
- Where I hesitated: I guessed "Comparar" and was lucky, because I could also have tried Ganancias. "A distintas horas en cada app" has a chevron and I did not understand it. It may mean the comparison is not fair.
- Ease: 6.

**Task 6: change my minimum to $6.50**
- Path: the "Semáforo funcionando / tu meta · tu mínimo" row, then "Tu meta y tu mínimo". Then I tapped the $6.00 figure and typed 6, a dot, 5, 0 on the keypad, then Listo and Guardar. Then I tapped the back arrow to check. That is 10 taps.
- Success: yes. The Semáforo screen then showed $8.00 · $6.50.
- Answer: Done, minimum $6.50.
- Where I hesitated:
  - I first tried the row that shows the numbers. Nothing said "settings".
  - The "+" and "–" buttons move only $0.10, so that would be 5 taps.
  - The typing option is just a dotted underline plus a small line saying "Toca la cifra para escribirla". Without that line I would never find it.
  - After Guardar the button went grey with no "Guardado" message. I did not know whether it saved, so I went back to check.
- Ease: 5.

**Task 7: the semáforo stopped during my shift**
- Path: swipe down from the top, tap the "Se apagó el semáforo" notification, tap "Volver a dar el permiso". That is 3 actions.
- Success: yes.
- Answer: The phone removed Kompara's Accessibility permission, so Kompara can no longer see offers. The fix is to tap "Volver a dar el permiso". That opens the same steps as day one. The notification also says to open Kompara when parked.
- Where I hesitated:
  - The map I saw just showed no overlay and no warning, so I had to go looking myself.
  - "Tu teléfono lo quitó" does not say why the phone did that or how to stop it happening again.
  - The notification says "ayer 20:31", which confused me. The shift is today.
- Ease: 6.

**Task 9: what my last trip left me**
- Path: tap "La tomaste", the top row. That is 1 tap.
- Success: yes.
- Answer: About $114. The offer was $148, minus $34 for gas and wear. That is $9.49 per km net, or about $285 per hour, over 24 minutes.
- Where I hesitated:
  - The row says "oferta" and not "viaje".
  - "El pago final puede cambiar" worries me. Is $114 real or just an estimate?
  - It says the car is subtracted per day and not per trip. I understand it but it takes reading.
- Ease: 6.

**(a) The three biggest usability problems**
1. **Screen:** the permission steps ("Enciende Kompara en Accesibilidad", then the drawn Android list). **Problem:** the screen draws a fake Android dialog with grey buttons and a second "Abrir ajustes" button. The words "control total" are scary. After returning I got no clear "ya quedó". **Fix:** use one real button per step, and label the picture clearly as an example, not tappable. Say plainly "Android lo dice así, pero Kompara solo lee". Show a big "Listo, el semáforo está encendido" when I return. Also explain what the app does if Uber or DiDi block it.
2. **Screen:** the offer overlay (the yellow chip, "$7.16/km neto"). **Problem:** it says "Entre tu meta y tu mínimo" in small grey text on black. It does not tell me to take it or skip it, and I cannot read it quickly while driving. **Fix:** put one big plain word on the chip ("Regular" or "Aceptable") and make the goal and minimum lines larger with more contrast. Make it follow the phone's large text setting.
3. **Screen:** "Tu meta y tu mínimo". **Problem:** the typing option is hidden behind a dotted-underline figure, the "+" and "–" move only $0.10, and Guardar greys out with no confirmation. **Fix:** make the figure look like a box I can type in. Use $0.50 steps on the buttons. Show a clear "Guardado" message after saving.

**(b) Ease compared with the Uber Driver app: 6 of 10.** Uber's Ganancias (the ref screenshot) is simpler, with one number and a bar chart. But it doesn't subtract gas or the car. Kompara answers my real questions, like what I keep and which app pays better per km. It also makes me read a lot more.

**(c) Would I understand every screen without help: 6 of 10.** The words I stumbled on were "neto", "Sin lectura", "A distintas horas en cada app", "control total" and "Tu resumen de Uber dice $34 más". The notification about the IMSS also lost me.

**(d) The same tasks in my Uber Driver app:**
- Task 1, today's earnings: 5. Ganancias shows the week, and I would have to tap the day's bar.
- Task 4, last week's earnings: 4. I can get last week's gross but not after all expenses including the car.
- Task 9, last trip detail: 5. Uber has a trip history, but it takes more screens and only shows Uber trips.

**(e) Would I install it:** Probably yes, for the traffic light and the real weekly net after the car. But only if someone I trust helps me with the permission setup and I am sure it won't get my Uber or DiDi account banned.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Abrir ajustes (preview) > back","taps":5,"success":"yes","answer":"Accepted privacy screen, followed the 3 accessibility steps (find Kompara, switch on, Permitir and return); the Android step was imagined, then back showed Semáforo apagado with no confirmation","ease":5,"stuck":"Fake Android dialog with grey Rechazar/Permitir looks tappable; second Abrir ajustes on the 'Así se verá' preview; 'control total' wording and 'Uber o DiDi podrían no permitirlo' are alarming; no clear success message on return"},"1":{"path":"none (screen already open)","taps":0,"success":"yes","answer":"$548 so far today (6 trips, net of gas, wear and car); Semáforo funcionando, so the app is reading offers","ease":6,"stuck":"'Te quedan hoy' could be read as money still to earn, not money kept so far"},"2":{"path":"none (one look at the overlay)","taps":0,"success":"yes","answer":"Yellow diamond, $7.16/km net, between goal $8 and minimum $6: acceptable but not good, above my floor, take it only if nothing better","ease":5,"stuck":"Chip text 'Entre tu meta y tu mínimo' and the meta/mínimo lines are small grey on black; no plain take-it or skip-it word; big Aceptar button pushes a decision"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":2,"success":"yes","answer":"10 offers today with $/km net: 14:32 $9.49 taken, 12:10 $2.20 skipped, 11:48 Sin lectura, 11:20 $9.81 taken, 10:41 $9.41 taken, 10:02 $10.46 taken, 9:51 $9.16 skipped, 9:24 $10.75 taken, 8:40 $9.20 skipped, 8:15 $10.00 taken","ease":6,"stuck":"Had to scroll to see all 10; Sin lectura row has no number"},"4":{"path":"Ganancias > previous-week arrow (<)","taps":2,"success":"yes","answer":"$4,980 for 12-18 Oct (62 trips), after gas, wear and car","ease":6,"stuck":"Tiny '<' arrow; $1,166 above the best bar looks like a total; 'Tu resumen de Uber dice $34 más' is confusing; last-week screen does not repeat the daily car charge"},"5":{"path":"Comparar","taps":1,"success":"yes","answer":"DiDi paid more: $10.34/km vs Uber $9.13/km, DiDi $1.21 more per km, 12-18 Oct, net of gas and wear","ease":6,"stuck":"Guessed the Comparar tab; 'A distintas horas en cada app' with a chevron was unclear"},"6":{"path":"Semáforo row (tu meta/tu mínimo) > Tu meta y tu mínimo > tap $6.00 figure > keypad 6 . 5 0 > Listo > Guardar > back to verify","taps":10,"success":"yes","answer":"Minimum changed to $6.50/km; Semáforo screen now shows $8.00/km · $6.50/km","ease":5,"stuck":"Typing option hidden behind a dotted underline; +/- steps of $0.10 would take 5 taps; Guardar greyed out with no 'Guardado' message so I went back to verify"},"7":{"path":"swipe down from top > tap notification 'Se apagó el semáforo' > Volver a dar el permiso","taps":3,"success":"yes","answer":"The phone removed Kompara's Accessibility permission so it can't see offers; tap 'Volver a dar el permiso' and redo the permission steps, preferably when parked","ease":6,"stuck":"Nothing on the map told me the semáforo was off; 'Tu teléfono lo quitó' doesn't say why or how to prevent it; notification says 'ayer 20:31'"},"9":{"path":"tap 'La tomaste' (latest taken offer)","taps":1,"success":"yes","answer":"About $114 (offer $148 minus $34 gas and wear), $9.49/km net, about $285/h, 24 min; car cost is subtracted per day","ease":6,"stuck":"Labelled 'oferta' not 'viaje'; 'el pago final puede cambiar' makes the $114 feel uncertain"}},"problems":[{"screen":"Permission steps (Enciende Kompara en Accesibilidad and the drawn Android list)","problem":"A drawn Android dialog with grey buttons that look tappable, a second Abrir ajustes button, 'control total' wording, and no clear 'it worked' when I return","fix":"One real button per step; label the picture as a non-tappable example; say 'Android lo dice así, pero Kompara solo lee'; show a big 'Listo, el semáforo está encendido' on return; explain what happens if Uber or DiDi block it"},{"screen":"Offer overlay chip (yellow, $7.16/km neto)","problem":"Verdict is 'Entre tu meta y tu mínimo' in small grey text on black; no plain take-it or skip-it word; hard to read at a glance with reading glasses while driving","fix":"Put one big plain word on the chip (Regular or Aceptable), enlarge the meta and mínimo lines with higher contrast, and follow the phone's large-text setting"},{"screen":"Tu meta y tu mínimo","problem":"Typing is hidden behind a dotted-underline figure, +/- moves only $0.10 (10 taps total for a $0.50 change), and Guardar greys out with no confirmation","fix":"Make the figure look like an input box, use $0.50 steps on the buttons, and show a clear 'Guardado' message after saving"}],"ease_vs_uber":6,"understand_all":6,"uber_seq":{"1":5,"4":4,"9":5},"install":"Probably yes for the traffic light and the real weekly net after the car, but only if someone I trust helps with the permission setup and I'm sure it won't get my Uber or DiDi account banned."}
```

### Judge 5 (sonnet)

Session report: Ana, 34, rents her car, drives Uber, medium comfort. I used only the tool, the screenshots and the two reference images. A "tap" below means any action I took, including a scroll, a swipe or a back.

**Task 8: make the semáforo work from the welcome screen**
- Path: Empezar, Acepto, Abrir ajustes, Abrir ajustes again, back.
- Taps: 5.
- Success: partial.
- Answer: I read the permission screen and accepted. The second "Abrir ajustes" opened a mock of Android's Accessibility list, with a message "Esta pantalla no está en esta prueba". As the facilitator said, I imagined finding Kompara, switching it on, accepting "control total" and coming back.
- Hesitation:
  - Two buttons both say "Abrir ajustes", one on the permission screen and one on the preview, so I wondered whether the first tap had worked.
  - "Uber o DiDi podrían no permitirlo" and "control total" made me hesitate before "Acepto".
  - The grey "Rechazar / Permitir" box in "Así se verá" looks like real buttons.
  - After going back the screen still said "Semáforo apagado", so I never saw proof that it worked.
- Ease: 5.

**Task 1: today's total and whether the app is reading**
- Path: none, the Hoy screen answered it.
- Taps: 0.
- Success: yes.
- Answer: "Te quedan hoy aprox. $548" after gasoline, wear and car, from 6 trips. "Semáforo funcionando" with my meta $8 and mínimo $6, so it is reading.
- Hesitation: "Te quedan" is a bit odd for "what I made". I only see the net figure, not what I billed. The green dot plus the "Semáforo funcionando" row works as the status.
- Ease: 6.

**Task 2: the Uber offer in one glance**
- Path: none, one look.
- Taps: 0.
- Success: yes.
- Answer: The chip is yellow, "$7.16/km neto, Entre tu meta y tu mínimo". That is above my $6 floor and below my $8 goal, so it's so-so. I'd take it if I had nothing better.
- Hesitation: The yellow chip doesn't say "take it" or "skip it". I had to compare 7.16 against two numbers myself. The screen text had already printed once when the previous task closed, then I looked once at the screenshot.
- Ease: 6.

**Task 3: all of today's offers with $ per km**
- Path: Ver todas las ofertas de hoy, scroll down.
- Taps: 2.
- Success: yes.
- Answer: 10 offers from 14:32 back to 8:15:

| Time | Result | $/km |
|---|---|---|
| 14:32 | La tomaste | 9.49 |
| 12:10 | Se fue sin tomar | 2.20 |
| 11:48 | Sin lectura | none |
| 11:20 | La tomaste | 9.81 |
| 10:41 | La tomaste | 9.41 |
| 10:02 | La tomaste | 10.46 |
| 9:51 | Se fue sin tomar | 9.16 |
| 9:24 | La tomaste | 10.75 |
| 8:40 | Se fue sin tomar | 9.20 |
| 8:15 | La tomaste | 10.00 |

- Hesitation: "Ofertas de hoy · 10" told me how many to expect. The column is "neto por km", which is net and not what the offer paid. Rows show no peso amount.
- Ease: 6.

**Task 4: last week after all costs, including the car**
- Path: Ganancias tab, left chevron.
- Taps: 2.
- Success: yes.
- Answer: 12–18 oct, "Te quedaron esa semana aprox. $4,980" from 62 trips, already net of gasoline, wear and car.
- Hesitation: The tab opened on the current week ($548). I had to guess the small "<" arrow, with no label. The "$1,166" above one bar looked like a second total until I saw it was the best day. "Tu resumen de Uber dice $34 más" is unexplained.
- Ease: 6.

**Task 5: which app paid more per km last week**
- Path: Comparar.
- Taps: 1.
- Success: yes.
- Answer: DiDi, $10.34/km against Uber $9.13/km, so DiDi paid $1.21 more per km.
- Hesitation: I guessed the tab name and it opened on last week already. "A distintas horas en cada app" is unclear, and "Tu ciudad" next to "Tus apps" is a small unknown.
- Ease: 7.

**Task 6: change the mínimo to $6.50**
- Path:
  1. The "tu meta $8 · tu mínimo $6" row on Hoy.
  2. "Tu meta y tu mínimo" on the Semáforo screen.
  3. Tapped the $6.00 figure.
  4. Keypad 6, ., 5, 0.
  5. Listo.
  6. Guardar.
  7. Back arrow, to check it saved.
- Taps: 10.
- Success: yes.
- Answer: The Semáforo screen then showed $8.00/km · $6.50/km.
- Hesitation:
  - I didn't know where the setting lived. I took the row with my numbers on it over the gear.
  - The path is two screens deep.
  - After Guardar the button just went grey, with no "Guardado" message and no leaving the screen. I only believed it after going back and seeing $6.50.
  - Using "+" would have taken 5 taps; typing felt faster.
- Ease: 5.

**Task 7: the semáforo stopped during the shift**
- Path: swipe down from the top, tap the "Se apagó el semáforo" notification, tap "Volver a dar el permiso".
- Taps: 3.
- Success: yes.
- Answer: Phone settings took away Kompara's Accessibility permission, "desde ayer 20:31", so Kompara can't see my offers. The fix is to tap "Volver a dar el permiso" and turn it on again, the same flow as day one. The notification also says "Abre Kompara al estacionarte", which is sensible.
- Hesitation: I noticed the small "K" in the status bar and swiped. That worked. The Hoy screen then explained it plainly. "Tu teléfono lo quitó" doesn't say why the phone would do that.
- Ease: 6.

**Task 9: what my last trip left me**
- Path: tapped the "La tomaste" row at 14:32 on Hoy.
- Taps: 1.
- Success: yes.
- Answer: "Te deja aprox. $114". The offer was $148, minus $34 for gasoline and wear (12 km × $2.84), which is $9.49/km net or $285/h over 24 min. The car cost is taken off per day, not per trip.
- Hesitation: It's the offer's estimate, not the real final payout ("el pago final puede cambiar"). I assumed the top "La tomaste" is the latest trip.
- Ease: 6.

**(a) Three biggest usability problems**
1. **Settings screen (Tu meta y tu mínimo).** It is deep, and Guardar gives no confirmation.
   - The problem: I had to guess the path (Hoy row, then Semáforo, then the screen), and after Guardar the button only greyed out, so I couldn't tell it saved and had to go back and check.
   - Fix: Show a "Guardado" note and return to the previous screen. Put a clear "Cambiar mi mínimo" shortcut on Hoy and on the Semáforo tab.
2. **Yellow chip on the Uber offer.**
   - The problem: It shows $7.16 against my numbers but never says what to do, and "meta", "mínimo" and "neto" ask me to do the comparison while driving.
   - Fix: Add a plain verb to the verdict phrase, such as "Aceptable" or "Solo si no hay algo mejor". Say "lo que te deja por km" instead of "neto".
3. **Onboarding Accessibility step.**
   - The problem: There are two "Abrir ajustes" buttons, the "Así se verá" mock looks like a real dialog with greyed Permitir, and when I come back it still says "Semáforo apagado", with no "Permiso listo".
   - Fix: Label the mock "Ejemplo, no toques aquí" and show it without button styling. Keep one button. On return, show a green "Permiso listo" with a sample offer.

**(b) Compared with the Uber Driver app:** 7 out of 10. The Uber earnings reference shows only a gross weekly total and bars. Here I get the net figure and the verdict, which I like. The tabs, the "Ganancias" name and the week arrows feel familiar.

**(c) Understanding every screen without help:** 7 out of 10. The permission wording ("control total"), "desgaste", "neto", the unexplained "$34 más", and "A distintas horas" lose me.

**(d) Same tasks in my Uber Driver app:**
- Today's earnings (task 1): 6.
- Last week's earnings (task 4): 4. It would be easy to find but it is gross, so I can't get "after the car" there.
- Last trip's detail (task 9): 6.

**(e) Install?** Yes, because it tells me at a glance what a trip really leaves me per km and what I keep after costs, which Uber doesn't. The "control total" permission and the warning that Uber or DiDi might not allow it would make me think twice first.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Abrir ajustes (preview) > back","taps":5,"success":"partial","answer":"Accepted, followed the accessibility steps, imagined enabling Kompara in Android settings and returning; screen still said 'Semáforo apagado' so could not confirm it worked","ease":5,"stuck":"Two 'Abrir ajustes' buttons; mock 'Permitir' dialog looks tappable; no 'Permiso listo' confirmation on return; 'control total' and 'Uber o DiDi podrían no permitirlo' are scary"},"1":{"path":"Hoy screen, no taps","taps":0,"success":"yes","answer":"Te quedan hoy aprox. $548 net from 6 trips; 'Semáforo funcionando' so it is reading","ease":6,"stuck":"'Te quedan' wording odd; only net shown, not gross billed"},"2":{"path":"one look at the offer screen","taps":0,"success":"yes","answer":"Yellow chip, $7.16/km neto, between my goal $8 and minimum $6: so-so, take only if nothing better","ease":6,"stuck":"Chip gives no take/skip verb; had to compare 7.16 with two numbers; screen text already printed once when the previous task closed"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":2,"success":"yes","answer":"10 offers: 14:32 $9.49, 12:10 $2.20 (not taken), 11:48 sin lectura, 11:20 $9.81, 10:41 $9.41, 10:02 $10.46, 9:51 $9.16 (not taken), 9:24 $10.75, 8:40 $9.20 (not taken), 8:15 $10.00","ease":6,"stuck":"Column is net per km not what offer paid; no peso amounts per row"},"4":{"path":"Ganancias tab > left chevron to previous week","taps":2,"success":"yes","answer":"12-18 oct: $4,980 net (62 trips, after gasoline, wear and car)","ease":6,"stuck":"Opens on current week; unlabeled '<' arrow; $1,166 best-day label looks like a second total; 'resumen de Uber dice $34 más' unexplained"},"5":{"path":"Comparar tab","taps":1,"success":"yes","answer":"DiDi paid more: $10.34/km vs Uber $9.13/km last week (+$1.21)","ease":7,"stuck":"'A distintas horas en cada app' unclear"},"6":{"path":"Hoy 'tu meta $8 · tu mínimo $6' row > Tu meta y tu mínimo > tap $6.00 figure > keypad 6 . 5 0 > Listo > Guardar > back arrow to verify","taps":10,"success":"yes","answer":"Minimum set to $6.50 (Semáforo screen showed $8.00/km · $6.50/km)","ease":5,"stuck":"Setting is two screens deep; Guardar just greyed out with no confirmation, had to go back to check it saved"},"7":{"path":"swipe down from top > tap 'Se apagó el semáforo' notification > tap 'Volver a dar el permiso'","taps":3,"success":"yes","answer":"Phone removed Kompara's Accessibility permission since yesterday 20:31, so it cannot see offers; fix: tap Volver a dar el permiso and switch it on again in Accessibility","ease":6,"stuck":"'Tu teléfono lo quitó' does not say why; found it only because of the small K in the status bar"},"9":{"path":"Hoy > tap latest 'La tomaste' (14:32)","taps":1,"success":"yes","answer":"Te deja aprox. $114: $148 offer minus $34 gasoline and wear; $9.49/km net, $285/h, 24 min; car cost subtracted per day","ease":6,"stuck":"It is the offer estimate not the final payout; assumed top 'La tomaste' is the latest trip"}},"problems":[{"screen":"Tu meta y tu mínimo (reached from the Hoy status row, then the Semáforo screen)","problem":"Setting is hidden two screens deep and Guardar gives no confirmation (button just greys out), so I could not tell if the new minimum saved","fix":"Show a 'Guardado' note and return to the previous screen; add a clear 'Cambiar mi mínimo' shortcut on Hoy and the Semáforo tab"},{"screen":"Yellow chip on the Uber offer","problem":"Shows $7.16 against meta and mínimo but never says what to do; 'meta', 'mínimo' and 'neto' make me do the math while driving","fix":"Add a plain verb to the verdict phrase such as 'Aceptable' or 'Solo si no hay algo mejor'; say 'lo que te deja por km' instead of 'neto'"},{"screen":"Onboarding 'Enciende Kompara en Accesibilidad' and the settings preview","problem":"Two 'Abrir ajustes' buttons, the 'Así se verá' mock dialog looks like real buttons, and on return it still says 'Semáforo apagado' with no 'Permiso listo'; 'control total' and the Uber/DiDi warning are scary","fix":"Label the mock 'Ejemplo, no toques aquí' without button styling, keep one button, and on return show a green 'Permiso listo' with a sample offer"}],"ease_vs_uber":7,"understand_all":7,"uber_seq":{"1":6,"4":4,"9":6},"install":"Yes, because it shows me at a glance what a trip really leaves me per km and what I keep after costs, which Uber doesn't, though the 'control total' permission and the warning that Uber or DiDi might not allow it would make me think twice."}
```

### Judge 6 (haiku)

## Session report: Diego (23, drives Uber, DiDi and inDrive, compares everything)

The prototype was started, all nine tasks were run in the order 8, 1 to 7, 9, and the session was logged and stopped. Log: `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j6/log.json`. Total real taps: 20 (scrolls and the back gesture not counted).

**Task 8 (first-day setup).** Path: «Empezar» → «Acepto» → «Abrir ajustes» → «Abrir ajustes» again. The second tap showed «Esta pantalla no está en esta prueba». Per the facilitator note I imagined the Android steps (Kompara in Accesibilidad, switch on, Permitir, back) and counted that as done. I was alarmed by the «control total de tu dispositivo» mock even after reading «Kompara solo lee». The explainer says «con la accesibilidad de Android» and also «de una captura de pantalla», which contradict each other. Ease 5.

**Task 1 (today's money, is it reading).** No taps. Answer: $548 approximate net from 6 trips, and yes, the semáforo is working (green dot). «Te quedan hoy · aprox.» reads like money still to come, not money earned. Ease 6.

**Task 2 (one glance at an offer).** No taps. Answer: I would pass. The overlay shows $7.16/km neto in the yellow band «Entre tu meta y tu mínimo», below my $8 goal. The $120 offer covers 12 km (2 pickup + 10 trip), about $10/km gross. The app's $7.16 ties out only if pickup km are included, which I could not tell at a glance. Ease 5.

**Task 3 (today's offers with $/km).** Path: «Ver todas las ofertas de hoy» → scroll. Ten offers, which matches the header count. The two green offers skipped (9:51, 8:40) match the «2 ofertas verdes se fueron sin tomar» line. The list is labelled «neto por km», not what each offer paid, and the 11:48 row has no value. Ease 6.

**Task 4 (last week's net).** Path: Ganancias tab → the unlabelled «<» chevron. Answer: $4,980 approximate for 12–18 oct, 62 trips, after gasolina, desgaste and coche. Ganancias opens on the current week, where «Te quedan esta semana» shows $548 (today's figure). «Tu resumen de Uber dice $34 más» is unexplained. Ease 5.

**Task 5 (Uber vs DiDi per km).** Path: Comparar tab. Answer: DiDi $10.34/km vs Uber $9.13/km, so DiDi paid $1.21/km more. The subtitle «A distintas horas en cada app» means it is not like-for-like. The footer says only «gasolina y desgaste» is subtracted, while Ganancias also subtracts coche. Ease 6.

**Task 6 (minimum to $6.50).** Path: «Semáforo funcionando» → «Tu meta y tu mínimo» → tapped the $6.00 figure → keypad 6 . 5 0 → «Listo» → «Guardar» → back to verify. The Semáforo screen then showed $8.00/km · $6.50/km. There was no confirmation after «Guardar» (the button just greyed). The Hoy header still said «tu mínimo $6» afterwards (`/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/walk5/j6/shot-039.png`). Ease 5.

**Task 7 (semáforo stopped).** Path: swipe down → tapped the notification «Se apagó el semáforo» → «Volver a dar el permiso», which leads to the same permission explainer as task 8. Cause: Android removed Kompara's Accessibility permission (since yesterday 20:31). Fix: grant it again, ideally while parked. «Tu teléfono lo quitó» gives no reason or way to prevent it, and it does not say whether offers during the gap are lost. Ease 6.

**Task 9 (last trip).** Path: tapped the 14:32 row. Answer: $114 approximate net, which is tarifa $148 minus gasolina y desgaste −$34 (12 km × $2.84). The sheet shows $9.49/km neto, $285/h, 24 min, and the arithmetic holds. The Hoy header still shows «tu mínimo $6». Ease 6.

**(a) Three biggest problems**
1. Hoy header shows the old minimum after saving $6.50, and Guardar gives no confirmation. Trust in the saved threshold is broken.
2. Net figures use different cost bases. Comparar omits coche and compares offers at different hours, while Ganancias includes coche.
3. Earnings labels «Te quedan hoy / esta semana · aprox.» read as pending money. The previous-week arrow is unlabelled, and the offer list shows «neto por km» where the task asked for what each offer paid.

Other notes: the permission explainer's «control total» wording alarms and «Abrir ajustes» dead-ends in the prototype. A driver would not do the settings step while driving.

**(b)** 6/10 against Uber Driver. Kompara's Hoy headline is clear, but it needs the semáforo, meta and mínimo vocabulary and the overlay decoded. Uber's home is one obvious GO.

**(c)** 6/10 for understanding every screen without help. The pending-money wording, the net labels and the cost-basis differences needed interpretation.

**(d)** Uber Driver, from the two reference screenshots and general knowledge: today's earnings 6 (headline total, but the screen is weekly); last week's 5 (I could not see a period switch in the screenshot); last trip 4 (I could not see the trip list in the screenshot).

**(e)** I would install it to check it against my own numbers, but only after the stale minimum and the cost-basis inconsistencies are fixed.

```json
{"tasks":{"8":{"path":"Empezar > Acepto > Abrir ajustes > Abrir ajustes (dead end: Esta pantalla no está en esta prueba) > imagined Android steps: Kompara on in Accesibilidad, Permitir, back","taps":4,"success":"yes","answer":"Semaforo activado after the imagined Android permission step (Kompara on in Accesibilidad, Permitir, return)","ease":5,"stuck":"The control total mock alarmed me even after the solo lee line; the explainer says accesibilidad de Android but also de una captura de pantalla; second Abrir ajustes led to a dead end"},"1":{"path":"No taps; arrived on Hoy","taps":0,"success":"yes","answer":"About $548 net today from 6 trips; yes, the semaforo is reading offers (green dot)","ease":6,"stuck":"Te quedan hoy aprox reads like money still to come, not money earned"},"2":{"path":"One glance at the offer overlay, no taps","taps":0,"success":"yes","answer":"No, I would pass: $7.16/km net sits in the yellow band between my $8 goal and $6 minimum","ease":5,"stuck":"Had to decode the yellow diamond band; $7.16 is per km net, not what the $120 pays; $120 over 12 km is about $10 gross, so I could not tell whether pickup km are included"},"3":{"path":"Ver todas las ofertas de hoy > scroll down","taps":1,"success":"yes","answer":"10 offers: 14:32 $9.49 taken; 12:10 $2.20 skipped; 11:48 no reading (faltó la tarifa); 11:20 $9.81 taken; 10:41 $9.41 taken; 10:02 $10.46 taken; 9:51 $9.16 skipped; 9:24 $10.75 taken; 8:40 $9.20 skipped; 8:15 $10.00 taken","ease":6,"stuck":"Header says neto por km, but the task asked what each offer paid; 10 offers need a scroll; the 11:48 row has no per-km value"},"4":{"path":"Ganancias tab > unlabelled < chevron to previous week","taps":2,"success":"yes","answer":"$4,980 approximate kept for 12-18 oct, 62 trips, after gasolina, desgaste and coche","ease":5,"stuck":"Ganancias opens on the current week showing $548 as Te quedan esta semana; the previous-week arrow has no label; Tu resumen de Uber dice $34 mas is unexplained"},"5":{"path":"Comparar tab","taps":1,"success":"yes","answer":"DiDi $10.34/km vs Uber $9.13/km last week; DiDi paid $1.21/km more","ease":6,"stuck":"Subtitle A distintas horas en cada app means it is not like-for-like; footer subtracts only gasolina y desgaste, while Ganancias also subtracts coche"},"6":{"path":"Semaforo funcionando row > Tu meta y tu minimo > tap the $6.00 figure > keypad 6 . 5 0 > Listo > Guardar > back to verify","taps":9,"success":"yes","answer":"Minimum saved: Semaforo shows $8.00/km and $6.50/km","ease":5,"stuck":"No confirmation after Guardar, the button just greys; had to go back to verify; Hoy header still says tu minimo $6 after the save; the tiny grey hint was the only clue that the figure is typable"},"7":{"path":"Swipe down from top > tap notification Se apagó el semáforo > Volver a dar el permiso (same explainer as task 8)","taps":2,"success":"yes","answer":"Cause: Android removed Kompara's Accessibility permission (since yesterday 20:31). Fix: grant Accesibilidad again (Abrir ajustes > Kompara on > Permitir > back), best done parked","ease":6,"stuck":"Tu telefono lo quitó gives no reason and no way to prevent it; does not say whether offers in the gap are lost; the fix needs phone settings, which I would only do when parked"},"9":{"path":"Hoy > tap the 14:32 row (Oferta de las 14:32 La tomaste)","taps":1,"success":"yes","answer":"About $114 net: tarifa $148 minus gasolina y desgaste -$34 (12 km x $2.84); $9.49/km neto; $285/h; 24 min","ease":6,"stuck":"None major; the arithmetic holds; Hoy header still shows tu minimo $6, not $6.50"}},"problems":[{"screen":"Semaforo > Tu meta y tu minimo (after Guardar), and the Hoy header","problem":"Saved minimum $6.50 shows on the Semaforo screen but Hoy still says tu minimo $6, and Guardar gives no confirmation, so I could not tell whether the change stuck","fix":"Use one source for the thresholds, refresh Hoy after saving, and show a Guardado: minimo $6.50/km confirmation"},{"screen":"Comparar (Tus apps), Ganancias and the offer sheet","problem":"Net figures use different cost bases: Comparar says Ya restamos gasolina y desgaste (no coche) while Ganancias includes coche, and the per-km comparison is at different hours, so the $1.21 DiDi gap is not like-for-like","fix":"Use one net definition on every net figure with the cost list shown inline, and show a caveat or a same-hours comparison when the apps' hours differ"},{"screen":"Hoy and Ganancias headline money; the offer list","problem":"Te quedan hoy / esta semana aprox reads as pending money; the previous-week arrow is unlabelled; neto por km in the offer list is not what the task asked for","fix":"Use Ganaste hoy (neto, aprox), label the arrows Semana anterior / Semana siguiente, and show gross and net per km side by side in the offer list"}],"ease_vs_uber":6,"understand_all":6,"uber_seq":{"1":6,"4":5,"9":4},"install":"Yes, I would install it to check it against my own numbers, but only after the stale minimum and the cost-basis inconsistencies are fixed."}
```
