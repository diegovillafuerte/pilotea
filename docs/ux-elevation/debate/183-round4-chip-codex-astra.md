# Round 4 chip proposal — Codex Astra (2026-10-27)

_Copied verbatim from the blind-test run (codex exec, gpt-6-astra). Brief: `../blind-test/r4-brief.md`. Rendered designs: `../blind-test/designs/chip/codex-r4/`._

The fare band stays above, between or below the two personal rules, preserving “la regla.”  
Plain ≥ $8, $6–8 and < $6 comparisons add a cue that does not depend on colour.  
“tu meta $8” and “tu mínimo $6” make the thresholds unambiguous.  
A 40px fare anchors one continuous surface; the hourly rate stays secondary.  
The 240×100 panel occupies y=440–540, leaving 8px before the offer card.

```green
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow:wght@500;600&family=Barlow+Semi+Condensed:wght@500;600&display=swap');

.g-green, .g-green * { box-sizing:border-box; }
.g-green {
  position:absolute; top:440px; right:12px;
  width:240px; height:100px;
  display:flex; flex-direction:column;
  overflow:hidden; border-radius:12px;
  background:#111313; color:#F5F5F2;
  box-shadow:0 2px 4px rgb(0 0 0 / 18%);
  font-family:"Barlow Semi Condensed",sans-serif;
  font-variant-numeric:tabular-nums;
}
.g-green .g-band {
  flex:none; height:56px; padding:0 12px;
  display:flex; align-items:center; justify-content:space-between;
  gap:10px; white-space:nowrap;
  background:#187A36; color:#FFFFFF;
}
.g-green .g-fare {
  display:flex; align-items:center; gap:5px;
}
.g-green .g-amount {
  font-size:40px; line-height:42px;
  font-weight:600; letter-spacing:-.5px;
}
.g-green .g-unit {
  display:flex; flex-direction:column;
  font-family:"Barlow",sans-serif;
  font-size:13px; line-height:14px; font-weight:500;
}
.g-green .g-aside {
  flex:none; display:flex; flex-direction:column;
  align-items:flex-end;
}
.g-green .g-relation {
  font-size:22px; line-height:24px; font-weight:600;
}
.g-green .g-hour {
  font-size:18px; line-height:20px; font-weight:500;
}
.g-green .g-limit {
  flex:none; height:22px; padding:0 12px;
  display:flex; align-items:center; gap:10px;
  font-family:"Barlow",sans-serif;
  font-size:14px; line-height:18px; font-weight:500;
  color:#D3D6D4;
}
.g-green .g-limit-name {
  flex:none; width:94px; white-space:nowrap;
}
.g-green .g-limit-name b {
  margin-left:4px; font-weight:600; color:#F5F5F2;
}
.g-green .g-rule {
  flex:1; height:2px; background:#929A95;
}
</style>
<div class="g-green" lang="es-MX" role="img"
     aria-label="9.49 pesos netos por kilómetro y 285 pesos por hora. Sobre tu meta de 8 pesos por kilómetro. Tu mínimo es 6 pesos por kilómetro.">
  <div class="g-band">
    <div class="g-fare">
      <span class="g-amount">$9.49</span>
      <span class="g-unit"><span>/km</span><span>neto</span></span>
    </div>
    <div class="g-aside">
      <span class="g-relation">≥ $8</span>
      <span class="g-hour">$285/h</span>
    </div>
  </div>
  <div class="g-limit">
    <span class="g-limit-name">tu meta<b>$8</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
  <div class="g-limit">
    <span class="g-limit-name">tu mínimo<b>$6</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
</div>
```

```yellow
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow:wght@500;600&family=Barlow+Semi+Condensed:wght@500;600&display=swap');

.g-yellow, .g-yellow * { box-sizing:border-box; }
.g-yellow {
  position:absolute; top:440px; right:12px;
  width:240px; height:100px;
  display:flex; flex-direction:column;
  overflow:hidden; border-radius:12px;
  background:#111313; color:#F5F5F2;
  box-shadow:0 2px 4px rgb(0 0 0 / 18%);
  font-family:"Barlow Semi Condensed",sans-serif;
  font-variant-numeric:tabular-nums;
}
.g-yellow .g-band {
  flex:none; height:56px; padding:0 12px;
  display:flex; align-items:center; justify-content:space-between;
  gap:10px; white-space:nowrap;
  background:#F2B705; color:#1A1300;
}
.g-yellow .g-fare {
  display:flex; align-items:center; gap:5px;
}
.g-yellow .g-amount {
  font-size:40px; line-height:42px;
  font-weight:600; letter-spacing:-.5px;
}
.g-yellow .g-unit {
  display:flex; flex-direction:column;
  font-family:"Barlow",sans-serif;
  font-size:13px; line-height:14px; font-weight:500;
}
.g-yellow .g-aside {
  flex:none; display:flex; flex-direction:column;
  align-items:flex-end;
}
.g-yellow .g-relation {
  font-size:22px; line-height:24px; font-weight:600;
}
.g-yellow .g-hour {
  font-size:18px; line-height:20px; font-weight:500;
}
.g-yellow .g-limit {
  flex:none; height:22px; padding:0 12px;
  display:flex; align-items:center; gap:10px;
  font-family:"Barlow",sans-serif;
  font-size:14px; line-height:18px; font-weight:500;
  color:#D3D6D4;
}
.g-yellow .g-limit-name {
  flex:none; width:94px; white-space:nowrap;
}
.g-yellow .g-limit-name b {
  margin-left:4px; font-weight:600; color:#F5F5F2;
}
.g-yellow .g-rule {
  flex:1; height:2px; background:#929A95;
}
</style>
<div class="g-yellow" lang="es-MX" role="img"
     aria-label="7.16 pesos netos por kilómetro y 191 pesos por hora. Entre tu mínimo de 6 y tu meta de 8 pesos por kilómetro; por debajo de tu meta.">
  <div class="g-limit">
    <span class="g-limit-name">tu meta<b>$8</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
  <div class="g-band">
    <div class="g-fare">
      <span class="g-amount">$7.16</span>
      <span class="g-unit"><span>/km</span><span>neto</span></span>
    </div>
    <div class="g-aside">
      <span class="g-relation">$6–8</span>
      <span class="g-hour">$191/h</span>
    </div>
  </div>
  <div class="g-limit">
    <span class="g-limit-name">tu mínimo<b>$6</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
</div>
```

```red
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow:wght@500;600&family=Barlow+Semi+Condensed:wght@500;600&display=swap');

.g-red, .g-red * { box-sizing:border-box; }
.g-red {
  position:absolute; top:440px; right:12px;
  width:240px; height:100px;
  display:flex; flex-direction:column;
  overflow:hidden; border-radius:12px;
  background:#111313; color:#F5F5F2;
  box-shadow:0 2px 4px rgb(0 0 0 / 18%);
  font-family:"Barlow Semi Condensed",sans-serif;
  font-variant-numeric:tabular-nums;
}
.g-red .g-band {
  flex:none; height:56px; padding:0 12px;
  display:flex; align-items:center; justify-content:space-between;
  gap:10px; white-space:nowrap;
  background:#D32F2F; color:#FFFFFF;
}
.g-red .g-fare {
  display:flex; align-items:center; gap:5px;
}
.g-red .g-amount {
  font-size:40px; line-height:42px;
  font-weight:600; letter-spacing:-.5px;
}
.g-red .g-unit {
  display:flex; flex-direction:column;
  font-family:"Barlow",sans-serif;
  font-size:13px; line-height:14px; font-weight:500;
}
.g-red .g-aside {
  flex:none; display:flex; flex-direction:column;
  align-items:flex-end;
}
.g-red .g-relation {
  font-size:22px; line-height:24px; font-weight:600;
}
.g-red .g-hour {
  font-size:18px; line-height:20px; font-weight:500;
}
.g-red .g-limit {
  flex:none; height:22px; padding:0 12px;
  display:flex; align-items:center; gap:10px;
  font-family:"Barlow",sans-serif;
  font-size:14px; line-height:18px; font-weight:500;
  color:#D3D6D4;
}
.g-red .g-limit-name {
  flex:none; width:94px; white-space:nowrap;
}
.g-red .g-limit-name b {
  margin-left:4px; font-weight:600; color:#F5F5F2;
}
.g-red .g-rule {
  flex:1; height:2px; background:#929A95;
}
</style>
<div class="g-red" lang="es-MX" role="img"
     aria-label="2.20 pesos netos por kilómetro y 54 pesos por hora. Bajo tu mínimo de 6 pesos por kilómetro. Tu meta es 8 pesos por kilómetro.">
  <div class="g-limit">
    <span class="g-limit-name">tu meta<b>$8</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
  <div class="g-limit">
    <span class="g-limit-name">tu mínimo<b>$6</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
  <div class="g-band">
    <div class="g-fare">
      <span class="g-amount">$2.20</span>
      <span class="g-unit"><span>/km</span><span>neto</span></span>
    </div>
    <div class="g-aside">
      <span class="g-relation">&lt; $6</span>
      <span class="g-hour">$54/h</span>
    </div>
  </div>
</div>
```

```nodata
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow:wght@500;600&family=Barlow+Semi+Condensed:wght@500;600&display=swap');

.g-nodata, .g-nodata * { box-sizing:border-box; }
.g-nodata {
  position:absolute; top:440px; right:12px;
  width:240px; height:100px;
  display:flex; flex-direction:column;
  overflow:hidden; border-radius:12px;
  background:#303537; color:#F5F5F2;
  box-shadow:0 2px 4px rgb(0 0 0 / 18%);
  font-family:"Barlow Semi Condensed",sans-serif;
  font-variant-numeric:tabular-nums;
}
.g-nodata .g-message {
  flex:none; height:56px; padding:0 12px;
  display:flex; flex-direction:column; justify-content:center;
}
.g-nodata .g-title {
  font-size:27px; line-height:29px; font-weight:600;
}
.g-nodata .g-reason {
  font-family:"Barlow",sans-serif;
  font-size:14px; line-height:18px; font-weight:500;
  color:#D3D6D4;
}
.g-nodata .g-limit {
  flex:none; height:22px; padding:0 12px;
  display:flex; align-items:center; gap:10px;
  font-family:"Barlow",sans-serif;
  font-size:14px; line-height:18px; font-weight:500;
  color:#D3D6D4;
}
.g-nodata .g-limit-name {
  flex:none; width:94px; white-space:nowrap;
}
.g-nodata .g-limit-name b {
  margin-left:4px; font-weight:600; color:#F5F5F2;
}
.g-nodata .g-rule {
  flex:1; height:2px; background:#A5ACAF;
}
</style>
<div class="g-nodata" lang="es-MX" role="img"
     aria-label="Sin lectura: falta la tarifa. No hay estimación. Tu meta es 8 pesos por kilómetro y tu mínimo es 6 pesos por kilómetro.">
  <div class="g-message">
    <span class="g-title">Sin lectura</span>
    <span class="g-reason">falta la tarifa</span>
  </div>
  <div class="g-limit">
    <span class="g-limit-name">tu meta<b>$8</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
  <div class="g-limit">
    <span class="g-limit-name">tu mínimo<b>$6</b></span>
    <span class="g-rule" aria-hidden="true"></span>
  </div>
</div>
```