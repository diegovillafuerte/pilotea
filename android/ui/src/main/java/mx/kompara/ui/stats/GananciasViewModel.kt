package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.PeriodMoneyRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The Ganancias tab (plan 4.5): the status line, which body Hoy shows, and Semana. Today's day and its
 * sheets belong to [DayDetailViewModel], which every day (Hoy and a past day) shares.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GananciasViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val selectedPeriod: SelectedPeriod,
    private val snapshots: TodaySnapshots,
    private val reader: ReaderReadinessRepository,
    settings: SettingsRepository,
    repository: PeriodMoneyRepository,
    clock: AppClock,
    zone: ZoneId,
) : ViewModel() {
    /** 0 Hoy, 1 Semana. A dated week route opens on Semana. */
    val period = savedStateHandle.getStateFlow(PERIOD, if (savedStateHandle.get<String>(ARG_WEEK_START) != null) 1 else 0)
    val weekSheet = savedStateHandle.getStateFlow(WEEK_SHEET, false)
    private val initialWeek = SelectedPeriod.resolve(savedStateHandle[ARG_WEEK_START], Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate())
    init {
        // Consume a dated route once; later visits keep the shared choice.
        if (savedStateHandle.get<String>(ARG_WEEK_START) != null && savedStateHandle.get<Boolean>(WEEK_ARG_CONSUMED) != true) {
            viewModelScope.launch { selectedPeriod.select(initialWeek); savedStateHandle[WEEK_ARG_CONSUMED] = true }
        }
    }

    val uiState: StateFlow<GananciasUiState> = combine(snapshots.state, reader.state, settings.settings) { result, readiness, prefs ->
        result.fold({ GananciasStats.of(it, readiness, prefs) }, { GananciasUiState(HoyMode.ERROR) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GananciasUiState())

    private val weekData: Flow<WeekUiState> = combine(selectedPeriod.week, snapshots.state) { selected, today -> selected to today }
        .flatMapLatest { (selected, today) ->
            val window = today.getOrNull()?.today?.money?.window
                ?: return@flatMapLatest flowOf(WeekUiState(initialWeek, loading = false, error = true))
            val start = SelectedPeriod.resolve(selected, window.start)
            repository.observeWeek(PeriodWindow(start, start.plusDays(6), window.asOf, window.zone))
                .map { WeekUiState(start, loading = false, week = WeekPresentation(it)) }
                .onStart { emit(WeekUiState(start)) }
                .catch { emit(WeekUiState(start, loading = false, error = true)) }
        }
    /** Clock ticks and Room invalidations keep the drawn week; changing the week clears it. */
    val week: StateFlow<WeekUiState> = weekData.scan(WeekUiState(initialWeek)) { previous, next ->
        if (next.loading && previous.weekStart == next.weekStart && previous.week != null) previous else next
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState(initialWeek))

    fun selectPeriod(index: Int) { savedStateHandle[PERIOD] = index }
    /** One week back or forward; never past the current week. */
    fun step(offset: Long) {
        val state = week.value
        val next = state.weekStart.plusWeeks(offset)
        if (state.week?.today?.monday()?.let { next > it } != true) viewModelScope.launch { selectedPeriod.select(next) }
    }
    fun showWeekCalculation(show: Boolean) { savedStateHandle[WEEK_SHEET] = show }
    fun retry() = snapshots.retry()
    /** L3's "Encender el semáforo": clears a stored pause (the screen-capture fallback's own stop). */
    fun resume() = reader.resume()

    companion object {
        const val ARG_WEEK_START = "weekStart"
        private const val PERIOD = "gananciasPeriod"
        private const val WEEK_SHEET = "weekCalculation"
        private const val WEEK_ARG_CONSUMED = "weekArgConsumed"
    }
}

data class WeekUiState(
    val weekStart: LocalDate,
    val loading: Boolean = true,
    val week: WeekPresentation? = null,
    val error: Boolean = false,
)
