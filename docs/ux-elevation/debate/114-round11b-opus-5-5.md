# Round 11b, Opus 5.5: cross-critique

**What I read and looked at.** Both Round 11 reports in full (`111-round11-codex-astra.md`, my `112-round11-opus-5-5.md`), the brief, both rubrics and `check-fixture.py` (to know which strings each frame must carry). With the Read tool I opened every render the prompt names: `concepts.png`, `b1-variants.png`, `host-test.png`, `cota.png`, `cota-zoom.png`, `raya.png`, `raya-zoom.png`, plus `cota-night.png` (rendered beside them) and the benchmarks `gigu-overlay-zoom.png` and `uber-offer-card-lightbox.jpg`. Every number below comes from the fixture. Contrast ratios are WCAG relative luminance. Visual angles assume a phone at 60 cm with 1dp = 0.159 mm.

**Verdict in one paragraph.** The renders settle more than either report could. My raya has the right *physics* and the wrong *grammar*. It has the largest colour mass, it sits docked under the host's control row, and in `raya.png` it reads as part of the phone frame. But one line cannot tell amarillo truthfully, so its amarillo is a plain yellow rectangle, and its number jumps between halves. Codex's cota has the right *grammar* and the wrong *physics*. Two rules and a stable number describe the product exactly. But the signal is a 32 × 20dp block on a light card that has no visible edge against a light map. The winner is the hybrid both renders point at: **my docked two-tone plate, carrying Codex's two thresholds as a 16dp lane, with the number fixed in place.** I call it **"la raya con carril"**. On the visual system we agree on more than we disagree: warm paper, no cards, five type steps, follow the system theme. I hold the line on three points: neutral (not green-tinted) neutrals, no fifth colour for errors, and Barlow for money.

---

## 1. Where Codex Astra is right and I was wrong, and where they are wrong

### 1.1 Codex is right; I was wrong

1. **There are two thresholds, so the signal needs two lines** (Codex §2, Candidate A: "A single rule with yellow crossing it would imply the wrong relationship"). This is the most important correction in the round. My raya drew one line, *tu mínimo*, and had amarillo "straddle" it. But $7.16/km is *below* $8.00/km. Amarillo means "between your rojo ($6.00/km) and your mínimo", not "on your mínimo". So my sketch told a lie about the state the driver most needs to think about. The render shows what that costs: in `raya.png` panel 2, amarillo is an all-yellow rectangle with a dark rule through it. That is the coloured badge the round exists to remove. I was wrong.
2. **The number must not move between states** (Codex §2: "Text stays at the same coordinates in all states"; Choice: "preserves a stable number"). In `raya.png` the number moves from the top half (verde) to the bottom half (rojo, negative), roughly one zone (≈ 36dp) lower. The eye's landing point changes with the verdict. I listed this as a cost and waved it off ("the decision does not need the number"). Criterion 10 asks for the number to *confirm* after the shape, so a moving number makes confirmation slower. I was wrong to accept it.
3. **Net-after-costs and personal thresholds are not exclusive** (Codex §1, last bullet). My pick's first argument was "Nobody shows *where you stand against your line*". That is false on our own evidence. `docs/competitive-analysis.md` lists GigU's Net Profit Calculator (line 41) and DecideRider's "progress bar against the driver's threshold" with a driver-set minimum (lines 55–56). Kompara's distinctiveness has to come from the *form*, not from the capability. The winner below is argued on form alone.
4. **A large colour fill is already peripheral information** (Codex §1, first bullet). I wrote that, because verde and rojo sit at 1.09 : 1 lightness, "the periphery literally cannot separate them". That overstated it. For a trichromat in daylight, a saturated 3° × 1.3° patch is told apart by hue in the near periphery. What the measurement does show is narrower: the current chip separates verde from rojo *only* by hue at glance scale. So it fails for colour-vision-deficient drivers, in mesopic night vision and in sun glare. The diagnosis stands; the word "literally" does not.
5. **Appearance buys no Play policy exemption** (Codex §2, closing paragraph). I argued that a "distinctive, attributed overlay is the opposite of the banking-trojan pattern". That is a guess about a heuristic, not policy. I withdraw it from the case for any concept.
6. **The source discrepancy belongs on E1's root** (Codex §1, sixth bullet; §4 E1 "Tu resumen de Uber dice un poco más >"). I moved it behind "Cómo se calculó". A driver comparing the week against their Uber statement should not have to find out by tapping. One row on the root, and the figures one tap away.
7. **"¿Se leyó mal? Avísanos" stays visible in D2** (Codex §4 D2). I hid it in an overflow ⋯. Misread reports are the parser's main quality signal and the driver's main trust lever. It goes back as a quiet row.
8. **State changes are atomic** (Codex §3 Motion: "Never animate the block travelling"). My 120 ms crossfade on a re-parse would, for one frame, show colour both above and below the line, which is a fifth, meaningless state. Swap instantly.
9. **The host needs a night version and a tall-offer / nav-banner variant** (Codex §3, "A credible host mock"). I specified only the day map. Most driving hours that matter are evening; the host's dark map is where the chip's edge treatment is tested.
10. **H2b: cut "≈ $18 a la semana" and move the failed-charge paragraph to H2c** (Codex §4 H2b). Both are extra numbers and extra sentences before the decision. Agreed.
11. **Text tabs need an underline** (Codex §3: "text tabs and an underline"). I removed it to keep 2dp lines for the raya. That traded a real affordance for a grammar that only needs *full-width* lines. A short 24dp underline cannot be confused with a full-width raya.
12. **F2: one context row, and "Aportar al promedio ›" stays visible** (Codex §4 F2). It is better than my "move the contribution into the provenance sheet". It stays default-off consent, and it feeds the benchmark.
13. **D1: three shift rows, not my day-raya timeline** (Codex §4 D1). My timeline put the "12:30" and "12:40" labels about 6dp apart on a 353dp line, which cannot be drawn. Rows led by a solid or dashed raya segment keep the signature and stay readable.

### 1.2 Codex is wrong

1. **The cota fails criterion 10 by construction** (`cota.png`, `cota-zoom.png`).
   - The verdict mass is a 32 × 20dp block, 640dp², 3.6 % of the plate, about 0.49° × 0.30° at 60 cm. That is a target you have to look straight at.
   - Amarillo's block is 32 × 12dp (2.2 %, 0.18° tall): the smallest signal goes to the state that needs the most attention.
   - The rubric's 10 is "read peripherally (shape/position/colour mass) before any number". In `cota.png` the bold "$9.49" is what the eye lands on; the block is a small swatch beside it. Codex concedes it ("Its weakest point is the smaller colour mass") and names the fix ("increase the mark's area within the same footprint"). The winner takes that fix to its end.
2. **By day the cota plate has no edge, and by night it is a white slab.** The rendered plate (≈ `#F5F3EB`) against the map (`#F4F6FB`) is **1.03 : 1**; only a hairline separates them in `cota.png`. In `cota-night.png` the same plate is the brightest object on a dark map, glare on a driver's dark-adapted eyes. A chip ink body is 17.75 : 1 on the day map, and at night it recedes, leaving only the verdict zone lit.
3. **The cota is GigU's overlay, and its amarillo mark is the ≡ menu glyph.** Put `cota-zoom.png` beside `gigu-overlay-zoom.png`. Both are a light card with bold figures, each led by a small coloured bar. Separately, amarillo's mark (rule, yellow bar, rule) is three horizontal bars: the hamburger icon that sits in the host's own top-left corner (`raya.png`'s host row, `uber-offer-card-lightbox.jpg`). It fails both "nobody else's" and "never looks like a control".
4. **The cota as drawn covers a host control.** Its plate spans roughly y 70–158dp at the right edge, which is the band where Uber keeps ⌕ (`uber-offer-card-lightbox.jpg`; `raya.png` host row at y 84–120). Placement is fixable, but the render shows how easily "top: 72px" comes back.
5. **The simplification is not radical enough.** Rubric 03 #12 asks for copy "said once"; the brief says "a qualifier is said once per screen".
   - Codex's B1 brings back the scope line ("Según tus ofertas · menos gasolina y desgaste · aprox. >").
   - E1 states the car-cost basis three times: "menos gasolina, desgaste y coche", "Lo del coche · ya restado −$1,500" and "Por día · antes de lo del coche".
   - E1 ends with two equal footer actions ("Compartir tu semana" and "Trae tu resumen"), which breaks rubric 02 #2 (one primary).
6. **The palette moves the brand-verdict collision into the neutrals** (Codex §3 Palette).
   - Every neutral is green-tinted: `#141D18` night surface, `#17231E` ink, `#B8C3B8` muted, brand `#226346`. Green then means "background" as well as "te deja".
   - `--attention #785A17` and `--error #9D3B35` add two hues. B2 draws a reader problem with "a short error-coloured rule", a red-brown next to verdict rojo for something that is not a verdict. That breaks the principle behind "gris never red": a failure must never look like a judgement.
7. **The failure state C6 borrows gris's grammar** (Codex §4 C6). "-- --  Lector detenido … -- --" is the broken-rule mark, so a stopped reader looks like C4's missing fare. Both of us diagnosed "failure dressed as a verdict" in §1. C6 must be a different object, with no rules.
8. **Roboto Condensed 700 for money is the weaker choice** (Codex §3 Type). It is Android's system condensed face, so the chip speaks the host OS's voice. In `cota-zoom.png` it reads as a generic ticker. Barlow Semi Condensed in `raya-zoom.png` is just as narrow and has a letterform of its own (the road-sign lineage). This is taste, but criterion 11 scores taste.

### 1.3 What the synthesis lead's renders settle

- **`concepts.png`.** All three of Fable's concepts fail a hard check; the tournament in §2 says which.
- **`b1-variants.png`.**
  - Removing cards does most of the work. The light (middle) and refined dark (right) variants are the same design and both read as product, not template. Codex's §1 point ("a white reskin would retain it") is half right: the surface model matters more than the surface colour.
  - Both variants keep three template tells: an Inter 900 hero; green "Lector funcionando" (green as status, colliding with verde as verdict); and dot-led rows (hue-only marks).
  - **Both would fail `check-fixture.py`.** They print "6 viajes" where B1 must carry "6 viajes tomados", and they drop "Tu mejor hora hoy: 9 a 10 h", which B1 must carry.
- **`host-test.png`.**
  - The offer card is right: the "Viaje" capsule, $148.00, the two rows, the black Aceptar.
  - The map is still a regular grid of rounded tiles.
  - The top control row (≡ · En línea · ⌕) is missing, and it is the main occlusion constraint. `raya.png` added it.
  - The old chip at y ≈ 72–143dp sits exactly where ⌕ lives. That one image argues for the placement rule in §2.
- **`raya-zoom.png` shows my own craft defect.** The K at the bezel corner is clipped by the screen edge. Docked content needs a 16dp inset from the bezel because of rounded and curved screens.

---

## 2. Chip concept tournament

Scores are 1–10 on criterion 9 (signature) and criterion 10 (glance physics). The four structural hard-fail columns are Yes / No / Risk. "Colour-only" means the state can be told apart only by hue at glance distance.

| # | Candidate | 9 | 10 | Covers a control | Looks like a control | Gris reads as verdict | Colour-only | Justification |
|---|---|---|---|---|---|---|---|---|
| 1 | **Opus A · La raya** (`raya.png`) | 7 | 6 | No (y=144, under ≡ · ⌕) | No (docked, reads as frame) | No (no colour, dashed) | No for verde/rojo; amarillo is "all yellow", which is the template rectangle | Biggest mass, four greyscale patterns. But one line misstates amarillo, the number jumps, and the K is clipped by the bezel. |
| 2 | Opus B · El brazo | 4 | 3 | No | No | No (outline) | No (tilt) | An 8° tilt on a 1.3° object is invisible in the periphery; level amarillo is a flat badge; slope means "trend", not "offer". |
| 3 | Opus C · El ticket | 6 | 2 | No | No | No | Risk (the stamp is about 20 % of the area) | Carries well into the app but has to be read; a white stub disappears on a white card; three numbers. |
| 4 | **Codex A · La cota** (`cota.png`) | 6 | 3 | As drawn, yes (plate over the ⌕ band) | Risk (amarillo mark = ≡) | No (broken rules) | In effect yes: the position cue is 0.3°, so at a glance only the hue of a tiny block reaches the eye | Right semantics (two rules, stable number); wrong physics (3.6 % mass, 1.03 : 1 plate); GigU's look. |
| 5 | Codex B · El recibo | 4 | 2 | No | Yes (Codex: the tab "can look draggable … drawer") | No | No | A 32 × 24dp edge feature is too subtle, and it expresses subtraction, not the floor (Codex's own words). |
| 6 | Codex C · El margen | 3 | 2 | No | Yes (Codex: "selection handles or a crop tool") | No | No | Negative space is weak; "overlap = below threshold" has to be learned. |
| 7 | Fable A · La banda (`concepts.png` left) | 5 | 3 | Yes ("under the status bar" is where Uber keeps ≡ · earnings · ⌕) | Yes (a full-width green top band is the navigation banner of Maps/Uber, or a heads-up notification) | n/a | Yes (same shape in every state) | Huge mass. But it breaks the 200 × 88dp footprint, and its "+$1.49 sobre tu mínimo" would print "−$0.84" beside a positive $7.16/km, colliding with the true negative (−$1.05/km). |
| 8 | Fable B · El marcador (`concepts.png` middle) | 5 | 4 | As drawn, yes (y ≈ 73–165dp) | Yes (a track with a round thumb is a Material slider) | n/a | Risk (the dot on a 6dp track is the only non-hue cue) | Floors as ticks are the right meaning, but the form is a bullet chart on GigU's white card. The proportional track clamps the negative and anything above its end. |
| 9 | Fable C · La ficha (`concepts.png` right) | 6 | 3 | As drawn, yes (y ≈ 68–145dp) | No | n/a | Yes | Typographic confidence, DiDi-scale numerals. But a yellow numeral on the light map is 1.68 : 1, the numeral out-sizes the host's $148.00 (rubric #10: "nothing competes with the host's fare"), and it drops the "$". |
| — | **Winner · La raya con carril** (forecast) | 9 | 9 | No (per-host safe band) | No | No | No (position, plus four distinct lightness patterns) | Opus A's mass and docking, plus Codex A's two thresholds and stable text. Capped at 8 until the §2.9 test passes. |

### 2.1 The winner: **la raya con carril**, and exactly what comes from where

- **From Opus A (raya):**
  - the docked two-tone plate, square on the bezel side;
  - the chip ink (`#0E0F10`) body;
  - the verdict as a full-width colour zone above or below the line;
  - no glyph and no verdict word;
  - the default dock under the host's control row (y = 144dp);
  - Barlow Semi Condensed money;
  - the K-raya mark;
  - C6 as a different object.
- **From Codex A (cota):**
  - two thresholds, drawn as two rules: the upper is tu mínimo ($8.00/km), the lower is tu rojo ($6.00/km);
  - the categorical (not proportional) reading;
  - every text element at the same coordinates in every state;
  - broken rules for gris;
  - atomic state changes;
  - clearing old values immediately on a new offer.
- **From Fable:** the realistic host and the docked-render evidence. Nothing from A/B/C goes on the chip. The *margin* idea is already said in D2 ("Llega a tu mínimo de $8.00/km") without a new signed number.

**One-sentence pitch:** *"Your two limits are a lane across the plate: the colour sits above it, inside it, or below it."*

The raya is no longer a hairline. It is a **16dp carril** whose upper edge is your mínimo and whose lower edge is your rojo.
- Verde: the colour fills the zone above the carril.
- Amarillo: the colour fills the carril itself.
- Rojo: the colour fills the zone below the carril.
- Gris: no colour, and the carril's edges are dashed.

The number never moves. It is not a traffic light: there are no lamps, no housing and no unlit coloured slots. The order is also the inverse of a real semáforo, whose red lamp is on top. Here "up = more money", the grammar of a chart.

### 2.2 Anatomy (plate-local coordinates, origin top-left, docked to the right bezel)

| Element | Spec |
|---|---|
| Plate | **200 × 88dp**. Corners: 0 on the bezel side; 6dp on the two inner corners. Fill `--chip-ink #0E0F10`. 1dp keyline `rgba(255,255,255,.28)` on the three free sides. Shadow `0 2dp 8dp rgba(0,0,0,.25)`. |
| Zone T (number) | y **0–36dp**, full width. |
| Carril | y **36–52dp** (16dp), full width, running to the bezel. Edge rules are 2dp, `rgba(245,244,239,.8)`, at y 36–38 and y 50–52. Between them is chip ink. |
| Zone B (qualifier) | y **52–88dp**, full width. |
| Verdict fill | **verde:** zone T = `#187A36`. **amarillo:** carril y 36–52 = `#F2B705`, no edge rules. **rojo:** zone B = `#D32F2F`. **gris:** no fill; the edge rules become dashed 6 on / 4 off at 50 %. |
| Number | Barlow Semi Condensed SemiBold 600, `tnum lnum`, **32sp**, x = 16dp, baseline y = 29dp. The unit "/km" or "/h" is **16sp** Medium on the same baseline, in the same text run as the number, with no whitespace (the checker asserts "$9.49/km" as one string). Colour: `#FFFFFF` on verde (5.42 : 1), `#EDEDEA` on chip ink (16.4 : 1). |
| Qualifier | Barlow Semi Condensed Medium 500, **16sp**, x = 16dp, baseline y = 76dp. Colour: `#EDEDEA` on chip ink, `#FFFFFF` on rojo (4.98 : 1). |
| K-raya | 12 × 12dp at x 172–184, y 11–23: always 16dp from the plate's right edge, in zone T, at 60 % of the on-colour. It never sits in the bezel corner, which is the defect in `raya-zoom.png`. |
| Gris text | Top: Barlow 600 **20sp**, baseline y = 25dp, `#EDEDEA`. Bottom: Barlow 400 **16sp**, baseline y = 76dp, `#B9B8B3` (9.7 : 1). Never a peso figure. |
| Text inset | 16dp from both plate edges in every state; content does not mirror when the chip docks left (only the square corners swap sides). |

Colour mass and visual angle at 60 cm (the whole plate is 3.0° × 1.3°):

| State | Lit area | Share of plate | Lit zone in degrees |
|---|---|---|---|
| verde | 7,200dp² | 41 % | 3.0° × 0.54° |
| rojo | 7,200dp² | 41 % | 3.0° × 0.54° |
| amarillo | 3,200dp² | 18 % | 3.0° × 0.24° |

For comparison, the cota lights 640dp² in verde and rojo and 384dp² in amarillo. Amarillo's lane is the highest-contrast element on the plate: `#F2B705` on chip ink is 10.55 : 1.

### 2.3 The states at 1×

Legend: 32 characters ≈ 200dp. `│` is the bezel. `V` / `A` / `R` are the verdict fills, `░` is chip ink, `═` is the empty carril (two paper rules), `╍` is the dashed carril, `K` is the 12dp K-raya.

```
VERDE  ($148 · 12.0 km · 24 min)        AMARILLO  ($120 · 12.0 km · 27 min)
VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV│       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
VV $9.49/km  VVVVVVVVVVVVVVK VVV│       ░░ $7.16/km  ░░░░░░░░░░░░░K ░░░│
════════════════════════════════│       AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA│
░░ neto aprox. · $285/h  ░░░░░░░│       ░░ neto aprox. · $191/h  ░░░░░░░│
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│

ROJO  ($62 · 12.3 km · 30 min)          ROJO, NEGATIVO  (C7: $22 · 12.3 km · 30 min)
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
░░ $2.20/km  ░░░░░░░░░░░░░░K ░░░│       ░░ −$1.05/km  ░░░░░░░░░░░░░K ░░│
════════════════════════════════│       ════════════════════════════════│
RR neto aprox. · $54/h  RRRRRRRR│       RR neto aprox. · −$26/h  RRRRRRR│
RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR│       RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR│

GRIS · C4  (faltó la tarifa)            GRIS · C5  (decide por hora, falta tiempo)
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
░░ Sin lectura  ░░░░░░░░░░░░░K ░│       ░░ Falta tiempo  ░░░░░░░░░░░░K ░│
╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ │       ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ │
░░ faltó la tarifa  ░░░░░░░░░░░░│       ░░ sin semáforo  ░░░░░░░░░░░░░░░│
░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│
```

**Exact strings** (the minus is U+2212, kept on one line with its amount and unit):

| State | Zone T | Zone B | Fill |
|---|---|---|---|
| verde (C1) | `$9.49/km` | `neto aprox. · $285/h` | zone T |
| amarillo (C2) | `$7.16/km` | `neto aprox. · $191/h` | carril |
| rojo (C3) | `$2.20/km` | `neto aprox. · $54/h` | zone B |
| rojo, negativo (C7) | `−$1.05/km` | `neto aprox. · −$26/h` | zone B |
| gris (C4) | `Sin lectura` | `faltó la tarifa` | none, dashed carril |
| gris (C5) | `Falta tiempo` | `sin semáforo` | none, dashed carril; never `$9.49` |

Width check at 1.0× font scale:

| String | Size | Width | Ends at | Room |
|---|---|---|---|---|
| `−$1.05/km` | 32 + 16sp | ≈ 115dp | x ≈ 131 | 41dp clear of the K |
| `neto aprox. · −$26/h` | 16sp | ≈ 135dp | x ≈ 151 | inside 200dp |

The 232dp C7 step is retired.

### 2.4 Placement rule

- **Dock** flush to the end bezel (right in LTR). Drag-only to the other bezel; it snaps to the nearer edge.
- **Vertical position comes from the host's validated safe band.** The band runs from the bottom of the host's top controls + 16dp to the top of the host's offer card − 16dp. The chip never covers the fare, Aceptar, the countdown, the ≡ / earnings / ⌕ row or a navigation banner.
- **Default** for the 393 × 852dp Uber-like reference: top at **y = 144dp** (control row 84–120 plus a 24dp gap).
- **Saved positions** are stored per host app and orientation. They are revalidated on every layout change (tall offer, nav banner, font scale). If the saved spot is unsafe, the chip moves to the nearest safe y. If no band fits the plate's current height, the chip is **suppressed**; the offer is still logged in Tu día.
- **Touch** region = the plate minus a 24dp strip at the bezel, so a drag never fires Back and Back never fires a drag. Tap and long-press are inert.

### 2.5 Growth rule

- **Width is fixed at 200dp** in every state and for every fixture amount.
- **Height grows only with system font scale.** Zones T and B scale; the carril stays 16dp. At 1.3×: 47 / 16 / 47 = 110dp. At 1.5×: 54 / 16 / 54 = 124dp. Placement is revalidated after any growth, and the chip is suppressed if no band fits.
- **Never truncate or ellipsize a figure.** If a line still does not fit, drop the K first, then let the zone B line wrap to two lines (zone B grows). Money, sign and unit never separate.

### 2.6 Day / night treatment

- **The chip is theme-independent.** It is drawn over a host whose theme we do not control, so the plate is always chip ink and the verdict triple keeps its exact colours and contrast in both modes. That stays locked.
- **By day** (light map) the plate is the darkest object on screen (17.75 : 1 against `#F4F6FB`), with the keyline and shadow.
- **By night** (dark map) the ink body recedes (1.22 : 1 against a `#1F2329` map). The 28 % keyline draws the edge, and **only the verdict zone emits light**: the peripheral signal is a lit patch on dark, and there is no white slab (compare `cota-night.png`). Gris at night is deliberately quiet: keyline, dashed carril, `#EDEDEA` words.

### 2.7 Colour-blind cue

The state is coded by **position relative to the carril**, so it needs no hue. In greyscale the four states are four lightness patterns:
- verde: top zone lit (L 0.144), the rest at L 0.005;
- amarillo: a thin bright stripe (L 0.528) across a dark plate;
- rojo: bottom zone lit (L 0.161);
- gris: uniformly dark, with dashed edges.

A deuteranope who sees verde and rojo as similar olives still sees top versus bottom. Gris can never be read as rojo, because gris has no lit zone at all.

### 2.8 Motion, and C6

- **Appear:** ≤ 150 ms after the parse, the plate slides 16dp out of the bezel with a fade, over 120 ms on a decelerate curve. Under reduced motion: a 100 ms fade only.
- **Leave:** 500 ms after the offer card leaves (same offer only), it slides back over 160 ms.
- **Re-parse to a different state:** an atomic swap, with no crossfade and no zone travel.
- **New offer:** old values clear immediately.
- **Never:** pulse, count-up, haptic or sound.

**C6 · Lector detenido is a different object.**
- 200 × 48dp, chip ink, docked to the same bezel, inner corners 6dp.
- **No carril, no rules, no colour, no K.**
- Copy: "Lector detenido" (Barlow 600, 16sp, baseline y = 20) and "Revisa Kompara al estacionarte" (Barlow 400, 13sp, baseline y = 38).
- Shown for 5 s, once per host foreground; touches pass through. It cannot be mistaken for C4: it is half the height, with no lane.

### 2.9 The mark, and carry-through

**The mark** is the chip in miniature and the only leading glyph in list rows.
- 16 × 24dp, radius 3dp, 1.5dp outline in `--ink-2`.
- Two 1dp `--ink-3` rules at y = 9 and y = 15 (the carril).
- Fill: verde y 0–9, amarillo y 9–15, rojo y 15–24. Gris has a dashed outline and no fill.
- Wireframes write it as `[V] [A] [R] [G]`.

How the idea carries into the app:
- **Hoy:** offer rows are led by the mark. Reader status is a 16 × 2dp ink raya segment plus words: solid = funcionando, dashed = apagado, broken "━ ━" = preparado.
- **Tu día:** offer rows are led by the mark; shift rows by a solid (en turno) or dashed (pausa) segment.
- **Ganancias:** bars stand on a 2dp ink raya at $0.
- **Comparar:** in F2 the city average *is* the raya, with "Tú en Uber" above it, drawn in ink because a comparison is not a verdict.
- **D2:** "Llega a tu mínimo de $8.00/km", with the mark in the title.

**What would make me abandon or adjust it** (the founder fork in §5):
- **The test:** 1 s exposure at about 25° eccentricity, 12 CDMX drivers, at least 3 colour-vision deficient, day and night.
- **Pass:** state identification ≥ 95 %, and gris is never called rojo.
- **If amarillo alone misses:** widen the carril to 20dp by setting the number at 30sp (zones 34 / 20 / 34). Do not go back to an all-yellow plate.
- **Comprehension:** after A1, at least 8 of 10 drivers answer "¿Qué quiere decir el color en medio?" correctly.

---

## 3. Visual system verdict

### 3.1 Theme: both, following the system, designed light-first

- **Kompara follows the system theme.** There is no onboarding question; Cuenta gets a "Tema · Automático / Claro / Oscuro" row.
- **Mockups lead with day**: it breaks the dashboard dialect, and the hosts are light by day.
- **The night set ships in v1.** The driver opens Kompara parked, often after dark. Hosts switch their maps dark at night, and a paper screen in a dark car is a flash.
- `b1-variants.png` proves the structure survives both modes, because the middle and right panels are the same design. Render B1, D1, E1, F1 and C1–C5 (on the dark host) in night as well.
- **Neutrals are neutral, not green-tinted** (§1.2 item 6).

| Token | Day | Night | Replaces in `base.css` |
|---|---|---|---|
| `--paper` | `#F5F4EF` | `#121211` | `--background`, `--surface` (`#0f172a`), `html,body #0b1120` |
| `--paper-raised` | `#FFFFFF` | `#1E1E1C` | `--surface-card` (`#1e293b`), for **sheets only** |
| `--paper-2` | `#EBE9E2` | `#262624` | Input fields, pressed state, skeletons; never a card |
| `--ink` | `#111110` (17.2 : 1) | `#F2F1EC` (16.6 : 1) | `--text-strong`, `--text-body`; primary button fill (`emerald-700`) |
| `--ink-2` | `#5C5B57` (6.2 : 1) | `#A8A7A1` (7.8 : 1) | `--text-muted` (`slate-300`) |
| `--ink-3` | `#8A8984` (3.2 : 1) | `#74736E` (4.0 : 1) | `--text-faint`; ≥ 18sp text and non-text only |
| `--rule` | `#DCDAD2` | `#2C2C29` | Row borders, `--border`, `--border-strong`; 1dp hairline |
| `--raya` | 2dp `--ink` | 2dp `--ink` | New: the $0 line, status segments, the F2 average |
| `--on-primary` | `--paper` | `--paper` | White on emerald |
| `--verde` / on | `#187A36` / `#FFFFFF` (5.42 : 1) | same | Unchanged |
| `--amarillo` / on | `#F2B705` / `#1A1300` (10.16 : 1) | same | Unchanged |
| `--rojo` / on | `#D32F2F` / `#FFFFFF` (4.98 : 1) | same | Unchanged |
| `--chip-ink` / on / on-2 | `#0E0F10` / `#EDEDEA` / `#B9B8B3` | same (theme-independent) | `chipNeutral #475569` |
| `--chip-rule` | 2dp `rgba(245,244,239,.8)` | same | New: carril edges |
| `--chip-keyline` | 1dp `rgba(255,255,255,.28)` | same | `.chip` shadow `0 8px 24px` |
| `--k` | `--verde` on the app icon; `--ink` in product | same | `--emerald-600`, `--primary` |

**Deleted:** every `--emerald-*` and `--slate-*`, `--info-blue`, `--amber-500`, `--error #dc2626`, and Codex's proposed `--brand #226346`, `--attention` and `--error`.

**Colour rules:**
- Errors and reader problems are **ink** states: words plus a dashed raya segment, never a hue.
- Destructive actions are ink text behind a confirmation.
- Verdict colours appear only on verdicts (the chip and the mark).
- No fifth colour anywhere.

### 3.2 Money typeface and the scale

- **Money:** Barlow Semi Condensed SemiBold 600, `tnum lnum`.
- **UI:** Barlow 400 and 600.
- Bundle the exact files; never simulate condensing with horizontal scaling (Codex is right).
- **Gate:** confirm `tnum` in the shipped files and check 13sp Barlow at 1× on a device. If either fails, UI falls back to Roboto and money falls back to Archivo SemiCondensed 600.
- No weight 800 or 900 anywhere. Inter is retired.

| Step | Size / line | Face | Use |
|---|---|---|---|
| Display | 56 / 56 | Money 600 | One hero per screen (Hoy, Tu día, Semana, Mes, F1 difference, F2 "78 %", H2b price) |
| Number | 32 / 36 | Money 600 | Chip number; E4 per-app figures |
| Title | 20 / 26 | UI 600 | Screen titles, sentence heroes, sheet titles, gris words on the chip |
| Body | 16 / 22 | UI 400 (600 for a row label that is the content) | Rows, buttons (600), chip zone B (Money 500) |
| Small | 13 / 18 | UI 400 | Labels, qualifiers, captions, nav labels; the floor |

Non-breaking spaces are required in "78 %", "$1,500", "8 h 10 min", "12 – 18 oct" and "−$1.05/km". No text wraps into a right gutter.

### 3.3 Surface model: one surface, no cards

- **The hero sits on the surface**, top-left: the number first, then one label line (as in `b1-variants.png`).
- **Groups** are separated by 32dp of whitespace and, where a ledger needs it, full-bleed 1dp `--rule` hairlines.
- **Rows** are at least 56dp, with one label, one value or chevron, and at most one figure.
- **Elevation in three places only:** sheets (top radius 16dp, `0 −8dp 24dp rgba(0,0,0,.12)`), the chip, and snackbars.
- **One primary per screen:** `--ink` fill, `--paper` text, 56dp tall, radius 12dp, full width, at the thumb. Secondary actions are ink text buttons (600, 48dp).
- **Selectors are text tabs:** active `--ink` 600 with a 24 × 2dp ink underline; inactive `--ink-3` 400. Choice lists open in sheets.
- **No pills anywhere.** "Ejemplo" is a 13sp label. Status is a raya segment plus words. The avatar is a 32dp initials ring.
- **Icons:** Material Symbols Sharp 300, 24dp, `currentColor` (fixes the black nav glyphs Codex found). Never inside a container; no icon tiles on rows.
- **Spacing and gutter:** a 4 / 8 / 12 / 20 / 32dp rhythm with a 20dp page gutter (Codex's values, adopted).

### 3.4 Host mock: corrections to `host-test.png`

- **Keep** the offer card exactly as rendered.
- **Replace the tile grid** with an irregular street network: white roads with 1px `#D9DBD5` casing, one diagonal avenue, one park, grey labels ("Av. Insurgentes Sur", "Calz. de Tlalpan").
- **Add the top control row** at y 84–120: a white 48dp ≡, a black "En línea" capsule, a white 48dp ⌕.
- **Night version:** map `#1F2329`, roads `#2E333B`, the same controls inverted, offer card `#2A2E35`.
- **Variants:** add a tall-offer variant and a navigation-banner variant (Codex), so the safe band visibly changes.
- **Never:** logos, Uber blue, DiDi orange, or a host earnings figure.

---

## 4. Simplification merge

Shared rules:
- "Te quedan" plus one qualifier, said once. The deduction basis is said in at most six words on the root, and the full provenance is one tap away.
- Share is a top-bar ⤴ icon.
- The mark replaces every circled glyph, dot and colour word.

`[V] [A] [R] [G]` are the marks. Annotations on the right are not displayed strings. Every figure the checker asserts for each frame is present.

### A1 · Demo (layout: Opus; gris legend: Codex)
```
K Kompara                                           K-raya + wordmark, ink
Antes de aceptar, ve si te deja.                    Title 20
Ejemplo                                             Small 13
┌ mini host: mapa + fila ≡ · En línea · ⌕ ─────────┐
│                       chip verde pegado al borde │ $9.49/km / neto aprox. · $285/h
│ $148 · 12 km en total (2 para recoger) · 24 min   │
└───────────────────────────────────────────────────┘
Color arriba: llega a tu mínimo.                    Body 16; changes with the state
Ver otra oferta                                     text 48dp
[                 Empezar                 ]
Ya tengo cuenta
```
The legend teaches the glance code once, here:
- verde: "Color arriba: llega a tu mínimo."
- amarillo: "Color en medio: entre tu mínimo y tu rojo."
- rojo: "Color abajo: abajo de tu rojo."
- gris: "Sin color: faltan datos para calcular."

### A2 · Lo que hace Kompara (copy: Codex; layout: Opus; legal must re-read, B-038)
```
‹  Lo que hace Kompara                              progreso 2dp ink
Para mostrarte lo que deja una oferta.              Title 20
Lee tu pantalla. Usa la accesibilidad de Android…   Body 16, lead words 600
───────────────────────────────────────────────
Guarda las cifras. Solo las de tus viajes…
───────────────────────────────────────────────
Qué sale del teléfono. Conteos anónimos…
───────────────────────────────────────────────
Tú decides. Kompara solo lee…
[                 Acepto                  ]
No acepto
```
The blocks are always expanded, never accordions:
- **Lee tu pantalla.** "Usa la accesibilidad de Android. Cuando aparece una oferta en Uber o DiDi, toma una imagen de toda tu pantalla para leer tarifa, distancia y tiempo, y la borra al momento."
- **Guarda las cifras.** "Solo las cifras de tus viajes, en este teléfono. Nunca imágenes ni datos del pasajero."
- **Qué sale del teléfono.** "Conteos anónimos de lectura (los apagas en Tus datos). Solo si tú lo eliges: tus totales con tu cuenta, tu aporte al promedio de tu ciudad, un archivo que subas o un reporte de lectura."
- **Tú decides.** "Kompara solo lee: nunca acepta ni rechaza por ti. Uber o DiDi podrían no permitir lectores como Kompara."

### A5 · ¿Cuánto rinde tu coche? (structure: Codex; stepper: Opus)
```
‹  ¿Cuánto rinde tu coche?                          Title 20
Puedes ajustarlo después.                           Body 16 ink-2
      ( − )          12 km/L          ( + )         Display 56; 56dp targets
Típico de un sedán                                  Small 13
───────────────────────────────────────────────
Gasolina a $24.50 el litro · referencia             Body 16 ink-2
Desgaste $0.80 por km                               Body 16 ink-2
Manejar te cuesta $2.84 por km                      Body 16 600
En 12 km: unos $34                                  Body 16
Ya sé mi costo por km                               text 48dp
[           Usar 12 km/L (típico)           ]
```

### B1 · Hoy (structure: `b1-variants.png` middle, corrected)
```
Hoy                                          (JA)   Title 20; initials ring
━━ Lector funcionando                               Small 13 ink; solid segment, NOT green
$762                                                Display 56
Te quedan hoy · 6 viajes tomados                    Body 16
Ya sin gasolina ni desgaste · aprox.          ›     Small 13 ink-2 → Cómo se calculó
Tu mejor hora hoy: 9 a 10 h                   ›
───────────────────────────────────────────────
Últimas ofertas                      Ver tu día ›   Title 20 / text 48dp
[V]  La tomaste                            14:32
[R]  Se fue sin tomar                      12:10
[G]  Sin lectura · faltó la tarifa         11:48
   Hoy            Ganancias           Comparar
```

### B2 · Hoy, reader off (merged)
```
Hoy                                          (JA)
╍╍ Lector apagado desde las 7:50                    Small 13 ink; dashed segment, no hue
Tu teléfono quitó el permiso                        Title 20 (the cause is the hero)
de accesibilidad.
Sin él, Kompara no ve tus ofertas.                  Body 16 ink-2
───────────────────────────────────────────────
Aún no registramos viajes hoy.                      Body 16 ink-2
Ayer te quedaron $1,240 aprox.                      Body 16
[              Encender lector              ]       at the thumb
   Hoy            Ganancias           Comparar
```

### B3 · Hoy, first run (copy: Codex; docked sample: Opus)
```
Hoy                                          (JA)
━ ━ Lector preparado                                Small 13; broken segment
Abre tu app de viajes.                              Title 20
La primera oferta confirmará la lectura.            Body 16 ink-2
                     Ejemplo  [chip verde, docked]│ $9.49/km / neto aprox. · $285/h
───────────────────────────────────────────────
Tu mínimo $8.00/km · costos típicos           ›     → Tu mínimo
Elegir mi ciudad                              ›
[            Avísame si se apaga            ]
   Hoy            Ganancias           Comparar
```

### C1 · Verde in the host (C2, C3, C7 and C4 follow §2.3 on the same host)
```
14:32                                         82 %
(≡)             ( En línea )                 (⌕)   host row y 84–120
                       VV $9.49/km VVVVVVVVVVVK V│  zone T verde, plate top y=144
                       ══════════════════════════│  empty carril
                       ░░ neto aprox. · $285/h ░░│  zone B chip ink
   [mapa: Av. Insurgentes Sur · ruta · recogida]
┌────────────────────────────────────────────────┐
│                    ( Viaje )                    │
│ $148.00                                         │
│ ○ 6 min (2.0 km) de distancia                   │
│ ■ 18 min (10.0 km) de viaje                     │
│ [                  Aceptar                   ]  │
```
- **C2:** amarillo carril, "$7.16/km" / "neto aprox. · $191/h". Host: $120.00 · 5 min (2.0 km) · 22 min (10.0 km).
- **C3:** zone B rojo, "$2.20/km" / "neto aprox. · $54/h". Host: $62.00 · 12 min (5.2 km) · 18 min (7.1 km).
- **C7:** zone B rojo, "−$1.05/km" / "neto aprox. · −$26/h", still 200dp. Host: $22.00 · 12 min (5.2 km) · 18 min (7.1 km).
- **C4:** gris, "Sin lectura" / "faltó la tarifa", dashed carril. Host fare "— —" · 4 min (1.5 km) · 15 min (6.0 km).

### C5 · Gris, deciding unit missing
```
14:32 / host row as C1
                       ░░ Falta tiempo ░░░░░░░░K ░│  Title 20
                       ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍ ╍╍╍│  dashed carril
                       ░░ sin semáforo ░░░░░░░░░░░│  Body 16; no rate anywhere
host card: $148.00 · Recoger — · 2.0 km · Viaje — · 10.0 km · Aceptar
```
"ve el detalle al estacionarte" is cut: the detail always exists in Tu día. `$9.49/km` must not appear (the checker fails C5 if it does).

### C6 · Lector detenido (a different object)
```
host: Conectado · map · controls
                       ░░ Lector detenido ░░░░░░░│  200 × 48dp, no carril, no colour, no K
                       ░░ Revisa Kompara al estacionarte │  13sp; 5 s; touches pass through
```

### D1 · Tu día (shift rows: Codex; hero and marks: Opus)
```
←       ‹  Hoy · lunes 19 oct  ›                    Title 20
$1,240                                              Display 56
Te quedan hoy · 9 viajes tomados                    Body 16
Ya sin gasolina ni desgaste · aprox.          ›     Small 13 → Cómo se calculó
En turno · 8 h 10 min                               Small 13 ink-2
━━ 08:00 – 12:30 · 5 viajes                  $648
╍╍ 12:40 – 14:20 · tú lo pausaste
━━ 14:20 – 18:00 · 4 viajes                  $592
Todas · 23    Verdes sin tomar · 2     neto por km  text tabs; "neto" said once
[A]  14:50 · La tomaste                   $7.16/km
[V]  14:32 · La tomaste                   $9.49/km
[R]  12:10 · Se fue sin tomar             $2.20/km  (11:48 [G] Sin lectura · faltó la tarifa scrolls)
```

### D2 · Detalle de oferta, sheet (rows and actions: Codex; hero: Opus)
```
[V] Oferta de las 14:32 · La tomaste                Title 20
$114                                                Display 56
Te quedan aprox. · $9.49/km · $285/h neto           Body 16 ink-2
Llega a tu mínimo de $8.00/km                       Body 16
Tarifa de la oferta                          $148
Distancia (2.0 km para recoger)           12.0 km
Tiempo                                     24 min
Gasolina y desgaste (12.0 × $2.84)           −$34
El pago final puede cambiar.                        Small 13
¿Se leyó mal? Avísanos                        ›
Ajustar mi mínimo                             ›
¿Se canceló? Quitar de mi día                 ›     undo snackbar
```

### E1 · Ganancias · Semana (economy: Opus; discrepancy row: Codex)
```
Ganancias                               ⤴    (JA)
Semana    Mes                                       text tabs, 24dp underline
‹  12 – 18 oct · semana pasada  ›
$4,980                                              Display 56
Te quedaron esa semana · 62 viajes tomados          Body 16
Ya sin gasolina, desgaste ni coche (−$1,500) · aprox. ›   Small 13 → Cómo se calculó
Tu resumen de Uber dice un poco más           ›     → sheet: both figures + Trae tu resumen
 ▅    ▆    ▆    ▆    █    ▅    ▃                    .spark 59 69 66 75 100 65 36 %
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  raya at $0 · L M M J V S D
Por día, antes de lo del coche                      Small 13
Tu mejor día fue el viernes                   ›     → E1b
Tu lugar entre los de Uber en CDMX            ›     → F2 (the basis is said there)
```

### E3 · Ganancias · Mes
```
Ganancias                               ⤴    (JA)
Semana    Mes
‹  Octubre 2026 · en curso  ›
$12,169                                             Display 56
Te quedan en octubre · 158 viajes tomados           Body 16
Hasta hoy · ya sin gasolina, desgaste ni coche · aprox. ›   Small 13 → D3b
  ▂          ▇          █          ▂                .spark 28 95 100 21 %
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  raya at $0 · 1 – 4 | 5 – 11 | 12 – 18 | 19 oct
Por semana, ya sin lo del coche                     Small 13
La mejor fue 12 – 18 oct                      ›     → E3d
IMSS del mes                                  ›     free
```

### E4 · IMSS del mes (root lines: Codex; disclaimer next to the PDF: Opus; legal B-038)
```
←   IMSS del mes                       Octubre ▾    month opens a sheet
Lo que te pagó cada app · aprox.                    Small 13
Uber                                    $12,608     Number 32
DiDi                                     $8,432     Number 32
Tu vigencia la confirma el IMSS.                    Body 16
Kompara no representa al IMSS.                      Body 16 ink-2
¿Cómo funciona?                               ›
───────────────────────────────────────────────
Resumen del mes en PDF · Premium              ›
No es asesoría fiscal ni constancia.                Small 13 ink-2
[      Consultar mi vigencia en el IMSS      ]      free primary
```

### F1 · Comparar · Tus apps
```
Tus apps     Tu ciudad                       (JA)   scope tabs are the title
‹  12 – 18 oct  ›
Por km      Por viaje      Por hora                 "Por hora" in ink-3; tap → sheet: falta tu resumen de DiDi
$1.21                                               Display 56
más por km en DiDi                                  Title 20
DiDi                                   $10.34/km
Uber                                    $9.13/km
Neto, según el lector · horarios distintos          Small 13 ink-2
De dónde salen estos datos                    ›
```

### F2 · Comparar · Tu ciudad (context row and contribution: Codex; raya: Opus)
```
Tus apps     Tu ciudad                  ⤴    (JA)
‹  12 – 18 oct  ›
Uber · CDMX · Por viaje                       ▾     one 48dp row → sheet
Por viaje, te pagan más que al                      Title 20
78 %                                                Display 56 (NBSP)
de los conductores de Uber en CDMX.                 Title 20
Tú en Uber                           $149/viaje
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  ink raya = the average
Promedio de Uber en CDMX             $131/viaje
Antes de gastos · según los resúmenes de Uber       Small 13; said once for both
Datos · 1,240 conductores                     ›
Aportar al promedio                           ›     default-off consent
```

### H2b · Suscribirme (order: Codex; banked-days copy kept from the current frame)
```
←   Suscribirme
$79 al mes                                          Display 56 + Body 16
Google Play cobra hoy.                              Title 20
Se renueva cada mes. Cancelas en Google Play        Body 16 ink-2
cuando quieras; sigues hasta el fin del mes pagado.
Tus 21 días gratis no se pierden:                   Body 16
se suman al final, cuando canceles.
[         Suscribirme por $79 al mes         ]
Mejor avísame el 9 de noviembre                     text 48dp
Restaurar compra  ·  Términos  ·  Privacidad        Small 13
```

**Dropped from my Round 11:** fork 1 (renaming "Tu semáforo"). "sin semáforo" is a checker-asserted C5 string, and "semáforo" is the category word drivers already use. G2's title is a copy call for the synthesis, not a founder fork.

---

## 5. Open forks only the founder can settle (max 3)

1. **The brand: the K-raya mark, and green leaving the chrome.**
   - The K square goes. The mark is a K whose arms spring from a horizontal bar (the raya).
   - Brand green survives only on the app icon (paper K-raya on verde) and as verdict verde. Everything else is ink on paper.
   - This is a brand decision, not a layout one.
   - **Recommendation: approve.** It is the only way green keeps one meaning ("te deja"), and it clears the R11 hard fail "a K square as the brand".
2. **Night theme in v1, or light-only at launch.**
   - Both themes, following the system, roughly doubles render and QA for the 66 frames and the host.
   - **Recommendation: ship both in v1.** Drivers open the app parked after dark, the hosts' maps go dark at night, and the chip is already theme-independent. Light-only would make Kompara the one bright screen in a dark car.
3. **Gate the chip on a mounted glance test before Android implementation.**
   - About one week, 12 CDMX drivers (at least 3 colour-vision deficient), day and night, 1 s exposures at about 25°.
   - Compare three chips: la raya con carril, the current chip, and an enlarged cota.
   - **Pass:** at least 95 % state identification for the raya con carril, better than the current chip, with zero gris-as-rojo calls.
   - **If amarillo alone misses:** widen the carril to 20dp (number at 30sp) and retest. Do not return to an all-yellow plate.
   - **Recommendation: fund it.** It is the only evidence that can lift criterion 10 above 8, and it costs less than rebuilding the overlay after launch.
