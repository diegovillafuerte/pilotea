package mx.kompara.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity
import org.robolectric.util.ReflectionHelpers
import java.time.Clock

/**
 * The launch notices' permission: the system's own dialog, once per install, after a first run that left the reader on.
 * The ask is recorded from the dialog's answer, never before it, and survives a configuration change in flight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationRequestTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private val settings = SettingsRepository(Memory())
    private val reader = ReaderReadinessRepository(object : ReaderMemoryStore {
        override fun load() = ReaderMemory()
        override fun save(memory: ReaderMemory) = Unit
    }, Clock.systemUTC()).apply { observeEnvironment(true, false) }
    private var asked = 0
    private var visit by mutableStateOf(0)
    private fun requested() = shadowOf(compose.activity).lastRequestedPermission?.requestedPermissions?.toList()

    /** Ganancias' wiring: eligibility from the stored flag (false until loaded), and the flag stored when the dialog answers. */
    @Composable private fun Ganancias() {
        val scope = rememberCoroutineScope()
        val eligible by remember { settings.notificationsAsked.map { NotificationAsk.eligible(true, it, reader.state.value) } }
            .collectAsState(false)
        key(visit) { NotificationPermissionRequest(eligible) { asked++; scope.launch { settings.setNotificationsAsked() } } }
    }
    private fun show() = compose.setContent { Ganancias() }
    private fun stored() = runBlocking { settings.notificationsAsked.first() }

    /** The system dialog's answer, as Android delivers it to the activity that launched the request. */
    private fun answer(request: ShadowActivity.PermissionsRequest, granted: Boolean) = compose.runOnIdle {
        @Suppress("DEPRECATION")
        compose.activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
            intArrayOf(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED))
    }

    @Test fun `asks once after a first run that left the reader on, records it when answered, and not on the next visit`() {
        show(); compose.waitForIdle()
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requested())
        assertEquals("Nothing is recorded while the dialog is up", 0, asked)
        assertFalse(stored())
        answer(shadowOf(compose.activity).lastRequestedPermission!!, granted = false); compose.waitForIdle()
        assertEquals("A decline is an answer", 1, asked)
        assertTrue(stored())
        val first = shadowOf(compose.activity).lastRequestedPermission
        compose.runOnIdle { visit++ }; compose.waitForIdle()
        assertSame("The next visit does not ask again", first, shadowOf(compose.activity).lastRequestedPermission)
        assertEquals(1, asked)
    }

    @Test fun `a process death while the dialog is up leaves the ask unrecorded, so the next visit asks again`() {
        show(); compose.waitForIdle()
        assertNotNull("The dialog is up", shadowOf(compose.activity).lastRequestedPermission)
        // The process dies here: no answer and no saved state survive, only the store. A fresh process reads it.
        assertFalse("Nothing was recorded before the answer", stored())
        assertEquals(0, asked)
        assertTrue("So the next visit is eligible again", NotificationAsk.eligible(true, stored(), reader.state.value))
    }

    @Test fun `a configuration change while the dialog is up launches no second request and still records the answer`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Ganancias() }
        compose.waitForIdle()
        val first = shadowOf(compose.activity).lastRequestedPermission!!
        restoration.emulateSavedInstanceStateRestore(); compose.waitForIdle()
        assertSame("The recreated screen does not launch a second dialog", first, shadowOf(compose.activity).lastRequestedPermission)
        answer(first, granted = true); compose.waitForIdle()
        assertEquals("The answer reaches the recreated screen", 1, asked)
        assertTrue(stored())
    }
    @Test fun `does not ask when the permission is already granted`() {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show(); compose.waitForIdle()
        assertEquals(0, asked); assertNull(requested())
    }
    @Test fun `does not ask below Android 13`() {
        val sdk = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 32)
        try { show(); compose.waitForIdle() } finally { ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", sdk) }
        assertEquals(0, asked); assertNull(requested())
    }
    @Test fun `only a finished first run with the reader on is eligible`() {
        val on = reader.state.value
        assertTrue(NotificationAsk.eligible(true, false, on))
        assertTrue(NotificationAsk.eligible(true, false, on.copy(phase = ReaderPhase.ATTENTION, batteryRestricted = true)))
        assertFalse("first run not finished", NotificationAsk.eligible(false, false, on))
        assertFalse("asked before, declined or not", NotificationAsk.eligible(true, true, on))
        assertFalse("reader off", NotificationAsk.eligible(true, false, on.copy(permission = false, phase = ReaderPhase.OFF)))
        assertFalse("reader stopped", NotificationAsk.eligible(true, false, on.copy(phase = ReaderPhase.PAUSED)))
        assertFalse("permission not known yet", NotificationAsk.eligible(true, false, on.copy(permission = null)))
    }
}
