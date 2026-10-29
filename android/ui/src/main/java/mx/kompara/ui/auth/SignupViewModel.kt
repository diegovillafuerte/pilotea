package mx.kompara.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.auth.SessionState
import mx.kompara.ui.stats.AppClock
import javax.inject.Inject

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val saved: SavedStateHandle,
    private val clock: AppClock,
) : ViewModel() {
    private val _state = MutableStateFlow(SignupUiState(
        step = saved["step"] ?: SignupStep.PHONE, phoneInput = saved["phone"] ?: "", codeInput = saved["code"] ?: "",
        devCodeHint = auth.devBypassCode))
    val state = _state.asStateFlow()
    private var resendJob: Job? = null
    private var request: Job? = null
    init {
        viewModelScope.launch {
            if (auth.sessionState.first() is SessionState.Authenticated) update { it.copy(step = SignupStep.DONE) }
            else if (_state.value.step == SignupStep.DONE) update { it.copy(step = SignupStep.PHONE) }
        }
        if (_state.value.step == SignupStep.CODE) countdown()
    }
    private fun update(change: (SignupUiState) -> SignupUiState) {
        _state.update(change)
        saved["step"] = _state.value.step; saved["phone"] = _state.value.phoneInput; saved["code"] = _state.value.codeInput
    }
    fun onPhoneChange(value: String) { if (!_state.value.busy) update { it.copy(phoneInput = value, error = null) } }
    fun onCodeChange(value: String) {
        if (_state.value.busy) return
        val digits = value.filter { it in '0'..'9' }.take(SignupUiState.CODE_LENGTH)
        val changed = digits != _state.value.codeInput
        update { it.copy(codeInput = digits, error = null) }
        if (changed && _state.value.canSubmitCode) submitCode()
    }
    fun submitPhone() {
        if (_state.value.busy) return
        val phone = _state.value.phoneE164
        if (phone == null) { update { it.copy(error = SignupError.INVALID_PHONE) }; return }
        send(phone)
    }
    private fun send(phone: String) {
        update { it.copy(busy = true, error = null) }
        request = viewModelScope.launch {
            try {
                auth.requestOtp(phone)
                saved["resendAt"] = clock.nowMs() + 30_000
                update { it.copy(busy = false, step = SignupStep.CODE, codeInput = "") }
                countdown()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                update { it.copy(busy = false, error = SignupError.REQUEST_FAILED) }
            }
        }
    }
    fun resendCode() {
        if (_state.value.resendSecondsLeft > 0 || _state.value.busy) return
        _state.value.phoneE164?.let(::send)
    }
    fun changePhone() {
        request?.cancel(); resendJob?.cancel(); saved["resendAt"] = 0L
        update { it.copy(step = SignupStep.PHONE, codeInput = "", busy = false, error = null, resendSecondsLeft = 0) }
    }
    fun submitCode() {
        val s = _state.value
        val phone = s.phoneE164 ?: return
        if (!s.canSubmitCode) return
        update { it.copy(busy = true, error = null) }
        request = viewModelScope.launch {
            try {
                auth.verifyOtp(phone, s.codeInput)
                resendJob?.cancel()
                update { it.copy(busy = false, step = SignupStep.DONE, codeInput = "") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                update { it.copy(busy = false, error = if ((e as? ApiException)?.status == 401) SignupError.WRONG_CODE else SignupError.VERIFY_FAILED) }
            }
        }
    }
    fun resume() { if (_state.value.step == SignupStep.CODE) countdown() }
    fun cancel() {
        request?.cancel(); resendJob?.cancel()
        update { it.copy(busy = false) }
    }
    private fun countdown() {
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            do {
                val seconds = (((saved.get<Long>("resendAt") ?: 0L) - clock.nowMs() + 999) / 1_000).coerceIn(0, 30).toInt()
                update { it.copy(resendSecondsLeft = seconds) }
                if (seconds == 0) break
                delay(1_000)
            } while (isActive)
        }
    }
}
