# Round 21: Opus 5.5 proposal

This is grounded in run 5: all six full reports, the log tally, and the 2× renders. Every count below is out of 6 judges.

## 1. Task 6: the limits editor (SEQ 5.00, 9.7 taps)

**Evidence.**
- Taps went into entering the value: + five times, or figure → 4 keys → Listo.
- Every judge then spent one more tap going back to check the save. No message after Guardar: 6/6.
- $0.10 steps: 5/6. Typing found only through the hint line: j2, j4, j6.
- The door caused 0 misses. Only j5 called it "two screens deep".

| Element | Decision and exact copy |
|---|---|
| Door | **Unchanged:** Hoy status row → Semáforo → "Tu meta y tu mínimo". Run 4 reverted the one-tap door, run 5 had 0 misses on this path, and the cost comes after arrival. |
| Steppers | **$0.50 per km; $10 per hour.** A value off the grid snaps to the next mark in the direction pressed ($6.30 → + → $6.50; − → $6.00). The caption under the rings changes from "de $0.10 en $0.10" to **"de $0.50 en $0.50"** (per hour: **"de $10 en $10"**). |
| Input | The dotted underline goes. The figure sits in **G5's filled field** (paper-2 fill, 8dp radius, the same idiom as "Cada semana $1,500"). Keep the helper **"Toca la cifra para escribirla."** G2d's dialog is unchanged and accepts "6.5". |
| Labels | "Tu meta · verde desde" → **"Tu meta · ▲ desde"**. "Tu mínimo · rojo abajo de" → **"Tu mínimo · ▼ abajo de"**. The cue is 13dp in the band colour, as in A1's legend. |
| Guardar | **Saves, closes, and returns to the screen that opened the editor** (Semáforo from Hoy, Ajustes, D2, B3). A 4 s snackbar shows one of:<br>• **"Guardado: tu mínimo ahora es $6.50/km"** (or **"Guardado: tu meta ahora es $8.50/km"**)<br>• both changed: **"Guardado: tu meta $8.50/km · tu mínimo $6.50/km"**<br>• after Volver a lo inicial: **"Guardado: volviste a $8.00/km y $6.00/km"**<br>The opener's line updates at once: G4 shows "$8.00/km · $6.50/km"; Hoy shows "tu meta $8.00 · tu mínimo $6.50". |
| Back with an unsaved change | Dialog **"¿Guardar el cambio?"**, body **"Tu mínimo: $6.50/km"**, buttons **"Guardar" · "No guardar"**. |
| Prototype | `@lim` steps 50; `@save` = keep, then `@back`, then the toast. |

**Moves:** task 6 from 5.00 to about 6.2. Taps drop from 9.7 to **4** with the stepper (row → limits → + → Guardar) or 8 by typing, and the check-back tap disappears.

## 2. Task 8: the first-day permission (SEQ 5.00)

**Evidence.**
- Two "Abrir ajustes" in a row: 6/6.
- Hit the "no está en esta prueba" dead end: 6/6.
- The drawn switch or Permitir looked tappable: j1 tapped it; j2, j4 and j5 said so.
- «control total» and the Uber/DiDi line felt scary: 6/6, and they appear in 4 of the 6 install answers.
- No "it worked" on return: j4, j5.

**Screens: A1 → A2 → A3 → Android → back on A3 (800 ms tick) → B3. A3b is deleted; nothing opens it.**

**A2 (the disclosure).** The risk line moves out of "Lee" into its own fourth line, with context. It stays on the consent screen, at the same strength:
- **Lee** "cada oferta con el permiso de accesibilidad de Android: toma tarifa, distancia y tiempo de una captura de pantalla y la borra."
- **Guarda** and **Manda** are unchanged.
- **Uber y DiDi** "no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app."
- This takes the screen to about 70 words. That breaks §7's ≤ 60 budget, and I flag it as a deliberate deviation. The same line also goes into Semáforo › "Qué lee Kompara".

**A3, merged into one screen:**
- Title: **"Enciende Kompara en Accesibilidad"**
- Under it: **"Es el permiso que deja a Kompara leer tus ofertas."**
- Steps, plain text with no chevrons:
  1. **"Busca Kompara en la lista y tócalo"**, sub-line **"Puede estar en «Apps instaladas» o «Apps descargadas»"**
  2. **"Activa el interruptor"**
  3. **"Toca Permitir y regresa aquí"**
- Label: **"Android te va a preguntar esto:"**
- **The picture:**
  - Drawn as a scaled miniature on a paper-2 panel. No dashed border, because a dashed box reads as a placeholder or target.
  - An **"EJEMPLO"** tag sits inside it, top-left.
  - Android's words appear once, in the OS face: "¿Permitir que Kompara tenga control total de tu dispositivo?"
  - "Rechazar   Permitir" are plain ink-3 text, not buttons.
  - A tap on the picture gives the toast **"Es solo un ejemplo. Toca «Abrir ajustes»."**
- Right under the picture, next to Permitir, at 16/22 ink: **"Android lo pregunta igual a toda app de accesibilidad. Kompara solo lee: nunca toca nada ni acepta viajes por ti."** Our own copy no longer repeats "control total".
- Status row: "Semáforo apagado". Buttons: **"Abrir ajustes"** · "Ahora no".

**Return:**
- **Granted:** the row becomes ✓ **"Permiso listo"** for 800 ms, then B3. B3's headline changes from "Abre tu app de viajes" to **"Listo. Abre tu app de viajes"**. It does not say "encendido": the first offer still has to confirm capture.
- **Not granted:** the row reads **"Semáforo apagado · aún falta el permiso"**.

**Prototype:** A3's "Abrir ajustes" opens a stand-in screen: **"Aquí se abren los Ajustes de Android"** / **"En tu teléfono harías los tres pasos."**, with buttons **"Ya lo activé"** (to the tick, then B3) and **"Regresar sin activarlo"** (back to A3).

**Moves:** task 8 from 5.00 to about 5.9. Taps go to 4 and misses to 0.

## 3. Task 2: the yellow state (SEQ 5.67)

**Evidence.**
- 6/6 judges reached the right reading on their own ("take it if nothing better").
- What they asked for was relief from doing the comparison: j2, j4, j5. j4 also named the small labels.

**Recommendation:**
- **Keep position and add no verdict word.** "Aceptable" and "Regular" break §6's "never a verdict" rule and the blind-test template finding.
- **Change only the amarillo phrase**, from "Entre tu meta y tu mínimo" to **"Pasa tu mínimo, no tu meta"**. It states the two comparisons the judges worked out themselves, against their own limits.
- The first words of the three states stay distinct: Arriba / Pasa / Abajo.
- It applies everywhere the phrase appears: the chip, A1's legend, D1 rows, D2c.

**Condition:** at 26 characters against 25, it must fit the 240dp plate at 17sp and the 1.3× plate at 22sp. If it overflows, keep "Entre…" and leave the question to R14.

R14 still runs v16 against the verdict-word chip with "¿La tomarías?". The founder's decision stands. The limit labels are already about 9:1 contrast and scale 1.3× (C10), so they don't change.

**Moves:** task 2 from 5.67 to about 5.9–6.0.

## 4. The smaller ones

| Where | Now | New | Evidence | Moves |
|---|---|---|---|---|
| Open-period heroes: B1, D1a, D3a, E8, E3 | "Te quedan hoy / esta semana / en octubre · aprox." | **"Llevas hoy · aprox."**, **"Llevas esta semana · aprox."**, **"Llevas en octubre · aprox."** Closed periods keep "Te quedaron…". | Read as money still to come: j2–j6 | T1, T4 |
| Tu día summary row | "2 ofertas verdes se fueron sin tomar" | **"No tomaste 2 ofertas arriba de tu meta"** (38 characters, fits one line) | j3 (colour vision) | T3 |
| G2 labels | colour words | see §1 | j3 | T6 |
| E1 row | "Tu resumen de Uber dice $34 más" | **"Uber reporta $34 más de lo que leímos"**, sub-line **"Leímos $5,028 · tu resumen dice $5,062"**. Not "porque no resta tus gastos": the gap is a reading gap. | "$34 more than what?": j1, j2, j4, j5, j6 | T4 |
| E1, E8, E3, F1 period row | small "‹ ›" | The period on its own line, then a 48dp row **"‹ Semana anterior"** · **"Semana siguiente ›"** (Mes: **"‹ Mes anterior"** · **"Mes siguiente ›"**); "siguiente" is disabled on the open period | j1, j2, j4, j5, j6 | T4 |
| E1 bar label | bold "$1,166" | **"vie $1,166"** at 13sp ink-2 | Read as a total: j2, j4, j5 | T4 |
| F1 basis | "Ya restamos gasolina y desgaste" | **"Ya restamos gasolina y desgaste. Lo del coche no entra: cuesta igual en las dos apps."** | j6 | T5 |
| F1 caveat | "A distintas horas en cada app ›" | **"Manejaste cada app a distintas horas ›"** | 5 of 6 | T5 |
| D2 label | "Te dejó · aprox." / "Te deja · aprox." | **"Te dejó antes de lo del coche · aprox."** / **"Te deja antes de lo del coche · aprox."** The note becomes **"Lo del coche ($214 al día) se resta de tu día, no de cada viaje."** | j1, j2, j4 | T9 |
| Unread offer row | "Faltó la tarifa" | **"No pudimos leer la tarifa"** | j1, j4 | T3 |

Spec §11 R17 then tests "Llevas hoy $548" in place of "Te quedan".

## 5. Moving "ease vs Uber" from 7.0 to 7.5 or more

The two judges who gave 6 named reading load (j4) and trust breaks (j6). The 7s and 8s named fear and Guardar. Nothing below adds a feature:

1. **One cost basis, said everywhere.** The F1 and D2 car clauses, plus the $34 line with both figures. This covers two of j6's three problems; his stale-minimum bug is already fixed.
2. **Fear down on the first day without hiding the risk.** This means the A2 and A3 changes above; fear shows up in 4 of the 6 install answers.
3. **Every save confirms and lands somewhere** (§1).
4. **Task 7's "why, and how do I stop it", asked by 6/6 judges.**
   - B2's sub-line becomes **"Android a veces lo apaga, sobre todo al ahorrar batería. Sin él, Kompara no ve tus ofertas."**
   - Add a text button **"Que no vuelva a pasar"** that opens A4. Show it only when a restriction is detected.
   - Engineering has to confirm the battery cause before this ships.
5. **Fewer words to decode:** "Llevas", the amarillo phrase, the F1 caveat, and "No pudimos leer".

**Projection:**
- Ease vs Uber goes to about 7.3–7.6. I can't promise 7.5: over five runs the model judges' score has stayed between 6.5 and 7.0.
- Expected SEQ: T8 about 5.9, T6 about 6.2, T2 about 5.95, T4 about 6.2, T1, T7 and T9 about 6.4, T3 about 6.2, T5 about 6.6. **Mean about 6.2, no task under 5.5.** Treat each figure as ±0.2.

**Spec sections to update:** §4 (money words), §5.6, §5.9, §5.14, §6 (amarillo phrase), §7 rows 2, 3 and 3b, §11 R17.
