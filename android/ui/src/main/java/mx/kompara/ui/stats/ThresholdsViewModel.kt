package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.DefaultThresholds
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/**
 * What the threshold editor renders: the selected platform's floors plus the city-seeded default
 * (the "restablecer" target), labeled with the benchmark city.
 */
data class ThresholdsUiState(
    val platform: Platform = Platform.UBER,
    val threshold: PlatformThreshold = PlatformThreshold.DEFAULT,
    val cityDefault: PlatformThreshold = PlatformThreshold.DEFAULT,
    val cityDisplayName: String = "",
)

/**
 * Drives the Ajustes threshold editor (B-070): all four floors (green/red × $/km/$/hr) per
 * platform, persisted immediately through the shared [SettingsRepository] so the overlay quick
 * sheet, the simulator, and the engine read the same values.
 */
@HiltViewModel
class ThresholdsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Platforms the editor exposes (everything the reader can judge). */
    val platforms: List<Platform> = listOf(Platform.UBER, Platform.DIDI, Platform.INDRIVE)

    private val platform = MutableStateFlow(Platform.UBER)

    val state: StateFlow<ThresholdsUiState> =
        combine(settingsRepository.settings, platform) { settings, plat ->
            ThresholdsUiState(
                platform = plat,
                threshold = settings.thresholdFor(plat),
                cityDefault = DefaultThresholds.forCity(settings.city.key, plat),
                cityDisplayName = settings.city.displayName,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThresholdsUiState(),
        )

    fun selectPlatform(value: Platform) {
        platform.value = value
    }

    fun setGreenPerKm(value: Double) = persist { ThresholdEditor.withGreenPerKm(it, value) }

    fun setRedPerKm(value: Double) = persist { ThresholdEditor.withRedPerKm(it, value) }

    fun setGreenPerHour(value: Double) = persist { ThresholdEditor.withGreenPerHour(it, value) }

    fun setRedPerHour(value: Double) = persist { ThresholdEditor.withRedPerHour(it, value) }

    /** Back to the city-seeded median floors (with their derived red floors). */
    fun resetToCityDefault() {
        val s = state.value
        viewModelScope.launch { settingsRepository.setThreshold(s.platform, s.cityDefault) }
    }

    private fun persist(fold: (PlatformThreshold) -> PlatformThreshold) {
        val s = state.value
        viewModelScope.launch { settingsRepository.setThreshold(s.platform, fold(s.threshold)) }
    }
}
