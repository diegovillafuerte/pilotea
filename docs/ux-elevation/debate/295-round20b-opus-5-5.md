# Round 20b: Opus 5.5 confirmation

**What I tested.** The plan and the prototype changed at 22:56–22:57, partway through my review: Codex's `294` moved task 8 to first. Every result below is from that newer build (`prototype.html` 410,310 bytes, sha256 starting `c72a4d6b`; plan modified 22:56:49). I re-ran the participant-mode checks after the change.

**Checkers.**
- `make-prototype.py --check`, with `--out` pointed at the scratchpad: 224 specs, 224 matched, 0 unmatched, 0 conflicts. The regenerated file is byte-identical to the repo's `prototype.html`.
- `check-fixture.py`: 215 figures in 72 frames, OK. `index.html` equals `_head.html` plus the parts.

**How I drove it.** Real CDP clicks, drags and touches in headless Chrome. All scripts and outputs are in `/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r20b-opus/`. Nothing in the repo was written.

## 1. My Round 20 blockers and findings

| # | Status | Evidence |
|---|---|---|
| **N1** answers on screen | **Resolved** | **Viewports:** participant mode tested at 1400×1000, 1366×657, 412×915 and 360×680 (the two phone sizes with touch emulation).<br>**Visible on load:** "← Atrás" and "Tarea 8" only. With the handle open: "‹ 8 › Iniciar Hecho Atascado Exportar" plus the code field.<br>**What is absent:** no answer strings, no frame-id tokens, no `pt-panel`, `pt-id` or `pt-note`. "Éxito" appears only inside the script source.<br>**Every screen:** I unhid each of the 105 screens and found no ids or annotation words in their visible text.<br>**Facilitator window:** `?facilitador=1` shows all nine "Éxito" lines. It mirrors the participant screen within 300 ms (E8 → E3 → E3d). Its Iniciar and its H key drive the participant window, which logged `4: hecho, B1>E8`.<br>**Phone:** the controls live behind the "Tarea N" handle; nothing needs scrolling (page height equals viewport height at every size). |
| **N2** month at 21:10 | **Resolved** | **Crawl:** every hotspot reachable from B1 in tasks 1, 3, 4 and 9 gives 32 states. All 32 read 14:40 · 82 %. None contains $1,026, $12,169, 158 viajes or 21:10.<br>**E3:** $11,692 · 155 viajes. The weeks foot: 27 + 60 + 62 + 6 trips; $1,417 + $4,747 + $4,980 + $548.<br>**E3d:** the 19 oct row reads "hoy · 6 viajes $548".<br>**D3b:** $20,429 − $4,666 − $4,071 = $11,692. The checks hold: 1,642.0 km × 2.8417 = 4,666; $1,500 ÷ 7 × 19 = 4,071. The footer reads "hasta hoy a las 14:40".<br>**E4:** Uber $12,252 + DiDi $8,177 = $20,429. |
| **N3** decision rules | **Resolved** | **One rule:** the plan has a single pre-registered "only one" rule. The ≥ 7.5/10 gate is gone, and the 1–10 Uber rating is "reported, not gated".<br>**Matched tasks:** task 1 ↔ today, task 4 ↔ last week, new task 9 (B1 → 14:32 → D2 "Te dejó $114", one tap) ↔ trip detail.<br>**Counterbalanced:** yes.<br>**Rule 4:** defined at 15 drivers as well as 10.<br>**Residual (section 2):** the Uber prompts are not written out word for word. |
| **N4** task-7 route bias | **Resolved in the prototype** | **Drag:** a drag from C13's status bar opens H6b, by mouse on the laptop and by touch at 412×915. The first tap is logged as "arrastre hacia abajo… → H6b".<br>**Tap:** a tap on the status bar also opens H6b.<br>**Near miss:** a drag that starts 30 px below the status bar is logged as a miss.<br>**Host bar:** "Abrir Kompara" has the same computed style as "← Atrás" (bg rgb(42,43,46), color rgb(201,200,194), weight 500, 12 px). It is still the only enabled control at the start of the task, which is acceptable as the stand-in for the home-screen icon.<br>**Residual on phone runs:** section 2, item 4. |
| **N5** task-7 leaks | **Resolved** | **Ajustes route:** B2 → ⚙ → G1b reads "Semáforo **Apagado**". G4h reads "Semáforo apagado · desde ayer 20:31 / Falta el permiso de Accesibilidad", and its primary goes to A3.<br>**19-oct screens:** B2's Ganancias and Comparar, H6b's "Octubre", and E1's Mes and › all give the neutral note.<br>**Week:** H6b → E1 shows 12–18 oct, which is correct on 20 oct.<br>**Across the world:** all 20 task-7 states read 8:01. G3 reads "confirmados ayer". Past A3, the second "Abrir ajustes" gives the note. |
| **N6** D1a ends at 10:02 | **Resolved** | "Ofertas de hoy · 10". The content's scrollHeight is 968 against 664, and a wheel scroll reaches **8:15** with the nav fixed.<br>The six taken trips are 14:32, 11:20, 10:41, 10:02, 9:24 and 8:15. The two verdes that left are 9:51 and 8:40. Every row gives a note. |
| **N7** stale limit after a save | **Resolved** | Driven: B1 → 12:10 → D2b → Cambiar → G2b → mínimo + ×5 ($6.50) → Guardar → back.<br>**D2b:** "abajo de tu mínimo de $6.00/km **a esa hora**". The sheet lifts to 292 px; scrollHeight equals clientHeight (560), so nothing is clipped.<br>**Behind it:** the door reads "tu mínimo $6.50".<br>**D2:** also gains "a esa hora".<br>**G4:** reads "$8.00/km · $6.50/km". |
| **N8** silent chevrons | **Resolved for chevrons** | E8's basis line, E1 "Tu resumen de Uber dice $34 más", F1 "A distintas horas" and G4 "Qué lee Kompara" all give the neutral note. No reachable screen is in `--check`'s silent-chevron list. Buttons and arrows that still give nothing are in section 2, item 7. |
| **N9** D3a backdrop | **Resolved** | **From B1:** over Hoy, the sheet sits flush under "Últimas ofertas".<br>**From a scrolled D1a:** over D1a.<br>**E8 → G5 → Guardar:** returns to E8.<br>**Back loops:** none in Ajustes (E8 ⚙ G1 ← goes to E8; E4 ← goes to E3). |
| **N10** task-8 split | **Resolved** | The plan states which device runs which steps. The real Kompara build from `android/` is required (no stand-in app). Time runs from A1 to the return, with one SEQ and the result on the run sheet. Task 8 now runs first. |
| Kit nits | **Resolved** | D2d reads "Sin semáforo: no cupo en la pantalla (navegación)". D1 reads "Ofertas de hoy · 13". No screen text says "lector". |

## 2. New items that would contaminate the driver test

1. **On a phone, Android's back gesture or a page reload drops the driver onto a 21:10 dead end.** *Blocks phone runs.*
   - **Why it happens:** participant mode never adds browser-history entries (history.length stays 2 after any number of taps). Nothing sets `overscroll-behavior`; html, body, `.content` and `.sheet` all compute to `auto`.
   - **The back gesture leaves the prototype.** Driven in task 3 (B1 → D1a → D2): one history step back lands on `about:blank`.
   - **Coming back shows the wrong day.** Going forward again reloads the page onto D2 over its frame's own backdrop: **"$1,026 · 9 viajes tomados en 8 h 10 min" under a 14:40 clock.** A plain reload in task 9 does the same. Chrome's pull-to-refresh would reload the page the same way; I inferred that from the CSS but could not simulate it headless.
   - **The driver is then stuck.** "← Atrás" is disabled. The scrim and the grab log `@back` but do nothing, because the history is empty (`stuck.mjs`).
   - **Fix:**
     - In participant mode, add a history entry on each screen change and map the browser's back to the in-app back.
     - Set `overscroll-behavior: none` on html, body, `.content` and `.sheet`.
     - On load with an active task, rebuild the history from `store.active.screens` (or fall back to the task's start screen).
   - **Alternatively,** strike "on a phone" from the plan.

2. **Before task 8, the driver's window shows the mid-day Hoy.** On load it shows B1 at 14:40 with $548 and "Semáforo funcionando". The driver can tap around freely and nothing is logged (`idle.mjs`). The first task is "Es tu primer día".
   - **Fix:** open the driver's window with `?screen=A1` (it already works, no code change), or show a neutral cover until Iniciar.
   - **Plan line:** after task 8, add a framing sentence before task 1, such as "ya llevas semanas con la app".

3. **Comparing to Uber mixes devices.** Kompara would be used by mouse on a laptop, or on a phone as a phone-in-a-browser scaled to 0.72 (360×680) or 0.96 (412×915). Uber is used by thumb, at full size, on the driver's own phone. The bias works against Kompara, so a pass on rule 2 still counts, but a fail would be ambiguous.
   - **Fix:** once item 1 is fixed, run on a phone with no bezel at 1:1. Otherwise, record the device difference as a stated limitation of rule 2.

4. **Task 7 on a phone: the habitual swipe from the top opens the real Android shade.** The prototype only catches a drag that starts on its own drawn status bar, about 54–96 px down the page.
   - **Fix:** run task 7 on the laptop, or tell the facilitator to record a swipe into the real shade as "shade attempted".

5. **The plan's Uber tasks have no fixed wording.** Rule 2 is gated on them.
   - **Fix:** write the three Spanish prompts out word for word, for example «¿Cuánto llevas hoy?», «¿Cuánto ganaste la semana pasada?» and «Busca cuánto te dejó tu último viaje».
   - Require every recruit to drive Uber.
   - Add a consent line, because the facilitator will see the driver's real earnings.

6. **The task-8 hand-off can fail for reasons unrelated to the design.**
   - **Blocked switch:** on Android 13 and later, an APK opened from Files shows Kompara's switch as a restricted setting.
     - **Fix:** install with `adb install` or through Play internal testing.
     - **Fix:** rehearse the hand-off on the exact test phone.
   - **Fake success on the laptop:** if the hand-off is late, A3 → A3b → "Abrir ajustes" opens B3 and simulates success.
     - **Fix:** in the first-day world, make A3b's button give the neutral note.

7. **Nits, low contamination:**
   - **Buttons that do nothing:**
     - G4 "Pausar el semáforo hasta mañana" (on task 6's path) and the same button on G4e;
     - E4 "Consultar mi vigencia en el IMSS";
     - A3 "Ahora no";
     - B3 "Avisarme si se apaga";
     - A5 "Ya sé mi costo por km";
     - G7, G8 and H3 primaries.
   - **Period arrows that do nothing:** E1 ‹, E3 ‹ and D1a ‹.
     - **Fix:** wire them all to the neutral note.
   - **First-day world leaks the normal Ajustes.** G1 reads "Semáforo · Funcionando", "$1,500 a la semana" and "confirmados hoy". It is reachable only after the simulated return.
   - **The last trip has not ended at 14:40.** D2 gives the 14:32 trip "24 min", so it ends at 14:56. Yet it reads "Te dejó" and counts among the 6 viajes.

## 3. Scores

Unchanged: **76/80 structural, 34/40 craft.**
- **Numbers, still 9:** the Mes branch is fixed, but the 14:32 trip lasting past 14:40 is a timing slip.
- **Consistency, still 9:** the first-day G1 leak and the buttons that do nothing.

**Hard fails: none.** No K square, no platform logos, no onboarding step asks for more than one thing, and the chip is unchanged. The one-second glance is still untested, so it is neither a pass nor a fail.

## 4. Verdict

**Go on a laptop as written. Do not run it on a phone until item 1 (back gesture and reload trap) is fixed, or strike "on a phone" from the plan. Before session one, also open the driver's window at `?screen=A1` and write out the three Uber prompts.**

**Comparable in use and feel to Uber Driver?**
- **Feel: yes.** The prototype now behaves as one app at one minute. Every money screen a task reaches reads 14:40, and every ledger foots. Hoy → Tu día → each trip's own detail is the drill Uber teaches (today → trip list → trip detail), in the same flat row-and-ledger idiom. The outage is told through the channel Android actually leaves open: the notification, then the off Hoy, then the permission steps.
- **Use: on paper at least as short as Uber; still not measured.** The three matched tasks take 0, 2 and 1 taps (B1; B1 → Ganancias → ‹; B1 → 14:32). Uber's equivalents are about the same distance or longer.
- **Where Kompara can still fall behind:**
  - the hand-off to Android Settings on day one, which Uber never asks for;
  - task 6's door → Semáforo → limits;
  - task 7's shade gesture.
- **How to read a laptop run:** it compares a mouse on a stranger's laptop against a thumb on the driver's own phone, so a pass on rule 2 is credible and a narrow fail is not conclusive. The fair instrument is the same prototype on a phone at full size, once the back gesture and reloads stop breaking it.
