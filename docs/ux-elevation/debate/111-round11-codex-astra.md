## 1. Diagnosis — why it reads as a template

**Current verdict: FAIL under the Round 11 bar.** The accounting and navigation work is valuable. The visual design still treats Kompara as a collection of dashboard components rather than an instrument for a driver.

Reviewed the 66 rendered frames, all eight contact sheets, the five benchmark images, the assembled HTML and its matching source parts, CSS, fixture, checker, specification, both rubrics, both Round 10 verdicts and competitive analysis. The current checker passes: **154 figures in 50 frames, 23 spec strings and five bar charts**, alongside its date and stale-string checks. That establishes selected numerical consistency, not visual quality.

### The visual system, element by element

| Element | What makes it generic |
|---|---|
| Surfaces | Slate background → lighter card → lighter nested list → boxed icon. Enclosure supplies nearly all the hierarchy. |
| Colour | Emerald means action, selection, functioning, progress, Premium and positive comparison. It competes with the actual green verdict. |
| Typography | Inter 900 makes every amount emphatic in the same way. Supporting text becomes a dense, weakly differentiated grey layer. |
| Chip | Rounded coloured rectangle, circled symbol, heavy money, small qualifier, tiny boxed initial and large shadow: a button-shaped badge. |
| Selectors | Segments, period arrows, metric pills and app/city pills accumulate before the answer. Available and unavailable choices receive similar visual space. |
| Rows | Icons receive containers even when they contribute no information. Many rows have a title, explanatory subtitle and chevron regardless of importance. |
| Charts | Small bars look appended to the page. Their scope, interaction instructions and navigation are separate elements rather than chart anatomy. |
| Copy | Provenance, approximation and deduction explanations repeat because no stable visual location owns them. |
| Spacing | Dense upper halves and empty lower halves are common. Empty space does not help when the information above it remains compressed. |
| Rendering | Navigation glyphs are black against slate. F2/F3 split `78` from `%`. D3b separates a minus sign from its amount. E1 squeezes “Cómo se calculó” into a narrow column. |
| Host | The grid and three white roads remove almost every real placement constraint. The chip is being demonstrated over an empty stage. |

### The ten worst screens

| Screen | The single thing that gives it away |
|---|---|
| **C1** | It looks like a green confirmation button floating over another app. |
| **C7** | A longer number is accommodated by enlarging the same badge to 232dp. |
| **B1** | Money card, observation card and offer-list card form three consecutive containers. |
| **B3** | A centred onboarding composition survives inside the everyday home screen. |
| **A2** | Four boxed icons make a serious disclosure look like a feature checklist. |
| **A5** | Two cards and oversized stepper furniture surround one simple question. |
| **E1** | The chart, its explanation, its drill-in and the comparison entry compete as separate modules. |
| **E4** | Paragraphs carry the hierarchy that the layout should provide. |
| **F2** | Four layers of selection precede and surround the comparison itself. |
| **H5b** | The most consequential reconciliation result is buried inside a dense explanatory card. |

### Where Fable is wrong or incomplete

- **A large colour fill is already peripheral information.** The current chip does not require reading to notice its colour. Its weaknesses are the identical silhouettes, small distinguishing glyphs, button resemblance and competition with the host.
- **E1, E3 and F1 already put their heroes on the background.** Removing B1’s hero card helps, but does not solve the broader system.
- **“6 viajes tomados” is useful scope.** It is not another approximation disclaimer. Keep the count; remove duplicated qualification.
- **Dark mode, Inter and segmented controls are not inherently amateur.** Their indiscriminate application is the problem. A white reskin would retain it.
- **The icons already include vectors.** The problem is excessive boxing and incorrect paint inheritance, not simply replacing emoji.
- **Some explanation must remain prominent.** A2’s capture disclosure, E1’s source discrepancy and H2b’s immediate charge cannot disappear into help.
- **Store screenshots are visual references, not usability evidence.** They show hierarchy and recognisable identities; they do not establish mounted legibility or safe overlay placement.
- **Net-after-costs and personal thresholds are not exclusive capabilities.** The supplied competitive analysis describes both in competing products. Kompara needs a distinctive expression of its promise, supported by its free reader and coherent personal records.

## 2. The signature idea

All three candidates use a neutral carrier, stable text positions, no verdict word, no brand strip, no tap response and no long-press response. **Drag is the only chip gesture.**

The sketches describe geometry, not monospace production typography. `####` denotes the state-coloured mass; dashed rules are structural marks. State names sit outside the rendered chip.

### Candidate A — La cota personal

**Director’s pitch:** “Kompara puts what the trip leaves against the two limits you chose.”

A small open measuring mark sits beside the money. Two fixed horizontal rules represent the green minimum and red cutoff. **Only one coloured block appears:** above, between or below them. Missing information breaks the rules and removes the block.

There are no three lamps, coloured slots, enclosing signal housing, track or movable thumb.

**Footprint: 200 × 88dp at 1×.**

```text
Verde
####   $9.49/km
----
       neto aprox.
----
       $285/h         K
```

```text
Amarillo
       $7.16/km
----
####   neto aprox.
----
       $191/h         K
```

```text
Rojo
       $2.20/km
----
       neto aprox.
----
####   $54/h          K
```

```text
Gris
       Sin lectura
-- --
       faltó la tarifa
-- --
                     K
```

**Exact construction**

- Neutral opaque carrier, 200 × 88dp, 2dp corners, no external shadow.
- Signal occupies `x=8–40`.
- Upper rule: `y=32`, 32 × 2dp. Lower rule: `y=56`, 32 × 2dp.
- Green block: `x=8, y=8, w=32, h=20`.
- Yellow block: `x=8, y=38, w=32, h=12`.
- Red block: `x=8, y=60, w=32, h=20`.
- Grey: both rules have a centred 8dp interruption; no block.
- Text starts at `x=48`; right edge is `192`.
- Main rate: 32sp; unit: 16sp. Qualifier: 13sp. Secondary rate: 16sp.
- Unboxed K: 13sp, lower right, outside the secondary rate’s text bounds.
- The mark is **categorical**, not a miniature proportional chart. It does not claim that the distance between the rules measures pesos.

This correctly accommodates yellow: `$7.16/km` is below `$8.00/km` but above `$6.00/km`. A single rule with yellow crossing it would imply the wrong relationship.

| Surface | Carry-through |
|---|---|
| **Hoy** | Money sits on the page above an open accounting rule; recent offers carry miniature cota marks. |
| **Tu día** | An open ledger uses the same marks beside offer rates and visibly interrupted lines for capture gaps. |
| **Ganancias** | Result, deductions and history align to shared rules; aggregate money remains neutral. |
| **Comparar** | Values align against a clearly labelled reference; the city average never masquerades as the driver’s minimum. |

**Risks**

- **One-second glance:** the coloured area is smaller than today’s badge. The 32dp mark must prove recognisable at 60cm.
- **Occlusion:** the 88dp height needs testing against tall host offers and navigation banners.
- **Colour-vision deficiency:** position and block thickness distinguish states; grey’s broken construction is separate from red.
- **Play review:** a measuring instrument communicates information, but appearance establishes no policy exemption.
- **Control resemblance:** a vertical connecting rail or handle would turn it into a slider. Neither is permitted.
- **Category resemblance:** enclosing the three positions would turn it into a traffic light. Keep the two open rules and one block.

### Candidate B — El recibo que queda

**Director’s pitch:** “Kompara shows the piece of the fare that remains after your costs.”

A neutral receipt has an exposed cut edge. Green raises a shelf, yellow makes a shallow step, red recesses the edge, and grey interrupts it. The cut carries colour; the money area does not.

**Footprint: 196 × 88dp.** Main rate 32sp, unit and secondary rate 16sp, qualifier 13sp. Text stays at the same coordinates in all states.

```text
Verde
    ####
+---+  +------------+
| $9.49/km          |
| neto aprox.       |
| $285/h          K |
+-------------------+
```

```text
Amarillo
+---####------------+
| $7.16/km          |
| neto aprox.       |
| $191/h          K |
+-------------------+
```

```text
Rojo
+---+  +------------+
    ####
| $2.20/km          |
| neto aprox.       |
| $54/h           K |
+-------------------+
```

```text
Gris
+---    ------------+
| Sin lectura       |
| faltó la tarifa   |
|                 K |
+-------------------+
```

The edge feature occupies a fixed 32 × 24dp area; raised, flush and recessed forms stay inside the footprint. Do not add paper texture, perforations or a drop shadow.

| Surface | Carry-through |
|---|---|
| **Hoy** | The daily result becomes an open receipt with recent trips beneath it. |
| **Tu día** | Offers become receipt lines; deductions sit beneath a cut rule. |
| **Ganancias** | The period result closes a concise income-and-cost statement. |
| **Comparar** | Two receipts share one basis and period. |

**Risks**

- **One-second glance:** the edge changes may be too subtle.
- **Occlusion:** the broad neutral receipt remains a substantial opaque object.
- **Colour-vision deficiency:** edge depth supplies a second cue, but requires learning.
- **Play review:** receipt imagery must not imply a payment issued or verified by the host.
- **Control resemblance:** the projecting tab can look draggable in a way that suggests opening a drawer.
- **Product meaning:** costs are represented well; the driver’s personal floor is not. The edge risks becoming decoration.

### Candidate C — El margen propio

**Director’s pitch:** “Kompara makes the room between what a trip leaves and what you require visible.”

Two open brackets frame the amount. Green has clear separation, yellow closes the space, red overlaps the reference, and grey leaves the brackets incomplete.

**Footprint: 200 × 88dp.** Two 6dp bracket strokes, fixed text position, 32sp main rate, 16sp secondary rate and 13sp qualifier.

```text
Verde
[    $9.49/km      ]
     neto aprox.
     $285/h       K
```

```text
Amarillo
   [ $7.16/km ]
     neto aprox.
     $191/h       K
```

```text
Rojo
   ][ $2.20/km
      neto aprox.
      $54/h       K
```

```text
Gris
[     Sin lectura
      faltó la tarifa
                  K
```

Brackets are coloured; the background remains neutral. Their geometry changes categorically, without animated closing or squeezing.

| Surface | Carry-through |
|---|---|
| **Hoy** | Brackets gather the daily result and its scope. |
| **Tu día** | Each offer retains its original bracket relationship. |
| **Ganancias** | A bracket groups income and deductions into the period result. |
| **Comparar** | A labelled span expresses the difference between two values. |

**Risks**

- **One-second glance:** negative space is weaker than a substantial solid mark.
- **Occlusion:** moving brackets must never move the number or change the outer bounds.
- **Colour-vision deficiency:** bracket relationships survive colour loss, but may not be understood.
- **Play review:** no special advantage; the same disclosure and behaviour obligations remain.
- **Control resemblance:** it resembles selection handles or a crop tool.
- **Meaning:** “overlap” is an abstract way to communicate falling below a threshold.

### Choice: La cota personal

It expresses the personal rule directly, preserves a stable number, and has an obvious relationship to an auditable ledger. The receipt is stronger on subtraction than thresholds. The brackets demand too much interpretation.

**The chosen direction is a testable hypothesis, not a proven glance improvement.** Its weakest point is the smaller colour mass. If drivers cannot identify the four states quickly, increase the mark’s area within the same footprint before adding words or returning to a coloured badge.

For every concept, Kompara remains a general driver utility using AccessibilityService; a distinctive shape does not make it an accessibility tool. Google’s policy ties that designation to disability support and requires prominent disclosure and affirmative consent for other qualifying uses. Keep A2 and the declaration truthful. [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en)

## 3. Visual system v3

### Palette

Use a warm paper surface by day and a dark green-black surface at night. Follow the system appearance; do not add a theme question to onboarding.

| Token | Day | Night | Replaces |
|---|---|---|---|
| `--background`, `--surface` | `#F5F3EB` | `#141D18` | Slate-900 |
| `--surface-raised` | `#FFFFFF` | `#202B23` | Slate-800 sheets |
| `--surface-field` | `#E9EDE5` | `#28372D` | Generic card backgrounds on inputs |
| `--text-strong`, `--text-body` | `#17231E` | `#F5F3EB` | Slate-50 |
| `--text-muted` | `#536158` | `#B8C3B8` | Slate-300 |
| `--border` | `#C5CCC3` | `#526357` | Slate-600 |
| `--rule-strong` | `#233A2B` | `#D8E4D8` | Unspecified chart/divider treatments |
| `--brand` | `#226346` | `#9FCCA9` | Emerald aliases |
| `--action` | `#17231E` | `#E6EBDD` | Emerald primary buttons |
| `--on-action` | `#FFFFFF` | `#17231E` | White in every mode |
| `--attention` | `#785A17` | `#E2C579` | Saturated amber warning furniture |
| `--error` | `#9D3B35` | `#F0A59C` | Verdict red reused for general errors |
| `--chip-neutral` | `#48544C` | `#48544C` | Slate-600 neutral verdict |

**Retain the verdict triple exactly:**

| State | Colour / on-colour | Contrast |
|---|---|---:|
| Verde | `#187A36` / `#FFFFFF` | 5.42:1 |
| Amarillo | `#F2B705` / `#1A1300` | 10.16:1 |
| Rojo | `#D32F2F` / `#FFFFFF` | 4.98:1 |

Day body text achieves approximately **14.59:1**, muted text **5.87:1**. The chip’s numbers use neutral ink on its neutral carrier.

Yellow against the paper surface is only approximately **1.64:1**. Give every signal block a 2dp contrasting keyline: dark in day mode, light in night mode. The mark must remain identifiable without its fill.

Green is not a reward for a positive daily total. Verdict colours belong to individual offer evaluations.

### Type

**Money:** Roboto Condensed 700.  
**UI:** Roboto 400, 500 and 600.

Bundle the exact fonts for both mockups and Android. Require tabular numerals; do not simulate condensed text with horizontal scaling.

Only five size steps:

| Size / line height | Use |
|---|---|
| **13 / 18sp** | Scope, units where necessary, chart labels, legal terms |
| **16 / 22sp** | Body, rows, buttons, secondary chip rate |
| **22 / 28sp** | Screen titles, neutral chip causes |
| **32 / 36sp** | Chip money, compact money comparisons, recovery headline |
| **48 / 52sp** | One period total |

Replace `--font`, `.hero .value`, `.chip .rate`, `.stepper b` and their inline overrides. Remove weight 900 and the separate 56sp hero tier.

Keep minus sign, currency, amount and unit inseparable. No line break inside `−$1.05/km`, `$149/viaje` or `78 %`.

### Surfaces and rhythm

Replace cards-in-cards with **one page, open rows and deliberate rules**.

- Page inset: **20dp**.
- Spacing: **4, 8, 12, 20, 32dp**.
- Ordinary rows: **56dp minimum**.
- Controls: **48dp minimum hit area**.
- Primary button: **52dp**, radius **8dp**.
- Sheets: radius **20dp** at the top, no nested list background.
- Chip: radius **2dp**, no shadow.
- Ordinary information groups: no radius, fill or elevation.
- Separate groups with 32dp of space; use a 1dp rule only when it clarifies a ledger or boundary.
- Every scroll container ends above its pinned controls with explicit bottom padding.

In `base.css`:

- Retire `.card` as the universal content wrapper.
- Make `.list` transparent; remove its rounded enclosure.
- Remove backgrounds from `.row .ic`.
- Replace `.seg` and metric-pill furniture with text tabs and an underline.
- Retain native switches and text fields where they express real controls.
- Remove `.chip.wide`, `.chip .strip`, `.g` and `.k-ov`.
- Remove the accumulated emerald/slate overrides rather than appending another override layer.

### Icons and brand

Use one 24dp vector family with a consistent optical weight. Icons serve navigation or identify an otherwise ambiguous action; they do not decorate every paragraph.

Set SVG paint explicitly to `currentColor`. The black navigation icons in the current renders are a defect.

The K becomes an **unboxed letterform**. Keep it small on the chip and larger on export or identity surfaces. No square, translucent badge or separate brand band.

Bottom navigation stays **Hoy · Ganancias · Comparar**, with visible labels, correctly painted icons and a short selected underline. No coloured navigation container.

### Motion

- Chip content and state geometry update atomically.
- Never animate the block travelling through yellow to green.
- Never interpolate money or count it upward.
- Initial appearance: at most **100ms opacity fade**.
- New offer or host change: clear old values immediately.
- Preserve the existing **500ms departure grace** only for the same offer’s disappearance.
- In-app transitions: **160ms**, simple opacity and translation.
- Reduced motion removes translations.
- No pulse, bounce, celebration, haptic or sound.
- C6 remains visible for **5 seconds**, once per host foreground, with touches passing through.

### A credible host mock

Replace `.map` and `.road` with a designed, unbranded ride-app scene:

- Irregular street geometry, minor and arterial roads, a park and subdued street labels.
- A route polyline, pickup and destination markers, and a driver-position marker.
- A real top status/navigation region and map controls.
- A bottom offer surface with the **existing fixture fare, pickup and trip figures**.
- Host fare at **48sp**; host acceptance control at **52dp**.
- No platform logo, branded vehicle icon or copied platform lockup.

Produce both a light map and dark navigation version. Include a taller offer and a top navigation banner so the safe region changes.

For the 393 × 852dp reference, begin with an offer spanning approximately `y=548–832`; then test a taller variant. The chip’s position must come from the supported layout’s exclusion regions, not a universal `top:72px`.

Protect the fare, route information and controls. Revalidate saved per-app positions after layout changes. Suppress the chip when no safe position fits.

## 4. Radical simplification, screen by screen

The wireframes show content groups, not literal text wrapping. Production copy wraps at the specified type size. Status bars retain their existing fixture values. Bracketed graphic descriptions are annotations, not strings displayed to drivers.

A single scope line applies to the relevant amount or ledger. **Source and deduction basis remain explicit; colour cannot communicate them reliably.**

### A1 — Demo

**Driver:** “Veo cuánto me deja una oferta antes de decidir.”

**Delete:** introductory paragraph, permanent four-state paragraph, coloured hero container and tonal demo button.  
**Keep:** real example, exact chip, one current-state explanation, example cycling, start and existing-account route.

```text
K Kompara
Antes de aceptar, ve si te deja.

Ejemplo
$148
12 km en total (2 para recoger) · 24 min
[cota] $9.49/km
       neto aprox. · $285/h
Llega a tu mínimo.

Ver otra oferta
[ Empezar ]
Ya tengo cuenta
```

“Ver otra oferta” cycles through all four states without changing settings. Its explanation changes with the example:

- “Llega a tu mínimo.”
- “Entre tu mínimo y tu rojo.”
- “Abajo de tu rojo.”
- “Faltan datos para calcular.”

### A2 — Disclosure

**Driver:** “Entiendo qué lee Kompara y decido si le doy permiso.”

**Delete:** decorative icons, repeated supported-app sentence and feature-list styling.  
**Keep:** full-screen capture, immediate deletion, retained figures, transmitted counters, separate optional flows, read-only behaviour, accessibility purpose and genuine refusal.

```text
< Lo que hace Kompara
Para mostrarte lo que deja una oferta

Lee tu pantalla
[texto completo]
Guarda las cifras
[texto completo]
Qué sale del teléfono
[texto completo]
Tú decides
[texto completo]

[ Acepto ]
No acepto
```

The four blocks are always expanded, not accordions. Exact reduced copy:

**Lee tu pantalla.** “Usa Accesibilidad de Android. Cuando aparece una oferta en Uber o DiDi, toma una imagen de toda tu pantalla para leer tarifa, distancia y tiempo. La borra al momento.”

**Guarda las cifras.** “Conserva las cifras de tus viajes en este teléfono. No guarda imágenes ni datos del pasajero.”

**Qué sale del teléfono.** “Envía conteos anónimos de lectura; puedes apagarlos en Tus datos. Solo si tú lo eliges: respalda tus totales con tu cuenta, aporta al promedio de tu ciudad, sube un archivo o envía un reporte de lectura.”

**Tú decides.** “Kompara nunca acepta ni rechaza por ti. Las apps de viajes podrían no permitir estas herramientas.”

This is the legitimate exception to extreme brevity. Simplify its presentation without hiding material facts.

### A5 — Costs

**Driver:** “Le digo cuánto rinde mi coche para que reste mis costos.”

**Delete:** introductory explanation and both surrounding cards.  
**Keep:** one editable value, visible assumptions, derived cost, direct-cost alternative and explicit default acceptance.

```text
< ¿Cuánto rinde tu coche?
Puedes ajustarlo después.

−           12 km/L           +
Típico de un sedán

Gasolina: $24.50/L · referencia
Desgaste: $0.80 por km
Manejar te cuesta $2.84 por km
En 12 km: unos $34

Ya sé mi costo por km
[ Usar 12 km/L (típico) ]
```

The cost result is subordinate to the editable value. Untouched defaults remain labelled typical downstream.

### B1 — Hoy, functioning

**Driver:** “Kompara está leyendo y hoy me quedan unos $762.”

**Delete:** three enclosing surfaces, clock icon, repeated approximation and redundant verdict words in recent rows.  
**Keep:** status, total, trip count, best-hour observation, three offers and day navigation.

```text
Hoy                              Cuenta
● Lector funcionando
Te quedan hoy
$762
6 viajes tomados
Según tus ofertas · menos gasolina y desgaste · aprox. >
Tu mejor hora hoy: 9 a 10 h

Últimas ofertas                 Ver tu día >
[cota] 14:32  La tomaste
[cota] 12:10  Se fue sin tomar
[cota rota] 11:48  Sin lectura · faltó la tarifa

Hoy          Ganancias          Comparar
```

The scope line opens calculation detail. Offer rows open their own detail; the hero does not contain competing nested tap targets.

### B2 — Hoy, reader off

**Driver:** “Se perdió un permiso y aquí puedo recuperarlo.”

**Delete:** duplicate off badge, warning card enclosure and opacity applied to valid history.  
**Keep:** exact cause, outage time, absence-of-records wording, yesterday’s result and recovery action.

```text
Hoy                              Cuenta

Lector apagado
Tu teléfono quitó el permiso
de accesibilidad.
Desde las 7:50

Aún no registramos viajes hoy.
Ayer te quedaron $1,240 aprox.

[ Encender lector ]

Hoy          Ganancias          Comparar
```

Use a short error-coloured rule beside the recovery heading. Yesterday’s amount remains fully readable and available.

### B3 — Hoy, first run

**Driver:** “Está preparado; falta que llegue una oferta real.”

**Delete:** “Listo”, centred welcome-page styling, repeated demo instructions and the second “Ver ejemplo” route.  
**Keep:** prepared status, explicit example, first-read distinction, default rule, typical-cost label, city route and optional notification ask.

```text
Hoy                              Cuenta
● Lector preparado

Abre tu app de viajes.
La primera oferta confirmará la lectura.

Ejemplo
[cota] $9.49/km
       neto aprox. · $285/h

Valor inicial: $8.00/km · costos típicos
Elegir mi ciudad
[ Avísame si se apaga ]
Hoy          Ganancias          Comparar
```

The demonstration remains unmistakably an example. It cannot establish “funcionando”.

### C1 — Verde

**Driver:** “Después de mis costos, esta oferta llega a mi mínimo.”

**Delete:** coloured body, check, circle, square K and shadow.  
**Keep:** both rates, qualifier, personal-rule relationship and host figures.

```text
####   $9.49/km
----
       neto aprox.
----
       $285/h         K

[mapa y ruta de la app anfitriona]
$148.00
Recoger          6 min · 2.0 km
Viaje           18 min · 10.0 km
[ Aceptar: control de la app anfitriona ]
```

### C2 — Amarillo

**Driver:** “Queda entre mi mínimo y mi rojo.”

**Delete:** warning symbol, circle and yellow button-like container.  
**Keep:** the middle-band mark and exact figures.

```text
       $7.16/km
----
####   neto aprox.
----
       $191/h         K

[mapa y ruta de la app anfitriona]
$120.00
Recoger          5 min · 2.0 km
Viaje           22 min · 10.0 km
[ Aceptar: control de la app anfitriona ]
```

### C3 — Rojo

**Driver:** “Con mis costos, queda abajo de mi rojo.”

**Delete:** cross and red action-like surface.  
**Keep:** lower-band mark, positive amount where appropriate and exact host figures.

```text
       $2.20/km
----
       neto aprox.
----
####   $54/h          K

[mapa y ruta de la app anfitriona]
$62.00
Recoger         12 min · 5.2 km
Viaje           18 min · 7.1 km
[ Aceptar: control de la app anfitriona ]
```

Red does not mean negative money. The separate C7 fixture remains **`−$1.05/km · −$26/h`**, using the same 200 × 88dp construction.

### C4 — Missing fare

**Driver:** “No leyó la tarifa; no tengo un cálculo.”

**Delete:** question-mark button and generic grey badge enclosure.  
**Keep:** cause, broken mark, no monetary rate and unchanged host inputs.

```text
       Sin lectura
-- --
       faltó la tarifa
-- --
                      K

[mapa de la app anfitriona]
— —
Recoger          4 min · 1.5 km
Viaje           15 min · 6.0 km
[ Aceptar: control de la app anfitriona ]
```

### C5 — Missing deciding time

**Driver:** “Elegí decidir por hora, pero falta el tiempo.”

**Delete:** instruction to open detail while viewing the offer and the question-mark circle.  
**Keep:** “Falta tiempo”, “sin semáforo”, broken mark and complete absence of a rate.

```text
       Falta tiempo
-- --
       sin semáforo
-- --
                      K

[mapa de la app anfitriona]
$148.00
Recoger               — · 2.0 km
Viaje                — · 10.0 km
[ Aceptar: control de la app anfitriona ]
```

Do not substitute `$9.49/km`. That information remains in parked offer detail.

### C6 — Reader stopped

**Driver:** “Se detuvo el lector; lo reviso cuando me estacione.”

**Delete:** badge shadow, boxed K and any suggestion of an overlay action.  
**Keep:** neutral state, parking instruction, bounded duration and touch-through behaviour.

```text
[app anfitriona: Conectado]

-- --  Lector detenido
       Revisa Kompara cuando
       te estaciones.
-- --                    K

[mapa y controles de la app anfitriona]
```

Use 16sp for the title and 13sp for the instruction. This is the **noninteractive exception**, not a draggable offer chip. It appears only when the service remains alive to draw it.

### D1 — Tu día

**Driver:** “Veo qué pasó durante mi día y de dónde salió el total.”

**Delete:** card backgrounds, circled verdicts and repeated approximation on every taken-offer row.  
**Keep:** day, total, duration, shifts, deliberate pause, filters, outcomes and rates.

```text
< Tu día                    Hoy · lunes 19 oct >
Te quedan hoy                         $1,240
9 viajes tomados · 8 h 10 min en turno
Según tus ofertas · menos gasolina y desgaste · aprox. >
08:00 – 12:30 · 5 viajes                $648
12:40 – 14:20 · tú lo pausaste
14:20 – 18:00 · 4 viajes                $592
Ofertas: Todas · 23 / Verdes sin tomar · 2
[cota] 14:50  La tomaste           $7.16/km neto
[cota] 14:32  La tomaste           $9.49/km neto
[cota] 12:10  Se fue sin tomar     $2.20/km neto
[rota] 11:48  Sin lectura · faltó la tarifa
Hoy              Ganancias              Comparar
```

The filter is one control opening its options, not two competing filled pills. The ledger scrolls; it is not compressed to fit every offer above navigation.

### D2 — Offer detail

**Driver:** “Entiendo qué dejó esta oferta y puedo corregir mi registro.”

**Delete:** verdict pill, nested table container and paragraph repeating the rate.  
**Keep:** historical minimum, calculation inputs, outcome, uncertainty, report route, threshold adjustment and cancellation.

```text
Oferta de las 14:32
La tomaste · el pago final puede cambiar.
Te quedan                               $114
Menos gasolina y desgaste · aprox.
[cota] $9.49/km neto · $285/h
Llega a tu mínimo de $8.00/km
Tarifa de la oferta                     $148
Distancia       12.0 km · 2.0 km para recoger
Tiempo                                24 min
Gasolina y desgaste                     −$34
12.0 × $2.84
¿Se leyó mal? Avísanos >
Ajustar mi mínimo >
¿Se canceló? Quitar de mi día >
```

Cancellation remains operable and offers undo. The sheet uses normal rows; none of these actions becomes a floating control over the host.

### E1 — Closed week

**Driver:** “Esa semana me quedaron unos $4,980, con lo del coche ya restado.”

**Delete:** coloured comparison card, boxed deduction row, prose beside a cramped calculation link and large outlined share button.  
**Keep:** shared period, total, source discrepancy, deduction, chart, direct daily breakdown, comparison, share and import.

```text
Ganancias                                Cuenta
Semana                         Mes
< 12 – 18 oct · semana pasada >
Te quedaron esa semana                   $4,980
62 viajes tomados
Lector · menos gasolina, desgaste y coche · aprox. >
Tu resumen de Uber dice un poco más >
Lo del coche · ya restado                 −$1,500
Por día · antes de lo del coche
[7 barras: L  M  M  J  V  S  D]
Ver cada día · Tu mejor día fue el viernes >
Tu lugar entre los de Uber en CDMX >
Compartir tu semana          Trae tu resumen
Hoy              Ganancias              Comparar
```

The comparison row retains the subordinate label **“Antes de gastos · según tu resumen de Uber”**. It describes a different basis from the earnings hero and must not be silently conflated with it.

Keep chart heights **59/69/66/75/100/65/36%**. Tapping a bar reveals its amount; “Ver cada día” opens E1b directly. Integrate the best-day sentence into this chart footer.

### E3 — Month

**Driver:** “Veo lo que me queda en octubre y cómo se reparte por semana.”

**Delete:** separate card enclosures, repeated “hasta hoy” explanations and the large share button.  
**Keep:** month scope, total, count, proportional chart, weekly breakdown and free IMSS entry.

```text
Ganancias                                Cuenta
Semana                         Mes
< Octubre 2026 · en curso >
Te quedan en octubre
$12,169
158 viajes tomados · hasta hoy
Lector · menos gasolina, desgaste y coche por día · aprox. >
Por semana · después de lo del coche
[4 barras: 1 – 4 | 5 – 11 | 12 – 18 | 19 oct]
Ver cada semana >
La mejor fue 12 – 18 oct
IMSS del mes >
Compartir tu mes
Hoy              Ganancias              Comparar
```

Keep heights **28/95/100/21%**. E3d remains one tap away. Preserve the distinction between October’s **$1,417** contribution and the complete boundary week’s **$774**; neither replaces the other.

### E4 — IMSS launch state

**Driver:** “Veo lo que registraron mis apps y consulto mi vigencia en el IMSS.”

**Delete:** payment card, two long paragraphs and inline link embedded inside prose.  
**Keep:** estimated payments, official verification action, non-representation, explanation route and subordinate PDF.

```text
< IMSS del mes                    Octubre v

Pagos de tus apps
Aprox. según tus registros
Uber                               $12,608
DiDi                                $8,432

Tu vigencia la confirma el IMSS.
Kompara no representa al IMSS.
¿Cómo funciona? >

Resumen del mes en PDF · Premium >

[ Consultar mi vigencia en el IMSS ]
```

The explanation sheet carries the distinction between recorded payment and statutory income, and the “no constancia” explanation. The primary remains free. No threshold, progress graphic, “faltan” amount or coverage verdict enters this screen.

### F1 — Tus apps

**Driver:** “En mis viajes de esa semana, DiDi me dejó $1.21 más por kilómetro.”

**Delete:** metric-pill row, repeated “según el lector” subtitles, enclosing comparison list and unavailable-hour explanation on the root.  
**Keep:** scopes, week, metric selection, exact difference, comparable rates and a short limitation.

```text
Comparar                                Cuenta
Tus apps                       Tu ciudad
< 12 – 18 oct >
Por km v

$1.21 más por km en DiDi
Según el lector · después de gasolina y desgaste
Uber                         $9.13/km neto
DiDi                        $10.34/km neto

Solo compara tus viajes de esa semana.
De dónde salen estos datos >

Hoy              Ganancias              Comparar
```

“Por km” opens a metric sheet in the existing order. “Por hora” explains the missing DiDi statement there; it never silently switches metric.

The provenance sheet retains the stronger caveat about different working hours. The root states an observation, not a recommendation to choose DiDi.

### F2 — Tu ciudad

**Driver:** “Por viaje, mi resumen está por encima del 78 % del grupo comparable.”

**Delete:** sentence card, twenty-dot population graphic, metric pills, two app/city pills and repeated basis captions.  
**Keep:** scope, period, editable comparison context, complete standing, both rates, sample size and provenance.

```text
Comparar                                Cuenta
Tus apps                       Tu ciudad
< 12 – 18 oct >
Uber · CDMX · Por viaje v
Por viaje, te pagan más que al
78 %
de los conductores comparados.
Antes de gastos · según los resúmenes
Tú en Uber                         $149/viaje
Promedio de Uber en CDMX            $131/viaje
Datos · 1,240 conductores >
Aportar al promedio >
Compartir
Hoy              Ganancias              Comparar
```

The context row opens a sheet with distinct app, city and metric fields. The standing remains one grammatical statement; do not present `78 %` as an unlabeled achievement.

“Aportar al promedio” opens the existing default-off consent with its confirmed-week eligibility sentence. Viewing never requires contributing. This is progressive disclosure of an optional action, not removal of consent.

The data sheet retains the waiting-time limitation and metric availability. Keep the sample size below the main comparison block so it does not become a fourth competing hero.

### H2b — Subscribe

**Driver:** “Sé qué me cobran hoy y qué pasa con mis días gratis.”

**Delete:** green price card, weekly price equivalent, boxed terms and the hypothetical failed-payment paragraph.  
**Keep:** price, immediate charge, renewal, cancellation, banked days, reminder, restore and legal links.

```text
< Suscribirme

Google Play cobra hoy
$79 al mes
Se renueva cada mes hasta que canceles.
Cancela en Google Play;
conservas el mes pagado.

Tus 21 días gratis quedan guardados.
Se usan al terminar tu último mes pagado.

[ Suscribirme por $79 al mes ]
Mejor avísame el 9 de noviembre
Restaurar compra
Términos · Privacidad
```

The removed **≈ $18 a la semana** is not replaced by another equivalent. Failed-renewal rules remain in H2c, including grace and banked access. Immediate billing must remain visible before the primary action.

## 5. Score the current version on criteria 9–12

### Current craft score

| Criterion | Score | Evidence |
|---|---:|---|
| **9. Signature idea** | **3/10** | No product-specific visual relationship connects chip, ledger and comparison. The K square supplies identity by attachment. |
| **10. Glance physics** | **5/10** | Strong colour mass and stable units help; identical silhouettes, action-like glyphs and unrealistic host placement remain. |
| **11. Craft** | **4/10** | Default palette, excessive containers, black navigation glyphs, broken numeric wrapping and weak footer/fold control. |
| **12. Radical simplicity** | **4/10** | The answers exist, but repeated scope prose and layers of selectors surround them. |
| **Total** | **16/40 — FAIL** | Below 34/40 and below 8 in every criterion. |

### Hard-fail check

| Hard fail | Current result |
|---|---|
| Coloured rounded rectangle containing number and glyph | **Present: C1–C3 and C7.** |
| Same-weight card composition | **Present: G3’s repeated field/card blocks and several dashboard compositions.** |
| K square as brand | **Present throughout the chip, sharing and notifications.** |
| Tailwind default palette | **Present explicitly in `base.css`.** |
| Auto-action | Not proposed. |
| Platform logos in product mockups | Not present. Benchmark images are references. |
| Paywall blanking the free product | Not established. Own totals remain visible. |
| Multiple onboarding asks | Not established; optional alternatives are not additional required asks. |
| Demonstrated one-second driving failure | **Unmeasured.** Static inspection cannot establish a timed result. |

C7 also violates the new **≤200 × 88dp** proposal constraint, although its 232dp exception was previously authorised.

### Do criteria 1–8 still hold at ≥8?

1. **One job per screen — 9: yes.** The core questions remain coherent despite excess presentation.
2. **One primary action — 9: yes.** Recovery and purchase generally have a clear primary.
3. **Navigation — 9: yes.** Three tabs, shared periods and the added breakdown sheets remain sound.
4. **Numbers discipline — 8: yes.** Selected arithmetic passes; numeric wrapping and hierarchy need repair.
5. **Copy — 7: no.** The rendered repetition and dense scope paragraphs do not support the previous 8.
6. **States designed — 9: yes.** Coverage is extensive and distinctions are mostly truthful.
7. **Time-to-value — 8: yes, provisionally.** Value precedes identity; actual setup time remains unmeasured.
8. **Consistency and feel — 7: no.** The rendered icon, wrapping, skeleton and footer defects prevent automatic carryover.

**Structural total: 66/80.** I would not preserve the Round 10 pass mechanically. The full rendered evidence exposes weaknesses that the earlier source-level review did not establish.

### Forecast for this proposal

**Conditional design target:** signature **9**, glance **8**, craft **9**, simplicity **9** = **35/40**. Structural target: **71/80**, with no criterion below 8.

Those are forecasts, not awarded scores. To believe them:

- **Render first:** all 66 states in the new system, with real fonts; inspect 1× output, enlarged text, day and night. Check negative amounts and inseparable units.
- **Test the signature:** after the demo, ask drivers to sketch and explain the four marks. Yellow must mean between their limits; grey must mean missing information.
- **Test mounted recognition:** one-second exposures at 60cm, daylight and night, including drivers with colour-vision differences. Record state identification separately from reading the rate.
- **Use explicit failure criteria:** any grey-as-red confusion triggers redesign; any covered host decision control fails placement validation.
- **Test control resemblance:** observe whether drivers try to tap the chip to accept, reject or expand it.
- **Replay real layouts:** supported host versions, tall offers, banners, navigation, font scaling and saved positions. Measure suppression frequency as well as overlap.
- **Test money comprehension:** B1 versus E1, reader versus statement, day versus fixed-cost deductions, and F2’s before-expenses basis.
- **Test purchase comprehension:** users must identify that `$79` is charged today, renewal is monthly, and the `21 días` begin after paid access ends.
- **Rerun the fixture checker after rebuilding.** Extend geometry and state checks where needed; do not treat text-presence assertions as a visual test.

## 6. What must not change

| Locked decision | Preserve |
|---|---|
| **Three tabs** | Hoy · Ganancias · Comparar. Cuenta stays behind the account affordance. |
| **Reader is status** | Prepared, functioning, paused and off remain distinct. A demo never proves capture. |
| **Drag-only chip** | Tap and long-press do nothing. Threshold editing remains in the app. C6 remains touch-through. |
| **Gris never red** | Missing required data removes the rate. No automatic unit substitution. |
| **No auto-action** | No accept, reject, dispatch or host-control manipulation. |
| **No platform logos** | Names are plain text; the realistic host remains unbranded. |
| **“Te quedan” plus qualification** | Keep the money vocabulary, correct tense, source and deduction basis. Remove duplication, not meaning. |
| **Optional signup** | Reader value and recovery remain available without WhatsApp or network access. |
| **Free IMSS review** | Official verification is the primary; accountant PDF stays subordinate. |
| **Fixture arithmetic** | Preserve exact figures, precision, rounding, coverage, dates and source distinctions. |
| **B-038 legal gate** | E4 remains the launch state. No statutory thresholds, factors or coverage claims migrate from E5. |
| **Personal ownership** | Own totals remain visible; missing data never becomes a purchase requirement. |
| **Explicit consent** | Capture disclosure, import consent and benchmark contribution remain distinct and truthful. |

Three presentation decisions explicitly change under this round:

1. **Geometry fork:** the new 200 × 88dp chip supersedes the old 232dp exception. Fit the negative fixture through typography and reclaimed glyph/brand space, not smaller text or a missing sign. At enlarged system text, reflow and revalidate placement; suppress when no safe placement exists.
2. **Appearance fork:** system-responsive warm light/dark surfaces supersede dark-first slate. The verdict colours and their semantics remain unchanged.
3. **Disclosure-placement fork:** repetitive explanations and optional comparison controls move to named detail surfaces. Material capture, billing, source and missing-data facts remain visible where the decision occurs.

The square K is removed. The letter K, green identity and the driver’s control over the decision remain.