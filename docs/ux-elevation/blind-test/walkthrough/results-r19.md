6 judges
| task | success (yes/partial/no) | mean taps | mean ease (1–7) |
|---|---|---|---|
| Hoy: kept today + reader | 6/0/0 | 0.0 | 6.2 |
| Offer: worth it? | 5/1/0 | 0.0 | 5.5 |
| Every offer today | 1/5/0 | 1.0 | 4.8 |
| Last week after car | 6/0/0 | 2.0 | 5.8 |
| Which app paid more | 6/0/0 | 1.0 | 6.2 |
| Change tu mínimo | 5/1/0 | 5.5 | 5.5 |
| Overlay stopped | 4/2/0 | 4.2 | 5.0 |
| First day setup | 4/2/0 | 7.3 | 4.7 |
mean ease 5.46 · ease vs Uber 6.5/10 · understand every screen 5.7/10
- B7 | The overlay-missing diagnosis is scattered across B2, B5, B6, B7 and B9. B7 says 'aún funciona' while the driver's overlay has stopped. B9 and B1 stay green while an offer got no overlay, and G4c expl | fix: Add a '¿No ves el semáforo?' link on Hoy's status row that opens one checklist (permiso, batería, pausa, app sin soporte, navegación), each marked OK or Falla w
- C2 | The yellow overlay gives a position ('Entre tu meta y tu mínimo'), not a decision. The driver must compare $7.16 against their own $8 and $6 within a second, and the meta/mínimo gauge text is tiny. | fix: Add a plain delta chip ('+$1.16 sobre tu mínimo · −$0.84 de tu meta') and the word 'Aceptable'. Enlarge the tu meta/tu mínimo labels to at least the size of the
- D1 | Taken offers 17:21 and 16:04 show '$.../km', which looks broken. The 23 offers sit in one flat list with no filters, and the figure is net per km, not what the offer paid. | fix: Show '—' with a reason ('sin km') or compute the value. Add Tomadas, Sin tomar and Sin lectura filter chips, and show the offer fare beside the net per km.
---
- B1 | Status row shows reading status but opens the limits screen (G2b); per-app reading state (Uber ok, DiDi waiting) is hidden in Ajustes > Semáforo (G4) | fix: Show 'Uber ✓ · DiDi esperando oferta' on Hoy and make the row open G4; add a separate 'Cambiar' link next to 'tu meta $8 · tu mínimo $6' that opens G2b
- C6/H6/B2 | The outage cause is told differently: pill gives no cause, notification says 'DiDi, le falta un permiso', Hoy says the phone removed accessibility; navigation-suppressed case (G4c) gives no action | fix: Use one plain cause string everywhere ('Falta el permiso de Accesibilidad'), deep-link notification and B2 button straight to the system toggle, and add 'No nec
- D1 | Offer list rows show unexplained '$.../km' placeholders and only net per km, with no fare or km | fix: Replace placeholder with a labelled state ('esperando pago final') and add 'Tarifa $148 · 12 km' as each row's second line
---
- B1/B7/G4/G8 | Diagnosing a missing overlay is buried and contradictory: the Hoy status row opens limits not diagnostics when 'funcionando', the real diagnosis is 3 taps away, B7 says 'aún funciona', G4 says all is  | fix: Make the Hoy status row always open the Semáforo status screen and move limits to their own row; add a visible '¿No ves el semáforo?' link on Hoy; reword B7/G4b
- C2 | The overlay shows only a $/km figure and colour; the verdict ('Entre tu meta y tu mínimo'), the meta/mínimo bars and 'neto' are tiny or undefined, so I cannot tell in one second whether to take it. | fix: Add one large verdict word on the overlay (Sí / Ojo / No) at about the size of the $/km figure; replace the tiny bars with 'meta $8 · mínimo $6' in larger text;
- D1 | Two taken offers show '$.../km' instead of a figure, 'Sin lectura' rows are cut off by the tab bar, and the screen's time and totals (21:10, 9 trips, $1,026) do not match the Hoy it was opened from (1 | fix: Show the real figure or '—/km' with a one-line reason; group 'sin lectura' rows under a count; add bottom padding so the last row is visible; keep Hoy and Tu dí
---
- B3 | Onboarding never asks the driver for their own meta and mínimo; the overlay judges offers against default $8/$6 the driver did not choose, which breaks the 'your own two limits' promise. | fix: Add a step after the accessibility permission with the G2b controls, prefilled with a city suggestion; on B3 make 'Confirma tus límites ($8 / $6)' the main butt
- E8 | On Monday the Ganancias tab opens on the current week, showing the same $548 as Hoy; last week, the one drivers check, is behind a small '<' arrow. | fix: On Mon–Tue open on the last closed week, or put a 'Semana pasada: $4,980 >' card on E8; make the week switcher a larger 'Esta semana / Semana pasada' control.
- D1 | Some taken offers show '$.../km' with no explanation, and the list only shows net per km, so 'what did each offer pay' cannot be fully answered. | fix: Always show the value, or a labelled state such as 'pendiente: falta el km' with a reason; add the fare (e.g. $148) in small text under each row.
---
- B1 | The status row 'Semáforo funcionando · tu meta $8 · tu mínimo $6' is one tap target that opens the limits screen (G2b), not the semáforo status (G4). The $ values have no /km unit. | fix: Split the row: the status line opens G4 with the reason; the limits get their own target with a pencil icon and /km units.
- C2 | The overlay shows a colour band and $/km neto with no verdict and no peso amount; the colour legend exists only on welcome screen A1. | fix: Show net pesos beside per-km (for example '$86 neto · $7.16/km'), add a one-word verdict for yellow, and repeat the legend on first-run screen B3.
- A3 | The Android wording 'control total de tu dispositivo' sits beside Rechazar or Permitir, and two consecutive 'Abrir ajustes' buttons (A3, A3b) leave it unclear which opens Android settings. | fix: Merge A3 and A3b into one screen with one button, and place the line 'Kompara solo lee tu pantalla' directly beside the dialog mock.
---
- A3/A3b | La salida a Android hace incierta la activación; cuesta distinguir la guía del ajuste real y comprobar que terminó. | fix: Usar Ir a Accesibilidad de Android, presentar la ilustración como ayuda desplegable y acompañar el regreso con una prueba guiada de lectura.
- D1 | El historial contiene importes incompletos y cambia la hora y los totales respecto de B1. | fix: Conservar el estado temporal de B1, completar todas las ofertas y mostrar el importe por km o explicar concretamente por qué falta.
- C2 | Los límites y la explicación del amarillo son pequeños para decidir en un segundo. | fix: Aumentar Supera tu mínimo; debajo de tu meta y los límites de $6 y $8, reduciendo las barras decorativas.
---
