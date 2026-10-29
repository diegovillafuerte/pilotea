# Round 15 chip proposal — Codex Astra (2026-10-27)

_Verbatim from codex exec (gpt-6-astra). Brief: `../blind-test/r15-brief.md`._

Both variants fix the primary cue, rate and unit in place; only the small ruler pointer moves.  
A — **Regla fija** retains the full-width band and connects the two thresholds into a visible ruler.  
B — **Regla lateral** puts the ruler beside the rate, reducing the plate to 92px tall.  
Rates use 34px in A and 32px in B; removing hourly income gives the host fare more breathing room.  
Solid labelled limits remain visible without a fare; “Sin lectura” removes the pointer and all verdict colour.

```A-green
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:429px;right:12px;width:240px;height:100px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:46px;display:grid;grid-template-columns:18px 88px 1fr;gap:8px;align-items:center;padding:0 14px;background:var(--g-band);color:var(--g-text)}
.g-a-cue{display:block;width:18px;height:18px;fill:currentColor}
.g-a-rate{font-size:34px;line-height:40px;font-weight:600;white-space:nowrap}
.g-a-unit{font-size:14px;line-height:18px;font-weight:500;white-space:nowrap}
.g-a-label{position:absolute;left:14px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-a-meta{top:55px}.g-a-min{top:75px}
.g-a-rule{position:absolute;left:130px;width:88px;height:2px;background:#C6C7C7}
.g-a-upper{top:63px}.g-a-lower{top:83px}
.g-a-spine{position:absolute;left:217px;top:54px;width:2px;height:40px;background:#858687}
.g-a-pointer{position:absolute;left:212px;top:calc(var(--g-y) - 6px);width:12px;height:12px;fill:#fff}
</style>
<div class="g-a" style="--g-band:#187A36;--g-text:#fff;--g-y:54px" role="img" aria-label="9.49 pesos netos por kilómetro. Por encima de tu meta de 8. Tu mínimo es 6.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 18 18" aria-hidden="true"><path d="M9 2 18 17H0Z"/></svg>
    <b class="g-a-rate">$9.49</b>
    <span class="g-a-unit">/km neto</span>
  </div>
  <span class="g-a-label g-a-meta">tu meta $8</span>
  <span class="g-a-label g-a-min">tu mínimo $6</span>
  <i class="g-a-spine" aria-hidden="true"></i>
  <i class="g-a-rule g-a-upper" aria-hidden="true"></i>
  <i class="g-a-rule g-a-lower" aria-hidden="true"></i>
  <svg class="g-a-pointer" viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1 11 6 1 11Z"/></svg>
</div>
```

```A-yellow
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:429px;right:12px;width:240px;height:100px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:46px;display:grid;grid-template-columns:18px 88px 1fr;gap:8px;align-items:center;padding:0 14px;background:var(--g-band);color:var(--g-text)}
.g-a-cue{display:block;width:18px;height:18px;fill:currentColor}
.g-a-rate{font-size:34px;line-height:40px;font-weight:600;white-space:nowrap}
.g-a-unit{font-size:14px;line-height:18px;font-weight:500;white-space:nowrap}
.g-a-label{position:absolute;left:14px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-a-meta{top:55px}.g-a-min{top:75px}
.g-a-rule{position:absolute;left:130px;width:88px;height:2px;background:#C6C7C7}
.g-a-upper{top:63px}.g-a-lower{top:83px}
.g-a-spine{position:absolute;left:217px;top:54px;width:2px;height:40px;background:#858687}
.g-a-pointer{position:absolute;left:212px;top:calc(var(--g-y) - 6px);width:12px;height:12px;fill:#fff}
</style>
<div class="g-a" style="--g-band:#F2B705;--g-text:#1A1300;--g-y:74px" role="img" aria-label="7.16 pesos netos por kilómetro. Entre tu mínimo de 6 y tu meta de 8.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 18 18" aria-hidden="true"><path d="M9 0 18 9 9 18 0 9Z"/></svg>
    <b class="g-a-rate">$7.16</b>
    <span class="g-a-unit">/km neto</span>
  </div>
  <span class="g-a-label g-a-meta">tu meta $8</span>
  <span class="g-a-label g-a-min">tu mínimo $6</span>
  <i class="g-a-spine" aria-hidden="true"></i>
  <i class="g-a-rule g-a-upper" aria-hidden="true"></i>
  <i class="g-a-rule g-a-lower" aria-hidden="true"></i>
  <svg class="g-a-pointer" viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1 11 6 1 11Z"/></svg>
</div>
```

```A-red
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:429px;right:12px;width:240px;height:100px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-a-band{position:absolute;inset:0 0 auto;height:46px;display:grid;grid-template-columns:18px 88px 1fr;gap:8px;align-items:center;padding:0 14px;background:var(--g-band);color:var(--g-text)}
.g-a-cue{display:block;width:18px;height:18px;fill:currentColor}
.g-a-rate{font-size:34px;line-height:40px;font-weight:600;white-space:nowrap}
.g-a-unit{font-size:14px;line-height:18px;font-weight:500;white-space:nowrap}
.g-a-label{position:absolute;left:14px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-a-meta{top:55px}.g-a-min{top:75px}
.g-a-rule{position:absolute;left:130px;width:88px;height:2px;background:#C6C7C7}
.g-a-upper{top:63px}.g-a-lower{top:83px}
.g-a-spine{position:absolute;left:217px;top:54px;width:2px;height:40px;background:#858687}
.g-a-pointer{position:absolute;left:212px;top:calc(var(--g-y) - 6px);width:12px;height:12px;fill:#fff}
</style>
<div class="g-a" style="--g-band:#D32F2F;--g-text:#fff;--g-y:94px" role="img" aria-label="2.20 pesos netos por kilómetro. Por debajo de tu mínimo de 6. Tu meta es 8.">
  <div class="g-a-band">
    <svg class="g-a-cue" viewBox="0 0 18 18" aria-hidden="true"><path d="M0 1H18L9 16Z"/></svg>
    <b class="g-a-rate">$2.20</b>
    <span class="g-a-unit">/km neto</span>
  </div>
  <span class="g-a-label g-a-meta">tu meta $8</span>
  <span class="g-a-label g-a-min">tu mínimo $6</span>
  <i class="g-a-spine" aria-hidden="true"></i>
  <i class="g-a-rule g-a-upper" aria-hidden="true"></i>
  <i class="g-a-rule g-a-lower" aria-hidden="true"></i>
  <svg class="g-a-pointer" viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1 11 6 1 11Z"/></svg>
</div>
```

```A-nodata
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-a,.g-a *{box-sizing:border-box}
.g-a{position:absolute;top:429px;right:12px;width:240px;height:100px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-a-missing{position:absolute;left:40px;top:3px;font-size:22px;line-height:26px;font-weight:600;white-space:nowrap}
.g-a-reason{position:absolute;left:40px;top:28px;font-size:14px;line-height:16px;font-weight:500;color:#C6C7C7;white-space:nowrap}
.g-a-label{position:absolute;left:14px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-a-meta{top:55px}.g-a-min{top:75px}
.g-a-rule{position:absolute;left:130px;width:88px;height:2px;background:#C6C7C7}
.g-a-upper{top:63px}.g-a-lower{top:83px}
.g-a-spine{position:absolute;left:217px;top:54px;width:2px;height:40px;background:#858687}
</style>
<div class="g-a" role="img" aria-label="Sin lectura: falta la tarifa. Tu meta sigue siendo 8 pesos netos por kilómetro y tu mínimo 6.">
  <b class="g-a-missing">Sin lectura</b>
  <span class="g-a-reason">falta la tarifa</span>
  <span class="g-a-label g-a-meta">tu meta $8</span>
  <span class="g-a-label g-a-min">tu mínimo $6</span>
  <i class="g-a-spine" aria-hidden="true"></i>
  <i class="g-a-rule g-a-upper" aria-hidden="true"></i>
  <i class="g-a-rule g-a-lower" aria-hidden="true"></i>
</div>
```

```B-green
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:437px;right:12px;width:240px;height:92px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-b-band{position:absolute;left:0;top:0;width:124px;height:92px;background:var(--g-band);color:var(--g-text)}
.g-b-cue{position:absolute;left:11px;top:31px;width:16px;height:16px;fill:currentColor}
.g-b-rate{position:absolute;left:34px;top:19px;font-size:32px;line-height:38px;font-weight:600;white-space:nowrap}
.g-b-unit{position:absolute;left:34px;top:55px;font-size:14px;line-height:18px;font-weight:500;white-space:nowrap}
.g-b-label{position:absolute;left:154px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-b-meta{top:24px}.g-b-min{top:52px}
.g-b-rule{position:absolute;left:128px;width:22px;height:2px;background:#C6C7C7}
.g-b-upper{top:32px}.g-b-lower{top:60px}
.g-b-spine{position:absolute;left:137px;top:17px;width:2px;height:60px;background:#858687}
.g-b-pointer{position:absolute;left:132px;top:calc(var(--g-y) - 6px);width:12px;height:12px;fill:#fff}
</style>
<div class="g-b" style="--g-band:#187A36;--g-text:#fff;--g-y:17px" role="img" aria-label="9.49 pesos netos por kilómetro. Por encima de tu meta de 8. Tu mínimo es 6.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 18 18" aria-hidden="true"><path d="M9 2 18 17H0Z"/></svg>
    <b class="g-b-rate">$9.49</b>
    <span class="g-b-unit">/km neto</span>
  </div>
  <span class="g-b-label g-b-meta">tu meta $8</span>
  <span class="g-b-label g-b-min">tu mínimo $6</span>
  <i class="g-b-spine" aria-hidden="true"></i>
  <i class="g-b-rule g-b-upper" aria-hidden="true"></i>
  <i class="g-b-rule g-b-lower" aria-hidden="true"></i>
  <svg class="g-b-pointer" viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1 11 6 1 11Z"/></svg>
</div>
```

```B-yellow
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:437px;right:12px;width:240px;height:92px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-b-band{position:absolute;left:0;top:0;width:124px;height:92px;background:var(--g-band);color:var(--g-text)}
.g-b-cue{position:absolute;left:11px;top:31px;width:16px;height:16px;fill:currentColor}
.g-b-rate{position:absolute;left:34px;top:19px;font-size:32px;line-height:38px;font-weight:600;white-space:nowrap}
.g-b-unit{position:absolute;left:34px;top:55px;font-size:14px;line-height:18px;font-weight:500;white-space:nowrap}
.g-b-label{position:absolute;left:154px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-b-meta{top:24px}.g-b-min{top:52px}
.g-b-rule{position:absolute;left:128px;width:22px;height:2px;background:#C6C7C7}
.g-b-upper{top:32px}.g-b-lower{top:60px}
.g-b-spine{position:absolute;left:137px;top:17px;width:2px;height:60px;background:#858687}
.g-b-pointer{position:absolute;left:132px;top:calc(var(--g-y) - 6px);width:12px;height:12px;fill:#fff}
</style>
<div class="g-b" style="--g-band:#F2B705;--g-text:#1A1300;--g-y:47px" role="img" aria-label="7.16 pesos netos por kilómetro. Entre tu mínimo de 6 y tu meta de 8.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 18 18" aria-hidden="true"><path d="M9 0 18 9 9 18 0 9Z"/></svg>
    <b class="g-b-rate">$7.16</b>
    <span class="g-b-unit">/km neto</span>
  </div>
  <span class="g-b-label g-b-meta">tu meta $8</span>
  <span class="g-b-label g-b-min">tu mínimo $6</span>
  <i class="g-b-spine" aria-hidden="true"></i>
  <i class="g-b-rule g-b-upper" aria-hidden="true"></i>
  <i class="g-b-rule g-b-lower" aria-hidden="true"></i>
  <svg class="g-b-pointer" viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1 11 6 1 11Z"/></svg>
</div>
```

```B-red
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:437px;right:12px;width:240px;height:92px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-b-band{position:absolute;left:0;top:0;width:124px;height:92px;background:var(--g-band);color:var(--g-text)}
.g-b-cue{position:absolute;left:11px;top:31px;width:16px;height:16px;fill:currentColor}
.g-b-rate{position:absolute;left:34px;top:19px;font-size:32px;line-height:38px;font-weight:600;white-space:nowrap}
.g-b-unit{position:absolute;left:34px;top:55px;font-size:14px;line-height:18px;font-weight:500;white-space:nowrap}
.g-b-label{position:absolute;left:154px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-b-meta{top:24px}.g-b-min{top:52px}
.g-b-rule{position:absolute;left:128px;width:22px;height:2px;background:#C6C7C7}
.g-b-upper{top:32px}.g-b-lower{top:60px}
.g-b-spine{position:absolute;left:137px;top:17px;width:2px;height:60px;background:#858687}
.g-b-pointer{position:absolute;left:132px;top:calc(var(--g-y) - 6px);width:12px;height:12px;fill:#fff}
</style>
<div class="g-b" style="--g-band:#D32F2F;--g-text:#fff;--g-y:77px" role="img" aria-label="2.20 pesos netos por kilómetro. Por debajo de tu mínimo de 6. Tu meta es 8.">
  <div class="g-b-band">
    <svg class="g-b-cue" viewBox="0 0 18 18" aria-hidden="true"><path d="M0 1H18L9 16Z"/></svg>
    <b class="g-b-rate">$2.20</b>
    <span class="g-b-unit">/km neto</span>
  </div>
  <span class="g-b-label g-b-meta">tu meta $8</span>
  <span class="g-b-label g-b-min">tu mínimo $6</span>
  <i class="g-b-spine" aria-hidden="true"></i>
  <i class="g-b-rule g-b-upper" aria-hidden="true"></i>
  <i class="g-b-rule g-b-lower" aria-hidden="true"></i>
  <svg class="g-b-pointer" viewBox="0 0 12 12" aria-hidden="true"><path d="M1 1 11 6 1 11Z"/></svg>
</div>
```

```B-nodata
<style>
@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600&display=swap');
.g-b,.g-b *{box-sizing:border-box}
.g-b{position:absolute;top:437px;right:12px;width:240px;height:92px;overflow:hidden;border-radius:14px;background:#0E0F10;color:#fff;font-family:'Barlow Semi Condensed','Arial Narrow',sans-serif;font-variant-numeric:tabular-nums;box-shadow:0 5px 12px #0003}
.g-b-missing{position:absolute;left:34px;top:19px;font-size:20px;line-height:38px;font-weight:600;white-space:nowrap}
.g-b-reason{position:absolute;left:34px;top:55px;font-size:14px;line-height:18px;font-weight:500;color:#C6C7C7;white-space:nowrap}
.g-b-label{position:absolute;left:154px;font-size:15px;line-height:18px;font-weight:600;color:#DADBD9;white-space:nowrap}
.g-b-meta{top:24px}.g-b-min{top:52px}
.g-b-rule{position:absolute;left:128px;width:22px;height:2px;background:#C6C7C7}
.g-b-upper{top:32px}.g-b-lower{top:60px}
.g-b-spine{position:absolute;left:137px;top:17px;width:2px;height:60px;background:#858687}
</style>
<div class="g-b" role="img" aria-label="Sin lectura: falta la tarifa. Tu meta sigue siendo 8 pesos netos por kilómetro y tu mínimo 6.">
  <b class="g-b-missing">Sin lectura</b>
  <span class="g-b-reason">falta la tarifa</span>
  <span class="g-b-label g-b-meta">tu meta $8</span>
  <span class="g-b-label g-b-min">tu mínimo $6</span>
  <i class="g-b-spine" aria-hidden="true"></i>
  <i class="g-b-rule g-b-upper" aria-hidden="true"></i>
  <i class="g-b-rule g-b-lower" aria-hidden="true"></i>
</div>
```