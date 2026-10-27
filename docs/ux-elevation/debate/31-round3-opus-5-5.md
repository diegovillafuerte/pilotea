# Kompara v2: Round 3 (Opus), scoring the result against the Uber-class bar

**What I judged:**
- `docs/ux-elevation-spec.md` (mtime 22:08)
- `design-system/ui_kits/android-app-v2/index.html` (mtime 22:10; it changed while I was reading, so every string below was re-checked against the 22:10 version)
- `rubric-uber-bar.md`

I judged the delivered artifacts, not the intentions behind them. Screen ids follow the mockup captions (A1–H4).

Arithmetic checks use the spec's own defaults: costs are **$2.84/km** (A2/G3: "$24.50 ÷ 12 + $0.80") and the floors are **verde ≥ $8.00/km, rojo < $6.00/km** (G2, C5, spec §6).

---

## 1. Scorecard

| # | Criterion | Score | Justification |
|---|---|---|---|
| 1 | One job per screen | **8** | B1, D1, E1, G4 each have one clear hero. E1 adds a second, competing hero in its "Le ganas al 78 % en Uber · CDMX" card. F2 stacks a standing sentence, "Top 22 %" and a Tú-vs-Promedio pair. |
| 2 | One primary action | **8** | B2's single "Encender" is a real GO. A2 has three exits that do nearly the same thing ("Continuar" / "Usar el valor típico" / "Ya sé mi costo por km"). G2 has no Guardar at all ("Se guarda solo…"), against spec §5.14. |
| 3 | Navigation that disappears | **8** | 3 tabs + avatar, depth ≤ 2. But F1–F3 stack segments + week stepper + metric chips + "Ciudad · CDMX · App · Uber ›" around one answer, and D1 puts two "‹" side by side. |
| 4 | Numbers discipline | **6** | The example arithmetic is wrong in C1, C2, C3 and D2 (C2's "amarillo $7.10" is rojo by its own inputs). The same week shows $4,980 (E1) and $6,480 (E3). B1, F1 and F2 each put 6–8 numbers in the first viewport against the spec's own "≤ 3". |
| 5 | Copy | **7** | Natural es-MX `tú` overall. But the vocabulary drifts: "Gris" vs "Sin color"; "lo típico" vs "valor inicial"; "Te quedan / Te quedaron hoy" on the same day; "Lector encendido" outside the state set. H1 sells "Retenciones", which the spec withholds. H1's "Al terminar, tú decides" replaces the decided "No habrá ningún cobro al terminar". |
| 6 | States designed | **8** | B2, B3, B4, C4, C6, E2, F3 and G4 are strong. Undesigned: Hoy "en pausa"; the no-cohort Comparar state; no-account trial; Tu plan; Importar review/errors; when "funcionando" expires. |
| 7 | Time-to-value | **7** | Simulated value on A1 in ~5 s. But onboarding grew to 5–7 screens, with a question most drivers can't answer (A2 km/L) placed *before* the permission cliff. A4 still shows "Toca Kompara en la lista" despite the spec's API 33+ deep link. A1's demo verdict is itself wrong (see D01). |
| 8 | Consistency and feel | **7** | One token set and one chip component, but two editor models (C5 draft + Guardar vs G2 auto-save), decorative emoji standing in for Material icons (A3, G1, H1, H3, B4), and several spec↔mockup contradictions (§3). |
| | **Total** | **59 / 80** | **NOT_YET.** Four criteria are below 8 (numbers, copy, time-to-value, consistency). The *structure* is pass-capable, roughly 67–69 once §3's defects are fixed. The spec's own claim of "68/80" (§0.10, §10) is not supported by the delivered mockups. |

---

## 2. Hard fails

- **Auto-action proposed: none.** C5's editor only changes Kompara's own rule ("Cancelar · Guardar"). A3 says "nunca acepta ni rechaza por ti". C3's caption: "it informs, the driver decides".
- **Platform logos: none.** Platform names appear as plain text only (G4, F1, H3). The generic host card in C1–C4 has an Uber-like countdown "ring" and an "Aceptar" button. That's fine for internal mocks, but it must not ship in A1's demo or G2's preview (design-system README: "never reproduce platform UIs").
- **A driving-time surface fails the 1-second glance: none confirmed; two near misses:**
  - **C2 shows the wrong colour for its own numbers** (D01). The glance is legible but untrue.
  - **C5** is an interactive editor whose *only* entry point is a long-press on a chip that exists only while a live offer is on screen. "Ajusta cuando estés estacionado" is advice; the spec admits "No motion lock is claimed".
- **Paywall blanks the free product: none.** E2 and F3 mask a value with a single card. However, E2 masks the driver's **own** money (D10), which breaks the spec's own rule.
- **Onboarding screen with more than one ask: none.** A2 is the closest: one question with three competing exits (D27).

---

## 3. Defects (ranked by damage)

**Counts:** 3 critical · 10 high · 14 medium · 9 low.

| id | Screen / § | Sev. | Defect | Exact evidence | Concrete fix |
|---|---|---|---|---|---|
| D01 | A1, C1, C2, C3, D1, D2, B1 | **critical** | The worked examples don't compute with the app's own formula and floors, so the flagship "the chip tells the truth" screens show wrong verdicts. | **C2:** "$96.00 · Recoger 3.4 km · Viaje 9.8 km · 9 + 31 min" → net $58.5 → **$4.43/km, $88/h = rojo**, but it's drawn "! $7.10 /km neto · $96/h". **C1:** $148.50, 14.5 km, 28 min → $7.40/km, $230/h; drawn "$7.20 · $186/h", and **verde at $7.20** although "Verde desde $8.00/km" (G2, C5). **C3:** $62, 12.3 km, 30 min → $2.20/km, $54/h; drawn "$3.10 · $74/h". **D2:** "Tarifa $148 · Gasolina y desgaste −$34 · Te quedaron $114 · 14.5 km · 28 min · $7.20/km · $186/h". But $34 ≠ 14.5 × $2.84 ($41), $114/14.5 = $7.86, and $114 over 28 min = $244/h. It then explains the colour with "mínimo de $7.00/km de esa semana", on the same Tuesday that C5 shows $8.00. **A1 (shipped demo):** "$148 · 16 km · 22 min · Recoger a 2 km" with "✓ $7.20 · verde". By A2's defaults that offer is amarillo (16 km) or rojo (18 km). The aha teaches a false verdict. | Generate every figure from one **golden fixture** run through the real `NetProfitEngine` (one cost profile, one threshold set, one week of offers). Ship A1 with a verified triple, e.g. $148 / 12 km total / 18 min → $9.49/km verde. Make D2's why-line quote the floor actually in force. Add a CI check that mock and preview numbers equal engine output. |
| D02 | F2, F3, H2; spec §5.9 | **critical** | Tu ciudad compares the driver's **net** against the city's **gross**, ranks on it, and the share card broadcasts the result. | F2: "Tú en Uber · $158 /h neto" vs "Promedio de Uber en CDMX · **Antes de gastos** · $142 /h", under "Le ganas al 78 %… Top 22 %". The spec codifies it ("the basis difference is written on the row"), contradicting its own F11 row ("strict like-for-like"). H2 shares "Top 22 % en Uber · CDMX". | Both rows and the percentile must be on **one basis**. If the population is gross, show "Tú en Uber · $205/h antes de gastos" vs "$142/h" and rank on gross; put net in "Cómo se calculó". If no matching basis exists, show no standing. |
| D03 | A3, A6, F2; Play prominent disclosure | **critical** | The consent and account copy never says that derived trip numbers leave the phone or feed city averages, and nowhere does a driver opt in to contributing. | A3: "Para qué. Solo para ponerte el semáforo y sumar tus totales." / "Dónde. Todo pasa en tu teléfono. No guardamos ni mandamos tu pantalla…". A6: "guardamos tu configuración y tus totales". F2 shows a cohort of "1,240 conductores", which needs drivers' uploaded weekly aggregates. Play's User Data and Accessibility policy and the Data-safety form require disclosing derived data that is transmitted (`play-compliance.md`: #1 launch risk). | Add to A3: "Si creas tu cuenta, guardamos tus totales por semana (nunca tu pantalla)." Add an explicit opt-in on A6 or the first Tu ciudad visit: "Sumar mis totales, sin mi nombre, al promedio de CDMX" (default **off**, required to *contribute*, not to *view*). Mirror it in Tus datos with an off switch. |
| D04 | H1; spec §5.12 vs §5.8 | **high** | The paywall sells things that don't exist or aren't validated, breaking the spec's own "Nobody is charged for absent data". | H1: "📄 Todo tu historial y tus papeles · **Retenciones** y PDF para tu contador", while spec §5.8 says withholding estimates "ship only after the rates and base are validated". "⚖️ Qué app paga mejor · En tu ciudad, con datos reales": no screen shows Uber-avg vs DiDi-avg (F2 has one app plus a selector). Hero "Esta semana te quedaron $4,980. **¿Es mucho o poco en CDMX?**": there's no population benchmark for weekly totals (`comparar-redesign-spec.md` §2 gap 1), so Premium can't answer it. E2 promises "…y tu lugar" for old weeks. | List only what exists for *this* driver: "El resumen del mes en PDF para tu contador" (no "Retenciones"). Either build "¿Qué app paga mejor?" as a two-row Tu ciudad view (same metric, same basis) or drop the benefit. Replace the hero with a question Premium answers: "Por hora te quedaron $158. ¿Cómo vas contra Uber en CDMX?" |
| D05 | E1, E3, H1, H4 | **high** | The same week carries two different "Te quedan" amounts, and day rows don't add up to the hero. This is the scope drift the F15 decision existed to kill. | E1: "Te quedan esta semana **$4,980** … Lo del coche −$1,500" (day rows are pre-rent, e.g. "Viernes 17 · $1,380"). E3, same week: "13 – 19 oct · 62 viajes · **$6,480**". E3's month hero deducts "lo del coche", but there's no rule for straddling weeks ("29 sep – 5 oct"). | One rule: every list row shows the **same scope as its parent hero**. E3 rows show $4,980; E1 day rows carry the muted header "Por día, antes de lo del coche". Define month allocation (pro-rate by days in the month) and state it in "Cómo se calculó". |
| D06 | G4, A3; spec §5.2 | **high** | inDrive support is claimed while the spec says it isn't available, and the disclosure omits the supported-app list it requires. | G4: "Lee tus ofertas en Uber, DiDi **e inDrive**", with no inDrive row. Spec §5.2: "inDrive · No disponible en esta versión". Spec §7 A3 requires "supported apps as plain text"; A3 lists none. | G4 hero "Lee tus ofertas en Uber y DiDi." with a row "inDrive · Aún no disponible". A3 adds "Funciona con: Uber y DiDi." Both are driven by the parser registry, never hard-coded. |
| D07 | B4, G4; spec §5.2, §12 | **high** | "Funcionando" never expires, so false readiness comes back the morning after. | B4 at **7:30**, "Aún no hay viajes": "● Lector funcionando", proven only by yesterday's read. The spec defines *preparado* vs *funcionando* but no decay after reboot, app update, service rebind or a new day. | Define *funcionando* as "≥ 1 successful host read since the service last bound". Otherwise show "● Lector preparado · última lectura ayer 22:10". Re-check per app. |
| D08 | C5; spec §6 | **high** | The overlay editor can *only* be opened during a live offer, so "parked only" is structurally impossible to honour. | Spec: "Threshold editor (over the host, parked only)"; "the chip exists only while an offer card is on screen"; "Long-press opens the threshold editor". C5 shows it over a live "$96.00 … Aceptar" card. | Either remove the overlay editor (adjust from D2 "Ajustar mínimo" and from the day-summary notification), or have long-press queue it: "Te lo abrimos cuando termine la oferta." It should never be live over a ticking offer. |
| D09 | G2 vs spec §5.14 and C5 | **high** | Tu semáforo silently auto-saves, re-creating the "silent edit" the redesign killed and contradicting the decided explicit save. | G2: "**Se guarda solo** y lo usa el semáforo en tus ofertas." There's no Guardar button. The spec says "· **Guardar**" and "demo changes never touch real settings until saved"; C5 uses "Cancelar · Guardar". G2 also has "Volver a **lo típico** de CDMX ($8.05/km)" (the spec says "valor inicial", not "lo típico, unless substantiated"), and "Más ajustes · Rojo por debajo de · **la otra cifra**" is orphaned now that the ▼ marker has been removed from C2. | Add a bottom-pinned "Guardar" plus a leave-with-changes confirmation. Rename the reset to "Volver al valor inicial ($8.05/km)". Delete "la otra cifra". |
| D10 | E2 vs spec §0.6, §4 | **high** | The history lock masks the driver's **own** money, against the spec's stated principle. | E2: "Te quedan esa semana **$•,•••** · 58 viajes · 39 h". Spec §0.6: "Locks mask the other side's number, never blur the driver's own." Spec §4: "the driver's own value stays visible". | Show the week's own total ("$4,620") and lock the *breakdown* (bars, days, best hours) with the card "Revisa el detalle de todas tus semanas". Or change the principle explicitly. Don't do both. |
| D11 | E3, E4, H4 | **high** | The IMSS threshold is a hard-coded **2025** figure in an Oct-2026 product, so the free notification can assert a wrong fact. | "$8,364 es el salario mínimo mensual de referencia" (= 30 × $278.80, the 2025 general daily minimum). H4: "DiDi te pagó $9,020, **arriba del monto de referencia**". My information is that CONASAMI raised the 2026 general minimum (≈ $315/day, ≈ $9,450/month). If so, $9,020 is *below* it. Verify before shipping. | Serve the threshold from remote config with a year and a source ("Salario mínimo 2026: $X · CONASAMI"). Fall back to "Usamos el valor de referencia de {año}". Add a legal gate on the basis (B-038, already noted). |
| D12 | A6, H1 vs spec §8 / F12 | **high** | The trial copy contradicts the explicit-start, no-charge decision. | A6: "te **regalamos** 14 días de Premium **al cerrar tu primera semana**" (reads as automatic). Spec: "Explicit start only … OTP completion does not start the clock". H1: "Sin tarjeta. **Al terminar, tú decides.**" vs spec and F3: "Sin tarjeta. **No habrá ningún cobro al terminar.**" | A6: "…y puedes probar Premium 14 días gratis, sin tarjeta." H1: "Sin tarjeta. No habrá ningún cobro al terminar." |
| D13 | B3, A2, G1, G2 vs spec §7 | **high** | "City is never inferred" is violated by CDMX defaults everywhere, before the driver has given any city. | Spec: "Until then thresholds are a labelled **national** valor inicial." B3: "Semáforo con el valor inicial **de CDMX**". A2: "Gasolina a $24.50 el litro **en CDMX**". G1: "Tu plan: Gratis · **CDMX**". G2: "($8.05/km)" (CDMX's table value). | Either use a national initial value and fuel price until the driver picks a city, or infer from the area code *visibly*: "Usamos CDMX por tu número · Cambiar" (my position, §6). |
| D14 | E4, E3; spec §5.8 | medium | One screen has three names, one of which promises content that is withheld. | Spec heading "IMSS: revisar mi mes"; the spec's own layout and E4 top bar say "IMSS **e impuestos**"; E3 row "IMSS: lo que te pagó cada app". Taxes are withheld until validated. | Use one name everywhere: "IMSS del mes". Drop "e impuestos" until withholding ships. |
| D15 | B1, F1, F2, E1 | medium | More than 3 numbers in the first viewport, against the spec's own law. | B1: "$1,240 · 9 viajes · 5 h 20 min · 18 a 19 h · $232 en 4 viajes · $7.20 · $3.10". F1: "$1.20 · 34 viajes · 410 km · $6.80 · 28 viajes · 302 km · $8.00". F2: "78 % · 1,240 conductores · 22 % · 41 h · $158 · $142". | Move counts, km and sample size into "De dónde salen estos datos". B1's observation keeps one figure ("Te quedaron $232"). F2 drops "41 h". |
| D16 | D1, B1, H4 | medium | "Horas" has no single definition; the day's shifts and its hours disagree. | D1 "Turnos 08:00 – 12:30 · 17:10 – 20:45" (= **8 h 05 min**), but the hero says "9 viajes · **5 h 20 min**". | Define it in the vocabulary: "en turno" (online) vs "con pasajero". Label it on the hero ("8 h en turno") and in "Cómo se calculó". |
| D17 | B1 | medium | The factual observation is impossible at the time shown, and the mid-shift totals equal the end-of-day totals. | B1 status bar **14:40**: "Tu mejor hora hoy: **18 a 19 h** · Te quedaron $232 en **4 viajes**" (a future hour; 4 trips in one hour where C1–C3 trips last 28–40 min). B1's "$1,240 · 9 viajes" equals D1 at 21:10, after the 17:10–20:45 shift. | The observation engine cites only completed hours; derive B1 from the fixture at 14:40 (5 viajes, $640). |
| D18 | C6; spec §6 vs F16 | medium | A persistent, tappable overlay over a host app with no offer, which is exactly what F16 refused for the greeting, and with no duration rule. | C6: "Conectado" host + "Lector detenido · Toca para revisar". Spec F16: the greeting was rejected because readiness "belongs in Hoy and Lector". Tapping takes an online driver out of the ride app. | Show once per host foreground for 5 s, then collapse to a 32dp grey K dot. Tap opens Lector only after a confirm ("¿Abrir Kompara?"). Or replace it with the notification alone. |
| D19 | G4; spec §4 vocabulary | medium | "Pausar lector" creates a state that has no name, no Hoy rendering and no auto-resume. | Spec states: "preparado / funcionando / apagado / detenido", with no "en pausa". G4 "Pausar lector"; no B-frame for paused. | Add the state "En pausa" (grey pill on Hoy plus primary "Reanudar"). Auto-resume next day or on the next ride-app open, and say so: "Se reanuda mañana". |
| D20 | A1, C4, legend, Ayuda vs spec §4 | medium | The grey state is still called three things. | A1: "**Gris** = no se pudo leer"; mock legend: "Gris = no se pudo leer (never red)"; C4 caption "Sin color"; spec vocabulary "**sin color**". | Pick one. I'd use "**Gris = no se pudo leer**" (concrete, matches what the eye sees) and update spec §4 and Ayuda. |
| D21 | D1, D2 | medium | Back and previous-day use the same glyph side by side. | D1 and D2 top: "‹ ‹ Martes 21 oct ›". | Top bar uses "←" back + title "Tu día". The day stepper sits on its own row: "‹  Martes 21 oct  ›". |
| D22 | F1–F3 | medium | Comparar's controls don't "disappear": four control rows around one answer, with the App selector *below* the result it changes. | F2: "Tus apps · Tu ciudad" / "‹ 13 – 19 oct ›" / "Por km · Por hora · Por viaje" / "Ciudad · CDMX · App · Uber ›" / "De dónde salen estos datos ›". | Make city and app tappable words *inside* the standing sentence ("…de **Uber** en **CDMX**"). Collapse the week into a label shared with Ganancias. Keep segments plus chips only. |
| D23 | H2 | medium | The share card's standing loses its metric and mixes scopes. | "Mi semana manejando · Top 22 % en Uber · CDMX · Viajes 62" (the rank is *por hora, Uber only*; 62 trips spans both apps). | "Top 22 % por hora en Uber · CDMX"; facts from the same scope ("Mejor día · viernes", "34 viajes en Uber"). |
| D24 | H3 vs spec §5.11 | medium | The import privacy line asserts deletion that the spec says must match an unconfirmed backend fact. | H3: "Tu archivo sí se sube, solo para leer tus números, **y lo borramos al terminar**." Spec: "(retention wording must match the backend fact)". | Ship "…y lo borramos al terminar" only with a verified retention job. Otherwise: "Tu archivo se sube solo para leer tus números." |
| D25 | Spec §5 / mockups | medium | The integrity-critical states exist only as text; nothing shows them. | Not mocked: Comparar with **no valid cohort**; Tus apps single-app; trial CTA with **no account** (A6 makes the account optional, but H1 still says "Probar 14 días gratis"); Tu plan (active trial); "Verificar mis datos"; Importar review, reconcile and error; Lo del coche, Cómo se calculó and Elegir periodo sheets; the km/L "¿Aplicar también a tus viajes anteriores?" prompt. | Add frames for each; for the no-account trial: "Activa tu prueba con tu WhatsApp". |
| D26 | A3, B4, G1, H1, H3, E4, top bars | medium | Decorative emoji stand in for icons, including inside the Play prominent disclosure. | A3 "👁 🎯 📱 ⚠️"; G1 "🚦 ⛽ 🚗 🔔 🎁 🔐"; H1 "📍 ⚖️ 📄"; B4 "⛽"; avatar "👤". Design-system README: emoji "only functionally: 🔥 🔒 🎉. Never decorative"; icons are Material Symbols. They render differently per OEM. | Use Material Symbols vectors (visibility, flag, smartphone, warning, traffic, local_gas_station, …); use AccountCircle for the avatar. |
| D27 | A2 | medium | One ask, three exits, two of which do the same thing when untouched. | "Continuar" (12 preselected) · "Usar el valor típico" · "Ya sé mi costo por km". | A single primary whose label reflects the state: "Usar 12 km/L (típico)" until changed, then "Continuar". Keep "Ya sé mi costo por km" as text. Drop the duplicate. |
| D28 | A4 vs spec §7 | low | The mock shows the list search the spec says API 33+ skips. | A4 step 1 "Toca Kompara **en la lista**"; spec: "on API 33+ deep-link to Kompara's own toggle". | Two variants: 33+ "1 · Activa el interruptor de Kompara · 2 · Toca Permitir"; older phones keep the list step. |
| D29 | A1–A7 vs spec §7 | low | The fixed six-segment progress bar contradicts "varies by device" and lies when the battery step is skipped. | `.steps` has six segments on every frame; A6 and A7 both show 6/6. | A continuous thin bar computed from the device's actual step count. |
| D30 | B1 vs spec §4 | low | "Cómo se calculó" isn't one tap from Hoy's hero, and the scope line doubles as a link. | B1: "Después de gasolina y desgaste · aprox. · **Ver tu día ›**". Spec: "'Cómo se calculó' is one tap away from every hero." | The scope line opens "Cómo se calculó"; the hero card opens Tu día. |
| D31 | A1, A5, B2, B1/D1, G3, B3 | low | Small copy drift against the spec vocabulary. | A1 "lo que te queda **de verdad**" (vocabulary: never "de verdad"). A5 "Lector **encendido** · Listo para leer" (not a state). B2 "Encender" (spec: "Encender lector"). B1 "Te quedan hoy" vs D1 "Te quedaron hoy" for the same day. G3 "**Mantenimiento**" as a row headline (vocabulary: "desgaste"). B3 "Ver cómo funciona" vs spec "Ver ejemplo". | Align with spec §4. |
| D32 | G3 vs A2 | low | A typical value is shown as if it were the driver's own. | G3 "Precio de la gasolina $24.50 por litro" (no label); A2 says "(referencia)". | "$24.50 por litro · referencia" until edited. |
| D33 | F2, E1, E3 | low | The mock data is inconsistent across frames. | F2 "Tú en Uber · **41 h** esta semana", but 41 h is the whole two-app week (E1: "62 viajes · 41 h"; F1: 28 DiDi trips). F2 "**esta semana**" labels 13–19 oct while D1 is 21 oct. E3 "248 viajes · 160 h" by 21 oct at ~60 trips/week. | Comes from the golden fixture (D01). |
| D34 | Spec §0.10, §10 | low | The spec scores itself above both reviewers without new evidence. | "v2 ~68/80"; "One primary action 9", "Time-to-value 8", while noting "Codex 67, Opus 66". | Score the artifacts after fixing D01–D13, or label 68 as a target. |
| D35 | G1 | low | The section header duplicates the row label. | Section "Tu semáforo" → row "🚦 Tu semáforo · Verde desde $8.00/km". | Rename the section "Tu manejo". |
| D36 | C1–C4 host mock, A1 | low | The generic host card borrows a recognisable platform affordance. | `.offer` includes a countdown `.ring` plus "★ 4.9 · Efectivo · Col. Roma Nte." plus "Aceptar". | Keep it in internal mocks only. A1 and G2 previews use a neutral card with no ring, rating or accept button. |

**Spec↔mockup contradictions, collected:**
- D04: "Retenciones"
- D06: inDrive
- D09: G2 auto-save, "lo típico", "la otra cifra"
- D10: E2 own-value mask
- D12: trial copy
- D13: CDMX
- D14: three names for the IMSS screen
- D20: Gris vs sin color
- D24: deletion claim
- D28: deep link
- D29: progress bar
- D30: Cómo se calculó
- D31: vocabulary drift
- The spec contradicts itself: §5.9's "basis difference written on the row" vs F11 "strict like-for-like" (D02); §5.8 heading vs layout (D14).

---

## 4. What a driver on a windshield mount gets from each tab root in one second

- **Hoy (B1):** "Kompara está jalando; hoy llevo $1,240 limpios y la última salió verde."
- **Ganancias (E1):** "Esta semana me quedaron $4,980 ya con la renta pagada; el viernes fue el bueno."
- **Comparar (F1, default "Tus apps"):** "DiDi me está dejando más por kilómetro que Uber." On F2 they'd read "le gano al 78 % de los de Uber". Today that rank compares their net against everyone else's gross (D02).
- **Chip verde (C1):** "Verde: llega a mi mínimo, $7.20 el kilómetro ya limpio." With the spec's own $8.00 floor, that number should not be green (D01).
- **Chip amarillo (C2):** "Amarillo: está en el límite, piénsale." Its own inputs make it rojo (D01).
- **Chip rojo (C3):** "Rojo: no llega a lo que me pongo como mínimo."
- **Chip sin color (C4):** "Gris: no la pudo leer, la decido yo." It is correctly *not* a "no".

---

## 5. The one change that would raise the score the most

**Build a single golden fixture and generate every number from it.** That means:
- one cost profile ($2.84/km), one threshold set (verde $8.00 / rojo $6.00), one city cohort with an explicit basis;
- one week of offers and trips with shift times, so "horas" has one definition;
- one "lo del coche".

Run it through the real `NetProfitEngine`, period folds and IMSS rule, and render every value in the mockups, the A1 demo, G2's previews, D2's "why", E1/E3, F1/F2, H1/H2 and H4 from it. Each figure carries its **basis** (net / gross / before-or-after "lo del coche") as data, so a row can't be shown beside a row of another basis. Add a CI check that fails when a preview or mock number differs from engine output.

**What it fixes:** the critical arithmetic (D01), the net-vs-gross ranking (D02), scope drift (D05, D16), impossible observations (D17) and the inconsistent mock data (D33).

**Score effect:** Numbers discipline 6 → 8 and Consistency 7 → 8, about +4 (to ~63). It also gives engineering the contract that keeps the shipped app as honest as the spec claims. The remaining gap to pass is time-to-value and copy, which need the A2 reorder (§6), the API 33+ deep link, and one vocabulary pass.

---

## 6. Positions I still hold against the synthesis

1. **F3, chip tap does nothing:** this was wrong, and D08 proves it. The synthesis left the overlay with exactly one interaction, an undiscoverable long-press that opens an editor, and that interaction is only reachable during a live offer. Meanwhile the one question the colour raises ("¿por qué amarillo?") has no answer until after the trip. A tap → three-line "por qué" in its own anchored window (closes when the offer changes; drag never fires it) is safer than a stepper editor over a ticking offer. If tap stays inert, then remove the overlay editor too (D08's fix).
2. **F7, "city is never inferred":** the rule is already broken by the synthesis's own screens, which default to CDMX silently (D13). Inferring from the WhatsApp area code and saying so ("Usamos CDMX por tu número · Cambiar") is more honest than an unlabelled CDMX default.
3. **Onboarding order:** A2 (km/L) should come **after** A4 (Activar), not before it. The accessibility grant is the cliff, and a question most drivers can't answer (inventory: "Drivers may not know their km/L") spends motivation right before it. Typical costs already keep the chip coloured, so nothing is lost by asking later.
4. **F16, the greeting:** the synthesis rejected a 2-second, non-interactive "✓ Kompara listo" because overlays don't belong on non-offer screens, then shipped C6, a persistent, tappable pill on exactly that kind of screen (D18). Be consistent. I still hold the gated greeting (only after a real capture check, once a day, touches passing through to the ride app) as the less intrusive way to answer "¿ya funciona?" at the moment the driver opens the ride app.
