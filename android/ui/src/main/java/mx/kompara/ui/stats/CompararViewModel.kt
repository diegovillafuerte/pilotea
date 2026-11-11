package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.PeriodMoneyRepository
import java.time.ZoneId
import javax.inject.Inject

enum class CompararTab { TU, CIUDAD }

/** [tu] is null while loading or after an [error]; [city] is the driver's chosen city, null when never chosen. */
data class CompararUiState(
    val tab: CompararTab = CompararTab.TU,
    val tu: TuPresentation? = null,
    val error: Boolean = false,
    val city: String? = null,
)

/**
 * Comparar (plan 4.6): Tú from the driver's own slices, in the chosen unit and basis; Tu ciudad is not
 * open at launch and reads nothing, sends nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompararViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    repository: PeriodMoneyRepository,
    settings: SettingsRepository,
    clock: AppClock,
    zone: ZoneId,
) : ViewModel() {
    private val tab = saved.getStateFlow("compararTab", CompararTab.TU.name)
    private val refresh = MutableStateFlow(0)
    private val measure = settings.settings.map { it.preferredMetric to it.rateBasis }.distinctUntilChanged()
    // The window is the 28 days before today: it moves only when the day does.
    private val tu: Flow<Result<TuPresentation>?> = refresh.flatMapLatest {
        clock.moments().distinctUntilChangedBy { it.atZone(zone).toLocalDate() }
            .flatMapLatest { now -> repository.observeSlices(now, zone) }
            .combine(measure) { slices, (unit, basis) -> Result.success(TuPresentation.of(slices, unit, basis)) }
            .catch<Result<TuPresentation>?> { emit(Result.failure(it)) }
            .onStart { emit(null) }
    }
    private val city = settings.settings.map { if (it.cityChosen) CompareUiText.cityName(it.city) else null }.distinctUntilChanged()

    val uiState: StateFlow<CompararUiState> = combine(tab, tu, city) { tab, tu, city ->
        CompararUiState(CompararTab.valueOf(tab), tu?.getOrNull(), tu?.isFailure == true, city)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompararUiState(CompararTab.valueOf(tab.value)))

    fun selectTab(value: CompararTab) { saved["compararTab"] = value.name }
    fun retry() { refresh.value++ }
}
