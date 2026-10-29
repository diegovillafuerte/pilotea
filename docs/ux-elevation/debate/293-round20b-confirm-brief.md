# Round 20b — confirm the Round 20 blockers are gone (go / no-go for the real-driver test)

Round 20: both PASS both rubrics (Codex 71/80 + 34/40, `291`; Opus 76/80 + 34/40, `292`), both **not ready as written**, with the same three blockers — (1) the task answers and frame ids visible to the participant, (2) Ganancias → Mes drawn at 21:10 ("hoy · 9 viajes $1,026", $12,169) under the prototype's 14:40 clock, (3) the plan's disagreeing decision rules and no Kompara task paired with Uber's trip detail — plus Opus's N4–N10 and Codex's E8 → G5 return and glance wording.

**Applied (then re-checked by an independent read-only reviewer that crawled every hotspot reachable inside each task):**
- **Participant mode by default:** no answer text, frame ids or first-tap readout in the DOM; a slim bar (task number, Iniciar, Hecho, Atascado, Exportar; N/H/A keys); `?facilitador=1` opens the full panel, mirrored over BroadcastChannel on the same computer.
- **The month at 14:40:** E3, E3d, D3b, E4, E5 (and N5): October $11,692, 155 viajes; rows $1,417 · $4,747 · $4,980 · hoy 6 viajes $548 (they foot, so no rounding note); ledger $20,429 − $4,666 − $4,071; IMSS Uber $12,252 / DiDi $8,177, × 0.94 → $11,517 / $7,686, faltan $1,896. Every money screen a task reaches reads one moment.
- **The plan:** one pre-registered decision rule (overall SEQ; Uber comparison on three matched tasks — task 1 ↔ today, task 4 ↔ last week, new task 9 "Desde Hoy, busca cuánto te dejó tu último viaje" ↔ trip detail — within 0.5; per-task flags failing only with a top-named problem, defined; comprehension; money consistency), counterbalanced Uber order, task 8 split between the prototype and a real Android hand-off, glance pass criteria separating C7's real loss from the ◆ yellow.
- **N4–N10 and the rest:** C13's shade opens by a drag down from the status bar or a tap, and "Abrir Kompara" is as quiet as "← Atrás"; the 20-oct world (B2 → ⚙ → G1b → G4h "Semáforo apagado" → A3; the 19-oct screens give a neutral note; past A3 the task ends); a first-day world for task 8 (Hoy → B3, Semáforo → G4e); D1a/D1b list all ten offers so far and scroll to 08:15; D1 "Ofertas de hoy · 13"; detail sheets keep the limits that judged the offer and add "a esa hora" after a change; silent chevrons give the neutral note; D3a sits over its opener (over Hoy it opens under "Últimas ofertas"); sheets keep the opener's scroll; E8 → G5 returns to E8; E3's Semana tab opens this week; no back loops in Ajustes; "lector" gone from UI copy; D2d "Sin semáforo: no cupo en la pantalla (navegación)".

Kit: 106 frames; `check-fixture.py` OK (215 figures, 72 frames); `make-prototype.py --check` 224/224 matched, 0 conflicts.

## Deliver (short markdown)
1. Each of YOUR Round 20 blockers and findings: resolved or not, with evidence (drive the prototype where it matters; headless Chrome at "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"; drivers below).
2. Anything new that would contaminate the driver test (route, evidence, fix).
3. Scores, both rubrics, only if they change; hard-fail line.
4. **Go / no-go for running the real-driver test as written**, in one line, then one paragraph on "comparable in use and feel to Uber Driver".

Inputs: frames `{PNG}/<id>.png` (2×); sheets `{SHEETS}/{A…H,N}.png`; kit and prototype `design-system/ui_kits/android-app-v2/`; plan `docs/ux-elevation/driver-test-plan.md`; spec §9p and the Round 20 section; your Round 20 report.
