# Competitive Analysis — Realtime Trip-Offer Readers for Ride-Hailing Drivers

> **Date:** 2026-06-10
> **Scope:** Ruta Rentable (LATAM/MX), StopClub (Brazil), GigU (US) — plus Uber's native response and the Mexican regulatory context.
> **Purpose:** Ground the Kompara Android rebuild: replicate proven best practices, differentiate where rivals are weak, and de-risk legally.

---

## 1. Market map

All three competitors converge on the same core mechanic: **read the trip-offer card off the driver's screen via Android accessibility, compute per-trip economics, and overlay an instant verdict before the driver accepts.** All are Android-only (iOS exposes no equivalent accessibility API). All monetize via low-priced monthly subscriptions with free trials. All claim ~+30% driver earnings.

| | Ruta Rentable | StopClub | GigU |
|---|---|---|---|
| **Home market** | LATAM es (AR, CL, PE, **MX**, CO) | Brazil (+ Portugal 2024) | US (Brazilian-origin team) |
| **Platforms read** | Uber, DiDi, inDrive, Cabify, Pronto | Uber, 99 | Uber, Lyft, DoorDash, GrubHub |
| **Scale claim** | 100k+ drivers | 700k+ drivers, 1,000+ cities | 1M+ downloads |
| **Realtime verdict** | "Semáforo" (green/regular/red) | R$/km + R$/min + auto-decline | "Cherry Picker" (green/yellow/red) |
| **Pricing** | Subscription, undisclosed; 3-day free trial, no card | R$9.90 / 19.90 / 39.90 per month; 5-day trial (card required); free tier exists | $6.90–6.95 USD/mo; 15–30 day free trial, no card |
| **Play Store** | Yes (`com.wsb.dalehelperdriver`), ~4.5★ claim | Yes (`br.com.stopclub.app`) | Yes (`co.gigu.app`) |
| **Beyond the reader** | Expense log, zone stats, secret camera, vehicle locator | Safety suite (radar, live stream, walkie-talkie), physical rest bases (Rio), partner discounts, courses, community/advocacy | Net Profit Calculator (real costs → net per trip/mi/hr), secret camera |

**Direct competitor in Mexico today: Ruta Rentable only.** StopClub has not entered Mexico (it expanded to Portugal instead); GigU is US-focused. The window for a Mexico-first, deeper product is open.

## 2. Per-competitor notes

### Ruta Rentable (the one to beat in MX)
- Positions as a "profitability copilot": checks trip value, distance **to the passenger**, estimated time, against the driver's $/km and $/hr targets; alerts when an offer is below target.
- Onboarding is "activate monitoring → drive normally → review stats" — capture runs in background, claims light battery use.
- Privacy marketing: "your data never leaves your phone."
- Has a creator/affiliate program ("Ruta Rentable Partners") paying driver-influencers — cheap, targeted acquisition.
- Weaknesses to exploit: no percentile benchmarks vs other drivers, no cross-platform earnings comparison, no fiscal/IMSS features, pricing opaque, no community/advocacy identity.

### StopClub (the playbook)
- The most complete product and brand: realtime economics + **auto-decline below threshold** (the legally contested feature), cost-aware net earnings, performance comparison vs regional drivers, large safety suite, physical driver bases, courses, discounts.
- Built a movement, not just an app: 60k+ signature petition against Uber's lawsuit, driver associations joining the case — the litigation became marketing.
- Free tier + cheap paid tiers anchored on tangible perks (bases, workshop labor).
- Lesson: **safety + community features create retention and word-of-mouth** beyond the utilitarian reader.

### GigU (the monetization/US analog)
- "Cherry Picker" grades every offer in ~1s (per-mile, per-hour, per-minute). Key differentiator vs Uber's own upfront info: **Net Profit Calculator** — driver inputs real costs (fuel, insurance, maintenance, registration) and the overlay shows *actual profit* per trip/mile/hour.
- Marketing leans on: no platform login → "they can't detect or ban you"; data stays yours; 30-day risk-free trial; $6.90/mo.
- Explicitly Android-only, citing iOS restrictions; "exploring iOS options" (i.e., none).

## 3. Uber's native counter-move (the strategic threat)

Uber is piloting an **estimated earnings-per-km badge on trip requests** — including Trip Radar and exclusive offers — in selected cities of **Mexico**, Chile, and Argentina (pilot announced April 2025; blog updated May 2026). A parallel pilot shows estimated earnings per **active hour** in Argentina. The badge = upfront fare ÷ total estimated distance (pickup leg included), explicitly **gross**, explicitly "not a guarantee." Uber shipped the same thing in Brazil during the StopClub fight.

**Implications for Kompara:**
1. Gross $/km is becoming table stakes on Uber — our overlay must lead with **net** profit (after the driver's real cost profile) and **configurable verdicts**, which Uber will never show.
2. DiDi and inDrive offer no such transparency — multi-app coverage stays differentiating.
3. The deeper moat is everything around the reader: percentiles vs city, cross-platform comparison, fiscal/IMSS tooling, history. Uber can copy a badge; it won't build driver-side analytics against itself.
4. Existential risk (Para/Lyft precedent, US 2021): platforms can move decision data server-side or strip it from the offer card. Hedge: product must remain valuable even if pre-acceptance capture dies (post-trip stats, weekly analytics, fiscal features).

## 4. Legal analysis (gating question for the rebuild)

**Question asked:** does StopClub's legal success depend on "where things sit" (jurisdiction or architecture) in a way that should change our greenfield plan?

**Finding: No jurisdictional dependency. Strong architectural dependency.**

The record:
- **Late 2023** — Uber sued StopClub in São Paulo claiming IP violation, unfair competition, and "improper information sharing" via capture of the Uber app's interface; obtained a lower-court suspension of features with a R$50k/day fine, and suggested drivers uninstall the app.
- **April 2024 — TJ/SP (1st Reserved Chamber of Corporate Law) reversed**: no demonstrated violation of industrial/intellectual property, no unfair competition. The court emphasized the app **"does not collect personal data or interfere with Uber's app"** and operates in a distinct segment. StopClub kept operating; the R$5M penalty threat was suspended; merits still pending.
- **January 2025 — CADE** (Brazil's antitrust authority) opened an inquiry into whether Uber's restrictions on driver-earnings tools abuse dominance; after an initial closure, **CADE reopened the case on appeal in early January 2026**. Regulatory pressure currently constrains Uber retaliation in Brazil.
- StopClub meanwhile expanded internationally (Portugal) and stayed on Google Play throughout — the model survives both courts and Play policy.

**What actually won the argument** (and what we must replicate architecturally):
1. Reads only what is already displayed on the **driver's own screen, with the driver's explicit consent** — no scraping of Uber servers, no credential use, no API abuse.
2. **No personal data collection** from the host app; processing happens on-device.
3. **No interference** with the host app's operation (display-only overlay; note: auto-decline — StopClub's contested feature — interacts with the host app and drew the strongest fire; Kompara v1 should stay read-only).
4. Driver consent + transparency framing ("right to choose") aligned drivers, associations, and regulators against the platform.

None of this depends on Brazil-specific law; the same defensive posture maps to Mexico (COFECE is the CADE analog, and the 2025 platform-work reform makes driver-transparency tools politically sympathetic). No Mexican litigation against Ruta Rentable was found; it operates openly.

**Conclusion: proceed full greenfield.** Encode the legal posture as architecture: capture, parsing, and verdicts stay **on-device**; the backend receives only consented, derived aggregates; v1 is read-only (no auto-accept/decline); ship an in-app disclosure that using third-party tools may conflict with platform ToS. This simultaneously satisfies Google Play's accessibility-API policy and the litigation-tested defense.

## 5. Mexican regulatory context (opportunity)

- The **platform-work labor reform** took effect **June 22, 2025**; IMSS pilot rules for platform workers effective **July 1, 2025**. Drivers generating ≥ 1 monthly minimum wage (~MXN $8,364) per platform get social-security coverage; fiscal regime (LISR platform withholding) unchanged for now, with open questions (CFDI de nómina, PTU).
- **Feature opportunity no competitor has:** track per-platform monthly net income against the IMSS threshold, explain coverage status, and generate fiscal summaries. This slots directly into our existing fiscal epic and is uniquely Mexican.

## 6. Best practices to replicate (and what to skip)

**Replicate:**
1. Traffic-light verdict overlay (universal pattern — zero-cognition while driving).
2. Configurable $/km + $/hr thresholds, per platform.
3. **Net-profit framing** with a real cost profile (GigU) — our wedge vs Uber's gross badge.
4. Include the pickup leg in distance math (Uber's own badge does; drivers think this way).
5. Free trial without card (Ruta Rentable/GigU style) → cheap monthly sub. Anchor: GigU $6.90 USD ≈ $115 MXN; StopClub R$9.90 ≈ $37 MXN. Our planned $59–$99 MXN tiers sit comfortably in-range.
6. "Your data never leaves your phone" privacy positioning (marketing = legal posture).
7. Offer simulator + video walkthrough for the scary accessibility-permission onboarding.
8. Secret-camera/safety feature (all three have it) — strong retention + word-of-mouth; cheap to build later.
9. Driver-creator affiliate program (Ruta Rentable Partners model).
10. Community/advocacy identity (StopClub) — "the app on the driver's side."

**Skip for now:**
- Auto-decline (StopClub's contested feature) — legal risk class jump, Play declaration complexity.
- Physical bases (StopClub) — capital-intensive; revisit as partnerships much later.
- iOS — structurally impossible for the reader; revisit only for companion features.

## 7. Sources

- [TJ/SP valida StopClub (Migalhas)](https://www.migalhas.com.br/quentes/404804/tj-sp-valida-stopclub-app-que-calcula-ganhos-em-corridas-da-uber)
- [Disputa Uber × StopClub (Tecnoblog)](https://tecnoblog.net/noticias/entenda-a-disputa-entre-uber-e-stopclub-nos-tribunais/)
- [CADE apura práticas da Uber (Mercado&Consumo)](https://mercadoeconsumo.com.br/16/01/2025/economia/cade-vai-apurar-praticas-da-uber-que-restringem-ferramentas-que-calculam-ganhos-de-motoristas/)
- [CADE desarquiva caso contra Uber (PlatôBR)](https://platobr.com.br/cade-desarquiva-caso-e-retoma-analise-de-processo-contra-uber)
- [StopClub chega a Portugal (Observador)](https://observador.pt/especiais/stopclub-empresa-brasileira-que-enfrenta-uber-em-tribunal-chega-a-portugal-para-ajudar-motoristas-a-perceber-que-viagens-valem-a-pena/)
- [StopClub no Brasil — Rest of World](https://restofworld.org/2023/stopclub-app-uber-driver-cost-breakdown/)
- [StopClub site oficial](https://site.stopclub.com.br/)
- [Petición de apoyo a StopClub (Change.org)](https://www.change.org/p/apoio-ao-stopclub-e-motoristas-em-rep%C3%BAdio-ao-processo-da-uber)
- [Ruta Rentable](https://rutarentable.app/) · [Play listing](https://play.google.com/store/apps/details?id=com.wsb.dalehelperdriver)
- [GigU](https://www.gigu.app/) · [Play listing](https://play.google.com/store/apps/details?id=co.gigu.app) · [Cherry Picker review (The Rideshare Guy)](https://therideshareguy.com/say-hello-to-gigu-and-its-new-cherry-picking-tool/) · [Net Profit Calculator (PRWeb)](https://www.prweb.com/releases/gigu-integrates-net-profit-calculator-into-its-app-giving-gig-drivers-real-time-visibility-into-what-they-actually-earn-302694309.html)
- [Uber pilot: ganancias estimadas por km (Uber Blog)](https://www.uber.com/es-US/blog/piloting-estimated-earnings-per-kilometer-on-trip-requests/) · [por hora activa (AR)](https://www.uber.com/es-AR/blog/prueba-piloto-de-la-funcion-que-muestra-las-ganancias-estimadas-por-hora-activa-en-las-solicitudes-de-viaje/)
- [Reforma laboral plataformas — entrada en vigor (KPMG MX)](https://kpmg.com/mx/es/tendencias/2025/07/flash-reforma-de-trabajo-en-plataformas-digitales.html) · [Reglas IMSS (Eje Central)](https://www.ejecentral.com.mx/nuestro-eje/entran-en-vigor-las-nuevas-reglas-del-imss-para-conductores-y-repartidores-de-plataformas-digitales) · [Tratamiento fiscal (IDC)](https://idconline.mx/fiscal-contable/2025/06/27/cambia-el-tratamiento-fiscal-de-trabajadores-de-plataformas-digitales)

**Unverified items** (flagged for follow-up): exact Ruta Rentable subscription price in MXN; current StopClub Play install count; final merits ruling status of Uber v StopClub (still pending as of last coverage); GigU's corporate relationship to StopClub (both Brazilian-origin, similar feature sets — coincidence not confirmed).
