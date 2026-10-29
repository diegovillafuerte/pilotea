# Usability walkthrough (first-time driver, task-based)

A repeatable "use" test for the static kit: six context-free judges (Claude Plan agents Opus ×2, Sonnet ×2, Haiku ×1, and Codex CLI on gpt-6-astra) act as a CDMX driver who has never used Kompara. They get the contact sheets A–H, a screen list (`SCREENS-*.md`, id: what it is) and two Uber Driver screenshots, and do eight tasks (`JUDGE.md`): read today's take and the reader state, judge an offer in one second, find every offer of the day, last week after the car, which app paid more, change the minimum, the overlay stopped, first-day setup. Per task: path, taps, success, where they got stuck, and the Single Ease Question (1–7); then the three biggest problems, ease against Uber (1–10) and whether every screen is understood without help (1–10). `tally_walk.py` aggregates.

| Run | Kit | Mean ease (1–7) | Ease vs Uber | Understood | Results |
|---|---|---|---|---|---|
| 1 | Round 15c | 5.48 | 6.5 / 10 | 5.7 / 10 | `results-r15c.md` |
| 2 | Round 17 | 5.50 | 6.5 / 10 | 6.0 / 10 | `results-r17.md` |
| 3 | Round 18 | 5.60 | 6.7 / 10 | 5.8 / 10 | `results-r18.md` |
| 4 | Round 19 | 5.46 | 6.5 / 10 | 5.7 / 10 | `results-r19.md` |
| 5 (interactive) | Round 20c | 5.83 | 7.0 / 10 | 7.0 / 10 | `results-r20c-run5.md` — first run on the working prototype: six persona judges drive it through a CLI harness; objective success 54/54 |
| 6 (interactive) | Round 21b | 5.93 | 7.0 / 10 | 7.2 / 10 | `results-r21b-run6.md` — change the minimum 5.00 → 6.00, first day 5.00 → 5.50; answer screens reached 52/54 (task 7 4/6); matched to Uber +0.50 / +1.17 / +1.33; 6/6 named the "Leímos $5,028" line |
| 7 (interactive) | Round 22 | 5.94 | 7.3 / 10 | 7.3 / 10 | `results-r22-run7.md` — no task below 5.5 (lowest 5.67); answer screens reached 52/54 (task 7 4/6); matched to Uber +1.00 / +1.50 / +1.17; money contradictions 1/6 (B2's undated «ayer»). Plan rule: 1 add-five band, 2 PASS, 3 no flags, 5 one report |
| 8 (interactive, corrected instrument) | Round 23b | 6.17 | 7.5 / 10 | 7.5 / 10 | `results-r23b-run8.md` — walkthrough gate **met** (≥ 6.0, ≥ 7.5, no task < 5.5); 54/54 answer screens; matched to Uber +1.67 / +2.00 / +1.50; Opus's readiness criteria 5 of 6 (the trip-vs-day basis repeated by 4/6) |

Gate set by Opus (`../../debate/252`): mean ease ≥ 6.0/7, ease vs Uber ≥ 7.5/10, no task below 5.5.

**Reading.** Four runs over three rounds of targeted fixes stayed within judge noise (5.48 → 5.50 → 5.60 → 5.46). Each round fixed the named defects and the judges named the next ones; the residual complaints are about leaving the app for Android settings, a setup that ends at "preparado" until a real offer arrives, and coined words — limits of a static, model-judged walkthrough. Both reviewers agree the next evidence is a clickable first run with real drivers (`../../driver-test-plan.md`).
