#!/usr/bin/env python3
"""Derive every figure the v4 frames show from fixture.json and assert it inside its frame.

Run after any edit:  python3 build.py && python3 check.py
Only the phone's own text counts; captions are excluded.
"""
import html
import json
import math
import pathlib
import re
import sys

here = pathlib.Path(__file__).parent
F = json.loads((here / "fixture.json").read_text(encoding="utf-8"))
V = F["v4"]
SRC = (here / "index.html").read_text(encoding="utf-8")
errors = []


def frames(s):
    out = {}
    marks = list(re.finditer(r'<div class="frame" id="([A-Z]\d+)">', s))
    for i, m in enumerate(marks):
        end = marks[i + 1].start() if i + 1 < len(marks) else len(s)
        frag = s[m.start():end]
        out[m.group(1)] = frag[: frag.find('<div class="cap">')]  # the phone, without its caption
    return out


def text(frag):
    t = re.sub(r"<[^>]+>", " ", frag)
    return re.sub(r"\s+", " ", html.unescape(t))


def peso(x):
    n = int(x + 0.5) if x >= 0 else -int(-x + 0.5)
    return ("−$" if n < 0 else "$") + f"{abs(n):,}"


def rate(x):
    return ("−$" if x < 0 else "$") + f"{abs(x):.2f}"


def hundreds(x):
    return "$" + f"{int(x / 100 + 0.5) * 100:,}"


def pct(x):
    return f"{x:.1f}"


FR = frames(SRC)
TX = {k: text(v) for k, v in FR.items()}
CONTRACT = {k: [] for k in FR}                                  # what each frame is asserted to say, for the app's tests


def expect(fid, *strings):
    if fid not in TX:
        errors.append(f"{fid}: frame missing")
        return
    for s in strings:
        if s not in CONTRACT[fid]:
            CONTRACT[fid].append(s)
        if s not in TX[fid]:
            errors.append(f"{fid}: expected «{s}»")


def expect_bars(fid, attr, *series):
    """The heights (or widths, or tick positions) in a frame, in DOM order, against the derived ones."""
    got = [float(x) for x in re.findall(attr + r":([\d.]+)%", FR.get(fid, ""))]
    want = [round(v, 1) for s in series for v in s]
    if len(got) != len(want) or any(abs(a - b) > 0.051 for a, b in zip(got, want)):
        errors.append(f"{fid}: {attr} {got} ≠ derived {want}")


# ── costs and limits ────────────────────────────────────────────────────────
c = F["costs"]
COST = c["fuel_price_per_l"] / c["km_per_l"] + c["wear_per_km"]          # 2.8417 per km
META, MINIMO = F["floors"]["verde_min_per_km"], F["floors"]["rojo_below_per_km"]
CAR = F["car_cost"]["weekly"]


def left(fare, km):
    return fare / km - COST


# ── S · the chip ────────────────────────────────────────────────────────────
ch = F["chips"]
for fid, key in (("S1", "verde"), ("S2", "amarillo"), ("S3", "rojo")):
    o = ch[key]
    expect(fid, rate(left(o["fare"], o["km"])), f"${o['fare']:.2f}", f"{o['min']} min · {o['km']:.1f} km", f"${o['fare'] / o['km']:.2f}/km est.")
assert left(ch["verde"]["fare"], ch["verde"]["km"]) >= META
assert MINIMO <= left(ch["amarillo"]["fare"], ch["amarillo"]["km"]) < META
assert left(ch["rojo"]["fare"], ch["rojo"]["km"]) < MINIMO
expect("S4", "Sin lectura")
def counter(limit, km):                                        # the counter-offer that reaches a limit, rounded up to the peso
    return "$" + f"{math.ceil((limit + COST) * km):,}"


o = V["second_app_offer"]                                      # Pon Tu Precio, below the mínimo: both counter-offers
assert left(o["fare"], o["km"]) < MINIMO
expect("S5", "Pon Tu Precio", rate(left(o["fare"], o["km"])), f"${o['fare']:.2f}", "Rechazo permitido",
       f"Desde {counter(MINIMO, o['km'])} {counter(META, o['km'])}")
y = V["today_second_app_offers"]["12:25"]                      # between the limits: only the one still ahead
assert MINIMO <= left(y["fare"], y["km"]) < META
expect("S9", "Pon Tu Precio", rate(left(y["fare"], y["km"])), f"${y['fare']:.2f}", f"Desde {counter(META, y['km'])}")
if counter(MINIMO, y["km"]) in TX.get("S9", ""):
    errors.append("S9: shows a counter-offer for a limit the offer already meets")
bruto = V["chip_basis"]["bruto_limits_per_km"]                 # the same offer as S1, shown in bruto
g = ch["verde"]["fare"] / ch["verde"]["km"]
assert g >= bruto["meta"]
expect("S10", rate(g), "bruto", f"${g:.2f}/km est.")
for k in ("meta", "minimo"):
    neto = META if k == "meta" else MINIMO
    if round((neto + COST) * 2) / 2 != bruto[k]:
        errors.append(f"fixture: bruto {k} {bruto[k]} is not neto {neto} plus the cost, rounded to the half peso")
expect("S6", rate(left(ch["amarillo"]["fare"], ch["amarillo"]["km"])))
expect("S7", rate(left(ch["verde"]["fare"], ch["verde"]["km"])))
expect("S8", "Semáforo apagado", "Dejamos de leer tus ofertas")
for fid in ("S1", "S2", "S3", "S4", "S5", "S6", "S7", "S9", "S10"):
    if 'class="pill' in FR.get(fid, "") and "aria-label=" not in FR[fid]:
        errors.append(f"{fid}: the chip has no accessible name")
for fid, frag in FR.items():                                   # the brand stays on every chip
    if frag.count('class="pill') != frag.count('<i class="k">K</i>'):
        errors.append(f"{fid}: a chip is drawn without Kompara's K")
    for cls, inner in re.findall(r'<span class="pill([^"]*)"[^>]*>(.*?)</span>', frag, re.S):
        if " gr" not in cls and "<u>neto</u>" not in inner and "<u>bruto</u>" not in inner:
            errors.append(f"{fid}: a chip shows a rate without saying neto or bruto")

# ── P · first run ───────────────────────────────────────────────────────────
expect("P1", rate(left(ch["verde"]["fare"], ch["verde"]["km"])), "Arriba de tu meta", "Entre tu mínimo y tu meta", "Abajo de tu mínimo", "Ya restamos gasolina y desgaste")
expect("P2", "Lo que hace Kompara", "Uber y DiDi")
expect("P3", "EJEMPLO", "Abrir ajustes")
expect("P4", "Sin restricciones")

# ── L · Ganancias ───────────────────────────────────────────────────────────
s1, s2 = F["today_shifts"]
now_gross = s1["gross"] + ch["verde"]["fare"]                 # shift 1 and the 14:32 trip under way
now_km = s1["km"] + ch["verde"]["km"]
now_net = now_gross - now_km * COST
day_gross, day_km = s1["gross"] + s2["gross"], s1["km"] + s2["km"]
day_net = day_gross - day_km * COST
morning = F["today_morning_trips"]
missed = F["today_morning_missed"]
afternoon = F["today_afternoon_trips"]


def r(t, table):
    return rate(left(table[t]["fare"], table[t]["km"])) + "/km"


v_rate = rate(left(ch["verde"]["fare"], ch["verde"]["km"])) + "/km"
a_rate = rate(left(ch["amarillo"]["fare"], ch["amarillo"]["km"])) + "/km"
r_rate = rate(left(ch["rojo"]["fare"], ch["rojo"]["km"])) + "/km"
other = {k: v for k, v in V["today_second_app_offers"].items() if not k.startswith("_")}
assert all(v["outcome"] == "se fue" for v in other.values()), "a taken trip on the second app would change the day's money"
offers_1440 = 1 + 1 + 1 + sum(1 for k in morning if not k.startswith("_")) + sum(1 for k in missed if not k.startswith("_")) + len(other)
expect("L1", peso(now_net), f"{F['today_at_1440']['trips']} viajes", "4 h 50 min", f"Ofertas de hoy {offers_1440}",
       v_rate, r_rate, r("12:25", other), r("11:20", morning), r("10:41", morning), "Semáforo funcionando",
       "Uber 14:32 · En curso", "DiDi 12:25 · Se fue sin tomar", "Uber 11:48 · Sin lectura")
assert MINIMO <= left(other["12:25"]["fare"], other["12:25"]["km"]) < META
expect("L2", "Semáforo preparado", "$8.00", "$6.00", rate(COST) + " por km", v_rate.replace("/km", ""))
expect("L3", "Semáforo apagado", "desde ayer 20:31", peso(day_net), "Volver a dar el permiso")

w = F["weeks"]["2026-10-12"]
week_net = w["gross"] - w["km"] * COST
days = [d["gross"] - d["km"] * COST for d in w["days"].values()]
best = max(days)
cum, covered = 0, None
for name, v in zip(["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"], days):
    cum += v
    if covered is None and cum >= CAR:
        covered = name
expect("L4", w["label"], peso(week_net), f"{w['trips']} viajes", peso(best), peso(CAR), peso(week_net - CAR), f"cubierto el {covered}")
expect_bars("L4", "height", [100 * d / best for d in days])
expect_bars("L4", "width", [100 * CAR / week_net])
expect("L5", "19 – 25 oct", peso(now_net), f"{peso(now_net)} de {peso(CAR)}", peso(CAR - int(now_net + 0.5)))
expect_bars("L5", "width", [100 * now_net / CAR])
trips_day = s1["trips"] + s2["trips"]
expect("L6", "Lunes 19 oct", peso(day_net), f"{trips_day} viajes", "8 h 10 min", f"Ofertas del día {trips_day + 4 + len(other)}",
       r("17:21", afternoon), r("16:04", afternoon), a_rate, v_rate, r("12:25", other), r_rate, "DiDi 12:25 · Se fue sin tomar")
o = ch["verde"]
trip_left = o["fare"] - o["km"] * COST
expect("L7", peso(trip_left), rate(left(o["fare"], o["km"])) + " por km", f"${o['fare']}", f"{o['km']:.1f} km × {rate(COST)}",
       peso(-o["km"] * COST), peso(trip_left / o["min"] * 60), f"{o['min']} min", "$8.00")
expect("L8", f"{F['today_at_1440']['trips']} viajes", peso(now_gross), f"{now_km:.1f} km × {rate(COST)}", peso(-now_km * COST), peso(now_net))

# ── K · Comparar ────────────────────────────────────────────────────────────
win = V["window"]
weeks = [F["weeks"][k] for k in win["weeks"]]
tot = {k: sum(x[k] for x in weeks) for k in ("trips", "gross", "km")}
for name, table in (("by_weekday", win["by_weekday"]), ("by_hour_block", win["by_hour_block"])):
    for k in ("trips", "gross", "km"):
        got = round(sum(row[k] for row in table.values()), 1)
        if abs(got - tot[k]) > 0.05:
            errors.append(f"fixture v4.window.{name}: {k} adds to {got}, the weeks add to {round(tot[k], 1)}")
app = {a: {k: sum(x["by_app"][a][k] for x in weeks) for k in ("gross", "km")} for a in ("uber", "didi")}
r_didi, r_uber = left(app["didi"]["gross"], app["didi"]["km"]), left(app["uber"]["gross"], app["uber"]["km"])
wd = [left(x["gross"], x["km"]) for x in win["by_weekday"].values()]
hb = [left(x["gross"], x["km"]) for x in win["by_hour_block"].values()]
shown = lambda x: round(x, 2)                                    # differences are taken from the printed figures
gap_app = shown(r_didi) - shown(r_uber)
gap_day = shown(max(wd)) - shown(min(wd))
expect("K1", "Tú Tu ciudad", rate(r_didi), rate(r_uber), f"DiDi te deja {rate(gap_app)} más por km", rate(max(wd)), rate(min(wd)),
       f"El sábado te deja {rate(gap_day)} más que el viernes", rate(max(hb)), "De 18 a 22 h")
expect_bars("K1", "width", [100, 100 * r_uber / r_didi])
expect_bars("K1", "height", [100 * x / max(wd) for x in wd], [100 * x / max(hb) for x in hb])
expect("K2", f"Con {win['min_trips_to_compare']} viajes", f"Llevas {win['first_days_trips']}")
expect_bars("K2", "width", [100 * win["first_days_trips"] / win["min_trips_to_compare"]])

city = V["examples"]["city_open"]
paid = tot["gross"] / tot["km"]                                  # what the apps pay per km, before costs
dec = sorted((int(k), v) for k, v in city["gross_per_km_deciles"].items())
rank = next(lo + 10 * (paid - a) / (b - a) for (lo, a), (_, b) in zip(dec, dec[1:]) if a <= paid <= b)
pc = city["percentile"]
expect("K3", f"Te pagan más por km que al {round(rank)} % de los conductores de CDMX", f"{city['drivers']} conductores",
       f"{pc['by_app']['didi']} %", f"{pc['by_app']['uber']} %", f"{max(pc['by_weekday'].values())} %", f"{min(pc['by_weekday'].values())} %",
       f"{max(pc['by_hour_block'].values())} %", f"{min(pc['by_hour_block'].values())} %")
below = int(rank // 5)                                           # of twenty drivers
k3 = FR.get("K3", "")
got = (k3.count("<i></i>"), k3.count('<i class="me"></i>'), k3.count('<i class="abv"></i>'))
if got != (below, 1, 19 - below):
    errors.append(f"K3: the row of twenty is {got}, derived {(below, 1, 19 - below)}")
expect_bars("K3", "width", [pc["by_app"]["didi"], pc["by_app"]["uber"]])
expect_bars("K3", "height", pc["by_weekday"].values(), pc["by_hour_block"].values())
prog = V["examples"]["city_cohort_progress"]
expect("K4", f"{prog['city']} abre con {prog['needed']} conductores", f"Van {prog['drivers']}", "Sumar mis promedios", "Nunca se mandan")
expect_bars("K4", "width", [100 * prog["drivers"] / prog["needed"]])

# ── M · IMSS ────────────────────────────────────────────────────────────────
im = V["imss_2026"]
keep = (1 - im["cost_deduction_vehicle"]) * (1 - im["iva"])      # what is left of each peso an app pays
need = im["min_monthly_wage_cdmx"] / keep                         # what one app has to pay in the month
trips_m = {a: sum(F["weeks"][k]["by_app"][a]["gross"] for k in win["weeks"]) + F["today_by_app_1440"][a] for a in ("uber", "didi")}
inc = V["imss_incentives_entered"]
base = {a: trips_m[a] + inc[a] for a in trips_m}                  # trips + incentives and referral incentives, no tips
day_of_month = int(F["today"][-2:])
pace = {a: trips_m[a] / day_of_month * im["month_days"] + inc[a] for a in trips_m}
RULE = "Cuentan viajes, incentivos y bonos por referidos; las propinas no"
expect("M1", f"quedan {im['month_days'] - day_of_month} días", peso(need), RULE, peso(base["uber"]), peso(base["didi"]),
       f"Viajes {peso(trips_m['uber'])} · Incentivos {peso(inc['uber'])}", f"Viajes {peso(trips_m['didi'])} · Incentivos: agregar",
       f"unos {hundreds(pace['uber'])}", "Lo alcanzas", f"unos {hundreds(pace['didi'])}", f"Te faltarían {hundreds(need - pace['didi'])}",
       f"Entre las dos llevas {peso(base['uber'] + base['didi'])}", "En una sola app ya lo habrías alcanzado")
assert pace["uber"] >= need > pace["didi"], "M1's two sentences no longer hold"
assert base["uber"] + base["didi"] >= need, "M1's line about one app no longer holds"
assert trips_m["uber"] / day_of_month * im["month_days"] < need, "the caption says Uber makes it only with its incentives"
expect_bars("M1", "width", [100 * base["uber"] / need, 100 * base["didi"] / need])
m2 = V["examples"]["m2_other_driver"]
m2_total = m2["uber_trips"] + m2["uber_incentives"]
assert m2_total >= need
expect("M2", peso(m2_total), f"quedan {m2['days_left']} días", f"Ya pasaste los {peso(need)} con Uber", RULE,
       f"Viajes {peso(m2['uber_trips'])} · Incentivos {peso(m2['uber_incentives'])}")
expect("M3", f"te paga {peso(need)} o más", f"{round(100 * im['cost_deduction_vehicle'])} % de gastos", f"{round(100 * im['iva'])} % de IVA",
       peso(im["min_monthly_wage_cdmx"]), "Las propinas no", "Cada app cuenta por separado")
expect("M4", "Incentivos de Uber · octubre", peso(inc["uber"]), "No incluyas propinas", "Guardar")
for fid in ("M1", "M2", "M3", "M4"):
    for banned in ("cubierto", "Cubierto", "Ya te toca"):
        if banned in TX.get(fid, ""):
            errors.append(f"{fid}: «{banned}» certifies coverage; only the app and the IMSS can")

# ── J · Ajustes, V · notices ────────────────────────────────────────────────
expect("J1", "$8.00 · $6.00 por km", f"{rate(COST)} por km · coche {peso(CAR)} a la semana", "Funcionando")
expect("J2", "Muestra Neto Bruto", f"Neto: ya restamos tu costo de {rate(COST)} por km", "$8.00", "$6.50", "De $0.50 en $0.50", v_rate.replace("/km", ""))
expect_bars("J2", "left", [100 * 6.5 / (1.5 * META)] * 2 + [100 * META / (1.5 * META)])      # the band runs to 1.5 x the meta
expect("J7", "Muestra Neto Bruto", "Bruto: la tarifa entre los km", rate(g), "bruto", f"${bruto['meta']:.2f}", f"${bruto['minimo']:.2f}",
       f"Son tus ${META:.2f} y ${MINIMO:.2f} neto más tu costo de {rate(COST)}")
expect_bars("J7", "left", [100 * bruto["minimo"] / (1.5 * bruto["meta"])] * 2 + [100 * bruto["meta"] / (1.5 * bruto["meta"])])
expect("J3", "El semáforo muestra Neto Bruto", rate(COST), f"{c['km_per_l']} km por litro", f"${c['fuel_price_per_l']:.2f} por litro", f"${c['wear_per_km']:.2f} por km", f"{peso(CAR)} a la semana")
expect("J4", "Semáforo funcionando", "Uber Leyó tu última oferta · 14:32", "DiDi Leyó tu última oferta · 12:25")
expect("J5", "Tus viajes se calculan en este teléfono", "Sumar mis promedios a mi ciudad")
expect("J6", "¿Por qué no me sale el semáforo?")
expect("V1", "Se apagó el semáforo", f"{w['trips']} viajes · {w['label']}")

# ── words the simple set retired ────────────────────────────────────────────
RETIRED = ["aprox", "sin el coche", "con el coche", "Te quedan", "Te quedaron", "te quedaron", "Cuadrar", "Premium", "Lo del coche", "en pausa"]
for fid, t in TX.items():
    for word in RETIRED:
        if word in t:
            errors.append(f"{fid}: retired wording «{word}»")

WORDS = {35: "Thirty-five", 36: "Thirty-six", 37: "Thirty-seven", 38: "Thirty-eight", 39: "Thirty-nine", 40: "Forty"}
if f"<h1>{WORDS.get(len(FR), '?')} screens</h1>" not in SRC:
    errors.append(f"the page's title does not match its {len(FR)} frames")
if errors:
    print("\n".join(errors))
    sys.exit(1)
if "--contract" in sys.argv:                                    # the strings the Android tests must find in each built frame
    lines = ["# Frame contract — strings check.py asserts INSIDE each frame's screen text (captions excluded). Keep every one verbatim (same characters, including the − minus sign and plain spaces).",
             "# Generated by `python3 check.py --contract`; do not edit by hand.", ""]
    for fid in FR:
        said = " · ".join(f"`{x}`" for x in CONTRACT[fid]) or "(no asserted figures)"
        lines.append(f"- **{fid}**: {said}")
    (here / "_frame-contract.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"OK · {len(FR)} frames · every figure derives from fixture.json")
