# Kompara v2, Round 8 (joint confirmation), Opus: re-score from scratch

**Judged:**
- `docs/ux-elevation-spec.md` (23 oct 16:23, §9e rows 9–11, §9f)
- `design-system/ui_kits/android-app-v2/index.html` (16:22, 60 frames)
- `base.css` (16:20)
- `fixture.json`
- `check-fixture.py`
- `rubric-uber-bar.md`

**The checker.** `python3 check-fixture.py` → "checked 47 derived figures, 22 stale patterns → OK" (exit 0).

**What I verified by hand:**
- E5: $12,608 × 0.94 = $11,852; $8,432 × 0.94 = $7,926; $9,582 − $7,926 = $1,656.
- F2: the cohort deciles put $149 at 70 + (149 − 142)/(151 − 142) × 10 = 77.8 → 78 %.
- E2: 5–11 oct = $6,247 − $1,500 = $4,747.
- E8: $762 − $214.29 = $548.
- B1: $648 + $113.90 = $762.
- October payments: Uber $1,100 + $5,028 + $5,000 + $1,480 = $12,608; DiDi $490 + $3,372 + $3,100 + $1,470 = $8,432.

**The checker test.** I also tested it. I removed "$762", "$4,980", "$12,170", "$1,026", "$4,747", "$1,417", "$9.13", "$10.34" and "$149" from every frame of a scratch copy, and it caught only "$548" and "$1.21". The other figures are also printed in the fixture box at the top of `index.html`, which satisfies a presence check (R8-02).

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **9** | Each root answers one question with one hero. B1: "$762 · 6 viajes tomados" + one word-only row. E1: "$4,980 · 62 viajes tomados". F1: "$1.21 más por km en DiDi". E8 holds the standing until the week closes. E3b states why there is no net instead of inventing one. |
| 2 | One primary action | **9** | One verb per screen, at the thumb: B2 "Encender lector", G4 "Quitar restricción de batería", F5 "Confirmar con mi resumen de Uber", H2c "Arreglar el pago en Google Play", H5d "Guardar corrección". H2d asks nothing ("no tienes que hacer nada"). |
| 3 | Navigation that disappears | **9** | 3 tabs + avatar, depth ≤ 2. Selectors are 48dp pills ("Uber ▾ CDMX ▾"). Status pill and Semana/Mes now meet 48dp (`.topbar .pill{height:48px}`, `.seg div{padding:14px 0;line-height:20px}`). E6 lists the reader-less period by name. |
| 4 | Numbers discipline | **8** | Every live chip, day, week, month and standing figure reconciles, and the root rule holds (B1's offer rows carry no rate). Residual: the 14–20 sep import shows "Viajes 36" in H5 but "29 viajes" in E3b and in `fixture.json`. H5 and H5b reuse the same "$5,340 · 36 · 26 h" for two different weeks. The checker can't see either (R8-02, R8-03). |
| 5 | Copy | **8** | One vocabulary ("aprox." only, sentence case). Tight `tú`. A3 now explains Android's "control total" dialog. Residual: E3b "IMSS de septiembre · Este pago sí cuenta" asserts an IMSS outcome. H2b "Google Play cobra hoy" sits under H2's "Seguir con Premium **después**". E5 says "Para el IMSS cuentan $11,852" twice. |
| 6 | States designed | **9** | 60 frames, including the import-only month (E3b), pago en proceso (H2d), Corregir (H5d), loading (E7), offline (E7b), the open week (E8), operable cancellation (D2 "¿Se canceló el viaje? Quitar de mi día") and the drawn control-total dialog (A3). One drawn state is missing: E7b never shows its "Sin conexión" card. |
| 7 | Time-to-value | **8** | A true verdict on screen 1 (A1). The permission at step 3 with the scary system dialog pre-explained (A3: "Es el aviso estándar de accesibilidad: Kompara solo lee, nunca toca nada"). The battery step only when detected (A4), costs after the cliff (A5), the account optional (A6). Not measured on a device. |
| 8 | Consistency and feel | **8** | The mockups are one hand and the verdict green now passes contrast (#187a36, 5.42:1). Residual: spec §5.1 still describes offer rows with a rate. The share preview, which "is the PNG that gets sent", shows "Horas en línea 24 h" with "Ocultar montos y horas" switched on (R8-04). The design-system README and design principles still say verde `#1B8A3A`. |
| | **Total** | **68 / 80** | **PASS.** Every criterion is ≥ 8 and the total is 68. The margin is zero, the same as Round 7: the fixes removed old defects and the new frames brought a few new ones. This is a pass for the design as specified; "comparable in use and feel" still needs the device and driver evidence in §6. |

---

## 2. Hard fails

- **Auto-action proposed: none.**
  - Spec §6: "Drag only… Tap does nothing… Long-press does nothing either."
  - A2: "Kompara solo lee: nunca acepta ni rechaza por ti."
  - A3: "Kompara solo lee, nunca toca nada."
- **Platform logos: none.** Platform names appear as plain text only. A1 and G2 use a neutral sample; the C1–C6 host card is "internal only".
- **A driving-time surface failing the 1-second glance: none.**
  - The chip keeps a fixed 184dp footprint with glyph, colour and one rate.
  - C5's gris now carries no peso figure ("Falta el tiempo · sin semáforo · ve el detalle al estacionarte").
  - C6 lasts 5 s and lets touches pass through.
- **A paywall that blanks the free product: none.**
  - E2 keeps "Te quedaron esa semana · aprox. $4,747" visible and its breakdown is a flat placeholder, not a blur.
  - F3 masks only the city average.
  - F4 and F5 show no purchase prompt.
- **An onboarding screen with more than one ask: none.**
  - A1–A7 each have one primary.
  - A3's drawn dialog is an illustration, not a second ask.

---

## 3. Round 7 defects: status

| R7 id | Status | Evidence |
|---|---|---|
| R7-01 (spec edited by hand behind the mockups) | **partly** | **Fixed:** §5.9 now reads "Chips **Por km · Por viaje · Por hora**, in that order everywhere". §5.1 hero is "6 viajes tomados (the only other number in the hero; hours live in Tu día)". §5.6 carries E1's "Con lo que leyó el lector · tu resumen de Uber dice un poco más" and the E8 state. The appendix says "B4 Hoy lunes 19 oct 7:30". **Not fixed:** §5.1 item 4 still specifies offer rows as "verdict glyph · `$9.49/km neto` · '14:32 · La tomaste · aprox.'", but B1's rows are now "✓ Verde · la tomaste 14:32 · aprox." with no rate. §5.6 and E7b's caption promise an offline city card saying "Sin conexión" that the E7b frame doesn't draw. §5.6 and E2 say 5–11 oct leaves the window on "lunes 9 nov"; it leaves on lunes 2 nov. **The checker doesn't close this:** it tests that a figure appears somewhere, the fixture box supplies most figures, and it compares no prose (R8-02). |
| R7-02 (E5 arithmetic) | fixed | "Te pagó $12,608 · Para el IMSS cuentan **$11,852** (× 0.94, ejemplo…)"; DiDi "**$7,926**… faltan **$1,656**". |
| R7-03 (share card overloaded, hours not hideable) | fixed, with a new defect | The label is now "Uber · CDMX", there is one sentence and one muted line, and the switch reads "**Ocultar montos y horas**". But the switch is on while the preview still shows "Horas en línea 24 h" (R8-04). |
| R7-04 (44 px targets) | fixed | `.topbar .pill{height:48px}`; `.seg div{padding:14px 0;line-height:20px}` gives 48 px. |
| R7-05 (F5 cohort size) | fixed | F5: "5 – 11 oct · por km: Uber no reporta km · el tamaño del grupo sale al confirmar". |
| R7-06 (offline wording) | fixed in E7; new gap in E7b | E7: "Cargando tus números…" with no network wording. E7b's frame has no "Sin conexión" text at all (`grep` finds the string only in the caption). |
| R7-07 (E3 clock) | fixed | E3's status bar reads 21:10, matching "19 oct · hoy · 9 viajes". |
| R7-08 (hard-coded citation) | fixed as a rule; residual mismatch | §5.8 Rules: "the official citations (DOF note, IMSS and CONASAMI links) are served from remote config… verified in the B-038 legal check". But `fixture.json` labels the same citation "DOF nota 5777156 (**ejemplo, regla pendiente**)", while E4, the launch screen, prints it as fact ("regla: dof.gob.mx, nota 5777156") (R8-05). |
| R7-09 (✓ for selection) | fixed | E6: "12 – 18 oct ●". |
| R7-10 (B1 numbers) | fixed | B1 rows: "✓ Verde · la tomaste 14:32 · aprox.", "✕ Rojo · se fue sin tomar 12:10", with no rate. The root rule is "the answer plus at most two supporting measures". |

---

## 4. Remaining defects (ranked by damage)

**Counts:** 0 critical · 0 high · 5 medium · 9 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| R8-01 | Spec §5.1, §5.6; E7b, E2 captions | medium | The spec and the captions still disagree with the frames in three places. | §5.1 item 4: "three rows, one number each — verdict glyph · `$9.49/km neto`…" vs B1 "✓ Verde · la tomaste 14:32 · aprox." (B1's own caption also still says "one number each"). §5.6: "offline (E7b)… only the city card says 'Sin conexión' with one retry", but the E7b frame draws the standing card unchanged. §5.6 and the E2 caption: "5 – 11 oct as seen on lunes 9 nov, the first Monday it leaves the last-4-weeks window"; on lunes 2 nov the window is 2–8 nov, 26 oct–1 nov, 19–25 oct and 12–18 oct, so it left a week earlier. | Rewrite §5.1 item 4 to match B1 (colour · outcome · time, no rate). Draw E7b's city card as "Sin conexión · La comparación con tu ciudad la cargamos cuando tengas datos · Reintentar" (or say the cached standing stays). Date E2 "lunes 2 nov" or say "hace 5 semanas" without the "first Monday" claim. |
| R8-02 | `check-fixture.py` | medium | The "equality check" is a presence check that the fixture box itself satisfies, so it can't detect a figure drifting out of a frame. | The checker passes `sv in idx` where `idx` is the whole page. In a scratch copy I removed "$762", "$4,980", "$12,170", "$1,026", "$4,747", "$1,417", "$9.13", "$10.34", "$149" from all 60 frames; it still reported those as present and flagged only "$548" and "$1.21". It also exempts eight stale strings from the spec entirely, and never compares prose. | Strip the fixture box and captions before matching. Assert each figure inside its named frame (a map like `{'B1': ['$762','6 viajes tomados'], …}`). Add the import, plan and date figures (R8-03, H2b's "21 días", H2c's "16 de diciembre"). Diff spec §5 strings against frame strings. |
| R8-03 | H5, H5b, E3b, `fixture.json` | medium | The imported weeks contradict each other and the fixture, outside the checker's reach. | H5 (14–20 sep): "Viajes **36** · Horas en línea 26 h · Comisión 24 %". E3b (same week): "$5,340 · **29 viajes** · solo 14 – 20 sep". `fixture.json` "2026-09-14": `"trips": 29`. H5b (5–11 oct PDF) repeats the identical "$5,340 · 36 · 26 h". `fixture.json` `h5b_partial_import.uber_week` is "2026-10-12" while H5b shows "5 – 11 oct", and H5b's "Kilómetros 404" vs the fixture's 5–11 Uber `"km": 405.0`. | Give each imported week its own values in `fixture.json` (14–20 sep: 29 viajes; a distinct 5–11 oct PDF, e.g. $5,180 · 34 viajes · 25 h, with reader km 405.0 for 31 trips). Point `h5b_partial_import` at 2026-10-05. Derive H5, H5b and E3b from it and add them to the checker. |
| R8-04 | H3 | medium | The share preview, promised to be the exact PNG, ignores its own privacy switch. | The switch is on (`class="sw"`): "Ocultar montos y horas · Tu ganancia y tus horas no salen en la tarjeta". The preview above it still shows "Horas en línea **24 h**". | With the switch on, replace the fact with "Viajes en Uber 34" only (or "Semana confirmada con tu resumen"). Show "Horas en línea 24 h" only when the switch is off. |
| R8-05 | E4 vs `fixture.json` | medium | The screen that ships first prints a legal citation as fact that the fixture marks as a placeholder. | E4: "¿Cómo funciona? (regla: dof.gob.mx, **nota 5777156** · imss.gob.mx/vigencia)". `fixture.json`: `"source": "CONASAMI tabla 2026 · DOF nota 5777156 (ejemplo, regla pendiente)"`. | Until B-038 confirms the note, E4 links only to imss.gob.mx/vigencia ("Consultar mi vigencia en el IMSS"). Show the DOF reference after legal verification, served from config as §5.8 requires. |
| R8-06 | E3b vs E4 | low | The import-only month asserts an IMSS outcome that E4 says Kompara can't know. | E3b: "IMSS de septiembre · **Este pago sí cuenta** · cómo consultar tu vigencia". E4: "El monto que cuenta para el IMSS y el monto mínimo los define la ley, no Kompara". | "IMSS de septiembre · Este pago está en tu mes · cómo consultar tu vigencia". |
| R8-07 | H2 vs H2b | low | The plan row says "later" and the offer screen charges today, against a driver who still has 21 free days. | H2: "Seguir con Premium **después** · $79 al mes · te mostramos las condiciones antes de cobrar". H2b: "Google Play **cobra hoy** y luego cada mes · Tus 21 días gratis que faltan no se pierden: se suman al final, cuando canceles". | Rename the row "Suscribirme ahora · $79 al mes". In H2b, lead with the trade-off: "Si te suscribes hoy, Google Play cobra hoy; tus 21 días gratis se suman al final". Offer the reminder "Avísame el 9 nov" as a text action. |
| R8-08 | H5d vs H5 | low | The correction screen belongs to a different import than the one that links to it. | H5 (14–20 sep · $5,340 · 36 viajes) has "¿Un dato se leyó mal? Corregir". H5d shows "Viajes · **12 – 18 oct** · 34 · Te pagó · se leyó $5,062 · Horas en línea 24 h". | Draw H5d for the 14–20 sep import, or caption it as reached from the 12–18 oct statement review. |
| R8-09 | E3b; spec §12 period accounting | low | September's month view skips the car cost for 28–30 sep without saying so, though month views "allocate the car cost by day". | G5: "Se resta desde lunes 28 sep · … En el mes: $214 por día". E3b shows no car line for 28–30 sep. | Add one muted line: "Lo del coche de 28 – 30 sep ($643) no se resta: sin km no hay neto." |
| R8-10 | E5 | low | The post-gate card repeats its headline and applies the tip note to one app only. | Uber: "Para el IMSS cuentan $11,852 · aprox. Te pagó $12,608 · Para el IMSS cuentan $11,852 (× 0.94, ejemplo; sin propinas: el lector no las ve)". DiDi has no tip note. | Headline once ("Para el IMSS cuentan $11,852"), one detail line "Te pagó $12,608 × 0.94 (ejemplo)", and the tip note once under both apps. |
| R8-11 | E7b | low | The offline frame is indistinguishable from the online one except for the status bar. | E7b reproduces E1 entirely, including "Comparar esa semana · Por viaje, te pagan más que al 78 %…", with no offline marker. | See R8-01: draw the city card's offline state, or a "Sin conexión · lo de tu ciudad es del lunes" label on the cached standing. |
| R8-12 | `design-system/README.md`, `docs/design-principles.md` vs `base.css`, spec §12 | low | The verdict green changed in the CSS and the spec but not in the design-system source of truth. | `base.css`: `--verde:#187a36`. Spec: "the verdict green is **#187a36**". README: "Verde `#1B8A3A`". Design principles: "Verde `#1B8A3A` on white". | Update the token in `design-system/tokens`, the README, the design principles and Compose `VerdictLevel.brandColor`. That's also the moment to delete `overlay/VerdictColors.kt` (spec §4). |
| R8-13 | H1 | low | One es-MX slip on the paywall. | "cada semana **compartes a Kompara** el PDF que te manda Uber". | "cada semana le compartes a Kompara el PDF que te manda Uber" (or "compartes con Kompara"). |
| R8-14 | Spec §5.6, E2 caption, fixture box | low | Date arithmetic about the free window is wrong in three places. | "5 – 11 oct as seen on lunes 9 nov, **the first Monday it leaves** the last-4-weeks window". It leaves on lunes 2 nov. | Fix the claim, or move E2 to "lunes 2 nov" (where "hace 4 semanas" applies). Add the window rule to the checker. |

---

## 5. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Está leyendo; hoy llevo unos $762 de 6 viajes; mi mejor hora fue de 9 a 10, y la última verde la tomé."
- **Ganancias (E1):** "La semana pasada me quedaron unos $4,980 ya con la renta; mi resumen de Uber dice un poquito más."
- **Comparar (F1, Tus apps):** "DiDi me dejó $1.21 más por kilómetro que Uber."
- **Chip verde (C1):** "Verde, $9.49 el kilómetro: pasa mi mínimo."
- **Chip amarillo (C2):** "Amarillo, $7.16: entre mi mínimo y mi rojo; piénsale."
- **Chip rojo (C3):** "Rojo, $2.20: no llega."
- **Chip gris (C4 / C5):** "Gris: le falta un dato para el semáforo; la decido yo y veo el detalle al estacionarme."

---

## 6. What only evidence can settle

| Claim | Test that settles it |
|---|---|
| The chip reads correctly in one second | 1-second exposures at 60 cm on a mount, day and night, 10–12 CDMX drivers including ≥ 3 with colour-vision deficiency. Pass if take/pass decisions are accurate against their stated floor and gris is never read as "no" (R1). |
| The new verde (#187a36) stays distinct from rojo | The same test, under night glare and for colour-vision-deficient drivers, with the ✓ / ✕ glyphs. |
| "neto aprox." earns its width; the second rate earns its line | Chip variants in the 1-second test, measuring accuracy and time (R11, R3). |
| The reader is fast and correct | On-device runs of the silent screenshot + OCR lane (TD-040): parse→chip ≤ 150 ms, the fare/km/time read rate on real Uber and DiDi cards, and no overlap with the host's buttons. |
| Install → "preparado" is quick, and A3's "control total" explanation cuts drop-off | Closed beta on Xiaomi, Samsung and Motorola: median ≤ 75 s; the abandonment rate at the system dialog with vs without A3's drawn mock. |
| The reader survives a shift | Full-shift soak on restricted-battery phones: incidents per shift after A4/G4. |
| Drivers understand the money words | A 10-driver comprehension test of "Te quedan · aprox.", "gris", "por viaje", "en turno", "según el lector / según tu resumen". |
| Drivers keep a weekly PDF routine | The share of active drivers who import their Uber PDF most weeks. The standing, the share card and Premium's only new datum all depend on it ("cada semana le compartes a Kompara el PDF"). |
| Typical costs are safe to start with | Beta: verdict flip rate when real costs replace typical ones; < 5 % means safe (R2). |
| Premium is worth $79 | A price test at $59 / $79 / $99 when it adds one city average + history detail + the PDF ("hoy es el único dato nuevo para ti"). |
| The subscription model passes Play and consumer review | Play policy review and a Profeco-style read of H2b: charging today while appending 21 server-granted days "cuando canceles". |
| The IMSS rule and citations are right | Legal memo (B-038): the statutory base, factor and tips; the 2026 minimum $9,582; the DOF note number and links. E5 and the DOF citation in E4 wait for it. |
| Play accepts the disclosure | A pre-flight of A2's full-screen-image wording, the accessibility declaration, the data-safety form and the out-of-app deletion route (kompara.mx/borrar). |
| Three tabs are findable | Tree test and first click: "¿Dónde ves si te pagan más que a otros de tu ciudad?", "¿Dónde ves lo del IMSS?" (R9). |

---

## 7. Verdict

**PASS, 68/80.** As specified, this version meets "comparable in use and feel to the Uber Driver app" for its job: status-first home, one hero per screen, an honest chip, and money that is labelled with its source and reconciles. There are no hard fails and no critical or high defects, but the margin is zero.

**Biggest residual risk.** The promises that matter while driving are still unproven on a real mounted phone: the 1-second read, OCR speed and accuracy, and surviving a shift. Close behind is a growth risk: the standing, the share card and Premium's one new figure all depend on drivers importing their Uber PDF every week.

**Build first.** The chip and the readiness model, on device:
- engine changes: gris never red, no unit swap, preparado / funcionando
- the silent-screenshot lane
- the C1–C6 states

Run them through the 1-second mounted test. In parallel, make `check-fixture.py` frame-scoped so the spec and mockups stop drifting before any Ganancias or Comparar screen is built.
