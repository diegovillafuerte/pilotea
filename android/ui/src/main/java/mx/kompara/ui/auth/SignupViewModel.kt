package mx.kompara.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.kompara.data.model.City
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.auth.SessionState
import javax.inject.Inject

/**
 * Drives the required signup flow: phone → WhatsApp OTP → profile (name + city).
 *
 * The account itself is created server-side on OTP verification (find-or-create by phone), which
 * also merges the install's anonymous device id so locally captured data attaches to the account.
 * The profile step is a follow-up PATCH; its city choice is mirrored into [SettingsRepository] so
 * the benchmarks/percentiles surfaces use the same city without asking twice.
 *
 * Re-entry: if a previous attempt already authenticated (app killed mid-flow), the flow resumes at
 * the profile step instead of asking for the phone again.
 */
@HiltViewModel
class SignupViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    private var resendJob: Job? = null

    init {
        viewModelScope.launch {
            val session = auth.sessionState.first()
            if (session is SessionState.Authenticated) {
                _state.update {
                    it.copy(
                        step = SignupStep.PROFILE,
                        nameInput = session.driver.name.orEmpty(),
                        city = City.fromKey(session.driver.city) ?: City.DEFAULT,
                    )
                }
            }
        }
    }

    fun onPhoneChange(value: String) {
        _state.update { it.copy(phoneInput = value, error = null) }
    }

    fun onCodeChange(value: String) {
        val digits = value.filter(Char::isDigit).take(SignupUiState.CODE_LENGTH)
        _state.update { it.copy(codeInput = digits, error = null) }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(nameInput = value, error = null) }
    }

    fun onCityChange(value: City) {
        _state.update { it.copy(city = value, error = null) }
    }

    /** Request the WhatsApp code and advance to the code screen. */
    fun submitPhone() {
        val phone = _state.value.phoneE164
        if (phone == null) {
            _state.update { it.copy(error = SignupError.INVALID_PHONE) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.requestOtp(phone) }
                .onSuccess {
                    _state.update { it.copy(busy = false, step = SignupStep.CODE, codeInput = "") }
                    startResendCooldown()
                }
                .onFailure {
                    _state.update { it.copy(busy = false, error = SignupError.REQUEST_FAILED) }
                }
        }
    }

    /** Re-send the code (rate-limited locally by the cooldown; the backend rate-limits too). */
    fun resendCode() {
        if (_state.value.resendSecondsLeft > 0 || _state.value.busy) return
        val phone = _state.value.phoneE164 ?: return
        viewModelScope.launch {
            runCatching { auth.requestOtp(phone) }
                .onFailure { _state.update { it.copy(error = SignupError.REQUEST_FAILED) } }
            startResendCooldown()
        }
    }

    /** Go back from the code screen to correct the phone number. */
    fun changePhone() {
        resendJob?.cancel()
        _state.update { it.copy(step = SignupStep.PHONE, codeInput = "", error = null, resendSecondsLeft = 0) }
    }

    /** Verify the code; on success the session persists and the flow advances to the profile. */
    fun submitCode() {
        val s = _state.value
        val phone = s.phoneE164 ?: return
        if (!s.canSubmitCode) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.verifyOtp(phone, s.codeInput) }
                .onSuccess { driver ->
                    resendJob?.cancel()
                    _state.update {
                        it.copy(
                            busy = false,
                            step = SignupStep.PROFILE,
                            nameInput = driver.name.orEmpty(),
                            city = City.fromKey(driver.city) ?: it.city,
                        )
                    }
                }
                .onFailure { e ->
                    val kind = if ((e as? ApiException)?.status == 401) {
                        SignupError.WRONG_CODE
                    } else {
                        SignupError.VERIFY_FAILED
                    }
                    _state.update { it.copy(busy = false, error = kind) }
                }
        }
    }

    /** Save name + city to the account, mirror the city into settings, then finish. */
    fun saveProfile(onDone: () -> Unit) {
        val s = _state.value
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                auth.updateProfile(
                    name = s.nameInput.trim().ifEmpty { null },
                    city = s.city.key,
                )
                settings.setCity(s.city)
            }
                .onSuccess {
                    _state.update { it.copy(busy = false) }
                    onDone()
                }
                .onFailure {
                    _state.update { it.copy(busy = false, error = SignupError.PROFILE_SAVE_FAILED) }
                }
        }
    }

    /** Skip the profile step — the account already exists; the profile is editable later. */
    fun skipProfile(onDone: () -> Unit) {
        onDone()
    }

    private fun startResendCooldown() {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            _state.update { it.copy(resendSecondsLeft = RESEND_COOLDOWN_SECONDS) }
            while (_state.value.resendSecondsLeft > 0) {
                delay(1_000)
                _state.update { it.copy(resendSecondsLeft = it.resendSecondsLeft - 1) }
            }
        }
    }

    private companion object {
        const val RESEND_COOLDOWN_SECONDS = 30
    }
}
