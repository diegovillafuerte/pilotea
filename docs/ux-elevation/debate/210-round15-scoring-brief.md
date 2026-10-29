# Round 15 — score the kit after the glance round (2026-10-27)

Round 14: Opus **PASS** 74/80 + 35/40 (`202`); Codex **NOT_YET** 71/80 + 34/40 with glance physics at 7 (`201`): the 40px chip out-shouted the host's 36px fare, C10's clearance was 12dp, C8 covered the pickup pin. Six context-free judges then compared eight screens with Uber Driver (`180`, "Round 14 screens") and asked, six of six, for a fixed reading order on the overlay, quieter limits, one route from Hoy to Tu día, date-aware Tu día, comparisons drawn to scale, a labelled A1 legend and equally live G2 limits.

**What changed since Round 14**
- **The chip (Round 16, `180`):** two blind rounds (lineups 7 and 8, the crop now shows the host's fare and asks reading order and host hierarchy). The winner replaces the moving band: a **fixed coloured band** with a large solid ▲ ◆ ▼, the deciding rate (≤ 30px, under the host's fare) and a **position phrase** ("Arriba de tu meta" / "Entre tu meta y tu mínimo" / "Abajo de tu mínimo"), over an ink tray with the two labelled limits and a marker above / between / below them; $/h moved off the overlay. Lineup 8: Uber-class rank 2.8/11 (first), glance 3.5 (generic leaders 3.2–3.3), reading order 8.5, host hierarchy 7.5, template 1/6 vs 6/6.
- **Screens:** B1 best-hour row is a plain fact (two routes: basis line → Cómo se calculó, grey "Ver tu día" → Tu día); D1 "Hoy · 19 oct" + a one-line shift timeline; E1/E7b/E8 best day in ink with its figure, one dashed car line; F1/F2/F4 proportional bars; H3 share card on the same bars; G2 both limits live, step printed, reset names both; G1/G4 a fixable reader takes the ink segment, not the green dot; A1 labelled 2×2 legend and a whole host card.
- **Your Round 14 items:** R14-01 dot rule, R14-02 one suppression threshold (plate + 32), R14-03 dead CSS + lede, R14-04 checker guards (vocabulary, chip/row phrase per state), R14-05 C5 per-hour limits, R14-06 "abajo", R14-07 gris accessible name, R14-10 lineup 6 recorded; Codex: C10 clearance, C8 pin, chip hierarchy.

## Deliver (short markdown)
1. Score both rubrics on the rendered frames: `02-rubric-uber-bar.md` (criteria 1–8) and `03-rubric-r11-craft.md` (9–12), totals and the hard-fail line.
2. For each item on YOUR Round 14 list: done / partly / not done, with frame evidence. New regressions (frame, evidence, fix).
3. The founder's two questions: does the chip still read as the Claude/category template, and is the product comparable in use and feel to Uber Driver? Name what still falls short.
4. Verdict: PASS (every criterion ≥ 8, ≥ 68/80 and ≥ 34/40) or NOT_YET with the shortest path.

Inputs: frames `{PNG}/<id>.png`; sheets `{SHEETS}/{A…H,N}.png`; kit `design-system/ui_kits/android-app-v2/` (run `python3 check-fixture.py`); spec `docs/ux-elevation-spec.md`; record `180`; Uber references `{UBER}/uber-1.png … uber-6.png`; your Round 14 report (`201` Codex, `202` Opus).
