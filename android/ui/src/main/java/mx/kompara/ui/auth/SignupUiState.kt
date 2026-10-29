package mx.kompara.ui.auth

enum class SignupStep { PHONE, CODE, DONE }
enum class SignupError { INVALID_PHONE, REQUEST_FAILED, WRONG_CODE, VERIFY_FAILED }

/** Optional identity task; no profile or trial starts as a side effect of verification. */
data class SignupUiState(
    val step: SignupStep = SignupStep.PHONE,
    val phoneInput: String = "",
    val codeInput: String = "",
    val busy: Boolean = false,
    val error: SignupError? = null,
    val resendSecondsLeft: Int = 0,
    val devCodeHint: String? = null,
) {
    val phoneE164: String? get() = MxPhone.normalizeOrNull(phoneInput)
    val canSubmitPhone get() = !busy && phoneE164 != null
    val canSubmitCode get() = !busy && codeInput.length == CODE_LENGTH && codeInput.all { it in '0'..'9' }
    companion object { const val CODE_LENGTH = 6 }
}
