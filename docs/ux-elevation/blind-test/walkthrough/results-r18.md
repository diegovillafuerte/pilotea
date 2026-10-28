6 judges
| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 5/1/0 | 0.0 | 5.5 |
| Every offer today | 3/3/0 | 1.0 | 5.7 |
| Last week after car | 6/0/0 | 1.8 | 6.0 |
| Which app paid more | 6/0/0 | 1.0 | 6.3 |
| Change tu mínimo | 6/0/0 | 4.3 | 5.3 |
| Overlay stopped | 4/2/0 | 3.5 | 5.2 |
| First day setup | 4/2/0 | 7.8 | 4.7 |
mean ease 5.60 · ease vs Uber 6.7/10 · understand every screen 5.8/10
- C2 | Overlay reason is in tiny meta/mínimo labels and thin bars, 'neto' is undefined, and it conflicts with Uber's own $/km on the same card (C8/C12). | fix: Replace the bars with one large line like '+$1.16 sobre tu mínimo · −$0.84 bajo tu meta', write 'neto' as 'después de gasolina y desgaste' (define it once on A1
- B1 | Hoy says 'Semáforo funcionando' even when offers were hidden for lack of room (C11); the explanation is only in G4/G8, so a driver whose overlay vanished finds no answer on Hoy. | fix: When it happens, change the status row to 'Funcionando · 2 ofertas sin espacio en pantalla ›' linking to the reason and how to avoid it.
- G2 | Tapping 'tu meta · tu mínimo' on B1 opens the G4 status page, not the editor, and on G2 the +/− step size is unknown and tap-to-type is only hinted in small grey text. | fix: Make B1's meta/mínimo text open G2 directly, label the steppers (e.g. '±$0.50'), and style the figure as an editable field.
---
- B1/B7/G4 | The app does not clearly say when it is not working: B1 shows 'Semaforo funcionando' even when the overlay is missing, B7 shows only a small icon and 'aun funciona', and the no-room suppression case i | fix: Replace the status row with a colored banner in any problem state, with a one-tap fix button (for example 'Arreglar: quitar restriccion de bateria'); on B1 show
- B1/E1/C2 | Core wording is jargon a new driver does not parse: 'Te quedan hoy' (sounds like money left to earn), 'lector', 'neto', 'semaforo', 'tu meta / tu minimo'. | fix: Use plain phrasing such as 'Te quedo limpio hoy: $548 (ya con gasolina, desgaste y coche)', replace 'segun el lector' with 'segun lo que Kompara leyo', show '/k
- D1/E1/E8 | Numbers cannot be audited or trusted: D1 says 23 offers but draws 4 with no scroll cue and a different time of day from B1; E1 has no week breakdown sheet and an unexplained 'Tu resumen de Uber dice $ | fix: Add scroll cues and 'Ver mas' on D1 and keep its stamp consistent with Hoy; add a 'Como se calculo' sheet for the week like D3; explain or remove the '$34 mas' 
---
- B1 | Main number's caption 'Te quedan... ya sin gasolina, desgaste ni coche' does not show what was subtracted or how much; 'ni coche' reads as 'without a car'; same wording on E1/E8 with no week breakdown | fix: Write 'Ya restados: gasolina y desgaste $X, coche $214', make the line open the 'Como se calculo' sheet, and add that sheet for a week
- G8 | Why the overlay is missing is explained only in Ajustes > Ayuda; when it is hidden for lack of room (C11) or battery (B7), Hoy still says 'Semaforo funcionando', which contradicts what the driver sees | fix: On Hoy add a row like '2 ofertas sin semaforo en pantalla hoy - por que?' linking to the specific cause, and a '¿No ves el semaforo?' link under the status row 
- A1 | First screen headline 'Antes de aceptar, ve si te deja.' is ambiguous, the legend uses 'tu meta/tu minimo' before they exist, and 'semaforo' is never introduced although every later screen uses it | fix: Headline 'Ve cuanto te deja cada viaje antes de aceptarlo' plus one line: 'Tu semaforo: verde arriba de tu meta ($8/km), rojo abajo de tu minimo ($6/km); los ca
---
- B1/G4/G8 | No single place explains why the overlay is not showing. B1 can say 'Semaforo funcionando' while the overlay is suppressed; causes are split across B2, B7, G4 (reached by a non-obvious status-row tap) | fix: Add a Hoy line when offers were suppressed ('2 ofertas sin semaforo en pantalla . ver por que'); make G4 start with a plain-language 'Por que no salio' list wit
- C2 | The yellow verdict states a position, not a decision. Overlay text and bars are about 8pt, and nothing shows why the figure is $7.16 at offer time. | fix: Replace the bars with one short line carrying the distance to the limit ('Pasable . +$1.16 sobre tu minimo'), set type at 14sp or larger, and add a '12 km incl.
- B1/E1/E8 | The money figure is ambiguous. 'Te quedan hoy . aprox.' reads as a balance still to come, the cost qualifier is 8pt grey, 'segun el lector' is never defined, and E1 has a contradicting 'Tu resumen de  | fix: Label it 'Libre hoy . despues de gasolina, desgaste y coche'; replace 'lector' with 'lo que Kompara leyo'; draw a weekly 'Como se calculo' sheet like D3 with th
---
- A3 | Android control-total prompt shown as a mock, with the explanation in small grey text under it; highest drop-off risk and it recurs as the fix on B2. | fix: Move the explanation above the mock at body size; caption the mock as what Android shows, not a request from Kompara.
- C8 | Two per-km numbers on one offer: unlabelled gross 12.33/km on the card beside net 9.49 /km neto; C11 shows gross 5.04/km with the overlay suppressed while the net for the same 62 offer is 2.20. | fix: Show only the labelled net per km on the card; delete the gross line or label it bruto in grey; show no Kompara figure when the overlay is suppressed.
- C2 | Amarillo (Entre tu meta y tu minimo) is a position between two thresholds, not a take or skip verdict, so the one-second decision fails. | fix: Add a verdict per state (verde Tomalo, amarillo Solo si no hay mejor bajo tu meta 8, rojo No te conviene); keep thresholds as detail.
---
- C11 / G8 / G4 | Silent overlay suppression is hard to diagnose; Help points to Tu día while the supplied suppression category appears in Semáforo settings. | fix: Link Help directly to the affected offer and suppression reason, and show that reason in offer history.
- A3 / A4 / B3 | The external permission handoff prevents end-to-end activation verification in the prototype. | fix: Include Android and return states, followed by separate confirmations for permission, battery status, and first successful offer reading.
- C2 | A one-second yellow-offer decision relies on small threshold and unit text. | fix: Use Aceptable · debajo de tu meta and enlarge net/km and minimum/goal labels.
---
