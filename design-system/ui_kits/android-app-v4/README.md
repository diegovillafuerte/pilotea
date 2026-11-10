# Kompara v4 kit — the simple launch set

Drawn 2026-11-09 after the simplicity review
(https://claude.ai/artifact/8uLPeoVir7nWrZPJZdioLu) and built the same day as version 0.3.0 on
branch `feat/v4-simple-launch` (worktree `.worktrees/v4-launch`, on top of 0.2.20). That branch's
`docs/v4-simple-launch-plan.md` and `docs/v4-progress.md` say what was built and where it differs
from these frames. The v3 kit (`../android-app-v2/`) stays the reference for the 0.2.x line.

38 frames at 360 × 780 dp (a Galaxy S25), against v3's 108:

| Frames | What |
|---|---|
| S1–S10 | The chip over the ride app: three colours, sin lectura, Pon Tu Precio below the mínimo (S5) and between the limits (S9), a tall offer under navigation, large type, apagado, and the chip in bruto (S10) |
| P1–P4 | First run: demo, what Kompara does, the permission, battery |
| L1–L8 | Ganancias: Hoy, the first day, apagado, Semana closed and under way, a past day, one offer, Cómo se calculó |
| K1–K4 | Comparar in two parts: Tú (own trips by app, day and hour) and its first days; Tu ciudad (the percentile among the city's drivers) and the state before the city opens |
| M1–M4 | IMSS: the month, amount passed, what changes and where the amount comes from, entering incentives |
| J1–J7 | Ajustes, Tu meta y tu mínimo, Tus costos, Semáforo, Tus datos, Ayuda, and Tu meta y tu mínimo in bruto (J7) |
| V1 | The two notices |

## The rules the frames follow

1. The chip is a colour and one number, with a small ▲ ◆ ▼ for drivers who cannot tell red from green. Kompara's K leads it in an ink head, in the brand green as on the app icon (`--k-on-dark`, the lighter green that reads on ink). It never sits on the verdict colour itself. 56 dp tall, 12 dp above the offer, flush with its left edge.
2. Three tabs: Ganancias, Comparar, IMSS. The semáforo's state is one line at the top of Ganancias.
3. Every peso figure means what the trips paid minus gasoline and wear. No figure includes the car.
4. The car is a weekly bill, drawn as one bar on the week.
5. "Estimate" is said once, in Cómo se calculó.
6. Ganancias has two periods, Hoy and Semana. The month lives in IMSS. Every offer row names its app.
7. Comparar has two parts. Tú compares the driver's own trips with each other, in what a trip leaves per km. Tu ciudad ranks the driver among the city's drivers as a percentile of what the apps pay per km, before costs, because costs differ from driver to driver.

8. IMSS, the founder's rule (2026-11-09): what an app paid in the month (trips, incentives and referral incentives, no tips) × (1 − cost deduction, 48 % for a vehicle) × (1 − IVA) must reach CDMX's monthly minimum wage. For 2026: $9,582.47 ÷ (0.52 × 0.84) = $21,938 from one app. The screen shows that amount and a bar, never the percentages. Incentives are typed by the driver, because the reader only sees offers. No screen says "cubierto".

9. Neto or bruto (founder, 2026-11-09): the driver chooses which one the chip shows, on Tu meta y tu mínimo and on Tus costos, and the chip says the word under the unit. Neto is the fare over the km minus the driver's cost per km; bruto is the fare over the km. The limits are kept in the chosen measure; switching proposes neto + cost rounded to the half peso. Every rate in the app follows the choice and says the word; peso totals are always after gasoline and wear.
10. Pon Tu Precio (founder, 2026-11-09): on a DiDi name-your-price offer the chip grows a 34 dp tray, "Desde ◆ $203 ▲ $249": the counter-offer that reaches the mínimo and the one that reaches the meta, each rounded up to the next peso. Only the ones still ahead are shown; an offer already above the meta shows the plain chip.

## What waits on the founder

- Sharing averages by app, weekday and hour (P2's third line, K3, K4, J5's second switch).
- IMSS, two details: whether IVA comes off as × 0.84 (drawn) or ÷ 1.16, and whether each app counts on its own (drawn) or the apps add up.
- The car as a weekly bill (L4, L5).

## Working on it

Edit `_head.html`, the `part-*.html` files and `_foot.html`; never `index.html`.

```bash
python3 build.py                      # assemble index.html
python3 check.py                      # assert every figure against fixture.json
python3 check.py --contract           # also write _frame-contract.md, the strings the app's tests assert
python3 render.py <out-dir> [S P L…]  # PNG sheets, one per section
python3 build.py --artifact <file>    # a self-contained page for publishing
```

`fixture.json` is the v3 fixture unchanged plus a `v4` block; `check.py` also fails on the
wording this set retired ("aprox.", "sin el coche", "/km neto"…). Two class names to keep
apart when adding frames: `.scrim` is a sheet's backdrop and `.orow.dim` a quiet offer row;
`.go` belongs to the ride app's accept button.
