package mx.kompara.overlay.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.model.Platform
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.CostProfile
import mx.kompara.metrics.CostProfileMapper
import mx.kompara.overlay.ThresholdSheet
import mx.kompara.overlay.VerdictChipState
import mx.kompara.parsers.snapshot.DemoSnapshots
import javax.inject.Inject

/**
 * Drives the offer simulator screen. It combines the driver's live [Settings] (thresholds) and
 * [CostProfileEntity] (costs) with the local UI choices (platform toggle, current step) and replays
 * the demo offers through [SimulatorEngine] on every change — so the screen always reflects the
 * driver's *real* config. Threshold edits persist through [SettingsRepository], identical to the
 * floating overlay's quick-threshold sheet, so the simulator is a faithful preview, not a sandbox.
 */
@HiltViewModel
class SimulatorViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val costProfileRepository: CostProfileRepository,
) : ViewModel() {

    private val engine = SimulatorEngine.bundled()

    // Local UI choices.
    private val platform = MutableStateFlow(Platform.UBER)
    private val stepIndex = MutableStateFlow(0)

    // Latest persisted config; null until the repositories first emit.
    @Volatile private var latestSettings: Settings = Settings.DEFAULT
    @Volatile private var latestCostEntity: CostProfileEntity? = null

    private val _state = MutableStateFlow(SimulatorUiState.initial())
    val state: StateFlow<SimulatorUiState> = _state.asStateFlow()

    init {
        // Recompute whenever persisted config OR a local UI choice changes.
        combine(
            settingsRepository.settings,
            costProfileRepository.profile,
            platform,
            stepIndex,
        ) { settings, costEntity, plat, step ->
            latestSettings = settings
            latestCostEntity = costEntity
            recompute(plat, step)
        }
            .onEach { _state.value = it }
            .launchIn(viewModelScope)
    }

    /** Flip between the Uber and DiDi demo decks; reset to the first step of the new deck. */
    fun selectPlatform(target: Platform) {
        if (target == platform.value) return
        platform.value = target
        stepIndex.value = 0
    }

    /** Advance the guided script to the next offer (clamped to the last). */
    fun next() {
        val max = _state.value.stepCount - 1
        if (max >= 0) stepIndex.value = (stepIndex.value + 1).coerceAtMost(max)
    }

    /** Step the guided script back (clamped to the first). */
    fun previous() {
        stepIndex.value = (stepIndex.value - 1).coerceAtLeast(0)
    }

    /** Jump directly to a step (e.g. tapping a step dot). */
    fun goToStep(index: Int) {
        val max = (_state.value.stepCount - 1).coerceAtLeast(0)
        stepIndex.value = index.coerceIn(0, max)
    }

    /**
     * The threshold-playground slider moved. Persist the new green $/km floor for the current
     * platform via the shared [SettingsRepository] (the combine above then re-grades all three
     * offers live). The red floor follows the green one down if they would cross.
     */
    fun setPerKmFloor(perKm: Double) {
        val plat = platform.value
        val current = latestSettings.thresholdFor(plat)
        val updated = ThresholdSheet.withGreenPerKm(current, perKm)
        viewModelScope.launch { settingsRepository.setThreshold(plat, updated) }
    }

    private fun recompute(plat: Platform, step: Int): SimulatorUiState {
        val offers = demoOffersFor(plat)
        val costProfile: CostProfile = CostProfileMapper.toCostProfileOrZero(latestCostEntity)
        val threshold: PlatformThreshold = latestSettings.thresholdFor(plat)

        val steps = offers.map { offer ->
            val result = engine.evaluate(offer, costProfile, threshold)
            SimulatorStep(
                id = offer.id,
                shape = offer.shape,
                chipState = VerdictChipState.from(result.metrics),
                visibleText = result.visibleText,
                result = result,
            )
        }
        val clampedStep = step.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
        return SimulatorUiState(
            platform = plat,
            stepIndex = clampedStep,
            offers = steps,
            threshold = threshold,
        )
    }

    private fun demoOffersFor(plat: Platform): List<DemoSnapshots.DemoOffer> = when (plat) {
        Platform.DIDI -> DemoSnapshots.DIDI
        else -> DemoSnapshots.UBER
    }
}
