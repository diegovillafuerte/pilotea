# Round 12 brief — score the v3 rebuild on both rubrics

**Date:** 2026-10-26 · **Reviewers:** Codex Astra (GPT-6 Astra, ultra) and Claude Opus 5.5, independently · **Version under review:** `design-system/ui_kits/android-app-v2/index.html` (assembled from `_head.html` + `part-a…h.html` + `part-n.html` on `base-v3.css`; 74 frames) and `docs/ux-elevation-spec.md` (v3).

## What changed since Round 11

The whole product was redrawn on the v3 "paper, ink and the raya" system you both specified in Round 11b (`120-round11-synthesis.md`): la raya con carril as the chip, the mark in every list, paper/ink surfaces following the system theme, Barlow money, no cards, no pills, text tabs, one qualifier row per hero, ink-only errors, a realistic unbranded host, and a night set (N1–N8). Every fixture figure is unchanged and `check-fixture.py` passes.

## Deliver (markdown, numbered sections exactly as below)

1. **Scorecard A — structural** (`02-rubric-uber-bar.md`, criteria 1–8, 1–10 each, total /80) with one line of evidence per criterion citing frames.
2. **Scorecard B — craft** (`03-rubric-r11-craft.md`, criteria 9–12, 1–10 each, total /40) with evidence; then the hard-fail check for BOTH rubrics (each hard fail: present / absent, with the frame).
3. **Round 11 defects: status.** Every defect you or the other reviewer raised in `111`–`114` (your numbered items and theirs): fixed / partly / not fixed, with evidence.
4. **Remaining defects, ranked by damage** (id · frame · severity critical/high/medium/low · defect · exact evidence · concrete fix). Judge from the RENDERED PNGs first (they are the ground truth), the HTML second.
5. **The one-second read**: what a driver on a windshield mount gets from B1, E1, F1 and C1–C5 (one line each).
6. **What only evidence can settle** (short).
7. **Verdict:** PASS only if every criterion ≥ 8 on both rubrics and totals ≥ 68/80 and ≥ 34/40; otherwise NOT_YET with the shortest path to PASS.

## Inputs
- Rendered frames at 2×: `{PNG}/<id>.png` (ids A1…H6, N1…N8); contact sheets `{SHEETS}/{A,B,C,D,E,F,G,H,N}.png`.
- Source: `design-system/ui_kits/android-app-v2/` (`index.html`, `base-v3.css`, `fixture.json`, `check-fixture.py`, `_frame-contract.md`); the frozen Round 10 kit for comparison: `design-system/ui_kits/android-app-v2-r10/`.
- Spec: `docs/ux-elevation-spec.md`. Synthesis: `docs/ux-elevation/debate/120-round11-synthesis.md`. Rubrics: `02-rubric-uber-bar.md`, `03-rubric-r11-craft.md`. Your Round 11 reports and cross-critiques: `111`–`114`.
- Benchmarks: `{BENCH}/` (Uber Driver store screenshots and offer card, DiDi Conductor, GigU overlay, Ruta Rentable, Uber's own per-km badge).
