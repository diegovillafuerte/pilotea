# Round 11, Opus 5.5: "Tu raya"

**What I reviewed:** all 66 rendered frames (`r11/frames/*.png`, and every contact sheet A–H; I opened C1–C6, A1, A2, A5, B1–B3, D1, D2, E1, E3, E4, F1, F2, H1, H2b, H3 and G2 at full size), the five named benchmarks, and three extra benchmark files in the same folder (`uber-badge-1.jpg`, `uber-badge-2.png`, `uber-offer-card-lightbox.jpg`). I also read `index.html`, `base.css`, `fixture.json`, `check-fixture.py` (it currently prints "checked 154 figures in 50 frames … → OK"), spec §§0–12, both rubrics, both Round 10 verdicts and `docs/competitive-analysis.md`.

**Verdict in one paragraph.** Rounds 1–10 produced an honest product drawn in a borrowed dialect. Every surface is Tailwind slate. Every action, link and selection is emerald. There are 18 type sizes, and weight 900 is the only tool for hierarchy. There are 47 cards and 37 pills in the source, plus a circled glyph on every list row. The chip is the category's badge. It is also, typographically, Uber's own gross badge printed in green, and its verde and rojo are the same lightness. Nothing in the visual system comes from the one thing only Kompara does: putting the offer next to *the driver's own line*. That line should be the whole product.

---

## 1. Diagnosis: why it reads as a template

### 1.1 The visual system, element by element

| Element | What the frames show | Why it is the template |
|---|---|---|
| **Surface** | `--background #0f172a`, cards `#1e293b`, page `#0b1120`. These are Tailwind slate-900/800 hex values copied verbatim. | The blue-black slate cast is the signature of generated dashboards. Darkness itself is not the problem. The problem is this exact navy plus a lighter card on top. |
| **Accent** | Emerald `#047857`/`#059669`/`#34d399` on every primary button, every link ("Cómo se calculó", "Ver tu día ›"), the status pill, the active tab, the active metric pill, H1's benefit slab, H2b's price slab and H3's share card. | The brand green has no job, so it is everywhere. It also sits next to verdict verde `#187a36`. On A1 a green button is 300dp below a green "good trip" chip, and on H1 two emerald slabs are stacked. The brand dilutes the one colour that must mean something. |
| **Type** | Inter, 18 sizes in `base.css` (10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 24, 28, 30, 34, 40, 44, 56), hero 44px/900 at −0.03em, inside a card. | Inter is the default face of every LLM mockup. Black 900 used as the only hierarchy device is "make it bold until it looks important". There is no scale, only a ladder of sizes. |
| **Containers** | 47 `class="card…"`. The hero, status, observation, offer list, car-cost row, Comparar card, "Ver cada día", the provenance row and the import notice each sit in their own rounded slate box. | Cards-in-cards: every element gets equal weight, so nothing is the screen. Uber puts the number on the surface. |
| **Selectors** | 37 `class="pill…"` plus four segmented controls (Semana/Mes, Tus apps/Tu ciudad, Gasolina/Eléctrico, Decide por km/hora), status pills, "Ejemplo" pills, the "Verde" pill (D2) and the "Octubre ▾" pill (E4). | A pill is the generated-UI answer to "how do I select". Here there are so many that the eye cannot tell a selector from a label from a status. |
| **Icons** | ✓ / ✕ / ? in translucent or solid circles on every offer row, plus 36dp tinted icon squares on every settings, benefit and import row (G1, H1, H4, A2). | Circled status glyphs and tinted icon tiles are the default list pattern of a Material starter kit. |
| **Chip** | 16dp-radius colour card, a translucent-circle glyph, a 30sp Black number, a 15sp second line, a 16dp "K" square at 25 %, a 24dp drop shadow. | It is GigU's overlay (a white card with a green border and three numbers) as a solid fill. It is the "verdict badge" every model draws. §1.2 shows it also fails the physics it was meant to deliver. |
| **Brand** | A "K" in a 24dp emerald rounded square (topbar, share card, notifications, chip). | It is a favicon, not a mark. It also collides with the hard-fail list ("a K square as the brand"). |
| **Host mock** | A grey grid with three white bars and a white card at the bottom. | It looks like a wireframe, so the chip reads as a toast over a prototype. Worse, it hides a real conflict (see §1.4, item 4). |
| **Motion** | A 100 ms fade, a 120 ms crossfade, "no pulse". | Correct restraint, but motion carries no identity at all: nothing tells you the chip belongs to the frame of the phone rather than floating over it. |
| **Craft defects visible in the PNGs** | F2 and H3 break "78 / %" across two lines (no non-breaking space). E1 squeezes "Cómo / se / calculó" into a three-line right gutter, and B1, D1 and E3 do the same in two lines. E4 has a text seam ("…representa al IMSS" / gap / "ni a ninguna autoridad.") and an orphaned "(imss.gob.mx/vigencia)". On B3 the "Ejemplo" pill collides with the chip's K, and the small print leaves "típicos ·" stranded. D2 uses an orange text action, a fifth colour. H6 prints an English developer note inside the phone ("No money in any notification…"). E3c's annotation spills over the phone. D1's list is cut by the nav bar with no fade. | Each one alone is small. Together they are the difference between "prototype" and "product". |

### 1.2 The chip, measured

These are relative luminances (WCAG) of the chip fills as shipped:

| Fill | Hex | Luminance | Against verde |
|---|---|---|---|
| verde | `#187a36` | 0.144 | — |
| rojo | `#d32f2f` | 0.161 | **1.09 : 1** |
| gris | `#475569` | 0.089 | 1.40 : 1 |
| amarillo | `#f2b705` | 0.528 | 2.98 : 1 |

What follows from these numbers:

- **Verde, rojo and gris are almost the same lightness.** Peripheral vision and low-light vision work mostly on lightness, so in both the three chips are one mid-dark blob of identical size and shape. Only amarillo stands out.
- **For a deuteranope or protanope, verde and rojo collapse further** (protan red darkens). The only colour-blind-safe cue is the glyph, about 22dp in a translucent circle. On a mount at 60 cm that is roughly 0.3° of visual angle, which is foveal, not peripheral. The current chip does not deliver the "shape is read before words" it promises in §6; you have to look straight at it.
- **It speaks Uber's dialect.** Uber's Mexico pilot badge (`uber-badge-1.jpg`) is a big "$8.28" over "$ / km" inside the offer card. For the C1 offer, that badge would read $12.33/km gross (148 ÷ 12.0), while our chip says "$9.49/km". The driver sees two $/km numbers in the same typographic form that disagree, and only the 15sp word "neto" resolves them. A net figure must not look like a gross figure with a different colour.
- **Failure looks like a verdict.** C4 (gris), C5 (gris) and C6 ("Lector detenido") use the verdict object's exact silhouette. A stopped reader and an unreadable fare arrive in the same shape as a judgement.

### 1.3 The ten worst screens, and the one thing that gives each away

| # | Frame | The giveaway |
|---|---|---|
| 1 | **C1–C3** chip | A circled ✓ in a 16dp-radius colour card with a favicon K: GigU's badge, set in Uber's gross-badge typography. |
| 2 | **B1** Hoy | The hero sits inside a slate card, with a green link wedged into a right gutter ("Cómo se / calculó") and a green status pill above it. This is the generated "stat card". |
| 3 | **E1** Semana | Ten stacked blocks of equal visual weight. The scope sentence wraps "Cómo / se / calculó" over three lines. |
| 4 | **F2** Tu ciudad | An emerald-bordered card, 20 identical dots, two 48dp selector pills, and "78 / %" broken across lines. |
| 5 | **H1** Premium | Two emerald slabs in a row: the benefit card ("14 días gratis") is dressed as a button directly above the real button. |
| 6 | **E4** IMSS | A legal paragraph is the screen. A text seam leaves "ni a ninguna autoridad." floating, and the official link is a parenthetical. |
| 7 | **A2** Aviso | Four 36dp tinted icon tiles (eye, flag, phone, warning triangle) beside paragraphs: the permissions-explainer template. |
| 8 | **G1** Cuenta | A tinted icon square on every row, grouped in slate cards: the Material settings template. |
| 9 | **H3** Compartir | An emerald gradient card with a K square and two translucent stat tiles: the "Wrapped" template, with "78 / %" broken again. |
| 10 | **C6** Lector detenido | The verdict chip's exact shape in slate: a system failure dressed as a fourth verdict. At a glance it cannot be told from C4. |

### 1.4 Fable's observations: where they are wrong or incomplete

1. **"Uber is white with black type and one accent."** This is incomplete. Uber's earnings pill renders "MXN" in green (green means money in Uber's own language), its $/km badge is outlined in blue, and its map goes dark at night. The lesson to take is not "go white". It is: one surface, type does the hierarchy, and colour appears only where it means something. The tell in our frames is the slate-blue cast and the cards, not darkness.
2. **"DiDi is orange with giant numbers."** That is DiDi's store frame. The phone screens inside the frames are mostly white or dark UIs with orange used sparingly. What transfers is the giant numeral, not the orange.
3. **"The colour is a fill the eye parses."** Correct but understated. It is measurable (§1.2): verde and rojo sit at 1.09 : 1, so the periphery literally cannot separate them.
4. **"The host mock looks fake."** True, and the bigger problem is what the fake hides. Uber's real offer screen (`uber-offer-card-lightbox.jpg`) keeps a top control row on screen during an offer: menu at the left, earnings pill in the centre, search at the right. The offer card is mid-screen, not at the bottom. Our "top-end" default corner sits on Uber's search button. The grid mock made an occlusion conflict invisible.
5. **The copy-density examples** are right. To be exact, B1's "aprox." appears three times: the label, the scope line, and the row "14:32 · aprox.". E1 has ten elements, not nine, counting "¿Te faltan viajes? Trae tu resumen" below the fold.
6. **What Fable missed:**
   - the brand-green versus verdict-verde collision;
   - the 18-size type ladder;
   - failure states built from the verdict object;
   - "dark-first" being an inherited assumption rather than a driver need (the app is read parked, and the host apps follow the system theme);
   - developer text leaking into frames;
   - the chip borrowing the host's own gross typography.

---

## 2. The signature idea

What only Kompara does is take *your* costs out and set the result against *your* mínimo. The verdict is not a grade. It is a position: above your line, between your lines, below your line. The name already says it: *Kompara* means comparing a number against a line.

**How to read the sketches:** each one is at true 1× footprint. 32 characters ≈ 200dp and 6 lines ≈ 88dp. `│` is the screen bezel the chip is docked against. `V`/`A`/`R` = verde/amarillo/rojo fill, `░` = chip-ink (near-black `#0E0F10`), `═` = the raya in paper white, `━` = the raya in dark ink, `╍` = a dashed raya, `K` = the K-raya mark (§3.4).

### Concept A: **La raya** (your line)

**Pitch:** *"Kompara draws one line, your mínimo, and every number in the product sits above it, on it, or below it."*

The chip is a two-part plate docked flush to the screen edge, split by a 3dp line (the raya). The verdict is **which side of the line the colour is on**:

- verde: colour above the line;
- amarillo: colour on both sides, straddling the line;
- rojo: colour below the line;
- gris: no colour, and the line is dashed because nothing was read.

There is no glyph and no verdict word. The number always sits in the colour; the qualifier always sits in the small half.

```
VERDE ($148 · 12.0 km · 24 min)        AMARILLO ($120 · 12.0 km · 27 min)
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV│      AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA│
VV  $9.49/km  VVVVVVVVVVVVVVVVVV│      AA  $7.16/km  AAAAAAAAAAAAAAAAAA│
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV│      AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA│
════════════════════════════════│      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━│
░░  neto aprox. · $285/h  ░░░░K░│      AA  neto aprox. · $191/h  AAAAKA│
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│      AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA│

ROJO ($62 · 12.3 km · 30 min)          GRIS (faltó la tarifa)
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
░░  neto aprox. · $54/h  ░░░░░K░│      ░░  Sin lectura  ░░░░░░░░░░░░░░░│
════════════════════════════════│      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR│      ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ │
RR  $2.20/km  RRRRRRRRRRRRRRRRRR│      ░░  faltó la tarifa  ░░░░░░░░░K░│
RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR│      ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
```

Seen in greyscale, the four states are four different patterns: light-over-dark (verde), light all over (amarillo), dark-over-light (rojo), dark all over (gris).

**How it carries into the app:**
- **Hoy:** the last three offers are rows led by *the mark*, a 20dp miniature of the chip (⬒ verde · ■ amarillo · ⬓ rojo · ⬚ gris). The reader status is a raya segment: solid while reading, dashed when off or paused.
- **Tu día:** the day is drawn as one raya, solid while en turno and dashed while en pausa, with each shift's total sitting on it. The offers below are led by the mark.
- **Ganancias:** the day and week bars stand on a 2dp raya at $0 after costs. A losing day would hang below the line. This is the same grammar as the chip, at the scale of money.
- **Comparar:** the reference becomes the line. "Promedio de Uber en CDMX $131/viaje" is the raya, and "Tú $149/viaje" sits above it.

**Risks:**
- **1-second mounted glance.** The cue is the position of a colour mass relative to a fixed white line, which is a lightness pattern and survives the periphery and night vision. The cost: colour now covers about 59 % of the plate instead of 100 % in verde and rojo, and the number's position flips between top and bottom, so the eye's landing point moves by state. Mitigation: the decision does not need the number, and the colour mass is the brightest thing within 20° of the fare.
- **Occlusion.** The footprint is 200×88dp, docked to the edge. The default top is 144dp, below the host's top control row and above a mid-screen offer card (on Uber's layout the safe band is about 136–370dp). It is validated per host and suppressed if no safe band exists.
- **Colour-vision deficiency.** The state is coded by position, so it needs no hue. The four greyscale patterns are distinct.
- **Play heuristics.** The chip never covers a control and copies no host element (no blue outline, no gross-badge typography). It carries Kompara's mark, so it cannot be passed off as Uber's own UI. Its touch region equals its visible area. A distinctive, attributed overlay is the opposite of the banking-trojan pattern (a mimicking full-screen overlay).
- **"Looks like a control".** Buttons are floating, rounded, with a centred label. This is a docked plate with a square bezel side, left-aligned text and a hairline keyline instead of a floating shadow, so it reads as part of the screen's frame. The residual risk is being mistaken for an edge-panel handle (Samsung) or triggering Back. The touch region therefore starts 24dp inside the bezel, so a drag never fires Back and Back never fires a drag.
- **"Traffic light".** A critic may call a vertical position code an inverted traffic light. It is not: there are no three lamps and no unlit cells. There is one line and one mass that is either above it, below it, or on it.

### Concept B: **El brazo** (the K's arm)

**Pitch:** *"The K is a fork: the trip's plate tilts up the arm when it clears your mínimo and down the arm when it doesn't."*

The chip is a wedge with a flat bottom edge. Its top edge rises to the right (verde) or falls (rojo), at about 8°, taken from the K's diagonal. Amarillo is level; gris is an outline. The text stays upright inside a 200×60dp content box, and the extra 28dp of the wedge is pure signal.

```
VERDE                                ROJO
                        ▁▂▄VVVV│     RRRR▄▂▁
              ▁▂▄VVVVVVVVVVVVVV│     RRRRRRRRRRRRRRRR▄▂▁
    ▁▂▄VVVVVVVVVVVVVVVVVVVVVVVV│     RRRRRRRRRRRRRRRRRRRRRRRRRR▄▂▁
VVVVVV  $9.49/km  VVVVVVVVVVVVV│     RRRRRR  $2.20/km  RRRRRRRRRRRRR│
VV  neto aprox. · $285/h  VVKVV│     RR  neto aprox. · $54/h  RRKRR│
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV│     RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR│

AMARILLO (level)                     GRIS (outline only)
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA│     ┌──────────────────────────────│
AAAAAA  $7.16/km  AAAAAAAAAAAAA│     │  Sin lectura                 │
AA  neto aprox. · $191/h  AAKAA│     │  faltó la tarifa           K │
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA│     └──────────────────────────────│
```

**How it carries:**
- **Hoy:** rows led by a tilted stroke.
- **Tu día:** the same strokes.
- **Ganancias:** a week-over-week slope.
- **Comparar:** a slope chart from Uber to DiDi.

Every one of these is forced. Slope means "trend over time", and an offer is not a trend.

**Risks:**
- **Glance.** Orientation is detected without effort, but an 8° tilt on a 3° object is weak in the periphery.
- **Colour vision.** Orientation is colour-blind-safe but subtle.
- **Template.** Amarillo becomes a flat coloured rectangle with a number, which is the template again.
- **Fit.** The wedge eats the text budget.
- **Control trap.** A slanted sticker does not look like a button, which is good.
- **Play.** Neutral.

### Concept C: **El ticket** (the receipt)

**Pitch:** *"Every peso Kompara shows is a ticket: what the app paid, minus your costs, equals what's yours, and the chip is the stub."*

The chip is a paper stub (`#FAF8F2`, monospaced figures) with a torn bottom edge. The verdict is a block of ink stamped across its right end.

```
VERDE                                  GRIS
┌──────────────────────────────┐│      ┌──────────────────────────────┐│
│ $148 − $34          $9.49/km ││      │ Sin lectura                  ││
│ neto aprox. · $285/h  VVVVVV ││      │ faltó la tarifa       ╍╍╍╍╍╍ ││
└╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱┘│      └╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱┘│
(amarillo / rojo: the same stub, with an A or R stamp)
```

**How it carries:** beautifully.
- **Hoy:** today's ticket total.
- **Tu día:** the day's ticket, line by line.
- **Ganancias:** "Te pagó la app $8,400 / Gasolina y desgaste −$1,920 / Lo del coche −$1,500 / Te quedaron $4,980".
- **Comparar:** two tickets side by side.

**Risks:**
- **Glance.** It fails the 1-second glance: a subtraction has to be read, and the stamp is about a fifth of the area.
- **Contrast.** A white stub over Uber's white card and light map disappears.
- **Numbers discipline.** Three numbers on the chip break the numbers discipline.
- **Passing off.** Receipt figures next to the fare could be mistaken for host content.
- **Craft.** The skeuomorphic torn edge is decoration.

### The pick: **La raya**

1. **It is derived from the product, not from the category.** Only Kompara has a personal mínimo to draw. GigU shows three numbers, Ruta Rentable a colour, DecideRider an ACEPTAR/RECHAZAR badge with a bar, and Uber a gross number. Nobody shows *where you stand against your line*.
2. **It is the only one of the three that fixes the physics in §1.2.** It gives four lightness patterns, needs no hue and no glyph, and is caught in the periphery. B's tilt is weak at this size; C has to be read.
3. **It carries everywhere with one meaning.** The raya is always *the line that matters at this scale*: your mínimo for an offer, $0 after costs for a day's money, the city average for a comparison. Dashed always means "Kompara wasn't reading". B's slope has no meaning on Hoy, and C carries well but cannot be the chip.
4. **It is drawable and nameable.** A driver can sketch it: a square with the colour on top or underneath, and a line. A design director can say it in one sentence. In Mexican Spanish *la raya* is also colloquial pay ("día de raya"), a lucky double meaning for an earnings product. Internally the name is "la raya"; driver-facing copy keeps the locked word "tu mínimo".
5. **It turns the brand into the system.** The K-raya mark (§3.4) is a K whose two arms spring from a horizontal bar: up the arm is above your line, down the arm is below it. The logo is the verdict grammar.

From C I keep the receipt arithmetic for the "Cómo se calculó" sheets, using a hairline sum rule, not the raya. From B I keep nothing.

---

## 3. Visual system v3

### 3.1 Palette

The system is paper and ink, plus the verdict triple. **Ink is the only accent.** Green exists only as verdict verde and in the K mark, and in Kompara verde *means* "te deja". Everything follows the system theme. The mockups lead with the day theme because the host apps are light by day and because it breaks the dashboard dialect; night is true black with the same structure.

| Token (v3) | Day | Night | Replaces in `base.css` |
|---|---|---|---|
| `--paper` | `#F5F4EF` | `#0B0B0A` | `--background`, `--surface` (`slate-900 #0f172a`); `html,body #0b1120` |
| `--paper-2` | `#EBE9E2` | `#1A1A18` | `--surface-card` (`slate-800`). Used **only** for input fields, the pressed state and skeletons; it is never a card |
| `--ink` | `#111110` (17 : 1 on paper) | `#F2F1EC` | `--text-strong`, `--text-body` (`slate-50`); `.btn.primary` background (`emerald-700`) |
| `--ink-2` | `#5C5B57` (6.2 : 1) | `#A6A59F` (8.0 : 1) | `--text-muted` (`slate-300`) |
| `--ink-3` | `#8A8984` (3.2 : 1: text ≥ 18sp and non-text only) | `#6E6D68` | `--text-faint` (`slate-400`) |
| `--rule` | `#DCDAD2` (1dp hairline) | `#2A2A27` | `.row` borders `rgba(255,255,255,.06)`, `--border`, `--border-strong` |
| `--raya` | 2dp `--ink` in the app; 3dp `--paper` (or `#1A1300` on amarillo) inside the chip | same | new |
| `--verde` / `--on-verde` | `#187a36` / `#fff` | same | unchanged |
| `--amarillo` / `--on-amarillo` | `#f2b705` / `#1a1300` | same | unchanged |
| `--rojo` / `--on-rojo` | `#d32f2f` / `#fff` | same | unchanged |
| `--chip-ink` / `--on-chip-ink` | `#0E0F10` / `#EDEDEA` (16 : 1) | same (theme-independent) | `chipNeutral #475569` (slate-600), `.chip .body` |
| `--chip-keyline` | 1dp `rgba(255,255,255,.28)` | same | `.chip` box-shadow `0 8px 24px` |
| `--k` | `var(--verde)` | same | `--emerald-600` brand, `--primary` |

**Deleted:** `--emerald-*`, `--slate-*`, `--info-blue`, `--amber-500`, `--primary-container`, `.card.accent`, every `.pill.*` colour, `.k`, `.people`, `.tile`, `.chip .strip`, `.spark i.today` (emerald), and the second red (`--error #dc2626`).

**Rules:**
- Verdict colours appear only on verdicts (the chip, the mark, and nowhere else).
- Reader problems and errors are **ink** states: an inverted ink band or a dashed raya, with words. They are never rojo and never amber.
- Destructive actions are ink text behind a confirm.
- No fifth colour anywhere.
- **Contrast bonus of the split:** small text never sits on verde or rojo. Only the 34sp number does (large text, 5.4 : 1 and 5.0 : 1); the 16sp line sits on chip-ink (16 : 1) or amarillo (10 : 1).

### 3.2 Type

- **Money: Barlow Semi Condensed SemiBold 600**, tabular lining figures (`tnum`, `lnum`).
- **UI: Barlow** Regular 400 and SemiBold 600 only.

Barlow (OFL) draws on California road signs and plates, so the numbers wear a road letterform. The semi-condensed width gives the chip room: "−$1.05/km" and two-digit rates fit inside 200dp, and the 232dp growth step is retired. Weight contrast comes from **size**, not from blackness; there is no 800 or 900 anywhere. Inter is retired. **Gate:** confirm `tnum` in the shipped font files; the fallback is Archivo at semi-condensed width.

| Step | Size / line height | Face | Used for | Replaces |
|---|---|---|---|---|
| Display | 56 / 56, −0.01em | Money | The one hero number per screen (Hoy, Tu día, Semana, Mes, the F1/F2 figure, the price on H2b) | `.hero .value` 44/900, `.hero .value.xl` 56, `.tile .v` 24/800, `.offer` hero |
| Number | 34 / 36 | Money | Chip number; secondary money (E4 per-app payments) | `.chip .rate` 30/900, the 28–34px headings |
| Title | 20 / 26 | UI 600 | Screen titles, sentence heroes, sheet titles, the chip's gris words | `.topbar .title` 22/700, `.h2` 24/700, `.sheet .st` 18 |
| Body | 16 / 22 | UI 400 (600 for row labels that are the content) | Rows, body, buttons (600), the chip's second line (Money 500) | 14/15/16/17/18 variants, `.btn` 17/700, `.chip .rate2` |
| Small | 13 / 18 | UI 400 | Labels above heroes, qualifiers, captions, nav labels: the floor | 10/11/12/13 variants, `.pill` 12 |

Non-breaking spaces are required in "78 %", "$1,500", "8 h 10 min" and "12 – 18 oct". No text ever wraps into a right gutter.

### 3.3 Surface model: surface, not cards

- **One surface per screen.** The hero number sits on it, top-left, with nothing around it.
- **Groups** are separated by whitespace (32dp between groups, 8dp rhythm) and full-bleed 1dp `--rule` hairlines. Rows are 56dp minimum and carry one label, one value or chevron, and one figure at most.
- **Elevation exists in three places only:** sheets (top radius 16, `0 -8dp 24dp rgba(0,0,0,.12)`), the chip (overlay) and snackbars.
- **Primary button:** `--ink` fill, `--paper` text, 56dp tall, radius 10, full width, at the thumb, one per screen. Secondary actions are text buttons (ink, 600, 48dp). Outlined buttons are retired.
- **Selectors:** text tabs. The active tab is `--ink` 600 and inactive tabs are `--ink-2` 400; there is **no underline**, because a 2dp line is reserved for the raya. Choice lists open in sheets. Steppers are a 56sp number between two 56dp targets with a 1.5dp `--ink-3` ring.
- **No pills.** Status is a raya segment plus words. "Ejemplo" is a 13sp label. "Premium" is a word in a row.
- **Gutter** 20dp.

### 3.4 Iconography and the mark

- **The mark (the only custom glyph set):** a 20dp square with a 1.5dp ink outline, split by a 2dp raya.
  - ⬒ verde: top half filled verde;
  - ■ amarillo: both halves amarillo, dark raya;
  - ⬓ rojo: bottom half filled rojo;
  - ⬚ gris: dashed outline, no fill.

  It is the chip in miniature and the only leading glyph in list rows anywhere in the product. In Comparar the same shape is drawn in ink, because a comparison is not a verdict.
- **K-raya (brand):** a K drawn from Barlow Semi Condensed 700. A horizontal bar runs out of the arms' junction to the right, 1.2× the letter's width. The bar is the raya; the upper arm is "above", the lower arm "below".
  - App icon: paper K-raya on verde. The launcher shape is set by the OS.
  - In-product: ink K-raya, with no container.
  - Notification small icon: monochrome K-raya.
  - On the chip: 12dp at 60 % in the small half, at the bezel corner.
  - The K square is gone.
- **System icons:** Material Symbols *Sharp*, weight 300, 24dp, `--ink-2`, never in a container. They are used only for the three nav tabs, back, close, share, overflow, the avatar (an initials or person glyph in a 1.5dp ring) and the row chevron (`--ink-3`). Settings and benefit rows have no leading icons.

### 3.5 Motion

| Event | Motion |
|---|---|
| Chip appears (≤ 150 ms after parse) | Slides out 16dp from the bezel it is docked to, with a fade, over 120 ms on a decelerate curve. It belongs to the frame of the phone; it does not float in. |
| Chip leaves | Slides back into the bezel over 160 ms, 500 ms after the offer card leaves. |
| Same offer re-parsed to a different state | Fills crossfade over 120 ms. The halves never animate their swap, and the number swaps with no animation. |
| Sheets | 240 ms, standard curve. |
| Tab switch | Crossfade over 90 ms, no slide. |
| Bars | Grow from the raya over 280 ms, staggered by 20 ms, only on the first view of a period. No bounce. |
| Pressed state | Instant `--paper-2`, fading out over 120 ms. |
| Never | Pulse, shimmer, confetti, countdown, haptic. Reduced motion means fades only. |

### 3.6 Host mock (generic, no platform mark)

The mock is built from what Uber's real offer screen keeps on screen, because those are the elements that constrain us.

- **Map:** `#EEF0EB` land; an irregular street network with white roads and a 1px `#D9DBD5` casing; one diagonal avenue; one pale-green park block; grey street labels ("Av. Insurgentes Sur", "Calz. de Tlalpan").
- **Route:** the driver chevron in a white circle, a 5dp `#111` route polyline, a pickup dot and a dropoff square.
- **Top control row** (y 84–120dp): a white 48dp "≡" at the left, a black "En línea" capsule in the centre and a white 48dp "⌕" at the right.
- **Offer card:** mid-lower, white, radius 24, soft shadow. It holds a black "Económico" capsule, the fare in 40sp bold, pickup and trip rows as in the current frames ("6 min · 2.0 km", "18 min · 10.0 km") with the street names but no house numbers, a countdown ring at the top-right, and a full-width black "Aceptar".
- **Never:** logos, wordmarks, Uber blue, DiDi orange, host earnings figures (an invented number), or a grid.
- The chip's default position is drawn where the real constraint is: docked right, top at 144dp.

### 3.7 Chip specification (one footprint for every state)

| Property | Value |
|---|---|
| Footprint | 200 × 88dp, including negative and two-digit amounts (the 232dp step is retired). It grows only in height at large font scales and never truncates. |
| Docking | Flush to the end bezel (mirrored on the left edge). Bezel-side corners 0, inner corners 6dp. Keyline `--chip-keyline` on the three free sides, plus a soft shadow `0 2dp 8dp rgba(0,0,0,.25)`. |
| Colour half | 52dp tall, 16dp inset. Number in Money 34sp, unit "/km" or "/h" in Money 16sp Medium on the same baseline. |
| Raya | 3dp `--paper`; `#1A1300` on amarillo; dashed 6/4 at 50 % on gris. |
| Small half | 33dp tall. Money 16sp Medium in `--on-chip-ink` (or `--on-amarillo`). K-raya 12dp at the bezel corner. |
| Gris layout | Both halves chip-ink. The top half carries the missing input in UI 20sp 600 ("Sin lectura", "Falta distancia", "Falta tiempo", "Sin confirmar", "Leyendo oferta"); the bottom half carries the 16sp reason. Gris never carries a peso figure. |
| Touch | Drag only. The touch region equals the plate minus the 24dp gesture inset at the bezel. It snaps to the nearer edge. Tap and long-press do nothing. |
| Position | Per-host validated band (Uber: below the top control row, above the offer card). Suppressed if no band fits. |
| "Lector detenido" (C6) | A **different object**: 200 × 48dp, chip-ink, one line in 16sp 600 and one in 13sp, no raya, no colour. 5 s, touches pass through. |

### 3.8 `base.css` replacement map (for the rebuild)

- **`:root`:** replace the whole block with the tokens in §3.1, plus `--font-money:"Barlow Semi Condensed"` and `--font-ui:"Barlow"`.
- **Delete:** `.card*`, `.tiles`/`.tile`, `.pill*`, `.seg`, `.k`, `.people`, `.chip .strip`, `.chip .word`, `.chip.wide`, `.big-icon`, `.blur`, `.status` (the pill version), `.row .ic` (icon tiles), `.att`.
- **Add:**
  - `.surface-row` (56dp, hairline);
  - `.hero` (13 label / 56 number / 48dp qualifier row);
  - `.tabs-text`;
  - `.mark.{v,a,r,g}` (20dp);
  - `.raya` (2dp ink), `.raya.dashed`;
  - `.chip.{v,a,r,g}` with `.chip .hi` (52dp), `.chip .ry` (3dp) and `.chip .lo` (33dp) in a column whose order is reversed for `.r`;
  - `.lector-tab` (C6);
  - `.host` (map, top row, offer card per §3.6).
- **Keep** the `.spark` markup and `height:%` styles, because `check-fixture.py` parses them for the E1, E7b, E8, E3 and E3c bars. Restyle them as ink bars on a raya.

---

## 4. Radical simplification, screen by screen

Rules applied to every screen:
- "Te quedan" plus **one** qualifier, said once, as a 48dp row that opens "Cómo se calculó". The §4 scope-line sentences are retired, because provenance now lives one tap away (inside that tap target), not in a sentence.
- Share is a top-bar ⤴ icon on every period screen.
- The mark replaces every circled glyph and every colour word.

Wireframes are at phone scale. Every fixture string the checker asserts in these frames is kept verbatim.

### A1: Demo
- **Driver's sentence:** "Me enseña cómo se ve el aviso encima de una oferta."
- **Delete:** the body paragraph (the title says it); the four-sentence legend; the tonal "Ver otra oferta" button (it becomes a text button); the slate sample card.
- **Keep:** the title; the sample as a real-looking mini host with the real chip docked to its edge; a one-line legend that changes with the state; Empezar; Ya tengo cuenta.

```
━━━━━━━───────────────────────────────────  progreso (2dp ink sobre rule)
Antes de aceptar, ve si te deja.                    Title 20
┌ mini host: mapa + tarjeta ─────────── Ejemplo ┐   Small 13 "Ejemplo"
│                     VVVVVVVVVVVVVVVVVVVVVVVVVV│   chip real, pegado al borde
│                     VV  $9.49/km  VVVVVVVVVVVV│
│                     ══════════════════════════│
│                     ░  neto aprox. · $285/h K░│
│ $148                                          │
│ 12 km en total (2 para recoger) · 24 min      │
└───────────────────────────────────────────────┘
Llega a tu mínimo.                                  Body 16; cambia por estado
Ver otra oferta                                     texto 48dp
[                 Empezar                 ]         ink 56dp
Ya tengo cuenta
```

**Copy:** "Antes de aceptar, ve si te deja." The legend is one line per state:
- verde: "Llega a tu mínimo."
- amarillo: "Entre tu mínimo y tu rojo."
- rojo: "Abajo de tu rojo."
- gris: "Faltan datos: sin semáforo."

### A2: Aviso
- **Driver's sentence:** "Me dice qué lee la app y qué sale de mi teléfono antes de darle permiso."
- **Delete:** the four tinted icon tiles; the separate footer line (merged into "Lee"); "Tú decides" (the button is the decision).
- **Keep:** all four disclosures with the same legal content, as hairline-separated blocks with bold lead words; Acepto; No acepto. Legal must re-read this wording (B-038).

```
‹  ━━━━━━━━━━━━━━──────────────────────────
Lo que hace Kompara en tu teléfono                  Title 20
Lee. Mientras hay una oferta en Uber o DiDi, toma una imagen
de toda la pantalla, lee tarifa, distancia y tiempo, y la borra
al momento. Usa la accesibilidad de Android.
───────────────────────────────────────────────
Guarda. Solo las cifras de tus viajes, en este teléfono. Nunca
tu pantalla ni datos del pasajero.
───────────────────────────────────────────────
Envía. Conteos anónimos de lectura (puedes apagarlos). Si tú eliges: tus totales
con tu cuenta, tu aporte al promedio de tu ciudad, un archivo o un reporte de lectura.
───────────────────────────────────────────────
Ojo. Uber o DiDi podrían no permitirlo. Kompara solo lee: nunca acepta ni rechaza por ti.
[ Acepto ]                                  No acepto
```

### A5: ¿Cuánto rinde tu coche?
- **Driver's sentence:** "Le digo cuántos km por litro hace mi coche."
- **Delete:** the card around the stepper; the separate emerald cost card; the second clause of the body.
- **Keep:** the title; the stepper as a 56sp number; one reference line; one cost line; "Ya sé mi costo por km"; the primary.

```
‹  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━──────────
¿Cuánto rinde tu coche?                             Title 20
Si no lo sabes, deja el típico y cámbialo después.  Body 16 ink-2

      ( − )         12 km/L          ( + )          Display 56 money; 56dp targets
Típico de un sedán · gasolina a $24.50 el litro (referencia) · desgaste $0.80 por km
───────────────────────────────────────────────
Manejar te cuesta $2.84 por km.                     Body 16
En un viaje de 12 km son unos $34.                  Body 16 ink-2
Ya sé mi costo por km                               texto 48dp

[            Usar 12 km/L (típico)            ]
```

### B1: Hoy, mid-shift
- **Driver's sentence:** "Cuánto llevo hoy y si el lector está leyendo."
- **Delete:** the hero card; the three-line scope sentence; the "Cómo se calculó" gutter link; the green status pill; the colour words ("Verde ·", "Rojo ·"); the repeated "aprox." on rows; the clock icon tile.
- **Keep:** the status as a raya segment plus words; the hero on the surface; one qualifier row; the observation; three offer rows led by the mark.

```
Hoy                                          (◯)    Title 20; avatar ring
━━ Lector funcionando                               Small 13; solid raya segment
Te quedan hoy                                       Small 13 ink-2
$762                                                Display 56 money
6 viajes tomados · aprox.                     ›     Body 16; 48dp → Cómo se calculó
───────────────────────────────────────────────
Tu mejor hora hoy: 9 a 10 h                   ›
───────────────────────────────────────────────
Últimas ofertas                      Ver tu día ›   Small 13 / texto 48dp
⬒  La tomaste                              14:32
⬓  Se fue sin tomar                        12:10
⬚  Sin lectura · faltó la tarifa           11:48

   Hoy            Ganancias           Comparar      ink activo, ink-3 inactivo
```

**Copy:** "Te quedan hoy", "6 viajes tomados · aprox." The rows say the outcome only; the mark says the verdict.

### B2: Hoy, reader off
- **Driver's sentence:** "El lector está apagado; lo prendo aquí."
- **Delete:** the red-outlined card (red is a verdict); the second, greyed headline card ("Aún no registramos viajes" at 32sp); the separate status pill.
- **Keep:** the state with its time; the cause as the hero; one consequence; yesterday's figure; the button.

```
Hoy                                          (◯)
╍╍ Lector apagado desde las 7:50                    Small 13; raya punteada
Tu teléfono quitó el permiso de                     Title 20 (la causa es el héroe)
accesibilidad.
Sin él, Kompara no ve tus ofertas.                  Body 16 ink-2
───────────────────────────────────────────────
Aún no registramos viajes hoy.                      Body 16 ink-2
Ayer te quedaron $1,240 aprox.                      Body 16



[              Encender lector              ]       ink 56dp, al pulgar
   Hoy            Ganancias           Comparar
```

### B3: Hoy, first run
- **Driver's sentence:** "Ya quedó; abro mi app y espero la primera oferta."
- **Delete:** the floating sample chip and its colliding "Ejemplo" pill; the three-line small print; "Ver ejemplo" (tapping the sample opens the demo).
- **Keep:** the headline; the proof line; the sample chip docked to *this* screen's right edge, exactly as it will appear; two value rows; the primary.

```
Hoy                                          (◯)
━ ━ Lector preparado                                Small 13; raya en espera
Listo. Abre tu app de viajes.                       Title 20
La primera oferta confirmará la lectura.            Body 16 ink-2
                    Ejemplo  VVVVVVVVVVVVVVVVVVVVVVV│  chip de muestra, pegado al borde
                             VV  $9.49/km  VVVVVVVVV│
                             ═══════════════════════│
                             ░ neto aprox. · $285/h K│
───────────────────────────────────────────────
Tu mínimo: $8.00/km · valor inicial           ›     → Tu mínimo
Costos típicos · Elegir mi ciudad             ›
[            Avísame si se apaga            ]
```

### C1–C4: the chip, in the host

**Driver's sentence:** "Si el color está arriba de la raya, el viaje me deja."

**Delete:**
- the circled glyphs and the K square;
- the 16dp-radius floating card and its 24dp shadow;
- the grid host;
- the top-right corner position, which covered the host's search button.

**Keep:** one rate, "neto aprox.", the other rate, colour on its side of the raya, and a 12dp K-raya.

```
14:32                                     82 %
 (≡)          (  En línea  )            (⌕)          host: fila de controles y 84–120
                          VVVVVVVVVVVVVVVVVVVVVVV│   chip: top y 144, pegado al borde
 Av. Insurgentes Sur      VV  $9.49/km  VVVVVVVVV│
      ◉━━━━━━┓            ═══════════════════════│
             ┗━━━━━━━■    ░ neto aprox. · $285/h K│
┌───────────────────────────────────────────────┐
│ ( Económico )                               ◔ │   tarjeta del host, a media pantalla
│ $148.00                                       │
│ ● 6 min · 2.0 km · Av. Insurgentes Sur        │
│ ■ 18 min · 10.0 km · Calz. de Tlalpan         │
│ [                  Aceptar                  ] │
└───────────────────────────────────────────────┘
```

- **C2:** amarillo, "$7.16/km" / "neto aprox. · $191/h".
- **C3:** rojo, "neto aprox. · $54/h" above the raya and "$2.20/km" below it.
- **C4:** gris, "Sin lectura" / "faltó la tarifa", dashed raya.
- All four plates are as drawn in §2. **C7** (not listed, same rule): rojo, "neto aprox. · −$26/h" over "−$1.05/km", inside 200dp.

### C5: gris, deciding unit not evaluable
- **Driver's sentence:** "No pudo calcular por hora; no hay semáforo en esta."
- **Delete:** the second clause "ve el detalle al estacionarte" (the detail always exists in Tu día; this line forced a two-line, taller chip).
- **Keep:** the missing input and "sin semáforo", with no rate.

```
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
░░  Falta tiempo  ░░░░░░░░░░░░░░│     UI 20 / 600
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ │     raya punteada: no hubo lectura completa
░░  sin semáforo  ░░░░░░░░░░░░K░│     Money 16
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
```

### C6: Lector detenido
- **Driver's sentence:** "Se detuvo el lector; lo reviso cuando me pare."
- **Delete:** the chip silhouette (a failure must not look like a verdict); "cuando te estaciones" (shortened).
- **Keep:** a half-height ink tab with no raya and no colour; 5 s; touches pass through.

```
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│     200 × 48dp, sin raya, sin color
░░  Lector detenido  ░░░░░░░░░░░│     UI 16 / 600
░░  Revísalo al estacionarte ░K░│     UI 13
```

### D1: Tu día
- **Driver's sentence:** "Mi día, oferta por oferta."
- **Delete:**
  - the separate back row and stepper row (merged into one top bar);
  - the scope sentence and its gutter link;
  - the "Turnos" card, which becomes the day's raya;
  - the "Todas · 23" pill (it becomes the section label);
  - "neto" on every row (said once in the label);
  - "· aprox." on rows.
- **Keep:** the hero, one qualifier row, the day line (solid while en turno, dashed while en pausa) with the shift totals on it, and the offer rows led by the mark.

```
←          ‹  Hoy · lunes 19 oct  ›                 Title 20
Te quedan hoy
$1,240                                              Display 56
9 viajes tomados · 8 h 10 min en turno · aprox. ›
      $648                            $592          Body 16 money, sobre cada turno
━━━━━━━━━━━━━━━━━━━━╍╍╍╍╍╍╍╍━━━━━━━━━━━━━━━━━━━     raya del día: turno sólido, pausa punteada
08:00          12:30 12:40  14:20          18:00    Small 13
Ofertas · 23 · neto por km      Verdes sin tomar · 2
■  $7.16/km                   14:50 · La tomaste
⬒  $9.49/km                   14:32 · La tomaste
⬓  $2.20/km                   12:10 · Se fue sin tomar
⬚  Sin lectura                11:48 · faltó la tarifa
```

**Dependency:** marks for every offer can sit on the day's raya (above or below it, by time) once `fixture.json` carries all 23 offers with their time, verdict and outcome; only 4 exist today. Until then the line shows the shifts and the pause only. Do not invent the other 19.

### D2: Detalle de oferta (sheet)
- **Driver's sentence:** "Por qué esta oferta salió verde y cuánto me dejó."
- **Delete:**
  - the "Verde" pill (the mark says it);
  - the card around the four rows (hairline rows instead);
  - the "Verde porque…" sentence (drawn instead);
  - "¿Se leyó mal? Avísanos" (moved into the ⋯ overflow);
  - the orange colour.
- **Keep:** the title with the mark and outcome; the hero; the rates; the "why" drawn as the rate against your line; four rows; "El pago final puede cambiar."; two text actions.

```
──── 
⬒  Oferta de las 14:32 · La tomaste            ⋯    Title 20; ⋯ = ¿Se leyó mal? Avísanos
Te quedan aprox.
$114                                                Display 56
$9.49/km · $285/h neto                              Body 16 ink-2
                          ⬒ $9.49/km                la oferta, arriba de tu raya
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  tu mínimo $8.00/km
Tarifa de la oferta                          $148
Distancia (2.0 km para recoger)           12.0 km
Tiempo                                     24 min
Gasolina y desgaste (12.0 × $2.84)           −$34
El pago final puede cambiar.                        Small 13
Ajustar mi mínimo           Se canceló · quitar de mi día
```

### E1: Ganancias · Semana (closed)
- **Driver's sentence:** "Cuánto me quedó la semana pasada, día por día."
- **Delete:**
  - the four-clause scope sentence and its gutter link;
  - the car-cost card (merged into the chart label, figure kept);
  - the emerald-bordered Comparar card (it becomes a row);
  - the "Ver cada día" card (it becomes the best-day row);
  - "Compartir tu semana" (it becomes the top-bar ⤴);
  - "¿Te faltan viajes? Trae tu resumen" (moved into Cómo se calculó and the period sheet).
- **Keep:** the tabs, the stepper, the hero, one qualifier row, the bars on the raya, the best day, the standing row. Ten blocks become seven.

```
Ganancias                               ⤴    (◯)
Semana    Mes                                       tabs de texto
‹  12 – 18 oct · semana pasada  ›
Te quedaron esa semana
$4,980                                              Display 56
62 viajes tomados · aprox.                    ›     → Cómo se calculó (lector $5,028 · resumen $5,062)
 ▅    ▆    ▆    ▆    █    ▅    ▃                    barras 59 69 66 75 100 65 36 % (.spark)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━     raya en $0
 L    M    M    J    V    S    D
Por día, antes de restar lo del coche (−$1,500)     Small 13 — la deducción, dicha una vez
Tu mejor día fue el viernes                   ›     → Por día (E1b)
───────────────────────────────────────────────
Tu lugar entre los de Uber en CDMX            ›     Body 16 / Small: según tu resumen de Uber
```

**Copy note:** the statement is named on the root by position (the standing row's "según tu resumen de Uber"), and both figures sit one tap away. The hero is never substituted.

### E3: Ganancias · Mes
- **Driver's sentence:** "Cuánto llevo en el mes, semana por semana."
- **Delete:** the scope sentence and its gutter link; the "Ver cada semana" card (it becomes a row); "Compartir tu mes" (it becomes ⤴); the IMSS icon tile; the emerald "today" bar (the current week is drawn in ink, past weeks in ink-2).
- **Keep:** the hero, one qualifier row, four bars on the raya, the best week, the IMSS row.

```
Ganancias                               ⤴    (◯)
Semana    Mes
‹  Octubre 2026 · en curso  ›
Te quedan en octubre
$12,169                                             Display 56
158 viajes tomados · hasta hoy · aprox.       ›     → Cómo se calculó (D3b)
  ▂          ▇          █          ▂                barras 28 95 100 21 % (.spark)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━     raya en $0
 1 – 4      5 – 11    12 – 18     19 oct
Por semana, ya sin lo del coche                     Small 13
La mejor fue 12 – 18 oct                      ›     → Por semana (E3d)
───────────────────────────────────────────────
IMSS del mes                                  ›     Small: lo que te pagó cada app
```

### E4: IMSS del mes
- **Driver's sentence:** "Cuánto me pagó cada app este mes y dónde checo mi IMSS."
- **Delete:** the five-line paragraph; the orphaned "(imss.gob.mx/vigencia)" (the primary is the link); "aprox." on each row (said once in the header); the card.
- **Keep:** the two payments as the content; two legal lines (non-representation, no verdict); "¿Cómo funciona?"; the PDF row; the primary. Legal (B-038) must re-read the two lines.

```
←   IMSS del mes                       Octubre ▾    Title 20; selector = texto + ▾ (hoja)
Lo que te pagó cada app · aprox.                    Small 13
Uber                                    $12,608     Number 34 money
DiDi                                     $8,432     Number 34 money
───────────────────────────────────────────────
Si ya te toca el IMSS lo define la ley, no
Kompara. Consúltalo directo.                        Body 16
¿Cómo funciona?                               ›
───────────────────────────────────────────────
Resumen del mes en PDF · Premium              ›     Small: para tu contador
Son estimaciones, no asesoría fiscal ni constancia.
Kompara no representa al IMSS.                      Small 13 ink-2
[       Consultar mi vigencia en el IMSS       ]
```

### F1: Comparar · Tus apps
- **Driver's sentence:** "En qué app me fue mejor por km esa semana."
- **Delete:**
  - the "Comparar" title (the scope tabs are the title; the tab bar already says Comparar);
  - the segmented control and the metric pills (both become text tabs);
  - "· tú" and "según el lector" on each row (said once);
  - the "por hora" reason in the provenance row (shown when the disabled tab is tapped).
- **Keep:** the scope tabs, the stepper, the metric tabs, the difference as number plus words, two rows, one qualifier line, provenance.

```
Tus apps     Tu ciudad                        (◯)   Title 20, tabs de texto
‹  12 – 18 oct  ›
Por km      Por viaje      Por hora                 Body 16; Por hora en ink-3
$1.21                                               Display 56
más por km en DiDi                                  Title 20
───────────────────────────────────────────────
DiDi                                   $10.34/km
Uber                                    $9.13/km
───────────────────────────────────────────────
Neto, según el lector. Manejaste en horarios distintos.   Small 13 ink-2
De dónde salen estos datos                    ›
```

### F2: Comparar · Tu ciudad (Premium)
- **Driver's sentence:** "Cómo me pagan por viaje comparado con los de Uber en CDMX."
- **Delete:**
  - the emerald card;
  - the 20-dot strip (the city average becomes the raya);
  - the two big selector pills (they become one 48dp row);
  - the duplicated "Antes de gastos · según…" lines (said once for both sides);
  - the contribution switch (moved into the provenance sheet, with its eligibility sentence; it stays in G6);
  - the outlined "Compartir" (it becomes ⤴).
- **Keep:** the standing as one sentence broken around its number; the two figures on either side of the raya; the cohort size in the provenance row.

```
Tus apps     Tu ciudad                  ⤴    (◯)
‹  12 – 18 oct  ›
Por km      Por viaje      Por hora                 Por km y Por hora en ink-3
Uber · CDMX                                   ▾     una fila 48dp → hoja
Por viaje, te pagan más que al                      Title 20
78 %                                                Display 56 (NBSP)
de los conductores de Uber en CDMX.                 Title 20
Tú en Uber                       ⬒  $149/viaje      marca en tinta, arriba de la raya
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━     raya = el promedio
Promedio de Uber en CDMX             $131/viaje
Los dos, antes de gastos y según los resúmenes de Uber.   Small 13
De dónde salen estos datos · 1,240 conductores ›
```

### H2b: Suscribirme
- **Driver's sentence:** "Cuánto cuesta y qué pasa si me suscribo hoy."
- **Delete:** the emerald price slab (it looked like a button); the card around the terms; the closing paragraph (it becomes one line); "Si prefieres esperar, te avisamos el 9 de noviembre" (merged into the text action).
- **Keep:** the price on the surface; the trade-off first; renewal and cancellation; the failed-charge line; the primary; the alternative; restore; legal links.

```
←   Suscribirme
$79 al mes                                          Display 56 money + Body 16
≈ $18 a la semana · precio de Google Play           Small 13 ink-2
───────────────────────────────────────────────
Google Play cobra hoy. Tus 21 días gratis no se     Body 16
pierden: se suman al final, cuando canceles.
───────────────────────────────────────────────
Se renueva cada mes. Cancelas en Google Play        Body 16 ink-2
cuando quieras. Si un cobro falla, conservas tu
acceso durante el periodo de gracia.

[         Suscribirme por $79 al mes         ]
Mejor avísame el 9 de noviembre                     texto 48dp
Restaurar compra  ·  Términos  ·  Privacidad        Small 13
```

**Screens not listed** inherit the same rules:
- G1 Cuenta: text rows, no icon tiles.
- G2 is renamed "Tu mínimo", with the real chip docked to the screen edge as the preview.
- H1: one benefit list and one button, with no green slab.
- H3: a paper share card with the K-raya and the standing sentence broken around "78 %", with no tiles.
- H6: the monochrome K-raya small icon, and the developer note is removed from the phone.

---

## 5. Scores

### 5.1 The current version on criteria 9–12 (`03-rubric-r11-craft.md`)

| # | Criterion | Score | Evidence |
|---|---|---|---|
| 9 | Signature idea | **3** | There is no idea. The chip is GigU's badge in Uber's gross typography, and nothing derived from "your costs against your floor" appears on any screen. A design director would describe it as "a dark dashboard with a green badge". |
| 10 | Glance physics | **4** | Verde and rojo are isoluminant (1.09 : 1), so the periphery cannot tell them apart, and gris is 1.40 : 1. The colour-blind-safe cue is a 22dp glyph, which only works when looked at directly. The chip must be *read* to separate net from Uber's gross $/km. C4, C5 and C6 share the verdict's silhouette. It does sit still, avoid the fare and have a stable footprint, but its default corner covers Uber's real top-right control. |
| 11 | Craft | **4** | Tailwind slate and emerald verbatim; Inter 900; 18 type sizes; 47 cards; 37 pills; circled glyphs; tinted icon tiles; two greens. Visible defects: "78 / %", "Cómo / se / calculó", the E4 seam, the B3 collision, H1's double slab, D2's orange, the H6 developer note, and a wireframe host. |
| 12 | Radical simplicity | **5** | The roots do have one hero (rounds 1–10 earned that). Every hero still carries a two-to-four-clause scope sentence plus a link; "aprox." appears three times on B1; E1 has ten blocks; E4 is a paragraph; F2 has eight elements. |
| | **Total** | **16 / 40** | **FAIL.** |

**Hard fails (R11): all four are present.**
- (a) The chip is a rounded coloured rectangle with a number and a glyph (C1–C3).
- (b) Screens that are lists of same-weight cards: A2, G1, H1's benefits, H4's import rows, G6.
- (c) A "K" square is the brand (chip, topbar, H3, H6).
- (d) The palette is Tailwind defaults (`#0f172a`, `#1e293b`, `#059669`, `#047857`, `#34d399` in `base.css`).

The structural hard fails are still none.

### 5.2 Criteria 1–8: do they still hold at ≥ 8?

| # | Criterion | Holds? | Why |
|---|---|---|---|
| 1 | One job per screen | **Holds (9)** | Each root still answers one question. |
| 2 | One primary action | **Holds (8)** | Down from 9: H1's emerald benefit slab reads as a second button directly above the primary. |
| 3 | Navigation that disappears | **Holds (9)** | Three tabs plus the avatar; drill-ins are correct. |
| 4 | Numbers discipline | **Holds (9)** | The checker passes 154 figures in 50 frames, including the E1 bars, and I re-checked C1–C3 and B1 by hand ($113.90 → $9.49/km · $285/h; $648.28 + $113.90 → $762). |
| 5 | Copy | **Drops to 7** | Measured against "said once", the scope sentences repeat each screen's qualifier (B1, D1, E1, E3, E8), and E4, A2 and H2b are paragraph-led. Round 10 scored completeness, not economy. |
| 6 | States designed | **Holds (9)** | Every state is drawn. |
| 7 | Time-to-value | **Holds (8)** | Unchanged. It remains unmeasured on a device. |
| 8 | Consistency and feel | **Drops to 7** | It is consistent, but it is the template's consistency. The "feel" half is exactly what the founder rejected, and the shared silhouette for failure and verdict is an internal inconsistency of meaning. |

### 5.3 What this proposal would score, and the evidence needed to believe it

On paper I expect **9 · 8 · 8 · 9 = 34 / 40**, with criteria 1–8 all ≥ 8 (copy and consistency back to 9). The rebuild also clears all four R11 hard fails:
- no rounded coloured rectangle with a glyph;
- no same-weight card lists;
- the K-raya instead of the K square;
- a non-Tailwind palette.

Criteria 10 and 11 are capped at 8 until there is evidence. A report cannot prove craft or physics. The evidence needed:

1. **A mounted peripheral test.** Twelve CDMX drivers, at least three with a red-green deficiency. The phone is mounted at 60 cm while the driver fixates a road video. The chip is shown at about 25° eccentricity for 300 ms and 1 s, by day and by night, comparing the current chip with the raya chip.
   - Pass: state identification ≥ 95 % for the raya chip and better than the current chip.
   - Pass: gris is never called rojo.
   - Pass: C6 is never read as a verdict.
2. **A greyscale render test.** The four states, desaturated and viewed from 2 m, are told apart by five naive viewers.
3. **An occlusion audit** on real Uber and DiDi offer layouts (founder S25 screenshots), at the default position and at 130 % font: nothing covers a host control, and the per-host safe band is recorded.
4. **A comprehension test after A1, with no instruction:** "¿Qué quiere decir que el color esté arriba de la raya?" ≥ 8 of 10 drivers answer correctly.
5. **A blind side-by-side.** Rebuilt frames next to Uber, DiDi and GigU screenshots. Three designers who have not seen the project are asked "which one has a point of view?"
6. **Checker green:** `check-fixture.py` passes unchanged, which is why the `.spark` markup is kept.

---

## 6. What must not change

**Kept as locked:**
- **Three tabs** (Hoy · Ganancias · Comparar), with Cuenta behind the avatar.
- **Reader = status:** a raya segment plus words, never a tab.
- **Drag-only chip:** tap and long-press are inert, with the touch region inset from the bezel.
- **Gris is never red.** Gris is now also never colour at all.
- **No auto-action:** the chip informs and nothing in it resembles Aceptar or Rechazar.
- **No platform logos**, and the host mock has none either.
- **"Te quedan" + one qualifier.** Kept, and enforced harder: the scope sentences go.
- **Optional signup.**
- **Free IMSS review.**
- **Fixture arithmetic:** every figure in §4 is a fixture figure; marks on Tu día's raya wait for the fixture to carry all 23 offers.
- **Legal gate B-038:** the shortened A2 and E4 wording must go back through it.
- **No verdict word and no brand strip on the chip.**
- **Silent during the trip; no money in notifications.**

**Changed without a fork (none of these is locked):**
- **The K square becomes the K-raya mark.**
- **Dark-first becomes "follow the system", designed light-first.** The app is read parked, and the hosts switch themes by time.
- **Inter and emerald are retired.**
- **The 232dp chip step is retired:** one footprint for every state.
- **The ✓ / ! / ✕ / ? glyphs are replaced by mass position.** This revises F2 (glyph + rate). The goal F2 protected, a language-free and colour-blind-safe cue, is met better.

**Forks I argue explicitly:**
1. **Rename "Tu semáforo" to "Tu mínimo"** (screen and copy). "Semáforo" is Ruta Rentable's product word and describes the template we are leaving. "Tu mínimo" is already the locked noun for the floor. The verdict state words verde / amarillo / rojo / gris stay. If the founder wants to keep "semáforo" as the driver's word for the system, that is a copy call and does not touch the design.
2. **Retire the §4 "scope line" contract** (the verbatim sentences under every hero). The lock is "Te quedan + one qualifier". The sentences were added to satisfy reviewers, not drivers, and under the new bar they *are* the defect. The same provenance moves into the tap target (the qualifier row opens "Cómo se calculó", where every source and deduction is listed, including both E1 figures). Codex will object on data integrity. My answer: the source is still named on the root by position (E1's standing row says "según tu resumen de Uber"), and no figure is ever substituted.
