# Blind template test — does the chip still look like "every app Claude designs"? (2026-10-27)

**Why:** the founder's example was that the chip "still looks like all other apps designed by Claude for that exact same concept". Rounds 11–13 passed both reviewers on their rubrics, but no test had checked that complaint directly or blindly.

## Method
1. **Baseline of the category default.** Fresh models with no project context designed the same chip from one neutral brief (`../blind-test/brief-generic.md`): Opus ×4, Sonnet ×4, Haiku ×3 (two runs) and Codex ×1 — 12 generic designs. All rendered on one identical, unbranded host with only their own CSS; v3 and the Round 10 chip (the version the founder rejected) were injected the same way.
2. **Blind round 1.** 14 designs, shuffled letters, answer key kept outside the judges' folder. Six judges with no project context (Claude Plan agents: Opus ×2, Sonnet ×2, Haiku ×1; Codex CLI ×1) grouped the designs into visual families, flagged templates, and scored distinctiveness, craft and one-second glance.
3. **Glance proxy.** Each state degraded as a windshield-mounted phone is seen peripherally (tiny, blur, greyscale, deuteranopia, sun glare) and classified blind. Result: every design scored 16/16 and 20/20 — models classify degraded images far better than human peripheral vision, so this proxy does not discriminate and is not used as evidence.
4. **Iteration and blind round 2.** Three v4 candidates on the v3 signature (v4a labelled lane, v4b moving lane, v4c level), v3, the Round 10 chip and six generic chips (including the judges' glance favourites), same six judges.

## Results
**The template question is settled.** 11 of 12 generic chips fall into two families every judge named: "split verdict tile" (coloured block with check/word + dark panel + red-amber-green slider with a thumb between $6 and $8) and "financial summary card" (status pill + big $/km + $/h + gauge). v3 was never grouped with a generic chip (6/6 judges), never called a template (0/6) and was the most distinctive design for all six judges (mean 8.0 vs 2.8–5.7). The Round 10 chip was called a template by 5/6 judges.

**But distinct is not yet Uber-class.** v3: craft 5.3/10 (generic 6.2–7.5), glance rank 10.7 of 14. Round 2: v4a is our best (distinct 7.2, craft 6.0, glance rank 8.2/11, Uber-class rank 8.0/11, point of view 3/6 judges); the generic split tiles win glance (rank 1.3–3.0) and the restrained Round 10 pill is ranked most Uber-like (2.2) — the very look the founder rejected. Judges' reasons, consistent across models: flush-edge bars and tiny grey labels look unfinished; the lit band is thin (amarillo especially); band position must be learned; no shape or word cue for first encounter.

| Round 2 | distinct | craft | glance rank | Uber-class rank | template | point of view |
|---|---|---|---|---|---|---|
| v3 | 5.8 | 4.7 | 10.7 | 10.0 | 0/6 | 0 |
| v4a labelled lane | 7.2 | 6.0 | 8.2 | 8.0 | 0/6 | 3 |
| v4b moving lane | 5.8 | 4.8 | 8.0 | 9.7 | 0/6 | 0 |
| v4c level | 7.0 | 4.8 | 9.2 | 9.8 | 0/6 | 1 |
| Round 10 chip | 3.0 | 7.2 | 6.3 | 2.2 | 6/6 | 0 |
| generic (6) | 2.5–4.5 | 6.7–7.5 | 1.3–8.3 | 2.8–5.7 | 5–6/6 | 0–1 |

**Target for round 3:** keep "not a template" (0 template votes, distinct ≥ 7) and reach craft ≥ 7, glance rank and Uber-class rank in the top third against the same field.

## Rounds 3–5: rebuilding the chip against the blind evidence
Each round used the same six judges and the same judging sheet: families, template, distinct and craft scored 1–10, glance and Uber-class ranks out of 11, and point of view. Every round had 11 designs under freshly shuffled letters, and the answer keys (`blind-key-lineup3/4/5.json`) stayed outside the judges' folder. Glance and Uber-class are mean ranks (1 = best). The generic field is the six from round 2; round 5 dropped codex-1.

### Round 3: two independent proposals
Opus 5.5 and Codex Astra each redesigned the chip from the round-2 evidence without seeing each other's work.
- **la regla, Opus (opus-r3, `182`)**: a 240 × 100 ink plate floating 12 px off the edge, with two ruled lines, "tu mínimo $8" above "tu rojo $6". The trip is one 56 px colour block above the lines (verde), between them (amarillo) or below them (rojo). Gris has no block and dashed lines.
- **Codex Astra (codex-r3, `181`)**: the same above/between/below idea as a denser table, with a 52 px colour field and "≥ $8 / $6–8 / < $6" comparisons.
- **lead-v5**: the debate lead's own successor to v4a: a top band, a labelled "tus límites $6–$8" lane and a bottom band.

| Round 3 | distinct | craft | glance rank | Uber-class rank | template | point of view |
|---|---|---|---|---|---|---|
| la regla (opus-r3) | 7.0 | 6.5 | 6.7 | 5.2 | 1/6 | 4 |
| codex-r3 | 6.7 | 5.5 | 7.7 | 9.8 | 1/6 | 1 |
| lead-v5 | 5.8 | 6.3 | 9.3 | 7.7 | 1/6 | 0 |
| v4a | 6.3 | 5.2 | 10.3 | 9.2 | 1/6 | 0 |
| Round 10 chip | 3.3 | 6.7 | 6.3 | 4.8 | 6/6 | 0 |
| generic (6) | 3.5–4.5 | 6.5–7.8 | 2.0–9.7 | 2.2–7.8 | 5–6/6 | 0–1 |

**Reading.** Four judges put all four ladder designs in one family. The Opus-1 and Codex judges split them into two pairs: la regla with codex-r3, and lead-v5 with v4a. No judge grouped any of them with a generic chip. The only template votes (1/6 each) came from the Haiku judge, who flagged the whole family. la regla was the most distinct design and had the clearest point of view (4/6). Five of six judges flagged its labels: "tu mínimo $8" above "tu rojo $6" reads backwards, because "mínimo" names the lower number. The split tiles still led glance (2.0–2.3) and Uber-class (2.2).

### Round 4: refining la regla
Brief (`../blind-test/r4-brief.md`): relabel the lines "tu meta $8" (upper) and "tu mínimo $6" (lower). Add a first-encounter cue that does not rely on colour, without the template's icon tile, verdict word, gauge thumb or pill. Finish it to Uber-class and place it just above the offer card.
- **opus-r4 (`184`)**: the new labels; a 28 px stroke glyph in the ruler's own language (an arrow rising off a bar, a dot between two bars, an arrow dropping off a bar); a 40 px number. Docked at top 428, right 12, 20 px above the offer card.
- **codex-r4 (`183`)**: the new labels, plain "≥ $8 / $6–8 / < $6" comparisons and a 40 px fare, in a panel at y = 440–540.
- **lead-v6**: the round-3 la regla with the new labels and a CSS direction mark (up chevron, level bar, down chevron).

| Round 4 | distinct | craft | glance rank | Uber-class rank | template | point of view |
|---|---|---|---|---|---|---|
| opus-r4 | 7.3 | 6.8 | 6.0 | 5.2 | 1/6 | 2 |
| codex-r4 | 7.2 | 7.0 | 7.3 | 4.0 | 0/6 | 2 |
| lead-v6 | 7.3 | 6.5 | 6.8 | 4.7 | 1/6 | 1 |
| la regla (opus-r3) | 6.5 | 5.2 | 9.5 | 10.0 | 0/6 | 0 |
| Round 10 chip | 3.3 | 6.3 | 8.8 | 5.2 | 6/6 | 0 |
| generic (6) | 3.2–4.8 | 5.7–7.3 | 1.8–9.7 | 4.2–8.5 | 4–6/6 | 0–1 |

**Reading.** All three refinements improved on craft (6.5–7.0) and on Uber-class rank (4.0–5.2, up from 5.2–9.8 in round 3). codex-r4 reached craft 7.0, 0/6 template votes and the best Uber-class rank in the field (4.0). Point of view was split. Glance did not improve enough: 6.0–7.3, against 1.8–2.8 for the split tiles.

### Round 5: glance variants on opus-r4
Three variants of opus-r4, each changing only the glance cue:
- **r5a**: the stroke glyph replaced by a solid shape: ▲ verde, ▬ amarillo, ▼ rojo.
- **r5b**: a phrase under the number, in a taller band: "Arriba de tu meta", "Entre tu meta y tu mínimo", "Abajo de tu mínimo".
- **r5c**: the shape and the phrase together.

The field: the three variants, opus-r4, codex-r4, the Round 10 chip and five generic chips, including the two glance leaders.

| Round 5 | distinct | craft | glance rank | Uber-class rank | template | point of view |
|---|---|---|---|---|---|---|
| r5a (la regla, solid shape) | 7.5 | 7.5 | 6.2 | 2.8 | 0/6 | 5 |
| r5b (phrase) | 6.7 | 6.0 | 7.5 | 7.5 | 0/6 | 0 |
| r5c (shape + phrase) | 7.2 | 6.7 | 5.8 | 4.5 | 0/6 | 0 |
| opus-r4 | 6.8 | 5.7 | 9.2 | 9.8 | 0/6 | 0 |
| codex-r4 | 7.7 | 6.7 | 9.0 | 6.0 | 0/6 | 0 |
| Round 10 chip | 3.7 | 6.8 | 8.8 | 4.5 | 6/6 | 0 |
| generic (5) | 3.2–4.8 | 6.2–7.5 | 2.0–9.0 | 3.0–10.5 | 6/6 | 0–1 |

**Reading.** r5a is the first design to meet the round-3 target on four of five counts:
- 0/6 template votes;
- distinct 7.5;
- craft 7.5, equal to the best generic chip;
- Uber-class rank 2.8/11, the best of all 11, with three judges ranking it first.

It also had the clearest point of view (5/6, every judge except Haiku). It misses on glance: rank 6.2/11, against 2.0 for the best split tile. The phrase did not help. r5b lost craft (6.0) and Uber-class rank (7.5), and r5c gained a little on glance (5.8) but lost Uber-class rank (4.5). The judges' reason for the glance gap was the same across models. The split tiles put a verdict word and distinct road-sign shapes (check, triangle or diamond, octagon) on a large colour block, while the ladder's band moves only about 40 px inside a small dark plate.

## Screen tests: Hoy and Ganancias against Uber Driver
**Method.** The reference was three real Uber Driver screens from its Play Store listing: an offer, the home map and Ganancias. They set the "Uber-class" bar. Four fresh models (Opus, Sonnet, Haiku and Codex) each designed Hoy from one neutral brief with the same content: reader status, money left today after costs, best hour, the last three offers and three tabs. The same six judges scored each screen with shuffled letters (keys in `blind-key-hoy*.json` and `blind-key-ganancias.json`). Each screen got 1–10 scores for Uber-class, one-glance clarity for a parked driver and distinct point of view, a template yes/no, and a rank on Uber-class.

### Hoy round 1
| Hoy round 1 | Uber-class | clarity | distinct | template | Uber-class rank (of 6) |
|---|---|---|---|---|---|
| v3 | 6.2 | 6.2 | 7.7 | 0/6 | 3.7 |
| Round 10 | 3.7 | 6.3 | 3.5 | 6/6 | 6.0 |
| generic Sonnet | 8.0 | 9.0 | 6.2 | 0/6 | 1.3 |
| generic Opus | 6.8 | 8.7 | 3.3 | 6/6 | 2.3 |
| generic Codex | 6.2 | 8.0 | 3.5 | 6/6 | 3.3 |
| generic Haiku | 5.6 | 8.0 | 4.2 | 6/6 | 4.3 |

**Reading.** v3's Hoy was the most distinct screen and not a template, but it sat mid-field on Uber-class and was the least clear of the six. The Sonnet screen led on Uber-class, clarity and rank, and was the only generic screen not called a template.

### Hoy round 2: Hoy v4
Hoy v4 has a white day surface, a 30 px title, a reader status with a green dot and a 76 px hero. The offer rows are flat hairline rows with the chip's mark and right-aligned times. Below them is a grey full-width "Ver tu día" button, and the nav has a top indicator.

| Hoy round 2 | Uber-class | clarity | distinct | template | Uber-class rank (of 7) |
|---|---|---|---|---|---|
| Hoy v4 | 7.5 | 7.5 | 7.2 | 0/6 | 1.8 |
| v3 | 5.5 | 6.2 | 6.8 | 0/6 | 4.8 |
| Round 10 | 3.7 | 7.0 | 3.3 | 6/6 | 6.8 |
| generic Sonnet | 7.3 | 9.0 | 5.7 | 2/6 | 1.8 |
| generic Opus | 6.7 | 8.5 | 3.8 | 5/6 | 3.2 |
| generic Codex | 6.2 | 8.0 | 3.7 | 6/6 | 4.2 |
| generic Haiku | 5.2 | 8.0 | 4.0 | 6/6 | 5.3 |

**Reading.** v4 tied the Sonnet screen on Uber-class rank (1.8), with the top Uber-class score (7.5) and 0/6 template votes, but trailed it on clarity (7.5 vs 9.0). The judges gave three reasons:
- an offer's outcome is shown only by the colour of its glyph, never in words;
- the glyph has to be learned;
- the "Ya sin gasolina ni desgaste" caption row is weak.

### Hoy round 3: Hoy v5
Hoy v5 is v4 with more words. The hero label reads "Te quedan hoy · aprox.", with one quiet line under the value ("6 viajes tomados, ya sin gasolina ni desgaste"). Each offer row states its meaning in the chip's own words: "Arriba de tu meta", "Abajo de tu mínimo", "Faltó la tarifa". The Round 10 screen was dropped from the field.

| Hoy round 3 | Uber-class | clarity | distinct | template | Uber-class rank (of 7) |
|---|---|---|---|---|---|
| Hoy v5 | 8.1 | 8.3 | 7.2 | 0/6 | 1.2 |
| Hoy v4 | 7.3 | 7.5 | 6.7 | 0/6 | 2.2 |
| v3 | 5.8 | 6.0 | 6.0 | 0/6 | 4.8 |
| generic Sonnet | 6.3 | 8.7 | 3.7 | 6/6 | 3.5 |
| generic Opus | 5.8 | 8.5 | 3.0 | 6/6 | 4.2 |
| generic Codex | 4.9 | 7.7 | 2.8 | 6/6 | 6.0 |
| generic Haiku | 4.8 | 8.0 | 3.7 | 6/6 | 6.2 |

**Reading.** Hoy v5 is the best of 7 on Uber-class (8.1) and Uber-class rank (1.2). Five of six judges ranked it first, and Codex ranked it second. Its clarity rose to 8.3, within 0.4 of the best generic screen (8.7). For comparison, v3's Hoy scored 6.2 on Uber-class in round 1. Template votes depend on the field: the Sonnet screen went from 0/6 to 2/6 to 6/6 as screens that were not templates joined the lineup.

### Ganancias board
Two "Ganancias · semana" designs, v3 and the Round 10 version, were judged against Uber's Ganancias screen in the same session as Hoy round 1. No generic baseline was built for this screen.

| Ganancias | Uber-class | clarity | closer to Uber |
|---|---|---|---|
| v3 | 7.2 | 7.0 | 6/6 |
| Round 10 | 4.0 | 5.8 | 0/6 |

## Decision
- **Chip: la regla (r5a).** Distinct 7.5; craft 7.5 (equal to the best generic chip); Uber-class rank 2.8/11 (best of 11); template 0/6; point of view 5/6; glance rank 6.2/11 (split tiles 2.0).
- **Hoy: Hoy v5.** Uber-class 8.1 (best of 7); clarity 8.3 (best generic 8.7); distinct 7.2; template 0/6; Uber-class rank 1.2, with 5 of 6 judges ranking it first. v3 had scored 6.2 on Uber-class.
- **Propagated into the kit on 2026-10-27** (`190-blind-winners-propagation.md`). `.regla` is in `base-v3.css`, with the exact markup for each state in `_regla-markup.html`. The Hoy v5 classes are appended to `base-v3.css`.
- **Vocabulary.** The green threshold ($8) is now "tu meta" and the red threshold ($6) is "tu mínimo", because judges read the old pair as backwards: in round 3, 5 of 6 flagged "tu mínimo $8" above "tu rojo $6". The phrases are "Arriba de tu meta", "Entre tu meta y tu mínimo" and "Abajo de tu mínimo". "Tu semáforo" stays as the screen name.
- **Residual risk: first-encounter glance.** The template's verdict word and road-sign icons still win it (glance rank 2.0 for the split tiles, 6.2 for la regla). Model judges cannot settle this: their glance ranks are predictions, and the degraded-image proxy (method step 3) did not discriminate. The mounted driver test will settle it: about 12 Mexico City drivers, the same test Round 13 already requires for the glance score.
