# Driver test kit — run the real-driver test in one week

Everything needed to run `driver-test-plan.md` with real drivers: setup, recruiting, consent, the facilitator script, one run sheet per driver, and the results file the tally reads. The plan is the authority; this kit is how to execute it.

## 1. Setup (once, the day before)

- [ ] **Laptop:** from the repo root, `python3 -m http.server 8765`. Fonts load from Google Fonts, so keep the internet on.
- [ ] **Test phone (412 dp wide, Android 13+):** on the same Wi-Fi, open `http://<laptop LAN IP>:8765/design-system/ui_kits/android-app-v2/prototype.html`. It opens in participant mode on «Tarea 8». Write the scale shown on the run sheet.
- [ ] **Facilitator window (laptop only):** add `?facilitador=1`. It mirrors a participant window only in the same browser on the same computer; with a phone participant, use the phone's slim bar and export the log from the phone at the end.
- [ ] **Hand-off phone (task 8):** a second Android phone with the Kompara build from `android/`, installed with `adb install` or Play internal testing (an APK opened from Files makes the Accessibility switch a restricted setting). Rehearse task 8 end to end on this exact phone. Reset the permission after every driver.
- [ ] **Printouts:** one run sheet (section 4) per driver; the consent text (section 3).
- [ ] **Ishihara screen** (any standard 6-plate set) for the colour-vision quota.

## 2. Recruiting (10 drivers; 15 if the decision rule asks for five more)

Quotas: all drive Uber; 5 rent their car (pay «la cuenta»), 5 own it; ≥ 3 also drive DiDi; ≥ 2 with red-green colour-vision deficiency (screened at the session). Pay the time (the market rate for 45 minutes).

WhatsApp message, ready to send:

> Hola, estamos probando una app para conductores de Uber y DiDi que te dice si una oferta te conviene y cuánto te queda de verdad después de gasolina, desgaste y coche. Buscamos conductores en CDMX para probarla 45 minutos (en persona, te pagamos $___ por tu tiempo). No necesitas instalar nada: usas un teléfono nuestro. ¿Te interesa? ¿Rentas tu coche o es tuyo?

## 3. Consent (read aloud before starting; tick on the run sheet)

> Gracias por venir. Vamos a probar una app, no a ti: si algo no se entiende, es culpa de la app. Te voy a leer unas tareas; hazlas como lo harías en tu día, y di en voz alta lo que piensas. No te puedo ayudar durante la tarea. También te voy a pedir que hagas tres cosas en tu propia app de Uber: veré tus pantallas, pero no voy a anotar ninguna cantidad. Grabamos solo lo que tocas en nuestro teléfono, sin tu nombre. Puedes parar cuando quieras. ¿Estás de acuerdo?

## 4. Run sheet (one per driver)

**Driver code** ____ · rents / owns · Uber / Uber + DiDi · CVD yes / no · phone scale ____ · Uber tasks first / after (alternate drivers)

Order: **8**, then read «Ahora imagina que ya llevas semanas usando Kompara. En esta prueba manejas Uber y DiDi, más Uber, y ya le pusiste lo que pagas del coche: $1,500 a la semana.», then **1–7**, then **9**. Read each prompt exactly as in the plan's table. After each task: «Del 1 al 7, ¿qué tan fácil fue? (1 = muy difícil, 7 = muy fácil)».

| Task | Success (yes / partial / no) | Time | SEQ 1–7 | First tap / gesture (tasks 6, 7) | Notes (where they hesitated, what they said) |
|---|---|---|---|---|---|
| 8 First day (real hand-off) | | | | | |
| 1 Hoy | | | | | |
| 2 Offer at a glance (+ «¿la tomarías?», reason) | | | | | |
| 3 Every offer today («sin el coche» mentioned unprompted? y/n) | | | | | |
| 4 Last week after the car | | | | | |
| 5 Which app paid more | | | | | |
| 6 Change tu mínimo | | | | | |
| 7 Semáforo not showing (route: shade / host bar / gear) | | | | | |
| 9 Last finished trip | | | | | |
| **Uber:** «¿Cuánto llevas hoy?» | | | | | |
| **Uber:** «¿Cuánto ganaste la semana pasada, ya con todo?» | | | | | |
| **Uber:** «Busca cuánto te dejó tu último viaje terminado.» | | | | | |

**Probes** (fresh window each; write the answer verbatim):
- `?screen=B1` «¿Qué quiere decir este número?» → kept so far today after costs, car included? ____
- `?screen=B8` «¿Qué pasó hoy?» → reads −$100 as an error or a debt? ____
- `?screen=D2e` «¿Cuánto te dejó este viaje?» ____ · «Esos $133, ¿ya tienen descontado lo que pagas del coche?» ____ · «¿Y en todo el día, cuánto llevas ya descontando el coche?» ____
- Record only, not gated (plan §1, Opus `322` item 5): the name the driver uses for the car's cost, «lo del coche» or «el coche» ____ · takes the two for different costs? ____

**End:** «Comparado con la app de Uber, ¿qué tan fácil fue? (1–10)» ____ · «¿Entendiste cada pantalla sin ayuda? (1–10)» ____ · «¿Qué fue lo más difícil?» ____ · Any money figure that seemed to contradict another? ____

## 5. Results file (so the result is tallied the same way as the model runs)

After each driver, export the prototype log (bar → Exportar) to `docs/ux-elevation/blind-test/drivers/dNN-log.json` and add one line to `docs/ux-elevation/blind-test/drivers/results.jsonl`:

```json
{"driver":"d01","rents":true,"apps":["uber","didi"],"cvd":false,"scale":1.02,"uber_first":true,
 "tasks":{"8":{"success":"yes","ease":6},"1":{"success":"yes","ease":7},"2":{"success":"yes","ease":6},"3":{"success":"yes","ease":6},"4":{"success":"yes","ease":6},"5":{"success":"yes","ease":6},"6":{"success":"yes","ease":6},"7":{"success":"yes","ease":6},"9":{"success":"yes","ease":6}},
 "uber_seq":{"1":6,"4":5,"9":5},"ease_vs_uber":8,"understand_all":8,
 "probes":{"b1_after_car":true,"b8_not_error":true,"d2e_q1":true,"d2e_q2":true,"d2e_q3":true},
 "hardest":"...","money_inconsistency":{"any":false,"which":""}}
```

Then apply the plan's decision rule (all five gates, over the final cohort). Rule 2 uses the drivers' measured Uber SEQ, not estimates.

## 6. The glance test (separate session, plan §2)

Mounted phone at 60 cm, road video on a second screen, 1 s flashes of C1–C5, C7, C12 (day and night, N8–N10), against the best generic chip as the control. Pass: ≥ 95 % correct, median ≤ 1.0 s, no ◆ read as a loss, compact within 5 points of the full plate.
