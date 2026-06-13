package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.kompara.data.settings.DefaultThresholds
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/**
 * What the threshold editor renders: the shared floors plus the city-seeded default (the
 * "restablecer" target), labeled with the benchmark city.
 */
data class ThresholdsUiState(
    val threshold: PlatformThreshold = PlatformThreshold.DEFAULT,
    val cityDefault: PlatformThreshold = PlatformThreshold.DEFAULT,
    val cityDisplayName: String = "",
    val preferredMetric: PreferredMetric = PreferredMetric.DEFAULT,
)

/**
 * Drives the Ajustes threshold editor (B-070): all four floors (green/red × $/km/$/hr), persisted
 * immediately through the shared [SettingsRepository] so the overlay quick sheet, the simulator,
 * and the engine read the same values. One semáforo for every platform (B-076).
 */
@HiltViewModel
class ThresholdsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<ThresholdsUiState> =
        settingsRepository.settings.map { settings ->
            ThresholdsUiState(
                threshold = settings.effectiveThreshold,
                cityDefault = DefaultThresholds.forCity(settings.city.key),
                cityDisplayName = settings.city.displayName,
                preferredMetric = settings.preferredMetric,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThresholdsUiState(),
        )

    /** Pick which metric decides the semáforo (B-079): IPK (net $/km) or IPH (net $/hr). */
    fun setPreferredMetric(metric: PreferredMetric) {
        viewModelScope.launch { settingsRepository.setPreferredMetric(metric) }
    }

    fun setGreenPerKm(value: Double) = persist { ThresholdEditor.withGreenPerKm(it, value) }

    fun setRedPerKm(value: Double) = persist { ThresholdEditor.withRedPerKm(it, value) }

    fun setGreenPerHour(value: Double) = persist { ThresholdEditor.withGreenPerHour(it, value) }

    fun setRedPerHour(value: Double) = persist { ThresholdEditor.withRedPerHour(it, value) }

    /** Back to the city-seeded median floors (with their derived red floors). */
    fun resetToCityDefault() {
        val s = state.value
        viewModelScope.launch { settingsRepository.setThreshold(s.cityDefault) }
    }

    private fun persist(fold: (PlatformThreshold) -> PlatformThreshold) {
        val s = state.value
        viewModelScope.launch { settingsRepository.setThreshold(fold(s.threshold)) }
    }
}
