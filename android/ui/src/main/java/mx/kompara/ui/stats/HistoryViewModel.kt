package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import mx.kompara.billing.Capability
import mx.kompara.billing.TierGatekeeper
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.ui.paywall.GateFunnel
import javax.inject.Inject

/**
 * Backs the History tab's weeks list (B-040 req 3): every week with data, newest first, each summed
 * across platforms and badged capturado/importado. Reactive over the weekly-aggregate table so a
 * fresh rollup or an import lands without a refresh.
 *
 * B-050: the free tier sees the current + previous week only; older weeks are blurred behind the
 * paywall gate. Gating flows through [TierGatekeeper] ([Capability.HISTORY]); the truncation maths live
 * in the pure [HistoryWeeks.partition] so they're unit-tested without Room.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    aggregateDao: AggregateDao,
    tierGatekeeper: TierGatekeeper,
    /** Exposed so the screen's [mx.kompara.ui.paywall.PaywallGate] can record gate impressions. */
    val gateFunnel: GateFunnel,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> =
        combine(
            aggregateDao.observeWeekly(),
            tierGatekeeper.gateFor(Capability.HISTORY),
        ) { rows, gate ->
            val partition = HistoryWeeks.partition(HistoryWeeks.build(rows), locked = gate.isLocked)
            HistoryUiState(loading = false, partition = partition)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(
                loading = true,
                partition = HistoryPartition(emptyList(), emptyList()),
            ),
        )
}

/** Render state for the history weeks list. */
data class HistoryUiState(
    val loading: Boolean,
    val partition: HistoryPartition,
) {
    /** The free-visible weeks (or all weeks when unlocked). */
    val weeks: List<HistoryWeek> get() = partition.visible

    /** Older weeks teased behind the gate (empty when unlocked). */
    val lockedWeeks: List<HistoryWeek> get() = partition.locked

    val isEmpty: Boolean
        get() = !loading && partition.visible.isEmpty() && partition.locked.isEmpty()
}
