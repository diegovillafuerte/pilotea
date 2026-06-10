package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.dao.OfferDao
import mx.kompara.data.db.dao.ShiftDao
import mx.kompara.data.db.dao.TripDao
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.metrics.CostProfileMapper
import java.time.ZoneId
import javax.inject.Inject

/**
 * Backs the day-detail screen (B-040 req 2): the per-shift timeline, the offer funnel with verdicts,
 * and the best-hour blocks for one local day.
 *
 * One-shot loads (a day is historical, not a live stream): it fetches the day's captured daily
 * aggregate, the shifts overlapping the day, and the trips/offers inside the day window, then folds
 * them via the pure [DayDetailBuilder]/[BestHours] helpers. The day is passed in nav args as
 * [ARG_DAY] (an ISO yyyy-MM-dd string), defaulting to today.
 */
@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aggregateDao: AggregateDao,
    private val shiftDao: ShiftDao,
    private val tripDao: TripDao,
    private val offerDao: OfferDao,
    private val costProfileRepository: CostProfileRepository,
    private val weekClock: WeekClock,
    private val clock: AppClock,
    private val zone: ZoneId,
) : ViewModel() {

    private val dayIso: String =
        savedStateHandle.get<String>(ARG_DAY)?.takeIf { it.isNotBlank() }
            ?: weekClock.dayIso(clock.nowMs())

    private val _uiState = MutableStateFlow<DayDetailUiState>(DayDetailUiState.Loading(dayIso))
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val from = weekClock.dayStartMs(dayIso)
            val until = weekClock.dayEndMs(dayIso)

            val dailyRows = aggregateDao.capturedDay(dayIso)
            val shifts = shiftDao.overlapping(from, until)
            val trips = tripDao.completedStartedBetween(from, until)
            val offers = offerDao.seenBetween(from, until)

            val marginalCostPerKm = CostProfileMapper
                .toCostProfileOrZero(costProfileRepository.get())
                .marginalCostPerKm

            val period = PeriodStats.fromDaily(dailyRows, platform = null)
            val bestHours = BestHours(zone, marginalCostPerKm).blocks(trips)
            val detail = DayDetailBuilder.build(
                dayIso = dayIso,
                period = period,
                shifts = shifts,
                trips = trips,
                offers = offers,
                bestHours = bestHours,
            )
            _uiState.value = DayDetailUiState.Loaded(detail)
        }
    }

    companion object {
        /** Nav argument key: the ISO day (yyyy-MM-dd) to detail. */
        const val ARG_DAY = "day"
    }
}

/** Render state for the day-detail screen. */
sealed interface DayDetailUiState {
    val dayIso: String

    data class Loading(override val dayIso: String) : DayDetailUiState
    data class Loaded(val detail: DayDetail) : DayDetailUiState {
        override val dayIso: String get() = detail.dayIso
    }
}
