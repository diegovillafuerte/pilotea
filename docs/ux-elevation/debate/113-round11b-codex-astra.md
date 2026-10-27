## 1. Where Opus 5.5 is right and I was wrong

**I withdraw cota as drawn. Raya is the stronger starting point.**

- **Colour mass and docking win.** [Opus §2A](/Users/panama/pilotea/docs/ux-elevation/debate/112-round11-opus-5-5.md:83) makes the signal part of the plate’s structure. In `raya.png`, it remains visible before reading the amount. In `cota.png`, my small block becomes an annotation beside the number; the pale carrier merges with the map. I identified that risk but underestimated it.
- **C6 needs a different silhouette.** Opus’s [§4 C6](/Users/panama/pilotea/docs/ux-elevation/debate/112-round11-opus-5-5.md:529) proposes a short status strip. My broken cota retained too much of the verdict construction. A stopped reader should not resemble another offer evaluation.
- **The neutral palette is better than my green-tinted surfaces.** Opus [§3.1](/Users/panama/pilotea/docs/ux-elevation/debate/112-round11-opus-5-5.md:210) and Fable’s middle/right `b1-variants.png` show the better direction: money on the page, restrained separators, almost no coloured furniture. My warm green system adds atmosphere without improving comprehension.
- **F1 needs the concrete limitation.** Opus’s “Manejaste en horarios distintos” in [§4 F1](/Users/panama/pilotea/docs/ux-elevation/debate/112-round11-opus-5-5.md:665) earns its space. My “Solo compara tus viajes de esa semana” is less useful.

We already agreed on removing cards, putting totals on the surface and supporting both themes. Those are shared conclusions, not reversals.

**Where Opus is wrong:**

| Section | Correction and evidence |
|---|---|
| **§2A: moving the numeral** | `raya.png` makes the problem concrete: `$9.49` is above the divider; `$2.20` and `−$1.05` move below it. The eye’s confirmation target should remain fixed. Criterion **10** requires the number to confirm the state; it should not require a second search. |
| **§2A: amarillo “straddles your minimum”** | The fixture has **two thresholds**: green begins at `$8.00/km`; red begins below `$6.00/km`. `$7.16/km` is between them. One line described as the minimum cannot accurately represent both boundaries. |
| **§1.2: luminance establishes peripheral failure** | The **1.09:1** calculation supports concern about equal-looking masses. It does not establish detection performance at a mount, viewing angle or exposure duration. The same limitation applies to claiming raya already fixes the physics. |
| **§2, “The pick”: unique capability** | “Only Kompara has a personal mínimo” conflicts with the supplied [competitive analysis](/Users/panama/pilotea/docs/competitive-analysis.md:54), which describes DecideRider’s driver threshold and net earnings. Distinction must come from execution, not that exclusivity claim. |
| **§4 E1 / §6: moving all provenance away** | A statement label beneath **Comparar** does not label the **Ganancias** hero. The fixture explicitly separates the reader’s `$5,028` from the statement’s `$5,062`. Keep a short visible reader label and the discrepancy notice; keep the amounts in detail. |
| **§4 D1: replacing the shift ledger** | The proposed timeline drops **5 viajes / 4 viajes** and weakens “tú lo pausaste.” It must not visually connect the unexplained **12:30–12:40** interval or invent the nineteen offers absent from the fixture. |
| **§4 F2 / H2b** | `78 %` concerns the participating comparison group, not every Uber driver in CDMX. Banked days also apply after failed renewal and grace, so “al final, cuando canceles” is too narrow. |

**Fable’s explorations:** adopt the open hierarchy in `b1-variants.png`, but restore **“6 viajes tomados”** and **“Tu mejor hora hoy: 9 a 10 h.”** Remove the newly added recent-offer rates: they exceed the root’s existing number budget.

`host-test.png` improves the fare, route and offer hierarchy, but omits the top controls. Only the raya comparison includes them. The [supplied Uber offer benchmark](/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r11/bench/uber-offer-card-lightbox.jpg) also has a much taller offer card. Use both constraints in the next render. Cota and raya currently differ in placement and host furniture, so their comparison is not fully controlled.

## 2. Chip concept tournament

All scores run **1–10, higher is better**. The four safety columns score resistance to the named failure. They are design assessments, not measured usability results; a hard failure cannot be averaged away.

`†` means no complete rendered state set exists. Low gris scores there reflect missing design evidence, not an observed misinterpretation.

| Candidate | 9: Signature | 10: Glance | Clear of controls | Does not look actionable | Gris distinct from verdict | Cue beyond colour | One-line judgment |
|---|---:|---:|---:|---:|---:|---:|---|
| **Astra: cota personal** | 8 | 5 | 3 | 8 | 8 | 9 | Accurate two-threshold grammar, but the tiny mass disappears into the pale plate; the shown position conflicts with a restored top control row. |
| **Astra: recibo que queda†** | 7 | 4 | 6 | 5 | 6 | 7 | The small raised/recessed edge requires inspection and can resemble a drawer tab. |
| **Astra: margen propio†** | 6 | 3 | 6 | 3 | 6 | 7 | Bracket relationships are abstract and resemble selection handles. |
| **Opus: raya** | 8 | 7 | 8 | 8 | 8 | 9 | Strongest rendered mass and placement; moving money and the nearly uniform yellow slab weaken it. |
| **Opus: brazo†** | 6 | 5 | 7 | 7 | 6 | 7 | The shallow tilt suggests trend, consumes space and leaves amarillo as an ordinary rectangle. |
| **Opus: ticket†** | 6 | 3 | 6 | 5 | 6 | 2 | Subtraction competes with the deciding rate; the small stamp changes hue without sufficient structural distinction. |
| **Fable A: full-width raya†** | 6 | 5 | 1 | 5 | 2 | 2 | The render replaces the status region; placing it below would compete with host controls, and it exceeds the footprint budget. |
| **Fable B: marcador†** | 7 | 4 | 5 | 1 | 2 | 6 | Personal thresholds are explicit, but the dot and track look operable; reading position adds work. |
| **Fable C: ficha†** | 4 | 3 | 6 | 8 | 2 | 1 | Minimal enclosure, but verdict recognition depends on hue and text contrast depends on the map. |

**Winner: raya’s docked plate, rebuilt with cota’s two boundaries and fixed text positions.**

Take **Opus’s broad colour fields, dark carrier and edge attachment**; take **Astra’s two-threshold meaning, stable amount and broken rules for missing data**. Drop the tiny side mark, reversed text order and all-yellow plate.

This is the winner **for the rebuild**. Its exact combined geometry has not yet been rendered or passed criterion 10.

### Anatomy

At default font scale:

- **200 × 88dp**, opaque carrier.
- Bezel corners **0dp**; exposed corners **2dp**.
- No shadow, circle, check, arrow, handle or separate brand strip.
- Main amount: **32sp/36sp**, Barlow Semi Condensed 600; unit **16sp**, baseline-aligned.
- Main line box: **x12, y4, width160, height36dp**. It never moves by verdict.
- Unboxed K: **13dp**, upper bezel corner, outside the amount’s bounds.
- Footer: **x12, y64, width176, height20dp**. “neto aprox.” is **13sp**; the hourly amount **16sp**.
- Gris headline: **20sp/26sp**, same left anchor; cause **16sp** in the footer.

The full-width fields are:

| Region | Geometry | Meaning |
|---|---|---|
| Upper field | `y0–42` | At or above the green minimum |
| Upper rule | `y42–44`, **2dp** | Green boundary |
| Middle field | `y44–60`, **16dp** | Between the two thresholds |
| Lower rule | `y60–62`, **2dp** | Red boundary |
| Lower field | `y62–88` | Below the red cutoff |

Only the relevant field receives colour. The other fields remain ink. These are **categorical regions**, not a proportional money chart. There are no lamps or inactive coloured slots.

This gives amarillo a broad central stripe instead of a yellow rectangle. Red fills the lower field while its deciding amount stays above.

### Exact state content

There are four states; negative remains rojo, and gris has two causes.

| State | Main line | Footer | Construction |
|---|---|---|---|
| Verde | **`$9.49/km`** | **`neto aprox. · $285/h`** | Upper field green |
| Amarillo | **`$7.16/km`** | **`neto aprox. · $191/h`** | Middle field yellow |
| Rojo | **`$2.20/km`** | **`neto aprox. · $54/h`** | Lower field red |
| Rojo, negative | **`−$1.05/km`** | **`neto aprox. · −$26/h`** | Identical red geometry |
| Gris, missing fare | **`Sin lectura`** | **`faltó la tarifa`** | No colour; both rules dashed |
| Gris, missing deciding time | **`Falta tiempo`** | **`sin semáforo`** | No colour; both rules dashed |

Never put a computable secondary rate on gris.

### Placement, growth and appearance

**Placement:** dock right by default, below the host’s persistent controls and above its offer surface. **`y=144dp` is the reference mock position, not a universal production coordinate.** Protect fare, pickup/trip information, navigation banners and all controls with an **8dp exclusion margin**. Revalidate saved positions after layout changes; use the opposite edge if necessary. Suppress when no safe rectangle fits.

Drag is the only gesture. Tap and long-press remain inert. Edge attachment does not itself prove that system Back gestures are safe.

**Growth:** all fixture amounts, including negative, use **200 × 88dp at 1×**. Accessibility text grows the shared component, never just the red state. Increase height and, within the safe region, width; preserve field order and fixed text anchors. Move the complete unit to a new line before splitting a signed amount. Never shrink, clip, ellipsize or replace the deciding metric. If the enlarged component cannot fit safely, suppress it.

**Day/night:** keep the chip’s ink carrier and verdict colours stable in both themes. Use a subtle **1dp keyline**, with no glow. Do not make the chip a bright paper card at night.

**Colour-blind cue:** upper mass / central stripe / lower mass / interrupted rules. Colour reinforces that structure. The next render must demonstrate all four in greyscale on the same host, at the same position, followed by the mounted test.

## 3. Visual system verdict

**Both themes. Follow the system, with an override in Cuenta.** Build the light and refined-dark screens together. The right-hand B1 exploration shows that dark can retain the same clear hierarchy without returning to slate cards.

**Money:** Barlow Semi Condensed 600.  
**UI:** Barlow 400 and 600.  
Bundle the fonts and verify tabular figures and actual string fit; the current repository only supplies Inter, so the proposed font fit is not yet proven.

| Token | Light | Dark |
|---|---|---|
| Page | `#F5F4EF` | `#0E1011` |
| Raised sheet | `#FFFFFF` | `#191C1E` |
| Input / pressed surface | `#EBE9E2` | `#23272A` |
| Primary text | `#111110` | `#F2F1EC` |
| Secondary text | `#5C5B57` | `#A6AAA8` |
| Separator | `#DCDAD2` | `#303638` |
| Primary action / on-action | `#111110` / `#FFFFFF` | `#F2F1EC` / `#111110` |
| Chip carrier / text | `#0E0F10` / `#F2F1EC` | Same |
| Verde / on-verde | `#187A36` / `#FFFFFF` | Same |
| Amarillo / on-amarillo | `#F2B705` / `#1A1300` | Same |
| Rojo / on-rojo | `#D32F2F` / `#FFFFFF` | Same |

Five type steps: **13/18, 16/22, 20/26, 32/36, 56/60sp**. One display amount per screen. Keep signed amounts, units and **`78 %`** unbroken.

**Surface model:** one page, open rows, whitespace between groups. No enclosing hero card, nested list card or boxed status icon.

- Page inset **20dp**; group gap **32dp**; internal rhythm **4/8/12/16dp**.
- Rows **56dp minimum**; interactive targets **48dp minimum**.
- Primary button **56dp**, radius **8dp**.
- Sheets have **20dp** top corners.
- Text tabs retain a **2dp selection underline**. Reserving every line for the brand, as Opus proposes, unnecessarily weakens selection.
- Reader status uses a neutral solid/broken mark **plus words**. Green is reserved for offer verdicts and restrained brand use.
- Offer rows repeat the two-rule signal in miniature. Aggregate totals remain neutral; chart zero and city average are explicitly labelled references, not personal floors.
- Chip updates are atomic, with at most a **100ms fade**. No slide from the bezel, travelling state mass or counting money.

## 4. Simplification merge

Each wireframe has at most twelve content lines. Bracketed descriptions are drawing instructions, not displayed copy. Roots retain **Hoy · Ganancias · Comparar** and Cuenta; drill-ins retain Back. Qualifier rows open **Cómo se calculó** with a full-width target.

### A1 — Demo

```text
Antes de aceptar, ve si te deja.
Ejemplo
[Mini host; actual winning chip]
$148
12 km en total (2 para recoger) · 24 min
$9.49/km
neto aprox. · $285/h
Llega a tu mínimo.
Ver otra oferta
[ Empezar ]
Ya tengo cuenta
```

Keep the sample noninteractive except for the explicit cycling action. Do not introduce an “Aceptar” action into onboarding.

### A2 — Disclosure

```text
← Lo que hace Kompara en tu teléfono
Lee con Accesibilidad de Android.
En ofertas de Uber o DiDi, toma una imagen de toda la pantalla,
lee tarifa, distancia y tiempo, y borra la imagen al momento.
Guarda cifras de tus viajes aquí; no imágenes ni datos del pasajero.
Envía conteos anónimos de lectura. Puedes apagarlos en Tus datos.
Solo si eliges: respaldo de totales con tu cuenta, aporte al promedio,
subida de un archivo o envío de un reporte de lectura.
Las apps de viajes podrían no permitir estas herramientas.
Kompara nunca acepta ni rechaza por ti.
[ Acepto ]
No acepto
```

Use four open, expanded text groups. Allow scrolling; this content is not compressed into twelve physical phone rows. Revised wording remains subject to **B-038**.

### A5 — Costs

```text
← ¿Cuánto rinde tu coche?
Puedes ajustarlo después.
−                 12 km/L                 +
Típico de un sedán
Gasolina: $24.50/L · referencia
Desgaste: $0.80 por km
Manejar te cuesta $2.84 por km
En 12 km: unos $34
Ya sé mi costo por km
[ Usar 12 km/L (típico) ]
```

### B1 — Hoy, functioning

```text
Hoy                                      Cuenta
[solid mark] Lector funcionando
Te quedan hoy
$762
6 viajes tomados
Según tus ofertas · menos gasolina y desgaste · aprox. ›
Tu mejor hora hoy: 9 a 10 h
Últimas ofertas                         Ver tu día ›
[upper mark] La tomaste                       14:32
[lower mark] Se fue sin tomar                 12:10
[broken mark] Sin lectura
11:48 · faltó la tarifa
```

No recent-offer rates, hero card or status pill.

### B2 — Hoy, reader off

```text
Hoy                                      Cuenta
[broken mark] Lector apagado desde las 7:50
Tu teléfono quitó el permiso
de accesibilidad.
Sin él, Kompara no ve tus ofertas.
Aún no registramos viajes hoy.
Ayer te quedaron $1,240 aprox.
[ Encender lector ]
```

Yesterday’s valid result stays fully legible.

### B3 — Hoy, first run

```text
Hoy                                      Cuenta
Lector preparado
Abre tu app de viajes.
La primera oferta confirmará la lectura.
Ejemplo
[winning chip] $9.49/km
neto aprox. · $285/h
Tu mínimo: $8.00/km · valor inicial ›
Costos típicos
Elegir mi ciudad
[ Avísame si se apaga ]
```

The sample remains tap-inert. Prepared is not functioning.

### C1 — Verde

```text
[Host top controls remain visible]
[Upper field green]
$9.49/km
neto aprox. · $285/h
[Host offer]
$148.00
Recoger: 6 min · 2.0 km
Viaje: 18 min · 10.0 km
[Host Aceptar]
```

### C2 — Amarillo

```text
[Host top controls remain visible]
[Middle stripe yellow]
$7.16/km
neto aprox. · $191/h
[Host offer]
$120.00
Recoger: 5 min · 2.0 km
Viaje: 22 min · 10.0 km
[Host Aceptar]
```

### C3 — Rojo

```text
[Host top controls remain visible]
[Lower field red]
$2.20/km
neto aprox. · $54/h
[Host offer]
$62.00
Recoger: 12 min · 5.2 km
Viaje: 18 min · 7.1 km
[Host Aceptar]
```

The negative variant uses **`−$1.05/km`**, **`neto aprox. · −$26/h`**, and host fare **`$22.00`**, with the same distances and times.

The tournament sheets’ repeated `$148.00` host is acceptable for comparing appearance, not for the rebuilt fixture screens.

### C4 — Missing fare

```text
[Host top controls remain visible]
[No colour; both rules interrupted]
Sin lectura
faltó la tarifa
[Host offer]
— —
Recoger: 4 min · 1.5 km
Viaje: 15 min · 6.0 km
[Host Aceptar]
```

### C5 — Missing deciding time

```text
[Host top controls remain visible]
[No colour; both rules interrupted]
Falta tiempo
sin semáforo
[Host offer]
$148.00
Recoger: — · 2.0 km
Viaje: — · 10.0 km
[Host Aceptar]
```

No fallback kilometre rate.

### C6 — Reader stopped

```text
[Host remains visible]
[Neutral 200 × 48dp strip; no verdict rules]
Lector detenido
Revísalo al estacionarte
```

Touch-through, five seconds, once per host foreground, only while the service can still draw it. It is not draggable.

### D1 — Tu día

```text
← Tu día                  ‹ Hoy · lunes 19 oct ›
Te quedan hoy                            $1,240
9 viajes tomados · 8 h 10 min en turno
Según tus ofertas · menos gasolina y desgaste · aprox. ›
08:00 – 12:30 · 5 viajes                    $648
12:40 – 14:20 · en pausa: tú lo pausaste
14:20 – 18:00 · 4 viajes                    $592
Ofertas · neto por km   Todas · 23 / Verdes sin tomar · 2
[middle] $7.16/km       14:50 · La tomaste
[upper]  $9.49/km       14:32 · La tomaste
[lower]  $2.20/km       12:10 · Se fue sin tomar
[broken] Sin lectura   11:48 · faltó la tarifa
```

The filter opens a sheet. Keep the open shift ledger; do not imply continuous observation across the unaccounted interval.

### D2 — Offer detail

```text
Oferta de las 14:32 · La tomaste              ⋯
Te quedan aprox.                          $114
$9.49/km · $285/h neto
Llega a tu mínimo de $8.00/km
Tarifa de la oferta                       $148
Distancia: 12.0 km · 2.0 km para recoger
Tiempo: 24 min
Gasolina y desgaste (12.0 × $2.84)         −$34
El pago final puede cambiar.
Ajustar mi mínimo ›
¿Se canceló? Quitar de mi día ›
```

The minimum is the one saved with the offer. Overflow contains **“¿Se leyó mal? Avísanos.”** Cancellation remains direct and offers undo.

### E1 — Ganancias, closed week

```text
Ganancias                         Compartir · ⋯ · Cuenta
Semana / Mes       ‹ 12 – 18 oct · semana pasada ›
Te quedaron esa semana                         $4,980
62 viajes tomados
Lector · menos gasolina, desgaste y coche · aprox. ›
Tu resumen de Uber dice un poco más ›
Lo del coche · ya restado                       −$1,500
Por día · antes de lo del coche
[Bars: L  M  M  J  V  S  D]
Ver cada día · Tu mejor día fue el viernes ›
Tu lugar entre los de Uber en CDMX ›
Antes de gastos · según tu resumen de Uber
```

Bars remain **59/69/66/75/100/65/36%**. The discrepancy opens the explanation containing **`$5,028` / `$5,062`**. **Trae tu resumen** remains in the header overflow. Daily breakdown stays one tap away.

### E3 — Ganancias, month

```text
Ganancias                             Compartir · Cuenta
Semana / Mes
‹ Octubre 2026 · en curso ›
Te quedan en octubre
$12,169
158 viajes tomados · hasta hoy
Lector · menos gasolina, desgaste y coche por día · aprox. ›
Por semana · después de lo del coche
[Bars: 1 – 4 | 5 – 11 | 12 – 18 | 19 oct]
Ver cada semana · La mejor fue 12 – 18 oct ›
IMSS del mes ›
```

Bars remain **28/95/100/21%**. Preserve the month portion **`$1,417`** versus whole boundary week **`$774`** in their existing detail contexts.

### E4 — IMSS del mes

```text
← IMSS del mes                         Octubre ▾
Pagos de tus apps · aprox. según tus registros
Uber                                      $12,608
DiDi                                       $8,432
Tu vigencia la confirma el IMSS.
Kompara no representa al IMSS.
¿Cómo funciona? ›
Resumen del mes en PDF · Premium ›
[ Consultar mi vigencia en el IMSS ]
```

The explanation retains the statutory-income distinction and non-constancia wording. Consultation remains free; no eligibility verdict appears. **B-038** still governs release wording.

### F1 — Comparar, Tus apps

```text
Tus apps / Tu ciudad                       Cuenta
‹ 12 – 18 oct ›
Por km ▾
$1.21
más por km en DiDi
DiDi                                    $10.34/km
Uber                                     $9.13/km
Neto, según el lector.
Manejaste en horarios distintos.
De dónde salen estos datos ›
```

One metric selector replaces the extra tab row. Unavailable hourly comparison explains its missing source in that selector.

### F2 — Comparar, Tu ciudad

```text
Tus apps / Tu ciudad            Compartir · Cuenta
‹ 12 – 18 oct ›
Uber · CDMX · Por viaje ▾
Por viaje, te pagan más que al
78 %
de los conductores comparados.
Tú en Uber                              $149/viaje
Promedio de Uber en CDMX                 $131/viaje
Antes de gastos · según los resúmenes
Datos · 1,240 conductores ›
```

Contribution moves into the data sheet, with separate, default-off consent and its eligibility explanation. Viewing never requires contributing.

### H2b — Suscribirme

```text
← Suscribirme
$79 al mes
Google Play cobra hoy.
Se renueva cada mes hasta que canceles.
Cancela en Google Play; conservas el mes pagado.
Tus 21 días gratis quedan guardados.
Se usan al terminar tu acceso pagado y,
si aplica, el periodo de gracia.
[ Suscribirme por $79 al mes ]
Mejor avísame el 9 de noviembre
Restaurar compra
Términos · Privacidad
```

Remove the weekly equivalent and the hypothetical failed-charge paragraph. Detailed renewal failure behaviour stays in H2c.

## 5. Open forks only the founder should settle

1. **Replace the locked verbatim scope sentences?**  
   **Recommendation: yes, with the short visible source-and-deduction labels above.** Retain approximation and E1’s discrepancy on the root. Approve retiring the sentence templates, not hiding their meaning.

2. **Rename “Tu semáforo” to “Tu mínimo”?**  
   **Recommendation: yes.** Keep verde/amarillo/rojo/gris as the state vocabulary and “sin semáforo” for the existing missing-data message. In settings, explicitly retain both **“Verde desde”** and **“Rojo debajo de”** so the new name does not erase the second threshold.