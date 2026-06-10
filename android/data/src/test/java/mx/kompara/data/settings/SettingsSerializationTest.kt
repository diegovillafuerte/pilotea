package mx.kompara.data.settings

import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure DataStore encode/decode logic. No Android dependency, so they run on
 * the plain JVM (instrumented DataStore IO is out of scope for this task).
 */
class SettingsSerializationTest {

    @Test
    fun `decode with no stored values yields defaults`() {
        val settings = SettingsSerialization.decode(enabledNames = null, lookupDouble = { null })
        assertEquals(Settings.DEFAULT.enabledPlatforms, settings.enabledPlatforms)
        assertTrue(settings.thresholds.isEmpty())
        // Missing thresholds fall back to the default floor.
        assertEquals(
            PlatformThreshold.DEFAULT.minPerKmMxn,
            settings.thresholdFor(Platform.UBER).minPerKmMxn,
            0.0001,
        )
    }

    @Test
    fun `decode reads enabled platforms and ignores unknown names`() {
        val settings = SettingsSerialization.decode(
            enabledNames = setOf("UBER", "DIDI", "NOT_A_PLATFORM"),
            lookupDouble = { null },
        )
        assertEquals(setOf(Platform.UBER, Platform.DIDI), settings.enabledPlatforms)
    }

    @Test
    fun `decode reconstructs per-platform thresholds`() {
        val stored = mapOf(
            SettingsSerialization.perKmKey(Platform.UBER) to 10.5,
            SettingsSerialization.perHourKey(Platform.UBER) to 120.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = setOf("UBER"),
            lookupDouble = { key -> stored[key] },
        )
        val uber = settings.thresholdFor(Platform.UBER)
        assertEquals(10.5, uber.minPerKmMxn, 0.0001)
        assertEquals(120.0, uber.minPerHourMxn, 0.0001)
    }

    @Test
    fun `partial threshold falls back to default for the missing field`() {
        val stored = mapOf(SettingsSerialization.perKmKey(Platform.DIDI) to 9.0)
        val settings = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { key -> stored[key] },
        )
        val didi = settings.thresholdFor(Platform.DIDI)
        assertEquals(9.0, didi.minPerKmMxn, 0.0001)
        assertEquals(PlatformThreshold.DEFAULT.minPerHourMxn, didi.minPerHourMxn, 0.0001)
    }

    @Test
    fun `encode round-trips enabled platform names`() {
        val settings = Settings(
            enabledPlatforms = setOf(Platform.UBER, Platform.INDRIVE),
            thresholds = emptyMap(),
        )
        val names = SettingsSerialization.encodeEnabledPlatforms(settings)
        assertEquals(setOf("UBER", "INDRIVE"), names)
        assertEquals(settings.enabledPlatforms, SettingsSerialization.decodeEnabledPlatforms(names))
    }

    @Test
    fun `isEnabled reflects the enabled set`() {
        val settings = Settings(enabledPlatforms = setOf(Platform.UBER), thresholds = emptyMap())
        assertTrue(settings.isEnabled(Platform.UBER))
        assertFalse(settings.isEnabled(Platform.DIDI))
    }
}
