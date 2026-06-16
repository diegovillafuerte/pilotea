# Comparar redesign — implementation spec

> Status: **design locked 2026-06-16, not yet built.** Scope: rebuild the Android `Comparar` tab into a **weekly benchmarking hub** (driver vs. the city). Supersedes today's cross-platform paired-bars screen. Visual target: the v5 mockups produced this session (`.session-captures/comparar/`).

## 0. Principles this honors

- **Screen purpose (user directive):** *individual earnings → Inicio; comparison vs. other drivers → Comparar.* The driver's own "best day / best hours" lives on Inicio, never here.
- **Data reality (user):** only **weekly** city benchmarks exist. No daily/hourly population data (except, later, Uber's weekly PDF or legal per-trip capture). **Day/Hour drill is deferred** — not in this build.
- **Reader stays free.** Benchmarks/compare are the paid layer, tease-then-gate (blur, never blank).
- Reuse before adding. Real tokens: emerald `#059669`, slate, verdict triple, Inter, dark by default.

---

## 1. Screen anatomy (top → bottom)

1. **Brand bar** — `Kompara` K-glyph + wordmark, left; "Comparar" context, right. (New shared composable; see §7.)
2. **Week dropdown** — replaces today's `WeekPickerRow` FilterChips with a dropdown built for many weeks (§6).
3. **Shareable hero (FREE)** — branded card: "Le ganas al X% de los choferes de tu ciudad" + 20-person `PercentileBar` + "Top X%" `PercentileBadge` + a share icon → routes to the existing `ShareCardScreen`. The screenshot-worthy viral surface.
4. **Benchmark table (PREMIUM)** — the 6-metric comparison (§2).
5. **"Dónde puedes ganar más" (PREMIUM)** — 1–3 comparison-derived opportunity cards (§5).
6. **Cross-link to Inicio** — "Tus ganancias y tus mejores momentos · Inicio ›".
7. Footnote: "Pronto: cómo te comparas por día y por hora" (deferred).

Four states: **full** (2+ apps) · **single-app** · **free-locked** · **empty** (§9).

---

## 2. The benchmark table — metrics, columns, data sources

Six rows. Per row, four reads:

| Métrica (key) | Tú (blended) | Uber | DiDi | Tu lugar (percentil) |
|---|---|---|---|---|
| Ganancia neta — *total* | `PeriodStats.netEarningsMxn` | ⚠ no benchmark | ⚠ no benchmark | ⚠ no benchmark |
| IPH `earnings_per_hour` | `PeriodStats.earningsPerHour` | `mean` (uber) | `mean` (didi) | `PercentileResult` |
| IPK `earnings_per_km` | `PeriodStats.earningsPerKm` | — *(Uber no km)* | `mean` (didi) | `PercentileResult` |
| IPT `earnings_per_trip` | `PeriodStats.earningsPerTrip` | `mean` (uber) | `mean` (didi) | `PercentileResult` |
| Viajes/hora `trips_per_hour` | `PeriodStats.tripsPerHour` | `mean` (uber) | `mean` (didi) | `PercentileResult` |
| Take rate `platform_commission_pct` | only imported/Uber-PDF, else — | `mean` (uber) | — *(DiDi no desglosa)* | `PercentileResult` (inverted) |

**Column data sources (all already exist):**
- **Tú (blended across apps):** `PeriodStats.fromWeekly(weekRows, platform = null)` — the existing fold (`android/ui/.../stats/PeriodStats.kt:56`): net/gross/trips/km summed, hours = `max` (replicated per platform-row), rates **recomputed** from the summed totals, acceptance averaged. This is the "Tú" column. Per-platform N/A propagates naturally (a null rate stays null).
- **Uber/DiDi averages:** `PopulationStat.mean` for `(city, platform, metric)` — fetched via `BenchmarksRepository.observe(city): Flow<List<PopulationStat>>` (returns every platform's rows for the city; filter by `platform` + `metric`). Use `mean` (arithmetic average) as "el chofer típico"; `p50` (median) is the alternative — pick `mean`.
- **Tu lugar (percentil):** `PercentileResult` from `PercentileRepository.observe(city, platform, metricValues)` → `MetricPercentiles.byMetric(results)`. `topPercent = 100 - displayPercentile`. Commission inversion is automatic (`PercentileCalculator.INVERTED_METRICS = {"platform_commission_pct"}`).

**Metric keys (canonical):** `earnings_per_trip`, `earnings_per_km`, `earnings_per_hour`, `trips_per_hour`, `platform_commission_pct` (from `MetricPercentiles` / `population_stats.metric_name`).

**N/A rules (never fake a number):** Uber `earnings_per_km` → "—" (Uber no reporta km); DiDi `platform_commission_pct` → "—" (DiDi no desglosa). For a single-app DiDi driver, the driver's *own* take rate is also "—" (DiDi gives no commission). Use `Formatters.DASH`.

### ⚠ Data gaps to resolve (these are the real scope risks)

1. **Ganancia neta has no population benchmark.** `population_stats` carries only the 5 efficiency metrics + commission — **no `net_earnings`**. So total earnings has no Uber/DiDi average and no percentile.
   - **Decision needed.** Options: (a) render Ganancia neta as a **Tú-only summary row** (averages + percentil = "—"); (b) add a `net_earnings` metric to the backend `population_stats` + seeds (richer, backend work). Spec defaults to **(a)** for phase 1 — comparing totals across drivers is also weak methodology (depends on hours), so Tú-only context is defensible.
2. **"Tu lugar = vs. all drivers" has no combined population.** The percentile engine ranks a value against a **single platform's** population (`PercentileRepository.observe(city, platform, …)`). There is no `platform = "all"` row.
   - **Decision needed.** Options: (a) add a combined `platform = "all"` (or `"combined"`) row to `population_stats` per `(city, metric)` so the blended "Tú" ranks against everyone (backend work); (b) **phase-1 fallback**: rank the blended value against the driver's **primary platform** (most hours that week) and label honestly — but copy then can't truthfully say "todos los choferes." Spec defaults to **(a) as the correct target**, **(b) as the interim** with copy "vs. choferes de {plataforma}" until the combined row exists.
3. **Take rate is sparse.** The driver's own commission is null on the captured path — only imported weeks / Uber PDF supply it. Uber has a commission benchmark; DiDi does not. Row stays, mostly "—" until imports land.

---

## 3. New / changed types

- **`ComparisonRow`** (new, in `:ui` `mx.kompara.ui.stats`): `data class ComparisonRow(val metric: String, val label: String, val unit: MetricUnit, val tu: Double?, val uberAvg: Double?, val didiAvg: Double?, val percentile: PercentileResult?, val lowerIsBetter: Boolean, val uberNaReason: NaReason?, val didiNaReason: NaReason?)`. `MetricUnit` drives formatting (MXN / per-hour / per-km / count / percent). `NaReason` ∈ {NO_KM, NO_COMMISSION, NO_BENCHMARK}.
- **`WeeklyComparison`** (new): `data class WeeklyComparison(val weekStart: String, val rows: List<ComparisonRow>, val standing: PercentileResult?, val standingMetric: String, val opportunities: List<Recommendation>)`. `standing` drives the hero (pick by priority `earnings_per_hour → earnings_per_km → earnings_per_trip`, first present — same order the old verdict used).
- **`CompareUiData`** (existing, `CompareState.kt:121`) currently holds only `{weekStart, mode}`. **Extend** it (or replace `CompareMode.Comparison`'s payload) to carry the `WeeklyComparison` for the 2+ and single-app modes. Keep `CompareMode.Empty`.
- **Removed:** `ExampleTeaser`/`exampleResult()` (fake single-app data), and the cross-platform `CompareRowBars`/`PairedBar`/`MetricBreakdown` paired-bars (replaced by the table). `PlatformPairChooser`/`nextPair` are no longer needed (no A/B pair — the table shows both platform averages at once); `selectPair`/`selectedPair` can be dropped.

---

## 4. ViewModel changes — `CompararViewModel`

Today (`stats/CompararViewModel.kt`) it injects only `AggregateDao`, `TierGatekeeper`, `GateFunnel` and builds `uiState` from `combine(observeWeekly, selectedWeek, selectedPair)`. Changes:

**Inject** (Hilt): add `percentileRepository: PercentileRepository`, `benchmarksRepository: BenchmarksRepository`, `settingsRepository: SettingsRepository`. Keep `aggregateDao`, `tierGatekeeper`, `gateFunnel`. Drop `selectedPair`.

**Build** (mirror `InicioDashboardViewModel`'s percentile pattern, `InicioDashboardViewModel.kt:99`):

```
uiState = combine(
  aggregateDao.observeWeekly(),
  selectedWeek,
  settingsRepository.settings.map { it.city }.distinctUntilChanged(),
) { rows, week, city -> Triple(rows, resolveWeek(rows, week), city) }
.flatMapLatest { (rows, week, city) ->
  if (week == null) flowOf(CompareUiState.empty())
  else {
    val weekRows   = rowsForWeek(rows, week)                 // IMPORTED preferred (existing rowsForWeek)
    val blended    = PeriodStats.fromWeekly(weekRows, platform = null)
    val refPlatform = primaryPlatform(weekRows)             // most hours; interim until "all" population
    combine(
      benchmarksRepository.observe(city),                    // platform means
      percentileRepository.observe(city, refPlatform, MetricPercentiles.metricValues(blended)),
    ) { stats, pct ->
      CompareUiState(
        loading = false,
        availableWeeks = CompareState.availableWeeks(rows),
        data = ComparisonBuilder.build(week, weekRows, blended, stats, MetricPercentiles.byMetric(pct)),
      )
    }
  }
}.stateIn(viewModelScope, WhileSubscribed(5_000), CompareUiState.LOADING)
```

- **`gateState`** stays `tierGatekeeper.gateFor(Capability.COMPARE)` (the table + opportunities). The **hero standing is rendered free** (outside the gate) — see §8.
- **`ComparisonBuilder`** (new pure object, testable, no Android) assembles `WeeklyComparison` from the blended `PeriodStats`, the `List<PopulationStat>`, and the `Map<String,PercentileResult>` — including N/A reasons, commission inversion, and the standing pick. Opportunities come from §5.

---

## 5. Opportunities engine ("Dónde puedes ganar más")

Comparison-only, weekly. **Do not** reuse Inicio's individual best-hours/streak rules here. Add a small pure builder **`ComparisonOpportunities`** (in `:metrics` or `:ui`) that emits `Recommendation`-shaped items (so we reuse `RecommendationCard`):

- **Platform-mix opportunity** — compare `mean` across platforms for a lead metric (e.g. `earnings_per_hour` or `earnings_per_km`) and the driver's app mix (hours per platform from `weekRows`): *"Los choferes ganan {gap}% más con {plataforma} — tú manejas más {otra}."* Fires when the better platform's `mean` exceeds the other by ≥ 15% and the driver under-indexes on the better one. Type `INFO`, premium.
- **Top-of-city gap** — `PopulationStat.p90` for `earnings_per_hour` vs the driver's value: *"El top 20% gana ${gap}/h más que tú."* Type `INFO`, premium.
- **Take-rate standing** — driver commission vs Uber `mean` (only when commission present): *"Tu comisión ({x}%) es mejor que el promedio ({y}%)."* Type `POSITIVE` when below average. Premium.

`Recommendation(id, type, title, body, premium=true)`; render with `RecommendationCard(type, title, body)` (accents: POSITIVE→`VerdictGreen`, INFO→`INFO_BLUE`, WARNING→`VerdictYellow`). Cap at 3, sort by `priority`. When `Capability.COMPARE` is locked, feed the builder empty inputs so nothing leaks (same self-guard pattern as `RecommendationsBuilder`).

---

## 6. Week dropdown

Replace the private `WeekPickerRow` (FilterChips) with a **dropdown** for many weeks:
- A tappable pill `"{Formatters.formatWeekRangeLabel(week)} ▾"` with optional `‹ ›` prev/next arrows that step through `availableWeeks` (already `sortedDescending()`).
- Tapping opens a `ModalBottomSheet` (preferred over `DropdownMenu` for long lists + big tap targets) listing weeks newest-first, **grouped by month** via `Formatters.formatMonthLabel(weekStart)`. Selecting calls `viewModel.selectWeek(weekStart)`.
- Keep it visible in the empty state so a driver can jump back to a week with data.

---

## 7. Composables

**New (in `:ui`):**
- `KomparaWordmark()` — promote the brand lockup to a shared public composable: `Icon(painterResource(R.drawable.ic_kompara_logomark), tint = …)` + `Text(stringResource(R.string.brand_name))`. (Today the only lockup is `private fun BrandStrip()` inside `overlay/.../VerdictChipUi.kt` — extract a `:ui` version; add a `brand_name` string = "Kompara".)
- `ComparBrandBar(context: String)` — top bar using `KomparaWordmark` + context label.
- `ShareableHeroCard(standing: PercentileResult?, onShare: () -> Unit)` — branded card: standing sentence (`percentile_bar_description` idiom), `PercentileBar(displayPercentile, contentDescription, highlightColor = BrandGreen)`, `PercentileBadge(topPercent, …)`, share icon → `onShare`. Degrade to "Aún estamos midiendo cómo te comparas con tu ciudad" when `standing == null`.
- `BenchmarkTable(comparison: WeeklyComparison)` — the 6-row grid: grouped "Promedio ciudad" header over Uber/DiDi, **Tú** column emerald-tinted, percentile mini-bar + "Top X%", `Formatters.DASH` for N/A with a tiny reason, Take-rate "↓ menos es mejor".
- `OpportunitiesSection(items: List<Recommendation>)` — `RecommendationCard` per item.
- `WeekDropdown(...)` (§6).

**Reuse verbatim:** `PercentileBar`, `PercentileBadge`/`LockedPercentileBadge`, `PercentilePanel`, `PaywallGate`, `EmptyState`, `RecommendationCard`, `Formatters`.

**Formatting** (note exact names — some assumed names don't exist): `formatMxn`, `formatPerHour`, `formatPerKm`, `formatPerHourCount` (→ "2.3/h"), `formatPercent` (→ "21 %"), `DASH`. There is **no `formatPerTrip`** (use `formatMxn`) and **no `pct`** (use `formatPercent`).

---

## 8. Gating

- **Free (the hook, screenshotable):** brand bar, week dropdown, and the **`ShareableHeroCard`** (standing sentence + 20-person bar + Top X% badge + share). This is strictly additive vs. today (today only the verdict sentence was free).
- **Premium:** the `BenchmarkTable` + `OpportunitiesSection`, wrapped in one `PaywallGate(surface = GateSurface.COMPARE, state = gateState, valueHint = stringResource(R.string.gate_hint_compare), funnel = gateFunnel, onUpgrade = onUpgrade, ctaText = stringResource(R.string.paywall_cta)) { … }`.
- **Locked contract:** when `Capability.COMPARE` is locked, the ViewModel must **not** compose real premium values — the table renders neutral stand-ins (`LockedPercentileBadge`, dim placeholder rows), opportunities are fed empty. The free hero already gave one real taste.
- **Capability divergence (documented decision):** Inicio gates percentile bars under `Capability.BENCHMARKS`; Comparar keeps the whole detailed surface under the single `Capability.COMPARE` for a one-gate story (the hero standing is free). A `BENCHMARKS`-only subscriber would see Inicio percentiles but not Comparar's table without `COMPARE`. Ratify, or split. (`GateSurface.PERCENTILE`/`Capability.PERCENTILE` do **not** exist — don't invent them.)
- **Never `FLAG_SECURE`** (would break the driver's own screenshots — and the share card is the point).

---

## 9. States

- **Empty** (`CompareMode.Empty` / no data): reuse `EmptyState(icon = Icons.AutoMirrored.Filled.List, title = comparar_no_data_title, body = comparar_no_data_body, ctaText = comparar_no_data_cta, onCtaClick = onOpenReader)`. Keep the week dropdown visible above it.
- **Single-app:** the percentile still works; the table still shows **both** platform averages (you compare to the typical Uber *and* DiDi driver even with one app). "Tú" = that platform's value (not blended); header note "Solo manejas {plataforma}". A highlighted opportunity: "Aún no manejas {otra} — el promedio gana {x}; pruébalo." (Retire the old fake `Ejemplo`.)
- **Free-locked:** hero free; table + opportunities behind the `PaywallGate` tease (blur + lock chip + `gate_hint_compare` + `paywall_cta`).
- **Full (2+ apps):** the complete experience.

---

## 10. Branding & share

- `ComparBrandBar` on Comparar (and ideally all tabs — separate follow-up).
- Hero share icon → `navController.navigate(KomparaDestination.SHARE_CARD_ROUTE)`. The existing `ShareCardScreen` / `ShareCardRenderer` already renders a "Tu Semana" card with a **`percentileFlex`** line + "Kompara" wordmark + "Descárgala gratis · kompara.mx" CTA — wire the current standing into `ShareCardData.percentileFlex` so the shared image carries the comparison flex.
- **Brand-green cleanup (flag, not blocking):** `ShareCardRenderer.BRAND_GREEN = 0xFF12A150` and `ic_launcher_foreground fillColor #1DB954` are stale; the live theme brand is `BrandGreen = #059669`. Align in a small follow-up so shared cards match the app.

---

## 11. Strings

**Reuse:** `gate_hint_compare` ("Compara tus ganancias entre Uber y DiDi"), `paywall_cta`, `metric_percentile_format` ("Top %1$d%%"), `percentile_bar_description`(`_national`), `percentile_synthetic_tag`, `comparar_no_data_*`.

**New `comparar_*` keys (deck):** brand_name ("Kompara"); comparar_hero_pending ("Aún estamos midiendo cómo te comparas con tu ciudad"); comparar_col_tu ("Tú"), comparar_col_city ("Promedio ciudad"), comparar_col_place ("Tu lugar"); comparar_legend (column decoder); comparar_metric_net/iph/ipk/ipt/tph/take + their sublabels; comparar_lower_better ("↓ menos es mejor"); comparar_na_km ("Uber no reporta km"), comparar_na_commission ("DiDi no desglosa comisión"), comparar_na_benchmark ("Sin comparativa todavía"); comparar_opps_title ("Dónde puedes ganar más"); comparar_xlink_inicio ("Tus ganancias y tus mejores momentos"); comparar_soon_day_hour ("Pronto: cómo te comparas por día y por hora"); comparar_single_note ("Solo manejas %1$s — «Tú» es tu %1$s"). Opportunity copy lives in code (like `RecommendationEngine`), Spanish.

**Retire:** `comparar_example_tag`, `comparar_locked_title/body`, `comparar_breakdown_title`, `comparar_verdict_title`, `comparar_winner_badge`, `comparar_not_comparable*`, `comparar_single_*` (old), `comparar_week_picker_label` (dropdown replaces it).

---

## 12. Tests

- **`ComparisonBuilderTest`** — blended-Tú assembly; N/A reasons (Uber km, DiDi commission, net-earnings no-benchmark); commission inversion (low cut → high `displayPercentile`); standing pick order; opportunity rules (platform-mix gap, p90 gap, take-rate).
- **`CompararViewModelTest`** — empty/single/full state derivation; city wiring; gate locked → no premium values composed; week resolution.
- **Compose previews** — full / single / locked / empty.
- **Promote typography debt while here:** `titleMedium`, `labelLarge`, `bodySmall` aren't in `KomparaTypography` (fall back to Roboto). Add them so the new card text renders in Inter.

---

## 13. Phasing

- **Phase 1 (this build):** table for the 5 benchmarked metrics + hero + branding + week dropdown + opportunities + 4 states + gating. Ganancia neta = Tú-only row. Percentile vs. **primary platform** (interim), copy labeled honestly.
- **Phase 2 (backend):** add a combined `platform = "all"` population row + a `net_earnings` benchmark → true "vs. todos los choferes" and a real Ganancia-neta comparison. Align ShareCard/launcher green to `#059669`.
- **Phase 3 (later, data-gated):** day/hour comparison once Uber-PDF / legal per-trip data exists.

---

## 14. Open questions for the founder

1. **Ganancia neta:** Tú-only row now (default), or invest in a `net_earnings` benchmark so it gets averages + percentile?
2. **"Vs. all drivers":** add the combined `"all"` population (correct), or ship phase 1 ranking vs. the primary platform with honest "vs. choferes de {plataforma}" copy?
3. **Capability gate:** ratify the single-`COMPARE` story (hero free, table premium), or follow Inicio and gate the table's percentile column under `BENCHMARKS`?
4. **Branding scope:** brand bar on Comparar only now, or roll the shared `KomparaWordmark` across all tabs in this change?
