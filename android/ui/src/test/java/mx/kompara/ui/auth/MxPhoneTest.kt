package mx.kompara.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** MX phone normalization into the E.164 form the backend expects. */
class MxPhoneTest {

    @Test
    fun `bare 10 digits normalize with the country code`() {
        assertEquals("+525512345678", MxPhone.normalizeOrNull("5512345678"))
    }

    @Test
    fun `spaces dashes and parentheses are tolerated`() {
        assertEquals("+525512345678", MxPhone.normalizeOrNull("55 1234 5678"))
        assertEquals("+525512345678", MxPhone.normalizeOrNull("(55) 1234-5678"))
    }

    @Test
    fun `existing country prefix is not doubled`() {
        assertEquals("+525512345678", MxPhone.normalizeOrNull("+52 55 1234 5678"))
        assertEquals("+525512345678", MxPhone.normalizeOrNull("525512345678"))
    }

    @Test
    fun `legacy +521 mobile prefix is collapsed`() {
        assertEquals("+525512345678", MxPhone.normalizeOrNull("+52 1 55 1234 5678"))
    }

    @Test
    fun `wrong lengths are rejected`() {
        assertNull(MxPhone.normalizeOrNull("551234567"))      // 9 digits
        assertNull(MxPhone.normalizeOrNull("55123456789"))    // 11 digits, no 52 prefix
        assertNull(MxPhone.normalizeOrNull(""))
        assertNull(MxPhone.normalizeOrNull("no soy un número"))
    }

    @Test
    fun `display formats the normalized number for humans`() {
        assertEquals("+52 55 1234 5678", MxPhone.display("+525512345678"))
    }
}
