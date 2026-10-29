package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/** Hoy observes the same dated money snapshot used by its drill-in, never a weekly rollup. */
@HiltViewModel
class InicioDashboardViewModel @Inject constructor(
    private val snapshots: TodaySnapshots,
    private val reader: ReaderReadinessRepository,
    settings: SettingsRepository,
    private val costs: CostProfileRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val actionError = MutableStateFlow<String?>(null)
    private val baseState = combine(snapshots.state, reader.state, settings.settings) { result, readiness, preferences ->
        result.fold({ InicioStats.today(it, readiness, preferences) }, { InicioUiState(loading = false, error = true) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUiState())
    val uiState = combine(baseState, actionError) { state, error -> state.copy(actionError = error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUiState())
    fun confirmCosts() {
        val version = uiState.value.typicalCost ?: return
        viewModelScope.launch {
            try {
                val now = clock.nowMs()
                costs.saveRunningCosts(version.inputs, now, now)
                actionError.value = null
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                actionError.value = "No pudimos guardar tus costos. Inténtalo otra vez."
            }
        }
    }
    fun resume() = reader.resume()
    fun retry() = snapshots.retry()
}
