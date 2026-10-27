#!/usr/bin/env python3
"""Generate part-n.html (the night set) mechanically from the day frames, so `diff` of an N frame against its
day twin shows only the theme class token, the caption, on three frames the status bar, and on N10 one host-mock colour
(HOST_NIGHT). Nothing else may differ.

  N1 = B1 · N2 = B2 · N3 = D1 · N4 = E1 · N5 = E3 · N6 = F1 · N7 = F2 · N8 = C1 · N9 = C2 · N10 = C4 (N8–N10 on the dark host)

The one allowed difference besides the class and the caption (Round 12c): the STATUS BAR (clock and battery), which is
not screen text and not a contract string. The day twins of N4, N6 and N7 carry 9:41 · 100 %; their content is a closed
week (E1) and a weekly comparison (F1, F2), so nothing on the screen is pinned to a time of day, and the night set shows
them at 21:10 · 41 % — the evening Tu día (D1/N3) and Mes (E3/N5) are already seen at, so one status bar runs through the
whole set. N1, N2 and N8 keep the day twin's status bar on purpose: $762 · 6 viajes is the fixture's 14:40 figure (B1),
"Lector apagado · desde las 7:50" is a morning (B2), and the verde offer is the fixture's 14:32 card (C1); the dark theme is
a system or host setting, not the hour, so an afternoon clock on those three is honest and an evening one would contradict
the fixture. Keep STATUS in step with those facts if the twins change.

Round 13: captions re-written for the fixed day twins (B1 header/action, D1 fade + whole last row, E1 car-cost raya, E3 light bars,
F1/F2 Comparar grammar + .ref + ink mark, C1 chip without inline overrides); N2 unchanged.
Round 13c: re-copied from the re-fixed twins (B1 time-led rows = D1's anatomy, D1 list under a paper fade, E1 one --ink-3 kept fill
+ 1dp $0 line + two-word caption column, E3 --ink-3 bars with today in the axis, F1 scope as the hero label, F2 sentence-as-selector
with the 13sp .ref caption); N9 (amarillo) and N10 (gris) added on the dark host — R12-04's night clause, the two chip states most at
risk on a dark map; N2 and N8 captions unchanged.

The one host-mock colour substitution (HOST_NIGHT): C4 draws its unpainted fare slot with the light host's skeleton grey inline;
the night copy takes the dark host's raised fill so the slot stays a quiet placeholder on the dark card. Kompara's own markup is
never substituted — every Kompara colour is a token and resolves to its night value through the .night class.

Residual pass (Round 13 residuals): N4, N5 and N7 re-copied from the fixed E1 (one-line qualifier, no −$1,500), E3 (30dp week bars) and F2
(the 48dp context row, plain sentence); only those three captions changed.

Run after any edit to part-b/c/d/e/f:  python3 make-part-n.py  (then re-assemble index.html)."""
import os, re, sys
H = os.path.dirname(os.path.abspath(__file__))
TWINS = [("N1", "B1", "part-b.html"), ("N2", "B2", "part-b.html"), ("N3", "D1", "part-d.html"), ("N4", "E1", "part-e.html"),
         ("N5", "E3", "part-e.html"), ("N6", "F1", "part-f.html"), ("N7", "F2", "part-f.html"), ("N8", "C1", "part-c.html"),
         ("N9", "C2", "part-c.html"), ("N10", "C4", "part-c.html")]
# frame -> [(day string, night string)]: the one host-mock colour a day frame writes inline. C4's unpainted fare slot is the light
# host's skeleton grey (#e3e5e8); on the dark host card (#2a3139) it would be the brightest object after the chip, so N10 takes the
# dark host's own raised fill (#3a434c, the night control buttons in base-v3.css). Host colours only; nothing of Kompara changes.
HOST_NIGHT = {"N10": [("background:#e3e5e8", "background:#3a434c")]}
# frame -> (clock, battery %) substituted into the status bar; frames absent here keep the day twin's status bar verbatim
STATUS = {"N4": ("21:10", 41), "N6": ("21:10", 41), "N7": ("21:10", 41)}
CAPS = {
 "N1": "<b>N1 · Hoy, night.</b> B1 on the system dark theme, generated from B1: the same markup and strings, only the phone carries the night tokens (paper #121211, ink #F2F1EC, ink-2 #A8A7A1, hairlines #2C2C29). It answers B1's question — is Kompara reading, what have I kept so far today — and proves that nothing in the layout depended on a light surface: the status segment, the hero on the page (13sp label · 56sp number · 16sp count · the 48dp deduction row), the hairline-bounded observation row, the full-bleed group rule, the section header with its action, the hairline offer rows and the three marks invert with the ink, while the verde and rojo fills on the marks stay the only colour on the screen. The account control is the bare 24dp person glyph, as in day. Round 13, inherited from B1: the header is the 16sp 600 ink title and its action, Ver tu día ›, is 16sp 400 ink-2 ending in the rows' 24dp chevron (a 48dp target), 12dp above the first rule. Round 13c, inherited from B1: the offer rows use Tu día's one row anatomy — the mark, the 13sp time in a fixed 40dp column, the outcome — so Ver tu día › opens N3 with the same 14:32 · 12:10 · 11:48 rows laid out the same way, gaining only the net rate at the right; the right edge carries nothing but chevrons. The clock stays 14:40 because $762 · 6 viajes is the fixture's 14:40 figure: the dark theme is a system setting, not the hour.",
 "N2": "<b>N2 · Hoy, reader off, night.</b> B2 on the dark theme, strings identical. An off reader is an ink state, so dark needs no re-tuning: the dashed segment with its since-when sub-line (Lector apagado · desde las 7:50), the 20sp cause (broken before \"de accesibilidad\", no terminal period) and the one body line invert with the ink; under the full-bleed group rule (32dp above and below) yesterday's result leads in ink with its figure in the money face and the ink-2 \"no trips yet\" line follows; the single primary at the thumb becomes paper-on-ink and is still the only thing that looks like a button. No red anywhere to fail on a dark surface. The clock stays 8:05 as in B2 — the reader has been off since 7:50 and the morning is the story; the theme does not move it.",
 "N3": "<b>N3 · Tu día, night.</b> D1 on the dark theme, strings identical. The drill top row — ← and Tu día at the gutter, the stepper ‹ Lunes 19 oct › as a cluster at the right, › dimmed ink-3 because today is the last day — then the root hero. \"En turno · 8 h 10 min\" is B1's 16sp 600 section title over the shift ledger, whose solid and dashed segments invert with the ink; the offer list is named by its first tab, \"Ofertas · 23\", with \"neto por km\" as the 13sp ink-2 column header on the tab row's baseline, so the figures cannot be read as the host's fare per km. The offer rows are N1's anatomy plus the rate: the mark (outline ink-2 and rules ink-3 at their night values, so the lane stays legible; the verde, amarillo and rojo fills unchanged — position against the lane carries the verdict in both themes), the 13sp time in its 40dp column, the outcome at 400, the net rate as the one 600 figure; the gris row is one line with no figure. Round 13c, inherited from D1: a 24dp spacer under the ledger lets the list run on under the nav, and the 24dp .fade above the nav resolves to the night paper (transparent → #121211), so the 11:48 row's mark and text soften into dark paper at their foot — the scroll cue of a list that continues to 23 offers, never a light edge. The four rows drawn are the fixture's, none invented. Seen at 21:10 · 41 %, the evening the day twin is seen at.",
 "N4": "<b>N4 · Semana, night.</b> E1 on the dark theme, strings identical. Answers \"how much did last week leave me, and where did it come from\": the root hero anatomy (label · number · 16sp count · the 48dp qualifier row that leads with its source, Según el lector · ya sin gasolina, desgaste ni coche · aprox., one line with single spaces around every ·), the plain discrepancy row, then the chart. The raya is the car cost: one 2dp ink line (#F2F1EC) crosses the seven day bars at the daily share of lo del coche ($214.29, 15.5 % of the viernes scale) and runs on into the 84dp caption column, where it sits between its two words, Lo del coche above and $214 por día below. Round 13c, inherited from E1 — the night half of R12-01 by token, not by a night override: every bar splits on the line from one shared fill, --rule (#2C2C29) below for the car's share and --ink-3 above for what you kept (#74736E, 3.9:1 on the night paper, a full step under ink-2), so the 56sp $4,980 is the brightest object on the screen and the bars read as its breakdown, not as a second light mass; the hero is exactly the sum above the line. The $0 line steps down to 1dp ink-3 and stops at the plot edge, so the car line is the chart's one 2dp light line — no double baseline, no track. No bar is highlighted on a closed week; the best day and the standing are two plain rows. Residual pass, inherited from E1: the qualifier drops (−$1,500), so the root says the car cost once, as $214 por día on its line, and the weekly −$1,500 lives in E1b's ledger. The status bar is the one generated difference from E1: a closed week is seen at any hour, so the night set shows it at 21:10 · 41 %, like Tu día and Mes.",
 "N5": "<b>N5 · Mes, night.</b> E3 on the dark theme, strings identical. Answers \"how much has October left me so far\" with Semana's hero anatomy (label · number · count · the month's deduction row, Hasta hoy · ya sin gasolina, desgaste ni coche · aprox. → Cómo se calculó); the stepper reads Octubre · en curso with the forward arrow dimmed. Round 13c, inherited from E3 — one fill grammar across Ganancias: the weeks are already after the car cost, so all four bars take N4's kept fill, --ink-3 (#74736E, 3.9:1 on #121211, above the 3:1 floor for a meaningful graphic), standing on the 2dp $0 raya, which is this chart's one 2dp line because there is no car line. Money kept after the car cost is one encoding on both tabs — the grey above N4's car line is the grey of every N5 bar — and the best week, 12 – 18, which the row under the chart names, is the tallest bar and fully legible, not the faintest object on the screen. Today is marked in the axis (19 · hoy, ink 600), never in a fill, so the open period is neither highlighted nor brighter than the closed weeks, and nothing in the chart outshines the 56sp $12,169. Residual pass, inherited from E3: the week bars are drawn at Semana's 30dp bar width, centred in their slots over the day numbers, so the month's chart no longer outweighs the hero; heights and the $0 raya are unchanged. Same clock and battery as Tu día (21:10 · 41 %), as in the day twin.",
 "N6": "<b>N6 · Comparar › Tus apps, night.</b> F1 on the dark theme, strings identical. Which of my apps paid me more per km last week? The Ganancias grammar: \"Comparar\" in the top bar, \"Tus apps | Tu ciudad\" as the 16sp text-tab row (24 × 2dp ink underline, each tab reserving its 600 width), the shared stepper 12 – 18 oct · semana pasada. Round 13c, inherited from F1: scope and metric are the hero's 16sp ink-2 label, \"DiDi y Uber · Por km\", a 48dp target whose 24dp caret opens the choice sheet (Por hora listed ink-3); the difference is the one display number with its sentence; then the chip's grammar at page scale — DiDi above the line with the ink mark, top zone filled, light on dark paper, and the main app hung on the 2dp line as one 13sp caption, \"Uber · tu app principal\" · $9.13/km (figure 13/600 ink-2), so $10.34/km is the only 16/600 ink figure and nothing reads as a second table row. At night the line is the one light rule on the page, so $1.21 más is read as a height. The basis line \"Neto, según el lector · a distintas horas\" and the \"Datos ›\" row (the trip counts live in its sheet) invert cleanly. No share glyph — only a standing has a share card — and no verdict colour, because a comparison is not a verdict: ink on dark paper from top to bottom. Status bar 21:10 · 41 %, the one generated difference from F1 (a weekly comparison is seen at any hour).",
 "N7": "<b>N7 · Comparar › Tu ciudad, night.</b> F2 on the dark theme, strings identical. Where do I stand among Uber drivers in CDMX, per trip, for 12 – 18 oct? Top bar and tab row as on N6, with Tu ciudad on and the share glyph, because a standing exists. Residual pass, inherited from F2: one selector grammar on Tu ciudad — the scope is the 48dp context row \"Uber · CDMX · Por viaje\" with its 24dp ink-2 caret (the choice sheet), as on F4 and F5, and the standing sentence is plain, with no dotted words: \"Por viaje, te pagan más que al\" (20sp 400 ink-2), then 78 % on its own line at the 20dp gutter like every hero in the set, then \"de los conductores de Uber en CDMX / que compartieron su resumen\". The Tú row leads with the ink mark, top zone filled; the city average is hung on the 2dp line as one 13sp caption, \"Promedio\" · $131/viaje (13/600 ink-2), so $149/viaje is the only full-weight figure; the basis line is \"Antes de gastos\", the source being in the sentence. Then \"Datos · 1,240 conductores ›\" and \"Aportar al promedio ›\". No colour, because a standing is not a verdict. Status bar 21:10 · 41 %, the one generated difference from F2.",
 "N8": "<b>N8 · Verde on the dark host, night.</b> C1 with the host app in its own dark theme, strings identical: the same unbranded map — one SVG whose night fills are selected by the .night class, so geometry cannot drift from C1 — with its irregular blocks cut by the road hierarchy (the cased avenue, the cased diagonal, secondaries, streets), parks, footprints, the two grey street labels (Av. Insurgentes Sur along the avenue, Plaza Carso under the destination), the route, the pickup dot and the destination square; the driver chevron sits under the offer card. A dark offer card with a light countdown ring, a light \"En línea\" capsule between dark-grey menu and search controls (24dp icons) that still read as buttons, light Aceptar. The chip is theme-independent: the same #0E0F10 plate, the same verde zone T with the K at 60 %, \"$9.49/km\" as one text run on one baseline, the empty carril and \"neto aprox. · $285/h\" on chip ink; the 1dp 28 % white keyline and the shadow keep the plate separable from the dark map. Round 13, inherited from C1: the frame carries no inline chip geometry — the 2dp exposed corners, the 32/36 number step with the unit on the number's baseline and the K all come from the one .chip component in base-v3.css, so the night host shows the same object as C1, A1, B3 and G2. Same dock (right bezel, y = 144, 16dp under the host's control row), clear of the route and the pins. The clock stays 14:32 because this is the fixture's 14:32 offer; the host's dark theme is the host's own setting, not the hour — the night set changes nothing about the offer moment.",
 "N9": "<b>N9 · Amarillo on the dark host, night.</b> C2 with the host app in its own dark theme, strings identical ($7.16/km · neto aprox. · $191/h), so the night set proves the state most at risk on a dark map, not only verde. The same SVG map with its .night fills, the same dark card ($120.00, light countdown ring and Aceptar) and the same dock as N8 (right bezel, y = 144, 16dp under the control row). The chip is theme-independent: only the 16dp lane fills amarillo #F2B705, between zone T and zone B, and both text zones stay on the #0E0F10 plate, so nothing is read on yellow. On a dark host the stripe cannot pass for a host banner or a glare strip: it is 16dp tall and 200dp wide — the plate's width, never the screen's — bounded above and below by the plate's own ink zones and enclosed by the 1dp 28 % white keyline and the shadow, which hold the plate's edge where the plate itself is only 1.4:1 against the dark map. The colour-blind cue is unchanged: a bright stripe through a dark plate, against verde's lit top (N8). The clock stays 14:50, the fixture's amarillo offer; the host's dark theme is its own setting, not the hour.",
 "N10": "<b>N10 · Gris on the dark host, night.</b> C4 with the host in its dark theme, strings identical (Sin lectura · falta la tarifa): the other state most at risk at night, because gris has no fill and its lane is two dashed rules. Those rules are drawn on the chip's own #0E0F10 plate, never on the map — 2dp bands, 6 on / 4 off, at 50 % of the chip ink (#828280, 5.0:1 on the plate) — so the broken lane reads the same on a dark or a light host, and the 1dp 28 % white keyline and the shadow hold the plate's edge against the dark map. Zone T carries the missing input at 20sp, zone B the reason in the present tense; no colour, no peso figure, never red. One host-side change, the only generated difference besides the class and this caption: the card's unpainted fare slot takes the dark host's raised fill (#3a434c on the #2a3139 card) instead of the light host's #e3e5e8 skeleton, which on a dark card would be the brightest object after the chip; its geometry is unchanged (144 × 26 on the fare baseline). Same dock as N8 and N9. The clock stays 11:48, the fixture's sin-lectura offer.",
}
tok = re.compile(r"<div\b|</div>")
def frames(src):
    out = {}; pos = 0
    while True:
        i = src.find('<div class="frame">', pos)
        if i < 0: break
        depth = 0; j = i
        for m in tok.finditer(src, i):
            depth += 1 if m.group(0) == "<div" else -1
            if depth == 0: j = m.end(); break
        frag = src[i:j]; cap = re.search(r'<div class="cap"><b>([A-Z]\d[a-z]?\d?)\b', frag)
        if cap: out[cap.group(1)] = frag
        pos = j
    return out
def status_bar(frag, clock, pct):
    """Rewrite the clock and the battery (fill width + label) inside the frame's .statusbar; nothing outside it is touched."""
    i = frag.index('<div class="statusbar">'); j = frag.index('</div>', frag.index('</span></div>', i)) + len('</div>')
    sb = frag[i:j]
    sb, n1 = re.subn(r'<span>\d{1,2}:\d{2}</span>', f'<span>{clock}</span>', sb, count=1)
    sb, n2 = re.subn(r'(<rect x="2" y="2" width=")[\d.]+(" height="6")', rf'\g<1>{pct/10:.1f}\2', sb, count=1)
    sb, n3 = re.subn(r'\d+%</span>', f'{pct}%</span>', sb, count=1)
    assert n1 == n2 == n3 == 1, "status bar not recognised"
    return frag[:i] + sb + frag[j:]
parts = {}
body = ['<!-- ===================================== N. NIGHT ===================================== -->',
        '<!-- GENERATED by make-part-n.py from the day frames (B1 B2 D1 E1 E3 F1 F2 C1 C2 C4): do not edit by hand — edit the day frame and regenerate.',
        '     Allowed differences from the day twin: the theme class, the caption, on N4/N6/N7 the status bar (21:10 · 41 %, see the generator docstring),',
        '     and on N10 the host card\'s unpainted-fare skeleton, which takes the dark host\'s fill (HOST_NIGHT). -->',
        '<h2 class="flow">N · Night — the same design, following the system theme</h2>', '<div class="grid">', '']
for nid, day, part in TWINS:
    if part not in parts: parts[part] = frames(open(os.path.join(H, part), encoding="utf-8").read())
    frag = parts[part][day]
    if '<div class="phone host">' in frag: frag = frag.replace('<div class="phone host">', '<div class="phone host night">', 1)
    else:
        assert '<div class="phone">' in frag, day
        frag = frag.replace('<div class="phone">', '<div class="phone night">', 1)
    if nid in STATUS: frag = status_bar(frag, *STATUS[nid])
    for a, b in HOST_NIGHT.get(nid, []):
        assert frag.count(a) == 1, (nid, a); frag = frag.replace(a, b)
    ci = frag.rfind('<div class="cap">'); assert ci > 0, day
    frag = frag[:ci] + '<div class="cap">' + CAPS[nid] + '</div></div>'
    body += [f'<!-- {nid} = {day} on the night theme -->', frag, '']
body += ['</div>']
open(os.path.join(H, "part-n.html"), "w", encoding="utf-8").write("\n".join(body) + "\n")
print("part-n.html written from", ", ".join(f"{d}→{n}" for n, d, _ in TWINS), "· status bar substituted on", ", ".join(STATUS))
