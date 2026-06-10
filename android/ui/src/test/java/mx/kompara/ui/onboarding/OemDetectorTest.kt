package mx.kompara.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Build.MANUFACTURER -> OemProfile mapping table (B-036). */
class OemDetectorTest {

    @Test
    fun `xiaomi family maps to XIAOMI`() {
        listOf("Xiaomi", "xiaomi", "Redmi", "POCO", "poco").forEach {
            assertEquals("manufacturer=$it", OemProfile.XIAOMI, OemDetector.detect(it))
        }
    }

    @Test
    fun `oppo family maps to OPPO`() {
        listOf("OPPO", "oppo", "realme", "Realme", "OnePlus", "oneplus").forEach {
            assertEquals("manufacturer=$it", OemProfile.OPPO, OemDetector.detect(it))
        }
    }

    @Test
    fun `vivo family maps to VIVO`() {
        listOf("vivo", "Vivo", "iQOO", "iqoo").forEach {
            assertEquals("manufacturer=$it", OemProfile.VIVO, OemDetector.detect(it))
        }
    }

    @Test
    fun `samsung maps to SAMSUNG`() {
        listOf("samsung", "Samsung", "SAMSUNG").forEach {
            assertEquals("manufacturer=$it", OemProfile.SAMSUNG, OemDetector.detect(it))
        }
    }

    @Test
    fun `huawei family maps to HUAWEI`() {
        listOf("HUAWEI", "huawei", "HONOR", "honor").forEach {
            assertEquals("manufacturer=$it", OemProfile.HUAWEI, OemDetector.detect(it))
        }
    }

    @Test
    fun `unknown brand falls back to GENERIC`() {
        listOf("Google", "Pixel", "Motorola", "Nokia", "Sony", "asus").forEach {
            assertEquals("manufacturer=$it", OemProfile.GENERIC, OemDetector.detect(it))
        }
    }

    @Test
    fun `null or blank falls back to GENERIC`() {
        assertEquals(OemProfile.GENERIC, OemDetector.detect(null))
        assertEquals(OemProfile.GENERIC, OemDetector.detect(""))
        assertEquals(OemProfile.GENERIC, OemDetector.detect("   "))
    }

    @Test
    fun `every profile has at least two distinct steps`() {
        OemProfile.entries.forEach { profile ->
            val steps = OemSteps.stepsFor(profile)
            assertTrue("profile=$profile has too few steps", steps.size >= 2)
            assertEquals("profile=$profile has duplicate steps", steps.size, steps.toSet().size)
        }
    }

    @Test
    fun `step tables are distinct per OEM family`() {
        // Sanity: the killer-OEMs should not share the generic copy.
        assertNotEquals(
            OemSteps.stepsFor(OemProfile.GENERIC),
            OemSteps.stepsFor(OemProfile.XIAOMI),
        )
        assertNotEquals(
            OemSteps.stepsFor(OemProfile.XIAOMI),
            OemSteps.stepsFor(OemProfile.OPPO),
        )
    }
}
