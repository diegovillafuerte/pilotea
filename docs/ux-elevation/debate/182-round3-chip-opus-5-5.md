# Blind round 3, Opus 5.5: "La regla" (the chip as a ruler of your two limits)

**The blind result, honestly.** The ladder chip I backed from Round 11 on (v3, and the v4a/v4c variants) was the only design that judges never called a template. It also lost on finish and speed. Craft averaged 4.7 to 6.0 against 6.7 to 7.5 for the generic chips; glance rank was 8 to 11 of 11; Uber-class rank was 8 to 10. All six judges gave the same reasons. The lit band was a thin stripe (amarillo was 24 px). The bars sat flush against the bezel and the 13 px grey labels made it look unfinished. The number stayed on the top row, so only verde actually coloured it. B left an empty black band. To understand the chip you had to learn what each band position meant, and nothing explained it the first time. This round keeps the idea (the verdict is where the colour sits against your two limits) and rebuilds the object around those complaints.

**The chip.** A 240 × 100 ink plate floats 12 px off the right edge at top 144, between the host's control row and the offer card, with a 14 px radius. The plate carries the driver's two limits as two ruled lines, always in the same order: **tu mínimo $8** above **tu rojo $6**. The app's own nouns label them (spec: "tu mínimo" is the floor's noun). The trip is one full-width colour block, 56 px tall, holding `$9.49 /km neto` and `$285/h`. It sits above both lines (verde), between them (amarillo) or below both (rojo). Gris has no block: it reads "Sin lectura · falta la tarifa" and the lines turn dashed, because nothing could be placed. A driver can describe it in one sentence: *two lines with your $8 and your $6, and a coloured block with your trip above them, between them or below them.*

## Rationale: one line per judge criticism

1. **"The lit band is thin."** The colour block is 240 × 56 = 13,440 px² in every state: 56 % of the chip, and bigger than the split tiles' verdict tile (about 85 × 110 ≈ 9,400 px²). Amarillo is no longer a stripe; it is the same block as verde and rojo.
2. **"Flush-edge bars and tiny grey labels look unfinished."** The chip floats off the bezel as one rounded plate with a soft two-layer shadow. The labels are 14 px Barlow Semi Condensed at 72 % with the amount in full white. The rules are 1.5 px hairlines on a 16 px left axis shared with the number. There is no K, no glyph and no empty band, because every dark band holds one of your lines.
3. **"Band position must be learned; there is no cue on first encounter."** The bands are now a number line you read on sight: $8 is always drawn above $6, and your trip's number sits physically above, between or below them. It explains itself in four words ("tu mínimo $8", "tu rojo $6"), with no verdict word that tells the driver what to do. It is the same sentence the app teaches: "Color arriba: llega a tu mínimo."
4. **"Colour alone fails colour-blind drivers and sunlight; the split tiles win with a shape cue."** Position is the shape cue. In greyscale the four states are a light mass at the top, in the middle or at the bottom, or no mass at all. Verde and rojo, the pair deuteranopes confuse, sit at opposite ends of the plate, 44 px apart. Amarillo is also the lightest fill.
5. **"Inconsistent: the number stays put, bands go empty, stripes look like glitches."** One rule holds in every state: colour is the trip, ink is your limits. The number and $/h always live in the colour block and move with it. The lines never move relative to each other, and nothing else changes colour.
6. **"Not restrained enough to be Uber-class."** The chip uses one surface, one colour block, three type sizes (36/20/14) and two hairlines. It has no gradient, glow, pill, icon or gauge thumb. Gris is drawn as missing data (no block, dashed lines), not as a fourth verdict in slate.

**Risks I see.** (a) Shown alone in a green-only lineup, the chip is "a green band over two ruled lines". That is quieter and more card-like than v4a, so distinctiveness may drop from 7.2. The ownable part is the ruled lines plus the moving block, which shows best across states. (b) No human has tested the windshield glance. The greyscale/blur check (`scratchpad/blind/r3/opus-work/final-glance.png`) shows clear top, middle and bottom masses, but the round 1 proxy showed that model-judged degradation does not discriminate. (c) The fixed frame means verde's two lines sit under the number, so drivers who never see amarillo may take them for a legend at first. The labels' nouns ("tu mínimo", "tu rojo") are what keep them reading as thresholds.

**Proof of render.** The snippets below are byte-identical to `scratchpad/blind/gen/opus-r3/{green,yellow,red,nodata}.html`, rendered with `render_designs.py opus-r3` on the identical host into `scratchpad/blind/render/opus-r3/*.png`. I iterated three times after the first render. Pass 2 widened the plate to 240 and changed the labels to the product's nouns. Pass 3 lifted label contrast and refined the shadow. Pass 4 rebalanced the block and line zones from 64/18/18 to 56/22/22 for air and a larger position shift. Footprint is 240 × 100; the smallest text is 14 px; fonts are Barlow and Barlow Semi Condensed (Google Fonts); there are no images and no JS. Every class is prefixed `g-`.

## Snippets

```green
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:144px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.22),0 2px 4px rgba(0,0,0,.22),0 0 0 1px rgba(0,0,0,.06)}
.g-k *{box-sizing:border-box}
.g-slab{position:absolute;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;white-space:nowrap}
.g-n{display:flex;align-items:baseline}
.g-n b{font-size:36px;line-height:1;font-weight:600;letter-spacing:-.01em}
.g-n small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:5px;opacity:.9}
.g-h{display:flex;align-items:baseline;margin-top:7px}
.g-h b{font-size:20px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px;opacity:.9}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 16px;font-size:14px;font-weight:500;letter-spacing:.005em;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.4)}
.g-v .g-slab{top:0;background:#187A36;color:#fff} .g-v .g-l8{top:56px} .g-v .g-l6{top:78px}</style><div class="g-k g-v"><div class="g-ln g-l8">tu mínimo<b>$8</b><i></i></div><div class="g-ln g-l6">tu rojo<b>$6</b><i></i></div><div class="g-slab"><div class="g-n"><b>$9.49</b><small>/km neto</small></div><div class="g-h"><b>$285</b><small>/h</small></div></div></div>
```

```yellow
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:144px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.22),0 2px 4px rgba(0,0,0,.22),0 0 0 1px rgba(0,0,0,.06)}
.g-k *{box-sizing:border-box}
.g-slab{position:absolute;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;white-space:nowrap}
.g-n{display:flex;align-items:baseline}
.g-n b{font-size:36px;line-height:1;font-weight:600;letter-spacing:-.01em}
.g-n small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:5px;opacity:.9}
.g-h{display:flex;align-items:baseline;margin-top:7px}
.g-h b{font-size:20px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px;opacity:.9}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 16px;font-size:14px;font-weight:500;letter-spacing:.005em;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.4)}
.g-y .g-l8{top:0} .g-y .g-slab{top:22px;background:#F2B705;color:#1A1300} .g-y .g-l6{top:78px}</style><div class="g-k g-y"><div class="g-ln g-l8">tu mínimo<b>$8</b><i></i></div><div class="g-ln g-l6">tu rojo<b>$6</b><i></i></div><div class="g-slab"><div class="g-n"><b>$7.16</b><small>/km neto</small></div><div class="g-h"><b>$191</b><small>/h</small></div></div></div>
```

```red
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:144px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.22),0 2px 4px rgba(0,0,0,.22),0 0 0 1px rgba(0,0,0,.06)}
.g-k *{box-sizing:border-box}
.g-slab{position:absolute;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;white-space:nowrap}
.g-n{display:flex;align-items:baseline}
.g-n b{font-size:36px;line-height:1;font-weight:600;letter-spacing:-.01em}
.g-n small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:5px;opacity:.9}
.g-h{display:flex;align-items:baseline;margin-top:7px}
.g-h b{font-size:20px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px;opacity:.9}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 16px;font-size:14px;font-weight:500;letter-spacing:.005em;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.4)}
.g-r .g-l8{top:0} .g-r .g-l6{top:22px} .g-r .g-slab{top:44px;background:#D32F2F;color:#fff}</style><div class="g-k g-r"><div class="g-ln g-l8">tu mínimo<b>$8</b><i></i></div><div class="g-ln g-l6">tu rojo<b>$6</b><i></i></div><div class="g-slab"><div class="g-n"><b>$2.20</b><small>/km neto</small></div><div class="g-h"><b>$54</b><small>/h</small></div></div></div>
```

```nodata
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:144px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.22),0 2px 4px rgba(0,0,0,.22),0 0 0 1px rgba(0,0,0,.06)}
.g-k *{box-sizing:border-box}
.g-slab{position:absolute;left:0;right:0;height:56px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;white-space:nowrap}
.g-n{display:flex;align-items:baseline}
.g-n b{font-size:36px;line-height:1;font-weight:600;letter-spacing:-.01em}
.g-n small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:5px;opacity:.9}
.g-h{display:flex;align-items:baseline;margin-top:7px}
.g-h b{font-size:20px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px;opacity:.9}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 16px;font-size:14px;font-weight:500;letter-spacing:.005em;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.4)}
.g-g .g-slab{top:0;flex-direction:column;align-items:flex-start;justify-content:center;gap:2px;padding-top:2px}
.g-g .g-slab b{font-size:21px;font-weight:600;line-height:1} .g-g .g-slab span{font-family:'Barlow',sans-serif;font-size:15px;font-weight:500;color:rgba(237,237,234,.7)}
.g-g .g-l8{top:56px} .g-g .g-l6{top:78px}
.g-g .g-ln i{background:repeating-linear-gradient(90deg,rgba(237,237,234,.4) 0 5px,transparent 5px 9px)}</style><div class="g-k g-g"><div class="g-ln g-l8">tu mínimo<b>$8</b><i></i></div><div class="g-ln g-l6">tu rojo<b>$6</b><i></i></div><div class="g-slab"><b>Sin lectura</b><span>falta la tarifa</span></div></div>
```
