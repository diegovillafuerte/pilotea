# Round 13b — confirm the residual pass (no regression)

Round 13: **both PASS** on the same version — Codex Astra 71/80 + 35/40 (`151`), Opus 5.5 73/80 + 35/40 (`152`). A residual pass then applied both reviewers' Round 13 lists (`160-round13-residual-brief.md`): E1/E7b/N4 qualifier on one line without "(−$1,500)" (the weekly −$1,500 now lives in E1b's ledger and the checker asserts it there), E3/E3c/N5 bars at Semana's width, E4 without the ghost band, F2/F3/N7 with the 48dp context row and a plain standing sentence, H3 broken like F2 with position-without-amounts in the privacy state, G2 limit labels with ≥48dp targets, suppression trace rule (C11/D1/G4 captions), hypothesis wording for perception claims (captions + spec), chip corners declared once at 2dp, spec §9i/§10.

## Deliver (short markdown)
1. For each residual item from YOUR Round 13 list: done / partly / not done, with frame evidence.
2. Any regression the pass introduced (frame, evidence, fix). Rendered PNGs are the ground truth.
3. Re-score both rubrics (criteria 1–12, totals) and the hard-fail check in one line.
4. Verdict: PASS (every criterion ≥ 8, ≥ 68/80 and ≥ 34/40) or NOT_YET with the shortest path.

Inputs: frames `{PNG}/<id>.png` (A1…H6, C8–C11, N1–N10); sheets `{SHEETS}/{A…H,N}.png`; kit `design-system/ui_kits/android-app-v2/` (run `python3 check-fixture.py`); spec `docs/ux-elevation-spec.md`; your Round 13 report.
