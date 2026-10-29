# Round 16 — la regla that wins the first glance

**Lineup 7** (blind, six context-free judges; the crop now shows the ride app's own offer card and its 36px fare "$148.00"; key in ../blind-key-lineup7.json). Means:

| design | distinct | craft | glance rank (/11) | Uber-class rank | reading order | host hierarchy | template |
|---|---|---|---|---|---|---|---|
| generic sonnet-1 (split tile, icon + word) | 3.2 | 7.6 | **1.2** | **2.6** | 9.0 | 5.2 | 5/5 |
| kit-r14 (moving band) | **7.8** | 6.6 | 6.8 | 6.8 | 3.8 | 6.6 | 0/5 |
| opus-r15b (fixed band + marker) | 6.2 | 6.6 | 8.0 | 5.2 | 8.8 | 7.4 | 0/5 |
| codex-r15b (side block + ruler) | 6.6 | 6.4 | 7.4 | 5.8 | 8.4 | 7.4 | 0/5 |
| opus-r15a (side block + stroke) | 7.2 | 6.6 | 9.6 | 7.0 | 8.2 | 7.8 | 0/5 |
| codex-r15a (fixed band + pointer) | 5.4 | 6.0 | 7.8 | 7.2 | 8.8 | 7.4 | 0/5 |

**Why the generics win the glance (every judge):** each state has a large colour area, a LARGE distinct shape (check / warning / octagon silhouettes) and a WORD, so a red-green colour-blind driver in sunlight still has the shape and the word. Ours rely on small ▲ ◆ ▼ glyphs (≈18px) and have no word; the moving band (kit-r14) also costs reading order.

**Target for round 16 — keep what is ours, take what wins the glance:**
1. **Fixed reading order** (as r15b): the band sits in one place, same size, every state.
2. **A big shape cue**: ▲ ◆ ▼ large enough to read as a silhouette at arm's length (≈26–32px), solid, high-contrast on the band. Still ours (the trip's position), not the template's check / triangle-warning / octagon.
3. **A short position phrase, not a verdict word**: the template says "CONVIENE / NO CONVIENE" (a judgement). Ours says where the trip sits against the driver's OWN limits — "Arriba de tu meta" / "Entre tu meta y tu mínimo" / "Abajo de tu mínimo" (gris: "Sin lectura · falta la tarifa"). Size it to be read in one second; it may lead and the number follow, or the reverse — your call, prove it in the render.
4. **The two labelled limits stay** (they are the signature and they make the phrase concrete), as quiet as they can be while still visible (lines ≥2px at ≈75 % white, labels ≥15px).
5. **Host hierarchy**: nothing louder than the host's 36px fare; the deciding rate ≤32px; $/h quiet or gone.
6. **No template tropes**: no icon tile/circle, no judgement word, no horizontal RAG slider with a thumb, no gradient/glow.

Constraints as round 15 (values, colours, Barlow / Barlow Semi Condensed, ≤ 240×100 or 240×112 at most, bottom edge at y 529, right 12, text ≥ 13px). Current snippets for reference: /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/blind/gen/kit-r14, /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/blind/gen/opus-r15b, /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/blind/gen/codex-r15b; lineup images /private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/blind/lineup7/design-*.png (key above maps letters).

**Deliver:** two variants A and B; four states each; format as in round 15 (fenced blocks `A-green` … `B-nodata` with one <style> + markup each, classes prefixed g-, root position:absolute).
