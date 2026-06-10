package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mx.kompara.data.db.dao.AggregateDao
import javax.inject.Inject

/**
 * Backs the History tab's weeks list (B-040 req 3): every week with data, newest first, each summed
 * across platforms and badged capturado/importado. Reactive over the weekly-aggregate table so a
 * fresh rollup or an import lands without a refresh.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    aggregateDao: AggregateDao,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> =
        aggregateDao.observeWeekly()
            .map { rows -> HistoryUiState(loading = false, weeks = HistoryWeeks.build(rows)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = HistoryUiState(loading = true, weeks = emptyList()),
            )
}

/** Render state for the history weeks list. */
data class HistoryUiState(
    val loading: Boolean,
    val weeks: List<HistoryWeek>,
) {
    val isEmpty: Boolean get() = !loading && weeks.isEmpty()
}
