# Round 14 — score the kit after the blind-test winners (2026-10-27)

Round 13b: both reviewers PASS on their own rubrics (`171`, `172`). The founder's complaint ("the chip still looks like every app Claude designs") was then tested blind, against generic designs from fresh models and Uber Driver screenshots, by six context-free judges (`180-blind-template-test.md`). Two winners came out of five chip rounds and three Hoy rounds and now run through the whole kit (`190-blind-winners-propagation.md`):
- **la regla** — the chip as two labelled limit lines (tu meta $8, tu mínimo $6) with the trip as a colour band above, between or below them and a solid ▲ ▬ ▼ cue; placed just above the host's offer card.
- **Hoy v5 language** — white day surface, 30px root titles, a green dot for "Lector funcionando", a 72px hero with one quiet basis line, offer rows that carry their meaning, a grey full-width secondary.
- **Vocabulary** — the $8 threshold is now "tu meta" and the $6 threshold "tu mínimo" (judges read the old pair as backwards).

## Deliver (short markdown)
1. Score both rubrics on the rendered frames: structural `02-rubric-uber-bar.md` (criteria 1–8) and craft `03-rubric-r11-craft.md` (criteria 9–12), with totals and the hard-fail check in one line.
2. Regressions the propagation introduced (frame, evidence, fix). Rendered PNGs are the ground truth; check vocabulary consistency (no frame may still call $8 "tu mínimo"), chip placement on C1–C11 and N8–N10, overflow from the bigger type.
3. Your honest answer to the founder's two questions: does the chip still read as the Claude/category template, and is the product now comparable in use and feel to Uber Driver? Name what still falls short.
4. Verdict: PASS (every criterion ≥ 8, ≥ 68/80 and ≥ 34/40) or NOT_YET with the shortest path.

Inputs: frames `{PNG}/<id>.png`; sheets `{SHEETS}/{A…H,N}.png`; kit `design-system/ui_kits/android-app-v2/` (run `python3 check-fixture.py`); spec `docs/ux-elevation-spec.md`; blind-test record `180`; Uber references `{UBER}/uber-1.png … uber-6.png`.
