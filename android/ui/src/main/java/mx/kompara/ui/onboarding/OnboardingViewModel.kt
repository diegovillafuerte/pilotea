package mx.kompara.ui.onboarding

import android.content.Context
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

enum class OnboardingPage {
    PITCH, DISCLOSURE, ACCESSIBILITY, BATTERY, CAPTURE, DONE,
    /** 0.2.20's sign-in page, retired: kept only so a saved state naming it still deserializes. It restores as [PITCH]. */
    SIGNIN,
}
enum class OnboardingEntry { FIRST_RUN, RECOVERY, BATTERY }

data class PermissionReturnState(val ready: Boolean = false, val missing: Boolean = false) {
    fun returned(granted: Boolean) = PermissionReturnState(ready = granted, missing = !granted)
}

/** The VM owns the OS round trip, including a saved pending hand-off across process recreation. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val funnel: OnboardingFunnel,
    private val reader: ReaderReadinessRepository,
    private val saved: SavedStateHandle,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    init {
        // A saved page this build does not show (0.2.20's SIGNIN, or a value of another type) restores as the first page.
        if (saved.contains("page") && saved.get<Any?>("page").let { it !is OnboardingPage || it == OnboardingPage.SIGNIN })
            saved["page"] = OnboardingPage.PITCH
    }
    val page = saved.getStateFlow("page", OnboardingPage.PITCH)
    // PermissionReturnState is reconstructed from primitive saved values below, never parcelled.
    private val _return = MutableStateFlow(PermissionReturnState(missing = saved["missing"] ?: false))
    val permissionReturn = _return.asStateFlow()
    val readiness = reader.state
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var confirmation: Job? = null
    private var resumed = false
    private var completing = false
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    private var initialized = false
    private var busy = false
    private val entry get() = saved.get<OnboardingEntry>("entry") ?: OnboardingEntry.FIRST_RUN

    init {
        viewModelScope.launch {
            combine(ScreenReaderState.running, ScreenReaderState.silentLaneActive) { running, silent -> running || silent }
                .collect { active ->
                    if (active && resumed && page.value == OnboardingPage.CAPTURE && ReaderDeviceEnvironment.permissionGranted(context)) afterPermission()
                }
        }
    }
    fun start(entry: OnboardingEntry) {
        if (initialized) return
        initialized = true
        if (saved.contains("entry")) { _loaded.value = true; refresh(); return }
        saved["entry"] = entry
        viewModelScope.launch {
            val consent = settings.settings.first().readerDisclosureAccepted
            go(when (entry) {
                OnboardingEntry.FIRST_RUN -> OnboardingPage.PITCH
                OnboardingEntry.RECOVERY -> if (consent) OnboardingPage.ACCESSIBILITY else OnboardingPage.DISCLOSURE
                OnboardingEntry.BATTERY -> OnboardingPage.BATTERY
            })
            _loaded.value = true
            refresh()
        }
    }
    private fun go(next: OnboardingPage) {
        saved["page"] = next
        _error.value = null
        val step = when (next) {
            OnboardingPage.PITCH -> OnboardingStep.PITCH
            OnboardingPage.DISCLOSURE -> OnboardingStep.DISCLOSURE
            OnboardingPage.ACCESSIBILITY -> OnboardingStep.ACCESSIBILITY
            OnboardingPage.BATTERY -> OnboardingStep.OEM
            else -> null
        }
        if (step != null) viewModelScope.launch { funnel.record(step) }
    }
    fun begin() = go(OnboardingPage.DISCLOSURE)
    fun consent(accepted: Boolean) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                settings.setReaderDisclosureAccepted(accepted)
                funnel.record(if (accepted) OnboardingStep.DISCLOSURE_ACCEPTED else OnboardingStep.DISCLOSURE_DECLINED)
                if (accepted) { go(OnboardingPage.ACCESSIBILITY); refresh() } else finish()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "No pudimos guardar tu respuesta. Inténtalo otra vez."
            } finally { busy = false }
        }
    }
    fun openSettings() {
        val battery = page.value == OnboardingPage.BATTERY
        saved["pending"] = if (battery) "battery" else "permission"
        val opened = if (battery) BatterySettings.open(context) else AccessibilitySettings.open(context)
        if (!opened) { saved["pending"] = null; _error.value = "No pudimos abrir los ajustes. Inténtalo otra vez." }
    }
    fun beforeCapture() { saved["pending"] = "capture" }
    fun captureLaunchFailed() { saved["pending"] = null; _error.value = "No pudimos abrir el permiso. Inténtalo otra vez." }
    fun onPause() { resumed = false; confirmation?.cancel(); confirmation = null }
    fun onResume() { resumed = true; refresh() }
    private fun refresh() {
        val granted = ReaderDeviceEnvironment.permissionGranted(context)
        val battery = ReaderDeviceEnvironment.batteryRestricted(context)
        reader.observeEnvironment(granted, battery)
        reader.refresh()
        if (!resumed) return
        val pending = saved.get<String>("pending")
        saved["pending"] = null
        if (page.value == OnboardingPage.BATTERY && pending == "battery") { finish(); return }
        if (page.value == OnboardingPage.CAPTURE) {
            if (!granted) { go(OnboardingPage.ACCESSIBILITY); _return.value = PermissionReturnState(missing = true) }
            else if (ScreenReaderState.running.value || ScreenReaderState.silentLaneActive.value) afterPermission()
            else if (pending == "capture") _error.value = "Aún falta el permiso de pantalla. Puedes intentarlo otra vez."
            return
        }
        if (page.value != OnboardingPage.ACCESSIBILITY) return
        if (pending == "permission" || granted) {
            _return.value = _return.value.returned(granted)
            saved["missing"] = !granted
        }
        if (!granted) { confirmation?.cancel(); confirmation = null; _return.value = _return.value.copy(ready = false); return }
        if (confirmation?.isActive == true) return
        confirmation = viewModelScope.launch {
            delay(PERMISSION_CONFIRMATION_MS)
            // Android may have revoked it again while the confirmation was visible.
            if (resumed && ReaderDeviceEnvironment.permissionGranted(context)) {
                funnel.record(OnboardingStep.ACCESSIBILITY_GRANTED)
                if (needsCaptureConsent(Build.VERSION.SDK_INT, ScreenReaderState.silentLaneFailed.value,
                        ScreenReaderState.silentLaneActive.value, ScreenReaderState.running.value)) go(OnboardingPage.CAPTURE)
                else afterPermission()
            } else { _return.value = PermissionReturnState(missing = true); saved["missing"] = true }
        }
    }
    private fun afterPermission() {
        if (ReaderDeviceEnvironment.batteryRestricted(context)) go(OnboardingPage.BATTERY) else finish()
    }
    fun back() {
        when {
            entry != OnboardingEntry.FIRST_RUN -> finish()
            page.value == OnboardingPage.DISCLOSURE -> go(OnboardingPage.PITCH)
            page.value == OnboardingPage.ACCESSIBILITY -> { confirmation?.cancel(); go(OnboardingPage.DISCLOSURE) }
            else -> finish()
        }
    }
    fun finish() {
        if (completing) return
        completing = true
        confirmation?.cancel()
        viewModelScope.launch {
            try {
                if (entry == OnboardingEntry.FIRST_RUN) { settings.setOnboardingCompleted(true); funnel.record(OnboardingStep.DONE) }
                go(OnboardingPage.DONE)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.value = "No pudimos guardar. Inténtalo otra vez."
            } finally { completing = false }
        }
    }
    companion object {
        const val PERMISSION_CONFIRMATION_MS = 800L
        fun needsCaptureConsent(sdk: Int, failed: Boolean, silentActive: Boolean, projectionActive: Boolean) =
            (sdk < 30 || failed) && !silentActive && !projectionActive
    }
}

/** Local use never waits for the network or a session refresh. */
@HiltViewModel
class RootViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {
    val route = settingsRepository.settings.map { RootRouter.route(it.onboardingCompleted, null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootRoute.LOADING)
}
