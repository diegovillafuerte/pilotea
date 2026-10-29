package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.billing.Capability
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.data.model.City
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.metrics.compare.ComparisonMetric
import mx.kompara.sync.aggregate.AggregateSyncScheduler
import mx.kompara.sync.aggregate.BenchmarksRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompararViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val repository: PeriodMoneyRepository,
    private val benchmarks: BenchmarksRepository,
    private val settings: SettingsRepository,
    tierGatekeeper: TierGatekeeper,
    private val selectedPeriod: SelectedPeriod,
    private val clock: AppClock,
    private val zone: ZoneId,
    connectivity: EarningsConnectivity,
    private val scheduler: AggregateSyncScheduler,
) : ViewModel() {
    val sheet = saved.getStateFlow<String?>("compareSheet", null)
    private val scope = saved.getStateFlow("compareScope", CompareScope.APPS.name)
    private val app = saved.getStateFlow("compareApp", "UBER")
    private val appsMetric = saved.getStateFlow("appsMetric", ComparisonMetric.PER_KM.name)
    private val cityMetric = saved.getStateFlow("cityMetric", ComparisonMetric.PER_TRIP.name)
    private val refresh = MutableStateFlow(0)
    private val saveError = MutableStateFlow(false)
    private val initialToday = Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate()
    private val initial = CompareUiState(initialToday.monday(), initialToday)
    private val choice = combine(scope, app, appsMetric, cityMetric, settings.settings) { scope, app, km, trip, settings ->
        CompareChoice(CompareScope.valueOf(scope), app, settings.city, ComparisonMetric.valueOf(km), ComparisonMetric.valueOf(trip))
    }.distinctUntilChanged()
    private val periods = combine(selectedPeriod.week, clock.moments(), refresh) { week, now, _ ->
        val today = now.atZone(zone).toLocalDate()
        val start = SelectedPeriod.resolve(week, today)
        PeriodWindow(start, start.plusDays(6), now, zone)
    }
    // Entitlement and evidence are independent: session-level verification never certifies a week.
    private val data = combine(periods, choice, tierGatekeeper.gateFor(Capability.COMPARE)) { window, choice, gate -> Triple(window, choice, gate.isUnlocked) }
        .flatMapLatest { (window, choice, premium) ->
            combine(repository.observeComparison(window), benchmarks.observeComparisonCohorts(choice.city)) { pair, cohorts ->
                val c = ComparisonBuilder.build(pair.first, pair.second, choice, cohorts = cohorts, premium = premium)
                CompareUiState(window.start, c.today, c.choice, false, c,
                    weeks = (CompareState.availableWeeks(pair.first.history).map(LocalDate::parse) + window.start + c.today.monday()).distinct().sortedDescending())
            }.onStart { emit(CompareUiState(window.start, window.asOf.atZone(zone).toLocalDate(), choice)) }
                .catch { emit(CompareUiState(window.start, window.asOf.atZone(zone).toLocalDate(), choice, false, error = true)) }
        }
    val uiState: StateFlow<CompareUiState> = combine(data.scan(initial) { old, next ->
        if (next.loading && old.weekStart == next.weekStart && (old.choice == next.choice || old.comparison?.singleApp != null && next.choice.scope == CompareScope.APPS && old.choice.city == next.choice.city && old.choice.appsMetric == next.choice.appsMetric && old.choice.cityMetric == next.choice.cityMetric) && old.comparison != null) old else next
    }, connectivity.offline, settings.settings, saveError) { state, offline, prefs, failed ->
        state.copy(offline = offline, contributing = prefs.shareAggregates, saveError = failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    init {
        viewModelScope.launch { settings.settings.map { it.city }.distinctUntilChanged().collectLatest { city ->
            try { benchmarks.refresh(city) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* Background refresh cannot take local earnings down. */ }
        } }
    }
    fun selectScope(value: CompareScope) {
        if (value == CompareScope.APPS && uiState.value.comparison?.singleApp != null) openSheet("single")
        else { if (value == CompareScope.CITY) saved["compareApp"] = uiState.value.choice.app; saved["compareScope"] = value.name; closeSheet() }
    }
    fun selectMetric(value: ComparisonMetric) {
        if (value in (uiState.value.comparison?.unavailable ?: emptyMap())) return
        saved[if (uiState.value.choice.scope == CompareScope.APPS) "appsMetric" else "cityMetric"] = value.name
        closeSheet()
    }
    fun selectApp(value: String) { if (value in listOf("UBER", "DIDI")) { saved["compareApp"] = value; saved["compareScope"] = CompareScope.CITY.name } }
    fun selectCity(value: City) { viewModelScope.launch {
        runCatching { settings.setCity(value) }.onSuccess { closeSheet(); saveError.value = false }.onFailure { saveError.value = true }
    } }
    fun selectWeek(date: LocalDate) { viewModelScope.launch { selectedPeriod.select(date.coerceAtMost(uiState.value.today.monday())); closeSheet() } }
    fun step(offset: Long) { val state = uiState.value; val next = state.weekStart.plusWeeks(offset); if (next <= state.today.monday()) selectWeek(next) }
    fun openSheet(id: String) { saved["compareSheet"] = id }
    fun closeSheet() { saved["compareSheet"] = null }
    fun setContribution(enabled: Boolean) { viewModelScope.launch {
        runCatching { settings.setShareAggregates(enabled); if (!enabled) scheduler.cancelPending() }
            .onSuccess { saveError.value = false }.onFailure { saveError.value = true }
    } }
    fun retry() { refresh.value++ }
}
