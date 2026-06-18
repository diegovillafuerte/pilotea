# Earnings import strategy — documents vs. credentials, UX, backend & legal

> **Date:** 2026-06-18 · **Status:** Decision locked (research-grade; legal items need counsel sign-off via [B-038](../pming/tasks/pending/B-038.md))
> **Scope:** How a driver gets their Uber/DiDi weekly earnings *into* Kompara as painlessly as possible, where that data lives, and the legal envelope around it. Feeds the Comparar redesign ([comparar-redesign-spec.md](comparar-redesign-spec.md)) — without imported data there is nothing to compare.
> **Decision basis:** web-verified legal research (Uber/DiDi MX terms, LFPDPPP 2025, Código Penal Federal art. 211 bis, Google Play policy, account-linking aggregators), each finding adversarially fact-checked; full source list in §9.

---

## 0. TL;DR

1. **Ship Option A (driver uploads their own documents). Reject Option B (credential sharing) outright.** Option B violates Uber's and DiDi's terms on multiple independent grounds, plausibly triggers Mexican computer-misuse criminal law (art. 211 bis), is high-removal-risk on Google Play, and **destroys the StopClub-modeled legal defense that is the entire product's moat**. Option A is already built end-to-end.
2. **The real work is UX, not architecture.** Make Option A frictionless: a **guided per-platform wizard** that validates each shot the instant it's picked, **annotated examples** of the exact screen to capture, and an **Android share-target** so the driver shares a screenshot/PDF straight from Uber/DiDi into Kompara with the file pre-picked.
3. **Surface it in Comparar.** The empty/locked Comparar state's primary CTA becomes **"Traer mis ganancias"** → the import flow, returning to Comparar with data. Today import is only reachable from History, and not at all until the login UI ships (TD-017/TD-008).
4. **Fix two backend issues before the reader-first gap-fill layer can work:** the captured-vs-imported reconciliation is currently **contradictory** (Android prefers imported, backend enforces the opposite), and partial imports can **clobber real totals to 0**.
5. **The privacy story has to change regardless.** Imports already send the driver's earnings *documents* (which contain name/phone/email — see the sample PDF) off-device to R2 + Anthropic. The current in-app copy says data "never leaves the phone." That is true for the reader, **false for imports**, and there is no privacy notice yet.
6. **One credible future upgrade only:** a Mexico-native aggregator (**Palenca**) for optional auto-import — never DIY credential scraping. Evaluate, don't assume; it shifts but does not erase the ToS/ban risk.

---

## 0.5 Review revisions (codex gpt-5.5 + independent review — 2026-06-18)

Both reviewers cross-checked against the code. **Where this section conflicts with text below, this governs.**

- **Reconciliation (§6.1) was under-specified — replace with a canonical merge model.** Verified facts: the server `weekly_aggregates` is **one row** keyed `(driver, platform, week)` (source *not* in the key → last-writer-wins); Android Room is **two rows** keyed `(platform, week, source)`; there is **no server→client downsync**; and `POST /v1/aggregates` (the captured sync) has **no source guard** — it blind-upserts and already clobbers imported rows. The "captured beats imported" invariant only holds on the *import* path. Correct model:
  1. **Preserve true nulls end-to-end** — make the nullable metric columns actually nullable in Room + serialization, stop the `?? "0"` coercion in `aggregateValuesFrom`, and have parsers emit `null` (not `0`) for "not reported" (Uber km/hours). Without this, coalesce can't tell "Uber genuinely has no km" from a real 0.
  2. **Merge present-fields-only, then RECOMPUTE derived ratios** (per-km/-hour/-trip) from the merged raw values — never carry a ratio independent of its numerator/denominator (stored ratios feed `population_stats`).
  3. **Provenance:** mark a merged row `source='mixed'` (or per-field provenance); a mixed row is honestly neither 'imported' nor 'captured'.
  4. **Do not create** a `weekly_aggregates` row from a commission-only import unless an existing row can absorb it.
  5. **Fix BOTH** `imports.ts` AND `aggregates.ts` (give the captured sync the same guard).
  6. Pick ONE canonical layer: two-row + client read-time field-merge (Android already row-picks) **or** one-row + per-field provenance. No hand-waving.
- **Benchmark integrity is via aggregate filtering, not the account boolean.** Feed `population_stats` only with verified + sufficiently-complete + non-outlier weekly rows. (A borrowed/fake import that "verifies" an account must still never pollute benchmarks.)
- **Dry-run must NOT persist the original (was TD-018 "phase 2" → promote to now, BLOCKER).** `?dry_run=true` currently writes the file to R2 *before* the dry-run branch → orphaned PII. Parse from the in-memory multipart bytes; never store on dry-run. Show a prominent upload consent **before** the first preview.
- **DiDi per-slot validation is infeasible** (the parser needs both images). Validate **after both** DiDi shots are present; keep per-slot labels/examples for guidance, but the dry-run runs once on the pair.
- **Share-target hardening:** copy shared bytes into app cache **immediately** (persistable URI grants aren't guaranteed for all `ACTION_SEND` sources); preselect platform/upload_type from MIME + source package + filename with a one-tap confirm; handle multi-file, wrong order, mixed MIME, offline, and Claude latency.
- **Privacy is a launch BLOCKER, not copy work, sequenced BEFORE import-based verification:** aviso + in-app upload consent + Data Safety update + Anthropic DPA / sub-processor disclosure (Anthropic API inputs are Customer Content under its DPA) + `parsed_payload` minimization (it stores all ~23 fields incl. PII on success **and** failure — §5's "good minimization" line is **wrong**, correct it) + R2 lifecycle/delete rules.
- **DiDi commission (§3):** the driver's OWN commission is not reliably readable from the 2 standard screenshots → keep "—" and **don't nag** (the UX was right; soften §3's "overly pessimistic" framing). The 16% MX figure is a **benchmark/fiscal constant only**, shown with provenance, never injected as the driver's measured value.
- **Sequencing (replaces §8 order):** (1) privacy + dry-run no-store + `parsed_payload` minimization + R2 lifecycle; (2) canonical merge + null preservation + `aggregates.ts` guard; (3) verification (derived, scoped — see account doc); (4) Android gate shipped **inert** behind the paywall kill-switch until (5) the import wizard + Comparar entry are live and reachable. **Never enable the verification term in prod before the wizard exists.**

---

## 1. The two options and the verdict

| | **Option A — driver uploads documents** | **Option B — driver shares Uber/DiDi login** |
|---|---|---|
| Mechanism | Driver downloads their own Uber weekly PDF / screenshots their own DiDi screens → uploads → Claude Vision parses | Driver hands Kompara their platform password → Kompara logs in / scrapes / hits undocumented endpoints |
| Build status | **Already built** (Android `ImportScreen` + backend `POST /v1/imports` + parsers) | Not built |
| Uber terms | Compliant — uses Uber's own self-service channels | **Violates** account-sharing ban + API anti-credential/anti-scraping clauses |
| DiDi terms | Compliant | **Violates** §1.3 (account non-transfer), §13.2(5) (automated scripts), §13.3(5) (unauthorized access) |
| MX criminal law (CPF 211 bis) | Not implicated (driver is lawful holder of their own document) | **Plausible exposure** — accessing a security-protected system "sin autorización"; account-holder consent likely not a complete defense |
| LFPDPPP 2025 | Ordinary `responsable` duties (consent, aviso, security, ARCO, breach) | All of those **plus** storing a third-party credential = aggravated breach risk + "medios engañosos" exposure |
| Google Play | Fine | **Removal risk** under Malware + Device-and-Network-Abuse policies |
| Legal-defense impact | Preserves the StopClub posture | **Forfeits it** — converts "reads my own screen" into "unauthorized server access" |
| Reliability | Driver-paced; PDF is rich, screenshots OCR fine | Breaks on 2FA/bot-detection; gets driver accounts flagged/banned |

**Verdict: Option A is the only defensible path. Option B is a strategic non-starter — do not build it, in-house, ever.**

The clinching point is strategic, not just compliance: Kompara's whole legal defense (and its differentiator vs. Uber's own badge) is the StopClub model — *"reads only what is displayed on the driver's own screen, with consent — no scraping, no credential use, no API abuse"* ([competitive-analysis.md §4](competitive-analysis.md)). Competitor GigU even markets *"no platform login → they can't detect or ban you."* The moment Kompara holds Uber/DiDi logins, that defense dies and the marketing line inverts: **login is the trigger for both ban risk and criminal exposure.**

---

## 2. Legal implications (the part counsel must ratify — B-038)

### 2.1 Why Option B fails — verified grounds

**Uber.** The Mexico Community Guidelines explicitly prohibit account sharing (*"No está permitido compartir cuentas… registrarse y mantener una cuenta activa"*), and the Uber API Terms forbid *"making credentials available to others,"* *"misrepresent[ing] or mask[ing] your identity,"* and *"parsing or scraping any of Uber's data… except as explicitly permitted by Uber in writing"* including *"web spiders, crawlers, robots… bots."* (Both quotes high-confidence/verbatim. The MX *general* terms page renders only §1 over fetch, so cite the Community Guidelines + API Terms as the load-bearing sources, not the general ToU.)

**DiDi.** "Términos y Condiciones de Uso de DiDi Conductor" (MX), verified verbatim:
- §1.3 — *"El Socio no cederá o transferirá de ninguna forma la cuenta vinculada a su nombre… a ninguna persona natural o jurídica."* (Handing credentials to Kompara is ceding the account.)
- §13.2(5) — bans *"un programa o script automatizado… que pueda hacer múltiples solicitudes a servidores."*
- §13.3(5) — bans *"intentar obtener acceso no autorizado al sitio web de DiDi, sus aplicaciones, los Servicios de DiDi… o sistemas o redes relacionados."*

Lead with these three. (§13.2(4)'s reverse-engineering clause is scoped to building a "competitive product" — a weaker hook for a pure import tool; keep it secondary.)

**Mexican criminal law — Código Penal Federal art. 211 bis 1-7.** Criminalizes accessing/copying information in computer systems *"protegidos por algún mecanismo de seguridad… sin autorización"* (verbatim confirmed), with penalties raised when the information is used *"en provecho propio o ajeno"* (211 bis 7). A password-protected Uber/DiDi backend is exactly such a system. **Whether the account-holder's consent immunizes a third party is genuinely unsettled — no Mexican precedent resolves it.** The conservative reading (the protected interest is the *system owner's* authorization, which Uber/DiDi affirmatively withhold via their ToS) is that consent is *not* a complete defense. Present this as a *plausible conservative reading creating real, unresolved exposure* — not as settled doctrine.

**Google Play.** Self-built Option B is exposed under two policies (both verified verbatim):
- *Malware* — a stated malware objective is to *"transmit personal data or credentials off the device without adequate disclosure and consent,"* plus the phishing pattern (capture credentials, send to a third party).
- *Device and Network Abuse* — bans *"apps that access or use a service or API in a manner that violates its terms of service."* Since Option B induces a ToS breach, it independently violates this.

### 2.2 Option A is not free of obligations — LFPDPPP 2025 compliance

Mexico replaced the 2010 law: the **new LFPDPPP** was published in the DOF **20 Mar 2025** (in force 21 Mar 2025; minor homologation reform 14 Nov 2025). **INAI was dissolved**; enforcement now sits with the **Secretaría Anticorrupción y Buen Gobierno** (an Executive dependency, no constitutional autonomy). Fines run **100–320,000 UMA** (2026 UMA = MXN 117.31 → ceiling ≈ **MXN 37.5M**).

As a `responsable`, Kompara must, **even under Option A**:
- **Express consent** for financial/patrimonial data (it is *not* classified "sensible," so no special-category regime — but it *is* express-consent data). *Caveat: sources disagree on the exact article (7 vs 8) and whether "por escrito" attaches to financial data — verify the precise text before drafting the consent flow.*
- Publish an **aviso de privacidad** (Arts. 15-16: identity, data processed, purposes, ARCO mechanisms; simplified notice + link for electronic collection).
- **Security measures** (Art. 18) and **immediate breach notification** (Art. 19, *"de forma inmediata"*).
- Honor **ARCO** rights (acceso, rectificación, cancelación, oposición; 20-day window).
- Not obtain data via *"medios engañosos o fraudulentos"* (Art. 6).

### 2.3 The on-device claim is now false for imports — fix the copy

Current in-app copy ([strings.xml:111](../android/ui/src/main/res/values/strings.xml:111)): *"todo el cálculo ocurre en tu teléfono… no se sube a internet."* True for the **reader**. **False for imports** — uploads send the original file to **Cloudflare R2** and the bytes to **Anthropic's Claude Vision API** (a sub-processor / remote transfer under LFPDPPP). And there is **no aviso de privacidad yet** (only the placeholder string `paywall_legal_placeholder` and the open counsel task B-038).

Required:
- The aviso must distinguish **reader (on-device)** from **import (off-device: R2 + Anthropic)** and name Anthropic as a processor (*remisión*, not third-party *transferencia*, if framed as processing-on-our-behalf).
- **Data minimization finding:** the sample Uber PDF contains the driver's **name, phone, and email** (page 3) plus a full per-trip ledger — i.e. uploaded PDFs carry PII well beyond earnings. Today the entire raw Claude extraction is retained in `imports.parsed_payload` (JSONB) and the original sits in R2. Recommend: scrub/limit `parsed_payload` to the fields actually used, configure the **R2 retention/lifecycle** (TD-012 — the 90-day rule is assumed but unconfirmed), and delete the original after a successful parse (or short TTL). Also fix **TD-018** (dry-run still stores the original → R2 orphans).

### 2.4 StopClub precedent — calibrate the claim

The TJ-SP decision (1ª Câmara Reservada de Direito Empresarial, **April 2024**, unanimous) that Kompara leans on was an **interlocutory ruling revoking Uber's injunction — the merits are still pending.** Cite it as a *favorable interim/probability ruling*, not a settled win. (This slightly corrects the "reversed / kept operating" framing in [competitive-analysis.md §4](competitive-analysis.md), which reads as more final than it is.)

---

## 3. Data reality per platform (corrects an assumption in the data model)

The "Uber omits km, DiDi omits commission" shorthand is **half wrong**:

| | **Uber** | **DiDi** |
|---|---|---|
| Official downloadable artifact | **Weekly PDF/CSV** at drivers.uber.com (also emailed every Monday). Privacy "Download your data" export **excludes** weekly pay statements, so the PDF is the authoritative source. | **None.** No emailed/downloadable earnings statement. CFDI invoices are tax facturas, not statements. → screenshots only. |
| Distance (km) | **Never reported.** | Not aggregatable directly, **but** the Tablero shows per-km / per-hour averages that OCR can capture. |
| Commission % | **Hidden** — only in the in-app fare-breakdown donut ("Desglose de tarifa") or the weekly statement's service-fee line. | **Exposed** — published 16% MX city-level *tarifa de servicio* + a per-trip *"Desglose del recibo"* commission line. |
| Rendering | Compose/Texture (offer card unreadable by a11y → OCR) | SurfaceView (no a11y text → screenshots/OCR only) |

**Implication for the model:** the Comparar spec's rule "DiDi `platform_commission_pct` → '—' (DiDi no desglosa)" is **overly pessimistic** — DiDi *does* expose commission (knowable as a 16%-MX benchmark constant, or OCR'd). The genuine DiDi gap is aggregatable km, not commission. Re-examine the parser prompts, the `weekly_aggregates`/`population_stats` N/A handling, and the Comparar table accordingly. (Today [didi-screenshot.ts](../src/lib/parsers/didi-screenshot.ts) hard-nulls `platform_commission_pct`.)

---

## 4. The easiest-upload UX (recommended design)

Three independent designs were generated and judged. **Recommendation = the Guided Wizard as the reliable floor + Android share-target as the fast ceiling + reader-first gap-fill as a phase-2 layer.** Auto-classification (`upload_type=auto`) is **deferred** — the backend only accepts `{pdf, screenshot}`, and a silent misclassification that still parses is the worst outcome for a numbers product.

### 4.1 Uber flow

1. **Entry from Comparar.** New driver → EmptyState CTA **"Traer mis ganancias"** → `IMPORT_ROUTE?source=comparar`. Reader-active driver (phase 2) → inline **"completar"** chip on the blank Take-rate row: *"Falta tu comisión Uber — 1 captura"* (deep-seeds `&platform=uber_screenshot`).
2. **Fast path (share-target).** Inside Uber Driver (Ganancias / donut) or with the PDF open in Gmail → OS **Share** → **"Kompara — Importar ganancias"** → Kompara receives `ACTION_SEND`, reads `EXTRA_STREAM`, lands in the wizard with the file **pre-picked** (no SAF round-trip).
3. **Wizard path (default, low-tech-proof).** Tap the **Uber** card → fork: **"Tengo el PDF semanal (recomendado, ~95%)"** dominant vs. a quiet **"Solo tengo capturas (~40%)"** secondary link.
4. **Step 1 (PDF).** Annotated example of the Uber "Resumen semanal" PDF + one-line instruction + a **"Mostrarme"** button (`getLaunchIntentForPackage("com.ubercab.driver")`) to bounce to the source and back. CTA "Elegir PDF" (SAF, `application/pdf`) — or it's pre-filled if shared.
5. **Validate-before-accept.** The instant the file is picked, run the existing **dry-run** (`repository.preview`, `dry_run=true`): inline-confirm *"Detectamos: semana del 2 jun, $3,200 neto, 142 viajes"* or reject with the backend's verbatim 422 Spanish string and let them re-pick **without losing progress**. A bad/wrong-screen file never reaches Review.
6. **Step 2 (commission bolt-on, optional/nudged).** *"El PDF no trae tu comisión Uber. Súmala con 1 captura del dona de tarifa."* Skippable. (Until a PDF+screenshot merge exists, ship this as a **separate** `upload_type=screenshot` import against the same week — do not add a second part to the `pdf` multipart.)
7. **Step 3 (Review).** Completeness reframed as a recoverable goal: *"95% completo — te faltó comisión"* + one-tap "Mejorar". Tap **Guardar** (`confirm()`).
8. **Return to Comparar** (because `source=comparar`), now rendering the hero/table with real data.

### 4.2 DiDi flow

1. **Entry from Comparar** — same CTA / banner → `IMPORT_ROUTE?source=comparar&platform=didi`. DiDi **never nags for commission** (Take-rate row policy below).
2. **Fast path** — driver multi-selects **both** screenshots in Gallery and shares once → `ACTION_SEND_MULTIPLE` → both slots pre-filled.
3. **Wizard path** — tap **DiDi** card → straight into a **"1 de 2"** checklist (reuse today's two-slot picker). Step 1: annotated **"Ganancias"** example; Step 2: annotated **"Tablero de ganancias"** example. Each pick runs a partial dry-run; a wrong screen ("Parece el Tablero, no la pantalla de Ganancias") keeps the slot empty. Won't advance until both validated.
4. **Review** — *"85% completo"* with the ceiling explained (*"es lo máximo que se puede leer"*) so the number doesn't read as the driver's fault. **Guardar** → return to Comparar.

### 4.3 Comparar entry points (the "reachable from Comparar" requirement)

| Priority | Surface | Action | Status |
|---|---|---|---|
| **Primary** | EmptyState CTA (new/empty driver) | "Traer mis ganancias" → `IMPORT_ROUTE?source=comparar` | **v1** — literal satisfaction of the requirement |
| Secondary | Inline "completar" chip on the Uber Take-rate row | → import pre-seeded `uber_screenshot` | **Phase 2** (blocked on §6 fixes + paywall-gating decision) |
| Tertiary | Low-frequency truth-up banner | Uber "Sube tu PDF para cerrar el mes" / DiDi "Confirma tus cifras" | Phase 2 |
| Global | History "Importar semana" (existing) + the new share-target | catch-all + no-tab entry from inside Uber/DiDi/Gmail | v1 |

### 4.4 Build list (ordered by impact / effort)

1. **Deep-seed platform** into `IMPORT_ROUTE` (`?platform=uber_pdf|uber_screenshot|didi`) → call `ImportViewModel.selectPlatform()` on entry, landing straight on the file-pick step. Pure reuse of `ImportUiState.Picking`.
2. **`source` nav arg** (`comparar|history`) so Save/close pops back to the originating tab (mirror the existing `paywallRoute(surface)` arg pattern).
3. **Wire Comparar entry** — EmptyState `onCtaClick` → `navigate(IMPORT_ROUTE?source=comparar)`; new string "Traer mis ganancias".
4. **Per-platform wizard** inside `ImportUiState.Picking` (per-slot validated files + step index + Uber PDF/screenshot fork). Enum untouched.
5. **Validate-before-accept** — dry-run on each pick; surface the verbatim 422 inline; cache last good preview so Review doesn't re-upload. *(Highest-value friction reducer.)*
6. **Inbound share-target** — exported, translucent `ShareImportActivity` with `ACTION_SEND` (`application/pdf`, `image/*`) + `ACTION_SEND_MULTIPLE` (`image/*`); take a persistable read grant; reuse `readImportFile()`; treat input as untrusted; **must never reach the MediaProjection/OcrConsent path**.
7. **"Mostrarme" jump-out** — extend manifest `<queries>` with `com.ubercab.driver` + `com.didiglobal.driver`; `getLaunchIntentForPackage(...)`. Pure navigation — no reading of the other app (preserves the documents-only posture).
8. **Annotated example drawables** per platform/step (kills the #1 failure: wrong screen). Flag as a maintenance tax; consider remote-hosting later.
9. **Completeness-as-goal** — derive a "missing pieces" line from which metric fields came back null; one-tap "Mejorar".

---

## 5. Backend storage map (how the information is stored)

End-to-end for one import (`POST /v1/imports`, [backend/src/routes/imports.ts](../backend/src/routes/imports.ts), bearer-authed):

```
Android ImportScreen ──multipart {platform, upload_type, files[]}──▶ POST /v1/imports[?dry_run]
   │
   ├─▶ 1. validate (≤10MB; png/jpeg/webp/pdf; DiDi=2 files else 1)
   ├─▶ 2. R2 object storage   key: {driverId}/{importId}.{ext}   (multi: {importId}_{i}.{ext})
   ├─▶ 3. imports row (status=pending)         ── skipped on dry_run
   ├─▶ 4. Claude Vision parse (parseUpload → per-platform parser)
   │        fail → imports.status=failed + 422 Spanish error
   │        dry_run → return metrics, NO durable record (import_id=null)
   ├─▶ 5. UPSERT weekly_aggregates (source='imported')
   └─▶ 6. imports.status=parsed + link weeklyAggregateId
```

| Store | Table/key | What lands | Notes |
|---|---|---|---|
| Object storage | R2 `{driverId}/{importId}.{ext}` | original PDF/screenshot(s) | retention/lifecycle **unconfirmed** (TD-012); dry-run leaves orphans (TD-018); **contains PII** (§2.3) |
| `imports` | id, driverId, platform, uploadType, fileKey, status, errorMessage, **parsedPayload (JSONB = full raw extraction)**, weeklyAggregateId | the attempt + raw Claude output | `parsed_payload` retains *everything* the doc had → minimize/scrub |
| `weekly_aggregates` | unique (driverId, platform, weekStart), `source` | **10** persisted metrics: net, gross, trips, km, hours, per-trip/km/hour, trips/hour, **commission_pct** | the other ~13 parsed fields (taxes, tips, incentives, surge, cash/card, peak day…) are **parsed but not persisted** — good minimization |
| `population_stats` | (city, platform, metric, period) | percentile breakpoints | synthetic seeds today; benchmarks/percentiles read from here |
| Local mirror (Android Room) | composite PK `(platform, weekStart, source)` | both captured + imported rows coexist | read prefers IMPORTED ([CompareState.kt:17](../android/ui/src/main/java/mx/kompara/ui/stats/CompareState.kt:17)) |

---

## 6. Two backend issues to fix (before the phase-2 gap-fill chip)

**6.1 The captured-vs-imported reconciliation is contradictory.**
- Backend enforces **captured beats imported**: the upsert's `setWhere: ne(source, "captured")` means an import **never overwrites** a live-captured row ([imports.ts:275](../backend/src/routes/imports.ts:275)).
- Android does the **opposite**: `rowsForWeek` prefers **IMPORTED** over CAPTURED ([CompareState.kt:17](../android/ui/src/main/java/mx/kompara/ui/stats/CompareState.kt:17)).

So locally the official import wins, but on the server the on-device *estimate* wins — the same week reconciles differently on each side, and the whole point of importing ("realized truth beats estimate", per [android-technical-design.md §3](android-technical-design.md)) is defeated server-side.

**Recommended fix: field-level coalesce, not blanket source-priority.** Imported numbers are authoritative for what they carry (net/gross/trips/commission); the reader is authoritative for what imports lack (km/hours on Uber). Take imported fields where present, keep captured fields where the import is null. This unifies both layers and is exactly what the reader-first gap-fill design needs.

**6.2 Partial imports can clobber real totals to 0.** `aggregateValuesFrom` defaults `netEarnings`/`grossEarnings` to `"0"` and `totalTrips` to `0` when null ([imports.ts:82-84](../backend/src/routes/imports.ts:82)) — the legacy TD-003 coercion. A commission-only Uber screenshot (earnings null) re-imported over an existing imported week would **overwrite its net/gross/trips with 0**. Needs **coalesce-don't-clobber** before the §4.3-secondary "completar" chip ships.

---

## 7. The only credible "credentials-adjacent" future path: an aggregator (optional, not v1)

If automated import ever becomes strategically necessary, **do not DIY credential scraping** — use a consent-based aggregator that holds the integration/ToS risk:

- **US incumbents (Argyle, Pinwheel, Truework, Plaid Income, Atomic): not options.** US-only; none support DiDi.
- **Palenca (YC S21, Experian-backed, Mexico-native): the one real candidate.** Explicitly supports **Uber + DiDi + 99 + inDrive + Rappi** via a consent-based widget; Mexico is its primary market.
- **Belvo: likely formal-payroll only** (its product targets stable-employment/social-security data, not gig). Drop as a gig path.

**Caveats that keep this out of v1:**
- Palenca still uses **credential entry, not OAuth** (neither Uber nor DiDi exposes a driver-earnings OAuth scope). So the ToS-conflict/ban risk **shifts to Palenca and the driver — it doesn't disappear.** The gain is that raw credentials never touch Kompara, and Kompara isn't the party logging in.
- **Verify before committing:** (a) does Palenca return Uber **km** and DiDi **commission %** that the driver's own documents can't? If not, the added risk/complexity buys little over Option A. (b) Who bears liability if a driver's account is actioned?
- Uber's **official Driver API** (OAuth, `/partners/payments`, `/partners/trips`) is the cleanest path in theory but is **"currently limited"** (gated by Uber approval) **and** its API Terms forbid building *"a competitive or substantially similar product"* and *"aggregating Uber's data with competitors' data"* — i.e. literally Kompara's compare feature. Doubly hostile; could apply long-term, don't depend on it.

**Framing:** Option A (own documents, built, lowest risk) is the default. Palenca is an **evaluated optional auto-import upgrade**, clearly consented and disclosed in Data Safety. DIY credential capture is rejected.

---

## 8. Open decisions & next steps

**For the founder to decide:**
1. **Reconciliation rule (§6.1):** ratify "field-level coalesce" (recommended) vs. a blanket source-priority?
2. **Commission gap-fill gating:** the Take-rate row sits inside `PaywallGate` — does the "completar" commission ask show to free users (in the free hero zone) or is it a paid-tier nudge?
3. **Palenca:** authorize a scoped evaluation (data depth + liability) now, or park until post-launch?
4. **DiDi commission (§3):** treat 16% MX as a benchmark constant, OCR it from the receipt/Tablero, or both?

**Sequenced work:**
- **Now (v1, mostly reuse):** build-list §4.4 items 1-9 — wizard + validate-before-accept + share-target + Comparar EmptyState CTA. *(Note: gated behind login UI landing — TD-017/TD-008.)*
- **Legal (B-038, parallel, counsel):** ratify the §2 posture; draft the **aviso de privacidad** + in-app risk disclosure covering the reader/import split and Anthropic as processor; verify the express-consent article/formality (§2.2).
- **Backend (before phase-2 chip):** §6.1 coalesce + §6.2 clobber fix; configure R2 retention (TD-012); skip dry-run storage (TD-018); minimize `parsed_payload` PII.
- **Phase 2:** reader-first gap-fill chip + DiDi share-buffer.

---

## 9. Sources (web-verified, adversarially checked)

**Uber:** [MX Community Guidelines](https://www.uber.com/legal/en/document/?country=mexico&lang=es&name=general-community-guidelines) · [API Terms](https://developer.uber.com/docs/drivers/terms-of-use) · [Driver API](https://developer.uber.com/docs/drivers/references/api) · [Download-your-data help](https://help.uber.com/en/driving-and-delivering/article/descarga-tus-datos-personales-de-la-app-de-uber) · [Weekly statements](https://drivers.uber.com/earnings/statements)
**DiDi:** [Términos DiDi Conductor (MX)](https://img0.didiglobal.com/static/dpubimg/445fc885ece0d133ec025e8e54185b78/index.html) · [Calcula tus ganancias (16% tarifa de servicio)](https://web.didiglobal.com/mx/conductor/calcula-tus-ganancias/) · [Revisar ganancias / Tablero](https://web.didiglobal.com/mx/guias/aprende-a-revisar-a-detalle-tus-ganancias/) *(verify this URL renders driver, not DiDi-Food, content)*
**Mexico law:** [LFPDPPP 2025 (Cámara de Diputados)](https://www.diputados.gob.mx/LeyesBiblio/pdf/LFPDPPP.pdf) · [CPF art. 211 bis (Justia)](https://mexico.justia.com/federales/codigos/codigo-penal-federal/libro-segundo/titulo-noveno/capitulo-ii/) · [INAI dissolution explainer](https://animalpolitico.com/verificacion-de-hechos/te-explico/proteccion-datos-personales-inai)
**Google Play:** [Malware policy](https://support.google.com/googleplay/android-developer/answer/9888380) · [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646)
**Aggregators:** [Palenca](https://www.ycombinator.com/companies/palenca) · [Palenca blog](https://blog.palenca.com/) · [Argyle gig coverage (US-only)](https://www.argyle.com/industries/gig-economy)
**Precedent:** [TJ-SP × StopClub (Migalhas)](https://www.migalhas.com.br/quentes/404804/tj-sp-valida-stopclub-app-que-calcula-ganhos-em-corridas-da-uber) *(interlocutory, merits pending)*

> **Confidence note:** ToS quotes (Uber API Terms, DiDi §1.3/§13) and statutory text (LFPDPPP, CPF 211 bis, Play policy) are high-confidence/verbatim. The art. 211 bis "consent is no defense" reasoning is a conservative reading, not settled doctrine. The financial-data consent formality (Art. 7 vs 8, "por escrito") needs counsel to pin. None of the corrections change the §0 decision.
