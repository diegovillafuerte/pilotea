package mx.kompara.data.settings

import mx.kompara.data.model.City
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertNull(settings.threshold)
        // A never-tuned threshold falls back to the default floor.
        assertEquals(
            PlatformThreshold.DEFAULT.minPerKmMxn,
            settings.effectiveThreshold.minPerKmMxn,
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
    fun `decode reconstructs the shared threshold from the global keys`() {
        val stored = mapOf(
            SettingsSerialization.KEY_THRESHOLD_PER_KM to 10.5,
            SettingsSerialization.KEY_THRESHOLD_PER_HOUR to 120.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = setOf("UBER"),
            lookupDouble = { key -> stored[key] },
        )
        val threshold = settings.effectiveThreshold
        assertEquals(10.5, threshold.minPerKmMxn, 0.0001)
        assertEquals(120.0, threshold.minPerHourMxn, 0.0001)
    }

    @Test
    fun `decode reads stored red floors`() {
        val stored = mapOf(
            SettingsSerialization.KEY_THRESHOLD_PER_KM to 10.0,
            SettingsSerialization.KEY_THRESHOLD_PER_HOUR to 120.0,
            SettingsSerialization.KEY_THRESHOLD_PER_KM_RED to 7.0,
            SettingsSerialization.KEY_THRESHOLD_PER_HOUR_RED to 80.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = setOf("UBER"),
            lookupDouble = { key -> stored[key] },
        )
        val threshold = settings.effectiveThreshold
        assertEquals(7.0, threshold.redPerKmMxn, 0.0001)
        assertEquals(80.0, threshold.redPerHourMxn, 0.0001)
    }

    @Test
    fun `missing red floors derive from the stored green floors`() {
        // An install that tuned its green floors before red floors existed.
        val stored = mapOf(
            SettingsSerialization.KEY_THRESHOLD_PER_KM to 10.0,
            SettingsSerialization.KEY_THRESHOLD_PER_HOUR to 120.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = setOf("UBER"),
            lookupDouble = { key -> stored[key] },
        )
        val threshold = settings.effectiveThreshold
        assertEquals(10.0 * PlatformThreshold.DEFAULT_RED_FRACTION, threshold.redPerKmMxn, 0.0001)
        assertEquals(120.0 * PlatformThreshold.DEFAULT_RED_FRACTION, threshold.redPerHourMxn, 0.0001)
    }

    @Test
    fun `stored red floor above the green floor is clamped down to it`() {
        val stored = mapOf(
            SettingsSerialization.KEY_THRESHOLD_PER_KM to 8.0,
            SettingsSerialization.KEY_THRESHOLD_PER_KM_RED to 11.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = setOf("UBER"),
            lookupDouble = { key -> stored[key] },
        )
        assertEquals(8.0, settings.effectiveThreshold.redPerKmMxn, 0.0001)
    }

    @Test
    fun `partial threshold falls back to default for the missing field`() {
        val stored = mapOf(SettingsSerialization.KEY_THRESHOLD_PER_KM to 9.0)
        val settings = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { key -> stored[key] },
        )
        val threshold = settings.effectiveThreshold
        assertEquals(9.0, threshold.minPerKmMxn, 0.0001)
        assertEquals(PlatformThreshold.DEFAULT.minPerHourMxn, threshold.minPerHourMxn, 0.0001)
    }

    @Test
    fun `legacy per-platform floors migrate, preferring DiDi (B-076)`() {
        // A pre-B-076 install that tuned DiDi and Uber separately: the DiDi floors win.
        val stored = mapOf(
            SettingsSerialization.perKmKey(Platform.UBER) to 12.0,
            SettingsSerialization.perHourKey(Platform.UBER) to 150.0,
            SettingsSerialization.perKmKey(Platform.DIDI) to 9.5,
            SettingsSerialization.perHourKey(Platform.DIDI) to 110.0,
            SettingsSerialization.perKmRedKey(Platform.DIDI) to 7.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { key -> stored[key] },
        )
        val threshold = settings.threshold!!
        assertEquals(9.5, threshold.minPerKmMxn, 0.0001)
        assertEquals(110.0, threshold.minPerHourMxn, 0.0001)
        assertEquals(7.0, threshold.redPerKmMxn, 0.0001)
    }

    @Test
    fun `legacy migration falls back to Uber when DiDi was never tuned (B-076)`() {
        val stored = mapOf(
            SettingsSerialization.perKmKey(Platform.UBER) to 12.0,
            SettingsSerialization.perHourKey(Platform.UBER) to 150.0,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { key -> stored[key] },
        )
        assertEquals(12.0, settings.threshold!!.minPerKmMxn, 0.0001)
    }

    @Test
    fun `global keys win over legacy per-platform keys (B-076)`() {
        // Once the shared semáforo has been written, stale legacy keys must be ignored.
        val stored = mapOf(
            SettingsSerialization.KEY_THRESHOLD_PER_KM to 8.5,
            SettingsSerialization.perKmKey(Platform.DIDI) to 9.5,
        )
        val settings = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { key -> stored[key] },
        )
        assertEquals(8.5, settings.threshold!!.minPerKmMxn, 0.0001)
    }

    @Test
    fun `encode round-trips enabled platform names`() {
        val settings = Settings(enabledPlatforms = setOf(Platform.UBER, Platform.INDRIVE))
        val names = SettingsSerialization.encodeEnabledPlatforms(settings)
        assertEquals(setOf("UBER", "INDRIVE"), names)
        assertEquals(settings.enabledPlatforms, SettingsSerialization.decodeEnabledPlatforms(names))
    }

    @Test
    fun `isEnabled reflects the enabled set`() {
        val settings = Settings(enabledPlatforms = setOf(Platform.UBER))
        assertTrue(settings.isEnabled(Platform.UBER))
        assertFalse(settings.isEnabled(Platform.DIDI))
    }

    @Test
    fun `telemetry defaults ON when never written and reads a stored value`() {
        // Never written → default ON (anonymous counters carry no personal data).
        val default = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { null },
        )
        assertTrue(default.telemetryEnabled)

        // Explicitly disabled → reads false.
        val disabled = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { key ->
                if (key == SettingsSerialization.KEY_TELEMETRY_ENABLED) false else null
            },
        )
        assertFalse(disabled.telemetryEnabled)
    }

    @Test
    fun `onboarding defaults to incomplete when never written and reads a stored value`() {
        // Fresh install → onboarding pending (route to the funnel).
        val fresh = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { null },
        )
        assertFalse(fresh.onboardingCompleted)

        // Completed previously → reads true (route straight to the main shell).
        val completed = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { key ->
                if (key == SettingsSerialization.KEY_ONBOARDING_COMPLETED) true else null
            },
        )
        assertTrue(completed.onboardingCompleted)
    }

    @Test
    fun `weekly net goal is null when unset and reads a stored value (B-039)`() {
        val unset = SettingsSerialization.decode(enabledNames = null, lookupDouble = { null })
        assertNull(unset.weeklyNetGoalMxn)

        val withGoal = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { key ->
                if (key == SettingsSerialization.KEY_WEEKLY_NET_GOAL) 5000.0 else null
            },
        )
        assertEquals(5000.0, withGoal.weeklyNetGoalMxn!!, 0.0001)
    }

    @Test
    fun `aggregate sharing defaults OFF when never written and reads a stored value (B-043)`() {
        // Fresh install → sharing OFF (strictly opt-in; this shares the driver's own aggregates).
        val fresh = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { null },
        )
        assertFalse(fresh.shareAggregates)

        // Explicitly opted in → reads true.
        val optedIn = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { key ->
                if (key == SettingsSerialization.KEY_SHARE_AGGREGATES) true else null
            },
        )
        assertTrue(optedIn.shareAggregates)
    }

    @Test
    fun `city defaults to CDMX when unset and round-trips a stored value (B-043)`() {
        // Never written → default city.
        val unset = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupString = { null },
        )
        assertEquals(City.DEFAULT, unset.city)

        // Stored as the enum name → decodes back to the city.
        val stored = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupString = { key ->
                if (key == SettingsSerialization.KEY_CITY) City.GUADALAJARA.name else null
            },
        )
        assertEquals(City.GUADALAJARA, stored.city)

        // Unknown/garbage stored value → falls back to default (never crashes).
        val garbage = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupString = { key ->
                if (key == SettingsSerialization.KEY_CITY) "ATLANTIS" else null
            },
        )
        assertEquals(City.DEFAULT, garbage.city)
    }

    @Test
    fun `debug premium defaults OFF when never written and reads a stored value (B-046)`() {
        // Fresh install → real entitlement decides (no debug override).
        val fresh = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { null },
        )
        assertFalse(fresh.debugPremium)

        // Explicitly enabled for demoing → reads true.
        val enabled = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { key ->
                if (key == SettingsSerialization.KEY_DEBUG_PREMIUM) true else null
            },
        )
        assertTrue(enabled.debugPremium)
    }

    @Test
    fun `share hide-amounts defaults OFF and reads a stored value (B-055)`() {
        val fresh = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { null },
        )
        assertFalse(fresh.shareHideAmounts)

        val hidden = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { key ->
                if (key == SettingsSerialization.KEY_SHARE_HIDE_AMOUNTS) true else null
            },
        )
        assertTrue(hidden.shareHideAmounts)
    }

    @Test
    fun `share weekly reminder defaults ON and reads a stored value (B-055)`() {
        val fresh = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { null },
        )
        assertTrue(fresh.shareWeeklyReminderEnabled)

        val off = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupBoolean = { key ->
                if (key == SettingsSerialization.KEY_SHARE_WEEKLY_REMINDER) false else null
            },
        )
        assertFalse(off.shareWeeklyReminderEnabled)
    }

    @Test
    fun `share watermark and count default empty and round-trip (B-055)`() {
        val fresh = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
        )
        assertNull(fresh.shareLastReminderWeek)
        assertEquals(0, fresh.shareCount)

        val stored = SettingsSerialization.decode(
            enabledNames = null,
            lookupDouble = { null },
            lookupString = { key ->
                if (key == SettingsSerialization.KEY_SHARE_LAST_REMINDER_WEEK) "2026-06-01" else null
            },
            lookupInt = { key ->
                if (key == SettingsSerialization.KEY_SHARE_COUNT) 7 else null
            },
        )
        assertEquals("2026-06-01", stored.shareLastReminderWeek)
        assertEquals(7, stored.shareCount)
    }

    @Test
    fun `city keys match the backend benchmark city slugs (B-043)`() {
        // The 10 seeded benchmark cities — keys MUST stay in lockstep with the backend CITIES list.
        assertEquals("cdmx", City.CDMX.key)
        assertEquals("guadalajara", City.GUADALAJARA.key)
        assertEquals("cancun", City.CANCUN.key)
        assertEquals(10, City.entries.size)
        assertEquals(City.CDMX, City.fromKey("CDMX"))
        assertNull(City.fromKey("national")) // national is a server-side fallback, not a driver city
    }
}
