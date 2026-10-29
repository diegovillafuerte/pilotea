# Blind round 4, Opus 5.5: "La regla", refined (your trip above, between or below your two lines)

1. **The labels now follow the scale.** The upper line is **tu meta $8** and the lower line is **tu mínimo $6**. The higher number sits on the higher line, so "above both lines" means the trip clears your goal and "below both" means it misses your floor. Nothing has to be decoded.
2. **A first-encounter cue without colour or words.** Next to the number there is a 28 px stroke glyph drawn in the ruler's own language: an arrow rising off a bar (↥) for verde, a dot held between two bars for amarillo, and an arrow dropping off a bar (↧) for rojo. It repeats where the colour block sits (top, middle or bottom of the plate), so verde and rojo, the pair a deuteranope sees as the same olive, differ twice, by arrow direction and by block position. Amarillo is also the only light fill in greyscale. There is no icon tile, verdict word, gauge thumb or pill.
3. **Uber-class restraint.** The chip is one 240 × 100 ink plate with one colour block and three type sizes (40 / 21 / 14, with a 13 px unit stack). The number, the unit's "neto" and $/h share one baseline. The glyph's bar sits on that baseline and its tip reaches cap height. The hairline rules carry the limits. There is no gradient, glow, brand mark or badge.
4. **Placement next to the offer card.** The chip is docked at top 428, right 12, flush with the offer card's right edge, and its bottom edge (y = 528) leaves a 20 px gap above the card at y ≈ 548. The verdict sits right above the fare it judges without covering the card or the host's control row.
5. **Gris shows that nothing could be placed.** It has no colour block and no glyph: "Sin lectura · falta la tarifa" sits where the trip would go, and both limit lines turn dashed and dim, so the state can't be mistaken for any of the three verdicts.

```green
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:428px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.24),0 2px 4px rgba(0,0,0,.2)}
.g-k *{box-sizing:border-box}
.g-band{position:absolute;left:0;right:0;height:56px;display:flex;align-items:baseline;padding:13px 14px 0 12px;white-space:nowrap}
.g-a{flex:0 0 18px;width:18px;height:28px;margin-right:9px;align-self:baseline}
.g-a path{fill:none;stroke:currentColor;stroke-linecap:round;stroke-linejoin:round}
.g-n{font-size:40px;line-height:30px;font-weight:600;letter-spacing:-.01em}
.g-u{position:relative;margin-left:5px;font-family:'Barlow',sans-serif;font-size:13px;line-height:14px;font-weight:500}
.g-u i{position:absolute;left:0;bottom:14px;font-style:normal}
.g-h{margin-left:auto;font-size:21px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 14px;font-size:14px;font-weight:500;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.42)}
.g-v .g-band{top:0;background:#187A36;color:#fff} .g-v .g-l8{top:56px} .g-v .g-l6{top:78px}</style><div class="g-k g-v"><div class="g-ln g-l8">tu meta<b>$8</b><i></i></div><div class="g-ln g-l6">tu mínimo<b>$6</b><i></i></div><div class="g-band" role="img" aria-label="Arriba de tu meta: 9.49 pesos netos por km, 285 por hora"><svg class="g-a" viewBox="0 0 18 28" aria-hidden="true"><path d="M2.5 26.5h13" stroke-width="2"/><path d="M9 21V3M3.3 8.7 9 3l5.7 5.7" stroke-width="3"/></svg><b class="g-n">$9.49</b><span class="g-u"><i>/km</i>neto</span><span class="g-h">$285<small>/h</small></span></div></div>
```

```yellow
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:428px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.24),0 2px 4px rgba(0,0,0,.2)}
.g-k *{box-sizing:border-box}
.g-band{position:absolute;left:0;right:0;height:56px;display:flex;align-items:baseline;padding:13px 14px 0 12px;white-space:nowrap}
.g-a{flex:0 0 18px;width:18px;height:28px;margin-right:9px;align-self:baseline}
.g-a path{fill:none;stroke:currentColor;stroke-linecap:round;stroke-linejoin:round}
.g-n{font-size:40px;line-height:30px;font-weight:600;letter-spacing:-.01em}
.g-u{position:relative;margin-left:5px;font-family:'Barlow',sans-serif;font-size:13px;line-height:14px;font-weight:500}
.g-u i{position:absolute;left:0;bottom:14px;font-style:normal}
.g-h{margin-left:auto;font-size:21px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 14px;font-size:14px;font-weight:500;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.42)}
.g-y .g-l8{top:0} .g-y .g-band{top:22px;background:#F2B705;color:#1A1300} .g-y .g-l6{top:78px}</style><div class="g-k g-y"><div class="g-ln g-l8">tu meta<b>$8</b><i></i></div><div class="g-ln g-l6">tu mínimo<b>$6</b><i></i></div><div class="g-band" role="img" aria-label="Entre tu mínimo y tu meta: 7.16 pesos netos por km, 191 por hora"><svg class="g-a" viewBox="0 0 18 28" aria-hidden="true"><path d="M2.5 1.5h13M2.5 26.5h13" stroke-width="2"/><circle cx="9" cy="14" r="3.8" fill="currentColor"/></svg><b class="g-n">$7.16</b><span class="g-u"><i>/km</i>neto</span><span class="g-h">$191<small>/h</small></span></div></div>
```

```red
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:428px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.24),0 2px 4px rgba(0,0,0,.2)}
.g-k *{box-sizing:border-box}
.g-band{position:absolute;left:0;right:0;height:56px;display:flex;align-items:baseline;padding:13px 14px 0 12px;white-space:nowrap}
.g-a{flex:0 0 18px;width:18px;height:28px;margin-right:9px;align-self:baseline}
.g-a path{fill:none;stroke:currentColor;stroke-linecap:round;stroke-linejoin:round}
.g-n{font-size:40px;line-height:30px;font-weight:600;letter-spacing:-.01em}
.g-u{position:relative;margin-left:5px;font-family:'Barlow',sans-serif;font-size:13px;line-height:14px;font-weight:500}
.g-u i{position:absolute;left:0;bottom:14px;font-style:normal}
.g-h{margin-left:auto;font-size:21px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 14px;font-size:14px;font-weight:500;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.42)}
.g-r .g-l8{top:0} .g-r .g-l6{top:22px} .g-r .g-band{top:44px;background:#D32F2F;color:#fff}</style><div class="g-k g-r"><div class="g-ln g-l8">tu meta<b>$8</b><i></i></div><div class="g-ln g-l6">tu mínimo<b>$6</b><i></i></div><div class="g-band" role="img" aria-label="Abajo de tu mínimo: 2.20 pesos netos por km, 54 por hora"><svg class="g-a" viewBox="0 0 18 28" aria-hidden="true"><path d="M2.5 1.5h13" stroke-width="2"/><path d="M9 7v18M3.3 19.3 9 25l5.7-5.7" stroke-width="3"/></svg><b class="g-n">$2.20</b><span class="g-u"><i>/km</i>neto</span><span class="g-h">$54<small>/h</small></span></div></div>
```

```nodata
<style>@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@500;600&display=swap');
.g-k{position:absolute;top:428px;right:12px;width:240px;height:100px;border-radius:14px;overflow:hidden;background:#0E0F10;color:#EDEDEA;font-family:'Barlow Semi Condensed','Roboto Condensed',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 10px 24px rgba(0,0,0,.24),0 2px 4px rgba(0,0,0,.2)}
.g-k *{box-sizing:border-box}
.g-band{position:absolute;left:0;right:0;height:56px;display:flex;align-items:baseline;padding:13px 14px 0 12px;white-space:nowrap}
.g-a{flex:0 0 18px;width:18px;height:28px;margin-right:9px;align-self:baseline}
.g-a path{fill:none;stroke:currentColor;stroke-linecap:round;stroke-linejoin:round}
.g-n{font-size:40px;line-height:30px;font-weight:600;letter-spacing:-.01em}
.g-u{position:relative;margin-left:5px;font-family:'Barlow',sans-serif;font-size:13px;line-height:14px;font-weight:500}
.g-u i{position:absolute;left:0;bottom:14px;font-style:normal}
.g-h{margin-left:auto;font-size:21px;font-weight:600}
.g-h small{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;margin-left:1px}
.g-ln{position:absolute;left:0;right:0;height:22px;display:flex;align-items:center;padding:0 14px;font-size:14px;font-weight:500;color:rgba(237,237,234,.72);white-space:nowrap}
.g-ln b{font-weight:600;color:#EDEDEA;margin-left:4px}
.g-ln i{flex:1;height:1.5px;margin-left:10px;border-radius:1px;background:rgba(237,237,234,.42)}
.g-g .g-band{top:0;flex-direction:column;align-items:flex-start;justify-content:center;gap:3px;padding:0 14px}
.g-g .g-band b{font-size:22px;font-weight:600;line-height:1} .g-g .g-band span{font-family:'Barlow',sans-serif;font-size:14px;font-weight:500;color:rgba(237,237,234,.68)}
.g-g .g-l8{top:56px} .g-g .g-l6{top:78px}
.g-g .g-ln{color:rgba(237,237,234,.5)} .g-g .g-ln b{color:rgba(237,237,234,.72)}
.g-g .g-ln i{background:repeating-linear-gradient(90deg,rgba(237,237,234,.32) 0 5px,transparent 5px 9px)}</style><div class="g-k g-g"><div class="g-ln g-l8">tu meta<b>$8</b><i></i></div><div class="g-ln g-l6">tu mínimo<b>$6</b><i></i></div><div class="g-band"><b>Sin lectura</b><span>falta la tarifa</span></div></div>
```

_Render check: the snippets are byte-identical to `scratchpad/blind/gen/opus-r4/*.html`, rendered with `render_designs.py opus-r4` (`scratchpad/blind/render/opus-r4/*.png`). I made three passes after the first render. Pass 2 changed the yellow glyph from an I-beam to a dot between bars and moved the chip off the card's shadow. Pass 3 tied the glyph to the number's baseline and cap height. Pass 4 fixed the /km–neto unit stack onto the baseline and the gris padding. I checked greyscale, blur (σ 4 and 10) and deuteranopia for each pass (`scratchpad/blind/r4/opus-work/it4-glance.png`)._
