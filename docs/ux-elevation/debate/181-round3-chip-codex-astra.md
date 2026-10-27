# Round 3 chip proposal — Codex Astra (2026-10-27)

_Copied verbatim from the blind-test run (codex exec, gpt-6-astra). Brief: `../blind-test/r3-brief.md`. Rendered designs: `../blind-test/designs/chip/codex-r3/`._

1. **Opus-1:** A 52px colour field replaces the thin stripe; large ≥ $8 / $6–8 / < $6 comparisons provide a non-colour cue on first encounter.
2. **Opus-2:** Only the selected interval expands; colour remains entirely above, between or below the two labelled rules, without empty bands or accidental dark stripes.
3. **Sonnet-1:** The 240×100 frame sits at top 112/right 12, clearing host controls; every label is at least 13px with strong contrast.
4. **Sonnet-2:** Barlow Semi Condensed, inset edges, modest corners and a matte surface replace the prototype finish without introducing a pill, icon tile or gauge.
5. **Haiku-1:** A 32px price leads; “Sobre tu meta / Entre tus límites / Bajo tu mínimo” explains the relationship without directing the driver.
6. **Codex:** Consistent numerals, one hourly label and an unlit, dashed no-data state preserve restraint; improved glance performance remains a hypothesis for the next blind test.

```green
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&display=swap');

.g-green, .g-green * { box-sizing:border-box; }
.g-green {
  position:absolute; top:112px; right:12px;
  width:240px; height:100px; padding:4px;
  background:#151a17; color:#f5f5ef;
  border-radius:6px;
  box-shadow:inset 0 0 0 1px #454d47;
  font-family:"Barlow Semi Condensed",sans-serif;
  font-size:13px; font-weight:500;
  font-variant-numeric:tabular-nums;
  user-select:none; touch-action:none; cursor:grab;
}
.g-green .g-reading {
  height:52px; padding:2px 8px;
  background:#55d795; color:#111714; border-radius:2px;
}
.g-green .g-main {
  display:flex; align-items:center; gap:5px;
  height:32px; white-space:nowrap;
}
.g-green .g-price {
  font-size:32px; line-height:32px; font-weight:700;
  letter-spacing:-.5px;
}
.g-green .g-unit {
  display:flex; flex-direction:column;
  font-size:13px; line-height:13px; font-weight:600;
}
.g-green .g-comparison {
  margin-left:auto;
  font-size:24px; line-height:28px; font-weight:700;
}
.g-green .g-detail {
  display:flex; align-items:baseline; justify-content:space-between;
  height:16px; font-size:13px; line-height:16px; font-weight:600;
}
.g-green .g-hour { font-size:16px; font-weight:700; }
.g-green .g-limit {
  display:flex; align-items:center; justify-content:space-between;
  height:20px; padding:0 8px;
  border-top:1px solid #a5afa7;
  font-size:13px; line-height:18px;
}
.g-green .g-limit-value { font-weight:600; }
</style>
<div class="g-green" lang="es-MX" role="img"
     aria-label="Estimación neta: 9.49 pesos por kilómetro y 285 pesos por hora. Sobre tu meta de 8 pesos por kilómetro. Tu mínimo es 6 pesos por kilómetro.">
  <div class="g-reading">
    <div class="g-main">
      <span class="g-price">$9.49</span>
      <span class="g-unit"><span>/km</span><span>neto</span></span>
      <span class="g-comparison">≥ $8</span>
    </div>
    <div class="g-detail">
      <span>Sobre tu meta</span>
      <span class="g-hour">$285/h</span>
    </div>
  </div>
  <div class="g-limit">
    <span>Tu meta</span><span class="g-limit-value">$8.00/km</span>
  </div>
  <div class="g-limit">
    <span>Tu mínimo</span><span class="g-limit-value">$6.00/km</span>
  </div>
</div>
```

```yellow
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&display=swap');

.g-yellow, .g-yellow * { box-sizing:border-box; }
.g-yellow {
  position:absolute; top:112px; right:12px;
  width:240px; height:100px; padding:4px;
  background:#151a17; color:#f5f5ef;
  border-radius:6px;
  box-shadow:inset 0 0 0 1px #454d47;
  font-family:"Barlow Semi Condensed",sans-serif;
  font-size:13px; font-weight:500;
  font-variant-numeric:tabular-nums;
  user-select:none; touch-action:none; cursor:grab;
}
.g-yellow .g-reading {
  height:52px; padding:2px 8px;
  background:#f2c94c; color:#111714; border-radius:2px;
}
.g-yellow .g-main {
  display:flex; align-items:center; gap:5px;
  height:32px; white-space:nowrap;
}
.g-yellow .g-price {
  font-size:32px; line-height:32px; font-weight:700;
  letter-spacing:-.5px;
}
.g-yellow .g-unit {
  display:flex; flex-direction:column;
  font-size:13px; line-height:13px; font-weight:600;
}
.g-yellow .g-comparison {
  margin-left:auto;
  font-size:24px; line-height:28px; font-weight:700;
}
.g-yellow .g-detail {
  display:flex; align-items:baseline; justify-content:space-between;
  height:16px; font-size:13px; line-height:16px; font-weight:600;
}
.g-yellow .g-hour { font-size:16px; font-weight:700; }
.g-yellow .g-limit {
  display:flex; align-items:center; justify-content:space-between;
  height:20px; padding:0 8px;
  font-size:13px; line-height:18px;
}
.g-yellow .g-upper { border-bottom:1px solid #a5afa7; }
.g-yellow .g-lower { border-top:1px solid #a5afa7; }
.g-yellow .g-limit-value { font-weight:600; }
</style>
<div class="g-yellow" lang="es-MX" role="img"
     aria-label="Estimación neta: 7.16 pesos por kilómetro y 191 pesos por hora. Entre tus límites: desde 6 y menos de 8 pesos por kilómetro.">
  <div class="g-limit g-upper">
    <span>Tu meta</span><span class="g-limit-value">$8.00/km</span>
  </div>
  <div class="g-reading">
    <div class="g-main">
      <span class="g-price">$7.16</span>
      <span class="g-unit"><span>/km</span><span>neto</span></span>
      <span class="g-comparison">$6–8</span>
    </div>
    <div class="g-detail">
      <span>Entre tus límites</span>
      <span class="g-hour">$191/h</span>
    </div>
  </div>
  <div class="g-limit g-lower">
    <span>Tu mínimo</span><span class="g-limit-value">$6.00/km</span>
  </div>
</div>
```

```red
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&display=swap');

.g-red, .g-red * { box-sizing:border-box; }
.g-red {
  position:absolute; top:112px; right:12px;
  width:240px; height:100px; padding:4px;
  background:#151a17; color:#f5f5ef;
  border-radius:6px;
  box-shadow:inset 0 0 0 1px #454d47;
  font-family:"Barlow Semi Condensed",sans-serif;
  font-size:13px; font-weight:500;
  font-variant-numeric:tabular-nums;
  user-select:none; touch-action:none; cursor:grab;
}
.g-red .g-reading {
  height:52px; padding:2px 8px;
  background:#f58273; color:#111714; border-radius:2px;
}
.g-red .g-main {
  display:flex; align-items:center; gap:5px;
  height:32px; white-space:nowrap;
}
.g-red .g-price {
  font-size:32px; line-height:32px; font-weight:700;
  letter-spacing:-.5px;
}
.g-red .g-unit {
  display:flex; flex-direction:column;
  font-size:13px; line-height:13px; font-weight:600;
}
.g-red .g-comparison {
  margin-left:auto;
  font-size:24px; line-height:28px; font-weight:700;
}
.g-red .g-detail {
  display:flex; align-items:baseline; justify-content:space-between;
  height:16px; font-size:13px; line-height:16px; font-weight:600;
}
.g-red .g-hour { font-size:16px; font-weight:700; }
.g-red .g-limit {
  display:flex; align-items:center; justify-content:space-between;
  height:20px; padding:0 8px;
  border-bottom:1px solid #a5afa7;
  font-size:13px; line-height:18px;
}
.g-red .g-limit-value { font-weight:600; }
</style>
<div class="g-red" lang="es-MX" role="img"
     aria-label="Estimación neta: 2.20 pesos por kilómetro y 54 pesos por hora. Bajo tu mínimo de 6 pesos por kilómetro. Tu meta es 8 pesos por kilómetro.">
  <div class="g-limit">
    <span>Tu meta</span><span class="g-limit-value">$8.00/km</span>
  </div>
  <div class="g-limit">
    <span>Tu mínimo</span><span class="g-limit-value">$6.00/km</span>
  </div>
  <div class="g-reading">
    <div class="g-main">
      <span class="g-price">$2.20</span>
      <span class="g-unit"><span>/km</span><span>neto</span></span>
      <span class="g-comparison">&lt; $6</span>
    </div>
    <div class="g-detail">
      <span>Bajo tu mínimo</span>
      <span class="g-hour">$54/h</span>
    </div>
  </div>
</div>
```

```nodata
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&display=swap');

.g-nodata, .g-nodata * { box-sizing:border-box; }
.g-nodata {
  position:absolute; top:112px; right:12px;
  width:240px; height:100px; padding:4px;
  background:#303633; color:#f5f5ef;
  border-radius:6px;
  box-shadow:inset 0 0 0 1px #67716b;
  font-family:"Barlow Semi Condensed",sans-serif;
  font-size:13px; font-weight:500;
  font-variant-numeric:tabular-nums;
  user-select:none; touch-action:none; cursor:grab;
}
.g-nodata .g-message {
  display:flex; flex-direction:column; justify-content:center;
  height:52px; padding:0 8px;
}
.g-nodata .g-title {
  font-size:26px; line-height:28px; font-weight:700;
}
.g-nodata .g-reason {
  font-size:14px; line-height:18px; font-weight:500;
}
.g-nodata .g-limit {
  display:flex; align-items:center; justify-content:space-between;
  height:20px; padding:0 8px;
  border-top:1px dashed #b4bdb7;
  font-size:13px; line-height:18px;
}
.g-nodata .g-limit-value { font-weight:600; }
</style>
<div class="g-nodata" lang="es-MX" role="img"
     aria-label="Sin lectura: falta la tarifa. No hay estimación. Tu meta es 8 pesos por kilómetro y tu mínimo es 6 pesos por kilómetro.">
  <div class="g-message">
    <span class="g-title">Sin lectura</span>
    <span class="g-reason">falta la tarifa</span>
  </div>
  <div class="g-limit">
    <span>Tu meta</span><span class="g-limit-value">$8.00/km</span>
  </div>
  <div class="g-limit">
    <span>Tu mínimo</span><span class="g-limit-value">$6.00/km</span>
  </div>
</div>
```