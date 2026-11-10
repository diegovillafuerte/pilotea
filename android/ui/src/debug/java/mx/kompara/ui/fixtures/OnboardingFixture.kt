package mx.kompara.ui.fixtures

import androidx.compose.runtime.Composable
import mx.kompara.ui.auth.*
import mx.kompara.ui.onboarding.*

/** Sign-in and capture scenarios; the first run itself is P1–P4 in [SettingsFixture]. OS Settings (A3s) is not a composable. */
@Composable fun OnboardingFixtureHost(frame: String) {
    when (frame) {
        "A6" -> SignupContent(SignupUiState(phoneInput = "55 1234 5678"), requestFocus = false)
        "A7", "auth-error" -> SignupContent(SignupUiState(step = SignupStep.CODE, phoneInput = "5512345678",
            codeInput = "381", resendSecondsLeft = 30, error = if (frame == "auth-error") SignupError.VERIFY_FAILED else null), requestFocus = false)
        "capture" -> CaptureConsentScreen({}, {})
    }
}
