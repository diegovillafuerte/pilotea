package mx.kompara.ui.onboarding

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.theme.KomparaTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val prefs = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private val settings = SettingsRepository(prefs)
    private val models = mutableListOf<OnboardingViewModel>()
    private lateinit var nav: NavHostController
    private fun model(context: Context) = OnboardingViewModel(settings, object : OnboardingFunnel {
        override suspend fun record(step: OnboardingStep) {}
    }, ReaderReadinessRepository(object : ReaderMemoryStore {
        override fun load() = ReaderMemory()
        override fun save(memory: ReaderMemory) {}
    }, Clock.systemUTC()), SavedStateHandle(), context).also { models += it }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() } }
    @Test fun `recovery skip and battery back return to their actual saved navigation opener`() {
        runBlocking { settings.setReaderDisclosureAccepted(true) }
        Settings.Secure.putInt(compose.activity.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        compose.setContent { KomparaTheme {
            nav = rememberNavController()
            NavHost(nav, "hoy") {
                composable("hoy") { Button({ nav.navigate("reader-recovery") }) { Text("Hoy: activar") } }
                composable("semaforo") { Button({ nav.navigate("reader-battery") }) { Text("Semáforo: batería") } }
                composable("reader-recovery") {
                    val context = LocalContext.current
                    val vm = remember { model(context) }
                    OnboardingNavGraph({ nav.popBackStack() }, viewModel = vm, entry = OnboardingEntry.RECOVERY)
                }
                composable("reader-battery") {
                    val context = LocalContext.current
                    val vm = remember { model(context) }
                    OnboardingNavGraph({ nav.popBackStack() }, viewModel = vm, entry = OnboardingEntry.BATTERY)
                }
            }
        } }
        compose.onNodeWithText("Hoy: activar").performClick()
        compose.onNodeWithText("Enciende Kompara en Accesibilidad").assertExists()
        compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Hoy: activar").assertExists()
        compose.runOnIdle { assertEquals("hoy", nav.currentDestination?.route); nav.navigate("semaforo") }
        compose.onNodeWithText("Semáforo: batería").performClick()
        compose.onNodeWithText("Semáforo apagado · aún falta el permiso").assertExists()
        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.onNodeWithText("Semáforo: batería").assertExists()
        compose.runOnIdle { assertEquals("semaforo", nav.currentDestination?.route) }
    }
}
