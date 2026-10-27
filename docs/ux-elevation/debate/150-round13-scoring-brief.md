# Round 13 scoring brief — re-score v3 on both rubrics

**Date:** 2026-10-27 · **Reviewers:** Codex Astra (GPT-6 Astra, ultra) and Claude Opus 5.5, independently · **Version:** `design-system/ui_kits/android-app-v2/index.html` (80 frames: A1–H6, C8–C11 new, N1–N10 night) and `docs/ux-elevation-spec.md`.

## Since Round 12
Round 12: Codex **PASS** (68/80 + 34/40); Opus **NOT_YET** (71/80 + 33/40, criterion 9 = 8: "the signature stops at Tu día"). Round 13 (`140-round13-brief.md`) applied both reviewers' remaining defects: the car-cost raya in Ganancias, the lane drawn in G2, the four marks in A1 on C1's host, Comparar's reference hung on the line with the ink mark and one title grammar, "que compartieron su resumen", F5's unconfirmed row, H3/H5b/H6/E4/G6/B1/B4/D1 polish, chip corners 2dp everywhere, 48dp targets, one 32sp number step, and four placement frames (C8 tall offer, C9 navigation banner, C10 1.3× font, C11 suppressed). Then a fresh critic pass and fixes per section. `check-fixture.py` passes.

## Deliver (markdown, numbered sections exactly as below)
1. **Scorecard A — structural** (`02-rubric-uber-bar.md`, 1–8, /80), one line of evidence per criterion citing frames.
2. **Scorecard B — craft** (`03-rubric-r11-craft.md`, 9–12, /40), evidence per criterion; then the hard-fail check for both rubrics.
3. **Round 12 defects: status** — every R12 defect from YOUR Round 12 report and from the other reviewer's (`131`, `132`): fixed / partly / not fixed, with evidence.
4. **Remaining defects, ranked by damage** (id · frame · severity · defect · evidence · fix). Rendered PNGs are the ground truth.
5. **The one-second read** for B1, E1, F1, C1–C5 and C8–C11.
6. **What only evidence can settle** (short).
7. **Verdict:** PASS only if every criterion ≥ 8 on both rubrics and totals ≥ 68/80 and ≥ 34/40; otherwise NOT_YET with the shortest path.

## Inputs
- Rendered frames at 2×: `{PNG}/<id>.png`; contact sheets `{SHEETS}/{A,B,C,D,E,F,G,H,N}.png`.
- Source: `design-system/ui_kits/android-app-v2/` (`index.html`, `base-v3.css`, `fixture.json`, `check-fixture.py`, `_frame-contract.md`). Spec: `docs/ux-elevation-spec.md`. Briefs and records: `docs/ux-elevation/debate/120`, `140`, `131` (Codex R12), `132` (Opus R12). Rubrics `02`, `03`.
- Benchmarks: `{BENCH}/`.
