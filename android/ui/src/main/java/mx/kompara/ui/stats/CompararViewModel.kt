package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mx.kompara.billing.Capability
import mx.kompara.billing.GateState
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.model.Platform
import mx.kompara.ui.paywall.GateFunnel
import javax.inject.Inject

/**
 * Backs the Comparar tab (B-047): which platform paid better per metric this week, from the
 * auto-captured weekly aggregates (B-039), preferring imported (realized) figures when present.
 *
 * Reactive end-to-end over the weekly-aggregate table, the chosen week, the chosen platform pair (only
 * used when 3+ platforms have data), and the premium [GateState]. All branching/selection/compare math
 * lives in the pure [CompareState] + `:metrics` [mx.kompara.metrics.compare.CompareCalculator] so the
 * 0/1/2/3-platform states and week switching are unit-tested without Room or the gatekeeper.
 *
 * The default week is the most recent week that has data (the picker offers every week with data,
 * newest first) — positional, like [HistoryWeeks], so no clock is needed and a driver who didn't drive
 * "this" week still lands on their latest comparison.
 *
 * Premium gate (B-050, B-047 req 4): the tab is [Capability.COMPARE]. The screen shows the verdict
 * summary FREE as the tease and gates the per-metric breakdown behind the [PaywallGate]; [gateState] is
 * the single source of truth and [gateFunnel] records gate impressions.
 */
@HiltViewModel
class CompararViewModel @Inject constructor(
    aggregateDao: AggregateDao,
    tierGatekeeper: TierGatekeeper,
    val gateFunnel: GateFunnel,
) : ViewModel() {

    /** The week the picker is showing; null ⇒ default to the newest week with data. */
    private val selectedWeek = MutableStateFlow<String?>(null)

    /** The platform pair chosen when 3+ platforms have data; null ⇒ first two (declaration order). */
    private val selectedPair = MutableStateFlow<Pair<Platform, Platform>?>(null)

    val gateState: StateFlow<GateState> =
        tierGatekeeper.gateFor(Capability.COMPARE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState.LOCKED)

    val uiState: StateFlow<CompareUiState> = combine(
        aggregateDao.observeWeekly(),
        selectedWeek,
        selectedPair,
    ) { rows, week, pair ->
        val weeks = CompareState.availableWeeks(rows)
        // Default to the newest week with data; keep the explicit choice if it still has data.
        val resolvedWeek = week?.takeIf { it in weeks } ?: weeks.firstOrNull()
        if (resolvedWeek == null) {
            CompareUiState(loading = false, availableWeeks = emptyList(), data = null)
        } else {
            CompareUiState(
                loading = false,
                availableWeeks = weeks,
                data = CompareState.forWeek(rows, resolvedWeek, pair),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CompareUiState.LOADING,
    )

    /** Switch the week the picker shows (ISO Monday). */
    fun selectWeek(weekStart: String) {
        selectedWeek.value = weekStart
    }

    /** Choose which two platforms to compare (only meaningful with 3+ platforms). */
    fun selectPair(a: Platform, b: Platform) {
        selectedPair.value = a to b
    }
}

/** Render state for the Comparar tab. */
data class CompareUiState(
    val loading: Boolean,
    /** Every week with data, newest first — what the week picker offers (empty ⇒ no data at all). */
    val availableWeeks: List<String>,
    /** The resolved comparison data for the shown week; null while loading or when there's no data. */
    val data: CompareUiData?,
) {
    companion object {
        val LOADING = CompareUiState(loading = true, availableWeeks = emptyList(), data = null)
    }
}
