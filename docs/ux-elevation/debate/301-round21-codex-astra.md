Prioritize saving and permission completion. Those are [run 5’s two 5.00-SEQ tasks](/Users/panama/pilotea/docs/ux-elevation/blind-test/walkthrough/results-r20c-run5.md); both leave drivers unsure whether their action worked.

**1. Task 6 — make editing direct and saving conclusive.**

- **One tap from Hoy:** give the limits their own row, **“Cambiar tu meta y tu mínimo”**, with the saved amounts beneath. It opens G2b directly. The separate **“Semáforo funcionando”** row continues to open status.
- **Visible inputs:** outlined amount fields labelled **“Tu meta”** and **“Tu mínimo”**, with fixed `$` and `/km`. Tapping selects the amount and opens the decimal keyboard directly; eliminate G2d’s separate confirmation dialog.
- **Keep both methods:** −/+ move **$0.50**; typing accepts individual cents. Helper: **“Escribe la cantidad o ajusta de $0.50 en $0.50.”** Controls have ≥48dp targets.
- **Guardar:** commit once, close the editor, return to its caller—Hoy for the direct route, Semáforo when entered there—and show **“Guardado: tu mínimo ahora es $6.50/km.”** The destination immediately displays the saved values. Keep Guardar accessible above the keyboard.
- Invalid minimum: **“Tu mínimo debe ser menor que tu meta.”**

This addresses the 9.7-tap average, hidden typing and verification trips after saving. The task-reset bug is already fixed; it supplies no reason for another design change.

**2. Task 8 — merge A3/A3b and make the return explicit.**

Flow: **A1 → A2 → merged A3 → Android settings → B3**, retaining the existing conditional battery step only when needed.

- **A2:** retain Lee / Guarda / Manda. Clarify the capture relationship: **“Con el permiso de Accesibilidad de Android, lee tarifa, distancia y tiempo de una captura de cada oferta y borra la imagen.”** Keep the required risk as a separate, body-size sentence before **“Acepto”**: **“Uber o DiDi podrían no permitir el uso de Kompara.”** It remains visible before consent.
- **Merged A3 title:** **“Activa Kompara en Accesibilidad.”**
- **Steps:** **“1. Busca Kompara en Accesibilidad. 2. Activa el interruptor. 3. Toca Permitir y vuelve a Kompara.”**
- **Reassurance beside those steps:** **“Android puede pedir «control total». Kompara usa este permiso para leer ofertas; no toca botones ni acepta viajes por ti.”** Describe Kompara’s behavior without suggesting the permission itself is narrowly scoped.
- **Example:** replace both realistic mock interfaces with one small, flat, numbered illustration labelled **“Ejemplo de Android · aquí no se toca.”** Remove switch and button styling; quote “Permitir” in the instruction. Grey controls and dashed borders already failed.
- **One primary:** **“Abrir ajustes”** opens Android directly. Secondary: **“Ahora no.”**
- **Verified return:** land on B3 with prominent **“Permiso listo”**, status **“Semáforo preparado”**, and **“Abrir Uber para probar”** for the applicable app. Show **“Semáforo funcionando”** only after a successful reading. If permission remains absent, stay on A3 with **“Todavía falta activar Kompara”** and **“Abrir ajustes.”**

The duplicated action, misleading examples and absent return confirmation recur across the reports. Account-safety assurances would be unsupported.

**3. Task 2 — retain the position phrase; improve the limits’ legibility.**

Keep **“◆ $7.16/km neto”**, **“Entre tu meta y tu mínimo”**, and the two labelled limits. Make the limit labels **16sp, semibold, near-white on ink**, preserving system text scaling.

Jorge and Ana want a verdict word; Martha understands the diamond and position. The rendered phrase is already black on yellow—the grey limit labels below it need attention. I recommend retaining position pending [R14’s founder decision](/Users/panama/pilotea/docs/ux-elevation-spec.md:1122). Run 5 does not establish that “Aceptable” improves the mounted glance.

**4. Smaller changes — make the meaning visible where the number appears.**

| Surface | Exact decision and copy | Run-5 evidence |
|---|---|---|
| Colour references | D1a: **“2 ofertas arriba de tu meta se fueron sin tomar.”** Editor: **“Tu meta · ▲ desde”** and **“Tu mínimo · ▼ abajo de.”** Carry ▲/◆/▼ into the corresponding positions of existing list marks; unread states retain dashed marks without those cues. | Martha cannot use colour words. |
| Open versus closed periods | **“Llevas hoy · aprox.”**, **“Llevas esta semana · aprox.”**, **“Llevas este mes · aprox.”** Closed periods retain **“Te quedaron…”** Keep the explicit deduction line. | “Te quedan” repeatedly reads as money still to earn. |
| Uber’s $34 difference | Row: **“Tu resumen de Uber reporta $34 más que lo leído por Kompara.”** Existing detail: **“Pago de Uber antes de gastos”**, **“Kompara: $5,028”**, **“Tu resumen: $5,062”**, **“El total de la semana todavía usa lo leído por Kompara.”** | Drivers cannot identify the comparison. Both amounts are payments before expenses; expenses do not explain this difference. |
| Comparar | Place the basis beside the comparison: **“Después de gasolina y desgaste; antes de lo del coche.”** Replace the hours qualifier with **“Trabajaste en horarios distintos en cada app.”** | Diego questions the cost basis and comparability; several judges cannot interpret the hours line. |
| Trip detail | Keep the rendered **“Te dejó · aprox.”** Immediately below $114: **“Después de gasolina y desgaste; antes de lo del coche.”** Retain **“Lo del coche se resta por día, no por viaje.”** in the breakdown. | Rosa’s cuenta is excluded from $114 but included in the day. The exclusion currently appears too late. |
| Previous week | Visible **“‹ Semana anterior”** / **“Semana siguiente ›”**, each ≥48dp, with dates on their own line. Keep the current-week default. | Small, unlabelled arrows caused hesitation. Monday’s $548 matching today is valid. |

**5. What could move ease versus Uber to ≥7.5?**

The strongest candidate is the combined reduction in effort and uncertainty: **one-tap limits access → one $0.50 adjustment → Guardar → visible saved result**, plus **one settings handoff → verified return**, followed by the money-copy corrections above.

If tasks 6 and 8 each reached 6.0, the overall SEQ would rise approximately **5.83 → 6.06**, holding everything else constant. That is an arithmetic target, not a predicted rating. The ≥7.5 comparison remains unproven: these were model personas, Android activation was imagined, and Uber scores were self-estimated.