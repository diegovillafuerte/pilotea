# Round 16 chip proposal — Codex Astra (2026-10-27)

_Verbatim from codex exec (gpt-6-astra). Brief: `../blind-test/r16-brief.md`. Designs: `../blind-test/designs/chip/codex-r16a/`, `codex-r16b/`._

A leads with the position phrase; B leads with the rate beside a vertical limits panel.  
Both keep fixed reading positions and solid 30–32px silhouettes.  
Rates stay below the host’s 36px fare; hourly estimates are omitted.  
Both measure 240×112, with 15px limit labels, 2px rules, and bottom edge at y529.  
Browser rendering was unavailable; geometry and fallback-font text widths were checked.

```A-green
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:72px;background:var(--g-color);color:var(--g-on)}
.g-a-cue{position:absolute;left:12px;top:20px;width:32px;height:32px;fill:currentColor}
.g-a-phrase{position:absolute;left:54px;right:10px;top:4px;font-size:18px;line-height:18px;font-weight:600}
.g-a-value{position:absolute;left:54px;top:40px;height:30px;display:flex;align-items:baseline;gap:6px;white-space:nowrap}
.g-a-rate{font-size:30px;line-height:30px;font-weight:600}
.g-a-unit{font-size:14px;line-height:18px;font-weight:500}
.g-a-limits{position:absolute;left:12px;right:12px;top:75px;display:grid;grid-template-rows:17px 17px}
.g-a-limit{display:flex;align-items:center;gap:10px;font-size:15px;line-height:17px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-a-line{flex:1;height:2px;background:rgba(255,255,255,.75)}
</style>
<div class="g-a" style="--g-color:#187A36;--g-on:#fff" role="img" aria-label="Arriba de tu meta. 9.49 pesos netos por kilómetro. Tu meta es 8; tu mínimo es 6.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 32 32" aria-hidden="true"><path d="M16 2 32 30H0Z"/></svg>
    <span class="g-a-phrase">Arriba de<br>tu meta</span>
    <div class="g-a-value"><b class="g-a-rate">$9.49</b><span class="g-a-unit">/km neto</span></div>
  </div>
  <div class="g-a-limits">
    <div class="g-a-limit"><span>tu meta $8</span><span class="g-a-line" aria-hidden="true"></span></div>
    <div class="g-a-limit"><span>tu mínimo $6</span><span class="g-a-line" aria-hidden="true"></span></div>
  </div>
</div>
```

```A-yellow
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:72px;background:var(--g-color);color:var(--g-on)}
.g-a-cue{position:absolute;left:12px;top:20px;width:32px;height:32px;fill:currentColor}
.g-a-phrase{position:absolute;left:54px;right:10px;top:4px;font-size:18px;line-height:18px;font-weight:600}
.g-a-value{position:absolute;left:54px;top:40px;height:30px;display:flex;align-items:baseline;gap:6px;white-space:nowrap}
.g-a-rate{font-size:30px;line-height:30px;font-weight:600}
.g-a-unit{font-size:14px;line-height:18px;font-weight:500}
.g-a-limits{position:absolute;left:12px;right:12px;top:75px;display:grid;grid-template-rows:17px 17px}
.g-a-limit{display:flex;align-items:center;gap:10px;font-size:15px;line-height:17px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-a-line{flex:1;height:2px;background:rgba(255,255,255,.75)}
</style>
<div class="g-a" style="--g-color:#F2B705;--g-on:#1A1300" role="img" aria-label="Entre tu meta y tu mínimo. 7.16 pesos netos por kilómetro. Tu meta es 8; tu mínimo es 6.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 32 32" aria-hidden="true"><path d="M16 0 32 16 16 32 0 16Z"/></svg>
    <span class="g-a-phrase">Entre tu meta<br>y tu mínimo</span>
    <div class="g-a-value"><b class="g-a-rate">$7.16</b><span class="g-a-unit">/km neto</span></div>
  </div>
  <div class="g-a-limits">
    <div class="g-a-limit"><span>tu meta $8</span><span class="g-a-line" aria-hidden="true"></span></div>
    <div class="g-a-limit"><span>tu mínimo $6</span><span class="g-a-line" aria-hidden="true"></span></div>
  </div>
</div>
```

```A-red
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:72px;background:var(--g-color);color:var(--g-on)}
.g-a-cue{position:absolute;left:12px;top:20px;width:32px;height:32px;fill:currentColor}
.g-a-phrase{position:absolute;left:54px;right:10px;top:4px;font-size:18px;line-height:18px;font-weight:600}
.g-a-value{position:absolute;left:54px;top:40px;height:30px;display:flex;align-items:baseline;gap:6px;white-space:nowrap}
.g-a-rate{font-size:30px;line-height:30px;font-weight:600}
.g-a-unit{font-size:14px;line-height:18px;font-weight:500}
.g-a-limits{position:absolute;left:12px;right:12px;top:75px;display:grid;grid-template-rows:17px 17px}
.g-a-limit{display:flex;align-items:center;gap:10px;font-size:15px;line-height:17px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-a-line{flex:1;height:2px;background:rgba(255,255,255,.75)}
</style>
<div class="g-a" style="--g-color:#D32F2F;--g-on:#fff" role="img" aria-label="Abajo de tu mínimo. 2.20 pesos netos por kilómetro. Tu meta es 8; tu mínimo es 6.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 32 32" aria-hidden="true"><path d="M0 2H32L16 30Z"/></svg>
    <span class="g-a-phrase">Abajo de<br>tu mínimo</span>
    <div class="g-a-value"><b class="g-a-rate">$2.20</b><span class="g-a-unit">/km neto</span></div>
  </div>
  <div class="g-a-limits">
    <div class="g-a-limit"><span>tu meta $8</span><span class="g-a-line" aria-hidden="true"></span></div>
    <div class="g-a-limit"><span>tu mínimo $6</span><span class="g-a-line" aria-hidden="true"></span></div>
  </div>
</div>
```

```A-nodata
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:72px;background:var(--g-color);color:var(--g-on)}
.g-a-cue{position:absolute;left:12px;top:20px;width:32px;height:32px;fill:currentColor}
.g-a-phrase{position:absolute;left:54px;right:10px;top:4px;font-size:18px;line-height:18px;font-weight:600}
.g-a-value{position:absolute;left:54px;top:40px;height:30px;display:flex;align-items:baseline;gap:6px;white-space:nowrap}
.g-a-rate{font-size:30px;line-height:30px;font-weight:600}
.g-a-limits{position:absolute;left:12px;right:12px;top:75px;display:grid;grid-template-rows:17px 17px}
.g-a-limit{display:flex;align-items:center;gap:10px;font-size:15px;line-height:17px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-a-line{flex:1;height:2px;background:rgba(255,255,255,.75)}
</style>
<div class="g-a" style="--g-color:#34373A;--g-on:#fff" role="img" aria-label="Sin lectura: falta la tarifa. Tu meta sigue siendo 8 pesos netos por kilómetro; tu mínimo, 6.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 32 32" aria-hidden="true"><path d="M0 14H32V18H0Z"/></svg>
    <span class="g-a-phrase">Sin lectura<br>falta la tarifa</span>
    <div class="g-a-value"><b class="g-a-rate" aria-hidden="true">—</b></div>
  </div>
  <div class="g-a-limits">
    <div class="g-a-limit"><span>tu meta $8</span><span class="g-a-line" aria-hidden="true"></span></div>
    <div class="g-a-limit"><span>tu mínimo $6</span><span class="g-a-line" aria-hidden="true"></span></div>
  </div>
</div>
```

```B-green
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-b-band{position:absolute;left:0;top:0;width:140px;height:112px;background:var(--g-color);color:var(--g-on)}
.g-b-cue{position:absolute;left:10px;top:12px;width:30px;height:30px;fill:currentColor}
.g-b-rate{position:absolute;left:48px;top:8px;font-size:32px;line-height:36px;font-weight:600;white-space:nowrap}
.g-b-unit{position:absolute;left:48px;top:43px;font-size:14px;line-height:18px;font-weight:500}
.g-b-phrase{position:absolute;left:10px;right:8px;top:65px;font-size:19px;line-height:20px;font-weight:600}
.g-b-limit{position:absolute;left:148px;right:10px;height:22px;font-size:15px;line-height:18px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-b-meta{top:30px}.g-b-min{top:70px}
.g-b-line{position:absolute;left:0;right:6px;bottom:0;height:2px;background:rgba(255,255,255,.75)}
.g-b-marker{position:absolute;right:2px;top:var(--g-marker-y);width:8px;height:8px;fill:#fff}
</style>
<div class="g-b" style="--g-color:#187A36;--g-on:#fff;--g-marker-y:18px" role="img" aria-label="9.49 pesos netos por kilómetro. Arriba de tu meta de 8. Tu mínimo es 6.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 30 30" aria-hidden="true"><path d="M15 2 30 28H0Z"/></svg>
    <b class="g-b-rate">$9.49</b>
    <span class="g-b-unit">/km neto</span>
    <span class="g-b-phrase">Arriba de<br>tu meta</span>
  </div>
  <div class="g-b-limit g-b-meta">tu meta $8<span class="g-b-line" aria-hidden="true"></span></div>
  <div class="g-b-limit g-b-min">tu mínimo $6<span class="g-b-line" aria-hidden="true"></span></div>
  <svg class="g-b-marker" viewBox="0 0 8 8" aria-hidden="true"><path d="M8 0 0 4 8 8Z"/></svg>
</div>
```

```B-yellow
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-b-band{position:absolute;left:0;top:0;width:140px;height:112px;background:var(--g-color);color:var(--g-on)}
.g-b-cue{position:absolute;left:10px;top:12px;width:30px;height:30px;fill:currentColor}
.g-b-rate{position:absolute;left:48px;top:8px;font-size:32px;line-height:36px;font-weight:600;white-space:nowrap}
.g-b-unit{position:absolute;left:48px;top:43px;font-size:14px;line-height:18px;font-weight:500}
.g-b-phrase{position:absolute;left:10px;right:8px;top:65px;font-size:19px;line-height:20px;font-weight:600}
.g-b-limit{position:absolute;left:148px;right:10px;height:22px;font-size:15px;line-height:18px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-b-meta{top:30px}.g-b-min{top:70px}
.g-b-line{position:absolute;left:0;right:6px;bottom:0;height:2px;background:rgba(255,255,255,.75)}
.g-b-marker{position:absolute;right:2px;top:var(--g-marker-y);width:8px;height:8px;fill:#fff}
</style>
<div class="g-b" style="--g-color:#F2B705;--g-on:#1A1300;--g-marker-y:60px" role="img" aria-label="7.16 pesos netos por kilómetro. Entre tu meta de 8 y tu mínimo de 6.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 30 30" aria-hidden="true"><path d="M15 0 30 15 15 30 0 15Z"/></svg>
    <b class="g-b-rate">$7.16</b>
    <span class="g-b-unit">/km neto</span>
    <span class="g-b-phrase">Entre tu meta<br>y tu mínimo</span>
  </div>
  <div class="g-b-limit g-b-meta">tu meta $8<span class="g-b-line" aria-hidden="true"></span></div>
  <div class="g-b-limit g-b-min">tu mínimo $6<span class="g-b-line" aria-hidden="true"></span></div>
  <svg class="g-b-marker" viewBox="0 0 8 8" aria-hidden="true"><path d="M8 0 0 4 8 8Z"/></svg>
</div>
```

```B-red
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-b-band{position:absolute;left:0;top:0;width:140px;height:112px;background:var(--g-color);color:var(--g-on)}
.g-b-cue{position:absolute;left:10px;top:12px;width:30px;height:30px;fill:currentColor}
.g-b-rate{position:absolute;left:48px;top:8px;font-size:32px;line-height:36px;font-weight:600;white-space:nowrap}
.g-b-unit{position:absolute;left:48px;top:43px;font-size:14px;line-height:18px;font-weight:500}
.g-b-phrase{position:absolute;left:10px;right:8px;top:65px;font-size:19px;line-height:20px;font-weight:600}
.g-b-limit{position:absolute;left:148px;right:10px;height:22px;font-size:15px;line-height:18px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-b-meta{top:30px}.g-b-min{top:70px}
.g-b-line{position:absolute;left:0;right:6px;bottom:0;height:2px;background:rgba(255,255,255,.75)}
.g-b-marker{position:absolute;right:2px;top:var(--g-marker-y);width:8px;height:8px;fill:#fff}
</style>
<div class="g-b" style="--g-color:#D32F2F;--g-on:#fff;--g-marker-y:98px" role="img" aria-label="2.20 pesos netos por kilómetro. Abajo de tu mínimo de 6. Tu meta es 8.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 30 30" aria-hidden="true"><path d="M0 2H30L15 28Z"/></svg>
    <b class="g-b-rate">$2.20</b>
    <span class="g-b-unit">/km neto</span>
    <span class="g-b-phrase">Abajo de<br>tu mínimo</span>
  </div>
  <div class="g-b-limit g-b-meta">tu meta $8<span class="g-b-line" aria-hidden="true"></span></div>
  <div class="g-b-limit g-b-min">tu mínimo $6<span class="g-b-line" aria-hidden="true"></span></div>
  <svg class="g-b-marker" viewBox="0 0 8 8" aria-hidden="true"><path d="M8 0 0 4 8 8Z"/></svg>
</div>
```

```B-nodata
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:417px;right:12px;width:240px;height:112px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 4px 10px #0003}
.g-b-band{position:absolute;left:0;top:0;width:140px;height:112px;background:var(--g-color);color:var(--g-on)}
.g-b-cue{position:absolute;left:10px;top:12px;width:30px;height:30px;fill:currentColor}
.g-b-rate{position:absolute;left:48px;top:8px;font-size:32px;line-height:36px;font-weight:600;white-space:nowrap}
.g-b-phrase{position:absolute;left:10px;right:8px;top:65px;font-size:19px;line-height:20px;font-weight:600}
.g-b-limit{position:absolute;left:148px;right:10px;height:22px;font-size:15px;line-height:18px;font-weight:600;color:#DADBDD;white-space:nowrap}
.g-b-meta{top:30px}.g-b-min{top:70px}
.g-b-line{position:absolute;left:0;right:6px;bottom:0;height:2px;background:rgba(255,255,255,.75)}
</style>
<div class="g-b" style="--g-color:#34373A;--g-on:#fff" role="img" aria-label="Sin lectura: falta la tarifa. Tu meta sigue siendo 8 pesos netos por kilómetro; tu mínimo, 6.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 30 30" aria-hidden="true"><path d="M0 13H30V17H0Z"/></svg>
    <b class="g-b-rate" aria-hidden="true">—</b>
    <span class="g-b-phrase">Sin lectura<br>falta la tarifa</span>
  </div>
  <div class="g-b-limit g-b-meta">tu meta $8<span class="g-b-line" aria-hidden="true"></span></div>
  <div class="g-b-limit g-b-min">tu mínimo $6<span class="g-b-line" aria-hidden="true"></span></div>
</div>
```