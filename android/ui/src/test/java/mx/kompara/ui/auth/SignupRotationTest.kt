package mx.kompara.ui.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.ui.stats.AppClock
import mx.kompara.ui.theme.KomparaTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Composition replacement reproduces Activity rotation while retaining its destination ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
class SignupRotationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val releaseResponse = CompletableDeferred<Unit>()
    private val calls = AtomicInteger()
    private lateinit var http: HttpClient
    private lateinit var model: SignupViewModel

    private fun setup(step: SignupStep): StateRestorationTester {
        val prefs = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }
        val json = Json { ignoreUnknownKeys = true }
        http = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = Dispatchers.Main
            addHandler { request ->
            val body = when {
                request.url.encodedPath.endsWith("otp/request") -> {
                    calls.incrementAndGet(); releaseResponse.await(); """{"ok":true}"""
                }
                request.url.encodedPath.endsWith("otp/verify") -> {
                    calls.incrementAndGet(); releaseResponse.await()
                    """{"token":"session","driver":{"id":"driver","phone":"+525512345678","name":null,"city":"cdmx","tier":"free"}}"""
                }
                request.url.encodedPath.endsWith("devices/register") -> """{"ok":true}"""
                else -> error("Unexpected signup request: ${request.url.encodedPath}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        } })) { install(ContentNegotiation) { json(json) } }
        val auth = AuthRepository(prefs, ApiClient(http, "https://test.invalid", { "" }, { "install" }), json, devBypassEnabled = false)
        compose.runOnIdle {
            model = SignupViewModel(auth, SavedStateHandle(mapOf("step" to step, "phone" to "5512345678")), AppClock { System.currentTimeMillis() })
        }
        return StateRestorationTester(compose).also { restore ->
            restore.setContent { KomparaTheme { SignupFlowScreen({}, viewModel = model) } }
        }
    }

    @After fun cleanup() {
        if (::model.isInitialized) model.viewModelScope.cancel()
        if (::http.isInitialized) http.close()
    }

    private fun awaitResponse(condition: () -> Boolean) = compose.waitUntil(5_000) {
        // Compose's virtual clock does not drain Android's paused Handler dispatcher.
        shadowOf(android.os.Looper.getMainLooper()).idle()
        condition()
    }

    @Test fun `rotating while requesting OTP retains one pending request and reaches code entry`() {
        val restore = setup(SignupStep.PHONE)
        compose.runOnIdle { model.submitPhone() }
        awaitResponse { calls.get() == 1 }
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertTrue("Rotation must not cancel the OTP request", model.state.value.busy) }
        compose.runOnIdle { releaseResponse.complete(Unit) }
        awaitResponse { model.state.value.step == SignupStep.CODE }
        assertEquals(1, calls.get())
        assertEquals("5512345678", model.state.value.phoneInput)
    }

    @Test fun `rotating while verifying OTP keeps the response instead of requiring the used code again`() {
        val restore = setup(SignupStep.CODE)
        compose.runOnIdle { model.onCodeChange("123456") }
        awaitResponse { calls.get() == 1 }
        restore.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertTrue("Rotation must not cancel verification", model.state.value.busy) }
        compose.runOnIdle { releaseResponse.complete(Unit) }
        awaitResponse { model.state.value.step == SignupStep.DONE }
        assertEquals(1, calls.get())
        assertEquals("", model.state.value.codeInput)
    }
}
