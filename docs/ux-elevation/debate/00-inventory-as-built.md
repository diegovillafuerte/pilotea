# Kompara Android: screen inventory (es-MX, as built)

Everything below comes from the Compose code and `strings.xml`, not from the docs. Line numbers are for the top-level Composable.

**App root** (`KomparaRoot.kt:35`) picks one of four routes:
- `LOADING`: blank screen.
- `ONBOARDING`: the onboarding funnel, with signup inside it.
- `AUTH`: standalone signup, for installs with no session.
- `MAIN`: the 5-tab shell (`KomparaApp.kt:76`).

Theme is dark by default (slate `#0F172A`, emerald `#059669`, Inter). Verdict colours: verde `#1B8A3A`, amarillo `#F2B705`, rojo `#D32F2F`.

**Shell chrome** (`KomparaApp.kt`, `KomparaBottomBar.kt:41`):
- The top bar is only the "K Kompara" wordmark, shown on the 5 tab roots only.
- Pushed screens have no top bar and no in-app back button. They rely on system back, or on re-tapping the active tab (which pops to its root).
- The bottom bar stays visible on every pushed screen, including Paywall, Import, Simulator and Share card.

---

## A. Tab roots

### Inicio (`screens/InicioDashboardScreen.kt:83`)
- **Reached from:** Tab 1, the start destination.
- **Purpose:** This week's net earnings, four metric tiles, tips, and a path to today's detail.
- **Layout top to bottom:**
  1. `WatchdogBanner` (`components/WatchdogBanner.kt:33`), only when the reader has dropped. It is a full-width red card with a warning icon. Title "El lector está apagado", body "Tu teléfono apagó el lector. Reactívalo para seguir viendo el veredicto de cada viaje.", button "Reactivar lector". It is shown on Inicio only.
  2. Header: label `GANANCIA NETA ESTA SEMANA` (hardcoded uppercase in the string), then the big net figure (44sp Black). On the right sit the streak badge "🔥 4 semanas" (or "🔥 1 semana"; hidden at 0) and a share icon with contentDescription "Compartir tu resumen".
  3. `GoalBar`, only if a weekly goal exists. It shows "$3,450.00 de $5,000.00", a progress bar, and "Te faltan $X para tu meta" or "¡Meta alcanzada! 🎉".
  4. `CostNudge` card, only if no cost profile is saved. Title "Configura tus costos para ver ganancias netas", link-style "Configurar costos".
  5. Platform chips "Todas | Uber | DiDi | inDrive | Otra", only if more than one platform has captured data this week.
  6. 2×2 metric grid. The tile labels are "$ por viaje", "$ por km", "$ por hora", "Viajes/hora".
     - Each tile has a "Top X%" green pill under the value when a percentile exists.
     - If the gate is locked, the pill is "🔒 Kompara Premium".
     - The 5th metric, "Tasa de aceptación", is dropped on Inicio.
  7. Benchmarks `PaywallGate`, shown whenever benchmarks are not unlocked. It is a blurred grey 96dp card with a lock, the hint "Mira cómo te comparas con otros conductores de tu ciudad", and the CTA "Probar Kompara Premium" (or "Importa una semana para desbloquear" when premium but unverified).
  8. "Consejos para ti": up to 3 `RecommendationCard`s. They are coloured by type (green check for praise, amber warning, blue info).
  9. Completeness note, "ⓘ Horas estimadas por turnos" or "ⓘ Sin turno detectado — no podemos estimar tus horas todavía".
  10. Link-style text "Ver detalle del día".
- **Numbers/metrics shown:** All formatted by `Formatters` in es-MX.

  | Metric | Format | Example |
  |---|---|---|
  | Net | `$#,###.00` | `$3,450.00` |
  | Per trip | `$#,###.00` | `$90.79` |
  | Per km | `$x.xx/km` | `$8.40/km` |
  | Per hour | `$x.xx/h` | `$153.30/h` |
  | Trips per hour | `x.x/h` | `1.7/h` |

  - Missing values show "—". Every metric is for the current Monday-anchored week, captured data only.
  - Tip bodies are written by `RecommendationEngine` (in `:metrics`, not `strings.xml`):
    - Free: "Dejaste ir buenos viajes", "Tus mejores horas" ("…el viernes de 19:00 a 20:00: $340 netos. Maneja más en ese horario."), "Estás tomando viajes en rojo", "Estás rechazando casi todo", "¡N semanas seguidas!".
    - Premium: "Estás en el top X%", "Tu pago por viaje es bajo", "Tu comisión está alta", "Uber te paga más por km".
- **Actions:**
  - Share icon → Share card.
  - Cost nudge → Tus costos.
  - "Ver detalle del día" → Detalle del día, today only.
  - Chips filter the tiles.
  - Reactivar lector → system Accessibility settings.
  - Gate CTA → Paywall (surface BENCHMARKS), or Import for premium-but-unverified users.
  - Empty-state CTA → Simulator.
  - Tapping a tip card does nothing.
- **States:**
  - Loading: blank.
  - Empty (no captured trips this week): the `EmptyState` component (`components/EmptyState.kt:34`), a 72dp Home icon with "Aún no hay números", "Activa el lector y maneja. Tus números aparecen solos.", and the button "Probar el lector". That button goes to the simulator, not the Lector tab.
  - With data.
  - Free vs premium vs unverified, for badges, the tease and tips.
  - Percentiles appear only when a single concrete platform is resolved; "Todas" with several platforms shows none.
- **Complexity smell:**
  - Up to ten stacked blocks, and the gate tease shows for every free user even when no badges exist.
  - There is no UI anywhere to set the weekly goal (`setWeeklyNetGoal` is never called from a screen; `dashboard_goal_set_cta` is unused), so the goal bar never appears for real users.
  - The "Todas" view hides the percentiles, which is confusing.
  - The premium-tip tease branch can effectively never fire. When RECOMMENDATIONS is locked the engine is fed no premium inputs.
  - A week with only imported data reads as "Aún no hay números".

### Comparar (`screens/CompararScreen.kt:74`)
- **Reached from:** Tab 2. It is also the target of Inicio's cross-link and the share intent chain.
- **Purpose:** A weekly benchmark against the city and the two platforms.
- **Layout top to bottom:**
  1. Week pill, e.g. "Semana del 1–7 jun ▾". It is a dropdown of every week with data, tappable only if there are 2 or more weeks.
  2. Hero card (free):
     - Wordmark "K Kompara" plus a green share icon.
     - Headline "Le ganas al 78% de los conductores de tu ciudad", or "Aún estamos midiendo cómo te comparas con tu ciudad".
     - A 20-person icon `PercentileBar`.
     - Row "Tu lugar esta semana" with a "Top 22%" pill.
     - Optional "Datos de muestra".
  3. Optional note: "Solo manejas Uber — «Tú» es tu Uber. Igual te comparamos con el promedio de cada app."
  4. `PaywallGate` (COMPARE) wrapping:
     - The benchmark table. Header: `Métrica | Tú | Uber | DiDi | Lugar`. Six rows: Neto, $/hora, $/km, $/viaje, Viajes/h, Comisión (with "↓ menos es mejor"). The legend reads "Tú = tu mezcla de apps · promedio = el conductor típico de tu ciudad · tu lugar = qué tan arriba estás. «—» = la app no reporta."
     - "Dónde puedes ganar más": up to 3 cards. Examples: "Aún no manejas DiDi", "Los conductores ganan 18% más con Uber", "El top 20% gana $X/h más que tú".
  5. Cross-link row "Tus ganancias y tus mejores momentos … Inicio ›".
  6. Footnote "Pronto: cómo te comparas por día y por hora".
- **Numbers/metrics shown:**
  - Net, per hour and per km are whole pesos or one decimal (`$4,200`, `$186/h`, `$9.2/km`); trips per hour is `2.3/h`; commission is `NN%`.
  - The Uber and DiDi columns are the city population mean per platform, not the driver's own per-platform results.
  - The Lugar cell is "Top X%" with a 3dp bar.
- **Actions:**
  - Week pill → dropdown.
  - Share icon → Share card.
  - Gate CTA → Paywall (COMPARE), or Import for premium-but-unverified.
  - Empty CTA → Import.
  - Cross-link → Inicio.
- **States:**
  - Loading: renders nothing.
  - No data: `EmptyState` with "Aún no hay nada que comparar", "Trae tus ganancias de Uber y DiDi — o activa el lector — y aquí verás cuál te paga mejor.", CTA "Traer mis ganancias".
  - Single platform: the note in item 3.
  - Locked or needs-verification: the table keeps its shape with every value masked to "—" and opportunities removed, so no real numbers leak through the blur.
  - Hero percentile missing.
  - The hero is free even when the table is locked.
- **Complexity smell:**
  - A five-column table at 12sp is very dense. "Uber" and "DiDi" are city averages, but the legend calls them "promedio" while no column carries that name.
  - "Tú / Lugar / mezcla de apps" is jargon.
  - Column headers "Uber" and "DiDi" are hardcoded, not in `strings.xml`.
  - The empty state sits inside a scroll container.
  - The "Pronto:" footnote is promise copy.
  - 12 strings for an earlier verdict design (`comparar_verdict_*`, `winner_badge`, `not_comparable*` and so on) are dead.

### Lector (`screens/LectorScreen.kt:88`, content at :119)
- **Reached from:** Tab 3. This is the only place that starts screen capture and the only entry point to the OCR disclosure.
- **Purpose:** Turn the reader on and manage it.
- **Layout top to bottom:**
  - **Accessibility OFF** (`DisconnectedLector`, :168):
    - A 72dp circle with a play icon.
    - "El lector está apagado".
    - "Enciéndelo y Kompara te dirá al instante cuánto ganas de verdad con cada viaje."
    - Primary button "Encender lector".
    - Hint "Después de encenderlo, aquí también activas el lector para DiDi."
  - **Accessibility ON** (`ConnectedLector`, :223):
    1. Header "Lector".
    2. "Kompara lee las ofertas en tu pantalla y muestra el semáforo encima. Todo se queda en tu teléfono."
    3. Status card: dot + "Lector para DiDi" + "Activo" (emerald) or "Detenido".
    4. A static 220dp mock "Oferta entrante · Uber / $148 / 16 km · 22 min · efectivo" with a mini green chip ($9.20/km, $165/h). All of it is hardcoded text.
    5. Primary "Iniciar lector para DiDi" (or "Reiniciar lector para DiDi" when running).
    6. Secondary buttons: "Configuración de accesibilidad", "Probar en el simulador", "Ajustar mi semáforo".
- **Numbers/metrics shown:** None live. The mock numbers are fake.
- **Actions:**
  - "Encender lector" → system Accessibility settings.
  - "Iniciar/Reiniciar" → opens the disclosure `AlertDialog` (below), then `OcrConsentActivity`, then the system "start recording" prompt.
  - "Configuración de accesibilidad" → system Accessibility settings.
  - "Probar en el simulador" → Simulator.
  - "Ajustar mi semáforo" → Tu semáforo.
- **States:** Accessibility off vs on; screen reader Activo vs Detenido. There is no loading or error state.
- **Complexity smell:**
  - Four buttons on one screen. The one live state is labelled "Lector para DiDi", while the intro says Uber and DiDi are read the same way (screen capture). The screen mixes two concepts: the accessibility service and the screen-capture reader.
  - On API 30+ the silent lane makes capture start with no system prompt, so tapping Iniciar may seem to do nothing after the dialog.
  - The mock card with fake numbers is decoration on a safety-relevant screen.

### Fiscal (`screens/FiscalScreen.kt:60`)
- **Reached from:** Tab 4.
- **Purpose:** IMSS coverage tracker plus an estimated ISR/IVA withholding summary with PDF export.
- **Layout top to bottom:**
  1. Title "Cobertura IMSS". This is visible even when locked, although the tab is labelled "Fiscal".
  2. Everything below sits inside `PaywallGate` (FISCAL), with the hint "Sigue tu avance hacia la cobertura del IMSS":
     - Month chips "Mes": the current month plus 5 past months, e.g. "Junio 2026".
     - "Umbral por plataforma este mes: $8,364.00". If the remote config failed, add "Usamos valores de referencia; no pudimos conectarnos para actualizar."
     - Per-platform card, or the empty note "Sin ingresos registrados este mes" with "Cuando captures o importes viajes, verás aquí tu avance hacia el umbral del IMSS por cada plataforma." Each card has the platform name and a status chip: "Cubierto este mes", "En camino" or "Difícil este mes"; past months read "Cubierto" or "No cubierto". Under that: "$5,200.00 de $8,364.00", a progress bar (green, yellow or red), "Te faltan $3,164.00 y quedan 11 días" (or "…y queda 1 día"), "Estimado a fin de mes: $9,100.00", or "Llevas $X — ya superaste el umbral".
     - "Resumen fiscal" with chips "Mes | Año (acum.)". Per-platform cards list Bruto, Neto, "ISR retenido (est.)", "IVA retenido (est.)" and a bold "Total retenido (est.)". Then a totals card ("Totales del mes" or "Acumulado del año"), the footnote "* DiDi no muestra su comisión, así que no podemos descontarla y tu neto aparecerá igual al bruto. Las retenciones se calculan sobre el bruto y no cambian.", and a full-width "Exportar PDF" button (or "Generando…"). Hint: "Comparte tu resumen por WhatsApp o correo, para tu contador o una solicitud de crédito."
     - Explainer card "¿Qué es la cobertura del IMSS para conductores de plataforma?" (3 paragraphs).
     - Explainer card "Régimen de plataformas: ¿qué te retienen?" (3 paragraphs: the 2.1% ISR / 8% IVA rule, the CSF and RFC requirement, and "Pregunta abierta: la reforma laboral del IMSS … aún no están del todo armonizados…").
     - "ⓘ Estas son estimaciones con base en tus datos, no asesoría legal ni fiscal. Confirma tu situación con el IMSS o un contador."
- **Numbers/metrics shown:** MXN with 2 decimals. Threshold is a minimum monthly wage. Withholding rates are 2.1% ISR and 8% IVA.
- **Actions:**
  - Month chip, Mes/Año chip, Exportar PDF (renders a PDF and opens the system share chooser).
  - Gate CTA → Paywall (FISCAL).
- **States:** Loading (blank), locked (whole body blurred), empty month, past vs current month, default config, exporting.
- **Complexity smell:**
  - The densest screen in the app, roughly 12 stacked blocks, and half of it is legal text. Three different concepts (coverage, withholding, export) share a tab titled "Fiscal" whose header says "Cobertura IMSS".
  - The legal copy is 7+ paragraphs, flagged TODO(legal-B038) in the code.
  - A free driver sees a fully blurred wall with one hint line.
  - "Umbral", "CSF", "ISR", "IVA" and "RFC" are unexplained jargon.

### Ajustes (`screens/PlaceholderScreens.kt:42`)
- **Reached from:** Tab 5.
- **Purpose:** The settings hub.
- **Layout top to bottom:** Title "Ajustes", then flat tonal rows with a "›" chevron:
  1. "Tu cuenta"
  2. "Tus costos"
  3. "Tu semáforo"
  4. "Historial"
  5. "Ver el simulador"
  6. "Invita y gana"
  7. "Ayuda"

  Then switch rows:
  - "Resumen de IMSS a fin de mes" (default on)
  - "Recordatorio semanal para compartir" (default on)
  - Debug builds only: "Premium (prueba) — desbloquea todo"
- **Numbers/metrics shown:** None.
- **Actions:** Each row opens the screen of the same name. The toggles persist immediately.
- **States:** Single state; the debug row is build-dependent.
- **Complexity smell:** A flat list of 7 equal-weight rows with no sections or subtitles. "Historial" (a data screen) and the simulator are buried here. There is no weekly-goal or platform setting, which is a gap (see Inicio). The `ajustes_empty_*` "en construcción" strings are dead.

---

## B. Pushed screens (inside the shell)

### Tus costos (`screens/CostProfileScreen.kt:48`)
- **Reached from:** Inicio cost nudge, Ajustes.
- **Purpose:** Capture vehicle costs so net earnings are real.
- **Layout top to bottom:**
  1. Title "Tus costos".
  2. "Con esto calculamos tu ganancia neta de verdad: lo que ganas menos lo que te cuesta manejar."
  3. Card "Vehículo eléctrico" with a switch.
  4. If gas: "Rendimiento (km por litro)", "Precio de gasolina (MXN por litro)". If EV: "Consumo (kWh por 100 km)", "Costo de carga (MXN por kWh)".
  5. "Mantenimiento (MXN por km)", "Seguro (MXN por día)", "Renta o financiamiento (MXN por día)".
  6. Preview card: "Tu costo por km" over a hero "$2.15/km", then "En un viaje de $120.00 y 12.0 km, te quedan $X netos".
  7. Button "Guardar costos".
- **Numbers/metrics shown:** The preview uses a fixed sample trip ($120, 12 km). Cost per km is energy plus maintenance; insurance and rent are not in the preview.
- **Actions:** Save persists the profile, recomputes about 2 years of history, and pops back to the previous screen. The "Costos guardados" string is unused.
- **States:** Pre-filled from the saved profile. EV consumption fields come back blank on edit. No validation messages.
- **Complexity smell:** Seven bare numeric fields with no placeholders or examples. Drivers may not know their km/L. Insurance and rent have no visible effect on the preview or the chip, and nothing says why they are asked for.

### Tu semáforo (`screens/ThresholdsScreen.kt:56`)
- **Reached from:** Ajustes, Lector.
- **Purpose:** Set the green and red floors and choose the deciding metric.
- **Layout top to bottom:**
  1. Title "Tu semáforo".
  2. Intro: "Define cuándo un viaje sale verde, amarillo o rojo: verde si gana al menos tu mínimo verde, rojo si gana menos que tu mínimo rojo, y amarillo entre los dos. Tus mínimos ya descuentan gasolina y desgaste, y el mismo semáforo aplica a todas tus apps." (about 55 words)
  3. Label "¿QUÉ DECIDE TU SEMÁFORO?" (forced uppercase).
  4. Segmented control "Por kilómetro | Por hora".
  5. Card with two slider rows: "Verde desde" ($8.1/km) and "Rojo por debajo de".
  6. Legend: three `VerdictBadge` pills "Verde", "Amarillo", "Rojo".
  7. Hint "El lector y el simulador usan estos mínimos al instante; no hay que guardar nada."
  8. Text button "Volver al valor típico de Valle de Mexico (CDMX + Edomex)".
- **Numbers/metrics shown:**
  - km: $3.0–$20.0, step 0.5, shown as `$8.0/km`.
  - hour: $40–$300, step 5, shown as `$161/h`.
  - The red floor can never exceed the green floor.
  - Defaults are per city: CDMX 8.05 / 161, Monterrey 7.7 / 154, and so on. Red defaults to 75% of green.
- **Actions:** Sliders save on release. The segmented control saves the metric. Reset restores the city default.
- **States:** Single state.
- **Complexity smell:**
  - The intro is wordy. "Neto", "mínimo verde" and "mínimo rojo" are abstract, and two metrics times two floors is four numbers.
  - The reset label uses the long city name.
  - "Qué decide tu semáforo" explains little because `thresholds_metric_hint` is unused.
  - The three legend badges look like buttons but do nothing.

### Ayuda (`screens/HelpScreen.kt:39`)
- **Reached from:** Ajustes.
- **Purpose:** Offline FAQ.
- **Layout top to bottom:** Title "Ayuda"; "Respuestas rápidas a lo que más preguntan los conductores. Toca una pregunta para abrirla."; then 7 expandable cards (chevron up/down, animated):
  1. "¿Cómo activo el lector?"
  2. "¿Qué significa el semáforo?"
  3. "Mi teléfono apaga el lector, ¿qué hago?"
  4. "¿Qué lee Kompara y a dónde van mis datos?"
  5. "¿Para qué es mi cuenta?"
  6. "¿Cuánto cuesta Kompara?"
  7. "Algo no funciona, ¿qué reviso?"
- **Numbers/metrics shown:** None.
- **Actions:** Tap a card to toggle its answer. There is no search and no contact option.
- **States:** All cards start collapsed.
- **Complexity smell:**
  - Answers are 50–110 word paragraphs.
  - The semáforo answer is a dense wall that mentions long-press, "mantenlo presionado" and a four-minimum editor.
  - The "Algo no funciona" answer refers to "el botón de reporte" that does not exist anywhere in the app.
  - The help text says to enable "Sin restricciones" for battery in Settings.

### Tu cuenta (`screens/AccountScreen.kt:63`)
- **Reached from:** Ajustes.
- **Purpose:** Edit the profile, log out, delete the account.
- **Layout top to bottom:**
  1. Title "Tu cuenta".
  2. Read-only "WhatsApp" field with prefix "+52" and value "55 1234 5678".
  3. "Tu nombre".
  4. "Tu ciudad" dropdown. The 10 cities are "Valle de Mexico (CDMX + Edomex)", Monterrey, Guadalajara, "Puebla-Tlaxcala", Toluca, Tijuana, Leon, Queretaro, Merida, Cancun (displayed without accents).
  5. "Cambios guardados." in green, or an error.
  6. Buttons: "Guardar cambios" (primary), "Cerrar sesión" (secondary), "Eliminar mi cuenta" (red text).
- **Numbers/metrics shown:** None.
- **Actions:**
  - Save sends a PATCH.
  - Logout clears the session; there is no confirmation. The root then flips to the signup gate.
  - Delete opens the dialog "¿Eliminar tu cuenta?" with "Se borrarán de forma permanente tu cuenta y los datos que guardaste en la nube. Esta acción no se puede deshacer." and the buttons "Eliminar" (red) and "Cancelar".
- **States:** Busy (inputs disabled), saved, save error ("No pudimos guardar tus cambios. Revisa tu conexión e inténtalo de nuevo."), delete error ("No pudimos eliminar tu cuenta. Revisa tu conexión e inténtalo de nuevo.").
- **Complexity smell:** City is also needed for benchmarks, but nothing says that changing it resets the comparison. The destructive row sits right next to the logout row.

### Historial (`screens/HistoryScreen.kt:56`)
- **Reached from:** Ajustes only.
- **Purpose:** List of weeks with their source.
- **Layout top to bottom:**
  1. Row: title "Historial" and a small tonal button "Importar semana".
  2. A card per week: "$3,450.00" (bold), "Semana del 8–14 jun" (muted), and a neutral chip "Capturado" or "Importado".
  3. If gated and older weeks exist, a blurred block of the older rows with a lock, "La versión gratis muestra esta semana y la anterior. Con Premium ves todas tus semanas." and "Probar Kompara Premium".
- **Numbers/metrics shown:** Net per week, summed across platforms; an imported row wins over a captured one. The free window is the two most recent weeks with data.
- **Actions:** Tap a row → Resumen de la semana. "Importar semana" → Import. Gate CTA → Paywall (HISTORY).
- **States:**
  - Loading (blank).
  - Empty: `EmptyState` "Tu historial está vacío", "Cada semana que manejes con el lector activo aparecerá aquí.", plus a full-width "Importar semana" button.
  - Free-locked tail.
  - Unlocked.
- **Complexity smell:** Only the net is shown, so the list gives little reason to open a week. Locked rows are non-tappable and blurred. `gate_hint_history` and `history_free_limit_title` are unused.

### Resumen de la semana (`screens/WeekSummaryScreen.kt:34`)
- **Reached from:** A Historial row.
- **Purpose:** One week's stats.
- **Layout top to bottom:**
  1. Title "Semana del 8 jun", a different label from the Historial row.
  2. Platform chips if the week has more than one platform.
  3. Five key/value rows: Neto, Bruto, Viajes, Distancia, Horas en línea.
  4. Five metric cards, each with a "Top X%" pill and a 20-person bar (or the locked 🔒 stand-in).
  5. If premium but unverified: "Importa una semana para desbloquear".
  6. Button "Compartir".
- **Numbers/metrics shown:** The same metric formats as Inicio, plus a total km line (`123.4 km`) and hours (`22.5 h`).
- **Actions:** "Compartir" → Share card, which is always the current month, not this week. The verify button → Import.
- **States:**
  - Empty: reuses "Sin actividad este día" / "No registramos viajes ni ofertas en esta fecha." for a week.
  - Locked: the bars show 🔒 with no CTA for a free (LOCKED) driver, which is a dead end. Only the NEEDS_VERIFICATION state gets a button.
- **Complexity smell:** The source badge is computed but not shown. The "Spacer(Modifier.padding(top = 0.dp))" is dead noise. Inicio shows 4 tiles with pills only, while this screen shows 5 cards with bars, so the same data looks different on the two screens.

### Detalle del día (`screens/DayDetailScreen.kt:47`)
- **Reached from:** "Ver detalle del día" on Inicio. It always opens today; the `day/{day}` variant exists but nothing navigates to it.
- **Purpose:** Today's shifts, offers and best hours.
- **Layout top to bottom:**
  1. Title, e.g. "mié 10 jun".
  2. Five metric cards, with no percentiles.
  3. The completeness note (same two strings as Inicio).
  4. "Turnos": rows "08:00–12:30 · 5 viajes" with the net in emerald on the right. An open shift reads "en curso".
  5. "Ofertas": a funnel row "12 vistas · 5 aceptadas · 6 rechazadas", then one row per offer "14:32 · $85.00" with a verdict badge "Verde / Amarillo / Rojo".
  6. "Mejores horas": the top 3 rows, "18:00–19:00 · 4 viajes", with the net.
- **Numbers/metrics shown:** Clock times (HH:mm), MXN with 2 decimals, counts.
- **Actions:** None. It is read-only.
- **States:** Loading (blank), loaded, empty ("Sin actividad este día" / "No registramos viajes ni ofertas en esta fecha.").
- **Complexity smell:** This is the only place the offer-level verdicts are visible after the fact. The list is long and there is no way to move to another day. "Rechazadas" includes expired offers, and pending offers are not shown, so the three numbers may not add up to "vistas".

### Paywall (`paywall/PaywallScreen.kt:65`)
- **Reached from:** Any `PaywallGate` CTA, with the originating surface name as a nav arg (analytics only).
- **Purpose:** Sell Kompara Premium.
- **Layout top to bottom:**
  1. Top-right close ✕ (no "Quizá después").
  2. Scrollable body: title "Kompara Premium"; "Si te ayuda a ganar $200 más a la semana, se paga solo. Premium te muestra dónde estás dejando de ganar."; 4 check rows ("Percentiles: cómo te comparas con tu ciudad", "Comparativa Uber vs. DiDi", "Historial completo de todas tus semanas", "Seguimiento fiscal e IMSS por plataforma"); then the price card.
  3. Price card: "Premium" … "$79.00/mes" (price from Google Play) and "Incluye prueba gratis. Cancela cuando quieras." (or "Cancela cuando quieras.").
  4. Fixed footer: the CTA "Empezar prueba gratis" (or "Suscribirme"), a text button "Restaurar compras", and the small legal line "La suscripción se renueva automáticamente hasta que la canceles. Términos y aviso de privacidad pendientes de revisión legal."
- **Numbers/metrics shown:** Price and trial come from Play at runtime.
- **Actions:** CTA launches Play billing. Restore re-queries purchases. ✕ pops back. Snackbar messages: "Listo, restauramos tu suscripción Premium.", "No encontramos una suscripción activa para restaurar.", "Ya tienes Premium activo.", "No se pudo abrir la compra. Inténtalo de nuevo."
- **States:**
  - Loading: "Cargando el plan…".
  - Ready.
  - Play unavailable: "Google Play no está disponible en este dispositivo, así que no se puede iniciar la compra. Intenta desde un teléfono con Play Store." Restore stays available; there is no CTA.
- **Complexity smell:**
  - The "$200 más a la semana" claim is a promise.
  - The visible legal footer says terms are "pendientes de revisión legal", which is placeholder copy that would ship as-is.
  - The feature list omits the premium tips (Consejos), which are also gated.
  - A free-trial promise is made with no card needed ("sin pedir tarjeta" in Ayuda), which Google Play subscriptions cannot guarantee.

### Importar semana (`imports/ImportScreen.kt:71`)
- **Reached from:** Historial "Importar semana", Comparar empty CTA, "Importa una semana para desbloquear" gates, and the OS Share sheet via `ShareImportActivity`.
- **Purpose:** Pull a past week's earnings in from a PDF or screenshots.
- **Layout top to bottom, by state:**
  - **Loading:** spinner.
  - **SignedOut:** "Para importar necesitas tu cuenta" + "Importar tus semanas requiere una cuenta para guardarlas de forma segura. Inicia sesión con tu número de WhatsApp y vuelve a intentarlo." + "Volver". This is effectively unreachable because the app forces signup.
  - **Picking, platform step:** title "Importar semana"; "Trae semanas que el lector no capturó: tu historial de antes de instalar Kompara o turnos que se te pasaron."; "¿De qué plataforma?"; four cards:
    - "Uber — PDF semanal" with "Recomendado: con el PDF semanal que Uber te envía por correo obtenemos casi todos tus datos."
    - "Uber — captura de pantalla" with "Con la captura del desglose de tarifa obtenemos solo una parte de tus datos. Para mejores resultados usa el PDF."
    - "DiDi — 2 capturas" with "Necesitamos 2 capturas: tu pantalla de ganancias y el tablero. Así sacamos casi todos tus datos."
    - "inDrive — captura de pantalla" with "Sube una captura de tu resumen de ganancias en inDrive."
  - **Picking, file step:** the platform title and hint; a primary "Elegir archivo" (or, for DiDi, "1. Pantalla de ganancias" and "2. Tablero" rows, each showing "Archivo listo" once picked, each with a secondary "Elegir archivo"); then "Elegir otra plataforma". It auto-submits when the file(s) are chosen.
  - **SharedReady:** "Recibimos tu archivo" / "Detectamos: Uber — PDF semanal (1 archivo(s)). Continúa para leer tus cifras." / "Continuar" / "Elegir otra plataforma".
  - **Uploading:** label `PROCESANDO ARCHIVOS` with a 4-step checklist ("Leyendo archivos…", "Extrayendo datos con IA…", "Calculando tus ganancias…", "Comparando con conductores…"), a progress bar and "Esto toma entre 5 y 15 segundos". The step advance is a cosmetic 900ms timer.
  - **Review:** "Revisa tus datos" / "Esto leímos de tu resumen. Si se ve bien, guárdalo en tu historial."; week label; whichever of Neto, Bruto, Viajes, Distancia and Horas en línea were parsed; "Datos extraídos NN %" with a bar; "Guardar" and "Descartar".
  - **Saved:** "¡Semana importada!" + week label + "Ya aparece en tu historial con la etiqueta Importado." + "Listo".
  - **Error:** "No se pudo importar", the backend's Spanish message, "Intentar de nuevo" (only for 5xx or transport errors) and "Volver". The generic transport message in code is "No se pudo conectar. Revisa tu conexion e intentalo de nuevo." (no accents).
- **Numbers/metrics shown:** Metrics are MXN 2dp, km 1dp, hours 1dp, completeness `NN %`. Accepted files are PDF, PNG, JPEG, WebP, up to 10 MB.
- **Actions:** Document picker, then a dry-run upload, then Save, which uploads again for real.
- **States:** The nine states above.
- **Complexity smell:**
  - There is no screen chrome back affordance; "Elegir otra plataforma" is the only way back.
  - Preview and confirm each run the AI parse (two waits of 5–15 s).
  - Four platform options with different quality levels is a lot for a decision, and the "AI" mention in "Extrayendo datos con IA…" is a privacy-sensitive line in a privacy-first app.
  - The share-target path shows two screens before any upload.

### Invita y gana (`referral/ReferralScreen.kt:51`)
- **Reached from:** Ajustes, or the deep link `kompara://referral?code=X`.
- **Purpose:** Share your code and redeem someone else's.
- **Layout top to bottom:**
  1. Title "Invita y gana".
  2. "Comparte tu código con otros conductores. Cuando lo usan, ¡los dos ganan días de premium gratis!"
  3. Code card: "Tu código" over a 40sp letter-spaced code.
  4. "Copiar código" (outlined) and "Compartir por WhatsApp" (primary).
  5. Two stats: "N conductores invitados" and "N días premium ganados".
  6. "¿Te recomendaron?" / "Ingresa el código que te compartió otro conductor." / field "Código de invitación" / "Canjear código". Success: "¡Listo! Ganaste N días de premium."
- **Numbers/metrics shown:** Counts (integers).
- **Actions:** Copy shows a toast "Código copiado". Share opens WhatsApp, or the system chooser if WhatsApp is absent. The message is "¡Únete a Kompara, la app para conductores! Usa mi código X al registrarte y los dos ganamos premium gratis. Descárgala: https://play.google.com/…". Redeem is disabled while the field is blank or a request is in flight.
- **States:** Loading, SignedOut ("Necesitas tu cuenta" … "Volver"), load error (backend message + "Reintentar"), Ready, redeem Idle/Submitting/Error (backend message)/Success.
- **Complexity smell:** The redeem form is below the fold under a large share section. The intro doesn't say how many days.

### Tu mes en Kompara / share card (`share/ShareCardScreen.kt:54`)
- **Reached from:** Inicio share icon, Comparar hero share icon, Resumen de la semana "Compartir", and the Monday week-close notification.
- **Purpose:** Preview and share a brag card.
- **Layout top to bottom:**
  1. Title "Tu mes en Kompara".
  2. Spinner while loading.
  3. Emerald-gradient hero:
     - "K Kompara · Junio".
     - "Ganancia neta del mes" and the 46sp net (or "••••••" when amounts are hidden).
     - A 2×2 grid: Tu lugar ("Top 22%" or "—"), Mejor app, Mejor día, Racha ("🔥 4 sem").
  4. Button "Compartir en WhatsApp" with a share icon.
  5. Switch row "Ocultar montos al compartir".
- **Numbers/metrics shown:** Monthly net, best-percentile flex, best platform by net, best weekday, streak.
- **Actions:** Share writes a separately rendered PNG and opens WhatsApp, or the system chooser. The caption is "{Mes año} · Tu mes en Kompara / Top 22% en CDMX 🚀 / Hecho con Kompara · descárgala gratis". Hiding amounts persists as the default.
- **States:** Loading or loaded.
- **Complexity smell:**
  - It is always the month card, even when opened from the week summary or the "Tu resumen de la semana está listo" notification.
  - The on-screen preview is not what gets shared. The shared PNG is a different dark card (`ShareCardRenderer`) with "Mi mes manejando", "Descárgala gratis · kompara.mx" and a footer wordmark.
  - The story/landscape variant toggle exists in the ViewModel but has no UI (`share_card_variant_*` unused).
  - The "Top X%" flex is ungated by design.

### Simulador de viajes (`overlay/simulator/SimulatorScreen.kt:61`)
- **Reached from:** Ajustes "Ver el simulador", the Lector tab, the Inicio empty state, and onboarding "Probar el lector" (the first screen after onboarding).
- **Purpose:** Show the real chip on demo offers.
- **Layout top to bottom:**
  1. Title "Simulador de viajes".
  2. "Así se ve Kompara mientras manejas: te llega un viaje y, al instante, te decimos cuánto ganas de verdad — después de gasolina y desgaste."
  3. Chips "Uber | DiDi".
  4. Mock offer card "Nuevo viaje · Uber" showing the fixture's lines, with the real `VerdictChipUi` in the bottom-right corner.
  5. Segmented control "Conviene | Regular | No conviene".
  6. Headline in the verdict colour: "Verde: ganas $15.00/km neto", "Amarillo: $7.75/km neto — está en el límite" or "Rojo: solo $4.36/km neto".
  7. A "why" paragraph (`sim_why_good`, `sim_why_marginal`, `sim_why_bad`).
  8. Row "neto por km: …" and "bruto por km: …".
  9. Threshold playground: "Tu mínimo por km" (or "por hora"), a 28sp value, a slider, and "Mueve tu mínimo y mira cómo cambian los colores de los tres viajes al instante. Se guarda y es el mismo que usa el lector."
- **Numbers/metrics shown:** Real engine output from the driver's actual cost profile and thresholds.
- **Actions:** Platform chips, the segmented control and the slider. The slider is not a sandbox: it persists the real green floor. Long-pressing the embedded chip opens the quick sheet.
- **States:** The three steps are fixed per platform.
- **Complexity smell:**
  - Three vocabularies for the same thing: segments "Conviene / Regular / No conviene", badge and chip "Verde / Amarillo / Rojo", and headlines "Verde: … / Amarillo: … / Rojo: …".
  - The "why" text follows the demo shape, not the live level, so it can contradict the chip after the slider moves.
  - The slider silently changes real settings.

### Standalone Detalle de la semana / Day variants
Both are described above. `day/{day}` exists in the graph but has no caller, and there is no week navigation.

---

## C. Onboarding and auth (outside the tab shell)

The funnel is hosted by `OnboardingNavGraph` (`onboarding/OnboardingNavGraph.kt:63`). Its chrome is a back chevron, a thin progress bar and a "n/5" counter.

### Pitch, 3 pages (`onboarding/OnboardingScreens.kt:101`)
- **Reached from:** First launch.
- **Purpose:** Value pitch.
- **Layout top to bottom:** There is no chrome on this screen. A pager of three pages with the K logomark tile, a title and a body, then page dots and the primary button.
  1. "Entérate si conviene antes de aceptar" / "Kompara lee la oferta en tu pantalla y te dice al instante, con un semáforo, si ese viaje te deja ganancia de verdad — antes de que toques "Aceptar"."
  2. "Gana hasta 30% más por turno" / "Rechazar los viajes que no convienen sube tu ganancia por hora. Es el promedio reportado por apps similares; tus resultados dependen de tu zona y tus horarios."
  3. "Lo que ves en tu pantalla se queda en tu teléfono" / "Todo el cálculo ocurre en tu celular. Kompara no vende ni comparte lo que aparece en tu pantalla; tu cuenta solo guarda tu configuración y tus totales."
- **Numbers/metrics shown:** "30%" claim.
- **Actions:** The button reads "Continuar" on pages 1–2 and "Empezar" on page 3. Swiping also works.
- **States:** Page 1 / 2 / 3.
- **Complexity smell:** The "30% más por turno" claim cites unnamed "apps similares". There is no skip (`onb_saltar` is unused).

### Signup: Teléfono (`auth/SignupScreens.kt:51`, step at :65)
- **Reached from:** After the pitch (progress 1/5), or standalone when there is no session (`AUTH`, no progress chrome).
- **Purpose:** Collect the WhatsApp number.
- **Layout top to bottom:** Title "Crea tu cuenta"; "Tu cuenta guarda tu configuración y tus números de forma segura, para que no pierdas nada si cambias de teléfono. Solo necesitas tu número de WhatsApp."; field "Número de WhatsApp" with the prefix "+52 "; the inline error "Revisa tu número: deben ser 10 dígitos, por ejemplo 5512345678."; privacy line "Usamos tu número únicamente para tu cuenta. Lo que el lector ve en tu pantalla se queda en tu teléfono."; footer CTA "Recibir código por WhatsApp".
- **Numbers/metrics shown:** None.
- **Actions:** The CTA is disabled until the number is valid (it accepts 10 digits, or a +52, 52 or legacy 521 prefix). It requests an OTP.
- **States:** Error "No pudimos enviar el código. Revisa tu conexión e inténtalo de nuevo."
- **Complexity smell:** Account creation is the first real step before any value is shown. There is no explanation of what WhatsApp will send.

### Signup: Código
- **Reached from:** After a successful OTP request.
- **Purpose:** Verify the code.
- **Layout top to bottom:** Title "Escribe el código"; "Te enviamos un código de 6 dígitos por WhatsApp al +52 55 1234 5678."; the 6-cell OTP input; debug-only "Modo dev: ingresa 000000 para entrar sin backend."; a row with "Cambiar número" on the left and "Reenviar código" or "Reenviar en 30 s" on the right; CTA "Confirmar código".
- **Numbers/metrics shown:** A 30-second resend cooldown.
- **Actions:** Confirm, Reenviar and Cambiar número.
- **States:**
  - Wrong or expired: "El código no es válido o ya venció. Revísalo o pide uno nuevo."
  - Network: "No pudimos verificar el código. Revisa tu conexión e inténtalo de nuevo."
  - `auth_code_hint` is unused.
- **Complexity smell:** There is no auto-submit when the 6th digit is typed.

### Signup: Perfil
- **Reached from:** After a verified code. Also the re-entry point if the app was killed mid-flow with a live session.
- **Purpose:** Name and city.
- **Layout top to bottom:** Title "Cuéntanos de ti"; "Con tu ciudad comparamos tus ganancias con las de otros conductores cerca de ti. Puedes cambiarla cuando quieras."; field "Tu nombre (opcional)"; dropdown "Tu ciudad" (defaults to "Valle de Mexico (CDMX + Edomex)"); CTA "Guardar y continuar"; text button "Completar después".
- **Numbers/metrics shown:** None.
- **Actions:** Save or skip. Either one finishes signup. Choosing a city also seeds the default thresholds and benchmarks.
- **States:** Busy; error "No pudimos guardar tu perfil. Revisa tu conexión e inténtalo de nuevo."
- **Complexity smell:** Skipping leaves the default city silently, which affects the verdict floors and the benchmark. The city list is shown without accents.

### Divulgación (`onboarding/OnboardingScreens.kt:188`)
- **Reached from:** Signup done (progress 2/5).
- **Purpose:** Play-policy prominent disclosure for the accessibility service.
- **Layout top to bottom:** Title "Antes de continuar: qué hace Kompara"; "Para darte el veredicto de cada viaje, Kompara necesita usar el servicio de accesibilidad de Android. Lee esto con calma antes de aceptar."; four cards:
  - "Qué lee": "Kompara lee el contenido de pantalla de las apps de Uber y DiDi mientras manejas — los textos de la oferta del viaje (tarifa, distancia, tiempo y destino)."
  - "Para qué": "Únicamente para calcular las métricas del viaje (ganancia por km y ganancia por hora) y mostrarte el semáforo del viaje."
  - "Dónde se procesa": "El procesamiento ocurre solo en tu teléfono. Kompara no vende, no comparte ni sube a internet el contenido de tu pantalla. Tu cuenta solo guarda tu número de WhatsApp, tu configuración y tus totales."
  - "Riesgo a tener en cuenta": "Usar herramientas de terceros puede contravenir los términos de las plataformas (Uber, DiDi). Kompara solo lee información y nunca actúa por ti dentro de esas apps, pero la decisión de usarla es tuya y bajo tu responsabilidad."

  Footer: primary "Aceptar y continuar" and text "No acepto".
- **Numbers/metrics shown:** None.
- **Actions:** Accept → Accessibility step. Decline → Limited info. Both are logged to the local funnel.
- **States:** Single state; copy is flagged TODO(legal-B038) as provisional.
- **Complexity smell:**
  - This is the main consent gate, a long scroll with the button at the bottom.
  - It covers the accessibility service only, while the Lector tab later shows a second, separate disclosure for screen capture ("Kompara leerá tu pantalla"). The driver consents twice, in two places, to what the app presents as one reader.

### Pantalla limitada (`:260`)
- **Reached from:** "No acepto". Back chevron only, no progress.
- **Purpose:** Explain what is lost.
- **Layout top to bottom:** K tile; "Sin el lector, Kompara queda limitada"; "Respetamos tu decisión. Sin el servicio de accesibilidad no podemos leer las ofertas, así que el semáforo en tiempo real no estará disponible. Aún puedes registrar viajes a mano y ver tu resumen fiscal. Puedes activar el lector cuando quieras desde Ajustes."; primary "Revisar de nuevo"; text "Continuar sin el lector".
- **Numbers/metrics shown:** None.
- **Actions:** Review pops back to the disclosure. Continue completes onboarding and lands on Inicio.
- **States:** Single state.
- **Complexity smell:** The copy promises "registrar viajes a mano" and "activar el lector … desde Ajustes", neither of which exists. There is no manual trip entry anywhere in the app, and the reader is activated from the Lector tab, not Ajustes.

### Activa el lector (`:300`, progress 3/5)
- **Reached from:** Disclosure accepted.
- **Purpose:** Walk the driver through granting accessibility.
- **Layout top to bottom, waiting state:** Title "Activa el lector de Kompara"; "Te llevamos a los Ajustes de accesibilidad de Android. Sigue estos pasos:"; three numbered rows ("Busca "Kompara" en la lista de servicios.", "Toca Kompara y activa el interruptor.", "Confirma en el aviso del sistema y regresa aquí."); status card "Lector de pantalla … Esperando…"; button "Abrir ajustes de accesibilidad"; caption "Esperando a que actives el lector…".
- **Layout top to bottom, granted state (auto-swaps when the service connects):** large K tile; "¡Listo! El lector está activo"; "Kompara ya puede leer tus ofertas y darte el veredicto al instante."; status card with an emerald "Activo" chip; button "Continuar".
- **Numbers/metrics shown:** Step badges 1–3.
- **Actions:** Open settings. Continue appears only once the service is connected.
- **States:** Waiting vs granted.
- **Complexity smell:** There is no skip. Without the grant the driver can only go back, and the only skip route is declining the disclosure. The sideload "restricted setting" prompt that Android 13+ shows for accessibility is not mentioned.

### Que tu teléfono no apague el lector (`:482`, progress 4/5)
- **Reached from:** Accessibility granted.
- **Purpose:** OEM battery survival kit.
- **Layout top to bottom:** Title "Que tu teléfono no apague el lector"; "Tu teléfono puede cerrar Kompara en segundo plano para ahorrar batería. Sigue estos pasos para que siga funcionando mientras manejas:"; 2–3 numbered steps chosen by `Build.MANUFACTURER`:
  - **Xiaomi, Redmi or Poco:**
    1. "Activa el inicio automático: Ajustes → Apps → Kompara → Inicio automático."
    2. "Sin restricciones de batería: Ajustes → Batería → Kompara → Sin restricciones."
    3. "En Recientes, fija Kompara con el candado para que MIUI/HyperOS no la cierre."
  - **Oppo, Realme or OnePlus:** "Permite el inicio automático…", "Uso de batería: … Permitir actividad en segundo plano.", "En Recientes, fija Kompara con el candado (ColorOS suele cerrarla)."
  - **Vivo or iQOO:** "Activa el inicio automático: … → Inicio automático → activa Kompara.", "Alto consumo en segundo plano: … → Consumo elevado → permite Kompara.", plus a pin step.
  - **Samsung:** "Quita Kompara de "Apps en suspensión": Ajustes → Batería → Límites de uso en segundo plano.", "Sin restricción: Ajustes → Apps → Kompara → Batería → Sin restricciones."
  - **Huawei or Honor:** "Gestión manual de inicio: … administrar manualmente y activar todo.", plus a pin step.
  - **Generic:** "Permite la actividad en segundo plano de Kompara en los ajustes de batería.", "En Recientes, fija Kompara para que el sistema no la cierre."

  Then a status-style card "Avísame si el lector se apaga" / "Activa los avisos." with a switch (default on). Footer: outlined "Abrir ajustes de batería" and primary "Ya lo configuré".
- **Numbers/metrics shown:** None.
- **Actions:** Open the battery-optimisation list (informational only), or continue.
- **States:** Six profiles.
- **Complexity smell:** The notification switch is local state only and is wired to nothing, so it does nothing. The menu paths are long and OEM-specific, and "Ya lo configuré" can't be verified.

### ¡Todo listo para manejar! (`:554`, no progress shown)
- **Reached from:** OEM step.
- **Purpose:** Finish.
- **Layout top to bottom:** Large K tile; "¡Todo listo para manejar!"; "Kompara ya está leyendo tus ofertas. Prueba el lector con un viaje de ejemplo para ver cómo se ve el veredicto."; on Android 13+, a card "Avísame si el lector se apaga" / "Activa las notificaciones para que te avisemos si tu teléfono apaga el lector mientras manejas." with an outlined "Activar avisos"; primary CTA "Probar el lector".
- **Numbers/metrics shown:** None.
- **Actions:** "Activar avisos" requests POST_NOTIFICATIONS. The CTA marks onboarding complete and lands on the Simulator, not on Inicio.
- **States:** The notification card appears only on API 33+.
- **Complexity smell:** "Kompara ya está leyendo tus ofertas" is not yet true. The OCR/screen-capture reader, which is what reads DiDi, is never started in onboarding, so the first real run still needs the Lector tab. A second notification prompt repeats the OEM step's switch.

---

## D. Overlay surfaces (the driver's real glance target)

### Verdict chip (`overlay/VerdictChipUi.kt:78`)
- **Reached from:** Floats over Uber, DiDi and inDrive while an offer card is on screen. The window is created by the accessibility service. It also appears inside the Simulator.
- **Purpose:** One-second read: is this ride worth it?
- **Layout top to bottom:** A pill, at least 132dp wide, clipped to 16dp radius:
  1. Brand strip: K logomark + "Kompara" (12sp, white, on `#059669`).
  2. Verdict-coloured body: hero rate (30sp Black), e.g. "$7.20/km", then the second rate (18sp SemiBold), e.g. "$186/h".
  3. Optional 10sp white hint under the figures: "Sin datos de distancia", "Sin tarifa" or "Datos incompletos".
- **Numbers/metrics shown:**
  - The hero is net $/km when the preferred metric is "Por kilómetro" (the default), or net $/h when it is "Por hora". The other rate is shown below it.
  - A missing figure shows "—".
  - The chip has no verdict word. TalkBack hears "Veredicto del viaje: Verde. Mantén presionado para ajustar tu mínimo."
- **Actions:**
  - Drag moves the chip. It snaps to the nearer edge on release, is kept off the fare area, and the position is persisted.
  - Long-press opens the quick threshold sheet.
  - A plain tap does nothing.
- **States:** See the state table in section 3.
- **Complexity smell:**
  - The two-number design is good for glancing, but there is no word for the verdict, so colour-blind drivers rely on the number.
  - The missing-data hint is 10sp white text, including on the yellow body (`#F2B705`), which is poor contrast and too small.
  - Long-press is an undiscoverable gesture, and the instruction lives only in Ayuda and TalkBack text.

### Quick threshold sheet (`VerdictChipUi.kt:214`)
- **Reached from:** Long-press on the chip.
- **Purpose:** Adjust the floors without leaving the host app.
- **Layout top to bottom:** Surface below the chip: title "Tu mínimo por km" (or "Tu mínimo por hora"); slider "Verde desde" with a value like "$8.0/km" or "$161/h"; slider "Rojo por debajo de"; hint "Si queda entre los dos, el viaje sale amarillo."; text button "Listo".
- **Numbers/metrics shown:** The same ranges as Tu semáforo, and only for the deciding metric.
- **Actions:** Sliders save on release; "Listo" closes it.
- **States:** IPK vs IPH only.
- **Complexity smell:** Sliders in a moving vehicle over another app are a risky interaction. It duplicates the Tu semáforo screen.

### Reader-down banner (`overlay/ReaderDownBannerUi.kt:31`)
- **Reached from:** Shown at the top-centre while the driver is inside DiDi or inDrive with the screen reader dead, after it has run at least once this session.
- **Purpose:** Recovery nudge.
- **Layout top to bottom:** Dark pill with a red dot and "Kompara: lector apagado. Toca y confirma para reactivar."
- **Numbers/metrics shown:** None.
- **Actions:** Tap relaunches the screen-capture consent. It disappears the instant capture returns.
- **States:** Visible vs hidden only.
- **Complexity smell:** It is a tappable target over a live ride-offer app. The message is short but ends with a second step the driver has to understand.

---

## E. Dialogs, hand-offs, notifications and exports

### E1. Lector "Kompara leerá tu pantalla" dialog (`LectorScreen.kt:131`, an `AlertDialog`)
- **Reached from:** The primary Lector button.
- **Purpose:** Prominent disclosure before screen capture.
- **Layout top to bottom:** Title "Kompara leerá tu pantalla"; body "Para mostrarte el semáforo sobre las ofertas de DiDi, Kompara captura tu pantalla y la analiza únicamente en tu teléfono. Nada de lo que aparece en tu pantalla se guarda en servidores ni sale de tu dispositivo. Puedes detener la captura cuando quieras desde la notificación o el ícono de transmisión del sistema."; buttons "Continuar" and "Ahora no".
- **Numbers/metrics shown:** None.
- **Actions:** Continue → `OcrConsentActivity`. Not now dismisses.
- **States:** Single state.
- **Complexity smell:** Second disclosure, overlapping the onboarding one.

### E2. Account deletion dialog (`AccountScreen.kt:172`)
Described under Tu cuenta.

### E3. `OcrConsentActivity` (`app/OcrConsentActivity.kt:17`)
- **Reached from:** The Lector button, or tapping the "se detuvo el lector" notification or the overlay banner.
- **Purpose:** Request MediaProjection consent.
- **Layout top to bottom:** Invisible, translucent activity that immediately launches the system dialog "start recording / casting". On API 30+ with the silent lane active it finishes immediately and shows nothing.
- **Numbers/metrics shown:** None.
- **Actions:** The system dialog is the only UI. On OK it starts `OcrCaptureService`.
- **States:** Silent-lane skip vs system prompt.
- **Complexity smell:** On modern phones the prompt can silently not appear, so the driver may not know why the button seems to do nothing.

### E4. `ShareImportActivity` (`app/ShareImportActivity.kt:64`)
- **Reached from:** The OS Share sheet entry "Kompara — Importar ganancias" for PDF or image shares.
- **Purpose:** Receive an earnings file.
- **Layout top to bottom:** Invisible. Toasts only: "Crea tu cuenta Kompara para importar tus ganancias." (when signed out) or "No pudimos leer ese archivo. Comparte el PDF semanal de Uber o las capturas de DiDi." Otherwise it opens the Import screen in its SharedReady state.
- **Numbers/metrics shown:** None.
- **Actions:** None; it reads the shared bytes with bounded I/O.
- **States:** Signed out, unsupported file, ready.
- **Complexity smell:** None for the driver. Everything is intentionally invisible.

### E5. Notifications (all es-MX, hardcoded in code or `strings.xml`)
- **Watchdog** (channel "Estado del lector"): "Kompara: el lector se apagó" / "Toca para reactivarlo y seguir viendo el veredicto de cada viaje." Tap → Accessibility settings.
- **OCR foreground (persistent):** "Kompara está leyendo la pantalla" (channel "Lector OCR", hardcoded).
- **OCR stopped (screen lock):** "Kompara: se detuvo el lector para DiDi" / "Toca para reiniciar la captura y ver el semáforo en DiDi." (high importance, vibrates; hardcoded). Tap → `OcrConsentActivity`.
- **IMSS month-end:** "Uber: cubierto por el IMSS" / "El mes pasado alcanzaste el umbral en Uber. Esa plataforma debe inscribirte al IMSS." or "Uber: no alcanzaste el umbral" / "El mes pasado no llegaste al umbral del IMSS en Uber."
- **Week close (Monday 9am):** "Tu resumen de la semana está listo" / "Comparte tu semana en Kompara con tu grupo de conductores." Tap → Share card (which is the month card).
- **Complexity smell:** Two different "stopped" notifications for the two reader types, with the same visible channel name "Estado del lector". The OCR one says "DiDi" even though Uber is read the same way.

### E6. Exports
- **Fiscal PDF:** "Resumen fiscal mensual", columns Plataforma | Bruto | Neto | ISR ret. | IVA ret., totals, an IMSS line, notes and disclaimer, shared with subject "Resumen fiscal Kompara — {mes}".
- **Share card PNG:** 1080×1920 (the 1200×630 variant is built but unreachable), shared to WhatsApp or the chooser.

---

## 1. Bottom-bar tabs (in order)

| # | Label | Icon | Route |
|---|---|---|---|
| 1 | Inicio | `Icons.Filled.Home` | `inicio` |
| 2 | Comparar | `Icons.AutoMirrored.Filled.List` | `comparar` |
| 3 | Lector | `Icons.Filled.PlayArrow` | `lector` |
| 4 | Fiscal | `Icons.Filled.DateRange` | `fiscal` |
| 5 | Ajustes | `Icons.Filled.Settings` | `ajustes` |

- All five are flat, uniform 64dp tabs with 24dp icons and 12sp labels. The selected tab is tinted emerald and unselected tabs are muted.
- Accessibility descriptions: "Pantalla de inicio con tus estadísticas", "Compara tus ganancias entre plataformas", "Activa el lector de Kompara", "Resumen fiscal de tus ingresos", "Ajustes de la aplicación".
- The active tab is the topmost tab in the back stack, so pushed screens keep their parent tab lit. Re-tapping the active tab pops to its root.

## 2. Full onboarding step sequence

**Logged funnel steps** (`OnboardingStep.kt`): `PITCH → SIGNUP → SIGNUP_DONE → DISCLOSURE → DISCLOSURE_ACCEPTED | DISCLOSURE_DECLINED → ACCESSIBILITY → ACCESSIBILITY_GRANTED → OEM → DONE`.

**Screens in order, with what each asks of the driver:**

| Screen | Progress | What it asks |
|---|---|---|
| Pitch (3 pages) | none | Read, swipe or tap "Continuar", then "Empezar". |
| Signup: Teléfono | 1/5 | Type a 10-digit WhatsApp number, tap "Recibir código por WhatsApp". |
| Signup: Código | 1/5 | Type the 6-digit code, tap "Confirmar código". |
| Signup: Perfil | 1/5 | Optionally enter a name, pick a city, tap "Guardar y continuar" (or "Completar después"). |
| Divulgación | 2/5 | Read 4 sections, "Aceptar y continuar" or "No acepto". |
| (Decline branch) Pantalla limitada | none | "Revisar de nuevo" (back to Divulgación) or "Continuar sin el lector" (finishes onboarding, lands on Inicio). |
| Activa el lector | 3/5 | Leave the app, find and enable Kompara in system Accessibility, return. Cannot be skipped. |
| Activa el lector: ¡Listo! | 3/5 | Tap "Continuar" once the service connects. |
| OEM survival kit | 4/5 | Follow 2–3 device-specific settings steps, optionally flip an inert switch, tap "Ya lo configuré". |
| ¡Todo listo para manejar! | hidden | On API 33+, optionally "Activar avisos"; tap "Probar el lector" (lands on the Simulator). |

- The standalone `AUTH` route re-runs only the 3 signup sub-steps, with no progress chrome.
- Screen-capture consent is not part of onboarding; it first happens from the Lector tab.

## 3. Overlay chip state machine

**`OverlayStateMachine`** (`OverlayStateMachine.kt`) emits `OverlayVisibility`:
- `Hidden`: the window is detached. The initial state, and the result of `NoCard` after a 500ms grace delay or an immediate `SpecDisabled`.
- `Showing(metrics)`: the window is attached with the chip.

Transitions:
- `Parsed` → `Showing`, immediately.
- `NoCard` → wait 500ms, then `Hidden`. A new `Parsed` inside the grace window cancels the hide.
- `SpecDisabled` (the parser was remotely kill-switched) → `Hidden` immediately.
- Output is deduplicated with `distinctUntilChanged`.

**`VerdictChipState`** variants when `Showing`:

| Dimension | Values |
|---|---|
| `level` | `GREEN` (verde `#1B8A3A`, white text), `YELLOW` (amarillo `#F2B705`, dark text), `RED` (rojo `#D32F2F`, white text) |
| `preferred` | `IPK` (hero = net $/km, second = $/h) or `IPH` (hero = $/h, second = $/km) |
| `missingHintKind` | `NONE`, `DISTANCE` ("Sin datos de distancia"), `FARE` ("Sin tarifa"), `GENERIC` ("Datos incompletos") |
| Figure placeholder | "—" for any missing rate |
| `explainKind` | `NONE / BOTH_STRONG / KM_WEAK / HOUR_WEAK / BOTH_WEAK`; computed but not rendered (parked, so `overlay_explain_*` is unused) |
| UI-local | quick sheet open (long-press), dragging |

**Level rule** (`NetProfitEngine`):
- The preferred metric alone decides the colour: at or above its green floor is green, below its red floor is red, in between is yellow.
- A missing fare means red.
- If the preferred rate is untestable, the other metric substitutes, capped at yellow.
- Any missing input caps green at yellow.

**Separate surface:** the reader-down banner shows when `hasRunThisSession && !running && foreground app is OCR-owned`.

## 4. What the paywall gates

Gate state per capability (`TierGate.kt`):

| State | Meaning |
|---|---|
| `UNLOCKED` | Premium (paid, trial or grace), or the debug override, or the remote promo kill switch (paywall disabled for everyone). |
| `LOCKED` | Free user. |
| `NEEDS_VERIFICATION` | Premium but not import-verified, for `BENCHMARKS` and `COMPARE` only. The CTA becomes "Importa una semana para desbloquear" and goes to Import. |

**Free for everyone:**
- The reader, the verdict chip, the quick threshold sheet.
- The Simulator.
- Inicio's own stats: net, goal, streak, the four metric tiles.
- Free tips: missed good offers, best hours, acceptance guidance, streak praise.
- Detalle del día.
- The Comparar hero (city standing and "Top X%").
- The Share card, including its "Top X%" flex.
- Historial: the 2 most recent weeks.
- Importar semana, Tus costos, Tu semáforo, Ayuda, Tu tarjeta/cuenta, Invita y gana.

**Premium (`PaywallGate` or locked stand-in):**

| Capability | Where | What is gated |
|---|---|---|
| `BENCHMARKS` | Inicio tiles; Resumen de la semana cards | "Top X%" pills and 20-person bars (locked: "🔒 Kompara Premium"); the Inicio tease strip |
| `COMPARE` | Comparar | The benchmark table and "Dónde puedes ganar más" |
| `HISTORY` | Historial | Weeks older than the two most recent |
| `FISCAL` | Fiscal tab | The whole body: IMSS tracker, fiscal summary, PDF, explainers |
| `RECOMMENDATIONS` | Inicio "Consejos" | Premium tips (percentile, commission and cross-platform) |

- `VERIFICATION_REQUIRED` is `{BENCHMARKS, COMPARE}` only. A premium driver's own data (history, fiscal, recommendations) is never locked behind verification.
- `GENERIC` is a paywall surface for entry points with no specific gate (analytics only).
- The reader and today's stats are not represented as a `Capability`, so they cannot be locked.

## 5. Counts

**Screens**
- **Tab roots:** 5.
- **Pushed in-shell screens:** 12 (Tus costos, Tu semáforo, Ayuda, Tu cuenta, Historial, Resumen de la semana, Detalle del día, Paywall, Importar semana, Invita y gana, share card, Simulador).
- **Onboarding/auth screens:** 10 (pitch, signup teléfono, código, perfil, divulgación, pantalla limitada, activa el lector waiting, activa el lector granted, OEM, done).
- **Total distinct screens:** 27.
- **Other surfaces:** 3 overlay (chip, quick sheet, reader-down banner), 2 dialogs, 1 reusable gate tease (`PaywallGate`), 2 invisible hand-offs (`OcrConsentActivity`, `ShareImportActivity`).
- **All surfaces:** 35. Not counted: 5 notification types, 2 exports (PDF, PNG), 1 Accessibility-settings description.

**Strings**
- **`ui/strings.xml`:** 465 `<string>` entries, 441 distinct texts (24 duplicate texts across keys). 422 are referenced from main Kotlin and 43 are dead. Of the 422 referenced: 14 are notification or channel copy, 13 are PDF-only, and 2 are debug-only, leaving about 393 release on-screen strings.
- **All modules:** `ui` 465, `overlay` 50 (12 unused, mostly parked tap-to-expand copy), `app` 4, `capture` 2. Total 521.

**The 43 dead `ui` strings:**
- `inicio_empty_*`, `comparar_empty_*`, `fiscal_empty_*`, `ajustes_empty_*` (12 placeholder-era strings)
- `thresholds_metric_hint`, `thresholds_section_km`, `thresholds_section_hour`
- `progress_label_format`, `onb_saltar`, `onb_paso_indicador`, `auth_code_hint`
- `dashboard_goal_set_cta`, `day_today`, `day_detail_title`, `day_shift_line_open`, `week_summary_title`
- `cost_editor_preview_per_km`, `cost_editor_saved`
- `gate_hint_history`, `history_free_limit_title`
- `comparar_locked_*`, `comparar_single_*`, `comparar_example_tag`, `comparar_verdict_title`, `comparar_breakdown_title`, `comparar_not_comparable*`, `comparar_winner_badge`, `comparar_week_picker_label`, `comparar_gate_breakdown_hint`
- `share_card_preview_desc`, `share_card_variant_story`, `share_card_variant_landscape`

---

## Cross-cutting findings for the redesign

1. **Copy and promises that don't match behaviour.**
   - The limited-info screen promises manual trip entry and activation "desde Ajustes".
   - Ayuda refers to a "botón de reporte".
   - "Sin pedir tarjeta" conflicts with a Play subscription.
   - The paywall footer ships "pendientes de revisión legal".
   - "Kompara ya está leyendo tus ofertas" appears before any capture has started.
2. **Two vocabularies for the verdict.** "Verde/Amarillo/Rojo" (badge, chip), "Conviene/Regular/No conviene" (simulator), and "semáforo", "veredicto" and "mínimo" in prose.
3. **No in-app back affordance on any pushed screen.** The bottom bar also stays visible on Paywall, Import and the Simulator.
4. **Features with no entry point.**
   - The weekly goal and platform preferences have no UI.
   - The Share-card variant toggle has no UI.
   - `day/{day}` has no caller.
   - The OEM notification switch is not wired to anything.
5. **Mismatches between surfaces.**
   - Share from the week summary or the week-close notification opens the month card.
   - The shared PNG differs from the preview.
   - "Semana del 8 jun" on the summary vs "Semana del 8–14 jun" on the history row.
   - The Fiscal tab is titled "Cobertura IMSS".
6. **Hardcoded copy outside `strings.xml`.**
   - Recommendation texts (in `:metrics`).
   - Comparar opportunity texts and column headers "Uber" and "DiDi".
   - The Lector mini-preview.
   - Share caption and PNG text.
   - OCR notifications.
   - The PercentilePanel locked description.
   - City display names (missing accents).
   - The generic import error (missing accents).
7. **Dead code and unused components.** `HomeUiText.kt` and `HomeViewModel.kt` are unused. `KomparaDialog` is unused, so the two dialogs use raw `AlertDialog`. The 43 dead strings listed above.
8. **Glance-safety.** Only the chip is used while driving. The rest is parked-time UI and could be simplified without driving risk. Notable chip issues are the 10sp white hint on yellow, and sliders in the long-press sheet while moving.
9. **Dead-end locks and double consent.**
   - Free-tier locks on the Resumen de la semana show a lock icon with no CTA.
   - The driver consents twice (onboarding disclosure, then the Lector dialog) for one reader.
