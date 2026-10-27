# Round 11 synthesis — Kompara v3: paper, ink and the raya

**Date:** 2026-10-26 · **Lead:** Claude Fable 5.1 · **Inputs:** `111`/`112` (independent Round 11 reports) and `113`/`114` (cross-critiques) by Codex Astra (GPT-6 Astra, ultra) and Claude Opus 5.5; the renders in the session scratchpad (`cota.png`, `raya.png`, `tri.png`, `v3-exemplars.png`, `b1-variants.png`, `host-test.png`). · **Status:** decisions locked for the rebuild; founder forks listed in §7.

Both reviewers scored the Round 10 design **16/40 FAIL** on the craft rubric (`03-rubric-r11-craft.md`) with all four hard fails present, and both independently proposed the same signature: the driver's own floor drawn as a line, with the verdict colour positioned against it. The cross-critique converged on one chip and one visual system. This document is the build contract for v3.

## 1. The signature: "la raya con carril"

*Your two limits are a lane across the plate; the colour sits above it, inside it, or below it.* The chip, every offer row (the mark), the day line, the $0 line under the bars and the city average in Comparar are the same object at different scales. Up means more money. No glyph, no verdict word, no brand strip, no traffic light, no gauge, no coloured rounded card.

### 1.1 Chip anatomy (plate-local dp, docked to the right bezel; mirrored corners only when docked left)

| Element | Spec |
|---|---|
| Plate | 200 × 88, fill `--chip-ink #0E0F10`, corners 0 on the bezel side / 6 inside, 1dp keyline `rgba(255,255,255,.28)`, shadow `0 2 8 rgba(0,0,0,.25)`. Theme-independent. |
| Zone T | y 0–36. The deciding rate: Barlow Semi Condensed 600, 32sp, unit 16sp Medium in the same text run (`$9.49/km`), x = 16. |
| Carril (lane) | y 36–52 (16dp). Upper rule = tu mínimo ($8.00/km), lower rule = tu rojo ($6.00/km); rules 2dp `rgba(245,244,239,.8)`. Categorical, never proportional. |
| Zone B | y 52–88. `neto aprox. · $285/h`, Barlow Semi Condensed 500, 16sp, x = 16. |
| K | 12dp letter K, 16dp from the plate's right edge, in zone T, 60 % of the on-colour. Never in the bezel corner. |
| Verde | zone T filled `#187A36`, white text. |
| Amarillo | the lane filled `#F2B705` (no rules); text stays on ink. |
| Rojo | zone B filled `#D32F2F`, white text; negative amounts identical (`−$1.05/km` / `neto aprox. · −$26/h`), still 200dp. The 232dp step is retired. |
| Gris | no colour; both rules dashed 6/4 at 50 %. Zone T carries the missing input in Barlow 600 20sp (`Sin lectura`, `Falta tiempo`, `Falta distancia`, `Sin confirmar`, `Leyendo oferta`); zone B the reason in Barlow 400 16sp (`faltó la tarifa`, `sin semáforo`, `sin cálculo`). Never a peso figure on gris. |
| Lector detenido (C6) | a different object: 200 × 48 ink tab, no lane, no colour, no K; `Lector detenido` 16sp 600 / `Revisa Kompara al estacionarte` 13sp; 5 s, once per host foreground, touches pass through. |

Placement: docked to the end bezel, vertical position from the host's validated safe band (below its control row + 16dp, above its offer card − 16dp); reference mock position y = 144 under a 84–120 control row. Suppressed when no band fits; the offer is still logged. Growth: width fixed; height grows with font scale (zones scale, lane stays 16); never truncate a figure; drop the K before wrapping zone B. Motion: appear ≤ 150 ms after parse, 16dp slide from the bezel + fade over 120 ms; leave 500 ms after the card leaves; state change = atomic swap, no crossfade; no pulse, count-up, haptic or sound. Gestures: drag only; touch region = plate minus a 24dp bezel strip; tap and long-press inert. Colour-blind cue: position (four lightness patterns: top lit · bright stripe · bottom lit · uniformly dark with dashed rules).

### 1.2 The mark (the chip in miniature, the only leading glyph in rows)

16 × 24dp, radius 3, 1.5dp `--ink-2` outline, two 1dp `--ink-3` rules at y 8 and 14; verde fills y 0–8, amarillo y 9–14, rojo y 15–24; gris = dashed outline, no fill. In Comparar it is drawn in ink (a comparison is not a verdict). Written `[V] [A] [R] [G]` in wireframes. CSS: `.mark.v/.a/.r/.g` with an inner `<i>`.

### 1.3 Carry-through

- **Hoy:** reader status = a 24 × 2dp ink segment + words (solid funcionando · dashed apagado/en pausa · broken "━ ━" preparado); offer rows led by the mark. Never green.
- **Tu día:** three shift rows led by a solid (en turno) or dashed (pausa) segment; offer rows led by the mark. No invented offers (the fixture has four).
- **Ganancias:** the bars stand on a 2dp ink raya at $0 (`.spark` + `.raya`).
- **Comparar › Tu ciudad:** the city average is the raya; "Tú en Uber $149/viaje" sits above it, in ink.
- **Brand:** the K square is gone. In product the K is an unboxed Barlow Semi Condensed 700 letter in verde (chip, share card, notification icon). The K-raya launcher mark (a K whose arms spring from a horizontal bar) is a founder fork (§7).

## 2. Visual system v3 (tokens in `design-system/ui_kits/android-app-v2/base-v3.css`)

| Token | Day | Night | Notes |
|---|---|---|---|
| `--paper` | `#F5F4EF` | `#121211` | the one surface |
| `--paper-2` | `#EBE9E2` | `#262624` | fields, pressed, skeletons — never a card |
| `--ink` / `--ink-2` / `--ink-3` | `#111110` / `#5C5B57` / `#8A8984` | `#F2F1EC` / `#A8A7A1` / `#74736E` | text and the primary button; ink-3 only ≥ 18sp or non-text |
| `--rule` | `#DCDAD2` | `#2C2C29` | 1dp hairlines |
| verde / amarillo / rojo | `#187A36` / `#F2B705` / `#D32F2F` with on-colours `#FFF` / `#1A1300` / `#FFF` | same | only on verdicts (chip, mark) |
| `--chip-ink` / on | `#0E0F10` / `#EDEDEA` | same | the chip and C6 |
| `--k` | verde | same | the letter only |

**Deleted:** every slate/emerald token, info-blue, amber, the second red, the brand strip, cards, pills, segmented controls, icon tiles, circled glyphs, Inter, weights 800/900, the 18-size ladder.

**Type:** money Barlow Semi Condensed 600 (`tnum lnum`); UI Barlow 400/600. Five steps: 56/56 display (one per screen) · 32/36 number (chip, E4 per-app) · 20/26 title · 16/22 body (buttons 600) · 13/18 small. Never wrap into a right gutter; keep `78 %`, `−$1.05/km`, `8 h 10 min`, `12 – 18 oct` unbroken (non-breaking spaces).

**Surfaces:** one page; hero number on the surface top-left (label 13 above, qualifier row 48dp below that opens Cómo se calculó); groups separated by 32dp whitespace and full-bleed 1dp rules; rows ≥ 56dp with one label, one value or chevron, at most one figure; elevation only on sheets (top radius 16), the chip and snackbars; one ink primary 56dp radius 10 at the thumb; secondaries are ink text buttons 48dp; selectors are text tabs (active ink 600 with a 24 × 2dp underline, inactive ink-2; unavailable ink-3) and choice sheets; steppers 56sp between two 56dp rings; no pills ("Ejemplo" is a 13sp label; status is a segment + words); icons Material-style 24dp `currentColor`, never in a container; gutter 20dp; rhythm 4/8/12/20/32.

**Theme:** follow the system; mockups lead with day; a night set (N1–N8: B1, B2, D1, E1, E3, F1, F2 and C1 on the dark host) proves the structure. The chip is theme-independent.

**Host mock:** unbranded. Light map (tinted blocks, white roads, a route, pickup dot, destination square, driver chevron), a top control row at y 84–120 (white ≡ · black "En línea" capsule · white ⌕), a mid-lower offer card ("Viaje" capsule, fare 36sp, two rows with street names, a countdown ring, a full-width black Aceptar). Night variant for N8. Fares per state from the fixture ($148.00 · $120.00 · $62.00 · $22.00 · "— —"). No logos, no host earnings figure.

## 3. Copy and simplicity rules (apply to every screen)

1. "Te quedan" + **one** qualifier row, said once, as the 48dp row under the hero that opens Cómo se calculó. The deduction basis is ≤ 6 words on the root: day `Ya sin gasolina ni desgaste · aprox.` · closed week `Ya sin gasolina, desgaste ni coche (−$1,500) · aprox.` · open week `Ya sin gasolina, desgaste ni coche por día · aprox.` · month `Hasta hoy · ya sin gasolina, desgaste ni coche · aprox.`. The verbatim §4 scope sentences are retired (founder fork §7, provisional yes); the source stays named where it matters (E1's row `Tu resumen de Uber dice un poco más ›`; F2's `Antes de gastos · según los resúmenes de Uber`).
2. The mark replaces every circled glyph, dot and colour word; rows say the outcome only (`La tomaste`, `Se fue sin tomar`, `Sin lectura · faltó la tarifa`).
3. Share is a top-bar icon on period screens; import lives in the discrepancy sheet and the period sheet, not as a second footer button.
4. Errors and reader problems are ink states (words + dashed segment), never red or amber; B2's hero is the cause as a 20sp title.
5. No paragraphs on roots; A2 (disclosure, four always-expanded blocks with bold lead words) and H2b (billing facts) are the explicit exceptions, still shortened as in `114` §4. Both go back through legal B-038.
6. Target: ≤ 45 words per root screen (R10 median was 83). Captions stay in English and may be long; captions are not screen text.
7. Spanish es-MX, `tú`, sentence case, one word per concept (§4 vocabulary of the spec still applies: Te quedan · neto · Te pagó la app · tu mínimo · lector · preparado/funcionando/apagado/en pausa · La tomaste · Se fue sin tomar · Semana confirmada con tu resumen · en turno · aprox.).

## 4. Screen-by-screen source of truth for the rebuild

- **Merged wireframes:** `114-round11b-opus-5-5.md` §4 (A1, A2, A5, B1, B2, B3, C1–C6, D1, D2, E1, E3, E4, F1, F2, H2b) with these corrections from `113-round11b-codex-astra.md`: keep `6 viajes tomados` and `Tu mejor hora hoy: 9 a 10 h` on B1; no rates on B1's offer rows; keep the three shift rows on D1 (`5 viajes` / `4 viajes`, `tú lo pausaste`); F1's limitation line `Neto, según el lector · horarios distintos`; H2b's banked-days line `Tus 21 días gratis quedan guardados. Se usan al terminar tu acceso pagado y, si aplica, el periodo de gracia.`; F2 `de los conductores de Uber en CDMX` (the cohort is labelled in the data row `Datos · 1,240 conductores ›`).
- **Every other frame** (A3, A4, A6, A7, B4, B5, B6, D3, D3b, E1b, E2, E3b, E3c, E3d, E5, E6, E7, E7b, E8, F3, F4, F5, G1–G9, H1, H1b, H2, H2c, H2d, H3, H4, H5–H5e, H6) keeps its R10 content and states (`android-app-v2-r10/` is the frozen reference) and is re-drawn on the v3 system with the rules in §2–§3: hero on the surface, rows not cards, text tabs, the mark, ink notices, one primary, ≤ 45 words. Specific notes: G2 is titled `Tu semáforo` (fork §7) and previews the real chip docked to the screen edge; G1 is text rows without icon tiles; H1 is one benefit list and one button; H3 is a paper share card with the K and `78 %` unbroken; H6 shows the notification list with the K as the small icon and no developer note inside the phone; E2's locked breakdown is a flat `--paper-2` placeholder, never a blur; E3c's "Escenario alterno" label stays outside the phone; E5 stays marked "Ejemplo · regla pendiente" and gated.
- **Night set:** N1 B1 · N2 B2 · N3 D1 · N4 E1 · N5 E3 · N6 F1 · N7 F2 · N8 C1 on the dark host, as `.phone.night` / `.phone.host.night` copies with identical strings.

## 5. Rebuild contract (what the checker and the reviewers hold us to)

- Frame ids, captions (`<div class="cap"><b>ID · Title.</b> …`) and the `<div class="frame">` wrapper are unchanged; `check-fixture.py` asserts the strings listed in the session's `frame-contract.md` inside each frame's screen text, the `.spark` bar heights, the E2 caption date and the stale-string bans. Run `python3 check-fixture.py` after every edit (set `KOMPARA_INDEX` to a private assembly while sections are rebuilt in parallel).
- `index.html` = `cat _head.html part-a.html part-b.html part-c.html part-d.html part-e.html part-f.html part-n.html`. Parts use only `base-v3.css` classes (plus small inline styles for layout); no new colours.
- Render: `python3 render-frames.py <out-dir> [ids]` (headless Chrome) and `KOMPARA_OUT=<dir> python3 sheets.py` for contact sheets. Review the PNGs, not the HTML.
- Scoring (Round 12+): both rubrics; pass = every criterion ≥ 8 on `02` (≥ 68/80) and on `03` (≥ 34/40), by both reviewers, on the same version.

## 6. Scores to beat

Round 10 structural: Opus 69 · Codex 68. Round 11 craft: Opus 16/40 · Codex 16/40 (4 hard fails each). Forecast for this design by its authors: Opus 34/40 (criteria 10 and 11 capped at 8 pending the mounted test), Codex 35/40 and 71/80.

## 7. Founder forks (provisional choice in bold; nothing here blocks the rebuild)

1. **Retire the verbatim scope sentences** in favour of the ≤ 6-word deduction row that opens Cómo se calculó (both reviewers: yes). **Yes.**
2. **Rename "Tu semáforo" → "Tu mínimo"** (Codex yes; Opus withdrew: "sin semáforo" is a checker string and the category word). **Keep "Tu semáforo" as the screen name**, keep "tu mínimo" as the floor's noun, keep "Verde desde" and "Rojo debajo de" rows.
3. **Brand:** the K square goes (both). The K-raya launcher mark and "green only on the icon and as verde" (Opus) vs. a restrained brand green in the chrome (Codex). **Ink-only chrome; K in verde; launcher mark TBD by the founder.**
4. **Night theme in v1** (both: yes). **Designed and rendered now; shipping both is the founder's scope call.**
5. **Mounted glance test before Android implementation** (both: fund it; ~1 week, 12 CDMX drivers incl. ≥ 3 colour-vision deficient; pass ≥ 95 % state identification, zero gris-as-rojo; if amarillo alone misses, widen the lane to 20dp, never back to an all-yellow plate). **Recommended; outside design scope.**
