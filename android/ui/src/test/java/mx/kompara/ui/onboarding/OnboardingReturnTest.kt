package mx.kompara.ui.onboarding

import android.app.ActivityManager
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.time.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingReturnTest {
    private class Memory : DataStore<Preferences> {
        var fail = false
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (fail) error("disk full")
            return transform(data.value).also { data.value = it }
        }
    }
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = Memory()
    private val settings = SettingsRepository(prefs)
    private val records = mutableListOf<OnboardingStep>()
    private val models = mutableListOf<OnboardingViewModel>()
    private val reader = ReaderReadinessRepository(object : ReaderMemoryStore {
        override fun load() = ReaderMemory()
        override fun save(memory: ReaderMemory) {}
    }, Clock.fixed(Instant.parse("2026-10-19T20:40:00Z"), ZoneId.of("America/Mexico_City")))
    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(settings, object : OnboardingFunnel {
        override suspend fun record(step: OnboardingStep) { records += step }
    }, reader, saved, context).also { models += it }
    private fun grant(value: Boolean) {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (value) 1 else 0)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            if (value) ReaderDeviceEnvironment.component(context).flattenToString() else "")
    }
    private fun support(intent: Intent) {
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "Settings"; exported = true }
        })
    }
    @Before fun setup() {
        ScreenReaderState.setRunning(false); ScreenReaderState.setSilentLaneActive(false); ScreenReaderState.setSilentLaneFailed(false)
        grant(false)
    }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    @Test fun `a saved page this build no longer shows restores as the first page`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // The bytes 0.2.20 parcelled for its sign-in page: the enum OnboardingPage, constant SIGNIN, by name.
        val bytes = java.io.ByteArrayOutputStream().also { java.io.ObjectOutputStream(it).use { out -> out.writeObject(OnboardingPage.DONE) } }.toByteArray()
        val done = byteArrayOf(0x74, 0, 4) + "DONE".toByteArray()
        val at = (0..bytes.size - done.size).first { i -> done.indices.all { bytes[i + it] == done[it] } }
        val signIn = bytes.copyOfRange(0, at) + byteArrayOf(0x74, 0, 6) + "SIGNIN".toByteArray() + bytes.copyOfRange(at + done.size, bytes.size)
        val restored = java.io.ObjectInputStream(signIn.inputStream()).use { it.readObject() }
        for (page in listOf(restored, "SIGNIN", 3)) {
            val vm = vm(SavedStateHandle(mapOf("entry" to OnboardingEntry.FIRST_RUN, "page" to page)))
            vm.start(OnboardingEntry.FIRST_RUN); vm.onResume(); runCurrent()
            assertEquals(OnboardingPage.PITCH, vm.page.value)
            vm.begin(); runCurrent()
            assertEquals(OnboardingPage.DISCLOSURE, vm.page.value)
        }
    }
    @Test fun `short run has no account cost profile limited or done detour`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(); vm.start(OnboardingEntry.FIRST_RUN); vm.onResume(); runCurrent()
        assertEquals(OnboardingPage.PITCH, vm.page.value)
        vm.begin(); vm.consent(false); runCurrent()
        assertTrue(settings.isOnboardingCompleted()); assertEquals(OnboardingPage.DONE, vm.page.value)
        assertFalse(settings.settings.first().readerDisclosureAccepted)
        assertFalse(records.contains(OnboardingStep.SIGNUP)); assertFalse(records.contains(OnboardingStep.ACCESSIBILITY))
    }
    @Test fun `permission is acknowledged for 800ms without service binding or reading proof`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(); vm.start(OnboardingEntry.FIRST_RUN); vm.onResume(); runCurrent()
        vm.begin(); vm.consent(true); runCurrent()
        assertTrue(settings.settings.first().readerDisclosureAccepted)
        support(AccessibilitySettings.candidates(context).last()); vm.openSettings(); vm.onPause()
        grant(true); vm.onResume(); runCurrent()
        assertTrue(vm.permissionReturn.value.ready); assertFalse(reader.state.value.bound)
        advanceTimeBy(799); runCurrent(); assertEquals(OnboardingPage.ACCESSIBILITY, vm.page.value)
        advanceTimeBy(1); runCurrent(); assertEquals(OnboardingPage.DONE, vm.page.value)
        assertEquals(ReaderPhase.PREPARED, reader.state.value.phase)
        assertEquals(1, records.count { it == OnboardingStep.ACCESSIBILITY_GRANTED })
    }
    @Test fun `declined return survives recreation then retry grants and returns to recovery caller`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.setReaderDisclosureAccepted(true)
        val saved = SavedStateHandle(); val first = vm(saved)
        first.start(OnboardingEntry.RECOVERY); first.onResume(); runCurrent()
        support(AccessibilitySettings.candidates(context).last()); first.openSettings(); first.onPause()
        first.viewModelScope.cancel()
        val restored = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        restored.start(OnboardingEntry.RECOVERY); restored.onResume(); runCurrent()
        assertTrue(restored.permissionReturn.value.missing)
        assertEquals(OnboardingPage.ACCESSIBILITY, restored.page.value)
        restored.openSettings(); restored.onPause(); grant(true); restored.onResume(); runCurrent()
        advanceTimeBy(800); runCurrent()
        assertEquals(OnboardingPage.DONE, restored.page.value)
        assertFalse(settings.isOnboardingCompleted()) // Recovery does not rewrite first-run completion.
        assertFalse(records.contains(OnboardingStep.DISCLOSURE))
    }
    @Test fun `battery step is conditional and returning from battery never grants accessibility`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        shadowOf(context.getSystemService(ActivityManager::class.java)).setBackgroundRestricted(true)
        grant(true)
        val vm = vm(); vm.start(OnboardingEntry.FIRST_RUN); vm.onResume(); runCurrent()
        vm.begin(); vm.consent(true); runCurrent(); advanceTimeBy(800); runCurrent()
        assertEquals(OnboardingPage.BATTERY, vm.page.value)
        support(BatterySettings.candidates(context).first()); vm.openSettings(); vm.onPause()
        grant(false); vm.onResume(); runCurrent()
        assertEquals(OnboardingPage.DONE, vm.page.value)
        assertEquals(false, reader.state.value.permission)
    }
    @Test fun `battery only entry preserves off state and exits to its opener`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(); vm.start(OnboardingEntry.BATTERY); vm.onResume(); runCurrent()
        assertEquals(OnboardingPage.BATTERY, vm.page.value); assertEquals(false, reader.state.value.permission)
        vm.back(); runCurrent(); assertEquals(OnboardingPage.DONE, vm.page.value)
        assertFalse(settings.isOnboardingCompleted()); assertFalse(records.contains(OnboardingStep.ACCESSIBILITY_GRANTED))
    }
    @Test fun `background and permission revocation cancel the success transition`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.setReaderDisclosureAccepted(true); grant(true)
        val vm = vm(); vm.start(OnboardingEntry.RECOVERY); vm.onResume(); runCurrent()
        advanceTimeBy(400); vm.onPause(); advanceTimeBy(1_000); runCurrent()
        assertEquals(OnboardingPage.ACCESSIBILITY, vm.page.value)
        grant(false); vm.onResume(); runCurrent(); advanceTimeBy(800); runCurrent()
        assertFalse(vm.permissionReturn.value.ready); assertEquals(OnboardingPage.ACCESSIBILITY, vm.page.value)
        assertFalse(records.contains(OnboardingStep.ACCESSIBILITY_GRANTED))
    }
    @Test fun `disclosure persistence failure cannot open permission and is retryable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = vm(); vm.start(OnboardingEntry.RECOVERY); vm.onResume(); runCurrent()
        prefs.fail = true; vm.consent(true); runCurrent()
        assertEquals(OnboardingPage.DISCLOSURE, vm.page.value); assertNotNull(vm.error.value)
        prefs.fail = false; vm.consent(true); runCurrent()
        assertEquals(OnboardingPage.ACCESSIBILITY, vm.page.value)
    }
    @Test fun `measured silent failure needs capture consent and delayed projection start completes it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.setReaderDisclosureAccepted(true); grant(true); ScreenReaderState.setSilentLaneFailed(true)
        val vm = vm(); vm.start(OnboardingEntry.RECOVERY); vm.onResume(); runCurrent(); advanceTimeBy(800); runCurrent()
        assertEquals(OnboardingPage.CAPTURE, vm.page.value)
        vm.beforeCapture(); vm.onPause(); vm.onResume(); runCurrent()
        assertEquals(OnboardingPage.CAPTURE, vm.page.value); assertNotNull(vm.error.value)
        ScreenReaderState.setRunning(true); runCurrent(); assertEquals(OnboardingPage.DONE, vm.page.value)
        assertTrue(OnboardingViewModel.needsCaptureConsent(29, false, false, false))
        assertFalse(OnboardingViewModel.needsCaptureConsent(34, false, false, false)) // Not yet bound is not failure.
        assertFalse(OnboardingViewModel.needsCaptureConsent(34, true, true, false))
    }
    @Test fun `settings resolves service page or list and posts the matching long native toast`() {
        val candidates = AccessibilitySettings.candidates(context)
        support(candidates.last())
        assertTrue(AccessibilitySettings.open(context))
        assertEquals("Busca «Kompara» y activa el interruptor", ShadowToast.getTextOfLatestToast())
        support(candidates.first())
        assertTrue(AccessibilitySettings.open(context))
        assertEquals("Activa el interruptor", ShadowToast.getTextOfLatestToast())
        assertEquals(ReaderDeviceEnvironment.component(context).flattenToString(), candidates.first().getStringExtra(Intent.EXTRA_COMPONENT_NAME))
        assertEquals(android.widget.Toast.LENGTH_LONG, ShadowToast.getLatestToast().duration)
    }
    @Test fun `failed deep launch falls back and battery targets app restriction rather than Doze`() {
        AccessibilitySettings.candidates(context).forEach(::support)
        val launches = mutableListOf<String?>()
        val rejecting = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                launches += intent.action
                if (intent.action == AccessibilitySettings.DETAILS_ACTION) throw ActivityNotFoundException()
            }
        }
        assertTrue(AccessibilitySettings.open(rejecting))
        assertEquals(listOf(AccessibilitySettings.DETAILS_ACTION, Settings.ACTION_ACCESSIBILITY_SETTINGS), launches)
        assertEquals("Busca «Kompara» y activa el interruptor", ShadowToast.getTextOfLatestToast())
        val battery = BatterySettings.candidates(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, battery.first().action)
        assertEquals("package:${context.packageName}", battery.first().data.toString())
        assertFalse(battery.any { it.action == Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS })
    }
    @Test fun `missing Settings handler remains retryable and never looks like a completed return`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.setReaderDisclosureAccepted(true)
        val vm = vm(); vm.start(OnboardingEntry.RECOVERY); vm.onResume(); runCurrent()
        vm.openSettings(); vm.onResume(); runCurrent()
        assertNotNull(vm.error.value); assertEquals(OnboardingPage.ACCESSIBILITY, vm.page.value)
        assertFalse(vm.permissionReturn.value.ready)
        vm.finish(); runCurrent(); assertEquals(OnboardingPage.DONE, vm.page.value)
    }
}
