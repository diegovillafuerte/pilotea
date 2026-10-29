#!/usr/bin/env python3
"""Clickable prototype of the Kompara v3 kit, for the nine-task driver usability test (design only).

Regenerate after any kit change (index.html is assembled from _head.html + part-*.html first, see README):
    python3 design-system/ui_kits/android-app-v2/make-prototype.py           # writes prototype.html
    python3 design-system/ui_kits/android-app-v2/make-prototype.py --check   # + headless-Chrome link check (exit 1 if any spec is unmatched)

Run a session: serve the repo root (python3 -m http.server 8765) and open, on the same computer,
    http://localhost:8765/design-system/ui_kits/android-app-v2/prototype.html                 (the driver's window)
    http://localhost:8765/design-system/ui_kits/android-app-v2/prototype.html?facilitador=1   (the facilitator's, out of sight)
Participant mode (the default) shows the phone and the host controls only: no answers, no frame ids or labels, no first tap.
The task controls (task number, Iniciar, Hecho, Atascado, Exportar, participant code) sit behind a small handle in the bar
above the phone, which is fitted to the window so nothing is scrolled to and nothing covers it; on a laptop N starts the
next task, H is Hecho and A is Atascado (in either window). The facilitator window shows the tasks with their answers and the
live log; while the driver's window is open it mirrors that window's screen (read-only) and its buttons and keys act on it,
through a BroadcastChannel with a localStorage-event fallback. Alone, the facilitator window records by itself as before.
Outside a task the driver's window waits on the next task's start screen (A1 before task 8; after Hecho or Atascado the next
one's), in that task's moment and world; nothing is logged there. On a phone (participant, 500 CSS px wide or less) the bezel
goes and the 393-wide screens are scaled to the viewport under the bar (about 1:1, no page scroll). Each screen change in the
driver's window adds a browser-history entry that carries the app's own history, so Android's back gesture steps back inside
the prototype (logged as "[teléfono] atrás"; under a task's first screen it stays put, over a floor entry), and a reload
restores the screen over its own opener (without its entry, the task's history is rebuilt from its screen log, else its start
screen); html, body, .content and .sheet set overscroll-behavior: none (no pull-to-refresh, no scroll chaining).
URL options: ?facilitador=1 · ?screen=B1 (start screen; in the driver's window only outside a task) · ?debug=1 (outline every
hotspot, list unmatched link specs) · ?fit=1 (facilitator: scale the phone to the window) · ?fit=0 (participant: keep the phone
1:1 and let the page scroll).

LINKS is the tap map: per source screen, (matcher, target[, options]).
  matcher  "text"      -> the smallest element whose trimmed text contains it (inside the top sheet/dialog when the frame has one),
                          climbed to the nearest tappable ancestor (TAPPABLE) so the whole row is the target.
           "css:sel"   -> elements matching the selector (icon-only targets), climbed the same way.
  target   a screen id, or an action: "@back" (history back) · "@toast:<text>" (a row with no detail; logged) ·
           "@lim:meta|min:+|-" (a ring: the next mark of the $0.50 grid) · "@entry:meta|min" (the field focused in place, G2d) ·
           "@save" / "@reset" / "@leave" (Guardar / Volver a lo inicial, which puts the fixture's pair in the editor only, nothing is
           saved until Guardar / the editor's ←, which asks first when a change is unsaved, G2e) · "@ret:<id>" (a stand-in's return:
           back to the screen that opened it, drawn as <id> in its place; A3s → A3n, A3s → B3, E6's weeks → E8 / E1; a world's
           "retAt" draws it in an earlier screen's place instead: task 7's A3s → B2r lands in B2's place, A3's "Ahora no" → B2 in
           B2's own) · "@backnote:<text>" (history back, then <text> as the note on the screen it lands on; logged).
  options  {"all": True} wire every match, not only the first · {"in": "sel"} search inside that element only.
GLOBALS, TABS and HOST_BAR are applied to every screen; an element keeps the first link it gets, so LINKS wins.
WORLDS remaps navigation while a session is in another moment: task 7's morning (20 oct, 8:01) is entered at C13, H6b, B2 or B2r,
and there Hoy is B2, Ajustes G1b, Semáforo G4h, and the 19-oct screens (E8, E3, F1) are the neutral note; A3's "Abrir ajustes" opens
A3s, the same stand-in as on the first day (Round 23: real drivers still end the task on A3), whose "Ya lo activé" lands on B2r (Hoy
with the permission back, drawn in B2's place, so back from it leaves Kompara) and "Regresar sin activarlo" on A3n (drawn in A3's
place, its back to B2); A3's and A3n's "Ahora no" return to Hoy (B2), not to the note (a world's "links": per source screen, a LINKS
matcher → the target it takes in that world). Landing on B2r sets the attempt's granted flag (cleared when a task starts and while
the driver's window waits; kept over a reload, in the attempt's log and the history entry): from then on the world maps B2 → B2r
("granted"), so every route into B2 opens B2r instead (C13's "Abrir Kompara", H6b's "Se apagó el semáforo", a back onto it) and the
off state is never shown again in that attempt. "Que no se vuelva a apagar" opens the battery step in its Hoy's own status words, A4b
from B2 (the permission still missing) and A4r from B2r (the permission back): "Abrir ajustes" goes back to that Hoy with the note
"En tu teléfono, aquí se abre Batería en los ajustes de Android." (@backnote), "Ahora no" and ← just go back; B2r's door, "Abrir
Uber para probar" and ⚙ give the note.
Task 8's first day (A1, 12:30) has Hoy B3 and Semáforo G4e; there A3's and A3n's "Ahora no" open B3n (Hoy off, the permission
missing) as the task's root (retAt: A1 and the steps above it leave), B3n's "Activar el semáforo" goes back to A3, a later "Ya lo
activé" lands B3 in B3n's place, and B3n's ⚙ gives the note (no first-day Ajustes in the off state is drawn; Round 24c).
A task start sets the world from its start screen.

LIVE is the runtime layer over the static frames:
  one moment per task  starting a task sets every screen's status bar (clock and battery) to the start screen's (B1 14:40,
                       C2 14:50, C13 8:01, A1 12:30; so task 7's B2 and B2r read 8:01, not the kit's 8:05 and 8:07, Opus 322 item 7); the session's first task and the limits task (6) reset the limits to the
                       fixture's $8.00/$6.00 (so task 6 never starts done); otherwise a save lasts the rest of the session; the sheets in
                       LIVE["backdrop"] (the offer details, Cómo se calculó · hoy, Lo del coche, Elegir semana) sit over the screen that
                       opened them, so a 14:40 row
                       never shows the 21:10 day behind its sheet, and outside a task they take that screen's status bar too
                       (B1's 14:40 · 82 %, not the sheet's own 21:10 · 41 %).
  offer details        their one comparison keeps the limits in force when the offer arrived (the fixture's pair); once the limit that
                       line names differs from the fixture's it adds " a esa hora" (D2's caption; Round 24d: D2 and D2e name tu meta, so
                       a mínimo save leaves them as drawn, while D2b, D2c and D2d, which name tu mínimo, add it). Their day row (LIVE["dayRow"], Round 23; Round 24's words) takes the opener's
                       hero figure and opens what its basis line opens ("Llevas hoy, con el coche: $548" → D3a over the 14:40 screens,
                       $1,026 → D3 over D1, B5's $434 → the note). A detail opens on the lowest line of LIVE["offerTops"] for its opener
                       at or above its drawn top where it fits, its three rows (the fare, gasolina y desgaste and the day) sharing the
                       slack evenly as the padding above and below each, so the 24dp inset holds (Round 24e: one gap above the actions took
                       it all before, Opus 322 item 6): over Hoy (B1, B5, B7, B9) D2 opens at 208, between the hero's label and its figure,
                       not at 224 through the figure; a line that wraps after " a esa hora" (+22dp) first narrows that padding (down to
                       LIVE["offerPad"], 14.5dp), then opens the sheet a line higher.
  live limits          G2b shows the saved pair; a ring or a typed figure moves to G2 (tu meta) or G2c (tu mínimo) and recolours the
                       preview chip (▲ if its $9.49 >= tu meta, ◆ between, ▼ below tu mínimo). The rings step on the $0.50 grid, and from
                       a value off it go to the next mark in the direction pressed ($6.30 → + → $6.50; − → $6.00); the prototype draws
                       Por km only ("Por hora" gives the neutral note; per hour the step would be $10). La regla never draws an impossible
                       pair: while tu mínimo is at or over tu meta (or a typed figure is empty) it holds the last valid one. "Volver a lo
                       inicial" keeps its slot under Guardar in every state (so Guardar never moves): live, as G2 draws it, whenever the
                       pair the editor shows differs from $8.00/$6.00 (G2b too, once a save moved it, where a tap moves the editor to G2 /
                       G2c), inert as G2b draws it at the initial pair; it puts $8.00/$6.00 in the editor and saves nothing. Guardar
                       refuses a mínimo at or over tu meta under the field (G2d's error, "Tu mínimo debe ser menor que tu meta" / "Tu
                       meta debe ser mayor que tu mínimo", led by G4b's ink ring-! glyph, never red); else it keeps the
                       pair in localStorage, closes the editor BACK to the screen that opened it (history) and shows G4i's snackbar for
                       4 s ("Guardado: tu mínimo ahora es $6.50/km", "Guardado: tu meta ahora es …", both: "Guardado: tu meta … · tu
                       mínimo …", after Volver a lo inicial: "Guardado: volviste a $8.00/km y $6.00/km"), 8dp over the opener's footer
                       primary when it has one (B3's "Abrir Uber para probar"; Semáforo's lone "Pausar…" counts, as G4i draws it), else
                       16dp over the bottom (Ajustes), risen to the first band 8dp clear of every row and button when that spot would
                       cover one (an offer detail's sheet: over its three rows); the opener already shows the pair on every "tu meta · tu
                       mínimo" line (Hoy's door, Semáforo, Ajustes, B3's first-run row) and B3's example chip repaints from it as G2b's preview does (its two
                       labels and its band; the limit lines start 12dp right of the wider label, as the kit draws "$6" and "$6.00").
                       Hoy's door and B3's chip keep the short form while both limits are whole ("tu meta $8 · tu mínimo $6") and give
                       both two decimals once either has cents ("tu meta $8.00 · tu mínimo $6.50"). A back with an unsaved change (the editor's
                       ←, the bar's "← Atrás", the phone's back) draws G2e's dialog over the editor: "No guardar" discards and goes back,
                       "Guardar" saves and goes back, the scrim keeps the editor; with nothing changed, back just goes back.
  typing in place      G2d is not a screen here: a tap on a figure's field focuses it on the current editor as G2d draws it (from
                       G2d's own markup): the 2dp ink outline, the figure alone selected (no caret) so the first digit, "." or delete replaces it,
                       then a caret after the typed figure; the helper under the field ("Menor que tu meta ($8.00/km)") in place of
                       "Toca la cifra para escribirla."; G2d's keypad under the screen, which shrinks above it (Guardar stays visible,
                       "Volver a lo inicial" waits) and scrolls so the focused card ends on the footer's edge. Two digits before the
                       point and two after ($99.99 at most); la regla redraws as the driver types, holding the last valid pair while the
                       figure is empty or crosses the other limit, and nothing is judged until Guardar (empty: Guardar is inert). The
                       rings stay live and step from the typed value. The system back closes the keypad
                       first, keeping the typed value unsaved; a second back asks (G2e).
  the shade            C13's status bar opens H6b by a tap or by a drag down that starts on it (pointer events, touch included).
  a trip under way     LIVE["underway"]: D2's 14:32 trip lasts 24 min, so at a clock before 14:56 (the 14:40 moment, over B1, B7, B9,
                       D1a, D1b or inside a task that starts at B1) its label reads "Te deja · aprox." and its title "Oferta de las 14:32 ·
                       en curso" (Round 24c; the kit's longer "· La tomaste · en curso" would wrap and cost the 24dp inset); over D1 (21:10)
                       its own "Te dejó · aprox." and "Oferta de las 14:32 · La tomaste". The figure's "sin el coche" (Round 24) stays on
                       its line. The kit draws the rest (Round 24d): at 14:40 the 14:32 row's outcome reads "En curso" on B1, B7, B9, D1a and
                       D1b (D1's 21:10 row keeps "La tomaste"), and the open day's basis line says "6 viajes tomados (1 en curso),". The
                       last finished trip at 14:40 is 11:20: its row on D1a / D1b opens D2e (task 9).
  the period picker    a week's period label (… ▾) opens E6 over its opener with the opener's week checked; a week there closes it
                       onto that week's screen in the opener's place (this week E8, last week E1), the undrawn weeks give the note; a
                       month's label gives the note (E6's month list is not drawn).
  first day            A3's "Abrir ajustes" opens A3s, the labelled stand-in for Android's settings; "Ya lo activé" returns past it to B3
                       ("Listo. Abre tu app de viajes") and "Regresar sin activarlo" to A3n, each drawn in A3's place (@ret); a tap on
                       A3's EJEMPLO picture says "Es solo un ejemplo. Toca «Abrir ajustes»." In task 7's world the same stand-in returns
                       to B2r (in B2's place) or A3n (WORLDS). On the first day A3's / A3n's "Ahora no" opens B3n (WORLDS, Round 24c).
The facilitator log keeps each attempt's taps, screens, limit events, first tap (shown for tasks flagged "first") and saved pair; its
mirror of the driver's window draws the editor's draft, the focused field, the save question and the snackbar too.
"""
import argparse
import html
import json
import re
import subprocess
import sys
from pathlib import Path

KIT = Path(__file__).resolve().parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

TAPPABLE = ".row, .btn, .of, .obs, .tab, .status, .sub2, .back, .icon, a, button, label"

BACK = "css:.top .back"
SCRIM = "css:.dim"
GRAB = "css:.sheet .grab"

NOT_HERE_TEXT = "Esta pantalla no está en esta prueba"
NOT_HERE = "@toast:" + NOT_HERE_TEXT  # a door to a screen the test does not draw (or another moment's): a neutral note, never a dead tap
TOAST_1148 = "@toast:Sin lectura: no pudimos leer la tarifa"  # the 11:48 gris offer has no detail sheet
NO_DETAIL = "@toast:Esta oferta no tiene detalle en esta prueba"  # an offer row the kit draws no detail for: a neutral note, never a dead tap
ROWS_1440 = [("14:32", "D2"), ("12:10", "D2b"), ("11:48", TOAST_1148)]  # the 14:40 moment's offers, each to its own detail
GREENS = "2 ofertas arriba de tu meta se fueron sin tomar"  # Tu día's observation row (D1a, D1b, D1): its filtered list is not drawn
GREENS_NOTE = "@toast:Esta lista no se abre en esta prueba"  # the filtered list behind the observation row: a neutral note
MORNING = [  # Tu día's 14:40 rows under 11:48 (D1a, D1b): the list scrolls to 8:15 under a fixed nav; 11:20, the last finished trip,
    # opens its detail (D2e, Round 24c: task 9), the rest the neutral note
    ("11:20", "D2e"), ("10:41", NO_DETAIL), ("10:02", NO_DETAIL),
    ("9:51", NO_DETAIL), ("9:24", NO_DETAIL), ("8:40", NO_DETAIL), ("8:15", NO_DETAIL),
]
LIMITS = [  # G2b, G2, G2c: each ring steps the $0.50 grid, a tap on the field focuses it in place (G2d), ← asks first when a change is unsaved (G2e);
    # "Por hora" is not drawn (per hour the step would be $10): the neutral note
    ("Por hora", NOT_HERE),
    ('css:.limit[aria-label="Tu meta"] i[aria-label^="Bajar"]', "@lim:meta:-"),
    ('css:.limit[aria-label="Tu meta"] i[aria-label^="Subir"]', "@lim:meta:+"),
    ('css:.limit[aria-label="Tu meta"] .lf', "@entry:meta"),
    ('css:.limit[aria-label="Tu mínimo"] i[aria-label^="Bajar"]', "@lim:min:-"),
    ('css:.limit[aria-label="Tu mínimo"] i[aria-label^="Subir"]', "@lim:min:+"),
    ('css:.limit[aria-label="Tu mínimo"] .lf', "@entry:min"),
    (BACK, "@leave"),
]
SEMAFORO = [  # every G4 state (G4–G4h); "Qué lee Kompara" is not drawn: the neutral note
    ("Tu meta y tu mínimo", "G2b"),
    ("¿No ves el semáforo?", "G8"),
    ("Qué lee Kompara", NOT_HERE),
]
PAUSE = ("Pausar el semáforo hasta mañana", NOT_HERE)  # the G4 states that carry it (not G4d, paused, nor G4h, off): the pause is not drawn
PREV_DAY = ("css:.top .period > i:first-child", NOT_HERE)  # Tu día's ‹ (D1a, D1b): the day before is not drawn
PERIOD_PICK = ("css:.period > span", "E6")  # a week's period label (… ▾): the picker, E6
MONTH_PICK = ("css:.period > span", NOT_HERE)  # a month's period label (… ▾): E6's month list is not drawn
EXAMPLE = ("css:.content > [role=img]", "@toast:Es solo un ejemplo. Toca «Abrir ajustes».")  # A3's picture of Android's question is never a control
BATTERY_BACK = "@backnote:En tu teléfono, aquí se abre Batería en los ajustes de Android."  # A4b's / A4r's "Abrir ajustes": back to its Hoy, saying so

LINKS = {
    # A · first run
    "A1": [("Empezar", "A2"), ("Recuperar mis datos", "A6")],
    "A2": [("Acepto", "A3"), ("No acepto", "A1")],
    # A3's "Abrir ajustes" opens A3s, the labelled stand-in for Android's settings (on the real phone Android's own open), with its
    # toast; its two buttons return past it, drawn in A3's place: granted → B3 ("Listo. Abre tu app de viajes"), not granted → A3n.
    # Round 23: task 7's world (20 oct) opens the same stand-in, and there granted is B2r (Hoy with the permission back, drawn in
    # B2's place: WORLDS) and not granted A3n, whose back returns to B2 (run 7: j4 and j5 met a note over an unchanged off state;
    # Codex 311 §3, Opus 312 §3). In that world A3's and A3n's "Ahora no" return to Hoy, B2 (WORLDS["20 oct"]["links"]; the pre-run-8
    # check met the note there). Round 24c: in the first day's world (task 8) their "Ahora no" opens B3n, Hoy off for want of the
    # permission (WORLDS["primer día"]["links"]), which becomes the task's root (retAt), not the note (Codex 321 §1, Opus 322 §2).
    "A3": [("Abrir ajustes", "A3s"), EXAMPLE, ("Ahora no", NOT_HERE)],
    "A3s": [("Ya lo activé", "@ret:B3"), ("Regresar sin activarlo", "@ret:A3n")],
    "A3n": [("Abrir ajustes", "A3s"), EXAMPLE, ("Ahora no", NOT_HERE)],
    # A4 (battery, the first run's): its "Abrir ajustes" is Android's again (re-checked on return, then B3); "Ahora no" goes to Hoy.
    # A4b and A4r are the same step opened from "Que no se vuelva a apagar" in task 7's morning, each in its Hoy's status words: A4b
    # from B2 (the permission still missing), A4r from B2r (the permission back; Round 23: the pre-run-8 check found B2r opening A4b's
    # "aún falta el permiso"). Their "Abrir ajustes" (Android's battery setting, then the return) goes back to the Hoy that opened it
    # with a note that says what the phone would open, never silently (the same check: it looked like a dead button); "Ahora no" goes
    # back too, as their ← does
    "A4": [("Abrir ajustes", "B3"), ("Ahora no", "B1")],
    "A4b": [("Abrir ajustes", BATTERY_BACK), ("Ahora no", "@back")],
    "A4r": [("Abrir ajustes", BATTERY_BACK), ("Ahora no", "@back")],
    "A5": [("Usar 12 km/L", "B1"), ("Ya sé mi costo por km", NOT_HERE)],
    "A6": [("Mandar código", "A7")],
    "A7": [("Cambiar número", "A6")],
    # B · Hoy — every status row (the door) opens Semáforo in the same state; B1, B7 and B9 (14:40) open only the 14:40 day.
    # B2 (task 7's morning) has no door; "Que no se vuelva a apagar" opens A4b (battery, the permission still missing); its ⚙ opens Ajustes in the same state (G1b → G4h), and Ganancias / Comparar,
    # which would show the 19-oct day, give the neutral note. B2r (the same morning, the permission back, reached from A3s's "Ya lo activé",
    # and from then on in place of B2: WORLDS["20 oct"]["granted"]) opens A4r from the same button (Round 23, not A4b); its door, "Abrir Uber para probar" and ⚙ give the note: G4e is the first run's ("Tu primera oferta lo
    # confirma", nothing ever read), the 20-oct offer is not drawn, and G1b's healthy counterpart is not drawn. B9's rows open their own details as B1's do, its 12:10 the one
    # with the cause line (D2d), and its "Ver todas" opens D1b, the 14:40 day whose 12:10 row reads "sin semáforo en
    # pantalla" as B9's does. B6's rows have no detail drawn: the neutral note.
    "B1": [
        ("Semáforo funcionando", "G4"),
        ("6 viajes tomados", "D3a"),
        ("Ver todas las ofertas de hoy", "D1a"),
        *ROWS_1440,
        ('css:use[href="#i-gear"]', "G1"),
        ("Hoy", "B1", {"in": ".nav"}),
        ("Ganancias", "E8", {"in": ".nav"}),
        ("Comparar", "F1", {"in": ".nav"}),
    ],
    "B2": [
        ("Volver a dar el permiso", "A3"),
        ("Que no se vuelva a apagar", "A4b"),
        ('css:use[href="#i-gear"]', "G1b"),
        ("Ganancias", NOT_HERE, {"in": ".nav"}),
        ("Comparar", NOT_HERE, {"in": ".nav"}),
    ],
    "B2r": [
        ("Semáforo preparado", NOT_HERE),
        ("Abrir Uber para probar", NOT_HERE),
        ("Que no se vuelva a apagar", "A4r"),
        ('css:use[href="#i-gear"]', NOT_HERE),
        ("Ganancias", NOT_HERE, {"in": ".nav"}),
        ("Comparar", NOT_HERE, {"in": ".nav"}),
    ],
    # B3n (Round 24c): the first day after A3's "Ahora no", Hoy off for want of the permission; its one primary goes back to A3, and
    # its ⚙ gives the note: no first-day Ajustes in the off state is drawn (G1 reads "Funcionando", G1b is 20 oct's)
    "B3n": [("Activar el semáforo", "A3"), ('css:use[href="#i-gear"]', NOT_HERE)],
    "B3": [
        ("Abrir Uber para probar", "C1"),
        ("Semáforo preparado", "G4e"),
        ("Usamos costos típicos", "A5"),
        ("Valores iniciales", "G2b"),
        ("Elegir mi ciudad", NOT_HERE),
        ("Avisarme si se apaga", NOT_HERE),
    ],
    "B4": [("Semáforo preparado", "G4f"), ("Ajustar mis costos", "A5")],
    "B5": [("Semáforo en pausa", "G4d"), ("Reanudar el semáforo", "B1"), ("12:10", "D2b"), ("11:48", TOAST_1148)],
    "B6": [
        ("Semáforo funcionando", "G4g"),
        ("10:05", NO_DETAIL),
        ("9:12", NO_DETAIL),
        ("8:36", NO_DETAIL),
        ("Ver todas las ofertas de hoy", NO_DETAIL),  # B6's 10:20 partial-support day has no Tu día drawn
    ],
    "B7": [
        ("Batería restringida", "G4b"),
        ("6 viajes tomados", "D3a"),
        ("Ver todas las ofertas de hoy", "D1a"),
        *ROWS_1440,
    ],
    "B8": [("Semáforo funcionando", "G4")],
    "B9": [
        ("no cupo el semáforo en 1 oferta", "G4c"),  # Round 24: the door names what it counts, in G4c's words
        ("6 viajes tomados", "D3a"),
        ("Ver todas las ofertas de hoy", "D1b"),
        ("14:32", "D2"),
        ("12:10", "D2d"),
        ("11:48", TOAST_1148),
    ],
    # C · the ride app (host frames): only C13's status bar is tappable (a tap or a drag down), as the pull-down to the shade
    # (H6b, the next morning's); the rest is HOST_BAR
    "C13": [("css:.statusbar", "H6b")],
    # D · Tu día — D1a, D1b and D3a are Hoy's 14:40; D1 and D3 are 21:10 (H6's "Tu resumen de hoy"). Sheets close with @back (GLOBALS).
    # D1a and D1b list the day's ten offers down to 8:15 (the list scrolls, the nav stays); every row answers, the morning ones
    # with the neutral note, as does the "2 ofertas arriba de tu meta se fueron sin tomar" observation (its filtered list is not drawn).
    "D1a": [("6 viajes tomados", "D3a"), (GREENS, GREENS_NOTE), *ROWS_1440, *MORNING, ("Hoy", "B1", {"in": ".nav"}), PREV_DAY],
    "D1b": [  # B9's world: its 12:10 row came without the semáforo, so its detail is D2d (D2b plus the cause line)
        ("6 viajes tomados", "D3a"),
        (GREENS, GREENS_NOTE),
        ("14:32", "D2"),
        ("12:10", "D2d"),
        ("11:48", TOAST_1148),
        *MORNING,
        ("Hoy", "B1", {"in": ".nav"}),
        PREV_DAY,
    ],
    "D1": [
        ("9 viajes tomados", "D3"),
        (GREENS, GREENS_NOTE),
        ("14:32", "D2"),
        ("14:50", "D2c"),
        ("12:10", "D2b"),
        ("11:48", TOAST_1148),
        ("17:21", NO_DETAIL),
        ("16:04", NO_DETAIL),
        ("Hoy", "B1", {"in": ".nav"}),
    ],
    # Round 23: each offer detail's day row (Round 24: "Llevas hoy, con el coche: $X ›") opens that day's breakdown, as drawn D3 (over D1,
    # 21:10) or D3a (D2d, over D1b, 14:40); over another opener LIVE["dayRow"] repaints its figure and its door from the opener's hero
    # and basis line ($548 → D3a at 14:40, $1,026 → D3 at 21:10; B5's $434 has no breakdown drawn: the note). Opus 312 §2.
    "D2": [("Llevas hoy, con el coche", "D3"), ("Cambiar tu meta y tu mínimo", "G2b"), ("Avisar que se leyó mal", NOT_HERE),
           ("¿Se canceló? Quitar de mi día", NOT_HERE)],
    "D2b": [("Llevas hoy, con el coche", "D3"), ("Cambiar tu meta y tu mínimo", "G2b"), ("Avisar que se leyó mal", NOT_HERE)],
    "D2c": [("Llevas hoy, con el coche", "D3"), ("Cambiar tu meta y tu mínimo", "G2b"), ("Avisar que se leyó mal", NOT_HERE),
            ("¿Se canceló? Quitar de mi día", NOT_HERE)],
    "D2d": [("Llevas hoy, con el coche", "D3a"), ("Cambiar tu meta y tu mínimo", "G2b"), ("Avisar que se leyó mal", NOT_HERE)],
    # D2e (Round 24c): the 11:20 trip, the last finished one at 14:40, over D1a / D1b (task 9; Codex 321, Opus 322 item 7)
    "D2e": [("Llevas hoy, con el coche", "D3a"), ("Cambiar tu meta y tu mínimo", "G2b"), ("Avisar que se leyó mal", NOT_HERE),
            ("¿Se canceló? Quitar de mi día", NOT_HERE)],
    "D3b": [(SCRIM, "@back"), (GRAB, "@back")],  # closing a sheet goes back, so the phone's back never reopens it
    # E · Ganancias — a chevron to a screen the test does not draw (the week before last, the IMSS help) gives the note.
    # Round 22: E1's reconciliation is one figure-free row, "Cuadrar con tu resumen de Uber", whose sheet (E1e, drawn over E1 as E1d is)
    # holds the Uber-only figures; "Por app" (DiDi te dejó $1.21 más por km) opened Comparar · Tus apps (F1). Run 6: six of six judges
    # stumbled on the old row's three figures, and j6's tap on it reached the note; j2, j5, j6 looked for the per-app split here.
    # Round 23: "Por app" opens its own sheet over E1, E1f (Por app · 12 – 18 oct), so the nav stays on Ganancias and closing it
    # returns to E1 on the same week (run 7: five of six judges were surprised by the silent switch to Comparar; Opus 312 §4). Its
    # "Manejaste cada app a distintas horas", as on F1, is a plain line in the kit (no chevron: Datos is not drawn), so it has no link.
    # The period's word steps ("Semana anterior" / "Semana siguiente", "Mes anterior" / "Mes siguiente") keep the old arrows' targets;
    # a step the kit draws inert (aria-disabled, ink-3: siguiente on an open period, anterior before Kompara) gets no link.
    # The period label between them ends in ▾, the picker's door: on a week it opens E6 (Elegir semana, drawn over its opener, its
    # check on the opener's week); on a month the note (E6's month list is not drawn).
    "E8": [
        PERIOD_PICK, ('css:use[href="#i-share"]', NOT_HERE),
        ("css:.period > i:first-child", "E1"),
        ("Mes", "E3"),
        ("Cada día se restan", "G5"),
        ("6 viajes tomados", NOT_HERE),
    ],
    "E1": [
        PERIOD_PICK, ('css:use[href="#i-share"]', NOT_HERE),
        ("62 viajes tomados", "E1d"),
        ("Por día", "E1b"),
        ("css:.spark i", "E1b", {"all": True}),
        ("css:.period > i:last-child", "E8"),
        ("css:.period > i:first-child", NOT_HERE),  # the week before last is not drawn
        ("Mes", "E3"),
        ("Cuadrar con tu resumen de Uber", "E1e"),
        ("Por app", "E1f"),
    ],
    "E1b": [(SCRIM, "@back"), (GRAB, "@back")],  # closing a sheet goes back, so the phone's back never reopens it
    "E1c": [(SCRIM, "@back"), (GRAB, "@back")],  # closing a sheet goes back, so the phone's back never reopens it
    "E1d": [(SCRIM, "@back"), (GRAB, "@back")],  # closing a sheet goes back, so the phone's back never reopens it
    # E1e opens only from E1's "Cuadrar…" row, so its scrim and handle go back (history) to E1, as the phone's back does, and
    # leave no E1e entry behind; "Traer otro resumen" (the import flow, H4, not wired in this test) gives the note, as E6's
    # "Traer un resumen" does
    "E1e": [(SCRIM, "@back"), (GRAB, "@back"), ("Traer otro resumen", NOT_HERE)],
    "E1f": [(SCRIM, "@back"), (GRAB, "@back")],  # opens only from E1's "Por app", as E1e does
    "E3": [
        MONTH_PICK, ('css:use[href="#i-share"]', NOT_HERE),
        ("Semana", "E8"),  # the Semana tab opens this week (E8), as the root does; last week is one step back
        ("155 viajes tomados", "D3b"),
        ("Por semana", "E3d"),
        ("css:.spark i", "E3d", {"all": True}),
        ("IMSS del mes", "E4"),
        ("css:.period > i:first-child", "E3b"),  # September, the month before Kompara (its › comes back)
    ],
    "E3b": [
        MONTH_PICK,
        ("css:.period > i:last-child", "E3"),
        ("Semana", "E8"),
        ("29 viajes según tu resumen", NOT_HERE),
        ("importada", NOT_HERE),
        ("IMSS de septiembre", NOT_HERE),
    ],
    "E3d": [(SCRIM, "@back"), (GRAB, "@back")],  # closing a sheet goes back, so the phone's back never reopens it
    # E6 (Elegir semana): a week closes the sheet onto its own screen in the opener's place (@ret: this week E8, last week E1); the
    # weeks the test does not draw at 19 oct (5 – 11 oct is E2's 2 nov, the September import) and "Traer un resumen" give the note
    "E6": [
        ("19 – 25 oct", "@ret:E8"),
        ("12 – 18 oct", "@ret:E1"),
        ("5 – 11 oct", NOT_HERE),
        ("28 sep – 4 oct", NOT_HERE),
        ("14 – 20 sep", NOT_HERE),
        ("Traer un resumen", NOT_HERE),
    ],
    "E4": [MONTH_PICK, ("css:.period > i:first-child", NOT_HERE), ("¿Cómo funciona?", NOT_HERE), ("Resumen en PDF", NOT_HERE), ("Consultar mi vigencia en el IMSS", NOT_HERE)],
    # F · Comparar
    "F1": [("css:.label.sel", NOT_HERE), ("Tu ciudad", "F2"), ("css:.period > i:first-child", NOT_HERE), ("css:.period > i:last-child", NOT_HERE)],
    "F2": [
        ("css:.label.sel", NOT_HERE), ("css:.period > i:first-child", NOT_HERE), ("css:.period > i:last-child", NOT_HERE),
        ("Tus apps", "F1"),
        ('css:use[href="#i-share"]', "H3"),
        ("Según los resúmenes de 1,240 conductores", NOT_HERE),
        ("Aportar al promedio", NOT_HERE),
    ],
    # G · Ajustes — G2*, G4* and G8 go back with @back (GLOBALS): they open from Hoy, Tu día and Ajustes. Every row that carries
    # a chevron to an editor or an answer the kit does not draw (Tus costos' rows, Tus datos', Ayuda's) gives the neutral note.
    "G1": [
        ("CDMX y Edomex", "G9"),
        ("Plan gratis", "H1"),
        ("Tu meta y tu mínimo", "G2b"),
        ("Tus costos", "G3"),
        ("Lo del coche", "G5"),
        ("Semáforo", "G4"),
        ("Avisos", "G7"),
        ("Ayuda", "G8"),
        ("Tus datos", "G6"),
        ("Invita y gana", NOT_HERE),
    ],
    "G1b": [  # task 7's morning: Ajustes with the semáforo off (from B2's ⚙); its back is history (GLOBALS)
        ("CDMX y Edomex", "G9"),
        ("Plan gratis", "H1"),
        ("Tu meta y tu mínimo", "G2b"),
        ("Tus costos", "G3"),
        ("Lo del coche", "G5"),
        ("Semáforo", "G4h"),
        ("Avisos", "G7"),
        ("Ayuda", "G8"),
        ("Tus datos", "G6"),
        ("Invita y gana", NOT_HERE),
    ],
    "G2b": LIMITS,  # its Guardar is drawn inactive at rest: the first change moves to G2 / G2c, where it is live
    "G2": [*LIMITS, ("Guardar", "@save"), ("Volver a lo inicial", "@reset")],
    "G2c": [*LIMITS, ("Guardar", "@save"), ("Volver a lo inicial", "@reset")],
    "G3": [*[(t, NOT_HERE) for t in ("Combustible", "Rendimiento", "Precio de la gasolina", "Desgaste", "Escribe tu costo por km")]],
    "G4": [*SEMAFORO, PAUSE],
    "G4b": [*SEMAFORO, ("Quitar restricción de batería", "G4"), PAUSE],
    "G4c": [*SEMAFORO, ("No cupo el semáforo", "D1b"), PAUSE],  # Round 24: the row's new name
    "G4d": [*SEMAFORO, ("css:.footer .btn.primary", "G4")],
    "G4e": [*SEMAFORO, PAUSE],  # B3's first run
    "G4f": [*SEMAFORO, PAUSE],  # B4's morning after
    "G4g": [*SEMAFORO, PAUSE],  # B6's DiDi switched off
    "G4h": [*SEMAFORO, ("Volver a dar el permiso", "A3")],  # B2's morning, from G1b: the same fix as B2's
    "G5": [("Guardar", "@back"), ("Quitar", "@back")],  # opens from Ajustes or from E8's car line, and closes back to it
    "G6": [("Eliminar cuenta", "G6b"), *[(t, NOT_HERE) for t in ("Sale del teléfono", "Datos guardados", "Borrar datos del teléfono")]],
    "G6b": [(SCRIM, "G6"), ("Cancelar", "G6")],
    "G7": [("Activar avisos en el teléfono", NOT_HERE)],
    "G8": [(t, NOT_HERE) for t in ("¿Por qué no me sale el semáforo?", "¿Qué significan los colores?", "¿Qué lee Kompara?",
                                    "¿Me pueden bloquear Uber o DiDi?", "¿Cuánto cuesta?", "Escríbenos por WhatsApp")],
    # H · Premium
    "H1": [("Empezar mi prueba", "H2")],
    "H2": [("Suscribirme ahora", NOT_HERE), ("Restaurar compra", NOT_HERE)],
    "H3": [("Compartir en WhatsApp", NOT_HERE), ("Más opciones", NOT_HERE)],
    "H6": [
        ("css:.content > div > div:first-child", "D1"),  # "Tu resumen de hoy está listo" (21:12): the 21:10 day (H6 itself only by ?screen=H6 now that C13's shade is H6b)
        ("css:.content > div > div:nth-child(2)", "B2"),  # "Se apagó el semáforo" (20:31): the permission lost, B2's outage
    ],
    "H6b": [  # C13's shade, the next morning
        ("css:.content > div > div:first-child", "B2"),  # "Se apagó el semáforo" (ayer 20:31): the same notice, B2
        ("css:.content > div > div:nth-child(2)", "E1"),  # "Tu semana está lista" (ayer 9:02): the week, E1
        ("css:.content > div > div:nth-child(3)", NOT_HERE),  # "Octubre: revisa tus registros del mes" (ayer 8:00): E3 is the 19-oct month
    ],
}

# Navigation remapped while the session is in another moment (see the module docstring); "enter" screens set the world.
WORLDS = {
    "20 oct": {  # task 7's morning: the permission was lost at 20:31 the night before; nothing of the 19-oct day is shown
        "enter": ["C13", "H6b", "B2", "B2r"],
        "map": {"B1": "B2", "G1": "G1b", "G4": "G4h", "E8": NOT_HERE, "E3": NOT_HERE, "F1": NOT_HERE,
                "B3": "B2r"},  # A3s's "Ya lo activé" (@ret:B3) lands on this driver's Hoy with the permission back, never first-day B3
        "retAt": {"B2r": "B2",  # ... in B2's place: back from it leaves Kompara (C13 or H6b)
                  "B2": "B2"},  # A3's / A3n's "Ahora no" (@ret:B2, "links"): back to Hoy itself, also from the gear route (G1b → G4h → A3)
        "links": {"A3": [("Ahora no", "@ret:B2")], "A3n": [("Ahora no", "@ret:B2")]},  # not the first day's note (the pre-run-8 check)
        "granted": {"by": "B2r", "map": {"B2": "B2r"}},  # landing on B2r sets the attempt's flag; from then on B2 is B2r on every route
        # (C13's "Abrir Kompara", H6b's "Se apagó el semáforo", a back): the off state never shows again in that attempt (the pre-run-8 check)
    },
    "primer día": {  # task 8's first day (12:30): nothing has been read yet, so Hoy is B3, Semáforo G4e, and the 19-oct day is the note
        "enter": ["A1"],
        "map": {"B1": "B3", "G4": "G4e", "E8": NOT_HERE, "E3": NOT_HERE, "F1": NOT_HERE},
        # Round 24c: A3's / A3n's "Ahora no" opens B3n (Hoy off, the permission missing) as the task's root, A1 and the steps above
        # it gone, as onboarding ends on the real phone; a later "Ya lo activé" (B3n → A3 → A3s) lands B3 in B3n's place, so back
        # never shows the off state again (Codex 321 §1, Opus 322 §2)
        "retAt": {"B3n": "A1", "B3": "B3n"},
        "links": {"A3": [("Ahora no", "@ret:B3n")], "A3n": [("Ahora no", "@ret:B3n")]},
    },
    "sin semáforo": {  # B9's day (an alternate, opened directly): the 12:10 offer got no semáforo, so Hoy is B9, Semáforo G4c, Tu día D1b
        "enter": ["B9"],
        "map": {"B1": "B9", "G4": "G4c", "D1a": "D1b", "D2b": "D2d"},
    },
}

# The runtime layer over the static frames (see the module docstring).
LIVE = {
    "backdrop": ["D2", "D2b", "D2c", "D2d", "D2e", "D3a", "D3b", "G5", "E6"],  # sheets shown over whichever screen opened them
    "picker": ["E6"],  # the period picker marks the opener's week (ink, 600 and the check), not the one it is drawn over
    "offers": ["D2", "D2b", "D2c", "D2d", "D2e"],  # offer details: " a esa hora" once the limit their line names differs from the fixture's
    "offerTops": {  # where an offer detail may open over each screen behind it, lowest first: lines that slice no glyph (Round 23,
        # part-d's measurements). A detail opens on the lowest at or above its drawn top where it fits, and its rows' padding takes
        # up the slack, so the 24dp inset holds: Hoy's hero sits 64dp lower than Tu día's, so D2's 224 would cut through
        # Hoy's $548 (208 lies between its label and its figure); "default" is Tu día's, the detail's own drawn backdrop
        "B1": [280, 208], "B5": [280, 208], "B7": [280, 208], "B9": [280, 208],
        "D1": [280, 224], "D1a": [280, 224], "D1b": [280, 224], "default": [280, 224],
    },
    "offerPad": 14.5,  # dp: the least padding above and below each of a detail's three rows before it opens higher (" a esa hora" wraps
    # D2c's line, +22dp, into its slack); 6 × 14.5 = 87dp, the floor of the old gap layout (12 + 12, 12 + 12, the 16dp gap, 8 + 8 and the
    # 7dp gap), so every detail opens where it opened before Round 24e
    "dayRow": "Llevas hoy, con el coche",  # the details' day row: its figure and its door follow the opener's hero and basis line
    "underway": {  # a trip still under way at the clock the sheet shows: D2's 14:32 trip lasts 24 min, so at 14:40 (or C2's 14:50) it "deja"
        "D2": {"from": "14:32", "until": "14:56", "label": "Te deja · aprox.",
               "title": "Oferta de las 14:32 · en curso"},  # Round 24c: the title says the trip is still under way; the kit's
        # "· La tomaste · en curso" needs 362 of the title's 325dp and wraps, so the sheet would lose 4dp of its 24dp inset (part-d's measure)
    },
    "limits": {"meta": 8.00, "min": 6.00},  # the fixture's pair: every task starts with it; "Volver a lo inicial" puts it in the draft
    "step": 50,  # cents: the rings' $0.50 grid (per hour the kit's step is $10; the prototype draws Por km only)
    "editors": {"G2b": "rest", "G2": "meta", "G2c": "min"},  # G2b at rest; the first change moves to G2 (tu meta) or G2c (tu mínimo)
    "regla": {  # the preview chip per band: cue path, marker path, phrase (verbatim from _regla-markup.html)
        "v": ["M15 2.5 29 27H1z", "M7 0 14 12H0z", "Arriba de tu meta"],
        "y": ["M15 1 29 15 15 29 1 15z", "M7 0 13 6 7 12 1 6z", "Cumple tu mínimo"],  # Round 23 (Codex 311 §1)
        "r": ["M1 3h28L15 27.5z", "M0 0h14L7 12z", "Abajo de tu mínimo"],
    },
}

# Applied to every Kompara screen (not the C host frames) after LINKS; skipped silently when absent or under a scrim.
GLOBALS = [
    ('use[href="#i-gear"]', "G1"),
    (".top .back", "@back"),
    ('use[href="#i-close"]', "@back"),
    (".dim", "@back"),
    (".sheet .grab", "@back"),
]
TABS = {"Hoy": "B1", "Ganancias": "E8", "Comparar": "F1"}  # every nav tab that is not the active one

# C frames are the ride app: nothing inside the phone is tappable (C6's plate passes touches through) except C13's status bar
# (LINKS: the pull-down to Kompara's waiting notice, H6b); the switch back is a top-bar control, drawn as quietly as "← Atrás".
# C13 is task 7's permission-lost shift, so opening Kompara lands on B2; C6 is a stopped capture that opening Kompara restarts,
# so it lands on a working Hoy.
HOST_BAR = {"default": [["← Volver a Kompara", "@opener"]], "C13": [["Abrir Kompara", "B2"]], "C6": [["Abrir Kompara", "B1"]]}

# States drawn live over the editor and its opener, from these frames' own markup (see LIVE in the docstring): G2d's focused field
# (its helper, keypad, the screen's height over it, the footer's inset), G2e's save question, G4i's snackbar and G4b's ring-! glyph.
ENTRY_FROM, ASK_FROM, SNACK_FROM, GLYPH_FROM = "G2d", "G2e", "G4i", "G4b"

TASKS = [  # "first": the facilitator panel shows and keeps the first tap · "limits": it shows the saved pair · "ok": facilitator only
    {"n": 1, "start": "B1", "prompt": "Abres la app a media jornada. ¿Cuánto llevas hoy y la app está leyendo tus ofertas?",
     "ok": "Lo dice B1: $548 y «Semáforo funcionando»."},
    {"n": 2, "start": "C2", "prompt": "Te llega una oferta en Uber. Mírala una vez, de reojo: ¿qué te dice esta oferta? Luego: ¿la tomarías? ¿por qué?",
     "ok": "Lo dice C2: $7.16/km, cumple tu mínimo de $6.00/km sin llegar a tu meta de $8.00/km (amarillo ◆). "
           "«¿La tomarías?» no tiene respuesta correcta: anota su porqué."},
    {"n": 3, "start": "B1", "prompt": "Desde Hoy, encuentra todas las ofertas que te llegaron hoy, con lo que te deja cada una por km.",
     "ok": "D1a (Tu día a las 14:40), desde «Ver todas las ofertas de hoy»: llega y lee lo que le deja cada oferta por km "
           "(las 10, bajando hasta las 8:15). Anota aparte si dice «sin el coche» sin que se lo pregunten."},
    {"n": 4, "start": "B1", "prompt": "Desde Hoy, encuentra cuánto te quedó la semana pasada después de todos tus gastos, incluido el coche.",
     "ok": "E1 ($4,980) o E1d. «Cuadrar con tu resumen de Uber» → E1e es la conciliación de Uber, no la respuesta."},
    {"n": 5, "start": "B1", "prompt": "Desde Hoy, encuentra qué app, Uber o DiDi, te pagó más por km la semana pasada.",
     "ok": "E1f (Ganancias → Semana anterior → Por app, una hoja sobre E1) o F1 (Comparar): DiDi, $1.21 más por km "
           "($10.34/km contra $9.13/km, bajo «te dejó por km, sin el coche»); la fila Por app de E1 ya lo dice."},
    {"n": 6, "start": "B1", "prompt": "Cambia tu mínimo a $6.50 por km.", "first": True, "limits": True,
     "ok": "Hoy → G4 → G2b (o ⚙ → G1 → G2b) → tu mínimo $6.50 (+ una vez, o tocar la cifra y escribir 6.50) → Guardar → "
           "de vuelta en Semáforo (o Ajustes) con «Guardado: tu mínimo ahora es $6.50/km»."},
    {"n": 7, "start": "C13", "prompt": "Empiezas tu turno temprano y el semáforo no sale en tus ofertas. Averigua por qué y qué hacer.",
     "first": True,
     "ok": "Llega a A3 por cualquier ruta: la notificación (tocar o arrastrar la barra de estado → H6b → «Se apagó el semáforo») "
           "o «Abrir Kompara» (barra) → B2 → Volver a dar el permiso → A3; también B2 → ⚙ → G1b → Semáforo → G4h → Volver a dar "
           "el permiso. Con conductores reales la tarea termina en A3, antes de «Abrir ajustes». En el prototipo sigue: «Abrir ajustes» → "
           "A3s (con su aviso) → «Ya lo activé» → B2r («Semáforo preparado · permiso listo», en lugar de B2; desde ahí «Abrir Kompara» "
           "y la notificación también abren B2r), o «Regresar sin activarlo» → A3n (atrás o «Ahora no» vuelven a B2); puntúa esa "
           "recuperación aparte. «Que no se vuelva a apagar» es la batería, no el permiso: desde B2 abre A4b, desde B2r A4r; su «Abrir "
           "ajustes» vuelve a esa pantalla de Hoy con el aviso «En tu teléfono, aquí se abre Batería…»."},
    {"n": 8, "start": "A1", "prompt": "Es tu primer día: desde la pantalla de bienvenida, haz que funcione el semáforo.",
     "ok": "A1 → A2 → A3 → Abrir ajustes → (teléfono real; en la prueba A3s) → Ya lo activé → B3."},
    {"n": 9, "start": "B1", "prompt": "Desde Hoy, busca cuánto te dejó tu último viaje terminado.",
     "ok": "B1 → Ver todas las ofertas de hoy → la fila de las 11:20 → D2e: $133 sin el coche (la de las 14:32 dice «En curso»)."},
]


TASK_ORDER = [8, 1, 2, 3, 4, 5, 6, 7, 9]  # the session order (driver-test-plan.md): task 8 first, before task 7's A3 can rehearse it


TAG = re.compile(r"<div\b|</div\s*>")


def div_end(s, start):
    """Index just past the </div> that closes the <div> opening at `start`."""
    depth = 0
    for m in TAG.finditer(s, start):
        depth += -1 if m.group().startswith("</") else 1
        if depth == 0:
            return m.end()
    raise ValueError(f"unbalanced <div> at {start}")


def extract(src):
    doc = re.sub(r"<!--.*?-->", "", src, flags=re.S)
    head = doc[: doc.find("</head>")]
    links = re.findall(r"<link\b[^>]*>", head)
    sprite = re.search(r'<svg style="display:none".*?</svg>', doc, re.S).group(0)
    screens, notes = {}, {}
    pos = 0
    while (start := doc.find('<div class="frame"', pos)) != -1:
        end = div_end(doc, start)
        frame = doc[start:end]
        pos = end
        cap = re.search(r'<div class="cap"><b>(\S+) ·', frame)
        p = re.search(r'<div class="phone[ "]', frame)
        if not cap or not p:
            print(f"warning: skipped a frame without caption id or phone near offset {start}", file=sys.stderr)
            continue
        sid = cap.group(1)
        if sid in screens:
            print(f"warning: duplicate frame id {sid}; keeping the first", file=sys.stderr)
            continue
        screens[sid] = frame[p.start(): div_end(frame, p.start())]
        label = re.search(r'<div class="alt">(.*?)</div>', frame[: p.start()], re.S)
        if label:
            notes[sid] = html.unescape(re.sub(r"<[^>]+>", "", label.group(1))).strip()
    return links, sprite, screens, notes


def live_parts(screens):
    """G2d's focused field (keypad, screen height over it, footer inset), G2e's dialog, G4i's snackbar and G4b's ring-! glyph, as markup
    for the live editor; G2d, G2e and G4i leave the screen list (they are drawn live over the editor and its opener)."""
    ph, ask, sn = screens.pop(ENTRY_FROM), screens.pop(ASK_FROM), screens.pop(SNACK_FROM)
    k0 = ph.find('<div data-os="ime"')
    scr = re.search(r'<div class="screen" style="bottom:(\d+px)">', ph)
    foot = re.search(r'<div class="footer" style="padding-bottom:(\d+px)">', ph)
    sel = re.search(r'<div class="lf focus"[^>]*><span class="money t-num">\$<span class="sel">[\d.]+</span>', ph)
    if k0 < 0 or not (scr and foot and sel and '<div class="lf-help">' in ph):
        raise SystemExit(f"{ENTRY_FROM} lost its keypad, its screen height over it, its footer inset, its focused field with the figure selected "
                         "or its helper: the field cannot be focused in place")
    glyph = re.search(r'<div class="status waits">(<svg\b.*?</svg>)', screens[GLYPH_FROM], re.S)
    if not glyph:
        raise SystemExit(f"{GLYPH_FROM} lost its ring-! glyph: the field's error cannot be drawn")
    dim, d0 = re.search(r'<div class="dim"></div>', ask), ask.find('<div class="dialog"')
    if not dim or d0 < 0 or not re.search(r'<div class="db"[^>]*>.*?</div><div class="da"', ask[d0:], re.S):
        raise SystemExit(f"{ASK_FROM} lost its scrim, its dialog or the dialog's body and actions: the save question cannot be drawn")
    t0 = sn.find('<div class="toast"')
    if t0 < 0:
        raise SystemExit(f"{SNACK_FROM} lost its .toast: the snackbar after Guardar cannot be drawn")
    return {"ime": ph[k0: div_end(ph, k0)], "imeH": scr.group(1), "footPad": foot.group(1), "glyph": glyph.group(1),
            "ask": dim.group(0) + ask[d0: div_end(ask, d0)], "snack": sn[t0: div_end(sn, t0)]}


def link_list():
    out = []
    for src, specs in LINKS.items():
        for spec in specs:
            matcher, to, *opt = spec
            out.append([src, matcher, to, opt[0] if opt else {}])
    return out


def build(src_path, out_path):
    links, sprite, screens, notes = extract(src_path.read_text(encoding="utf-8"))
    live2 = live_parts(screens)
    specs = link_list()
    remapped = {to for w in WORLDS.values() for to in [*w["enter"], *w["map"], *w["map"].values(), *w.get("retAt", {}), *w.get("retAt", {}).values()]}
    for w in WORLDS.values():
        g = w.get("granted")
        if g:
            remapped |= {g["by"], *g["map"], *g["map"].values()}
        for src, over in w.get("links", {}).items():
            for matcher, to in over:
                if not any(spec[0] == matcher for spec in LINKS.get(src, [])):
                    raise SystemExit(f"a world's link {src} · {matcher} has no LINKS spec with that matcher to override")
                remapped |= {src, to[5:] if to.startswith("@ret:") else to}
    named = {sid for src, _, to, _ in specs for sid in (src, to[5:] if to.startswith("@ret:") else to)} | {t["start"] for t in TASKS} | remapped
    named |= set(LIVE["backdrop"]) | set(LIVE["offers"]) | (set(LIVE["offerTops"]) - {"default"}) | set(LIVE["underway"])
    missing = sorted(sid for sid in named if not sid.startswith("@") and sid not in screens)
    cfg = {"tappable": TAPPABLE, "links": specs, "globals": GLOBALS, "tabs": TABS, "hostBar": HOST_BAR, "worlds": WORLDS,
           "notHere": NOT_HERE_TEXT, "tasks": TASKS, "taskOrder": TASK_ORDER, "live": LIVE, "live2": live2, "notes": notes, "order": list(screens)}
    sections = "\n".join(f'<section class="pt-scr" data-id="{sid}" hidden>{ph}</section>' for sid, ph in screens.items())
    page = (TEMPLATE
            .replace("%LINKS%", "\n".join(links))
            .replace("%SPRITE%", sprite)
            .replace("%SCREENS%", sections)
            .replace("%CONFIG%", json.dumps(cfg, ensure_ascii=False).replace("</", "<\\/")))
    out_path.write_text(page, encoding="utf-8")
    print(f"wrote {out_path.name}: {len(screens)} screens (+ {ENTRY_FROM}, {ASK_FROM}, {SNACK_FROM}, drawn live over the editor and its opener), "
          f"{len(specs)} link specs, {len(TASKS)} tasks, {len(page) // 1024} KB")
    if missing:
        print("screens referenced by the map, the worlds, LIVE or the tasks but not in the kit: " + ", ".join(missing))
    return missing


def check(out_path):
    url = out_path.resolve().as_uri() + "?debug=1"
    run = subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--virtual-time-budget=4000", "--dump-dom", url],
                         capture_output=True, text=True, timeout=180)
    m = re.search(r'<pre id="pt-report"[^>]*>(.*?)</pre>', run.stdout, re.S)
    if not m:
        print("check: no report in the rendered page (did the script fail?)", file=sys.stderr)
        return 2
    rep = json.loads(html.unescape(m.group(1)))
    print(f"check: {rep['specs']} specs · {rep['matched']} matched · {len(rep['unmatched'])} unmatched · "
          f"{len(rep['conflicts'])} conflicts · {rep['hotspots']} hotspots")
    for k in ("unmatched", "conflicts", "missingSource", "missingTarget"):
        for line in rep[k]:
            print(f"  {k}: {line}")
    silent = rep.get("silent", [])
    print(f"info: {len(silent)} chevrons with no link (not under a scrim), on any screen, reachable or not")
    for line in silent:
        print(f"  silent: {line}")
    return 1 if rep["unmatched"] or rep["conflicts"] else 0


TEMPLATE = r"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Kompara · prototipo</title>
<meta name="description" content="Clickable prototype of the Kompara v3 kit for the nine-task driver usability test. Generated by make-prototype.py from index.html; do not edit by hand.">
%LINKS%
<style>
html,body{background:#17181a;overscroll-behavior:none}
body{margin:0;padding:0;color:#e8e7e2;font-family:var(--font-ui)}
.pt-app{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);column-gap:32px;padding:12px 24px 32px;align-items:start;box-sizing:border-box}
.pt-col{grid-column:2;display:flex;flex-direction:column;align-items:center}
.pt-bar{width:393px;min-height:40px;margin-bottom:18px;display:flex;align-items:center;gap:10px;font-size:13px;line-height:18px}
.pt-bar b{font-size:15px;font-weight:600;min-width:34px}
.pt-bar .pt-note{color:#a6a59f;flex:1;min-width:0}
.pt-host{display:flex;gap:8px}
.pt-b{font:600 13px/18px var(--font-ui);color:#e8e7e2;background:#2a2b2e;border:1px solid #3a3b3f;border-radius:8px;padding:7px 12px;cursor:pointer}
.pt-b:hover{background:#34353a}.pt-b:disabled{opacity:.4;cursor:default}
.pt-b.pt-go{background:#e8e7e2;color:#111110;border-color:#e8e7e2}
.pt-b.pt-warn{background:#3a2a1c;border-color:#6b4a2a}
.pt-fit{position:relative}
.pt-stage{width:393px;height:852px;transform-origin:0 0;position:relative;touch-action:manipulation}
.pt-scr[hidden]{display:none}
.pt-stage .content,.pt-stage .sheet{overscroll-behavior:none}
.pt-stage .phone{cursor:default;user-select:none;-webkit-user-select:none}
.pt-stage .statusbar.pt-hot{touch-action:none}
.pt-hot{-webkit-tap-highlight-color:transparent}
.pt-hot:active,.pt-hot.pt-pressed{box-shadow:inset 0 0 0 999px rgba(17,17,16,.08)}
.pt-debug .pt-hot{outline:2px dashed #ff4d6d;outline-offset:-2px;cursor:pointer}
.pt-toast{position:absolute;left:50%;bottom:120px;max-width:300px;text-align:center;transform:translateX(-50%);background:#111110;color:#f5f4ef;font-size:13px;padding:8px 12px;border-radius:8px;z-index:5;pointer-events:none;opacity:0;transition:opacity .2s}
.pt-toast.on{opacity:1}
.pt-stage [data-pt-k],.pt-stage [data-pt-a]{cursor:pointer}
.pt-stage [data-pt-k]:active,.pt-stage .da [data-pt-a]:active{box-shadow:inset 0 0 0 999px rgba(17,17,16,.08)}
.pt-stage .limit .lf .pt-caret{display:inline-block;width:2px;height:30px;margin-left:1px;vertical-align:-5px;background:var(--ink);font-style:normal;animation:pt-blink 1.06s steps(1) infinite}
@keyframes pt-blink{50%{opacity:0}}
.pt-stage .limit .lf-help.pt-err{display:flex;align-items:center;gap:6px;color:var(--ink);font-weight:600}
.pt-panel{grid-column:3;justify-self:start;width:340px;background:#232426;border-radius:12px;padding:16px;box-sizing:border-box;position:sticky;top:12px;max-height:calc(100vh - 24px);overflow:auto;font-size:13px;line-height:18px}
.pt-panel h2{font-size:15px;line-height:20px;margin:0 0 8px;font-weight:600}
.pt-panel label{display:block;color:#a6a59f;margin-bottom:12px}
.pt-panel input{display:block;width:100%;box-sizing:border-box;margin-top:4px;font:14px/20px var(--font-ui);color:#f2f1ec;background:#17181a;border:1px solid #3a3b3f;border-radius:8px;padding:7px 10px}
.pt-conn{margin:0 0 12px;color:#a6a59f;min-height:54px}
.pt-conn.on{color:#bfe3c8}
.pt-live{background:#17181a;border-radius:10px;padding:10px 12px;margin-bottom:14px}
.pt-live .pt-row{display:flex;gap:8px;margin-top:8px}
.pt-log{margin:6px 0 0;padding:0;list-style:none;color:#a6a59f}
.pt-log li{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.pt-tasks{list-style:none;margin:0;padding:0}
.pt-tasks li{border-top:1px solid #34353a;padding:10px 0}
.pt-tasks li.on{background:#1d2a20;margin:0 -16px;padding:10px 16px}
.pt-tasks li.next .pt-q b{color:#bfe3c8}
.pt-tasks .pt-q{color:#f2f1ec}
.pt-tasks .pt-meta{color:#a6a59f;margin-top:4px}
.pt-tasks .pt-res{margin-top:4px;color:#c9c8c2}
.pt-tasks .pt-row{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:8px}
.pt-foot{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px;padding-top:14px;border-top:1px solid #34353a}
.pt-foot p{margin:0;color:#a6a59f;width:100%}
.pt-dbg{position:fixed;left:12px;bottom:12px;max-width:440px;max-height:45vh;overflow:auto;background:#111110;border:1px solid #ff4d6d;border-radius:10px;padding:10px 12px;font:12px/16px ui-monospace,Menlo,monospace;color:#f5f4ef;z-index:10;white-space:pre-wrap}
@media (max-width:1180px){.pt-app{grid-template-columns:auto auto;justify-content:center}.pt-col{grid-column:1}.pt-panel{grid-column:2}}
@media (max-width:820px){.pt-app{grid-template-columns:1fr;padding:12px 16px 32px}.pt-col,.pt-panel{grid-column:1;justify-self:center}.pt-panel{position:static;max-height:none;width:min(393px,100%);margin-top:24px}}
/* participant mode: the phone fitted under one slim bar (the host controls, and the task controls behind a handle) */
.pt-par .pt-app{display:flex;flex-direction:column;align-items:center;padding:6px 8px 0}
.pt-par .pt-col{width:100%}
.pt-par .pt-bar{position:sticky;top:0;z-index:20;width:min(413px,100%);min-height:34px;margin:0 0 14px;padding:2px 0;flex-wrap:wrap;gap:6px;background:#17181a;box-sizing:border-box}
.pt-par .pt-b{font-size:12px;line-height:16px;padding:6px 10px;font-weight:500;color:#c9c8c2}
.pt-par #pt-back{order:1}.pt-par .pt-host{order:2}
.pt-handle{order:4;margin-left:auto;font:500 12px/16px var(--font-ui);color:#8d8c86;background:transparent;border:1px solid #2c2d31;border-radius:8px;padding:6px 10px;cursor:pointer;display:flex;align-items:center;gap:6px}
.pt-handle i{display:inline-block;width:6px;height:6px;border-radius:3px;background:#6f9c7b}
.pt-handle i[hidden]{display:none}
.pt-ctl{display:contents}
.pt-ctl[hidden]{display:none}
.pt-ctl > *{order:6}
.pt-ctl .pt-break{order:5;flex-basis:100%;height:0}
.pt-ctl input{order:3;margin-left:auto;width:84px;box-sizing:border-box;font:12px/16px var(--font-ui);color:#e8e7e2;background:#111110;border:1px solid #3a3b3f;border-radius:8px;padding:6px 8px}
.pt-par .pt-bar:has(.pt-ctl:not([hidden])) .pt-handle{margin-left:0}
.pt-pick{display:flex;align-items:center;gap:4px}
.pt-pick b{font-size:12px;font-weight:600;min-width:18px;text-align:center;color:#c9c8c2}
@media (max-width:420px){.pt-par .pt-ctl .pt-b{padding:6px 8px}}
/* a phone (participant, 500px wide or less): no bezel, the screens edge to edge under the bar at about 1:1 (fit) */
.pt-narrow .pt-app{padding:0}
.pt-narrow .pt-bar{width:100%;margin:0 0 4px;padding:4px 8px}
.pt-narrow .pt-stage .phone{border-radius:0;box-shadow:none}
</style>
</head>
<body>
%SPRITE%
<div class="pt-app">
  <div class="pt-col">
    <div class="pt-bar" id="pt-bar">
      <button class="pt-b" id="pt-back" type="button">← Atrás</button><b id="pt-id"></b><span class="pt-note" id="pt-note"></span><span class="pt-host" id="pt-host"></span>
      <button class="pt-handle" id="pt-handle" type="button" aria-expanded="false" aria-controls="pt-ctl"><i id="pt-dot" hidden></i><span id="pt-hn"></span></button>
      <div class="pt-ctl" id="pt-ctl" hidden>
        <span class="pt-break"></span>
        <span class="pt-pick"><button class="pt-b" id="pt-prev" type="button" aria-label="Tarea anterior">‹</button><b id="pt-n"></b><button class="pt-b" id="pt-next" type="button" aria-label="Tarea siguiente">›</button></span>
        <button class="pt-b" id="pt-start" type="button">Iniciar</button>
        <button class="pt-b" id="pt-done" type="button">Hecho</button>
        <button class="pt-b" id="pt-stuck" type="button">Atascado</button>
        <button class="pt-b" id="pt-export2" type="button">Exportar</button>
        <input id="pt-code" type="text" autocomplete="off" placeholder="Código" aria-label="Código del participante">
      </div>
    </div>
    <div class="pt-fit" id="pt-fit"><div class="pt-stage" id="pt-stage">
%SCREENS%
      <div class="pt-toast" id="pt-toast"></div>
    </div></div>
  </div>
  <aside class="pt-panel" id="pt-panel">
    <h2>Prueba con conductores · facilitador</h2>
    <p class="pt-conn" id="pt-conn"></p>
    <label>Participante<input id="pt-who" type="text" autocomplete="off" placeholder="Nombre o código"></label>
    <div class="pt-live" id="pt-live"></div>
    <ol class="pt-tasks" id="pt-tasks"></ol>
    <div class="pt-foot">
      <button class="pt-b" id="pt-export" type="button">Exportar resultados (JSON)</button>
      <button class="pt-b" id="pt-clear" type="button">Borrar</button>
      <p id="pt-count"></p>
      <p>Atajos de teclado (en cualquiera de las dos ventanas): N inicia la tarea siguiente · H Hecho · A Atascado.</p>
    </div>
  </aside>
</div>
<script type="application/json" id="pt-config">%CONFIG%</script>
<script>
(function () {
  "use strict";
  var cfgEl = document.getElementById("pt-config");
  var CFG = JSON.parse(cfgEl.textContent);
  cfgEl.parentNode.removeChild(cfgEl);  // the config, answers included, leaves the page: only this window's memory keeps it
  var LIVE = CFG.live;
  var Q = new URLSearchParams(location.search);
  var DEBUG = Q.get("debug") === "1";
  var FAC = Q.get("facilitador") === "1";
  var ROLE = FAC ? "facilitador" : "participante";
  var $ = function (id) { return document.getElementById(id); };
  var drop = function (id) { var e = $(id); if (e) e.parentNode.removeChild(e); };
  if (FAC) { drop("pt-handle"); drop("pt-ctl"); }
  else {  // participant mode: no answers, no frame ids or labels, no facilitator panel
    CFG.tasks.forEach(function (t) { delete t.ok; });
    CFG.notes = {};
    drop("pt-panel"); drop("pt-id"); drop("pt-note");
  }
  document.body.classList.add(FAC ? "pt-fac" : "pt-par");
  var KEY = "kompara-prototipo-v1";
  var LKEY = "kompara-prototipo-limites";
  var BUS = "kompara-prototipo-bus-";
  var each = function (list, fn) { Array.prototype.forEach.call(list, fn); };
  var stage = $("pt-stage"), fitBox = $("pt-fit");
  var screens = {};
  each(document.querySelectorAll(".pt-scr"), function (s) { screens[s.dataset.id] = s; });
  var norm = function (t) { return t.replace(/ /g, " ").replace(/\s+/g, " ").trim(); };
  var phoneOf = function (id) { return screens[id].querySelector(".phone"); };
  var isHost = function (id) { return !!screens[id] && phoneOf(id).classList.contains("host"); };
  var isAct = function (to) { return to.charAt(0) === "@" && to !== "@back" && to !== "@opener"; };
  var maxN = CFG.tasks.reduce(function (m, t) { return Math.max(m, t.n); }, 1);
  var ORDER = (CFG.taskOrder || CFG.tasks.map(function (t) { return t.n; })).slice();  // the session order: ‹ › and "next" walk it
  var first = function () { return ORDER[0]; };
  var stepTask = function (n, d) { var i = ORDER.indexOf(n); if (i === -1) return first(); return ORDER[Math.max(0, Math.min(ORDER.length - 1, i + d))]; };

  // ---------- link map ----------
  function topLayer(phone) {  // the sheet or dialog over a scrim, else the phone
    if (!phone.querySelector(".dim")) return phone;
    var d = phone.querySelectorAll(".dialog"); if (d.length) return d[d.length - 1];
    var s = phone.querySelectorAll(".sheet"); if (s.length) return s[s.length - 1];
    return phone;
  }
  function covered(el, phone) {
    if (!phone.querySelector(".dim")) return false;
    if (el.classList.contains("dim")) return false;
    return !topLayer(phone).contains(el);
  }
  function byText(root, text) {
    var best = null, len = Infinity;
    each(root.querySelectorAll("*"), function (el) {
      if (el.closest("style")) return;
      var t = norm(el.textContent);
      if (t.length <= len && t.indexOf(text) !== -1) { best = el; len = t.length; }
    });
    return best;
  }
  function climb(el, phone) {
    var c = el.closest(CFG.tappable);
    return c && phone.contains(c) ? c : el;
  }
  var report = { specs: 0, matched: 0, unmatched: [], conflicts: [], missingSource: [], missingTarget: [], hotspots: 0, silent: [] };
  function wire(el, to, why) {
    if (el.dataset.ptTo) return false;
    el.dataset.ptTo = to;
    el.classList.add("pt-hot");
    report.hotspots++;
    if (DEBUG) el.title = "→ " + to + "   (" + why + ")";
    return true;
  }
  CFG.links.forEach(function (spec) {
    var src = spec[0], m = spec[1], to = spec[2], opt = spec[3] || {};
    var desc = src + " · " + m + " → " + to;
    report.specs++;
    if (!screens[src]) { report.missingSource.push(desc + "  (no hay " + src + " en el kit)"); return; }
    var tgt = to.indexOf("@ret:") === 0 ? to.slice(5) : to;
    if (tgt.charAt(0) !== "@" && !screens[tgt]) report.missingTarget.push(desc + "  (no hay " + tgt + " en el kit)");
    var phone = phoneOf(src), css = m.indexOf("css:") === 0;
    var root = opt.in ? phone.querySelector(opt.in) : (css ? phone : topLayer(phone));
    var els = [];
    if (root) {
      if (css) { var q = root.querySelectorAll(m.slice(4)); els = opt.all ? Array.prototype.slice.call(q) : (q[0] ? [q[0]] : []); }
      else { var e = byText(root, m); if (e) els = [e]; }
    }
    if (!els.length) { report.unmatched.push(desc); return; }
    if (els.some(function (e) { return e.closest("[aria-disabled]"); })) { report.conflicts.push(desc + "  (el kit lo dibuja desactivado)"); return; }
    var fresh = 0;
    els.forEach(function (e) { var h = climb(e, phone); if (wire(h, to, m)) { h.dataset.ptM = m; fresh++; } });
    if (fresh) report.matched++; else report.conflicts.push(desc + "  (ese elemento ya tenía otro enlace)");
  });
  Object.keys(CFG.worlds).forEach(function (w) {  // a world's links: each overrides a LINKS hotspot by its matcher (20 oct: A3's "Ahora no")
    var wl = CFG.worlds[w].links || {};
    Object.keys(wl).forEach(function (src) {
      wl[src].forEach(function (o) {
        report.specs++;
        var desc = "[" + w + "] " + src + " · " + o[0] + " → " + o[1];
        var hit = !!screens[src] && Array.prototype.some.call(phoneOf(src).querySelectorAll(".pt-hot"), function (e) { return e.dataset.ptM === o[0]; });
        if (hit) report.matched++; else report.unmatched.push(desc + "  (no hay ese enlace en LINKS)");
      });
    });
  });
  Object.keys(screens).forEach(function (id) {
    if (isHost(id)) return;
    var phone = phoneOf(id);
    CFG.globals.forEach(function (g) {
      each(phone.querySelectorAll(g[0]), function (e) {
        if (!covered(e, phone)) wire(climb(e, phone), g[1], g[0]);
      });
    });
    each(phone.querySelectorAll(".nav .tab:not(.on)"), function (t) {
      var to = CFG.tabs[norm(t.textContent)];
      if (to && !covered(t, phone)) wire(t, to, "pestaña");
    });
  });
  Object.keys(screens).forEach(function (id) {  // a chevron promises a door: list the ones left without a link (info for --check)
    var phone = phoneOf(id);
    each(phone.querySelectorAll(".chev"), function (c) {
      if (c.closest(".pt-hot") || covered(c, phone)) return;
      report.silent.push(id + " · " + norm((c.closest(CFG.tappable) || c.parentNode).textContent).slice(0, 60));
    });
  });

  // ---------- one moment per task: each frame's own status bar and the sheets' own backdrop, kept before anything changes ----------
  function barOf(ph) {  // the clock, the battery fill and the battery percentage of a frame's status bar
    var sp = ph.querySelectorAll(".statusbar > span"); if (sp.length < 2) return null;
    var first = sp[0].firstChild, last = sp[1].lastChild;  // text nodes: C13 sets Kompara's K after the clock, inside the same span
    return { time: first && first.nodeType === 3 ? first : null, fill: sp[1].querySelector('svg[viewBox="0 0 16 10"] rect:nth-of-type(2)'), pct: last && last.nodeType === 3 ? last : null };
  }
  var clock0 = {}, isSheet = {}, backdrop0 = {}, sub0 = {}, top0 = {};
  Object.keys(screens).forEach(function (id) {
    var ph = phoneOf(id), b = barOf(ph);
    if (b && b.time) clock0[id] = { t: b.time.nodeValue.trim(), f: b.fill && b.fill.getAttribute("width"), p: b.pct && b.pct.nodeValue };
    isSheet[id] = !!ph.querySelector(".dim");
  });
  LIVE.backdrop.forEach(function (id) { if (screens[id]) backdrop0[id] = phoneOf(id).querySelector(".screen"); });
  var pad0 = {}, day0 = {};  // the offer details' three rows that share the slack (the two figures and the day row) with their padding, and their day row (LIVE.dayRow) as drawn
  LIVE.offers.forEach(function (id) {  // the offer details' one comparison line and their own opening
    var sh = screens[id] && phoneOf(id).querySelector(".sheet"), sub = sh && sh.querySelector(".hero .sub");
    if (sub) { sub0[id] = sub.innerHTML; top0[id] = sh.style.top; }
    var rows = sh ? [].slice.call(sh.querySelectorAll(":scope > .kv, :scope > .rows:not(.lined) > .row")) : [];
    if (rows.length === 3) pad0[id] = { rows: rows, p: parseFloat(rows[0].style.paddingTop) };
    var t = sh && byText(sh, LIVE.dayRow), row = t && t.closest(".row"), m = row && row.querySelector(".money");
    if (m) day0[id] = { row: row, money: m, fig: m.textContent, to: row.dataset.ptTo };
  });
  var revLook = {};  // the revert's two drawings, from the kit: inert (G2b at rest) and live (G2 mid-edit)
  [["inert", "rest"], ["live", "meta"]].forEach(function (p) {
    var id = roleId(p[1]), b = id && screens[id] && phoneOf(id).querySelector(".footer .btn.text");
    if (b) revLook[p[0]] = { cls: b.className.replace(/\s*\bpt-(hot|pressed)\b/g, ""), style: b.getAttribute("style") || "", dis: b.getAttribute("aria-disabled") };
  });
  var check0 = {};  // the period pickers' check (LIVE.picker), moved to the opener's week
  (LIVE.picker || []).forEach(function (id) { var c = screens[id] && phoneOf(id).querySelector(".sheet .row > svg"); if (c) check0[id] = c; });
  var label0 = {}, title0 = {};  // the sheets whose trip can still be under way (LIVE.underway): their own label ("Te dejó · aprox.")
  // and their title's text after the mark ("Oferta de las 14:32 · La tomaste")
  Object.keys(LIVE.underway).forEach(function (id) {
    var l = screens[id] && phoneOf(id).querySelector(".sheet .hero .label"); if (l) label0[id] = l.textContent;
    var st = LIVE.underway[id].title && screens[id] && phoneOf(id).querySelector(".sheet .st"), tn = st && st.lastChild;
    if (tn && tn.nodeType === 3) title0[id] = { node: tn, text: tn.nodeValue };
  });

  // ---------- worlds: another moment's navigation (task 7's morning) ----------
  var world = null;
  var granted = false;  // this attempt gave the permission back (20 oct: landed on B2r); cleared by a task start and by the wait
  var own = function (o, k) { return !!o && Object.prototype.hasOwnProperty.call(o, k); };
  function worldOf(id) { for (var w in CFG.worlds) if (CFG.worlds[w].enter.indexOf(id) !== -1) return w; return null; }
  function asGranted(id) {  // once granted, the world's off-state screens are drawn as their granted counterparts (20 oct: B2 → B2r)
    var g = granted && world && CFG.worlds[world].granted;
    return g && own(g.map, id) ? g.map[id] : id;
  }
  function resolve(to) {  // a target as the current world draws it (20 oct: Hoy is B2, Ajustes G1b, the 19-oct screens the note)
    var m = world && CFG.worlds[world].map;
    return asGranted(own(m, to) ? m[to] : to);
  }
  function target(hot) {  // a hotspot's target, as the current world's links override it (20 oct: A3's "Ahora no" returns to B2)
    var o = world && CFG.worlds[world].links, l = o && o[cur], m = hot.dataset.ptM;
    if (l && m) for (var i = 0; i < l.length; i++) if (l[i][0] === m) return l[i][1];
    return hot.dataset.ptTo;
  }

  // ---------- live limits (cents): saved = what Guardar kept; draft = the pair being edited on G2 / G2c ----------
  var DEF = { meta: Math.round(LIVE.limits.meta * 100), min: Math.round(LIVE.limits.min * 100) }, STEP = LIVE.step;
  var saved = loadLimits(), draft = null;  // draft: {meta, min, last: the field last changed, reset: Volver a lo inicial put the fixture's pair}
  var m2 = function (c) { return (c / 100).toFixed(2); };                // 650 → "6.50"
  var short = function (v) { return v.meta % 100 || v.min % 100 ? m2 : function (c) { return String(c / 100); }; };  // "8" while both are whole, both with cents once either has them
  function doorPair(v) {  // Hoy's door: "tu meta $8 · tu mínimo $6", or "tu meta $8.00 · tu mínimo $6.50"
    var f = short(v);
    return "tu meta $" + f(v.meta) + " · tu mínimo $" + f(v.min);
  }
  function loadLimits() {
    var v = null;
    try { v = JSON.parse(localStorage.getItem(LKEY) || "null"); } catch (e) { v = null; }
    var ok = v && typeof v.meta === "number" && typeof v.min === "number" && v.min >= 0 && v.meta > v.min && v.meta <= 9999;
    return ok ? { meta: Math.round(v.meta), min: Math.round(v.min) } : { meta: DEF.meta, min: DEF.min };
  }
  function keep(v, why) {
    saved = { meta: v.meta, min: v.min }; draft = null;
    if (recorder()) { try { localStorage.setItem(LKEY, JSON.stringify(saved)); } catch (e) { /* private window: keep in memory */ } }
    if (why) logEvent({ kind: why, meta: m2(saved.meta), min: m2(saved.min) });
    announce();
  }
  function roleId(role) { for (var id in LIVE.editors) if (LIVE.editors[id] === role) return id; return null; }
  var LBL = { meta: "Tu meta", min: "Tu mínimo" };
  var ERR = { min: "Tu mínimo debe ser menor que tu meta", meta: "Tu meta debe ser mayor que tu mínimo" };  // G2d: said on Guardar, never while typing
  var pair = function () { return draft || { meta: saved.meta, min: saved.min }; };
  var shown = function (id) { return (LIVE.editors[id] === "rest" ? null : draft) || saved; };  // G2b shows the saved pair, G2 / G2c the draft
  var dirty = function () { return !!LIVE.editors[cur] && !!draft && (draft.meta !== saved.meta || draft.min !== saved.min); };
  function edited(which) {  // the draft changed: G2b (at rest) moves to G2 (tu meta) or G2c (tu mínimo), a focused field with it
    if (LIVE.editors[cur] !== "rest") { renderEditor(cur); return; }
    var e = entry; entry = null;
    swap(roleId(which), "cambio");
    if (e) { entry = e; paintFields(cur); }
  }
  function step(which, dir) {  // a ring: the next mark of the $0.50 grid in the direction pressed ($6.30 → + → $6.50; − → $6.00), from the typed figure when its field is focused
    var v = pair(), base = v[which];
    if (entry && entry.which === which && !isNaN(cents(entry.text))) base = cents(entry.text);
    var c = dir > 0 ? Math.floor(base / STEP) * STEP + STEP : Math.ceil(base / STEP) * STEP - STEP;
    if (c < 0 || c > 9999) return;  // nothing under $0.00 or past $99.99
    v[which] = c; v.last = which; v.reset = false; draft = v; fieldErr = null;
    if (entry && entry.which === which) { entry.text = m2(c); entry.fresh = true; entry.err = false; }
    logEvent({ kind: "paso", which: which, value: m2(c) });
    edited(which);
  }
  var DOOR = /^tu meta \$[\d.]+ · tu mínimo \$[\d.]+$/, PAIR = /^\$[\d.]+\/km · \$[\d.]+\/km$/;
  var restClock = null, restRun = 0;  // outside a task (driver's window): the next task's moment, and this wait's own history key
  var taskClock = function () {
    if (store.active) return (typeof store.active.clock === "object" && store.active.clock) || null;
    return !FAC || mirroring() ? restClock : null;
  };
  function setBar(ph, c) {  // a status bar's clock, battery fill and percentage
    var b = barOf(ph);
    if (b && b.time && c) { b.time.nodeValue = c.t; if (b.fill && c.f) b.fill.setAttribute("width", c.f); if (b.pct && c.p) b.pct.nodeValue = c.p; }
  }
  function paintRegla(rg, v, f) {  // a regla's two limit labels and its band for the example offer ($9.49): ▲ arriba, ◆ cumple, ▼ abajo
    rg.querySelector(".rg-l8 b").textContent = "$" + f(v.meta);
    rg.querySelector(".rg-l6 b").textContent = "$" + f(v.min);
    var rate = Math.round(parseFloat(rg.querySelector(".rg-n").textContent.match(/\d+(\.\d+)?/)[0]) * 100);
    var st = rate >= v.meta ? "v" : rate >= v.min ? "y" : "r", S = LIVE.regla[st];
    rg.classList.remove("v", "y", "r"); rg.classList.add(st);
    rg.querySelector(".rg-a path").setAttribute("d", S[0]);
    rg.querySelector(".rg-m path").setAttribute("d", S[1]);
    rg.querySelector(".rg-p").textContent = S[2];
    rg.querySelector(".rg-band").setAttribute("aria-label", S[2] + ": " + m2(rate) + " pesos netos por km");
    fitRegla(rg);
  }
  function fitRegla(rg) {  // the two limit lines start 12dp right of the wider label, as the kit draws them ("$6" at 104, "$6.00" at 123), never left of the kit's own start
    if (rg.dataset.ptX0 === undefined) rg.dataset.ptX0 = parseFloat(getComputedStyle(rg).getPropertyValue("--rg-x")) || 104;
    var R = rg.getBoundingClientRect(); if (!R.width) return;  // hidden: measured when shown
    var s = parseFloat(getComputedStyle(rg).getPropertyValue("--s")) || 1, k = R.width / rg.offsetWidth || 1, right = 0;
    each(rg.querySelectorAll(".rg-ln"), function (l) {
      var r = document.createRange(); r.setStart(l, 0); r.setEndAfter(l.querySelector("b"));
      right = Math.max(right, (r.getBoundingClientRect().right - R.left) / k / s);
    });
    rg.style.setProperty("--rg-x", Math.max(+rg.dataset.ptX0, Math.ceil(right + 12)) + "px");
  }
  function paint(id) {  // the task's status bar (clock and battery) and the saved pair on every "tu meta · tu mínimo" line
    var ph = phoneOf(id);
    setBar(ph, taskClock() || clock0[id]);
    each(ph.querySelectorAll(".row.door .s"), function (s) {  // Hoy's door
      if (DOOR.test(norm(s.textContent))) s.textContent = doorPair(saved);
    });
    each(ph.querySelectorAll(".row .m"), function (m) {  // Semáforo's and Ajustes' limits row
      var a = m.querySelector(".t"), s = m.querySelector(".s");
      if (a && s && norm(a.textContent) === "Tu meta y tu mínimo" && PAIR.test(norm(s.textContent)))
        s.textContent = "$" + m2(saved.meta) + "/km · $" + m2(saved.min) + "/km";
    });
    each(ph.querySelectorAll(".row .m > .t"), function (t) {  // B3's first-run row, "Tu meta $8.00/km · tu mínimo $6.00/km" over "Valores iniciales"
      var ms = t.querySelectorAll(".money");
      if (ms.length !== 2 || !/^Tu meta \$[\d.]+\/km · tu mínimo \$[\d.]+\/km$/.test(norm(t.textContent))) return;
      ms[0].textContent = "$" + m2(saved.meta) + "/km"; ms[1].textContent = "$" + m2(saved.min) + "/km";
      var s = t.parentNode.querySelector(".s");
      if (s) s.style.display = saved.meta === DEF.meta && saved.min === DEF.min ? "" : "none";  // once saved, no longer the initial values
    });
    var ex = ph.querySelector(".sample .regla");  // B3's example chip: the saved pair's labels ("tu meta $8" / "tu mínimo $6.50" in the door's short form) and its band
    if (ex) paintRegla(ex, saved, short(saved));
  }
  function paintOffer(id) {  // the limits in force when the offer arrived (the fixture's); " a esa hora" once the limit the line NAMES
    // differs (Round 24d: "tu meta" on D2 / D2e, "tu mínimo" on D2b / D2c / D2d); the day row and the opening follow the screen behind
    // (LIVE.dayRow, LIVE.offerTops)
    var sh = phoneOf(id).querySelector(".sheet"), sub = sh.querySelector(".hero .sub"), from = openerOf();
    var named = /tu mínimo/.test(sub0[id]) ? "min" : /tu meta/.test(sub0[id]) ? "meta" : null;
    var changed = named ? saved[named] !== DEF[named] : saved.meta !== DEF.meta || saved.min !== DEF.min;
    sub.innerHTML = sub0[id] + (changed ? " a esa hora" : "");
    var d = day0[id];
    if (d) {  // the opener's hero figure ($548 at 14:40, $1,026 at 21:10) and what its basis line opens (D3a / D3; none drawn: the note)
      var o = from && phoneOf(from), hv = o && o.querySelector(".hero .value"), fig = hv ? norm(hv.textContent) : "", b = o && o.querySelector(".hero .sub2");
      var mine = /^\$[\d,]+$/.test(fig);
      d.money.textContent = mine ? fig : d.fig;
      d.row.dataset.ptTo = mine ? (b && b.dataset.ptTo) || "@toast:" + CFG.notHere : d.to;
    }
    fitOffer(id, from);
    if (document.fonts && document.fonts.status === "loading")  // a face first used here (the money 600) measures on its fallback: fit again once it is in
      document.fonts.ready.then(function () { if (cur === id) fitOffer(id, openerOf()); });
  }
  function fitOffer(id, from) {  // the lowest clean line of LIVE.offerTops where the detail fits, its three rows' padding sharing the slack
    var sh = phoneOf(id).querySelector(".sheet"), T0 = parseFloat(top0[id]), g = pad0[id];
    var tops = (LIVE.offerTops[from] || LIVE.offerTops["default"]).filter(function (t) { return t <= T0; });
    if (!tops.length) tops = [T0];
    var ph = phoneOf(id), k = ph.getBoundingClientRect().width / ph.offsetWidth || 1;  // layout dp under the fit's scale
    for (var i = 0; i < tops.length; i++) {
      sh.style.top = tops[i] + "px";
      if (!g) break;
      padRows(g, g.p); sh.scrollTop = 0;
      var R = sh.getBoundingClientRect(), need = (sh.lastElementChild.getBoundingClientRect().bottom - R.top) / k + parseFloat(getComputedStyle(sh).paddingBottom);
      var p = Math.floor((g.p + (R.height / k - need) / (2 * g.rows.length)) * 10) / 10;
      if (p >= LIVE.offerPad || i === tops.length - 1) { padRows(g, Math.max(p, LIVE.offerPad)); break; }
    }
  }
  function padRows(g, p) { g.rows.forEach(function (r) { r.style.paddingTop = r.style.paddingBottom = p + "px"; }); }
  function openerOf() {  // the screen a sheet sits over: the nearest plain screen in history
    for (var i = hist.length - 1; i >= 0; i--) if (screens[hist[i]] && !isSheet[hist[i]] && !isHost(hist[i])) return hist[i];
    return null;
  }
  var mins = function (t) { var p = String(t).split(":"); return +p[0] * 60 + +p[1]; };
  function paintUnderway(id) {  // at the clock this sheet shows (the task's, else its opener's) the trip is under way: "Te deja", not "Te dejó",
    // and the title says so ("Oferta de las 14:32 · en curso", Round 24c)
    var u = LIVE.underway[id], ph = phoneOf(id), l = ph.querySelector(".sheet .hero .label"), b = barOf(ph), tt = title0[id];
    if (!l || !b || !b.time) return;
    var t = mins(b.time.nodeValue.trim()), on = t >= mins(u.from) && t < mins(u.until);
    l.textContent = on ? u.label : label0[id];
    if (tt) tt.node.nodeValue = on ? u.title : tt.text;
  }
  var okPair = null;  // the last valid pair la regla drew: it holds while a typed figure is empty or crosses the other limit (G2d)
  function renderEditor(id) {  // la regla against the pair the editor shows (else the last valid one), then both fields (paintFields)
    var v = shown(id), bad = v.min >= v.meta || (!!entry && id === cur && isNaN(cents(entry.text)));
    if (!bad || !okPair) okPair = { meta: v.meta, min: v.min };
    var rg = phoneOf(id).querySelector(".stage .regla"); if (rg) paintRegla(rg, okPair, m2);
    paintFields(id);
  }
  function paintFields(id) {  // both fields; the focused one as G2d draws it (outline, selection or caret, helper, keypad), or the one Guardar refused
    var ph = phoneOf(id), on = !!entry && id === cur, v = shown(id), L = CFG.live2;
    ["meta", "min"].forEach(function (w) {
      var card = ph.querySelector('.limit[aria-label="' + LBL[w] + '"]'); if (!card) return;
      var lf = card.querySelector(".lf"), fig = lf && lf.querySelector(".money > span"), help = card.querySelector(".lf-help");
      var focus = on && entry.which === w, err = focus ? entry.err : fieldErr === w && id === cur;
      card.classList.toggle("focused", focus || err);
      if (lf) lf.classList.toggle("focus", focus);
      if (fig) {
        fig.textContent = focus ? entry.text : m2(v[w]);
        fig.classList.toggle("sel", focus && entry.fresh && entry.text !== "");  // the figure alone selected: the first key replaces it
        if (focus && !entry.fresh) { var k = document.createElement("i"); k.className = "pt-caret"; fig.appendChild(k); }
      }
      if (!focus && !err) { if (help) help.parentNode.removeChild(help); return; }
      if (!help) { help = document.createElement("div"); help.className = "lf-help"; card.appendChild(help); }
      help.classList.toggle("pt-err", !!err);
      if (err) help.innerHTML = L.glyph + "<span>" + ERR[w] + "</span>";
      else help.textContent = w === "meta" ? "Mayor que tu mínimo ($" + m2(v.min) + "/km)" : "Menor que tu meta ($" + m2(v.meta) + "/km)";
    });
    var scr = ph.querySelector(".screen"), foot = scr && scr.querySelector(".footer"), line = ph.querySelector(".limit-step");
    if (line) line.style.display = on ? "none" : "";                      // the focused field's helper takes its place
    var alt = foot && foot.querySelector(".btn.text");
    if (alt) { paintRevert(alt, id); alt.style.display = on ? "none" : ""; }  // Volver a lo inicial waits for the keypad (its slot is kept at rest, so Guardar never moves)
    if (foot) foot.style.paddingBottom = on ? L.footPad : "";
    if (scr) scr.style.bottom = on ? L.imeH : "";                          // the screen resizes above the keypad: Guardar stays in sight
    var ime = ph.querySelector(".pt-ime");
    if (on && !ime) {
      var box = document.createElement("div"); box.innerHTML = L.ime; ime = box.firstElementChild; ime.classList.add("pt-ime");
      each(ime.children, function (k) { k.dataset.ptK = k.querySelector("svg") ? "borrar" : norm(k.textContent); });
      ph.appendChild(ime);
    } else if (!on && ime) ime.parentNode.removeChild(ime);
    if (on && scr) {  // the content scrolls so the focused card and its helper end on the footer's edge (G2d: 56dp for tu mínimo)
      var c = scr.querySelector(".content"), cd = ph.querySelector('.limit[aria-label="' + LBL[entry.which] + '"]');
      var kk = ph.getBoundingClientRect().width / ph.offsetWidth || 1, d = (cd.getBoundingClientRect().bottom - c.getBoundingClientRect().bottom) / kk;
      if (c && d > 0.5) c.scrollTop += Math.ceil(d);
    }
  }
  function paintRevert(alt, id) {  // "Volver a lo inicial": live (G2's muted text button) whenever the pair the editor shows differs from
    // $8.00/$6.00 — on G2b too, once a save moved it — and inert at the initial pair (G2b's ink-3 drawing, no link)
    var v = shown(id), live = v.meta !== DEF.meta || v.min !== DEF.min, L = revLook[live ? "live" : "inert"];
    if (!L) return;
    alt.className = L.cls; alt.setAttribute("style", L.style);
    if (L.dis) alt.setAttribute("aria-disabled", L.dis); else alt.removeAttribute("aria-disabled");
    if (live) { alt.dataset.ptTo = "@reset"; alt.classList.add("pt-hot"); } else alt.removeAttribute("data-pt-to");
  }
  function setBackdrop(id) {  // a sheet sits over the screen that opened it (the nearest plain screen in history), under its status bar
    var ph = phoneOf(id), now = ph.querySelector(".screen"), from = null, next = backdrop0[id];
    for (var i = hist.length - 1; i >= 0 && !from; i--) if (screens[hist[i]] && !isSheet[hist[i]] && !isHost(hist[i])) from = hist[i];
    if (from) {
      paint(from);
      next = phoneOf(from).querySelector(".screen").cloneNode(true);
      each(next.querySelectorAll(".pt-hot"), function (e) { e.classList.remove("pt-hot", "pt-pressed"); e.removeAttribute("data-pt-to"); e.removeAttribute("title"); });
      var c0 = phoneOf(from).querySelector(".content"), c1 = next.querySelector(".content");
      if (c0 && c1) c1.dataset.ptScroll = scrollMem[from] != null ? scrollMem[from] : c0.scrollTop;  // the opener as it was scrolled (it is hidden by now, so read the remembered scroll)
      var sh = ph.querySelector(".sheet");  // Cómo se calculó · hoy opens flush under the opener's first offer row: over Hoy that row ends at 400, not 458
      if (sh && id === "D3a") sh.style.top = (["B1", "B7", "B9"].indexOf(from) !== -1 ? 400 : 458) + "px";
      setBar(ph, taskClock() || clock0[from]);  // outside a task the opener's clock and battery, not the sheet's own
    }
    if (next !== now) ph.replaceChild(next, now);
    if (id === "D3b") {  // over the live E3 the sheet opens flush under the row above "Por semana" (its rule), so Por semana and IMSS are covered whole
      var sh3 = ph.querySelector(".sheet"), rows = [].filter.call(next.querySelectorAll(".content *"), function (e) { return /^\s*Por semana/.test(e.textContent) && e.children.length <= 3; });
      var pr3 = ph.getBoundingClientRect(), k3 = pr3.height / ph.offsetHeight || 1;
      if (sh3 && rows.length) { var rr = rows[0].closest(".row, .obs, .kv, a, div").getBoundingClientRect(); sh3.style.top = Math.round((rr.top - pr3.top) / k3) + "px"; }
    }
    var c = next.querySelector(".content");
    if (c && c.dataset.ptScroll) c.scrollTop = +c.dataset.ptScroll;
  }
  function decorate(id) {
    var role = LIVE.editors[id];
    if (!role || role === "rest") draft = null;
    paint(id);
    if (role) renderEditor(id);
    if (backdrop0[id]) setBackdrop(id);
    if (label0[id] !== undefined) paintUnderway(id);
    if (sub0[id] !== undefined) paintOffer(id);
    if (check0[id]) paintPicker(id);
  }
  function paintPicker(id) {  // E6: the opener's week (its label up to " · ") in ink 600 with the check, the rest plain
    var from = null;
    for (var i = hist.length - 1; i >= 0 && !from; i--) if (screens[hist[i]] && !isSheet[hist[i]] && !isHost(hist[i])) from = hist[i];
    var lab = from && phoneOf(from).querySelector(".period > span"); if (!lab) return;
    var want = norm(lab.textContent).replace(/\s*▾$/, "").split(" · ")[0];
    each(phoneOf(id).querySelectorAll(".sheet .row"), function (r) {
      var t = r.querySelector(".m > .t"); if (!t) return;
      var on = norm(t.textContent).split(" · ")[0] === want;
      t.style.fontWeight = on ? "600" : "";
      if (on) r.appendChild(check0[id]);
    });
  }

  // ---------- typing in place (G2d): a tap on a field focuses it on the current editor, the figure selected; Guardar is the only confirm ----------
  var entry = null, fieldErr = null;  // entry: {which, text, fresh, err} · fieldErr: the field Guardar refused (its helper is the error until the next change)
  var cents = function (t) { return t === "" || t === "." ? NaN : Math.round(parseFloat(t) * 100); };  // "6." → 600, ".5" → 50
  function focusField(which) {
    if (entry && entry.which === which) return;  // a tap on the focused field keeps what is typed
    entry = { which: which, text: m2(shown(cur)[which]), fresh: true, err: false }; fieldErr = null;
    paintFields(cur);
    logEvent({ kind: "cifra abierta", which: which });
  }
  function closeEntry() { if (entry) { entry = null; paintFields(cur); } }  // the keypad closes; what was typed stays in the draft, unsaved
  function entryKey(k) {  // k: "borrar" | "." | a digit: two digits before the point and two after ($99.99 at most); la regla redraws (or holds), nothing is judged
    if (!entry || (k !== "borrar" && k !== "." && !/^\d$/.test(k))) return;
    var t = entry.fresh ? "" : entry.text, dot = t.indexOf(".");  // the selected figure: the first key replaces it
    if (k === "borrar") t = t.slice(0, -1);
    else if (k === ".") { if (dot === -1) t = (t || "0") + "."; }
    else if (dot !== -1) { if (t.length - dot - 1 < 2) t += k; }
    else if (t === "0") t = k;
    else if (t.length < 2) t += k;
    entry.text = t; entry.fresh = false; entry.err = false; fieldErr = null;
    var c = cents(t), v = pair(), w = entry.which;
    if (!isNaN(c) && c !== v[w]) { v[w] = c; v.last = w; v.reset = false; draft = v; edited(w); }
    else paintFields(cur);
  }
  function savedMsg(prev, v) {  // G4i's snackbar, naming what changed
    if (v.reset && v.meta === DEF.meta && v.min === DEF.min) return "Guardado: volviste a $" + m2(DEF.meta) + "/km y $" + m2(DEF.min) + "/km";
    var dm = v.meta !== prev.meta, dn = v.min !== prev.min;
    if (dm && !dn) return "Guardado: tu meta ahora es $" + m2(v.meta) + "/km";
    if (dn && !dm) return "Guardado: tu mínimo ahora es $" + m2(v.min) + "/km";
    return "Guardado: tu meta $" + m2(v.meta) + "/km · tu mínimo $" + m2(v.min) + "/km";
  }
  function saveEditor() {  // Guardar: inert at rest or on an empty field; tu mínimo strictly under tu meta, else the field's error (G2d)
    if (LIVE.editors[cur] === "rest" || (entry && isNaN(cents(entry.text)))) return;
    var v = pair();
    if (v.min >= v.meta) {
      var w = entry ? entry.which : v.last || "min";
      if (entry) entry.err = true; else fieldErr = w;
      paintFields(cur);
      logEvent({ kind: "guardar rechazado", which: w, meta: m2(v.meta), min: m2(v.min) });
      return;
    }
    var msg = savedMsg(saved, v);
    keep(v, "guardar");
    leave(msg, "guardar y volver");
  }

  // ---------- G2e: back with an unsaved change asks; G4i: the snackbar on the opener after Guardar ----------
  var ask = null, snackEl = null, snackTimer = null, snackNext = null;
  function openAsk() {  // G2e's scrim and dialog over the edited screen, its body the change ("Tu mínimo: $6.50/km", both on two lines)
    closeAsk();
    var v = pair(), lines = [], box = document.createElement("div");
    if (v.meta !== saved.meta) lines.push("Tu meta: $" + m2(v.meta) + "/km");
    if (v.min !== saved.min) lines.push("Tu mínimo: $" + m2(v.min) + "/km");
    box.className = "pt-ask"; box.innerHTML = CFG.live2.ask;
    var dlg = box.querySelector(".dialog"), acts = dlg.querySelectorAll(".da > span"), db = dlg.querySelector(".db");
    box.querySelector(".dim").dataset.ptA = "fuera"; acts[0].dataset.ptA = "no guardar"; acts[acts.length - 1].dataset.ptA = "guardar";
    dlg.setAttribute("role", "dialog"); dlg.setAttribute("aria-label", norm(dlg.querySelector(".dt").textContent));
    phoneOf(cur).appendChild(box); ask = box;
    var h0 = dlg.offsetHeight, top = parseFloat(dlg.style.top);  // the kit's one-line body, where G2e draws it (the free band over Guardar)
    if (lines.length) db.innerHTML = lines.map(esc).join("<br>");
    var h = dlg.offsetHeight;
    if (h > h0 && !isNaN(top)) {  // a two-line body: over the whole "Toca la cifra para escribirla." line when it still clears Guardar, else grown upward
      var ph = phoneOf(cur), pr = ph.getBoundingClientRect(), k = pr.width / ph.offsetWidth || 1;
      var at = function (el) { return el && el.offsetParent ? (el.getBoundingClientRect().top - pr.top) / k : NaN; };
      var lt = at(ph.querySelector(".limit-step")), bt = at(ph.querySelector(".footer .btn"));
      dlg.style.top = (lt < top && lt + h <= bt - 8 ? lt : top - (h - h0)) + "px";
    }
    logEvent({ kind: "¿guardar el cambio?", text: lines.join(" · ") });
  }
  function closeAsk() { if (ask) { ask.parentNode.removeChild(ask); ask = null; } }
  function askDo(a) {  // "No guardar" discards and goes back · "Guardar" saves and goes back · the scrim keeps the editor, change and all
    closeAsk();
    if (a === "no guardar") { logEvent({ kind: "no guardar" }); leave(null, "no guardar"); }
    else if (a === "guardar") saveEditor();
  }
  function held() {  // the system back's stops on the editor: the dialog closes, then the keypad; an unsaved change asks (G2e)
    if (ask) { closeAsk(); return true; }
    if (entry) { closeEntry(); return true; }
    if (dirty()) { openAsk(); return true; }
    return false;
  }
  function snack(msg) {  // G4i's .toast, 4 s, over whatever screen is shown
    unsnack();
    var box = document.createElement("div"); box.innerHTML = CFG.live2.snack;
    var el = box.firstElementChild; el.replaceChild(document.createTextNode(msg), el.lastChild);
    el.classList.add("pt-snack"); el.dataset.msg = msg;
    phoneOf(cur).appendChild(el); snackEl = el;
    placeSnack(el);
    snackTimer = setTimeout(function () { unsnack(); announce(); }, 4000);
    logEvent({ kind: "aviso guardado", text: msg });
  }
  function placeSnack(el) {  // 8dp over the opener's footer primary when it has one (B3's "Abrir Uber para probar"; Semáforo's footer has only
    // "Pausar el semáforo hasta mañana", which counts, as G4i draws it), else 16dp over the bottom (Ajustes); where that would cover a row
    // or a button, it rises to the first band clear of them by 8dp (an offer detail: over its three rows), else it stays put
    var ph = phoneOf(cur), pr = ph.getBoundingClientRect(), k = pr.width / ph.offsetWidth || 1, H = ph.offsetHeight, h = el.offsetHeight;
    var y = function (v) { return (v - pr.top) / k; }, layer = topLayer(ph);
    var prim = layer.querySelector(".footer .btn"), vis = function (e) { return !!e && !!e.offsetParent && e.getBoundingClientRect().height > 0; };
    var bot = vis(prim) ? y(prim.getBoundingClientRect().top) - 8 : H - 16;   // the snackbar's bottom edge, in the phone's dp
    var bar = ph.querySelector(".screen > .top"), ceil = vis(bar) ? y(bar.getBoundingClientRect().bottom) : 44;
    var hot = [];
    each(layer.querySelectorAll(".pt-hot"), function (e) {
      if (!vis(e) || e.matches(".statusbar, .dim, .grab")) return;
      var r = e.getBoundingClientRect(), t = r.top, b = r.bottom, sc = e.closest(".content, .sheet");
      if (sc && sc !== e) { var q = sc.getBoundingClientRect(); t = Math.max(t, q.top); b = Math.min(b, q.bottom); }
      if (b - t > 1) hot.push([y(t) - 8, y(b) + 8]);
    });
    var hit = function (b) { return hot.some(function (r) { return b > r[0] && b - h < r[1]; }); };
    if (layer !== ph && layer.classList.contains("sheet")) { el.style.bottom = "16px"; return; }  // over a sheet there is no clear band: 16dp over the bottom, as G4i's rule says
    for (var b = bot; b - h >= ceil && hit(b); b--) { /* rise past the rows */ }
    el.style.bottom = Math.round(H - (b - h >= ceil ? b : bot)) + "px";
  }
  function unsnack() { clearTimeout(snackTimer); if (snackEl) { snackEl.parentNode.removeChild(snackEl); snackEl = null; } }
  function hush() {  // the editor's live states leave the screen they were drawn on
    closeAsk(); unsnack();
    if (cur && screens[cur] && LIVE.editors[cur]) { entry = null; fieldErr = null; paintFields(cur); }
  }
  function leave(msg, why) {  // the editor closes BACK to the screen that opened it (history), under the snackbar when something was saved
    hush(); draft = null;
    if (!hist.length) { swap(roleId("rest"), why); if (msg) snack(msg); return; }
    snackNext = msg || null;
    back(why);
  }

  // ---------- keyboard: the number entry types like the keypad; N / H / A drive the session ----------
  document.addEventListener("keydown", function (e) {
    if (e.metaKey || e.ctrlKey || e.altKey || /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
    var lower = e.key.length === 1 ? e.key.toLowerCase() : "";
    if (lower === "n" || lower === "h" || lower === "a") {
      e.preventDefault();
      if (lower === "n") { if (!store.active) command("iniciar", store.next || first()); else flash("Termina la tarea " + store.active.task + " con H o A"); }
      else if (store.active) command(lower === "h" ? "hecho" : "atascado");
      return;
    }
    if (mirroring() || !LIVE.editors[cur]) return;
    if (ask) { if (e.key === "Escape") { e.preventDefault(); tap({ screen: cur, hit: true, to: "@pregunta:fuera", label: "[¿guardar?] tecla Escape" }); closeAsk(); } return; }
    if (!entry) return;
    var k = { Enter: "guardar", Escape: "cerrar", Backspace: "borrar", ",": "." }[e.key] || e.key;
    if (!/^(guardar|cerrar|borrar|\.|\d)$/.test(k)) return;
    e.preventDefault();
    if (k === "guardar" || k === "cerrar") tap({ screen: cur, hit: true, to: "@cifra:" + k, label: "[cifra] tecla " + e.key });
    if (k === "guardar") saveEditor(); else if (k === "cerrar") closeEntry(); else entryKey(k);
  });

  // ---------- actions: targets that start with "@" and are not navigation ----------
  function act(to) {
    var p = to.split(":");
    if (p[0] === "@toast") { toast(to.slice(7)); logEvent({ kind: "aviso", text: to.slice(7) }); return; }
    if (p[0] === "@lim") { step(p[1], p[2] === "+" ? 1 : -1); return; }
    if (p[0] === "@entry") { focusField(p[1]); return; }
    if (p[0] === "@ret") { var r = resolve(to.slice(5)); if (isAct(r)) act(r); else ret(r, "regreso"); return; }
    if (p[0] === "@backnote") {  // back, then the note on the screen it lands on (A4b / A4r's "Abrir ajustes": Android's battery setting)
      var msg = to.slice(10);
      logEvent({ kind: "aviso", text: msg });
      if (!hist.length) { toast(msg); return; }
      noteNext = msg; back();
      return;
    }
    if (to === "@save") { saveEditor(); return; }
    if (to === "@leave") { closeEntry(); if (dirty()) openAsk(); else back(); return; }  // the editor's ←: the keypad closes, an unsaved change asks
    if (to === "@reset") {  // the fixture's pair in the editor only: the saved pair waits for Guardar (from G2b, the editor moves to G2 / G2c)
      draft = { meta: DEF.meta, min: DEF.min, last: null, reset: true }; fieldErr = null;
      logEvent({ kind: "volver a lo inicial", meta: m2(draft.meta), min: m2(draft.min) });
      edited(saved.meta !== DEF.meta ? "meta" : "min");
      return;
    }
    toast(FAC ? to + " no está definido" : CFG.notHere);
  }

  // ---------- navigation ----------
  var cur = null, hist = [], scrollMem = {};
  var store = load();
  var idEl = $("pt-id"), noteEl = $("pt-note");
  function missing(id) { toast(FAC ? id + " aún no está dibujada en el kit" : CFG.notHere); }
  function contentOf(id) { return id && screens[id] ? phoneOf(id).querySelector(".screen > .content, .content") : null; }
  var BACKWHY = /^(atrás|volver a Kompara|guardar y volver|no guardar)$/;  // the steps that go back: the list stays where it was left
  var noteNext = null;  // a note for the screen a back lands on (@backnote)
  function show(id, why, mirrored) {
    if (!mirrored) id = asGranted(id);  // a back onto B2 after the permission came back draws B2r
    if (!screens[id]) { missing(id); return; }
    hush();
    $("pt-toast").classList.remove("on");
    if (cur && screens[cur]) { var c0 = contentOf(cur); if (c0) scrollMem[cur] = c0.scrollTop; screens[cur].hidden = true; }
    cur = id;
    var w = worldOf(id); if (w) world = w;
    var gw = world && CFG.worlds[world].granted;
    if (gw && gw.by === id && !granted && !mirrored) { granted = true; if (store.active && recorder()) store.active.granted = true; }  // saved with the screen log below
    screens[id].hidden = false;
    decorate(id);
    var c1 = contentOf(id); if (c1 && !backdrop0[id]) c1.scrollTop = BACKWHY.test(why || "") ? (scrollMem[id] || 0) : 0;  // back keeps the list where it was; a sheet's backdrop keeps its opener's scroll (setBackdrop)
    if (idEl) idEl.textContent = id;
    if (noteEl) noteEl.textContent = CFG.notes[id] || "";
    $("pt-back").disabled = !hist.length || mirroring();
    renderHost();
    if (mirrored) return;
    if (store.active && recorder()) { store.active.screens.push({ id: id, t: Date.now() - store.active.t0, via: why || "toque" }); store.active.world = world; save(); }
    if (FAC) { try { var u = new URL(location.href); u.searchParams.set("screen", id); history.replaceState(null, "", u); } catch (e) { /* file:// may refuse */ } }
    if (snackNext) { var sn = snackNext; snackNext = null; snack(sn); }  // Guardar's snackbar, on the opener it went back to
    if (noteNext) { var nn = noteNext; noteNext = null; toast(nn); }  // @backnote's note, on the screen it went back to
    announce();
  }
  function go(id, why) {
    id = resolve(id);
    if (isAct(id)) { act(id); return; }
    if (id === cur) return;
    if (!screens[id]) { missing(id); return; }
    hist.push(cur); show(id, why); hPush();
  }
  function swap(id, why) { if (id === cur) { decorate(id); announce(); } else { show(id, why); hReplace(); } }  // a new state of the same screen: no history entry
  function back(why) { why = why || "atrás"; if (!hist.length || hBack(1, why)) return; show(hist.pop(), why); hReplace(); }
  function ret(to, why) {  // a stand-in's return (A3s): back past it to the screen that opened it, drawn as `to` in its place (A3 → A3n, A3 → B3);
    // a world's retAt draws it in an earlier screen's place instead (20 oct: B2r in B2's, so back never shows the off state again)
    if (!hist.length) { go(to, why); return; }
    var at = retAt(to, world), i = at ? hist.lastIndexOf(at) : -1, n = i !== -1 ? hist.length - i : 1;
    if (hBack(n, why)) { pending.then = to; return; }
    hist.length -= n; show(to, why); hReplace();
  }
  function retAt(to, w) {  // the screen whose place a return to `to` takes in world w (any world when w is undefined), else null
    for (var k in CFG.worlds) { var r = CFG.worlds[k].retAt; if ((w === undefined || k === w) && r && r[to]) return r[to]; }
    return null;
  }
  function opener() {
    var n = 0; while (n < hist.length && isHost(hist[hist.length - 1 - n])) n++;
    if (n < hist.length && hBack(n + 1, "volver a Kompara")) return;
    hist.length -= n;
    if (hist.length) { show(hist.pop(), "volver a Kompara"); hReplace(); } else go("B1", "volver a Kompara");
  }

  // ---------- the phone's back (driver's window): one browser-history entry per screen, so Android's back gesture steps back inside
  // the prototype and never leaves it (under a task's first screen it stays put); each entry carries the app's history, so a reload,
  // or a return to an entry, restores the screen over its own opener ----------
  var HIST = !FAC && !!(window.history && history.pushState);
  var runKey = function () { return store.active ? store.active.t0 : restRun; };
  function hSet(push) {
    if (!HIST || !cur) return;
    var st = { pt: 1, run: runKey(), cur: cur, hist: hist.slice(-80), world: world, granted: granted, scroll: scrollMem };  // scroll: the lists as they were left, so a reload keeps them
    try { if (push) history.pushState(st, ""); else history.replaceState(st, ""); } catch (e) { /* a sandbox may refuse: the in-app back still works */ }
  }
  function hPush() { hSet(true); }
  function hReplace() { hSet(false); }
  function ours(st) { return !!st && st.pt === 1 && !st.floor && st.run === runKey() && !!screens[st.cur] && Array.isArray(st.hist); }
  var clean = function (h) { return h.filter(function (id) { return !!screens[id]; }); };
  var pending = null;  // an in-app back or a stay-put sent through the browser's history, for the popstate that follows
  function hBack(n, why) {  // n entries back through the browser, when its current entry is the app's state; else false (the app steps itself)
    var st = HIST ? history.state : null;
    if (!ours(st) || st.cur !== cur || st.hist.length !== hist.length || n < 1 || n > hist.length) return false;
    pending = { why: why, t: Date.now(), cur: hist[hist.length - n], hist: hist.slice(0, hist.length - n) };
    history.go(-n);
    return true;
  }
  function stay() { pending = { why: "quedarse", t: Date.now() }; history.forward(); }  // back up to the entry the phone's back left
  addEventListener("popstate", function (e) {
    if (!HIST) return;
    var st = e.state, p = pending && Date.now() - pending.t < 1000 ? pending : null;
    pending = null;
    if (p && p.why === "quedarse") { hReplace(); return; }  // back up on the upper entry, which takes the app's state
    if (p && p.then) {  // a stand-in's return: the opener's entry takes the new screen
      if (ours(st)) { hist = clean(st.hist); world = st.world || world; show(p.then, p.why); hReplace(); }
      else { hist = p.hist; show(p.then, p.why); stay(); }
      return;
    }
    if (!p) {  // Android's back gesture (or the browser's own back): a tap in the log, then the app's back
      tap({ screen: cur, hit: true, to: ask ? "@pregunta:fuera" : entry ? "@cifra:cerrar" : dirty() ? "@pregunta" : "@back", label: "[teléfono] atrás", phone: true });
      if (held()) { stay(); return; }  // the dialog, then the keypad, close first; an unsaved change asks (G2e)
    }
    if (ours(st)) { restore(st, p ? p.why : st.hist.length > hist.length ? "adelante" : "atrás"); return; }
    // under the entries the app knows (the floor, another task's entry, or a history rebuilt from the log): the app steps back by
    // itself and the browser goes back up, so the phone's back never leaves the prototype; under a task's first screen it stays put
    if (p) { hist = p.hist; show(p.cur, p.why); }
    else if (hist.length) show(hist.pop(), "atrás");
    stay();
  });
  function restore(st, why) {
    var h = clean(st.hist);
    if (st.cur === cur && h.join() === hist.join()) return;
    hist = h; world = st.world || null;
    show(st.cur, why);
  }
  function replay(list) {  // the app's history rebuilt from a task's screen log (a reload that lost its history entry)
    var h = [], c = null;
    (list || []).forEach(function (x) {
      var id = x && x.id; if (!screens[id]) return;
      if (x.via === "inicio" || !c) { h = []; c = id; return; }
      if (id === c) return;
      if (x.via === "regreso") {                                            // a stand-in's return: drawn in its opener's place (or retAt's)
        var at = retAt(id), k = at ? h.lastIndexOf(at) : -1;
        if (k !== -1) h.length = k; else h.pop();
        c = id; return;
      }
      var at = /^(atrás|volver a Kompara|carga|guardar y volver|no guardar)$/.test(x.via) ? h.lastIndexOf(id) : -1;
      if (at !== -1) h.length = at;                                         // a step back: the screens above it leave
      else if (!/^(cambio|guardar|carga|atrás)$/.test(x.via)) h.push(c);    // a step in; a new state of the same screen keeps the depth
      c = id;
    });
    return c ? { cur: c, hist: h } : null;
  }
  function follow(to, why) {
    if (to === "@back") back();
    else if (to === "@opener") opener();
    else if (isAct(to)) act(to);
    else go(to, why);
  }
  function renderHost() {  // the host frames' switch back to Kompara: a top-bar control, as quiet as "← Atrás"
    var box = $("pt-host");
    box.textContent = "";
    if (!isHost(cur)) return;
    (CFG.hostBar[cur] || CFG.hostBar["default"]).forEach(function (c) {
      var b = document.createElement("button");
      b.type = "button"; b.className = "pt-b"; b.textContent = c[0];
      b.disabled = mirroring();
      b.onclick = function () {
        if (mirroring()) return;
        var to = resolve(c[1]);
        tap({ screen: cur, hit: true, to: to, label: "[barra] " + c[0], bar: true }); follow(to, "barra");
      };
      box.appendChild(b);
    });
  }
  var toastTimer = null;
  function toast(msg) {  // the neutral note, 12dp over the screen's footer when it has one (never over its primary), else at its own height
    var t = $("pt-toast"), ph = cur && screens[cur] ? phoneOf(cur) : null, f = ph && ph.querySelector(".screen .footer");
    var pr = ph && ph.getBoundingClientRect(), k = pr && pr.width / ph.offsetWidth;
    t.style.bottom = f && f.offsetParent && k ? Math.round(852 - (f.getBoundingClientRect().top - pr.top) / k + 12) + "px" : "";
    t.textContent = msg; t.classList.add("on");
    clearTimeout(toastTimer); toastTimer = setTimeout(function () { t.classList.remove("on"); }, 2200);
  }
  function flash(msg) { if (FAC) { var c = $("pt-conn"); if (c) { c.dataset.flash = msg; renderConn(); setTimeout(function () { delete c.dataset.flash; renderConn(); }, 2500); } } }
  $("pt-back").onclick = function () {  // the system back, as the phone's: the editor's dialog and keypad close first, an unsaved change asks
    if (mirroring()) return;
    tap({ screen: cur, hit: true, to: ask ? "@pregunta:fuera" : entry ? "@cifra:cerrar" : dirty() ? "@pregunta" : "@back", label: "[barra] Atrás", bar: true });
    if (!held()) back();
  };
  function pointIn(phone, cx, cy) { var r = phone.getBoundingClientRect(), k = r.width / 393; return { x: Math.round((cx - r.left) / k), y: Math.round((cy - r.top) / k) }; }
  var swallowNext = false;  // the click that ends a drag gesture is not a tap; the next press clears it
  stage.addEventListener("click", function (e) {
    if (swallowNext) { swallowNext = false; return; }
    if (mirroring()) return;
    var phone = cur && phoneOf(cur);
    if (!phone || !phone.contains(e.target)) return;
    var pt = pointIn(phone, e.clientX, e.clientY), x = pt.x, y = pt.y;
    if (ask && ask.contains(e.target)) {  // G2e's dialog: its two actions, or the scrim
      var a = e.target.closest("[data-pt-a]");
      if (!a) return;  // the dialog's text
      e.preventDefault();
      tap({ screen: cur, hit: true, to: "@pregunta:" + a.dataset.ptA, label: "[¿guardar?] " + (a.dataset.ptA === "fuera" ? "fuera del cuadro" : norm(a.textContent)), x: x, y: y });
      askDo(a.dataset.ptA);
      return;
    }
    var key = entry && e.target.closest(".pt-ime [data-pt-k]");
    if (key) {  // G2d's keypad
      e.preventDefault();
      tap({ screen: cur, hit: true, to: "@cifra:" + key.dataset.ptK, label: "[teclado] " + key.dataset.ptK, x: x, y: y });
      entryKey(key.dataset.ptK);
      return;
    }
    e.preventDefault();
    var hot = e.target.closest(".pt-hot");
    var label = hot && hot.matches(".statusbar") ? "barra de estado (notificaciones)" : norm((hot || e.target).innerText || "").slice(0, 80);
    if (!hot) { tap({ screen: cur, hit: false, label: label, x: x, y: y }); return; }
    var to = resolve(target(hot));
    tap({ screen: cur, hit: true, to: to, label: label || hot.getAttribute("aria-label") || "", x: x, y: y });
    hot.classList.add("pt-pressed");
    if (isAct(to)) { act(to); setTimeout(function () { hot.classList.remove("pt-pressed"); }, 140); return; }  // in place, no delay
    setTimeout(function () { hot.classList.remove("pt-pressed"); follow(to); }, 140);
  });

  // ---------- the shade: a drag down that starts on a tappable status bar (C13's) opens what its tap opens ----------
  var drag = null;
  function dragStart(id, cx, cy) {
    if (mirroring() || entry) return;
    var sb = cur && phoneOf(cur).querySelector(".statusbar.pt-hot"); if (!sb) return;
    var p = pointIn(phoneOf(cur), cx, cy);
    if (p.x < 0 || p.x > 393 || p.y < 0 || p.y > Math.max(24, sb.offsetHeight)) return;
    drag = { id: id, y0: p.y, to: sb.dataset.ptTo, screen: cur };
  }
  function dragMove(id, cx, cy) {
    if (!drag || drag.id !== id || drag.screen !== cur) return false;
    var p = pointIn(phoneOf(cur), cx, cy);
    if (p.y - drag.y0 < 40) return false;
    var to = resolve(drag.to);
    drag = null; swallowNext = true;
    tap({ screen: cur, hit: true, to: to, label: "arrastre hacia abajo desde la barra de estado (notificaciones)", gesture: "arrastre", x: p.x, y: p.y });
    follow(to, "arrastre");
    return true;
  }
  if (window.PointerEvent) {
    stage.addEventListener("pointerdown", function (e) { swallowNext = false; if (e.isPrimary) dragStart(e.pointerId, e.clientX, e.clientY); });
    window.addEventListener("pointermove", function (e) { if (dragMove(e.pointerId, e.clientX, e.clientY)) e.preventDefault(); });
    window.addEventListener("pointerup", function () { drag = null; });
    window.addEventListener("pointercancel", function () { drag = null; });
  } else {
    stage.addEventListener("touchstart", function (e) { var t = e.changedTouches[0]; swallowNext = false; dragStart(t.identifier, t.clientX, t.clientY); }, { passive: true });
    window.addEventListener("touchmove", function (e) { var t = e.changedTouches[0]; if (dragMove(t.identifier, t.clientX, t.clientY)) e.preventDefault(); }, { passive: false });
    window.addEventListener("touchend", function () { drag = null; });
  }

  // ---------- two windows: the driver's records; the facilitator's mirrors it and sends it orders ----------
  var peer = { seen: 0, st: null }, seenIds = {}, seenList = [], seq = 0;
  var bc = null;
  try { bc = new BroadcastChannel("kompara-prototipo"); } catch (e) { bc = null; }
  // a driver's window is open: it beats every second while in front, but a browser slows a hidden window's timers to about one
  // a minute, so the link holds for 70 s after the last beat, and a closing window says goodbye at once
  function connected() { return FAC && !peer.gone && Date.now() - peer.seen < 70000; }
  function mirroring() { return connected(); }
  function recorder() { return !connected(); }  // the driver's window always; the facilitator's only while alone
  function post(msg) {
    msg.src = ROLE; msg.ts = Date.now(); msg.id = ROLE + "-" + msg.ts + "-" + (++seq) + "-" + Math.random().toString(36).slice(2, 7);
    if (bc) { try { bc.postMessage(msg); } catch (e) { /* closed */ } }
    try { localStorage.setItem(BUS + msg.kind, JSON.stringify(msg)); } catch (e) { /* no storage: the channel alone */ }
  }
  function receive(msg) {
    if (!msg || !msg.id || seenIds[msg.id] || msg.src === ROLE) return;
    seenIds[msg.id] = 1; seenList.push(msg.id); if (seenList.length > 300) delete seenIds[seenList.shift()];
    if (FAC && msg.kind === "estado") { peer.gone = false; onState(msg); }
    else if (FAC && msg.kind === "adios") { peer.gone = true; $("pt-back").disabled = !hist.length; renderHost(); render(); }
    else if (!FAC && msg.kind === "orden") run(msg.cmd, msg.arg);
  }
  if (bc) bc.onmessage = function (e) { receive(e.data); };
  window.addEventListener("storage", function (e) {
    if (!e.key || e.key.indexOf(BUS) !== 0 || !e.newValue) return;
    try { receive(JSON.parse(e.newValue)); } catch (err) { /* not ours */ }
  });
  var annTimer = null;
  function announce() {  // the driver's window tells the facilitator's where it is (batched; also every second)
    if (FAC || annTimer) return;
    annTimer = setTimeout(function () { annTimer = null; post(stateMsg()); }, 60);
  }
  function stateMsg() {
    var c = contentOf(cur);
    return { kind: "estado", cur: cur, hist: hist.slice(-40), world: world, granted: granted, scroll: c ? c.scrollTop : 0, draft: draft, entry: entry,
             fieldErr: fieldErr, ask: !!ask, snack: snackEl ? snackEl.dataset.msg : null, rest: store.active ? null : restClock };
  }
  function onState(m) {
    var was = connected();
    peer.seen = Date.now(); peer.st = m;
    store = load(); saved = loadLimits(); draft = m.draft || null; world = m.world || null; granted = !!m.granted; restClock = m.rest || null;
    var h = (m.hist || []).filter(function (id) { return !!screens[id]; });
    var moved = m.cur !== cur || h.join() !== hist.join() || !was;
    hist = h;
    if (m.cur && screens[m.cur]) { if (moved) show(m.cur, null, true); else decorate(cur); }
    if (cur && LIVE.editors[cur]) { draft = m.draft || null; entry = m.entry || null; fieldErr = m.fieldErr || null; renderEditor(cur); }  // the editor as the driver sees it
    if (m.ask && !ask) openAsk(); else if (!m.ask && ask) closeAsk();
    if (m.snack && !(snackEl && snackEl.dataset.msg === m.snack)) snack(m.snack); else if (!m.snack && snackEl) unsnack();
    var c = contentOf(cur); if (c && typeof m.scroll === "number" && Math.abs(c.scrollTop - m.scroll) > 1) c.scrollTop = m.scroll;
    $("pt-back").disabled = true;
    renderHost();
    render();
  }
  function command(cmd, arg) {  // a facilitator's button or key: to the driver's window when it is open, else here
    if (FAC && connected()) { post({ kind: "orden", cmd: cmd, arg: arg }); flash("Enviado a la ventana del participante: " + cmd + (arg ? " " + arg : "")); return; }
    run(cmd, arg);
  }
  function run(cmd, arg) {
    if (cmd === "iniciar") startTask(+arg);
    else if (cmd === "hecho") endTask("hecho");
    else if (cmd === "atascado") endTask("atascado");
    else if (cmd === "elegir") { store.next = ORDER.indexOf(+arg) !== -1 ? +arg : first(); save(); if (!FAC && !store.active) rest(store.next); render(); }
    else if (cmd === "quien") { store.participant = String(arg || "").trim(); save(); render(); }
    else if (cmd === "borrar") { store = { participant: store.participant, attempts: [], active: null, next: first() }; save(); if (!FAC) rest(store.next); render(); }
    else if (cmd === "pedir") announce();
  }

  // ---------- recording, storage ----------
  function load() {
    var s = null;
    try { s = JSON.parse(localStorage.getItem(KEY) || "null"); } catch (e) { s = null; }
    if (!s || !Array.isArray(s.attempts)) s = { participant: "", attempts: [], active: null };
    var ord = CFG.taskOrder || [1]; if (ord.indexOf(s.next) === -1) s.next = ord[0];  // a fresh session starts on the plan's first task (8)
    return s;
  }
  function save() {
    if (!recorder()) return;
    try { localStorage.setItem(KEY, JSON.stringify(store)); } catch (e) { /* private window: keep in memory */ }
    announce();
  }
  function tap(t) {
    if (!store.active || !recorder()) return;
    t.t = Date.now() - store.active.t0;
    store.active.taps.push(t);
    if (!store.active.firstTap) store.active.firstTap = t;
    save();
    renderLive();
  }
  function logEvent(ev) {  // limit changes, saves and entries, with the screen they happened on
    if (!store.active || !recorder()) return;
    ev.t = Date.now() - store.active.t0; ev.screen = cur;
    (store.active.events = store.active.events || []).push(ev);
    save();
    renderLive();
  }
  function task(n) { return CFG.tasks.filter(function (t) { return t.n === n; })[0]; }
  function startTask(n) {
    var t = task(n); if (!t) return;
    if (store.active) endTask("interrumpida");
    if (n === ORDER[0] || n === 1 || !(store.attempts || []).length || t.limits) keep(DEF, null);  // a session (its first task), task 1 ("ya llevas semanas") and the limits task (6) start from the fixture's pair, so task 6 never starts done; a saved change otherwise lasts the session, as in the app
    world = worldOf(t.start); granted = false; noteNext = null;
    store.next = n;
    store.active = { participant: store.participant, task: n, prompt: t.prompt, startScreen: t.start, clock: clock0[t.start] || null, world: world,
                     startedAt: new Date().toISOString(), t0: Date.now(), screens: [], taps: [], firstTap: null, events: [] };
    hist = []; scrollMem = {}; snackNext = null; hush();
    if (cur && screens[cur]) screens[cur].hidden = true;
    cur = null;
    show(t.start, "inicio");
    hReplace();  // the task's first entry: the phone's back under it stays put
    render();
  }
  function rest(n, quiet) {  // outside a task the driver's window waits on the next task's start screen, in its moment and world (not logged)
    var t = task(n); if (!t || !screens[t.start]) return;
    restClock = clock0[t.start] || null; restRun = "espera-" + Date.now();
    hist = []; scrollMem = {}; world = worldOf(t.start); granted = false; noteNext = null; snackNext = null; hush();
    if (cur && screens[cur]) screens[cur].hidden = true;
    cur = null;
    show(t.start, "espera");
    if (!quiet) hReplace();
  }
  function endTask(outcome) {
    var a = store.active; if (!a) return;
    a.endedAt = new Date().toISOString();
    a.durationMs = Date.now() - a.t0;
    a.outcome = outcome;
    a.endScreen = cur;
    a.tapCount = a.taps.length;
    a.missCount = a.taps.filter(function (t) { return !t.hit; }).length;
    a.limits = { meta: m2(saved.meta), min: m2(saved.min) };
    store.attempts.push(a);
    store.active = null;
    if (outcome !== "interrumpida") store.next = stepTask(a.task, 1);
    save();
    if (!FAC && outcome !== "interrumpida") rest(store.next);  // the driver's window waits on the next task's start screen
    else if (cur) decorate(cur);  // back to the frame's own status bar (a sheet's: its opener's)
    render();
  }
  function exportData() {
    var s = load(), src = recorder() ? store : s;
    var data = { kit: "design-system/ui_kits/android-app-v2", exportedAt: new Date().toISOString(), exportedFrom: ROLE,
                 participant: src.participant, attempts: src.attempts, active: src.active };
    var blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    var a = document.createElement("a");
    var who = (src.participant || "sin-nombre").replace(/[^\w-]+/g, "-");
    a.href = URL.createObjectURL(blob);
    a.download = "kompara-prueba-" + who + "-" + new Date().toISOString().slice(0, 16).replace(/[:T]/g, "") + ".json";
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
  }

  // ---------- facilitator panel (?facilitador=1): tasks with their answers, the live log, the link to the driver's window ----------
  var fmt = function (ms) { var s = Math.round(ms / 1000); return Math.floor(s / 60) + ":" + ("0" + (s % 60)).slice(-2); };
  var OUT = { hecho: "Hecho", atascado: "Atascado", interrumpida: "Interrumpida" };
  function tapText(t) {
    if (!t) return "ninguno";
    return "«" + (t.label || "sin texto") + "» en " + t.screen + (t.hit ? " → " + t.to : " (sin enlace)");
  }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]; }); }
  function renderConn() {
    var c = $("pt-conn"); if (!c) return;
    var on = connected();
    c.className = "pt-conn" + (on ? " on" : "");
    var age = Math.round((Date.now() - peer.seen) / 1000);
    c.textContent = c.dataset.flash ? c.dataset.flash : on
      ? "Ventana del participante conectada: esta ventana la refleja (solo lectura) y sus botones y atajos actúan en ella." +
        (age > 4 ? " Sin señal desde hace " + age + " s (¿está oculta?)." : "")
      : "Sin ventana del participante: esta ventana graba. Para la sesión, abre prototype.html sin ?facilitador en otra ventana del mismo navegador.";
  }
  function renderLive() {
    var box = $("pt-live"); if (!box) return;
    var a = store.active;
    box.textContent = "";
    var head = document.createElement("div");
    head.innerHTML = "En pantalla: <b>" + esc(cur || "—") + "</b>" + (CFG.notes[cur] ? ' <span class="pt-meta">· ' + esc(CFG.notes[cur]) + "</span>" : "") +
      (world ? ' <span class="pt-meta">· mundo ' + esc(world) + "</span>" : "") +
      (connected() && peer.st && peer.st.entry ? ' <span class="pt-meta">· escribiendo ' + (peer.st.entry.which === "meta" ? "tu meta" : "tu mínimo") + " en el teclado</span>" : "");
    box.appendChild(head);
    if (!a) {
      var idle = document.createElement("div"); idle.className = "pt-meta";
      idle.textContent = "Ninguna tarea en curso. Siguiente: tarea " + (store.next || first()) + " (N, o Iniciar en la lista).";
      box.appendChild(idle);
      return;
    }
    var t = task(a.task);
    var p = document.createElement("div");
    p.innerHTML = "<b>Tarea " + a.task + " en curso</b> · " + fmt(Date.now() - a.t0) + " · " + a.taps.length + " toques" +
      (t.first ? '<div class="pt-meta">Primer toque: ' + esc(tapText(a.firstTap)) + "</div>" : "") +
      (t.limits ? '<div class="pt-meta">Guardado: tu meta $' + m2(saved.meta) + " · tu mínimo $" + m2(saved.min) + "</div>" : "");
    box.appendChild(p);
    var log = document.createElement("ol"); log.className = "pt-log";
    a.taps.slice(-5).forEach(function (x) {
      var li = document.createElement("li"); li.textContent = fmt(x.t) + " " + tapText(x); log.appendChild(li);
    });
    box.appendChild(log);
    var row = document.createElement("div"); row.className = "pt-row";
    var done = document.createElement("button"); done.type = "button"; done.className = "pt-b pt-go"; done.textContent = "Hecho";
    done.onclick = function () { command("hecho"); };
    var stuck = document.createElement("button"); stuck.type = "button"; stuck.className = "pt-b pt-warn"; stuck.textContent = "Atascado";
    stuck.onclick = function () { command("atascado"); };
    row.appendChild(done); row.appendChild(stuck);
    box.appendChild(row);
  }
  function renderTasks() {
    var ol = $("pt-tasks"); if (!ol) return;
    ol.textContent = "";
    CFG.tasks.forEach(function (t) {
      var li = document.createElement("li");
      li.className = (store.active && store.active.task === t.n ? "on" : "") + (!store.active && store.next === t.n ? " next" : "");
      var mine = store.attempts.filter(function (a) { return a.task === t.n && a.participant === store.participant; });
      var last = mine[mine.length - 1];
      li.innerHTML = '<div class="pt-q"><b>' + t.n + ".</b> " + esc(t.prompt) + "</div>" +
        '<div class="pt-meta">Empieza en ' + t.start + (clock0[t.start] ? " (" + clock0[t.start].t + ")" : "") + " · Éxito: " + esc(t.ok) + "</div>" +
        (last ? '<div class="pt-res">' + OUT[last.outcome] + " · " + fmt(last.durationMs) + " · " + last.tapCount + " toques (" + last.missCount + " fuera)" + (mine.length > 1 ? " · " + mine.length + " intentos" : "") +
          (t.first ? "<br>Primer toque: " + esc(tapText(last.firstTap)) : "") +
          (t.limits && last.limits ? "<br>Guardado: tu meta $" + last.limits.meta + " · tu mínimo $" + last.limits.min : "") + "</div>" : "");
      var row = document.createElement("div"); row.className = "pt-row";
      var b = document.createElement("button"); b.type = "button"; b.className = "pt-b"; b.textContent = "Iniciar";
      b.onclick = function () { command("iniciar", t.n); };
      row.appendChild(document.createElement("span")); row.appendChild(b);
      li.appendChild(row);
      ol.appendChild(li);
    });
    $("pt-count").textContent = store.attempts.length + " intentos guardados en este navegador.";
    var who = $("pt-who"); if (who && document.activeElement !== who) who.value = store.participant || "";
  }

  // ---------- participant bar: the task controls behind a handle ----------
  function renderBar() {
    var a = store.active, n = store.next || first();
    $("pt-hn").textContent = "Tarea " + (a ? a.task : n);
    $("pt-dot").hidden = !a;
    $("pt-n").textContent = n;
    $("pt-prev").disabled = ORDER.indexOf(n) <= 0; $("pt-next").disabled = ORDER.indexOf(n) >= ORDER.length - 1;
    $("pt-done").disabled = $("pt-stuck").disabled = !a;
    var code = $("pt-code"); if (document.activeElement !== code) code.value = store.participant || "";
  }
  function render() { if (FAC) { renderConn(); renderLive(); renderTasks(); } else renderBar(); }
  if (FAC) {
    $("pt-who").oninput = function () { command("quien", this.value); };
    $("pt-export").onclick = exportData;
    $("pt-clear").onclick = function () {
      if (!confirm("¿Borrar todos los resultados guardados en este navegador?")) return;
      command("borrar");
    };
  } else {
    $("pt-handle").onclick = function () {
      var c = $("pt-ctl"), open = c.hidden;
      c.hidden = !open; this.setAttribute("aria-expanded", String(open));
      fit();
    };
    $("pt-prev").onclick = function () { run("elegir", stepTask(store.next || first(), -1)); };
    $("pt-next").onclick = function () { run("elegir", stepTask(store.next || first(), 1)); };
    $("pt-start").onclick = function () { run("iniciar", store.next || first()); };
    $("pt-done").onclick = function () { run("hecho"); };
    $("pt-stuck").onclick = function () { run("atascado"); };
    $("pt-export2").onclick = exportData;
    $("pt-code").oninput = function () { run("quien", this.value); };
  }
  setInterval(function () {
    if (FAC) { var was = peer.wasOn, on = connected(); peer.wasOn = on; if (store.active || on || was !== on) render(); if (was && !on) { $("pt-back").disabled = !hist.length; renderHost(); } }
    else post(stateMsg());  // the heartbeat that keeps the facilitator's window connected
  }, 1000);

  // ---------- fit: participant mode fits the phone under the bar (nothing scrolls, nothing covers it); ?fit=1 for the facilitator ----------
  var FIT = FAC ? Q.get("fit") === "1" : Q.get("fit") !== "0";
  function fit() {
    var k = 1, vw = document.documentElement.clientWidth, narrow = !FAC && FIT && vw <= 500;
    document.body.classList.toggle("pt-narrow", narrow);  // a phone: no bezel, the 393-wide screens scaled to the viewport (about 1:1)
    if (FIT) {
      var top = fitBox.getBoundingClientRect().top + (window.scrollY || 0);
      k = narrow ? Math.min(vw / 393, (innerHeight - top) / 852)
                 : Math.min(1, (innerHeight - top - 12) / 852, (vw - 2 * 18) / 393);  // the phone's 10px ring and a gutter
      k = Math.max(0.3, Math.floor(k * 1000) / 1000);
    }
    stage.style.transform = k === 1 ? "" : "scale(" + k + ")";
    fitBox.style.width = (393 * k) + "px"; fitBox.style.height = (852 * k) + "px";
  }
  addEventListener("resize", fit);
  addEventListener("load", fit);
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(fit);  // the bar's height settles once its font loads
  if (window.ResizeObserver) new ResizeObserver(fit).observe($("pt-bar"));

  // ---------- start ----------
  // The facilitator's window: ?screen, else the task's last screen, else B1 (as before). The driver's: this tab's history entry
  // (a reload, or back after one), else the task in progress (its history rebuilt from its log, else its start screen), else
  // ?screen, else the next task's start screen.
  var start0 = Q.get("screen"), st0 = HIST ? history.state : null, mine0 = ours(st0), nav0 = "";
  try { nav0 = performance.getEntriesByType("navigation")[0].type || ""; } catch (e) { nav0 = ""; }
  var landed = !!st0 && st0.pt === 1, under = landed && !mine0 && nav0 === "back_forward", stepped = false;  // under: back (after a reload) onto the floor or another task's entry
  if (FAC) {
    if (!screens[start0]) start0 = (store.active && store.active.screens.length) ? store.active.screens[store.active.screens.length - 1].id : (screens.B1 ? "B1" : CFG.order[0]);
    world = (store.active && store.active.world) || worldOf(start0); granted = !!(store.active && store.active.granted);
    show(start0, "carga");
  } else if (mine0) {
    hist = clean(st0.hist); world = st0.world || null; granted = !!st0.granted || !!(store.active && store.active.granted); if (st0.scroll) scrollMem = st0.scroll;
    show(st0.cur, nav0 === "back_forward" ? "atrás" : "carga");
  } else if (store.active) {
    var r0 = replay(store.active.screens) || { cur: store.active.startScreen, hist: [] };
    if (!screens[r0.cur]) r0 = { cur: store.active.startScreen, hist: [] };
    hist = r0.hist; world = store.active.world || worldOf(r0.cur); granted = !!store.active.granted;
    if (under && hist.length) { stepped = true; show(hist.pop(), "atrás"); } else show(r0.cur, "carga");  // the phone's back, one step
  } else if (screens[start0]) {
    world = worldOf(start0); restClock = clock0[start0] || null;  // the wait in that screen's moment, as on a task's start screen
    show(start0, "carga");
  } else rest(store.next || first(), true);
  if (HIST) {
    if (under && !stepped) stay();                     // under a task's first screen (or the wait): up again to where the driver was
    else if (landed && !st0.floor && !under) hReplace();  // a reload: the entries under this one are still there
    else { try { history.replaceState({ pt: 1, floor: 1 }, ""); } catch (e) { /* refused */ } hPush(); }  // a floor under this screen
  }
  render();
  fit();
  if (FAC) post({ kind: "orden", cmd: "pedir" });
  else {
    addEventListener("pagehide", function () { post({ kind: "adios" }); });
    document.addEventListener("visibilitychange", function () { if (!document.hidden) post(stateMsg()); });
  }
  if (DEBUG) {
    document.body.classList.add("pt-debug");
    var lines = ["Enlaces: " + report.specs + " · con coincidencia: " + report.matched + " · sin coincidencia: " + report.unmatched.length + " · conflictos: " + report.conflicts.length + " · zonas: " + report.hotspots];
    report.unmatched.forEach(function (l) { lines.push("SIN COINCIDENCIA  " + l); console.warn("sin coincidencia:", l); });
    report.conflicts.forEach(function (l) { lines.push("CONFLICTO  " + l); console.warn("conflicto:", l); });
    report.missingSource.concat(report.missingTarget).forEach(function (l) { lines.push("PANTALLA FALTANTE  " + l); console.warn("pantalla faltante:", l); });
    var dbg = document.createElement("div"); dbg.className = "pt-dbg"; dbg.textContent = lines.join("\n");
    document.body.appendChild(dbg);
    var pre = document.createElement("pre"); pre.id = "pt-report"; pre.hidden = true; pre.textContent = JSON.stringify(report);
    document.body.appendChild(pre);
    console.info(lines[0]);
  }
})();
</script>
</body>
</html>
"""



def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--src", type=Path, default=KIT / "index.html")
    ap.add_argument("--out", type=Path, default=KIT / "prototype.html")
    ap.add_argument("--check", action="store_true", help="render ?debug=1 in headless Chrome and fail on unmatched specs")
    args = ap.parse_args()
    build(args.src, args.out)
    if args.check:
        sys.exit(check(args.out))


if __name__ == "__main__":
    main()
