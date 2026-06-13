package mx.kompara.ui.auth

import mx.kompara.data.model.City

/** Which screen of the signup flow is showing. */
enum class SignupStep { PHONE, CODE, PROFILE }

/** What went wrong, mapped to a Spanish string by the UI. */
enum class SignupError {
    /** The typed phone isn't a valid 10-digit MX number. */
    INVALID_PHONE,

    /** The OTP request didn't reach the backend (network/server). */
    REQUEST_FAILED,

    /** The backend rejected the code (wrong, expired, or too many attempts). */
    WRONG_CODE,

    /** The verify call failed before the backend could judge the code (network/server). */
    VERIFY_FAILED,

    /** PATCH /v1/me failed; the account exists, only the profile didn't save. */
    PROFILE_SAVE_FAILED,
}

/**
 * Render-ready state for the signup flow (phone → WhatsApp code → profile). Pure data so the
 * step transitions and input gating are unit-testable without Compose.
 */
data class SignupUiState(
    val step: SignupStep = SignupStep.PHONE,
    val phoneInput: String = "",
    val codeInput: String = "",
    val nameInput: String = "",
    val city: City = City.DEFAULT,
    val busy: Boolean = false,
    val error: SignupError? = null,
    /** Seconds until "Reenviar código" re-enables; 0 = available. */
    val resendSecondsLeft: Int = 0,
    /**
     * In debug builds, the fixed code that logs in offline (e.g. "000000"); null in release. The
     * code screen surfaces it as a hint so on-device testing needs no backend (TD-022).
     */
    val devCodeHint: String? = null,
) {
    /** The normalized E.164 phone, or null while the input isn't a valid MX number. */
    val phoneE164: String? get() = MxPhone.normalizeOrNull(phoneInput)

    val canSubmitPhone: Boolean get() = !busy && phoneE164 != null

    val canSubmitCode: Boolean get() = !busy && codeInput.length == CODE_LENGTH && codeInput.all(Char::isDigit)

    companion object {
        /** The backend issues 6-digit OTP codes. */
        const val CODE_LENGTH: Int = 6
    }
}
