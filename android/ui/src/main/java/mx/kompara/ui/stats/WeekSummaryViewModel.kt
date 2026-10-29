package mx.kompara.ui.stats

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.billing.Capability
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.metrics.PeriodMoneyRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val selectedPeriod: SelectedPeriod,
    private val snapshots: TodaySnapshots,
    repository: PeriodMoneyRepository,
    tierGatekeeper: TierGatekeeper,
    clock: AppClock,
    zone: ZoneId,
    connectivity: EarningsConnectivity,
) : ViewModel() {
    val sheet = savedStateHandle.getStateFlow<String?>("weekSheet", null)
    private val initialWeek = SelectedPeriod.resolve(savedStateHandle[ARG_WEEK_START], java.time.Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate())
    init {
        // Consume legacy dated routes once; later visits to another root retain the shared choice.
        if (savedStateHandle.get<String>(ARG_WEEK_START) != null && savedStateHandle.get<Boolean>("weekArgConsumed") != true) {
            viewModelScope.launch { selectedPeriod.select(initialWeek); savedStateHandle["weekArgConsumed"] = true }
        }
    }
    private val data: Flow<WeekSummaryUiState> = combine(selectedPeriod.week, snapshots.state,
        tierGatekeeper.gateFor(Capability.HISTORY)) { selected, today, gate -> Triple(selected, today, gate) }
        .flatMapLatest { (selected, today, gate) ->
            val window = today.getOrNull()?.today?.money?.window
                ?: return@flatMapLatest flowOf(WeekSummaryUiState(initialWeek, loading = false, error = true))
            val start = SelectedPeriod.resolve(selected, window.start)
            repository.observeWeek(PeriodWindow(start, start.plusDays(6), window.asOf, window.zone))
                .map { WeekSummaryUiState(start, loading = false, week = WeekPresentation(it, premium = !gate.isLocked)) }
                .onStart { emit(WeekSummaryUiState(start)) }
                .catch { emit(WeekSummaryUiState(start, loading = false, error = true)) }
        }
    // Do not flash a skeleton for every clock tick/Room invalidation. Changing period clears values.
    val uiState: StateFlow<WeekSummaryUiState> = combine(data.scan(WeekSummaryUiState(initialWeek)) { previous, next ->
        if (next.loading && previous.weekStart == next.weekStart && previous.week != null) previous else next
    }, connectivity.offline) { state, offline -> state.copy(offline = offline) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekSummaryUiState(initialWeek))

    fun selectWeek(date: LocalDate) { viewModelScope.launch { selectedPeriod.select(date); closeSheet() } }
    fun step(offset: Long) {
        val state = uiState.value
        val next = state.weekStart.plusWeeks(offset)
        if (state.week?.today?.monday()?.let { next > it } != true) selectWeek(next)
    }
    fun openSheet(value: String) {
        val week = uiState.value.week
        if (value == "picker" || week != null && (value == "calculation" || !week.locked)) savedStateHandle["weekSheet"] = value
    }
    fun closeSheet() { savedStateHandle["weekSheet"] = null }
    fun retry() = snapshots.retry()
    companion object { const val ARG_WEEK_START = "weekStart" }
}

data class WeekSummaryUiState(
    val weekStart: LocalDate,
    val loading: Boolean = true,
    val week: WeekPresentation? = null,
    val error: Boolean = false,
    val offline: Boolean = false,
)

/** Connectivity qualifies local data; it never controls its query or availability. */
class EarningsConnectivity @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    val offline: Flow<Boolean> = callbackFlow {
        fun update() { trySend(manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = update()
            override fun onLost(network: Network) = update()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update()
        }
        manager.registerDefaultNetworkCallback(callback)
        update()
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
