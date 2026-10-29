# Blind template test — materials (2026-10-27)

The evidence behind `../debate/180-blind-template-test.md`: was the chip (and later the Hoy screen) still "what every Claude design for this concept looks like"? Fresh models with no project context designed the same feature from one neutral brief; the kit's candidates were rendered on the identical host and judged blind, with shuffled letters, by six context-free judges (Claude Plan agents Opus ×2, Sonnet ×2, Haiku ×1, and Codex CLI on gpt-6-astra).

| Path | What it is |
|---|---|
| `brief-generic.md` | The neutral brief every generic chip was designed from |
| `r3-brief.md`, `r4-brief.md` | The Round 3 and Round 4 iteration briefs given to Opus and Codex (proposals: `../debate/181`–`184`) |
| `host-clean.html` | The identical neutral host every chip was rendered on (host-only CSS) |
| `designs/chip/<name>/{green,yellow,red,nodata}.html` | Every chip in the test: generic baselines (`opus-*`, `sonnet-*`, `haiku-*`, `r1-*`, `codex-1`), the Round 10 chip (`r10`), v3, the v4 variants, the lead variants, the Round 3/4 proposals and the round 5 variants. **`r5a` won** ("la regla", now in the kit as `.regla`) |
| `designs/hoy/` | The Hoy screen test: generic screens from four models and the winning `hoy-v5.html` |
| `judges/` | The exact judge prompts: chip lineup, Hoy board, and the whole-product read against Uber Driver |
| `keys/` | Answer keys (letter → design) for each blind lineup and board; they were kept outside the judges' folders |
| `tools/` | Build, render, lineup and glance scripts as used. Paths point at the session scratchpad where the run happened; set them to a working folder to re-run. Uber Driver reference screenshots are not committed |

Rendered PNGs, judge outputs and logs stayed in the session scratchpad; the tables in `180` quote them.
