package mx.kompara.ui.fixtures

import androidx.compose.runtime.Composable
import mx.kompara.ui.auth.*
import mx.kompara.ui.onboarding.*

/** Isolated render scenarios. OS Settings (A3s) is deliberately not a composable. */
@Composable fun OnboardingFixtureHost(frame: String) {
    when (frame) {
        "A1" -> PitchScreen({}, autoplay = false)
        "A2" -> DisclosureScreen({}, {})
        "A3", "A3n", "granted" -> AccessibilityGrantScreen(PermissionReturnState(ready = frame == "granted", missing = frame == "A3n"), {}, {})
        "A4", "A4b", "A4r" -> OemSurvivalScreen(frame != "A4b", {}, {}, recovered = frame == "A4r")
        "A6" -> SignupContent(SignupUiState(phoneInput = "55 1234 5678"), requestFocus = false)
        "A7", "auth-error" -> SignupContent(SignupUiState(step = SignupStep.CODE, phoneInput = "5512345678",
            codeInput = "381", resendSecondsLeft = 30, error = if (frame == "auth-error") SignupError.VERIFY_FAILED else null), requestFocus = false)
        "capture" -> CaptureConsentScreen({}, {})
    }
}
