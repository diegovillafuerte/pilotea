package mx.kompara.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Input gating for the signup steps (pure state, no Compose). */
class SignupUiStateTest {

    @Test
    fun `phone submit enables only on a valid MX number and not while busy`() {
        assertFalse(SignupUiState(phoneInput = "55").canSubmitPhone)
        assertTrue(SignupUiState(phoneInput = "5512345678").canSubmitPhone)
        assertFalse(SignupUiState(phoneInput = "5512345678", busy = true).canSubmitPhone)
    }

    @Test
    fun `phoneE164 mirrors the normalizer`() {
        assertEquals("+525512345678", SignupUiState(phoneInput = "55 1234 5678").phoneE164)
        assertNull(SignupUiState(phoneInput = "123").phoneE164)
    }

    @Test
    fun `code submit needs exactly six digits`() {
        assertFalse(SignupUiState(codeInput = "12345").canSubmitCode)
        assertTrue(SignupUiState(codeInput = "123456").canSubmitCode)
        assertFalse(SignupUiState(codeInput = "12345a").canSubmitCode)
        assertFalse(SignupUiState(codeInput = "123456", busy = true).canSubmitCode)
    }
}
