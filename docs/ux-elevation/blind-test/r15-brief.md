# Round 15 — la regla with a fixed reading order

**Where we are.** "la regla" (kit-r14) is the chip in the kit: an ink plate 240×100, radius 14; two labelled lines "tu meta $8" and "tu mínimo $6"; the trip is a full-width colour band that sits ABOVE both lines (verde), BETWEEN them (amarillo) or BELOW both (rojo), with a solid cue ▲ ◆ ▼, the deciding rate $9.49 at 40px, "/km neto" stacked, and $285/h. Snippets: /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/blind/gen/kit-r14/{green,yellow,red,nodata}.html. Rendered on the standard host: /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/blind/render/kit-r14/*.png (the host's offer card starts at y 545; its fare "$148.00" is 36px bold).

**Blind lineup (6 context-free judges, 11 designs) — it wins comparatively:** distinct 7.8, craft 7.3, glance rank 3.5/11 (best), Uber-class rank 2.2/11 (best), template 1/6 vs 6/6 for every generic chip, clearest point of view 4/6.

**But judged standalone against Uber Driver, six of six judges said the same three things:**
1. **The number moves.** The band and its number sit at the top in verde, the middle in amarillo, the bottom in rojo, so the eye has to find the figure in a different place on each offer (in rojo it lands on grey limit text first). Keep the verdict and its number in ONE position and size in every state.
2. **The limits tray competes.** Two hairline rows with labels take almost half the plate and look like empty progress bars; they should be quieter or smaller, but still say where this trip sits against MY two limits.
3. **Hierarchy against the host.** The 40px figure is bigger than the host's 36px fare right below it (Codex: "a saturated second hero"). Reduce optical dominance; demote $/h (smaller and quieter, or gone).

**Do not fall back into the template.** The generic family every judge calls "the template" is: a coloured tile with a check/!/✕ icon and a verdict word (CONVIENE / NO CONVIENE) + a dark panel + a horizontal red-amber-green slider with a thumb between $6 and $8. Avoid the icon tile, the verdict word, and the horizontal RAG slider with a thumb. The signature to keep is the idea "where this trip sits against my two labelled lines" — you may re-express it (for example a small marker that sits above / between / below two short labelled lines while the band stays fixed), but it must stay readable without colour (▲ ◆ ▼ or equivalent position cue) and ownable.

**Constraints.** Values: verde $9.49/km neto · $285/h; amarillo $7.16 · $191/h; rojo $2.20 · $54/h; gris "Sin lectura" · "falta la tarifa". Verdict colours #187A36 / #F2B705 (text #1A1300) / #D32F2F; plate ink #0E0F10. Footprint ≤ 240×100 (smaller is welcome); text ≥ 13px; Barlow / Barlow Semi Condensed. Placement: bottom edge 16px above the host card → for a 100px plate, top 429 (adjust top so the bottom stays at 529 if your plate is shorter), right 12.

**Deliver:** a 5-line rationale, then TWO variants (name them A and B), each as four fenced code blocks labelled `A-green`, `A-yellow`, `A-red`, `A-nodata` (then the same for B). Each block = one <style> + markup, all classes prefixed g-, root position:absolute in a 393×852 phone; no external images.

**Also from the two standing reviewers (Round 14):**
- Opus R14-09: the ruler that makes la regla ownable is its quietest element (1.5px lines at 42 % white, 14px labels at 72 %); at 60 cm they vanish and verde collapses to "a green block with ▲ and a bold number" (the stock-ticker idiom). Make the two limits visible at distance (e.g. 2px lines ≈ 75 % white, 15px/600 labels) — but quieter than the verdict.
- Opus R14-08 / Codex: the plate now sits 16–20px above the host fare and its 40px number outsizes the host's 36px "$148.00". Try ≤ 34px for the deciding rate and a quieter or absent $/h.
- Codex: keep the threshold grammar; prototype a band with only the configured primary rate (move $/h to detail), reduce the number's optical dominance while retaining readable labels.
