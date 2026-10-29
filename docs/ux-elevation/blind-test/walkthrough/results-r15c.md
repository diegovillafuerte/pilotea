6 judges
| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 6/0/0 | 0.0 | 6.0 |
| Every offer today | 4/2/0 | 1.0 | 5.5 |
| Last week after car | 6/0/0 | 1.8 | 6.0 |
| Which app paid more | 6/0/0 | 1.0 | 6.2 |
| Change tu mínimo | 6/0/0 | 8.0 | 4.8 |
| Overlay stopped | 3/3/0 | 3.8 | 4.7 |
| First day setup | 5/1/0 | 8.3 | 4.5 |
mean ease 5.48 · ease vs Uber 6.5/10 · understand every screen 5.7/10
- B1 | 'Te quedan' means two things: Hoy $762 excludes the car cost, while E8 Semana en curso shows $548 for the same Monday with the car ($214/day) subtracted; only explained in D3 fine print | fix: On Hoy show 'Te quedan antes del coche $762' and 'Despues del coche ($214/dia) $548', or subtract the car on Hoy too so every 'Te quedan' uses the same maths
- B2 | Overlay is called 'semaforo' in settings/help but Hoy reports 'Lector'; state shown with low-contrast dash-pattern icons (---, - -, -) a driver can't decode; non-permission causes (no room, navigation | fix: Use one name ('semaforo'), plain-language coloured status like 'Semaforo apagado: Android quito el permiso' with the fix button, and list the other reasons it d
- G2 | Meta/minimo are behind an unlabelled person icon -> Cuenta -> 'Tu semaforo' and change only in $0.10 steps (5 taps for +$0.50), no typing | fix: Add a 'Tu meta $8 - tu minimo $6' row on Hoy (as on B3) that opens G2 directly; allow tapping the number to type it; label the icon 'Cuenta y ajustes'
---
- B1 | 'Te quedan hoy $762' excludes the car while Ganancias (E8) shows $548 for the same day; the same word means two numbers and the explanation is only inside the D3 sheet | fix: Show both on Hoy ('$762 antes de lo del coche · $548 después') or use one definition of 'te quedan' everywhere and label the other number differently
- G2 | Limits are behind the person icon under the jargon row 'Tu semáforo' and change only in $0.10 steps (5 taps for +$0.50), no typing; Hoy has no link to them | fix: Rename the row 'Tu meta y tu mínimo', add an 'Ajustar mis límites' row on Hoy, let the driver tap the amount to type it or hold to repeat
- B1 | When the overlay is hidden but the reader is on (C11 no room, C9 nav banner, tall offer), Hoy still says 'Lector funcionando' with no hint; the help is buried in Cuenta > Ayuda / Lector, and 'lector'  | fix: Add a Hoy row like '2 ofertas sin semáforo hoy (no cabía) >' plus a '¿No ves el semáforo?' link next to the status; use one name for reader and overlay
---
- B1 | Headline 'Te quedan hoy · aprox. $762' reads as money still to earn, and its subline omits the car, which is only deducted in Ganancias, so the kept figure looks more final than it is. | fix: Relabel to 'Llevas hoy, neto' and add 'antes del coche ($214/día)' under the number, or show the daily car share as a visible line.
- A3 | The Android accessibility dialog reads 'Permitir que Kompara tenga control total de tu dispositivo?' and the only reassurance, 'Kompara solo lee, nunca toca nada', is small grey text under it, so a fi | fix: Put 'Kompara solo lee tu pantalla y nunca toca nada' in bold above step 1, and add before 'Abrir ajustes' that Android will say control total, which is its stan
- B2 | The reader-off state gives the cause (accessibility permission removed) only in body text, and its single button 'Encender lector' does not say it will send the driver back to the permission flow. | fix: Title it 'Falta el permiso de accesibilidad', label the button 'Volver a dar permiso' to open A3's steps, keep 'apagado desde 7:50' as the subline, and show the
---
- A3 | Permission cliff: A2 is a wall of small text, A3 uses scary 'control total de tu dispositivo' wording with a tiny reassurance line, shows a mock Android dialog with live-looking Rechazar/Permitir butt | fix: Cut A2 to three short icon lines; put 'Kompara solo lee, nunca toca nada' above the fold on A3 next to the Android wording; make the mock dialog a non-tappable 
- B1 | Hoy shows 'Lector funcionando' (green) even when the battery is restricted or the overlay is suppressed; C6 only says 'Abre Kompara al estacionarte', C11 has no cue, and G8's 'Por qué no me sale el se | fix: Show an amber line on B1 when the phone can kill the reader ('Tu teléfono puede apagar el lector · Arreglar') with a one-tap fix; make the C6 chip say 'Toca par
- B1 | 'Te quedan hoy $762' excludes the car while E8/E1 'Te quedan' includes it, so the same 6 trips show $762 on Hoy and $548 on Ganancias; only tiny grey captions differ | fix: Use one definition everywhere, or label Hoy 'Te quedan hoy, antes del coche' and add a visible '− $214 del coche = $548' line; tapping the amount opens D3 with 
---
- B1 | Two different 'kept' numbers for the same trips: Hoy shows $762 (after gas and wear) and Ganancias E8 shows $548 (after the $214 car). The car deduction is explained only in small text and the D3 shee | fix: Add a visible line under the Hoy headline such as '-$214 coche = $548 en la semana', or label the headline 'Después de gasolina y desgaste' with a link to the c
- G2 | My two limits (meta and mínimo), the core of the product, are never asked in onboarding (A5 only asks car consumption, B3 shows only meta 'valor inicial') and are buried under the person icon. Changin | fix: Add a 'Tu meta $8 · tu mínimo $6' row on Hoy (B1) opening G2 directly, show both limits on B3, allow tapping the value to type it, and show a 'Guardado' confirm
- C6 | When the overlay disappears the in-car cue is a tiny grey 'Lector detenido' notice, suppression for no room (C11) is silent, and Hoy (B1) still says 'Lector funcionando' in those cases; B2 only covers | fix: Make C6 a large tappable 'Abrir Kompara', show a one-line chip when the overlay is suppressed, and add a 'Por qué no vi el semáforo' row on Hoy with a reason pe
---
- B1 / D1 / E8 | Te quedan excludes car cost in Hoy but includes it in Ganancias, making $762 today and $548 this week appear inconsistent. | fix: Explicitly label the daily amount as after gasoline and wear but before car cost, and show the car deduction in the weekly breakdown.
- A3 / A4 / B3 | Permission setup ends at Lector preparado without a clear action to test the overlay. | fix: Offer Abrir Uber para probar and Abrir DiDi para probar, show Esperando primera oferta, and confirm the first successful reading.
- C6 / C11 / G4 | A missing overlay can have different causes, but the driver lacks a clear explanation of the latest disappearance. | fix: Show the latest cause and timestamp in Lector, distinguishing permission loss, pauses and insufficient screen space, with the appropriate next action.
---
