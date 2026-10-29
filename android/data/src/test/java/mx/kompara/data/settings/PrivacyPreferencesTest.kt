package mx.kompara.data.settings

import org.junit.Assert.*
import org.junit.Test

class PrivacyPreferencesTest {
    @Test fun `fresh consent is disclosed telemetry on and contribution off`() {
        val fresh = SettingsSerialization.decode(null, { null })
        assertTrue(fresh.telemetryEnabled); assertFalse(fresh.shareAggregates)
        assertTrue(fresh.readerAlertsEnabled); assertTrue(fresh.dailySummaryEnabled); assertTrue(fresh.importReminderEnabled)
    }
    @Test fun `new notice choices coexist with migrated weekly and monthly keys`() {
        val flags = mapOf("reader_alerts_enabled" to false, "daily_summary_enabled" to false,
            "import_reminder_enabled" to false, SettingsSerialization.KEY_SHARE_WEEKLY_REMINDER to false,
            SettingsSerialization.KEY_FISCAL_MONTHLY_SUMMARY to true, "city_chosen" to true)
        val restored = SettingsSerialization.decode(null, { null }, lookupBoolean = flags::get)
        assertFalse(restored.readerAlertsEnabled); assertFalse(restored.dailySummaryEnabled)
        assertFalse(restored.importReminderEnabled); assertFalse(restored.shareWeeklyReminderEnabled)
        assertTrue(restored.fiscalMonthlySummaryEnabled); assertTrue(restored.cityChosen)
        assertTrue(restored.telemetryEnabled); assertFalse(restored.shareAggregates)
    }
}
