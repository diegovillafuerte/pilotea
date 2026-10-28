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

## Round 14b — the yellow cue (2026-10-27)
**Why:** two section agents propagating la regla flagged that the yellow cue, a solid bar, sits left of the figure like a sign: next to C7's real negative (−$1.05/km) the yellow plate reads "−$7.16".
**Method:** five glyphs for the yellow state only (bar, solid diamond, solid dot, hollow pill, equals), each shown between the green plate and the negative red plate, full size and small greyscale; letters shuffled (`../blind-test/keys/blind-key-cue.json`); the same six context-free judges (`../blind-test/judges/yellow-cue.md`).

| Glyph | reads as negative | mean rank (1 best) | first place |
|---|---|---|---|
| ◆ solid diamond | 0/6 | 1.2 | 5/6 |
| ● solid dot | 0/6 | 1.8 | 1/6 |
| = equals | 0/6 | 3.2 | 0 |
| ▭ hollow pill | 0/6 | 3.8 | 0 |
| ▬ solid bar (was shipped) | **6/6** | 5.0 | 0 |

**Decision:** the yellow cue is ◆ (▲ ◆ ▼): solid, one family with the triangles, keeps its shape small and in greyscale, reads as "up and down merged". Its echo of Mexico's yellow diamond caution sign fits the state. Applied to `_regla-markup.html`, every frame and the spec.

## Round 6 — the kit's final chip against the same field (2026-10-27)
**Field:** lineup 5 with r5a replaced by the chip exactly as the kit now draws it (`kit-r14`: la regla, ◆ yellow cue, top 429) — the other two round-5 variants, opus-r4, codex-r4, the Round 10 chip and five generic chips including the glance leaders. Same six context-free judges, new shuffle (`../blind-test/keys/blind-key-lineup6.json`).

| design | distinct | craft | glance rank | Uber-class rank | template | point of view |
|---|---|---|---|---|---|---|
| kit-r14 | 7.8 | 7.3 | 3.5 | 2.2 | 1/6 | 4 |
| r5c | 6.7 | 6.8 | 4.3 | 4.0 | 1/6 | 1 |
| codex-r4 | 7.3 | 7.0 | 8.0 | 4.3 | 0/6 | 1 |
| opus-r4 | 6.8 | 6.3 | 6.3 | 4.5 | 1/6 | 0 |
| r10 | 3.5 | 6.5 | 10.3 | 5.0 | 6/6 | 0 |
| sonnet-1 | 4.2 | 6.7 | 3.8 | 5.3 | 6/6 | 0 |
| r5b | 6.5 | 6.2 | 5.7 | 5.5 | 1/6 | 0 |
| haiku-2 | 3.3 | 6.8 | 9.3 | 6.5 | 6/6 | 0 |
| opus-1 | 3.5 | 5.8 | 5.2 | 9.2 | 6/6 | 0 |
| r1-sonnet-1 | 4.2 | 5.5 | 4.5 | 9.7 | 6/6 | 0 |
| r1-sonnet-2 | 3.5 | 6.3 | 5.0 | 9.8 | 6/6 | 0 |

**Reading.** The final chip ranks first on every axis: distinct, craft (best generic 6.8), Uber-class rank, and now **glance rank (3.5 of 11; best generic 3.8)** — up from 6.2 in round 5, the only change being the ◆ cue that no longer reads as a minus. Template votes 1/6 (that judge marked every design except one as a template); every generic chip 6/6. Four judges of six name it the clearest point of view. First-encounter glance is no longer the template's advantage in model judgement; driver evidence remains the mounted test (spec §11, R14).

## Round 14 screens — Hoy board and the whole product against Uber Driver (2026-10-27)
**Hoy board** (`../blind-test/judges/hoy-board-r14.md`, key `blind-key-hoy4.json`): the kit's B1 after propagation, the Hoy v5 prototype, v3 and four generic screens from fresh models, with three Uber Driver references; six judges. Caveat: the six reused screens still carried the letters printed by the previous board, the kit's B1 carried none; judges scored by file name, so the result stands but the kit screen was distinguishable.

| design | Uber-class | clarity | distinct | template | Uber rank |
|---|---|---|---|---|---|
| hoy-v5 | 6.8 | 7.5 | 6.2 | 1/6 | 2.3 |
| kit-B1 | 7.0 | 7.7 | 6.0 | 1/6 | 2.3 |
| gen-sonnet | 6.3 | 8.7 | 4.3 | 6/6 | 3.3 |
| gen-opus | 6.3 | 8.2 | 3.7 | 5/6 | 3.7 |
| gen-codex | 5.3 | 7.5 | 3.0 | 6/6 | 5.3 |
| v3 | 5.0 | 6.3 | 6.3 | 1/6 | 5.3 |
| gen-haiku | 5.0 | 7.7 | 3.8 | 5/6 | 5.7 |

The kit's B1 kept the prototype's standing (Uber-class 7.0 vs 6.8, rank 2.3 tied first; five of six judges ranked the two first and second, three put the kit's B1 first), so propagation lost nothing. Absolute scores ran lower than round 3 (same design, stricter panel). Clarity stays a point under the best generic (7.7 vs 8.7).

**Whole product against Uber Driver** (`../blind-test/judges/product-vs-uber.md`): eight kit screens next to five Uber references, not blind to generics; six judges, means.

| screen | Uber-class | simplicity | template |
|---|---|---|---|
| B1 Hoy | 7.2 | 7.7 | 0/6 |
| C1 chip verde | 7.0 | 6.8 | 0/6 |
| C3 chip rojo | 6.7 | 6.8 | 0/6 |
| D1 Tu día | 6.7 | 6.0 | 0/6 |
| E1 Semana | 6.5 | 6.2 | 2/6 |
| F1 Tus apps | 5.7 | 6.7 | 1/6 |
| G2 Tu semáforo | 7.7 | 7.8 | 0/6 |
| A1 demo | 6.3 | 6.3 | 0/6 |

Coherence 7.8. **The convergent asks (six of six):** keep the overlay's number in one place in every state (the moving band makes the eye search, and in rojo it lands on grey limit text first); quieter limits that still show where the trip sits; demote $/h; one route from Hoy to Tu día; date-aware labels on Tu día; draw comparisons to scale (Comparar, the week chart's best day); label the four states in A1; G2's two limit cards equally live. These feed Round 15.

## Round 15 — a fixed reading order (lineup 7, 2026-10-27)
**Why:** the whole-product board (six of six) and Codex's Round 14 score (glance physics 7) asked for the overlay's number to stay in one place, quieter limits and a deciding rate that does not out-shout the host's fare. Codex and Opus each proposed two variants from one brief (`../blind-test/r15-brief.md`): codex-r15a (fixed band + pointer), codex-r15b (side block + vertical ruler), opus-r15a (side block + stroke), opus-r15b (fixed band + marker on the two lines).
**Method change:** the crop now includes the top of the ride app's offer card with its 36px fare, and the judge prompt (`../blind-test/judges/chip-lineup-r7.md`) adds two scores — reading order and host hierarchy.

| design | distinct | craft | glance rank | Uber-class rank | reading order | host hierarchy | template | point of view |
|---|---|---|---|---|---|---|---|---|
| sonnet-1 | 3.3 | 7.8 | 1.2 | 2.3 | 9.0 | 5.3 | 6/6 | 0 |
| r10 | 3.0 | 6.8 | 8.5 | 3.3 | 8.3 | 8.8 | 6/6 | 0 |
| haiku-2 | 3.3 | 7.0 | 6.3 | 4.3 | 9.2 | 4.2 | 6/6 | 0 |
| opus-r15b | 6.3 | 6.8 | 8.0 | 5.0 | 8.8 | 7.7 | 0/6 | 0 |
| codex-r15b | 6.8 | 6.5 | 7.8 | 5.7 | 8.5 | 7.7 | 0/6 | 1 |
| opus-1 | 3.3 | 6.5 | 3.0 | 6.5 | 8.5 | 5.5 | 6/6 | 0 |
| codex-r15a | 5.8 | 6.2 | 8.0 | 7.0 | 8.8 | 7.7 | 0/6 | 0 |
| kit-r14 | 8.0 | 6.5 | 6.8 | 7.2 | 3.7 | 6.8 | 0/6 | 5 |
| opus-r15a | 7.3 | 6.5 | 9.8 | 7.2 | 8.0 | 7.8 | 0/6 | 0 |
| r1-sonnet-2 | 4.2 | 7.0 | 2.8 | 8.3 | 8.8 | 2.8 | 6/6 | 0 |
| r1-sonnet-1 | 3.5 | 5.5 | 3.7 | 9.2 | 8.7 | 4.8 | 6/6 | 0 |

**Reading.** With the host card in view and reading order asked about, the picture changes: the generic split tile (icon tile + verdict word) leads the glance (1.2) and Uber-class rank (2.3), and the kit's moving band falls to 6.8 / 7.2 with the worst reading order (3.7). The fixed-order variants fix reading order (8.0–8.8) and host hierarchy (7.7–7.8) and stay non-template (0/6), but they do not win the glance (7.8–9.8). Every judge gives the same reason: the generics pair a large colour area with a LARGE shape and a WORD, so a colour-blind driver in sunlight still has two cues; ours carry an 18px glyph and no word. **Lesson on method:** model judgement of glance is framing-sensitive (the same kit chip ranked 3.5 in lineup 6 and 6.8 here); from now on every chip lineup uses this format. Round 16 targets the glance directly.

## Round 16 — winning the first glance (lineup 8, 2026-10-27)
**Brief** (`../blind-test/r16-brief.md`): keep the fixed reading order and the two labelled limits; take what wins the glance — a big solid shape (▲ ◆ ▼ at ≈30px) and a short **position phrase** ("Arriba de tu meta" / "Entre tu meta y tu mínimo" / "Abajo de tu mínimo"), not the template's judgement word; nothing louder than the host's 36px fare; $/h gone. Proposals: Codex `185` (codex-r16a phrase-led band, codex-r16b side block), Opus opus-r16a (number-led band + phrase, labelled limits with a marker) and opus-r16b (phrase-led band, limits as a bracket). Same format as lineup 7 (`../blind-test/judges/chip-lineup-r8.md`).

| design | distinct | craft | glance rank | Uber-class rank | reading order | host hierarchy | template | point of view |
|---|---|---|---|---|---|---|---|---|
| opus-r16a | 5.8 | 7.0 | 3.5 | 2.8 | 8.5 | 7.5 | 1/6 | 0 |
| opus-r16b | 6.3 | 6.7 | 5.5 | 3.3 | 8.3 | 7.8 | 1/6 | 0 |
| opus-1 | 4.5 | 7.0 | 3.3 | 4.3 | 8.7 | 5.7 | 6/6 | 0 |
| r10 | 2.8 | 6.3 | 9.8 | 4.7 | 8.0 | 8.5 | 6/6 | 0 |
| opus-r15b | 5.8 | 6.3 | 9.5 | 4.7 | 8.2 | 8.2 | 1/6 | 0 |
| sonnet-1 | 3.5 | 7.0 | 3.2 | 5.2 | 8.8 | 5.0 | 6/6 | 0 |
| haiku-2 | 3.3 | 7.0 | 9.8 | 6.7 | 9.2 | 3.7 | 6/6 | 0 |
| codex-r16a | 5.5 | 5.5 | 4.0 | 7.3 | 8.0 | 7.0 | 1/6 | 0 |
| codex-r16b | 6.8 | 6.2 | 4.8 | 7.5 | 8.3 | 7.0 | 0/6 | 0 |
| kit-r14 | 7.5 | 6.2 | 8.2 | 9.3 | 3.3 | 6.5 | 1/6 | 5 |
| r1-sonnet-2 | 4.3 | 7.0 | 4.3 | 10.2 | 8.7 | 2.8 | 5/6 | 1 |

**Decision: opus-r16a becomes the kit's chip.** It ranks **first on Uber-class (2.8)**, ties the best craft (7.0), keeps a fixed reading order (8.5) and host hierarchy (7.5), and reaches the generic glance leaders (3.5 vs 3.2–3.3) — the first time one of ours is in the glance lead group under the realistic crop — while staying non-template (1/6 against 6/6 for every generic chip). **What it costs:** distinctiveness 5.8 (the moving band scored 7.5) and the clearest point of view (five of six still pick the moving band). The trade is deliberate: a driver's one-second read and Uber-class restraint outrank the most sketchable idea; the idea survives in the two labelled limits and the marker, and in the row marks across the app.

## Round 15/16 screens — the whole product against Uber Driver again (2026-10-27)
Same eight screens, same five Uber references, same prompt (`../blind-test/judges/product-vs-uber.md`), six judges, after the Round 15 screen fixes and the Round 16 chip. Round 14 values in brackets.

| screen | Uber-class R16 (R14) | simplicity R16 (R14) | template |
|---|---|---|---|
| B1 Hoy | 6.8 (7.2) | 7.5 (7.7) | 1/6 |
| C1 chip verde | 6.8 (7.0) | 7.5 (6.8) | 0/6 |
| C3 chip rojo | 6.8 (6.7) | 7.7 (6.8) | 0/6 |
| D1 Tu día | 5.8 (6.7) | 5.7 (6.0) | 1/6 |
| E1 Semana | 6.0 (6.5) | 5.8 (6.2) | 4/6 |
| F1 Tus apps | 6.5 (5.7) | 6.8 (6.7) | 1/6 |
| G2 Tu semáforo | 7.0 (7.7) | 7.2 (7.8) | 0/6 |
| A1 demo | 5.8 (6.3) | 5.7 (6.3) | 3/6 |
mean Uber-class 6.5 (R14 6.7) · mean simplicity 6.7 (R14 6.8) · coherence 7.8 (R14 7.8)

**Reading.** Within judge noise of Round 14 overall (mean Uber-class 6.5 vs 6.7; coherence unchanged at 7.8): the chip frames read simpler (7.5–7.7 vs 6.8) and Comparar rose (6.5 vs 5.7); Tu día, Semana, G2 and A1 drifted down. Standalone model judges keep finding the next thing to cut, so this board measures direction, not a ceiling. **Recurring asks across both boards:** one money definition across day and week (Hoy leaves the car out, the week takes it off — a locked product decision since Round 7, now a founder fork); fewer second-tier items per screen; Comparar's hero reads as a rate rather than a difference; some judges would turn the chip's limits into a gauge or drop them — the comparative lineups show that is exactly what makes a chip read as the template, so the tray stays.

## Close (Round 15c, 2026-10-27)
The standing reviewers passed the final version on both rubrics: Codex 71/80 + 34/40 (`231`), Opus 74/80 + 34/40 (`232`), every criterion ≥ 8, no hard fail. One late lesson for the record: a "compact" fallback that dropped the two limits was, on its own, the category template again (Codex, `221`); the compact form now drops the phrase line and keeps the limits and the marker (240 × 80, C12). What model judges cannot settle stays with the mounted driver test (spec §11 R14, on C1's and C12's geometries): one-second recognition of position + phrase against the generic verdict word, the compact chip, the C9 manoeuvre case and the marker size.

## Round 17 — the tray marker (2026-10-27)
**Why:** on the whole-product board four of six judges read the tray's white ▲/▼ at the plate's right end as a dropdown or collapse caret — a false affordance on a drag-only overlay (Opus, `242`). **Method:** three markers on the same plates, shuffled (`../blind-test/keys/blind-key-marker.json`), six context-free judges (`../blind-test/judges/tray-marker.md`), full size and small greyscale.

| Marker | reads as a control | position (1–10) | greyscale | craft | mean rank |
|---|---|---|---|---|---|
| **a short bar in the band colour, in the slot** | **0/6** | **7.8** | 6.3 | **7.5** | **1.3** |
| the triangle in the band colour | 6/6 | 6.7 | 5.7 | 6.8 | 2.2 |
| the white triangle (was shipped) | 6/6 | 6.5 | 8.3 | 6.0 | 2.5 |

**Decision:** the marker becomes a short bar in the band's colour sitting in its slot — above the meta line, between the lines, below the mínimo line — the chip's own "third line", the same object as the row mark in every list. Judges' refinements applied: a lighter red for the dark tray, the green bar kept off the tray's top edge. The ▲ ◆ ▼ stays in the band only.

## Round 17 — debate, walkthrough and re-measure (2026-10-27)
After Round 15c both reviewers passed, but the boards plateaued (Uber-class 6.5–6.7) and "use" had never been measured. A first-time-driver **task walkthrough** (`../blind-test/walkthrough/`; six judges, eight tasks) gave mean ease 5.48/7, and six of six hit "Te quedan" meaning two numbers. Codex and Opus debated three changes (`240`–`242`): one money definition (every total after the car's daily share; a trip "te deja"), cutting the second tier, a drawn icon set — plus Codex's type rebalance and Opus's "the 72sp display is the day's alone". All applied with the walkthrough's fixes (one word "semáforo", distinct status glyphs, typeable limits, B3 "Abrir Uber para probar", G8 opens the semáforo answer) and the tray-marker bar.

**Whole-product board (same eight screens, same prompt; Round 16 in brackets):**

| screen | Uber-class R17 (R16) | simplicity R17 (R16) | template |
|---|---|---|---|
| B1 Hoy | 6.8 (6.8) | 7.7 (7.5) | 1/6 |
| C1 chip verde | 7.2 (6.8) | 7.3 (7.5) | 0/6 |
| C3 chip rojo | 7.2 (6.8) | 7.3 (7.7) | 0/6 |
| D1 Tu día | 6.8 (5.8) | 6.8 (5.7) | 1/6 |
| E1 Semana | 7.2 (6.0) | 7.5 (5.8) | 3/6 |
| F1 Tus apps | 7.0 (6.5) | 7.7 (6.8) | 1/6 |
| G2 Tu semáforo | 6.3 (7.0) | 6.3 (7.2) | 0/6 |
| A1 demo | 6.2 (5.8) | 6.2 (5.7) | 1/6 |
mean Uber-class 6.8 (R14 6.4) · mean simplicity 7.1 (R14 6.7) · coherence 7.8 (R16 7.8)

**Walkthrough, run 2:**

| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 6/0/0 | 0.0 | 5.8 |
| Every offer today | 3/3/0 | 1.0 | 5.5 |
| Last week after car | 6/0/0 | 1.8 | 5.8 |
| Which app paid more | 6/0/0 | 1.0 | 6.3 |
| Change tu mínimo | 6/0/0 | 6.8 | 5.0 |
| Overlay stopped | 4/2/0 | 2.2 | 5.2 |
| First day setup | 3/3/0 | 8.7 | 4.2 |
mean ease 5.50 · ease vs Uber 6.5/10 · understand every screen 6.0/10

**Reviewers:** Opus PASS 74/80 + 34/40 (`252`); Codex NOT_YET 67/80 + 34/40 (`251`: A1's 40dp legend rows, limits and recovery unreachable from Hoy, A3's realistic mock dialog). The money chain now holds end to end (both reviewers). The board rose where Round 17 acted (Ganancias +1.2, Tu día +1.0, Comparar +0.5, the chip +0.4) and fell where it added text (G2 −0.7). The walkthrough held at 5.5: the weak tasks are the first day, finding the limits and recovering a missing overlay — Round 18's brief.

## Round 18 — the usability round, measured (2026-10-27)
Ajustes behind a gear, Hoy's status row as a door to the limits, the shorter first run (A1 → A2 → A3 → B3), B7/B8/E1c, D3b's four lines, A1's 48dp legend and the example's basis.

**Walkthrough, run 3:**

| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 5/1/0 | 0.0 | 5.5 |
| Every offer today | 3/3/0 | 1.0 | 5.7 |
| Last week after car | 6/0/0 | 1.8 | 6.0 |
| Which app paid more | 6/0/0 | 1.0 | 6.3 |
| Change tu mínimo | 6/0/0 | 4.3 | 5.3 |
| Overlay stopped | 4/2/0 | 3.5 | 5.2 |
| First day setup | 4/2/0 | 7.8 | 4.7 |
mean ease 5.60 · ease vs Uber 6.7/10 · understand every screen 5.8/10

**Whole-product board (Round 17 in brackets):**

| screen | Uber-class R18 (R17) | simplicity R18 (R17) | template |
|---|---|---|---|
| B1 Hoy | 6.7 (6.8) | 7.0 (7.7) | 1/6 |
| C1 chip verde | 7.0 (7.2) | 7.5 (7.3) | 0/6 |
| C3 chip rojo | 6.8 (7.2) | 7.3 (7.3) | 0/6 |
| D1 Tu día | 6.3 (6.8) | 6.2 (6.8) | 1/6 |
| E1 Semana | 7.0 (7.2) | 7.3 (7.5) | 2/6 |
| F1 Tus apps | 6.7 (7.0) | 7.7 (7.7) | 1/6 |
| G2 Tu semáforo | 6.5 (6.3) | 6.5 (6.3) | 0/6 |
| A1 demo | 5.8 (6.2) | 5.8 (6.2) | 3/6 |
mean Uber-class 6.6 (R14 6.8) · mean simplicity 6.9 (R14 7.1) · coherence 7.7 (R16 7.8)

**Reviewers:** both rubrics PASS for both — Codex 70/80 + 34/40 (`261`), Opus 76/80 + 34/40 (`262`); both NOT_YET on "comparable in use": the walkthrough gate (≥ 6.0 mean, ≥ 7.5 vs Uber, no task < 5.5) is not met. Opus predicted 5.9; measured 5.6. The board is flat within noise (6.6 vs 6.8). Named residuals: the door's destination (judges expected the limits editor), the Semáforo screen drawn only in its warning state, G2 drawn only mid-edit, D1's four rows under "Ofertas · 23", the undrawn Android hand-off, the basis line ("ni coche" read as "without a car"), and no trace on Hoy of an offer drawn without a semáforo — Round 19's brief.

## Round 19 — the second usability pass, measured (2026-10-27)
Doors that open what they name (healthy → Tu meta y tu mínimo at rest), the Semáforo screen's states (G4 healthy, G4b battery, G4c suppression, later G4d paused), Hoy rows with their rate, D1 newest-first, E1d (the week's four lines), A3b (Android's list as "Así se verá"), B3's "Permiso listo" and typical-costs row, B9, "ya restamos gasolina, desgaste y coche".

**Walkthrough, run 4:**

| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 5/1/0 | 0.0 | 5.5 |
| Every offer today | 1/5/0 | 1.0 | 4.8 |
| Last week after car | 6/0/0 | 2.0 | 5.8 |
| Which app paid more | 6/0/0 | 1.0 | 6.2 |
| Change tu mínimo | 5/1/0 | 5.5 | 5.5 |
| Overlay stopped | 4/2/0 | 4.2 | 5.0 |
| First day setup | 4/2/0 | 7.3 | 4.7 |
mean ease 5.46 · ease vs Uber 6.5/10 · understand every screen 5.7/10

**Whole-product board (Round 18 in brackets):**

| screen | Uber-class R19 (R18) | simplicity R19 (R18) | template |
|---|---|---|---|
| B1 Hoy | 6.3 (6.7) | 7.2 (7.0) | 1/6 |
| C1 chip verde | 6.8 (7.0) | 7.7 (7.5) | 0/6 |
| C3 chip rojo | 6.8 (6.8) | 7.7 (7.3) | 0/6 |
| D1 Tu día | 5.7 (6.3) | 6.3 (6.2) | 1/6 |
| E1 Semana | 6.8 (7.0) | 7.0 (7.3) | 2/6 |
| F1 Tus apps | 6.5 (6.7) | 7.8 (7.7) | 2/6 |
| G2 Tu semáforo | 6.5 (6.5) | 6.5 (6.5) | 1/6 |
| A1 demo | 5.3 (5.8) | 5.8 (5.8) | 3/6 |
mean Uber-class 6.4 (R14 6.6) · mean simplicity 7.0 (R14 6.9) · coherence 7.8 (R16 7.8)

**Reviewers:** Opus PASS 76/80 + 34/40 (`272`), NOT_YET on "comparable in use" and advising to stop adding static frames; Codex 70/80 PASS structurally, craft 34/40 with one hard gate (`271`: A3b drew the K inside a square — fixed in 19b) and E1c's hidden loss (fixed in 19b). Run 4 also exposed one regression — D1's two newest rows printed "$…/km" (fixed in 19b with the fixture's split trips, $11.04/km and $11.40/km).

**Where it stands.** Four walkthrough runs: 5.48 · 5.50 · 5.60 · 5.46 — a plateau at about the industry-average SEQ (≈ 5.5), short of the 6.0 gate. The comparative evidence is strong (the chip first on Uber-class among 11 designs, not a template; Hoy v5 first against generics and Uber references); the standalone and "in use" evidence has reached what static frames judged by models can show. The remaining question — is it as easy as Uber Driver for a real driver, on a real phone, in a car — needs the clickable prototype in front of drivers (`../driver-test-plan.md`) and the mounted glance test (spec §11 R14).

## Rounds 20–20c — the instrument made fit for real drivers (2026-10-27/28)

No new blind or walkthrough run: these rounds removed the prototype's seams so the next evidence — run 5 with real drivers — measures the design, not the prototype. **Reviewers:** Round 20 both PASS both rubrics (Codex 71/80 + 34/40, `291`; Opus 76/80 + 34/40, `292`) and both NOT READY as written: the answers were visible to the participant, the month was drawn at 21:10 under a 14:40 clock, the plan had three disagreeing rules. Round 20b: Codex (`294`) — all findings resolved, no-go only on three plan items; Opus (`295`) — all resolved, go on a laptop, phone runs held for the back/reload trap. Round 20c fixed both; an independent phone check (real touch input at three phone sizes and a laptop) returned **GO on a phone and on a laptop**. Kit 106 frames; the prototype runs nine tasks in participant mode (task 8 first), with a mirrored facilitator window; the plan has one pre-registered rule and three tasks matched to the driver's own Uber app.

**Where it stands.** Rubrics passed by both reviewers in every round since 15c; the chip is first on Uber-class and not a template in the blind lineups; "comparable in use" now waits only on the driver test (`../driver-test-plan.md`) and the mounted glance test.

## Rounds 21–24d — the interactive walkthroughs (2026-10-28)

The static walkthrough had plateaued (runs 1–4 ≈ 5.5/7). A participant harness (`blind-test/walkthrough/` README) let six persona judges **drive the clickable prototype** like first-time drivers. Runs 5–8: mean ease **5.83 → 5.93 → 5.94 → 6.17**, ease against Uber **7.0 → 7.0 → 7.3 → 7.5**, understood 7.0 → 7.5; every answer screen reached in runs 5 and 8 (52/54 in 6–7); on the three tasks matched to Uber, Kompara above the judges' own Uber estimates every run. Run 8 (corrected instrument) met the walkthrough gate and five of Opus's six readiness criteria; the sixth (the trip's money vs the day's, repeated by 4/6) was fixed in Round 24 without a run 9, as both reviewers required. **Final verdicts** (`321`, `322`): both PASS both rubrics (Codex 71/80 + 34/40, Opus 76/80 + 34/40, no hard fail); their last protocol items (the last finished trip for task 9, the in-progress trip marked «En curso», a neutral D2e probe) were applied in Rounds 24c–24d and an independent check returned **READY for the real-driver test as written**.

**Where it stands.** The design is at the benchmark bar by every instrument available without people: both expert rubrics, blind lineups against category templates, and model drivers using the working prototype. Both reviewers agree "comparable in use to Uber Driver" can only be claimed after the pre-registered real-driver test (`../driver-test-plan.md`), the mounted one-second glance test (spec §11 R14) and the live app on a phone; until then: **designed to the Uber Driver bar and ready to be tested against it.**
