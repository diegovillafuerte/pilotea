# Round 20 — confirm the pre-test pass (is the kit + prototype ready for real drivers?)

Round 19b: both PASS both rubrics (Codex 71/80 + 34/40, `281`; Opus 75/80 + 34/40, `282`); both NOT_YET on "comparable in use", and both named the same next instrument — a real-driver test on the clickable prototype — after the prototype's seams are removed. This round checks that pass. Nothing in the chip, the money definition or the information architecture changed.

**What the pre-test pass did (three batches, each re-checked by an independent read-only reviewer that drove the prototype with real clicks).**
- **One moment per task.** Tu día and Cómo se calculó at 14:40 (D1a, D3a: $979 · 76.3 km −$217 · −$214 · $548; six trips in 4 h 50 min on shift; the list runs on under the nav to the cut 10:02 row). Each offer row opens its own detail: D2 (14:32 verde), D2b (12:10 rojo, "Te habría dejado"), D2c (14:50 amarillo). Sheets sit over the screen that opened them and take its clock; inside a task every screen takes the task's clock.
- **Doors land on their state.** Every Hoy status row opens Semáforo in its state: B1/B8 → G4, B3 → G4e (preparado, first run), B4 → G4f (preparado, keeps "última lectura ayer 20:10"), B5 → G4d, B6 → G4g (solo Uber, DiDi "Por ahora no la leemos"), B7 → G4b, B9 → G4c; G4–G4c read "Lectura comprobada · 14:32" at 14:40. G4c's "Ofertas sin semáforo" and B9's list open D1b (the 12:10 row "sin semáforo en pantalla"), whose 12:10 opens D2d.
- **The outage story is technically true.** C6 (drawn by the accessibility overlay) can only be "capture stopped while the service lives": "Semáforo apagado / Dejamos de leer tus ofertas / Abre Kompara al estacionarte", its notice's K after the clock. The permission lost draws nothing: C13 (Uber with no semáforo, Kompara's notice waiting), H6b (the shade at 8:01, the notice "ayer 20:31"), B2 ("desde ayer 20:31", "Volver a dar el permiso" → A3). Task 7 starts at C13.
- **Live limits.** Steppers move $0.10; tapping a figure opens G2d (number entry, opens selected, error only after Listo, equal refused, both actions in ink); Guardar keeps the pair (Hoy, Semáforo, Ajustes show it; Hoy's door switches to two decimals when a value has cents); "Volver a lo inicial" resets only the draft; every task start resets $8.00/$6.00. The preview stage reads "Ejemplo · $148 · 12 km" (G2b is reached from first-run screens too).
- **Copy.** E1d's first row drops "según el lector" (as D3, D3a, D3b); B3 "permiso listo"; G4c one line of action; C6 a 240 × 72 three-line tab.
- **Test plan** (`../driver-test-plan.md`): Uber baseline on three matched tasks ("comparable" = within 0.5 SEQ), task 8 on a real Android Settings hand-off, first-tap logging on tasks 6–7, a pre-registered rule for 10 drivers, "¿La tomarías?" after yellow glance trials, the shade opened by a tap in the prototype (not prompted), task 7 ending at A3.

Kit: 104 frames (`design-system/ui_kits/android-app-v2/index.html`), `check-fixture.py` OK, `make-prototype.py --check` 0 unmatched.

## Deliver (short markdown)
1. Are your Round 19b items resolved? Codex: the four prototype routes in `281` (increments/entry/persistence; preparado destination and history; G4c → history; one snapshot and per-offer detail). Opus: R1–R3 and P1–P4 in `282`. Evidence per item — for the prototype, drive it (headless Chrome is available at "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"; earlier Node drivers are listed below) or read `make-prototype.py`'s link map.
2. New defects or regressions (frame or route, evidence, fix). Say which, if any, would contaminate the driver test.
3. Final scores, both rubrics (criteria 1–12, totals), hard-fail line.
4. Verdict: (a) the rubrics; (b) **is the kit + prototype ready to run the real-driver test as written — yes, or the blockers**; (c) one paragraph on "comparable in use and feel to Uber Driver".

Inputs: frames `{PNG}/<id>.png` (2×, 2px = 1dp); sheets `{SHEETS}/{A…H,N}.png`; kit `design-system/ui_kits/android-app-v2/` (`prototype.html`, `make-prototype.py`, `check-fixture.py`); spec `docs/ux-elevation-spec.md` (§9p is this pass); record `180`; `../driver-test-plan.md`; your Round 19b report.
