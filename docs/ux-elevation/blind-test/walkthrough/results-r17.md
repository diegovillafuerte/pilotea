6 judges
| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 6/0/0 | 0.0 | 5.8 |
| Every offer today | 3/3/0 | 1.0 | 5.5 |
| Last week after car | 6/0/0 | 1.8 | 5.8 |
| Which app paid more | 6/0/0 | 1.0 | 6.3 |
| Change tu mínimo | 6/0/0 | 6.8 | 5.0 |
| Overlay stopped | 4/2/0 | 2.2 | 5.2 |
| First day setup | 3/3/0 | 8.7 | 4.2 |
mean ease 5.50 · ease vs Uber 6.5/10 · understand every screen 6.0/10
- G7 | Kompara's phone notifications are off ('Tu teléfono los tiene apagados'), so 'Si el semáforo se apaga' cannot alert the driver mid-shift while the toggles look on; the overlay can stop unnoticed until | fix: Ask for notification permission in setup (A3) and show a banner on Hoy when blocked; grey out the G7 toggles with an 'Activar' button on each row.
- A3 | Android's 'control total de tu dispositivo' dialog appears right after A2 says Kompara only reads the screen, at the point where new drivers are most likely to quit. | fix: Add one line above 'Abrir ajustes' explaining that Android uses this wording for every accessibility app, and show the dialog with 'Permitir' marked.
- B1 | Offer rows on Hoy show no $/km (only in D1 after a tap), and the cost basis changes by screen: E1 says 'ni coche', F1 says 'ni desgaste' only, so the car may or may not be in the Uber/DiDi comparison. | fix: Show $/km on each Hoy offer row, use one cost label everywhere (e.g. 'sin gasolina, desgaste ni coche'), and state in F1 whether the car is included.
---
- A3 | Accessibility permission is a trust cliff: Android's 'control total' dialog contradicts 'solo lee tu pantalla', and the generic steps don't match each phone brand's settings menus. | fix: Detect phone brand and show its exact settings path or a short animation; add a one-line 'Por qué Android dice control total' directly under the mock dialog.
- G1 | Limits, costs, semáforo and help sit under a person icon labelled 'Cuenta', where drivers don't look for settings. | fix: Rename to 'Ajustes' with a gear icon and keep a permanent 'Tu meta $8 · tu mínimo $6 >' row on Hoy, as B3 does on first run.
- B1 | 'ya sin gasolina, desgaste ni coche' reads as 'excluding costs', and Hoy's net subtracts the car while the overlay's 'neto' (C2) does not; two meanings of net. | fix: Hoy: 'Ya restamos gasolina, desgaste y coche ($214/día)'; overlay: '/km después de gasolina'; use one verb everywhere.
---
- A3 / B3 | Overlay setup is a hand-off I cannot verify: the steps disappear once I am in Android Settings, the OS dialog says 'control total de tu dispositivo' after the app said it never touches anything, and B | fix: Add a reassurance line matching the OS dialog wording, keep a floating mini-checklist while in Settings, and on return show a live test overlay plus a 'Listo, K
- B1 / E1 / F1 / D1 | Internal jargon and ambiguous phrasing: 'según el lector', 'semáforo', 'Te quedan hoy · aprox.', 'ya sin gasolina, desgaste ni coche' in small grey, and an unexplained 'Tu resumen de Uber dice $34 más | fix: Use plain words ('lo que Kompara leyó en tu pantalla', 'Llevas hoy, ya descontando gasolina, desgaste y coche'), define 'semáforo' once on B3, show 'incluye coc
- B1 / G1 | Key controls and numbers are buried: limits sit behind an unlabelled person icon (4 taps to change mínimo), B1's offer rows show no $/km and do not look tappable, and 'Ver tu día' is generic. | fix: Add a tappable 'Tu mínimo $6.00/km · meta $8.00/km' chip on Hoy, label the icon 'Ajustes', show $/km on B1 rows with chevrons, and rename the button 'Ver las 23
---
- G1 | Meta and mínimo, the core of the overlay, are buried behind an unlabeled person/account icon; Hoy (B1) neither shows nor links them, and D2 calls them 'límites' | fix: Add a 'Tu meta $8 · tu mínimo $6 ›' row on Hoy (as B3 has) opening G2 directly; label the icon 'Ajustes'; use one name everywhere
- E8 | Ganancias opens on the week in progress (on Monday it repeats today's $548); last week sits behind a small '<', and 'ya sin gasolina, desgaste ni coche' doesn't clearly say the car cost was subtracted | fix: Default to 'semana pasada' when the current week has fewer than 2 days, or pin a 'Semana pasada: $4,980' card; rewrite as 'ya restamos gasolina, desgaste y $214
- A3 | Onboarding sends the driver twice into Android Settings (A3, A4) with only text hints and a scary 'control total' dialog, then ends at B3 'Semáforo preparado' with default limits, so you can't tell it | fix: Illustrated step-by-step with auto-return to Kompara, a practice overlay on a sample offer right after the permission so the state shows 'funcionando', and a me
---
- D1 | Offer list header says 23 offers but only 4 rows are shown, no scroll or see-all cue; the unread offer has no value; B1 recent offers lack $/km and 'Ver tu día' is a low-contrast grey button. | fix: Show all offers in a scrollable list with a clear scroll cue, a $/km value or a dash plus reason on every row, $/km on B1's three rows, and make 'Ver tu día' a 
- B1 | Status says 'Semáforo funcionando' even when the overlay was hidden (no room, navigating, no reading); the explanation lives only in Ayuda (G8), so a driver whose overlay vanished has no cue on Hoy. | fix: Make the status row tappable on every Hoy variant and add 'Semáforo funcionando · N ofertas sin semáforo hoy' linking to a per-offer reason list with the fix in
- B1 | Money wording is ambiguous: 'Te quedan hoy' reads as remaining to earn, 'Ganancias' means net here but gross in Uber, and E1's 'Tu resumen de Uber dice $34 más' is unexplained. | fix: Use 'Llevas limpio hoy · aprox.' with 'después de gasolina, desgaste y coche' as a normal-size first caption, and reword the E1 row to say what differs from the
---
- A3/A4 | Permission setup is difficult to complete confidently; the warning is intimidating and external setup and return states are absent. | fix: Explain Android's control total wording, include settings screens, and show explicit permission, battery and first-reading checks.
- G2 | The supplied mid-edit fixture shows a $9.50 goal while G1 shows $8, creating uncertainty when changing only the minimum. | fix: Start this route with saved values and preserve the goal while editing the minimum.
- D1 | An unread fare prevents an offer from displaying net earnings per km. | fix: Add an Agregar tarifa faltante action and recalculate when the required information is available.
---
