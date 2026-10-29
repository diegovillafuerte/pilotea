package mx.kompara.ui.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.ui.stats.AppClock
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignupViewModelTest {
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private val authPrefs = Memory()
    private val models = mutableListOf<SignupViewModel>()
    private lateinit var auth: AuthRepository
    private lateinit var http: HttpClient
    private var requests = 0
    private var verifies = 0
    private var failRequest = false
    private var failVerify = false
    private var wrongCode = false
    @Before fun setup() {
        val json = Json { ignoreUnknownKeys = true }
        http = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = Dispatchers.Main
            addHandler { request ->
            val path = request.url.encodedPath
            val body: String
            val status: HttpStatusCode
            when {
                path.endsWith("otp/request") -> { requests++; delay(50); body = "{\"ok\":true}"; status = if (failRequest) HttpStatusCode.InternalServerError else HttpStatusCode.OK }
                path.endsWith("otp/verify") -> { verifies++; delay(50)
                    body = """{"token":"session","driver":{"id":"driver","phone":"+525512345678","name":null,"city":"cdmx","tier":"free"}}"""
                    status = if (wrongCode) HttpStatusCode.Unauthorized else if (failVerify) HttpStatusCode.InternalServerError else HttpStatusCode.OK
                }
                path.endsWith("devices/register") -> { body = "{\"ok\":true}"; status = HttpStatusCode.OK }
                else -> error("Unexpected signup API: $path") // No profile save, backup or trial side effects.
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        } })) { install(ContentNegotiation) { json(json) } }
        auth = AuthRepository(authPrefs, ApiClient(http, "https://test.invalid", { "" }, { "install" }), json, devBypassEnabled = false)
    }
    @After fun cleanup() { models.forEach { it.viewModelScope.cancel() }; http.close(); Dispatchers.resetMain() }
    private fun TestScope.vm(saved: SavedStateHandle = SavedStateHandle()) = SignupViewModel(auth, saved, AppClock { testScheduler.currentTime }).also { models += it }
    private suspend fun TestScope.code(vm: SignupViewModel) {
        vm.onPhoneChange("5512345678"); vm.submitPhone(); runCurrent(); advanceTimeBy(50); runCurrent()
        assertEquals(SignupStep.CODE, vm.state.value.step)
    }
    @Test fun `sixth digit or paste auto submits once while input and retry are busy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val vm = vm(); runCurrent(); code(vm)
        vm.onCodeChange("12345"); runCurrent(); assertEquals(0, verifies)
        vm.onCodeChange("123456"); vm.onCodeChange("123456"); vm.submitCode(); runCurrent()
        assertEquals(1, verifies); assertTrue(vm.state.value.busy)
        advanceTimeBy(50); runCurrent(); assertEquals(SignupStep.DONE, vm.state.value.step)
        assertEquals("session", auth.currentToken())
    }
    @Test fun `network failure keeps code and gives explicit retry without repeated auto requests`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val vm = vm(); runCurrent(); code(vm)
        failVerify = true; vm.onCodeChange("12 34-56"); runCurrent(); advanceTimeBy(50); runCurrent()
        assertEquals(SignupError.VERIFY_FAILED, vm.state.value.error); assertEquals("123456", vm.state.value.codeInput)
        advanceTimeBy(2_000); runCurrent(); assertEquals(1, verifies)
        failVerify = false; vm.submitCode(); runCurrent(); advanceTimeBy(50); runCurrent()
        assertEquals(SignupStep.DONE, vm.state.value.step); assertEquals(2, verifies)
    }
    @Test fun `wrong code can be replaced and Unicode digits cannot trigger verification`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val vm = vm(); runCurrent(); code(vm)
        vm.onCodeChange("１２３４５６"); runCurrent(); assertEquals(0, verifies)
        wrongCode = true; vm.onCodeChange("123456"); runCurrent(); advanceTimeBy(50); runCurrent()
        assertEquals(SignupError.WRONG_CODE, vm.state.value.error)
        wrongCode = false; vm.onCodeChange("654321"); runCurrent(); advanceTimeBy(50); runCurrent()
        assertEquals(SignupStep.DONE, vm.state.value.step)
    }
    @Test fun `cooldown survives process recreation and failed resend does not restart it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val saved = SavedStateHandle(); val vm = vm(saved); runCurrent(); code(vm)
        vm.resendCode(); runCurrent(); assertEquals(1, requests)
        advanceTimeBy(10_000); runCurrent(); vm.viewModelScope.cancel()
        val restored = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })); runCurrent()
        assertEquals(SignupStep.CODE, restored.state.value.step); assertEquals(20, restored.state.value.resendSecondsLeft)
        assertEquals("5512345678", restored.state.value.phoneInput)
        advanceTimeBy(20_000); runCurrent(); assertEquals(0, restored.state.value.resendSecondsLeft)
        failRequest = true; restored.resendCode(); restored.resendCode(); runCurrent(); advanceTimeBy(50); runCurrent()
        assertEquals(2, requests); assertEquals(SignupError.REQUEST_FAILED, restored.state.value.error)
        assertEquals(0, restored.state.value.resendSecondsLeft)
        restored.changePhone(); assertEquals(SignupStep.PHONE, restored.state.value.step)
    }
    @Test fun `restored authenticated session returns immediately without profile or new OTP`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val vm = vm(); runCurrent(); code(vm)
        vm.onCodeChange("123456"); runCurrent(); advanceTimeBy(50); runCurrent()
        val next = vm(); runCurrent(); assertEquals(SignupStep.DONE, next.state.value.step)
        assertEquals(1, requests); assertEquals(1, verifies)
    }
    @Test fun `closing and reopening optional sign in preserves a running cooldown without stuck busy`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler)); val vm = vm(); runCurrent(); code(vm)
        vm.onCodeChange("123456"); vm.cancel(); runCurrent()
        assertFalse(vm.state.value.busy)
        advanceTimeBy(5_000); vm.resume(); runCurrent()
        assertEquals(25, vm.state.value.resendSecondsLeft)
        vm.changePhone(); vm.submitPhone(); vm.cancel(); runCurrent()
        assertFalse(vm.state.value.busy); assertEquals(SignupStep.PHONE, vm.state.value.step)
    }

}
