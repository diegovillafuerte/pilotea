# Kompara: product and UX elevation, Round 1 (independent analysis)

Reviewer: principal product designer, round 1, written without seeing the other reviewer's work.
Ground truth: `inventory.md` (as-built screens and real es-MX copy), `docs/design-principles.md`, `design-system/README.md`, `docs/benchmark-ux-gaps.md`, `docs/claude-design-redo-brief.md`, `docs/design-system-audit.md`, `docs/competitive-analysis.md`, `docs/comparar-redesign-spec.md`, `docs/play-compliance.md`, `docs/android-technical-design.md` §0–§2. I spot-checked the source where the inventory left a question open: `VerdictChipUi.kt`, `SilentScreenshotLane.kt`, `RecommendationEngine.kt`, `CostProfile*.kt`, and `strings.xml` in `ui` and `overlay`.

Notation: "today" means the build as inventoried. Quoted strings are the real copy. New copy is written in es-MX with `tú`.

---

## 1. Diagnosis (ruthless, evidence-based)

**The 5-minute uninstall path today.** The driver installs and swipes three text pages, one of which promises "Gana hasta 30% más por turno". Next they hand over their WhatsApp number, type a code, and pick a city, all before seeing anything work. Then they read a four-card legal scroll, leave the app to find a switch in Android Accessibility, follow two or three device-specific battery steps (one of them has a switch that does nothing), and arrive at "¡Todo listo para manejar!", which drops them into a simulator rather than the app. They open Uber. The first offer arrives, OCR misses the fare, and the chip turns **red**. Sitting next to Uber's own "$/km" badge, the chip shows a *different* "$/km" with no word saying which is right. They uninstall.

The 15 problems below are ranked by severity.

### 1. CRITICAL: The chip says "no la tomes" when it just couldn't read the offer, and it shows an unlabeled $/km beside Uber's own $/km.
- **Evidence:**
  - Overlay level rule (inventory §3): "A missing fare means red."
  - The missing-data hint is "Sin tarifa", "10sp white text, including on the yellow body (`#F2B705`)".
  - The hero is "$7.20/km" with no unit qualifier: the word "neto" appears nowhere on the chip.
  - Uber is piloting "an estimated earnings-per-km badge on trip requests … in selected cities of Mexico … explicitly gross" (`competitive-analysis.md` §3).
- **Why it hurts:** A reader failure becomes a "reject" signal. The driver either turns down a good trip (lost money) or learns that red sometimes lies and stops trusting every colour. Two "$/km" figures on one offer card that disagree make Kompara's number look wrong, because it is the unlabeled one. The product's single most important pixel is the least trustworthy.

### 2. CRITICAL: Onboarding takes 10 screens, asks for the account before any value, and ends in the wrong place with a false claim.
- **Evidence:**
  - The sequence (inventory §2): Pitch (3 pages) → Teléfono → Código → Perfil → Divulgación → Activa el lector → ¡Listo! → OEM → ¡Todo listo!. That is 10 screens, not counting the pager pages.
  - The first real ask is "Crea tu cuenta". The done screen says "Kompara ya está leyendo tus ofertas" before anything has been read, and its CTA "Probar el lector" "lands on the Simulator, not on Inicio".
  - The OEM step's "Avísame si el lector se apaga" switch "is wired to nothing", and notifications are asked for again on the next screen.
  - The pitch is text describing a visual product, and leads with an unsourced claim ("…reportado por apps similares").
- **Why it hurts:** The accessibility grant is the cliff in this funnel. Every screen before it burns the motivation the driver needs to get over it. The aha moment (seeing a chip) is the very last thing, and even then it is a demo.

### 3. CRITICAL: The reader is presented as two readers with two consents, and the consent that matters doesn't describe what is actually captured.
- **Evidence:**
  - The Lector tab has the status card "Lector para DiDi … Activo / Detenido", the button "Iniciar lector para DiDi", and the hint "Después de encenderlo, aquí también activas el lector para DiDi."
  - A second dialog follows: "Kompara leerá tu pantalla" (E1).
  - On API 30+ the primary lane is the accessibility service's own silent `takeScreenshot()` (`play-compliance.md`, "The capture lane"). That lane starts automatically with the service (`SilentScreenshotLane.start` sets `running = true`), and `OcrConsentActivity` "finishes immediately and shows nothing". So on most phones the Lector button is a no-op, and the screenshot disclosure is never shown.
  - Meanwhile the onboarding disclosure says Kompara reads "los textos de la oferta (tarifa, distancia, tiempo y destino)" of "Uber y DiDi". It never mentions screen images (and leaves out inDrive). Ayuda repeats it: "Kompara lee únicamente los textos de la oferta".
- **Why it hurts:** Drivers can't tell whether Kompara is working ("tapping Iniciar may seem to do nothing"). The prominent disclosure, which is the #1 launch risk per `play-compliance.md`, under-describes the data the app really accesses. That is a trust gap and a review risk at the same time.

### 4. HIGH: The chip's legibility works against the one-second read: green brand strip on red verdicts, no non-colour cue, and an undiscoverable "why".
- **Evidence:**
  - The "Brand strip: K logomark + 'Kompara' (12sp, white, on `#059669`)" sits on top of every verdict body. On a rojo chip the driver's peripheral vision gets an emerald band *and* a red body. On a verde chip the strip (`#059669`) and body (`#1B8A3A`) are two near-identical greens.
  - "The chip has no verdict word". Colour plus number only, and red/green is exactly the pair that colour-vision deficiency confuses (common in men, who are most of this driver base).
  - The "why" (`overlay_explain_*`) is "computed but not rendered".
  - Long-press is "an undiscoverable gesture".
  - Ayuda still tells drivers "Toca el chip para ver el detalle y por qué salió ese color". Tap does nothing.
- **Why it hurts:** The verdict should land in peripheral vision in under a second. Today, two hues compete on one pill, some drivers can't tell green from red at all, and the help text describes a chip that no longer exists.

### 5. HIGH: Thresholds are adjusted with two fine sliders over a live offer that expires in 8–20 seconds.
- **Evidence:**
  - Quick sheet: "slider 'Verde desde' … slider 'Rojo por debajo de'", opened "below the chip".
  - The inventory itself flags: "Sliders in a moving vehicle over another app are a risky interaction."
  - Offer cards "live ~8–20 s" (`android-technical-design.md` §1).
  - Nothing keeps the sheet away from the host app's accept/decline area.
- **Why it hurts:** A slider needs fine motor control and two hands on a mounted phone. A sheet that can cover the host's buttons during a timed offer is a safety and money problem, and it is the kind of overlay behaviour a Play reviewer looks at twice.

### 6. HIGH: Five tabs with overlapping meanings. A state (the reader) and a monthly chore (Fiscal) get tabs, while the driver's own history is buried in settings.
- **Evidence:**
  - Tabs: "Inicio | Comparar | Lector | Fiscal | Ajustes".
  - Comparar cross-links back to Inicio ("Tus ganancias y tus mejores momentos … Inicio ›").
  - The Fiscal tab's title reads "Cobertura IMSS".
  - Historial is "Reached from: Ajustes only".
  - The Simulator is reachable from four places (Ajustes, Lector, the Inicio empty state, onboarding).
- **Why it hurts:** The driver can't predict where "mi semana" lives: Inicio, Historial, Resumen de la semana or Comparar all claim a piece of it. Meanwhile the reader, which should just be "on", occupies the centre tab.

### 7. HIGH: No screen has a single hero. The main screens are number grids.
- **Evidence:**
  - Inicio stacks "up to ten stacked blocks": banner, net + streak + share, goal, cost nudge, platform chips, a 2×2 grid ("$ por viaje", "$ por km", "$ por hora", "Viajes/hora") each with a "Top X%" pill, a gate tease, three consejos, a completeness note, and a link.
  - Comparar's table "Métrica | Tú | Uber | DiDi | Lugar" × 6 rows is ~30 values at 12sp.
  - Fiscal is "roughly 12 stacked blocks, and half of it is legal text".
  - Resumen de la semana shows 5 key/value rows plus 5 metric cards plus bars.
- **Why it hurts:** At 60cm, four equal-weight tiles read as noise. The 1-second test fails on every data screen.

### 8. HIGH: The paywall blanks screens, dead-ends, locks out a paying user, and makes promises it can't keep.
- **Evidence:**
  - Fiscal: "A free driver sees a fully blurred wall with one hint line."
  - Resumen de la semana: "the bars show 🔒 with no CTA for a free (LOCKED) driver, which is a dead end".
  - Inicio's gate tease "shows for every free user even when no badges exist".
  - NEEDS_VERIFICATION: a paying user sees "Importa una semana para desbloquear".
  - Paywall copy: "Si te ayuda a ganar $200 más a la semana, se paga solo". Footer: "Términos y aviso de privacidad pendientes de revisión legal".
  - Ayuda promises "sin pedir tarjeta", which a Play-billing trial cannot guarantee.
- **Why it hurts:** Blur walls over the driver's own on-device data feel like hostage-taking. A paying user hitting a lock reads as a bug. Placeholder legal copy at the moment of payment kills conversion.

### 9. HIGH: Copy promises features that don't exist and implies actions the product never takes.
- **Evidence:**
  - Pantalla limitada: "Aún puedes registrar viajes a mano … activar el lector cuando quieras desde Ajustes". Neither exists.
  - Ayuda: "avísanos con el botón de reporte". There is none.
  - A consejo (`RecommendationEngine.kt`, `acceptance_raise_floor`): "Sube tu mínimo en Ajustes para que la app filtre los que no valen la pena". Kompara filters nothing; it is read-only by policy.
  - Another consejo: "Rechaza los viajes cortos … y deja que la app te traiga uno mejor".
- **Why it hurts:** Every broken promise taxes the chip's credibility. "Filtre" also contradicts the read-only posture (`play-compliance.md` §1) in the exact words a reviewer would quote.

### 10. HIGH: Standing claims are shown, and shared virally, as fact on sample data.
- **Evidence:**
  - Comparar hero: "Le ganas al 78% de los conductores de tu ciudad" with an "Optional 'Datos de muestra'".
  - The scorecard lists "city percentiles (synthetic seed)" (`benchmark-ux-gaps.md` §1).
  - The share caption is "Top 22% en CDMX 🚀", and "The 'Top X%' flex is ungated by design".
  - The spec admits there is no combined population: the blended "Tú" is ranked against the primary platform (`comparar-redesign-spec.md` §2, gap 2).
- **Why it hurts:** The viral loop broadcasts a number that isn't real or isn't what it says. The first WhatsApp group that compares notes discredits the brand, and trust *is* the brand ("Trust is a theme, not a footnote", design-system README).

### 11. HIGH: Three vocabularies for one verdict, plus internal jargon everywhere.
- **Evidence:**
  - The same verdict has three names: Simulator segments "Conviene | Regular | No conviene"; chip and badges "Verde/Amarillo/Rojo"; headlines "Verde: ganas $15.00/km neto".
  - Prose adds "semáforo", "veredicto", "mínimo", "lector", "Lector para DiDi".
  - Fiscal: "Umbral por plataforma este mes: $8,364.00", "ISR retenido (est.)", "CSF", "RFC".
  - Comparar's legend: "Tú = tu mezcla de apps · promedio = … · tu lugar = …". Also "Viajes/hora" and "Tasa de aceptación".
  - Forced caps: "GANANCIA NETA ESTA SEMANA", "¿QUÉ DECIDE TU SEMÁFORO?", "PROCESANDO ARCHIVOS".
  - Tu semáforo's intro is ~55 words; Ayuda answers run 50–110 words.
- **Why it hurts:** For a driver with low-to-mid tech comfort, every new noun is one more thing to learn, and every long paragraph gets skipped.

### 12. MEDIUM: The semáforo model asks for four numbers plus a metric choice, and the simulator silently edits them.
- **Evidence:**
  - Tu semáforo: "Por kilómetro | Por hora" × "Verde desde" / "Rojo por debajo de" is four floors (Ayuda: "edita los cuatro mínimos").
  - The Simulator's playground "is not a sandbox: it persists the real green floor". The only warning is a hint line: "Se guarda y es el mismo que usa el lector."
  - The three legend badges "look like buttons but do nothing".
- **Why it hurts:** Drivers think in one number ("no salgo por menos de 8 el km"). Four numbers invite misconfiguration, and the "playground" changes real verdicts without the driver knowing.

### 13. MEDIUM: No navigation chrome, the same data drawn four ways, and surfaces that disagree with each other.
- **Evidence:**
  - "Pushed screens have no top bar and no in-app back button".
  - "The bottom bar stays visible on every pushed screen, including Paywall, Import, Simulator and Share card".
  - In Import, "'Elegir otra plataforma' is the only way back".
  - The same metrics appear four ways: Inicio has 4 tiles with pills, Resumen 5 cards with 20-person bars, Día 5 cards without percentiles, and Comparar a table.
  - Share is "always the month card, even when opened from the week summary or the 'Tu resumen de la semana está listo' notification", and "The on-screen preview is not what gets shared".
  - The same week is labelled "Semana del 8 jun" in one place and "Semana del 8–14 jun" in another.
  - Two verdict palettes exist (`overlay/VerdictColors.kt` vs `:ui`, per the design-principles "Debt" note).
- **Why it hurts:** Drivers get lost, and the inconsistency reads as amateur. That alone rules out "Uber-class".

### 14. MEDIUM: Some data entry does nothing, and some features have no way in.
- **Evidence:**
  - Tus costos asks "Seguro (MXN por día)" and "Renta o financiamiento (MXN por día)". In the source, `CostProfileMapper` folds them into `dailyFixedCosts`, which nothing reads.
  - Tus costos is "Seven bare numeric fields with no placeholders".
  - Weekly goal: "There is no UI anywhere to set the weekly goal."
  - The OEM switch is "wired to nothing", `day/{day}` "has no caller", and "Tapping a tip card does nothing."
- **Why it hurts:** The cost screen is the step that makes "net" true, and it wastes the driver's effort. Drivers who don't know their km/L give up and silently keep defaults. Meanwhile, the one thing renters care about (covering the week's rent) has a goal bar that never appears.

### 15. MEDIUM: Loading screens are blank, and several states mislead.
- **Evidence:**
  - Loading is "blank" on Inicio, Fiscal, Historial and Día; Comparar "renders nothing".
  - "A week with only imported data reads as 'Aún no hay números'".
  - Inicio's empty-state CTA "Probar el lector" "goes to the simulator, not the Lector tab".
  - Comparar: "'Todas' with several platforms shows none [percentiles]".
  - Import: "Preview and confirm each run the AI parse (two waits of 5–15 s)", with a cosmetic 900ms step timer and "Extrayendo datos con IA…", and no line saying the file leaves the phone.
- **Why it hurts:** On a cheap phone a blank screen reads as a crash. Waiting twice for the same parse is pure cost. "IA" with no disclosure undercuts the privacy promise.

---

## 2. The elevated concept

### The promise
- **es-MX, in the driver's words:** «Antes de aceptar, ya sé si el viaje me deja lana de verdad, y al cerrar la semana sé cuánto me quedó y cómo voy contra los demás.»
  - In-product tagline: **"Ve si el viaje te deja, antes de aceptarlo."**
- **English, for us:** At the instant of the offer, Kompara tells a Mexican ride-hail driver in one glance whether the trip pays *after their own costs*. Afterwards it shows what they actually kept and how they stack up against their city. It never touches the ride app and never leaves the phone.

### The driver's day, and what Kompara does at each moment

| Moment | The driver's question | What Kompara does | Surface |
|---|---|---|---|
| **Before the shift** (opens the ride app) | "¿Está prendido?" | The first time a ride app comes to the front each day, a 2-second pill appears where the chip lives: "✓ Kompara activo". If the reader is dead, one notification fires *before* the first offer. | Overlay greeting; notification |
| **The offer** (8–20 s) | "¿La tomo?" | The chip: colour + glyph + net figure, in under 150 ms, gone 500 ms after the card closes. It never touches anything. | Chip |
| **During the trip** | (driving) | **Nothing.** Kompara is silent while the driver is driving a passenger. | — |
| **Between trips** (parked) | "¿Cómo voy hoy?" | Hoy: today's net plus the last offers and what the driver did with them. | Hoy |
| **End of shift** | "¿Cuánto me quedó?" | One quiet notification with the shift net, time and $/h, plus one learning ("dejaste ir 2 verdes"). Tapping it opens Tu día. | Notification → Tu día |
| **Week close** (Monday) | "¿Fue buena semana? ¿Cómo voy contra los demás?" | The week view: net, days, best hours, standing vs city, one-tap share. This is also when the free trial starts (see §7). | Notification → Ganancias · Semana → Tu lugar → Compartir |
| **Month / fiscal** | "¿Ya me toca IMSS? ¿Cuánto me retienen?" | IMSS progress per app (free), withholdings estimate and a PDF for the accountant (Premium). | Notification → IMSS e impuestos |

**The three moments the app is built around:**
1. **The offer.** This is the product, and the free hook.
2. **End of shift.** This is the habit: it answers "¿cuánto me quedó limpio?", the question no ride app answers honestly.
3. **Week close.** This is where retention, virality and monetization meet: standing, share, trial.

Everything else, including fiscal, settings, import and referrals, is support and lives one level down.

### What to steal from the Uber Driver app (structure, not brand)
1. **A status-first home.** Uber's home answers "am I online?" before anything else. Kompara's home answers "¿está prendido el lector?" with a status pill, and turns into a single big fix action when the answer is no. That fix is our "GO" button.
2. **One hero number.** Uber has today's earnings pill; Kompara has today's net, 44sp, alone.
3. **Earnings as a timeline.** A week view with a 7-bar chart, tap a bar to get the day, tap a day to get the trips. Week and month toggle. Nothing else at the top level.
4. **Trip-card discipline.** Every offer gets a uniform row: time · fare · verdict pill · what you did. Details live behind a tap in a bottom sheet, never inline.
5. **Settings tucked behind Account.** Rows show their *current value* ("Verde desde $8.00/km"), so the driver rarely needs to open them.
6. **One top-app-bar pattern** (back arrow + title) on every drill-in, and **full-screen tasks without the bottom bar** (paywall, import, share).
7. **Quiet notifications.** Only for events the driver would want: something broke, a shift closed, the week closed, the month closed.

### What NOT to copy
- **The map.** Kompara is not navigation. A map would imply location tracking (which breaks the on-device posture) and duplicates the ride app.
- **A fake GO toggle.** Kompara can't "go online". The reader's real on/off switch is Android Accessibility, so a pretend in-app toggle that only hides the chip would be a lie about state.
- **The Inbox and promotions feed.** We have no messages, and an upsell feed is noise.
- **Quests, streak pressure, "maneja más" gamification.** Nudging tired drivers to drive longer is a safety problem, and it isn't our job. The streak survives only as a line on the share card.
- **Uber's black/white brand, iconography or any mark.** We keep emerald + slate + the verdict triple.
- **Any auto-accept or auto-decline.** Even though the host apps have their own, ours stays read-only, forever.

### What to CUT entirely

| Cut | Reason |
|---|---|
| **Lector tab** | The reader is a *state*, not a destination. Its status moves to the Hoy status pill; repair moves to Cuenta › Lector. |
| **"Lector para DiDi" as a second reader, its Iniciar/Reiniciar button, and the E1 "Kompara leerá tu pantalla" dialog** (on API 30+) | The silent lane makes them vestigial. One reader, one switch, one consent. API <30 keeps a single onboarding step plus the system prompt (see §6). |
| **Fiscal tab** | It's a monthly chore. It becomes IMSS e impuestos, reached from Ganancias › Mes and the month-end notification. |
| **Comparar tab** | The comparison is weekly, so it becomes Tu lugar, one tap from the week. The IA principle "individual vs comparison" survives as two screens, not two tabs (see the open question in §9). |
| **Historial screen** | Week arrows plus the Mes list *are* the history. |
| **Resumen de la semana screen** | It becomes the Ganancias week view: one screen for every week. |
| **Simulador as a destination** | It becomes the onboarding demo, the live preview inside Tu semáforo, and "Ver cómo funciona" in Lector. Its playground stops being a hidden real setting. |
| **The 3-page text pitch** | Replaced by one live demo screen. |
| **Perfil step (name + city)** | City is inferred from the WhatsApp area code; the name is optional in Tu perfil. |
| **"¡Listo! El lector está activo" and "¡Todo listo para manejar!"** | Auto-advance; land on Hoy. |
| **Emerald brand strip on the chip** | Colour conflict (see Diagnosis 4). It becomes a 10dp K mark. |
| **Quick-sheet sliders** | Replaced by ± steppers. |
| **Insurance and rent fields** | They feed `dailyFixedCosts`, which nothing reads. Renters get the weekly goal instead (see Ganancias). |
| **Inicio's 2×2 metric grid, platform chips, gate tease, header streak badge, completeness note, consejos ×3** | One hero, one insight. Metrics move to the week, and per-app data moves to a "Por app" section. |
| **"Tasa de aceptación" and "Viajes/hora" at first level** | Not decision-relevant at a glance. "Viajes por hora" survives only inside Tu lugar. |
| **"Pronto: cómo te comparas por día y por hora"** | Promise copy for something that doesn't exist. |
| **Lector's fake mock offer card** | Fake numbers on a safety-relevant screen. |
| **The inert OEM notification switch and the second notification ask** | Ask once, in context, on Hoy (see §6). |
| **The "30%" pitch claim and the paywall's "$200 más a la semana"** | Unverifiable promises. |
| **The double AI parse in Import, the 4-way platform picker, and the cosmetic step timer** | Detect the file, parse once, show real progress. |
| **Fiscal's 7+ legal paragraphs** | One "¿Cómo funciona?" sheet with three bullets. |
| **43 dead `ui` strings and 12 dead `overlay` strings; `HomeUiText.kt`, `HomeViewModel.kt`; share-card variant code; the second verdict palette in `overlay/VerdictColors.kt`** | Dead weight and two sources of truth. |

---

## 3. Information architecture

### Top-level navigation: 3 tabs

| # | Name | Icon | Purpose (one line) |
|---|---|---|---|
| 1 | **Hoy** | `Icons.Filled.Home` | Is Kompara on, how much have I kept today, and what happened with my offers. |
| 2 | **Ganancias** | `bar_chart` (Material Symbols glyph imported as a vector drawable; `material-icons-core` doesn't have it) | My week and my month as a timeline. From here I reach my place in the city, IMSS/taxes, import and share. |
| 3 | **Cuenta** | `Icons.Filled.AccountCircle` | My plan, my semáforo, my costs, the reader's health, notifications, invites, help, profile. |

Rules:
- Tab roots get a large left-aligned title ("Hoy", "Ganancias", "Cuenta") and **no "K Kompara" wordmark bar**. The brand lives in the launcher icon, the chip's K and the share card.
- Drill-ins get a top app bar: back arrow + title (+ at most one action icon).
- The bottom bar stays visible on drill-ins (Tu día, Tu lugar, IMSS e impuestos, the Cuenta rows). It is **hidden on full-screen tasks**: Paywall, Importar, Compartir and onboarding.
- Re-tapping the active tab pops to its root (keep today's behaviour).

### Map

```
Hoy ─────────────── Tu día ── Detalle de oferta (sheet)
 └─ status pill ─── Lector
Ganancias [Semana | Mes]
 ├─ Semana ─ selector de semana (sheet) · Tu meta (sheet) · Tu día · Tu lugar · Compartir · Importar
 └─ Mes ──── IMSS e impuestos ── ¿Cómo funciona? (sheet) · Semana · Compartir
Cuenta
 ├─ Tu perfil (dialogs: Cerrar sesión, Eliminar cuenta) · Kompara Premium → Paywall
 ├─ Tu semáforo · Tus costos · Tu meta (sheet)
 ├─ Lector · Avisos
 └─ Invita y gana · Ayuda
Full-screen tasks: Paywall · Importar · Compartir · Onboarding / Entrar
Overlay: chip · quick sheet · greeting · paused chip
Notifications: Kompara se apagó · Turno cerrado · Tu semana · IMSS del mes · Tu prueba termina · (API <30) Kompara está leyendo
```

Depth: nothing is more than two taps from a tab root, except Detalle de oferta (Hoy › Tu día › sheet), which is a sheet.

### Fate table: every current surface → its fate in the new IA

| Current surface (inventory) | Fate | Where / reason |
|---|---|---|
| Shell top bar "K Kompara" | **Kill** | Large titles on tab roots; the brand lives in the chip and the share card. |
| Bottom bar, 5 tabs | **Rebuild → 3 tabs** | Hoy · Ganancias · Cuenta. |
| Pushed screens without back | **Fix** | A top app bar everywhere. |
| **Inicio** (`InicioDashboardScreen.kt`) | **Keep → Hoy** | Rebuilt around *today* plus reader status. |
| ↳ WatchdogBanner | **Merge** → Hoy status card | It *is* the home hero when the reader is off. |
| ↳ Weekly net hero | **Move** → Ganancias · Semana | Hoy's hero is today; the week has its own home. |
| ↳ Streak badge "🔥 4 semanas" | **Move** → share card only | Header noise. |
| ↳ Share icon | **Move** → Ganancias "Compartir tu semana" | Share where the content is. |
| ↳ GoalBar | **Keep** → Ganancias · Semana (+ Hoy's week row) | Finally gets a setter (Tu meta sheet). |
| ↳ CostNudge | **Replace** → one-tap "¿Tu coche rinde unos 12 km por litro?" card on Hoy after the first shift | Progressive and specific. |
| ↳ Platform chips | **Kill** → "Por app" section in the week | Filters are a power-user tool. |
| ↳ 2×2 metric grid + "Top X%" pills | **Kill on home** → "Cómo ganaste" rows (week) and Tu lugar | One hero per screen. |
| ↳ Benchmarks PaywallGate tease | **Kill** | The tease lives on Tu lugar, with real masked values. |
| ↳ "Consejos para ti" ×3 | **Keep 1** on Hoy, tappable | One insight per glance. |
| ↳ Completeness note | **Move** → Tu día, only when hours are unknown | |
| ↳ "Ver detalle del día" | **Replace** → tap the hero | |
| ↳ EmptyState "Aún no hay números" | **Replace** → Hoy first-run state | |
| **Comparar** (`CompararScreen.kt`) | **Merge → Tu lugar** (drill-in from the week) | Weekly-only data belongs with the week. |
| ↳ Week pill dropdown | **Merge** → Ganancias week nav + selector sheet | One week selector in the app. |
| ↳ Free hero + PercentileBar | **Keep** (Tu lugar hero, free) | The viral, honest headline (see the data rule in §7). |
| ↳ Single-app note | **Kill** | The rows speak for themselves. |
| ↳ 5-column table | **Rebuild** → stacked rows "Tú vs promedio" + "¿Qué app paga mejor?" | Fits 360dp; no 12sp cells. |
| ↳ "Dónde puedes ganar más" | **Keep, max 2** | |
| ↳ Cross-link to Inicio, "Pronto" footnote | **Kill** | |
| ↳ 12 dead `comparar_*` strings | **Delete** | |
| **Lector** (`LectorScreen.kt`) | **Move** → Hoy status pill + Cuenta › Lector (health checklist) | A state, not a tab. |
| ↳ "Lector para DiDi" card, Iniciar/Reiniciar | **Kill on API 30+**; on <30 a checklist row | Silent lane. |
| ↳ Static mock offer | **Kill** | Fake numbers. |
| ↳ 4 buttons | **Replace** → checklist with one fix action | |
| **Fiscal** (`FiscalScreen.kt`) | **Merge → IMSS e impuestos** (Ganancias › Mes) | Monthly. |
| ↳ IMSS tracker | **Keep, now FREE** | See §7. |
| ↳ Resumen fiscal + PDF | **Keep, Premium** | "Tus papeles". |
| ↳ Two explainer cards (7+ paragraphs) | **Replace** → "¿Cómo funciona?" sheet, 3 bullets | |
| **Ajustes** (`PlaceholderScreens.kt`) | **Rebuild → Cuenta tab** | Rows with current values; sections. |
| ↳ "Resumen de IMSS…", "Recordatorio semanal…" toggles | **Move** → Avisos | |
| ↳ Debug "Premium (prueba)" | **Keep** (debug builds only) | |
| **Tus costos** | **Keep, simplified** (3 fields + advanced) | Kill insurance/rent. |
| **Tu semáforo** | **Keep, rebuilt**; absorbs the Simulator | One number + steppers + live preview. |
| **Ayuda** | **Keep**: 5 questions + "Escríbenos por WhatsApp" | Fix false claims. |
| **Tu cuenta** | **Rename → Tu perfil** (under Cuenta) | |
| ↳ Delete dialog (E2) | **Keep** (good copy) | |
| **Historial** | **Kill** | Week arrows + Mes list. |
| **Resumen de la semana** | **Merge** → Ganancias · Semana | One week screen. |
| **Detalle del día** | **Keep → Tu día**, any day, ‹ › arrows | `day/{day}` finally used. |
| **Paywall** | **Keep, rebuilt** full-screen | Real legal footer, honest trial. |
| **Importar semana** | **Keep, simplified** | Detect → parse once → review → save. |
| **Invita y gana** | **Keep** under Cuenta | Redeem collapsed; days stated. |
| **Tu mes en Kompara / share card** | **Rebuild → Compartir** (week or month by context) | Preview = the exact PNG. |
| **Simulador** | **Kill as destination** → onboarding demo + Tu semáforo preview + Lector "Ver cómo funciona" | |
| **Pitch (3 pages)** | **Merge → Demo** (1 screen) | |
| **Signup: Teléfono** | **Keep** (onboarding step 5) | After the reader is live. |
| **Signup: Código** | **Keep** (step 6), auto-submit | |
| **Signup: Perfil** | **Kill** | City from area code; name optional. |
| **Divulgación** | **Keep, rewritten** | One screen; covers screen images. |
| **Pantalla limitada** | **Keep, copy fixed** | |
| **Activa el lector (waiting)** | **Keep** (step 3) | |
| **Activa el lector (granted)** | **Kill** → 800ms check, auto-advance | |
| **OEM survival kit** | **Keep, conditional, one ask** | Only on OEMs that kill apps. |
| **¡Todo listo para manejar!** | **Kill** → land on Hoy first-run | |
| **AUTH standalone** | **Keep → "Entra con tu WhatsApp"** (2 screens) | Reinstall / session expiry. |
| **Verdict chip** | **Keep, redesigned** | §5. |
| **Quick threshold sheet** | **Keep, steppers** | §5. |
| **Reader-down banner** | **Merge** → "Kompara en pausa" chip (chip footprint) | |
| **E1 Lector dialog** | **Kill** (API 30+); on <30 folded into onboarding | |
| **E3 `OcrConsentActivity`** | **Keep** (invisible) | |
| **E4 `ShareImportActivity`** | **Keep**; toast copy fixed | |
| **`PaywallGate` component** | **Replace** → masked-value pattern | §7. |
| **Notif: Watchdog + OCR stopped** | **Merge** → "Kompara se apagó" | One channel, one message, routes to the right fix. |
| **Notif: OCR foreground** | **Keep on API <30 only** → "Kompara está leyendo tus ofertas" | |
| **Notif: IMSS month-end** | **Keep, rewritten** | |
| **Notif: Week close** | **Keep, rewritten** → opens the week, not the month card | |
| **Export: Fiscal PDF** | **Keep** (Premium) | |
| **Export: Share PNG** | **Keep**; single renderer shared with the preview | |
| *New:* Turno cerrado notification | **Add** | The end-of-shift moment. |
| *New:* Overlay greeting "Kompara activo" | **Add** | Before-shift confidence. |
| *New:* Tu meta sheet | **Wire** existing GoalBar | |
| *New:* Detalle de oferta sheet | **Reuse** the parked expanded-chip data, after the fact | |

---

## 4. Screen-by-screen spec (new IA)

### 4.0 Shared rules (every screen inherits these)

- **Spacing and type:** 16dp edges and section rhythm; tonal cards with no shadows; Inter; dark by default. Tokens per `design-principles.md`.
- **Number style (one rule, everywhere):**
  - Totals are whole pesos, e.g. `$6,480`.
  - Per-km is two decimals: `$8.40/km`.
  - Per-hour is whole pesos: `$186/h`.
  - Counts are plain: `62 viajes`.
  - Hours: `41 h` or `5 h 20 min`.
  - Use tabular figures.
  - "neto" appears once per block, not on every number.
- **Vocabulary (one per concept):**
  - **semáforo**: the system.
  - **verde / amarillo / rojo**: its states. Gris = couldn't read.
  - **tu mínimo**: the green floor.
  - **neto**: after gasolina y desgaste.
  - **Retired from UI copy:** veredicto, Conviene/Regular/No conviene, lector de pantalla, umbral, Tú/Lugar as column jargon, IPK/IPH, aceptación.
- **Casing:** sentence case everywhere. No forced caps.
- **Loading:** a static slate skeleton in the final layout, with no shimmer and never a blank screen. If loading passes 3s, add the line "Cargando tus números…".
- **Error (local data):** "No pudimos cargar tus números." + text button "Reintentar".
- **Error (network):** "Sin conexión. Lo intentamos de nuevo cuando tengas datos." + "Reintentar".
- **Empty:** one line saying what will appear and when. Never a lone CTA to some other place.
- **Locked (Premium):**
  - The driver's own value is always visible. The comparison/archive value is masked as `•••` with a 🔒 glyph.
  - One tonal card per screen: "Ve [what] · Prueba 14 días gratis" → Paywall.
  - Never blur the driver's own numbers, and never lock a whole screen.
- **Components:** reuse `KomparaButton` (4 tiers), `KomparaCard`, `VerdictBadge`, `PercentileBar`, `KomparaProgressBar`, `KomparaTextField`, `EmptyState`.
  - New: `ListRow` (label · value · chevron, 56dp), `StepperField` (− value +, 48dp targets) and `MaskedValue` (`•••` + lock).
- **Colour discipline:**
  - Verdict colours appear only on verdicts.
  - Comparisons (better/worse) use emerald ▲ / slate ▼.
  - IMSS progress uses emerald.
  - Statuses use emerald (good), `error` red (broken), or an accent-amber `#F59E0B` outline (needs attention), never verdict amarillo `#F2B705`.
  - New token: `chipNeutral` slate-600 `#475569` for the "couldn't read" and "paused" chip.

---

### 4.1 Hoy (tab 1)

- **Purpose:** Is Kompara on, and how much have I kept today?
- **Hero:** today's net, `$1,240`, at 44sp Black.
- **Primary action:** state-dependent and never more than one. Reader off → **"Encender"**. First run without notification permission → **"Avísame si se apaga"**. Healthy → none (the hero card itself opens Tu día).

**Layout (healthy, mid-shift), top to bottom:**
1. Title row: "Hoy" on the left (large title), status pill on the right: `● Lector activo` (emerald dot). Tap → Lector.
2. Hero card (tappable → Tu día):
   - "Ganancia neta de hoy"
   - **`$1,240`**
   - "9 viajes · 5 h 20 min"
   - Chevron on the right.
3. Insight card (exactly one, tappable), highest priority from the free engine. For example, amber accent, title **"Dejaste ir 2 viajes verdes"**, body "Eran $310 netos. Cuando veas verde, tómalo con confianza." Tap → Tu día filtered to those offers.
4. Section "Últimas ofertas", 3 rows of trip-card discipline:
   - `[Verde]` "$148 · $7.20/km" / "14:32 · La tomaste"
   - `[Rojo]` "$62 · $3.10/km" / "14:18 · La pasaste"
   - `[Gris]` "Sin leer" / "14:05"
   - Text button "Ver todo tu día".
5. Week row (ListRow): "Esta semana" · `$6,480` · ›. If a goal exists, a 4dp emerald bar sits under it with "de $9,000". → Ganancias · Semana.

**Secondary actions:** status pill → Lector; "Ver todo tu día"; week row.

**States:**
- **Reader off** (accessibility revoked or the OEM killed it): the hero is replaced by a red-outlined card:
  - Title **"Kompara está apagado"**, body "Tu teléfono lo cerró. Sin él no ves el semáforo en tus ofertas."
  - Bottom-pinned primary **"Encender"** goes to Accessibility settings. If the disclosure was never accepted, it goes to Aviso first.
  - Today's numbers stay visible below in a muted style.
- **Paused on API <30** (capture consent lost): amber-outlined status pill "Lector en pausa" and primary **"Seguir leyendo"**, which goes to the consent prompt.
- **First run** (reader on, never seen an offer):
  - Title **"Listo. Abre tu app de viajes."**
  - Body "Cuando llegue una oferta, tu semáforo aparece encima, así:" followed by a static sample chip (verde, `$8.40/km`, tagged "Ejemplo").
  - Primary **"Avísame si se apaga"** (API 33+, only if not yet granted), with the line "Te mandamos un aviso si tu teléfono apaga Kompara."
  - Footer line "Usamos los valores típicos de CDMX · Cambiar" (→ city picker).
  - Text button "Ver cómo funciona" (demo).
- **No trips today yet, history exists:** hero "Hoy aún no hay viajes" and sub "Ayer te quedaron $1,120". The insight shows yesterday's best learning if any.
- **Costs never configured** (after the first shift): the insight slot shows **"¿Tu coche rinde unos 12 km por litro?"** with a tonal button "Sí" (confirm the defaults) and a text button "Cambiar" (→ Tus costos). It is shown once; it doesn't come back after "Sí".
- **Loading:** skeleton of pill + hero + one card.
- **Locked:** none. Hoy is 100% free.
- **Error:** shared pattern.

**Removed vs today:** the weekly hero (now the week row), the streak badge, share, platform chips, the 2×2 grid with pills, the paywall tease, two of three consejos, the completeness note, "Ver detalle del día", and the cost nudge card. Inicio could reach ~10 blocks; Hoy shows 4–5.

**1-second glance (60cm):** one dot (on or off), one big number, one coloured insight. If the dot is red, the whole screen is a red card with one big button.

---

### 4.2 Tu día (drill-in from Hoy's hero, the week chart, the shift notification)

- **Purpose:** what happened today (or any day), offer by offer.
- **Hero:** the day's net.
- **Primary action:** none. It's a ledger. Rows open details.

**Layout:**
1. Top app bar: ← "Tu día".
2. Day stepper: "‹  Martes 21 oct  ›" (the → arrow is disabled on today).
3. Hero: "Ganancia neta" / **`$1,240`** / "9 viajes · 5 h 20 min · $232/h".
4. Section "Turnos": rows "08:00 – 12:30 · 5 viajes" with `$640` on the right; "17:10 – ahora · 4 viajes" with `$600`.
5. Section "Ofertas":
   - Filter chips "Todas (23)" | "Verdes que pasaste (2)".
   - Rows (as on Hoy): verdict pill with word + "$fare · $X/km", second line "hh:mm · La tomaste / La pasaste / Se venció".
   - Tap → **Detalle de oferta** sheet.
6. Only when the shift detector found no shift: muted line "Sin turno detectado: aún no podemos calcular tus horas."

**Detalle de oferta (bottom sheet):**
- Title "Oferta de las 14:32" + pill `[Verde]`.
- Hero "Te quedaban **$114**" and sub "$7.10/km · $186/h netos".
- Rows: "Tarifa · $148", "Distancia · 16 km (4 para recoger)" (pickup is shown only if parsed), "Tiempo · 22 min", "Gasolina y desgaste · −$34".
- Why line (from `overlay_explain_*`): "Bien por km y por hora." / "Bien por km, pero lento: poco por hora." / "No pudimos leer la tarifa."
- Outcome: "La tomaste".
- Text button "¿Se leyó mal? Avísanos" opens a WhatsApp chat with Kompara support, prefilled with the time and the derived numbers only (no screen content). The driver sends it.

**States:**
- Empty past day: "Este día no hubo ofertas."
- Today, empty: "Aún no hay ofertas hoy."
- Loading: skeleton.
- Imported-only day: the hero shows the day's net from the import, "Turnos" and "Ofertas" are hidden, and the note reads "Este día viene de tu resumen importado; no tenemos tus ofertas."
- Locked: days older than the free window (4 weeks) show the hero masked and the shared lock card "Ve todo tu historial · Prueba 14 días gratis".

**Removed vs today:** the five metric cards (now in the week), "Mejores horas" (now in the week, where it's statistically meaningful), the "vistas · aceptadas · rechazadas" funnel line (replaced by the filter counts and per-row outcome, which always add up).

**1-second glance:** one net number and a column of coloured pills. "Lots of red after 14:00" reads as a pattern without reading any text.

---

### 4.3 Ganancias · Semana (tab 2, default segment)

- **Purpose:** my week as a timeline, and the door to how I compare.
- **Hero:** the week's net.
- **Primary action:** none at the root. Share is a secondary, full-width outlined button at the end. The week's primary *moment* is the Tu lugar row.

**Layout:**
1. Title "Ganancias"; segmented control **"Semana | Mes"**.
2. Week stepper: "‹  13 – 19 oct  ›". Tapping the label opens the **selector de semana** sheet (weeks newest first, grouped by month, with a lock glyph on weeks outside the free window). The current week shows "13 – 19 oct · en curso".
3. Hero: "Ganancia neta" / **`$6,480`** / "62 viajes · 41 h".
4. Goal line (if set): emerald progress bar and "Te faltan $2,520 para tu meta de $9,000", or "¡Llegaste a tu meta! 🎉". If not set: text button **"Ponte una meta"** → **Tu meta** sheet.
5. Bar chart, 7 bars, Mon–Sun ("L M M J V S D"):
   - Height = net per day. Today is emerald; past days are slate-400; future days are outline only.
   - Tapping a bar shows its value above the chart ("Vie · $1,380") and a second tap opens Tu día.
6. **Tu lugar row** (card, tappable → Tu lugar): "Tu lugar en CDMX" / **"Le ganas al 78%"** with a pill "Top 22%" and a chevron.
   - Pending city data: "Tu lugar en CDMX" / "Aún juntamos conductores de tu ciudad".
7. Section "Cómo ganaste" (3 ListRows): "Por hora · $158", "Por km · $8.40", "Por viaje · $105".
8. Section "Por app" (only with 2+ apps): "Uber · $4,100 · 34 viajes", "DiDi · $2,380 · 28 viajes". Text names, no marks.
9. Section "Tus mejores horas": "Viernes, 19–21 h · $340 netos" (top 1–2 blocks, from own captured trips).
10. Outlined button **"Compartir tu semana"** → Compartir.
11. Text button "¿Te faltan viajes? Importa tu resumen" → Importar.

**Tu meta (bottom sheet):**
- Title "Tu meta de la semana"; field `$` "9,000"; helper "Si rentas el coche, súmale la renta: así sabes cuándo ya trabajas para ti."
- Primary "Guardar meta"; text "Quitar meta".

**States:**
- Empty week: hero "$0" replaced by "Esta semana aún no hay viajes"; the chart shows empty bars; Tu lugar row hidden; the stepper still works.
- Imported-only week: everything renders, with a muted tag "Importada" next to the week label. This fixes "Aún no hay números".
- Locked (older than 4 weeks, free): stepper and week label visible; hero shows the week net masked `$•••`; one lock card "Tu historial completo es Premium · Prueba 14 días gratis"; the rest hidden.
- Loading: skeleton.
- First week (fewer than 2 days of data): the chart shows the available days only; the Tu lugar row reads "Tu lugar se calcula al cerrar la semana · lunes".

**Removed vs today:** Historial as a screen; Resumen de la semana's 5 key/value rows + 5 cards + bars; Inicio's grid; Comparar's week pill. One screen now serves every week.

**1-second glance:** the week's net plus the bar shape (which days were good). The Tu lugar pill is the second read.

---

### 4.4 Ganancias · Mes (tab 2, second segment)

- **Purpose:** my month, and whether I'm getting IMSS.
- **Hero:** the month's net.
- **Primary action:** none. The IMSS card is the main tap.

**Layout:**
1. "Ganancias" + segmented "Semana | **Mes**".
2. Month stepper: "‹  Octubre 2026  ›".
3. Hero: "Ganancia neta" / **`$24,310`** / "248 viajes · 160 h".
4. **IMSS card** (tappable → IMSS e impuestos): title "IMSS este mes"; one line per app with emerald mini progress bars: "Uber · te faltan $3,164 · quedan 11 días", "DiDi · ya te toca".
5. Section "Semanas": ListRows "13 – 19 oct · $6,480 ›" → that week in the Semana segment.
6. Outlined button "Compartir tu mes" → Compartir (month variant).

**States:**
- Empty month: "Este mes aún no hay viajes."
- Past month: the IMSS card reads "Uber · te tocó" / "DiDi · no te tocó".
- Locked (months older than the free window): hero masked plus the lock card.
- Loading: skeleton.

**Removed vs today:** the Fiscal tab's month chips (replaced by the stepper) and the six-month chip row.

**1-second glance:** the month's net and whether the IMSS bars are full.

---

### 4.5 Tu lugar (drill-in from the week's Tu lugar row; the hero share target)

- **Purpose:** how I compare to drivers in my city this week, and where I could earn more.
- **Hero:** one sentence, "Le ganas al 78% de los conductores de CDMX", plus the 20-person bar.
- **Primary action:** **"Compartir en WhatsApp"** when unlocked; **"Probar 14 días gratis"** when locked (share then moves to a top-bar icon).

**Layout:**
1. Top app bar: ← "Tu lugar" · share icon.
2. Context line: "13 – 19 oct · CDMX".
3. Hero card (free):
   - **"Le ganas al 78% de los conductores de CDMX"** (24sp Bold)
   - `PercentileBar` (20 people)
   - Pill "Top 22%"
   - Basis line (honest, muted): "Por hora neta, contra conductores de Uber de tu ciudad." Until the combined population exists, this is the primary platform (`comparar-redesign-spec.md` §2, gap 2). The copy never says "todos".
4. Section **"Tú contra el promedio de CDMX"** (Premium). One row per metric, with the label on the left and two stacked values on the right:
   - "Por hora": **Tú $158**, under it "Promedio $142 · Top 30%", plus an emerald ▲.
   - "Por km": **Tú $8.40**, "Promedio $7.90 · Top 41%".
   - "Por viaje": **Tú $105**, "Promedio $112 · Top 58%", plus a slate ▼.
   - "Viajes por hora": **Tú 1.5**, "Promedio 1.7".
   - "Comisión" (only when known from an import): **Tú 24%**, "Promedio 27% · menos es mejor".
5. Section **"¿Qué app paga mejor en CDMX?"** (Premium): "Uber · $150/h en promedio", "DiDi · $131/h en promedio"; muted "Por hora, para comparar parejo."
6. Section **"Dónde puedes ganar más"** (Premium, max 2 cards). For example: "El top 20% gana $41/h más que tú" / "Casi todo lo hacen entre 18 y 22 h."
7. Bottom-pinned primary "Compartir en WhatsApp" → Compartir (week variant, standing pre-filled).

**Like-for-like rule:** the "Tú" value and the city average must be on the same basis. If population stats are gross, "Tú" is the driver's gross, labelled "bruto". Never compare our net against their gross.

**States:**
- **Locked (free):**
  - The hero stays free.
  - Each Premium row shows the driver's own value and masks the city side: "**Tú $158** · Promedio ••• · Top ••% 🔒".
  - The "¿Qué app…?" and "Dónde…" sections collapse into one lock card: "Ve cómo te comparas en todo y qué app paga mejor en tu ciudad · Prueba 14 días gratis, sin tarjeta".
  - Primary "Probar 14 días gratis".
- **Not enough real drivers in the city-week (the data honesty rule):**
  - The hero becomes "Aún juntamos conductores de CDMX para compararte."
  - It falls back to a self-benchmark, which is free and real: "Contra tu semana pasada: **+12% por hora**."
  - No "Top X%", no share of a standing.
- **Week in progress:** "Tu lugar se calcula al cerrar la semana, el lunes." It shows last week's standing with the label "Semana pasada".
- **Empty (no data):** "Tu lugar sale de tu semana. Maneja con Kompara prendido y el lunes lo verás aquí."
- **Premium, not verified:** never locks (see §7). If the backend still requires verification to *contribute*, show a one-line tonal note: "Importa tu resumen semanal para que tu semana cuente en el promedio de CDMX · Importar". It doesn't block anything.
- **Loading:** skeleton.
- **Error** (benchmarks fetch failed): "No pudimos traer los promedios de CDMX." + "Reintentar". The driver's own values still render.

**Removed vs today:** the 5-column 12sp table, the "Tú = tu mezcla de apps…" legend, the single-app note, the cross-link to Inicio, the "Pronto" footnote, "Datos de muestra" standing claims, and the hardcoded "Uber"/"DiDi" headers (moved to `strings.xml`).

**1-second glance:** one sentence and 20 people with yours highlighted.

---

### 4.6 IMSS e impuestos (drill-in from the Mes IMSS card; the month-end notification)

- **Purpose:** whether each app must register me with IMSS this month, and what they withhold.
- **Hero:** per-app IMSS progress.
- **Primary action:** **"Descargar PDF para tu contador"** (Premium). Locked: **"Probar 14 días gratis"**.

**Layout:**
1. Top app bar: ← "IMSS e impuestos" · month chip "Octubre ▾" (sheet with the last 6 months).
2. Section "IMSS este mes" (FREE). One card per app:
   - "Uber" + status text "En camino".
   - Emerald progress bar.
   - "$5,200 de $8,364".
   - "Te faltan $3,164 · quedan 11 días".
   - Muted: "A este ritmo llegas a $9,100."
   - Statuses: "Ya te toca" / "En camino" / "Difícil este mes"; past months "Te tocó" / "No te tocó". **Status words carry the meaning; no verdict colours.**
3. Explainer line: "Si en un mes juntas al menos $8,364 en una app, esa app debe inscribirte al IMSS." + text button "¿Cómo funciona?"
4. Section "Lo que te retienen" (Premium) with toggle "Mes | Año":
   - Hero **"≈ $612"**.
   - Rows: "Uber · $98 de ISR · $374 de IVA", "DiDi · $41 de ISR · $157 de IVA".
   - Muted note: "DiDi no muestra su comisión: calculamos sobre lo que cobras."
5. Primary (bottom-pinned): "Descargar PDF para tu contador". Muted: "Sirve también para pedir un crédito."
6. Footer: "Son cálculos con tus datos, no asesoría fiscal. Confírmalo con un contador o en el IMSS."

**"¿Cómo funciona?" sheet** (3 bullets, legal review B-038):
- "**IMSS:** si en un mes juntas al menos un salario mínimo mensual ($8,364) en una app, esa app debe darte de alta."
- "**Retenciones:** cada app te quita 2.1% de ISR y 8% de IVA de lo que cobras y lo paga al SAT por ti."
- "**Tu RFC:** dáselo a cada app; si no lo tiene, te pueden retener más."

**States:**
- Locked (free): the IMSS section is fully free. "Lo que te retienen" shows "≈ $••• 🔒" and one lock card "Ve tus retenciones y descarga tu PDF · Prueba 14 días gratis".
- Empty month: "Este mes aún no hay ingresos. Cuando manejes o importes una semana, verás aquí tu avance."
- Config fallback (thresholds not refreshed): muted line "Usamos el salario mínimo de referencia; no pudimos actualizarlo."
- Exporting: the button reads "Preparando tu PDF…" (disabled) and then opens the share chooser.
- Error: "No pudimos crear el PDF. Inténtalo otra vez."
- Loading: skeleton.

**Removed vs today:** the "Cobertura IMSS" title on a "Fiscal" tab, the "Umbral por plataforma" jargon line, the 7+ paragraph explainers, the per-platform Bruto/Neto/ISR/IVA/Total five-row cards (now one line per app), and the full-screen blur.

**1-second glance:** a bar per app and how full it is.

---

### 4.7 Compartir (full-screen task; from the week, the month, Tu lugar, the week-close notification)

- **Purpose:** send a brag card to my drivers' group.
- **Hero:** the card preview, which is **the exact PNG that will be sent**.
- **Primary action:** **"Compartir en WhatsApp"**.

**Layout:**
1. Top app bar: ✕ "Compartir tu semana" (or "tu mes"). No bottom bar.
2. Card preview (rendered once by the single `ShareCardRenderer`, shown scaled), 1080×1920 story format:
   - "K Kompara · 13 – 19 oct"
   - Hero: "Top 22% en CDMX" when amounts are hidden, or "$6,480 netos" when shown.
   - Grid: "Mejor día · viernes", "Mejor app · Uber", "Viajes · 62", "Racha · 🔥 4 semanas".
   - Footer: "kompara.mx".
3. Switch row **"Ocultar montos"**, **default ON**. Showing your earnings in a group can make you a target; the driver opts in to show pesos.
4. Primary "Compartir en WhatsApp"; text button "Más opciones" (system chooser).
5. Caption sent: "Mi semana manejando: Top 22% en CDMX 🚀 Mide la tuya con Kompara: kompara.mx".

**States:**
- No standing (pending or not enough data): the hero becomes "62 viajes esta semana" (or the net if shown) and the caption drops the standing.
- WhatsApp missing: the primary opens the chooser and is labelled "Compartir".
- Loading: "Preparando tu tarjeta…".
- Error: "No pudimos preparar la imagen." + "Reintentar".

**Removed vs today:** the preview-vs-PNG mismatch, "always the month card", the unreachable variant toggle, and the "Tu mes en Kompara" title for week contexts.

**1-second glance:** this is what my group will see.

---

### 4.8 Importar (full-screen task; from the week's "¿Te faltan viajes?", the OS share sheet, the Tu lugar verification note)

- **Purpose:** bring in weeks the reader didn't see.
- **Hero:** the review of parsed numbers.
- **Primary action per step:** "Elegir archivos" → "Guardar".

**Layout. Step 1, "Trae tu resumen":**
1. Top app bar: ✕ "Importar".
2. Body: "Para semanas que Kompara no vio, como las de antes de instalarla."
3. Three info rows (not choices):
   - "Uber · el PDF semanal que te llega por correo (el más completo)"
   - "DiDi · 2 capturas: Ganancias y Tablero"
   - "inDrive · 1 captura de tu resumen"
4. Muted privacy line: "Tu archivo se sube a Kompara solo para leer tus números." (The exact retention promise needs a backend fact. If files are deleted after parsing, add "y lo borramos al terminar.")
5. Primary **"Elegir archivos"** (multi-select; PDF, PNG, JPG, WebP; up to 10 MB).

**Step 2, "Leyendo tu archivo":** a card with a 3-row checklist driven by *real* events: "Subiendo tu archivo", "Leyendo tus números", "Armando tu semana". Linear `KomparaProgressBar`; "Tarda unos segundos."

**Step 3, "Revisa tu semana":**
- "13 – 19 oct · Uber".
- Rows: Neto · Bruto · Viajes · Horas · Km. Missing fields show "No venía en tu archivo".
- Primary **"Guardar"** (commits the *already-parsed* result; no second parse); text "Descartar".

**After save:** a toast "Listo, tu semana ya está en Ganancias", then navigate to that week.

**States:**
- Shared-in (from the OS share sheet): skip step 1 and go straight to step 2. No "Recibimos tu archivo" interstitial.
- Incomplete DiDi pair: "Falta la captura del Tablero." + primary "Agregar captura".
- Unreadable: "No pudimos leer este archivo." / "Para Uber usa el PDF semanal; para DiDi, las 2 capturas." + "Elegir otro".
- Network: shared network pattern + "Reintentar".
- Week already exists: in Review, "Ya tienes esta semana. ¿La reemplazamos con la importada?" + "Reemplazar" / "Cancelar".
- Signed out (only reachable if account creation was deferred by a network failure): "Para importar, termina tu cuenta" + primary "Terminar mi cuenta".

**Removed vs today:** the 4-card platform picker (detection decides the platform), the double parse, the cosmetic 900ms timer, "Extrayendo datos con IA…", the "¡Semana importada!" screen, and "Elegir otra plataforma" as the only way back.

**1-second glance:** in review, "these are my numbers, they look right, Guardar".

---

### 4.9 Paywall (full-screen task)

- **Purpose:** explain Premium in the driver's terms and start the trial.
- **Hero:** a personal question built from their own data (not a promise).
- **Primary action:** **"Probar 14 días gratis"** (eligible) or **"Suscribirme por $79 al mes"**.

**Layout:**
1. ✕ top-left. No bottom bar.
2. Hero line: **"Esta semana te quedaron $6,480. ¿Es mucho o poco en CDMX?"** When there's no data: "¿Cómo vas contra los conductores de tu ciudad?"
3. Title "Kompara Premium".
4. Three benefit rows, each with an icon:
   - "**Tu lugar en CDMX**, métrica por métrica, cada semana"
   - "**Qué app paga mejor** en tu ciudad"
   - "**Todo tu historial** y **tus papeles**: retenciones y PDF para tu contador"
5. Price card (emerald): **"$79 al mes"** / "≈ $18 a la semana · Cancela cuando quieras".
6. Fixed footer:
   - Primary.
   - Text button "Restaurar compra".
   - Legal (11sp): "Se renueva cada mes hasta que canceles en Google Play." + links "Términos" · "Privacidad".
7. Muted line under the primary when the trial is eligible: "Sin tarjeta. Al terminar, tú decides."

**States:**
- Loading the price: the price card shows a skeleton and the primary is disabled with "Cargando…".
- Trial already used: the primary is "Suscribirme por $79 al mes" and there is no trial line.
- Active trial: a top line "Tu prueba termina el 3 nov" and primary "Seguir con Premium por $79 al mes".
- Play unavailable: "Para suscribirte necesitas Google Play en este teléfono." (Restore is still shown.)
- Purchase error: snackbar "No se pudo abrir la compra. Inténtalo de nuevo."
- Restored / none found / already active: keep today's snackbars.

**Removed vs today:** "Si te ayuda a ganar $200 más a la semana, se paga solo", "Percentiles:", "pendientes de revisión legal", and the bottom bar.

**1-second glance:** a price and one green button.

---

### 4.10 Cuenta (tab 3)

- **Purpose:** everything about me and how Kompara behaves, each row showing its current value.
- **Hero:** the plan status.
- **Primary action:** none (settings).

**Layout:**
1. Title "Cuenta".
2. Profile card (→ Tu perfil): "Juan" (or "Tu perfil"), "+52 55 1234 5678 · CDMX", and a plan pill: "Gratis", "Premium · prueba, quedan 9 días", or "Premium".
3. For free users only, a tonal row: "**Kompara Premium** · Prueba 14 días gratis ›" → Paywall.
4. Section "Tu semáforo":
   - "Tu semáforo · Verde desde $8.00/km ›"
   - "Tus costos · $2.15 por km ›"
   - "Tu meta · $9,000 a la semana ›" (or "Sin meta").
5. Section "Kompara":
   - "Lector · Activo ›" (the value turns red "Apagado" when off)
   - "Avisos ›"
   - "Invita y gana · 7 días por amigo ›"
   - "Ayuda ›"
6. Footer (muted): "Kompara {versión} · Aviso de privacidad · Términos".

**States:**
- The reader off shows red in the Lector row value.
- Signed out (deferred by a network failure): the profile card reads "Termina tu cuenta · guarda tu configuración" → the Número step.
- Debug builds keep "Premium (prueba)".

**Removed vs today:** seven equal-weight valueless rows, "Historial" (now in Ganancias), "Ver el simulador", inline toggles (now in Avisos), and the dead "en construcción" strings.

**1-second glance:** my plan, and whether anything is red.

---

### 4.11 Tu semáforo (from Cuenta; Lector; Hoy first-run "Ver cómo funciona"; the long-press sheet's "Más ajustes")

- **Purpose:** set the one number that turns an offer green, and see what it does.
- **Hero:** "Verde desde **$8.00** /km neto".
- **Primary action:** none. It saves itself. The steppers are the interaction.

**Layout:**
1. Top app bar: ← "Tu semáforo".
2. Live preview: a row of three real `VerdictChipUi` chips (at ~70% scale) on three sample offers, captioned "Viaje largo", "Viaje corto", "Recogida lejos". They recolour instantly as the floor changes. *This replaces the Simulator.*
3. Hero control: label "Verde desde"; `StepperField` **[−] $8.00 /km [+]**, step $0.50 (or $5/h).
4. One line: "Abajo de **$6.00** es rojo; en medio, amarillo."
5. Segmented: "Decide por: **Km** | Hora".
6. Expander "Más ajustes" (collapsed):
   - "Rojo por debajo de" stepper (by default it follows green at 75%).
   - "La otra cifra" (the non-deciding metric's green and red floors, used only for the ▼ marker), with the helper "Si la otra cifra queda baja, te la marcamos con ▼ en el semáforo."
7. Text button "Volver a lo típico de CDMX ($8.05/km)".
8. Muted footer: "Se guarda solo y lo usa el semáforo en tus ofertas."

**States:**
- First visit: the preview chips pulse once (200ms) to show that they're live.
- After a reset: snackbar "Listo: volviste a lo típico de CDMX" with "Deshacer".
- Loading: skeleton.

**Removed vs today:** the 55-word intro, "¿QUÉ DECIDE TU SEMÁFORO?", two always-visible sliders, the decorative legend badges, "Valle de Mexico (CDMX + Edomex)" in the reset label, the Simulator screen and its silent-persist playground, and the "Conviene / Regular / No conviene" segments.

**1-second glance:** one number and three coloured chips.

---

### 4.12 Tus costos (from Cuenta; Hoy's one-tap cost card "Cambiar")

- **Purpose:** make "neto" true for my car.
- **Hero:** "Manejar te cuesta **$2.15** por km".
- **Primary action:** **"Guardar"**. This is explicit because it recalculates history.

**Layout:**
1. Top app bar: ← "Tus costos".
2. Hero card: "Manejar te cuesta **$2.15 por km**" / "En un viaje de 12 km son unos $26." It updates live.
3. Segmented: "Gasolina | Eléctrico".
4. Gasoline fields:
   - "Rendimiento": `12` "km por litro"; helper "¿No sabes? Deja 12: es lo típico de un sedán."
   - "Precio de la gasolina": `$` `24.50` "por litro"; helper "Precio típico en tu ciudad" (from remote config when available).
5. Electric fields: "Consumo" `15` "kWh por 100 km"; "Costo de carga" `$` `3.20` "por kWh".
6. Expander "Mantenimiento": `$` `0.80` "por km"; helper "Llantas, aceite y servicios."
7. Primary "Guardar". On success: pop back, then snackbar "Listo. Recalculamos tus viajes con tus costos."

**States:**
- Validation: "Revisa este número" under an out-of-range field (e.g., km/L outside 4–40).
- EV edit pre-fills its saved values (fixes the blank-on-edit bug).
- Saving: "Guardando…" (disabled).
- Error: "No pudimos guardar. Inténtalo otra vez."

**Removed vs today:** "Seguro (MXN por día)" and "Renta o financiamiento (MXN por día)" (unused in any calculation), the fixed sample trip "$120.00 y 12.0 km" phrasing, and the "Vehículo eléctrico" switch card.

**1-second glance:** my cost per km.

---

### 4.13 Lector (from Hoy's status pill; Cuenta › Lector; the "Kompara se apagó" notification)

- **Purpose:** is the reader healthy, and fix it if not.
- **Hero:** the status word ("Activo" or "Apagado").
- **Primary action:** only when something is wrong: **"Encender"** / **"Arreglar"** for the first failing row.

**Layout:**
1. Top app bar: ← "Lector".
2. Hero: an emerald dot plus **"Activo"** (or a red dot plus **"Apagado"**); body "Lee tus ofertas en Uber, DiDi e inDrive y te pone el semáforo encima. Todo pasa en tu teléfono."
3. Checklist (ListRows with a trailing ✓ or a tonal fix button):
   - "Permiso de accesibilidad · ✓" / [Activar]
   - "Batería sin restricciones · ✓" / [Revisar]. Opens a sheet with *this phone's* OEM steps, one deep-link button per step.
   - "Aviso si se apaga · ✓" / [Activar] (notification permission plus the channel).
   - API <30 only: "Lectura de ofertas · ✓" / [Iniciar] (MediaProjection).
4. Text buttons: "Ver cómo funciona" (replays the onboarding demo), "Qué lee Kompara" (the disclosure text, read-only), "Apagar Kompara" (→ Accessibility settings; honest off).

**States:**
- All good: no primary.
- Off: red hero + primary "Encender".
- Partial: amber-outlined row + primary for that row.
- Just returned from settings: "Revisando…" for ≤1s, then the result.
- OEM unknown: the battery sheet shows the generic step "Permite que Kompara funcione en segundo plano en los ajustes de batería."

**Removed vs today:** "Lector para DiDi", Iniciar/Reiniciar on API 30+, the fake 220dp mock offer, four stacked buttons, and the E1 dialog.

**1-second glance:** a green dot or a red one.

---

### 4.14 Avisos (from Cuenta)

- **Purpose:** choose which notifications I get.
- **Hero:** none (a list).
- **Primary action:** none, except when system notifications are off: banner "Tienes los avisos apagados en tu teléfono" + primary "Activar avisos".

**Layout (switch rows, each with a one-line description):**
1. "Si Kompara se apaga": "Para que lo vuelvas a encender." (on)
2. "Al cerrar tu turno": "Cuánto te quedó, en un aviso." (on)
3. "Tu semana, los lunes": "Tu ganancia y tu lugar en la ciudad." (on)
4. "IMSS a fin de mes": "Si ya te toca IMSS en cada app." (on)

**States:** system-blocked banner; per-channel disabled at the OS level shows "Apagado en tu teléfono · Cambiar".

**Removed vs today:** "Recordatorio semanal para compartir" (now the week-close notification, which leads with value, not with sharing).

**1-second glance:** four switches.

---

### 4.15 Tu perfil (from the Cuenta profile card)

- **Purpose:** my number, name and city; log out or delete.
- **Hero:** none.
- **Primary action:** **"Guardar cambios"** (enabled only when something changed).

**Layout:**
1. Top app bar: ← "Tu perfil".
2. "Tu WhatsApp": +52 55 1234 5678 (read-only); helper "¿Cambiaste de número? Escríbenos por WhatsApp."
3. "Tu nombre (opcional)".
4. "Tu ciudad": picker with accented names ("Valle de México (CDMX y Edomex)", "Monterrey", "Guadalajara", "Puebla-Tlaxcala", "Toluca", "Tijuana", "León", "Querétaro", "Mérida", "Cancún"); helper "Con ella te comparamos con conductores de tu zona."
5. Primary "Guardar cambios" → snackbar "Cambios guardados".
6. Outlined "Cerrar sesión" → dialog: "¿Cerrar sesión?" / "Tu configuración y tus totales quedan guardados en tu cuenta." / "Cerrar sesión" · "Cancelar".
7. 24dp gap, then text button in error red: "Eliminar mi cuenta" → today's dialog (keep its copy: "¿Eliminar tu cuenta?" / "Se borrarán de forma permanente…").

**City-change confirm:** "¿Cambiar a Monterrey?" / "Desde esta semana te comparamos con conductores de Monterrey. Tu semáforo no cambia." / "Cambiar" · "Cancelar".

**States:** busy (inputs disabled); save error and delete error (keep today's copy); network.

**Removed vs today:** logout without confirmation, the destructive row next to logout, unaccented city names, and the floating-label fields (use `KomparaTextField`).

**1-second glance:** who I am and which city I'm compared in.

---

### 4.16 Invita y gana (from Cuenta; deep link `kompara://referral?code=X`)

- **Purpose:** share my code; redeem one.
- **Hero:** my code.
- **Primary action:** **"Compartir por WhatsApp"**.

**Layout:**
1. Top app bar: ← "Invita y gana".
2. Hero: "Tu código" / **`PANA4821`** (40sp, letter-spaced).
3. Line: "Por cada conductor que entre con tu código, **los dos ganan 7 días de Premium**." (The number comes from the backend; the copy always states it.)
4. Primary "Compartir por WhatsApp"; outlined "Copiar código" → toast "Código copiado".
5. Muted stats line: "Invitaste a 3 · ganaste 21 días".
6. Collapsed row "¿Te invitaron? Escribe su código ›". It expands into the field "Código" + tonal "Canjear". Success: "¡Listo! Ganaste 7 días de Premium."
7. Message sent: "Uso Kompara para saber si un viaje me deja antes de aceptarlo. Entra con mi código PANA4821 y los dos ganamos 7 días de Premium: kompara.mx/i/PANA4821".

**States:**
- Loading: skeleton.
- Load error: "No pudimos cargar tu código." + "Reintentar".
- Redeem error: the backend message.
- Opened by deep link: the redeem row is pre-expanded and pre-filled.

**Removed vs today:** the redeem form below the fold under a large share section, and the vague "días de premium gratis".

**1-second glance:** my code and one green button.

---

### 4.17 Ayuda (from Cuenta; links from Lector)

- **Purpose:** quick answers plus a human.
- **Hero:** none (5 questions).
- **Primary action:** **"Escríbenos por WhatsApp"** (bottom-pinned; needs a support number, an ops dependency).

**Layout:** top app bar "Ayuda"; a single-open accordion with the first question open:
1. "**No me sale el semáforo**": "Entra a Cuenta › Lector y revisa que todo tenga ✓. En Xiaomi, Oppo, Vivo o Huawei, quita las restricciones de batería."
2. "**¿Qué significan los colores?**": "Verde: llega a tu mínimo. Rojo: no llega. Amarillo: está en medio. Gris: no pudimos leer la oferta. El número es lo que te queda por km, ya sin gasolina ni desgaste."
3. "**¿Qué lee Kompara?**": "Solo la oferta que ya ves en tu pantalla. Toma una imagen, la lee en tu teléfono y la borra. No la guarda ni la manda a ningún lado. Nunca toca nada por ti."
4. "**¿Me pueden bloquear Uber o DiDi?**": "Las apps de viajes podrían no permitir herramientas como esta. Kompara solo lee y nunca acepta ni rechaza por ti, pero usarla es tu decisión."
5. "**¿Cuánto cuesta?**": "El semáforo, tu día, tu semana y tu IMSS son gratis. Premium cuesta $79 al mes: tu lugar en tu ciudad, todo tu historial y tus papeles del SAT."

**States:** offline works (local copy); the WhatsApp-missing fallback is the chooser.

**Removed vs today:** 7 answers of 50–110 words, the non-existent "botón de reporte", "Toca el chip para ver el detalle", and "pestaña Lector".

**1-second glance:** five questions and a way to reach a person.

---

## 5. The offer moment (the real product)

### 5.1 Laws of the overlay
1. **It informs; it never acts.** No tap, gesture or global action on the host, ever. This is already enforced by `verifyReadOnlyCapture`.
2. **One second.** Colour + glyph is the decision. The number is the magnitude. No sentences.
3. **Never cover the host's controls.** There is a no-go zone over the bottom 35% of the screen (where accept/decline live) and over the fare region (the existing `avoid()`). The chip and the sheet are clamped out of both.
4. **Never pretend.** A reader failure is grey, not red.
5. **Silent during the trip.** The chip exists only while an offer card is on screen.

### 5.2 Anatomy (collapsed chip)

```
┌───────────────────────────┐
│ ✓  $7.20 /km           K │  row 1: glyph 20dp · hero 32sp Black tabular · unit 14sp SemiBold · K mark 10dp @60%
│    neto · $186/h          │  row 2: 15sp SemiBold ("▼ $96/h" if the other metric is weak)
└───────────────────────────┘
  ~150 × 72dp · radius 16dp · shadow 6dp (floating) · padding 12/10dp · min width 132dp, max 176dp
```

- The colours stay the verdict triple, from one source of truth (delete `overlay/VerdictColors.kt`): verde `#1B8A3A` with white text, amarillo `#F2B705` with `#1A1300` text, rojo `#D32F2F` with white text, and the new `chipNeutral` `#475569` with white text.
- Glyphs, shape-redundant for colour-blind drivers: verde ✓ (`Icons.Filled.CheckCircle`), amarillo ⚠ (`Icons.Filled.Warning`), rojo ✕ (a filled circle-x vector), gris "?" (a vector).
- The emerald brand strip is gone. Brand presence is the 10dp K mark (still on every screenshot), so there's no second colour on the pill.
- The hero is always the metric that **decided** the colour, followed by "neto" in row 2, so a driver reading "$7.20/km" next to Uber's own "$/km" knows ours is what they keep.

### 5.3 States and exact content

| State | When | Colour | Row 1 | Row 2 | Touch |
|---|---|---|---|---|---|
| **Verde** | Deciding metric ≥ green floor; all inputs present | verde | ✓ `$9.40` /km | `neto · $212/h` | drag, long-press |
| **Amarillo** | Between the floors, or green capped by a missing input | amarillo | ⚠ `$7.10` /km | `neto · $164/h` | drag, long-press |
| **Rojo** | Deciding metric < red floor | rojo | ✕ `$4.30` /km | `neto · ▼ $96/h` | drag, long-press |
| **Weak second metric** | The other metric is below its own red floor | (as decided) | (as above) | `neto · ▼ $96/h` (▼ glyph 10dp) | — |
| **Partial: no distance** | Distance missing; the hour metric substitutes (capped at amarillo) | ≤ amarillo | ⚠ `$171` /h | `neto · falta distancia` | drag, long-press |
| **Partial: no time** | Time missing; km decides (capped at amarillo) | ≤ amarillo | ⚠ `$8.10` /km | `neto · falta tiempo` | drag, long-press |
| **Sin leer** | Card detected, fare unreadable or parse failed | gris | ? `Sin leer` | `revisa la oferta` | drag only |
| **inDrive counter-offer** (fast-follow, when inDrive capture lands) | Bid-style offer with an editable price | as decided | as decided | `verde si pides $135+` | drag, long-press |
| **Greeting** | First ride-app foreground of the day, reader healthy | slate, emerald 1dp outline | ✓ `Kompara activo` | — | none (touch passes through); fades after 2s |
| **Paused** (API <30 capture lost; or the silent lane self-disabled) | Ride app in the foreground, capture dead | gris | `Kompara en pausa` | `toca para seguir` | tap → `OcrConsentActivity`; collapses to a 32dp K dot after 5s |
| **Hidden** | No card; or 500ms after the card leaves; kill-switched parser | — | — | — | — |

**Text sizes on the chip:** nothing below 13sp, and text uses each colour's `onBrandColor` (no more 10sp white on yellow).

**TalkBack:** "Semáforo verde. 9 pesos 40 netos por kilómetro, 212 por hora. Mantén presionado para cambiar tu mínimo." / "No pudimos leer esta oferta."

### 5.4 Size, position, motion
- **Default position:** top-end, 12dp from the edge, just below the status bar, clamped outside the fare region and the bottom 35% no-go zone.
- **Drag:** snaps to the nearer edge. The position is saved **per host app**, since Uber and DiDi cards differ.
- **Appear:** ≤150ms after the card is parsed, with a 100ms fade and no bounce.
- **Update** (re-parse, surge change): 120ms colour crossfade. The number swaps without animation.
- **Disappear:** 500ms after the card leaves, as today.
- **De-flicker:** keep `distinctUntilChanged`. Don't re-show the greeting within the same day.

### 5.5 Gestures
- **Drag:** move.
- **Long-press (500ms):** quick sheet (5.7).
- **Tap:** nothing, on purpose. Tap-to-expand was removed for good reasons (`docs/parked/verdict-chip-tap-to-expand.md`), and the "why" now lives in the glance (5.6) and after the fact (Tu día). The one exception is the Paused chip, where tap = resume.

### 5.6 How "why" is explained, without words on the chip
1. **The number vs. your floor:** the driver set "Verde desde $8" in onboarding or in Tu semáforo, so "$7.10" being yellow explains itself.
2. **The ▼ marker** on the second line names the weak metric. For example, "▼ $96/h" means "pays per km, but it's slow". This renders the already-computed `explainKind` as one glyph.
3. **After the fact:** Tu día › Detalle de oferta shows the full sentence (`overlay_explain_*`), the fare, km (pickup included), time, cost and net.

### 5.7 Adjusting thresholds without leaving the ride app (long-press sheet)

```
┌──────────────────────────────────┐
│ Tu mínimo para verde             │
│   [ − ]    $8.00 /km     [ + ]   │   56dp buttons, 28sp value
│ Esta oferta: amarillo            │   live; the chip behind recolours
│                    Más ajustes   Listo │
└──────────────────────────────────┘
```

- **One number:** the deciding metric's green floor, stepped by $0.50/km or $5/h. The red floor moves with it, keeping the driver's gap.
- **Persist** on each tap (debounced 300ms).
- **Placement:** anchored to the chip, opening toward the screen centre, clamped out of the bottom 35% and the fare region. It is **not modal**: touches outside pass to the host.
- **Auto-close** when the offer card leaves, after 8s idle, or on "Listo".
- **"Más ajustes"** opens Kompara › Tu semáforo. This is the only path that leaves the host app, and only by the driver's explicit tap.

### 5.8 Reader-down and degraded states
- **API 30+, accessibility service killed:** the service hosts the overlay, so nothing can be drawn over the host. The only channel is the notification **"Kompara se apagó"** / "Tu teléfono lo cerró. Toca para encenderlo otra vez." (high importance, once until fixed; tap → Accessibility settings). Hoy and Lector turn red.
- **API <30, or the silent lane self-disabled after `MAX_HARD_FAILURES`:** the Paused chip appears in the chip's own spot when a ride app comes to the front, plus a notification with the same title. Tap → consent prompt.
- **Parser kill-switched remotely:** chip hidden. If a ride app is in the foreground, the greeting slot says "Kompara: esta app cambió. Ya lo estamos arreglando." (once per day). Lector shows "Uber: actualizando" in its checklist.
- **Partial reads:** grey chip (never red), and the offer is still logged with "Sin leer" in Tu día, with "¿Se leyó mal? Avísanos".

### 5.9 After the offer: post-trip, end of shift, week, month

| Moment | What happens | Exact copy | Lock-screen version | Tap → |
|---|---|---|---|---|
| **Post-trip** | **Nothing on screen.** The ledger records the outcome silently. | — | — | — |
| **End of shift** (shift closes per the detector; ≥2 trips) | One silent notification, channel "Resúmenes" | **"Turno cerrado: $1,240 netos"** / "5 h 20 min · $232 por hora. Dejaste ir 2 verdes." | "Tu turno está listo" | Tu día |
| **Week close** (Mon 9:00) | One notification, "Resúmenes" | **"Tu semana: $6,480 netos"** / "Le ganaste al 78% de CDMX. Toca para verla y compartirla." (no standing: "62 viajes · $158 por hora. Toca para verla.") | "Tu semana está lista" | Ganancias · Semana (last week) |
| **First week close** | Same as above, plus a server-side trial start (§7) | **"Tu primera semana con Kompara: $6,480 netos"** / "Ve tu lugar en CDMX: tienes 14 días de Premium gratis." | "Tu semana está lista" | Tu lugar |
| **Month close** (day 1, 10:00) | One per month, channel "Resúmenes" | **"IMSS de octubre: en Uber ya te toca"** / "Juntaste $9,100 en Uber. Esa app debe inscribirte al IMSS." Or "IMSS de octubre: en DiDi no llegaste" / "Te faltaron $1,200. Toca para ver cómo vas este mes." | "Tu resumen del mes está listo" | IMSS e impuestos |
| **Trial ending** (3 days before) | Once | **"Tu prueba de Premium termina el jueves"** / "El semáforo y tus ganancias siguen gratis. Toca si quieres seguir con Premium." | same | Paywall |
| **Reader died** | Channel "Estado del lector", high | **"Kompara se apagó"** / "Tu teléfono lo cerró. Toca para encenderlo otra vez." | same | the right fix (Accessibility or consent) |
| **API <30 capture running** | Ongoing, minimal | "Kompara está leyendo tus ofertas" | same | Lector |

Lock-screen versions use `setPublicVersion`, so amounts never show on a locked phone on a windshield mount.

---

## 6. Onboarding

**Target:** 5 steps. That is 6 screens on Xiaomi, Oppo, Vivo, Huawei and Samsung (the battery step) and 5 elsewhere, down from 10 screens. One ask per screen. The simulated aha lands on screen 1 (~5s after install). The reader is live in ~60–90s. The real aha is the first real offer.

The progress chrome is a thin bar only, with no "n/5" counter (it changes with OEM and API). The back chevron is on every step except step 1.

| # | Screen | The single ask | Copy |
|---|---|---|---|
| 1 | **Demo** | "Empezar" | A neutral sample offer card (slate, no marks): "Nueva oferta · $148 · 16 km · 22 min". The **real** chip fades in over its corner 600ms later. Title **"Antes de aceptar, ve si te deja."** Body "Kompara lee la oferta y te pone un semáforo con lo que te queda de verdad, ya sin gasolina ni desgaste." Tonal "Ver otra" cycles verde → amarillo (▼) → rojo. Hint "Mantén presionado el semáforo para cambiar tu mínimo" (optional; the real quick sheet works here). Primary **"Empezar"**. Text "Ya tengo cuenta" (→ Entrar). |
| 2 | **Aviso** (Play prominent disclosure; one screen, no scroll at 360×640) | "Acepto" | Title **"Lo que hace Kompara en tu teléfono"**. Four rows with icons: "**Qué lee:** la oferta que ves en tu app de viajes (tarifa, distancia, tiempo). Toma una imagen de la pantalla, la lee y la borra al momento." · "**Para qué:** solo para darte el semáforo y tus totales." · "**Dónde:** todo pasa en tu teléfono. No guardamos ni mandamos tu pantalla a ningún lado." · "**Ojo:** las apps de viajes podrían no permitir herramientas como esta. Kompara solo lee: nunca acepta ni rechaza por ti. Usarla es tu decisión." Muted: "Para esto usa el servicio de accesibilidad de Android." Primary **"Acepto"**, text "No acepto". (Legal B-038 review; this one disclosure now truthfully covers both capture lanes.) |
| 3 | **Activar** | "Abrir ajustes" | Title **"Enciende Kompara en Accesibilidad"**. Three stylised steps (drawn rows, not OEM screenshots): "1. Toca **Kompara** · 2. Actívalo · 3. Toca **Permitir**". Body "Te llevamos a los ajustes. Al terminar, regresa aquí." Primary **"Abrir ajustes"**. On return, if granted: an 800ms ✓ "¡Listo!", then auto-advance. If not granted: "Todavía no está activado." + text "¿No encuentras Kompara?" opens an OEM hint sheet (Samsung: "Apps instaladas"; Xiaomi: "Apps descargadas"; Motorola/others: "Apps descargadas" or "Servicios instalados"). Text "Ahora no" → step 5, and Hoy will show the off state. |
| 4 | **Batería** (conditional, by `Build.MANUFACTURER`) | "Abrir ajustes" | Title **"Que tu teléfono no apague Kompara"**. Body (per OEM, one instruction only), e.g. Xiaomi: "Los Xiaomi cierran apps para ahorrar batería. Activa **Inicio automático** para Kompara." Samsung: "Pon Kompara en **Sin restricciones** de batería." Primary **"Abrir ajustes"** (deep link to that OEM screen). After returning the label becomes **"Ya quedó"**. Text "Saltar". Secondary OEM steps (pin in recents, etc.) live in Lector › Batería. The inert switch is gone. |
| 4b | **Lectura** (API <30 only) | "Activar" | Title **"Un permiso más para leer tus ofertas"**. Body "En tu versión de Android, Kompara necesita que aceptes **Iniciar grabación**. Te lo volverá a pedir si reinicias el teléfono." Primary **"Activar"** (→ system prompt). Text "Después". |
| 5 | **Tu número** | "Mandar código" | Title **"Guarda tu semáforo con tu WhatsApp"**. Body "Con tu número guardamos tu configuración y tus totales, y te regalamos 14 días de Premium cuando cierres tu primera semana. Sin tarjeta." Field `+52` "55 1234 5678". Muted "Te mandamos un código por WhatsApp. No compartimos tu número." Primary **"Mandar código"**. Inline error (keep): "Revisa tu número: deben ser 10 dígitos, por ejemplo 5512345678." |
| 6 | **Código** | type 6 digits | Title **"Escribe el código"**. Body "Te lo mandamos por WhatsApp al +52 55 1234 5678." Six cells; **auto-submit on the 6th digit**; an "Autocompletar" button if WhatsApp one-tap autofill is configured for the template. Row: "Cambiar número" · "Reenviar en 30 s". On success → **Hoy** (first-run). On a network failure after one retry: "No pudimos conectarnos." + "Reintentar" + text "Seguir sin cuenta por ahora" (the reader keeps working; Cuenta shows "Termina tu cuenta"). |

**City** is inferred from the WhatsApp area code: 55/56 → Valle de México, 81 → Monterrey, 33 → Guadalajara, 222 → Puebla-Tlaxcala, 722 → Toluca, 664 → Tijuana, 477 → León, 442 → Querétaro, 999 → Mérida, 998 → Cancún. It seeds the default thresholds and benchmarks, and Hoy's first-run footer shows "Usamos los valores típicos de CDMX · Cambiar". An unknown area code defaults to Valle de México, and Hoy's first-run primary becomes **"¿En qué ciudad manejas?"**. The name is never asked in onboarding.

**Notification permission** (API 33+) is not asked in onboarding. It is Hoy's first-run primary ("Avísame si se apaga"), asked at the moment the driver is waiting for the first offer: in context, once.

**Decline branch, "Sin el lector"** (from step 2's "No acepto"): title **"Sin el lector no hay semáforo"**; body "Respetamos tu decisión. Sin el lector, Kompara no ve tus ofertas. Aún puedes importar tus resúmenes semanales para ver tus ganancias y tu IMSS." Primary "Leer el aviso otra vez"; text "Seguir sin el lector" → step 5. Hoy then shows the off state, and "Encender" always goes through Aviso first.

**Returning user / session expiry ("Entra con tu WhatsApp"):** step 5 (title "Entra con tu WhatsApp", body "Usa el número con el que creaste tu cuenta.") → step 6 → if the reader isn't on this phone: steps 2–4 → Hoy. Two screens when the reader is already on.

**Where signup sits and why.** Signup comes after the reader is live, for four reasons:
1. The accessibility grant is the biggest drop-off, so it must come while the demo's motivation is fresh.
2. The reader works entirely on-device and needs no account.
3. By step 5 the driver has invested and has a concrete reason to give their number: it saves the setup and carries the trial.
4. The trial being server-granted and card-free is *only possible* with a verified number (anti-abuse). That turns the account from a toll into a benefit.

The account stays effectively required (no "skip" button), with an escape only when the network fails, so a Twilio outage never strands a driver who has already granted the reader.

**Framing the permissions:**
- **Accessibility:** "Enciende Kompara" is the verb drivers know from Uber's "Conectarse", and the system screen is pre-drawn so nothing on it surprises them.
- **Screen capture:** on API 30+ it is covered truthfully by the one disclosure ("toma una imagen… y la borra al momento"), with no second consent and no second dialog. On API <30, one extra framed step precedes the system's "Iniciar grabación" prompt.

**The aha, and getting there faster.** Today the aha is screen 10 and it's a simulator. In this design:
- The simulated aha is screen 1, with the real `VerdictChipUi` and the real quick sheet.
- The real aha is the first offer after the grant. The before-shift greeting ("✓ Kompara activo") gives a micro-aha even before an offer arrives.
- Hoy's first-run then shows a sample chip "así se verá", so the driver knows exactly what to wait for.

**Compared with GigU** (phone → WhatsApp code → name/email/platform):
- *Matched:* WhatsApp OTP (the market's expectation), no card, and no name or email up front (we drop the name entirely and infer the city).
- *Reordered:* GigU asks for identity first; we ask last. Our differentiator is a visible verdict, so we show it before asking for anything.
- *Removed vs GigU:* the "platform pick". The reader detects the apps the driver actually uses, so there's no question to answer.

---

## 7. Monetization and paywall placement

### The line (and where I moved it)

| | **Gratis, always** | **Kompara Premium** ($79 al mes) |
|---|---|---|
| The offer | Reader, chip, quick sheet, semáforo, costs | — |
| Your day and shift | Hoy, Tu día, offer details, shift notification, all personal consejos | — |
| Your week and month | **Current week + the 3 before it** (4 weeks); the current month; days chart; "Cómo ganaste"; **Por app**; **Tus mejores horas**; goal; share card | **Full history**: every older week and month, year totals |
| How you compare | **One sentence:** "Le ganas al 78%…" + Top X% + share (only on real data) | **Tú vs. promedio** per metric; **¿Qué app paga mejor en tu ciudad?**; **Dónde puedes ganar más** |
| Taxes and IMSS | **IMSS progress per app** + month-end notification | **Retenciones** (ISR/IVA est.), year view, **PDF para tu contador** |

**Explicit moves vs today:**
1. **IMSS tracker: Premium → free.** It's the driver's legal entitlement, it's unique in Mexico, and it's a word-of-mouth hook ("la app que te dice si ya te toca IMSS"). The month-end notification already tells free users their status, so the tracker behind a blur was incoherent. The paid part of fiscal is the *papeles* (retenciones + PDF), which is what an accountant or a loan needs.
2. **Free history: 2 → 4 weeks.** Two weeks makes a free month look broken. Four weeks covers a full IMSS month and a "Tu mes" share.
3. **Premium consejos dissolve.** Percentile, commission and cross-platform-average tips become Tu lugar's "Dónde puedes ganar más". The driver's own per-app split ("Uber te paga más por km" from *their own* trips) becomes free under "Por app". Hoy's insight is 100% free, and the "premium tip tease that never fires" disappears.
4. **Verification gates contribution, not viewing.** A paying driver is never locked out of what they paid for. `NEEDS_VERIFICATION` becomes a non-blocking note ("Importa tu resumen semanal para que tu semana cuente en el promedio de CDMX"), whatever the backend needs for data quality.
5. **No standing on sample data.** The free hook shows a percentile only when the city-week has a real population (a threshold N, set by data). Before that, the free hook is the self-benchmark ("+12% por hora contra tu semana pasada").

### Where the lock sits in the new IA
- **Locks appear only on drill-ins and segments, never on a tab root:**
  - Tu lugar (the comparison rows)
  - IMSS e impuestos ("Lo que te retienen" + PDF)
  - weeks or months beyond the free window
- **Hoy and Cuenta have no locks.** The only Premium mention there is the plan pill and one tonal row in Cuenta.

### The tease pattern: masked values, never a blur wall
- The driver's **own** number is always shown. Only the comparison/archive number is masked: "**Tú $158** · Promedio ••• · Top ••% 🔒". The driver sees exactly what they'd get, about *their* data.
- **One lock card per screen** (tonal, not blurred grey), naming the concrete thing: "Ve cómo te comparas en todo y qué app paga mejor en tu ciudad · Prueba 14 días gratis, sin tarjeta".
- **Old weeks** show their label and a masked hero; the stepper still moves, so the driver feels the archive is there.
- **No tease where there's nothing to show** (fixes Inicio's always-on gate).

### Trial framing
- **14 days, server-granted, no card,** tied to the verified WhatsApp number. This is not a Play-billing trial, which would require a payment method and make "sin tarjeta" false. Conversion goes through Play billing at the end. *Validate with Play's payments policy (a free promotional entitlement with no purchase), but this is the only way the "sin tarjeta" promise is honest.*
- **It starts automatically at the first week close**, when Premium finally has something to show (a full week and a standing). It doesn't start at signup, when everything Premium would be empty. The first week-close notification announces it. A driver can also start it early from any lock card ("Probar 14 días gratis").
- **The countdown is calm:** the Cuenta plan pill "Premium · prueba, quedan 9 días"; one notification 3 days before the end; on the last day, a one-time Tu lugar banner "Tu prueba termina mañana". After it ends: "Tu prueba terminó. Lo tuyo sigue gratis: el semáforo, tu día, tu semana y tu IMSS."

### Price surface
- **Single plan, $79/mes** (matches today's Play price), shown as **"≈ $18 a la semana"**, because drivers are paid weekly and think weekly.
- Test $59 / $79 / $99 and a weekly SKU (see §9).
- There is one price surface (the Paywall). Lock cards say "Prueba 14 días gratis", not the price.

### How a free user still feels the app is complete
The free loop closes every moment of the day:
- the offer (chip)
- the shift (Hoy, Tu día, the shift notification)
- the week (net, days, best hours, per-app, one standing sentence, share)
- the month (net, IMSS)

Premium answers a different question, "¿y contra los demás, y para el SAT?", so it never feels like a piece of the free product was ripped out.

---

## 8. Scorecard

Scale 1–10. "Today" is the inventoried build; "Proposal" is this document as specified.

| Criterion | Today | Why (today) | Proposal | Why (proposal) |
|---|---|---|---|---|
| **First-use clarity** | 3 | 10 screens, account before value, a text pitch with an unsourced 30% claim, and the end lands in a simulator saying "ya está leyendo" before it is. | 8 | Demo first, 5–6 screens, one ask each, land on Hoy with a clear "wait for an offer". It's capped by the unavoidable trip into Android Accessibility and OEM battery screens. |
| **Glanceability** | 5 | The chip's size and colour are right, but there's a green strip on red, no glyph, a 10sp hint and missing→red. Inicio and Comparar are number grids. | 9 | Chip: colour + glyph + one hero + "neto", grey when unsure. Every screen has one hero and at most 3 numbers in the first viewport. |
| **IA simplicity** | 3 | 5 tabs with overlaps, the reader as a tab, history in settings, the simulator in 4 places, no back chrome. | 9 | 3 tabs, max depth 2, one week screen for every week, top app bars, settings rows with values. |
| **Copy quality** | 4 | Three verdict vocabularies, jargon (umbral, CSF, Tú/Lugar), 55–110-word paragraphs, forced caps, false promises. | 8 | One vocabulary, sentence case, one-line explainers, honest claims. It still needs a pass with real drivers (e.g., "neto" vs "limpio", see §9). |
| **Consistency** | 4 | The same metric drawn 4 ways, share ≠ preview, week labels differ, two verdict palettes, bottom bar on tasks. | 8 | One number style, one row component, one week selector, one renderer, one palette. Residual risk: the chip lives in `:overlay` and the app in `:ui`, which drift unless the tokens truly sit in one place. |
| **Trust / transparency** | 5 | The privacy stance is strong, but the disclosure omits screen images, standings rest on sample data, "filtre" contradicts read-only, legal placeholders show, and "sin tarjeta" is unbacked. | 9 | One truthful disclosure, grey "Sin leer", no standing without real data, honest basis lines, a real trial, "¿Me pueden bloquear?" answered plainly. The platform-ToS risk itself can only be disclosed, not designed away. |
| **Delight** | 4 | The Tu mes card and the chip; otherwise the screens are utilitarian and dense. | 7 | The before-shift greeting, live chips in Tu semáforo, the shift-close summary, the week bar chart, a share card that matches its preview. Still short of the bar: no motion identity, no sound or haptic signature, and the "aha" depends on the first real offer. |
| **Feels like Uber-class** | 3 | Reads as a feature collection: five tabs, cards in cards, missing chrome, blank loading. | 8 | Status-first home, earnings timeline, trip-card discipline, tucked settings, full-screen tasks. It falls short where we can't control the platform: the permission detour, OEM survival steps, and an overlay that must coexist with someone else's UI. |
| **Total** | **31 / 80** | | **66 / 80** | Below the rubric's 68 pass line on **delight** (7) and on the system-detour ceiling for first use. I'd rather say that than inflate it. |

---

## 9. Open questions (test with real drivers before committing)

1. **Does "glyph + /km neto" beat colour + number alone, and do drivers trust our net figure next to Uber's own gross $/km badge?**
   - *Test:* 10–12 drivers in CDMX, phone on a mount at 60cm. Show 1-second exposures of 20 offer cards with and without Uber's badge, plus colour-blind participants on purpose. Measure correct take/pass vs. their stated floor, and which number they quote back. Also test "neto" vs. "limpio" as the label.
   - *Default:* ship the ✓/⚠/✕ glyph and "neto". Never ship the brand strip back on the chip.
2. **Account before or after the accessibility grant?**
   - *Test:* closed-beta A/B. The primary metric is "reader active at 24 h"; secondaries are "account completed at 24 h" and "week-1 retention". Watch the existing local funnel counters (`OnboardingStep`).
   - *Default:* after (steps 5–6), with the trial as the reason to give the number.
3. **Three tabs, or four with "Tu lugar" as its own tab?** (This also tests the locked "Inicio = individual / Comparar = comparison" principle.)
   - *Test:* tree test plus first-click on "¿Dónde ves si ganas más que otros conductores de tu ciudad?" and "¿Dónde ves si ya te toca IMSS?". Compare findability and trial-start rate between the arms.
   - *Default:* 3 tabs, with Tu lugar one tap from the week. Switch to 4 only if findability is below 70% or trial starts drop.
4. **Is the new paywall line right, and is $79/mes the price?** (IMSS free, 4 free weeks, the trial auto-starting at the first week close, weekly framing.)
   - *Test:* a three-cell price test on the Paywall ($59 / $79 / $99 monthly) and a weekly SKU ($19/semana) vs. a monthly one shown as "≈ $18 a la semana". Also run qualitative interviews on whether a free IMSS tracker raises or lowers willingness to pay for the PDF.
   - *Default:* $79/mes with the weekly translation, 14-day no-card trial at the first week close, IMSS free.
5. **Should thresholds be adjustable over the ride app at all?**
   - *Test:* on-device counters (never transmitted without consent) for quick-sheet opens during a live offer vs. between offers, plus 5 ride-alongs.
   - *Default:* keep the ± stepper sheet (auto-close, out of the no-go zone). If mid-offer use is near zero, replace it with an after-shift suggestion in the shift notification ("¿Subimos tu mínimo a $8.50? Hoy tomaste 4 amarillos que te dejaron poco") and remove the overlay sheet entirely. That would delete one more surface over someone else's app.
