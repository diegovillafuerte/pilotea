package mx.kompara.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mx.kompara.data.model.City
import mx.kompara.data.model.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Flow-based access to the driver's [Settings], backed by DataStore preferences.
 *
 * Reads expose a cold [Flow] that re-emits on every change; writes are suspending. The
 * encode/decode logic lives in [SettingsSerialization] so it stays unit-testable.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val enabledPlatformsKey =
        stringSetPreferencesKey(SettingsSerialization.KEY_ENABLED_PLATFORMS)
    private val telemetryEnabledKey =
        booleanPreferencesKey(SettingsSerialization.KEY_TELEMETRY_ENABLED)
    private val onboardingCompletedKey =
        booleanPreferencesKey(SettingsSerialization.KEY_ONBOARDING_COMPLETED)
    private val weeklyNetGoalKey =
        doublePreferencesKey(SettingsSerialization.KEY_WEEKLY_NET_GOAL)
    private val shareAggregatesKey =
        booleanPreferencesKey(SettingsSerialization.KEY_SHARE_AGGREGATES)
    private val aggregatePromptDismissedKey =
        booleanPreferencesKey(SettingsSerialization.KEY_AGGREGATE_PROMPT_DISMISSED)
    private val cityKey =
        stringPreferencesKey(SettingsSerialization.KEY_CITY)
    private val debugPremiumKey =
        booleanPreferencesKey(SettingsSerialization.KEY_DEBUG_PREMIUM)
    private val fiscalMonthlySummaryKey =
        booleanPreferencesKey(SettingsSerialization.KEY_FISCAL_MONTHLY_SUMMARY)
    private val fiscalLastNotifiedMonthKey =
        stringPreferencesKey(SettingsSerialization.KEY_FISCAL_LAST_NOTIFIED_MONTH)
    private val shareHideAmountsKey =
        booleanPreferencesKey(SettingsSerialization.KEY_SHARE_HIDE_AMOUNTS)
    private val shareWeeklyReminderKey =
        booleanPreferencesKey(SettingsSerialization.KEY_SHARE_WEEKLY_REMINDER)
    private val shareLastReminderWeekKey =
        stringPreferencesKey(SettingsSerialization.KEY_SHARE_LAST_REMINDER_WEEK)
    private val shareCountKey =
        intPreferencesKey(SettingsSerialization.KEY_SHARE_COUNT)
    private val fiscalExportCountKey =
        intPreferencesKey(SettingsSerialization.KEY_FISCAL_EXPORT_COUNT)
    private val preferredMetricKey =
        stringPreferencesKey(SettingsSerialization.KEY_PREFERRED_METRIC)
    private val rateBasisKey =
        stringPreferencesKey(SettingsSerialization.KEY_RATE_BASIS)

    suspend fun setReaderDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("reader_disclosure_v3")] = accepted }
    }

    suspend fun setReaderAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("reader_alerts_enabled")] = enabled }
    }
    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("daily_summary_enabled")] = enabled }
    }
    suspend fun setImportReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("import_reminder_enabled")] = enabled }
    }

    val settings: Flow<Settings> = dataStore.data.map { prefs -> prefs.toSettings() }

    /** Shared Ganancias/Comparar selection; null follows the current calendar week. */
    val selectedWeekStart: Flow<String?> = dataStore.data.map { it[stringPreferencesKey("selected_week_start")] }

    val selectedMonth: Flow<String?> = dataStore.data.map { it[stringPreferencesKey("selected_month")] }

    suspend fun selectMonth(month: String?, comparisonWeek: String) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("selected_month")
            if (month == null) prefs.remove(key) else prefs[key] = month
            prefs[stringPreferencesKey("selected_week_start")] = comparisonWeek
        }
    }

    suspend fun selectWeekStart(week: String?) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("selected_week_start")
            if (week == null) prefs.remove(key) else prefs[key] = week
        }
    }

    /** Snapshot read of whether anonymous telemetry upload is currently allowed. */
    suspend fun isTelemetryEnabled(): Boolean = settings.first().telemetryEnabled

    /** Turn anonymous parse-health telemetry upload on or off (B-034). */
    suspend fun setTelemetryEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[telemetryEnabledKey] = enabled }
    }

    /** Snapshot read of whether the onboarding funnel has been completed (B-036). */
    suspend fun isOnboardingCompleted(): Boolean = settings.first().onboardingCompleted

    /** Mark the onboarding funnel as completed; flips the root composable to the main shell (B-036). */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[onboardingCompletedKey] = completed }
    }

    /**
     * Snapshot read of whether consented aggregate sharing is on (B-043). The sync worker also
     * reads this; default OFF so nothing uploads until the driver explicitly opts in.
     */
    suspend fun isShareAggregatesEnabled(): Boolean = settings.first().shareAggregates

    /**
     * Turn consented aggregate sharing on or off (B-043). When ON (and signed in) the
     * [mx.kompara.sync] AggregateSyncWorker uploads ONLY derived weekly aggregates; raw capture data
     * never leaves the device. Turning it off does not retroactively delete already-uploaded
     * aggregates (account deletion is the path for that) — it just stops future uploads.
     */
    suspend fun setShareAggregates(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[shareAggregatesKey] = enabled }
    }

    /**
     * Record whether the one-shot aggregate-sharing consent prompt has been dismissed (B-043), so the
     * Inicio prompt isn't shown again after the driver declines. Independent of [setShareAggregates].
     */
    suspend fun setAggregatePromptDismissed(dismissed: Boolean) {
        dataStore.edit { prefs -> prefs[aggregatePromptDismissedKey] = dismissed }
    }

    /** Snapshot read of the driver's benchmark city (B-043). */
    suspend fun currentCity(): City = settings.first().city

    /** Snapshot read of whether the month-end IMSS summary notification is enabled (B-051). */
    suspend fun isFiscalMonthlySummaryEnabled(): Boolean = settings.first().fiscalMonthlySummaryEnabled

    /**
     * Turn the month-end IMSS summary notification on or off (B-051). When OFF, the month-end worker
     * short-circuits and posts nothing. Default ON.
     */
    suspend fun setFiscalMonthlySummaryEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[fiscalMonthlySummaryKey] = enabled }
    }

    /**
     * The "yyyy-MM" of the last month a month-end IMSS summary was posted (B-051), or null if never.
     * The month-end worker compares this against the just-ended month so a day-1 trigger plus a
     * next-app-open trigger don't double-post (idempotency watermark).
     */
    suspend fun fiscalLastNotifiedMonth(): String? = settings.first().fiscalLastNotifiedMonth

    /** Record that the month-end summary for [monthKey] ("yyyy-MM") has been posted (B-051). */
    suspend fun setFiscalLastNotifiedMonth(monthKey: String) {
        dataStore.edit { prefs -> prefs[fiscalLastNotifiedMonthKey] = monthKey }
    }

    // ─── B-055 shareable earnings card ───────────────────────────────────────────────────────────

    /** Snapshot read of the share-card hide-amounts default (B-055). */
    suspend fun isShareHideAmounts(): Boolean = settings.first().shareHideAmounts

    /** Legacy preference writer, retained for migration; v3 uses per-preview disclosure. */
    suspend fun setShareHideAmounts(hide: Boolean) {
        dataStore.edit { prefs -> prefs[shareHideAmountsKey] = hide }
    }

    /** Snapshot read of whether the Monday week-close share reminder is enabled (B-055). */
    suspend fun isShareWeeklyReminderEnabled(): Boolean = settings.first().shareWeeklyReminderEnabled

    /**
     * Turn the Monday week-close share reminder on or off (B-055). When OFF the week-close worker
     * short-circuits and posts nothing. Default ON.
     */
    suspend fun setShareWeeklyReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[shareWeeklyReminderKey] = enabled }
    }

    /**
     * The ISO Monday (yyyy-MM-dd) of the last week a share reminder was posted (B-055), or null if
     * never. The week-close worker compares this against the just-closed week so a Monday periodic
     * trigger plus a next-app-open trigger don't double-post (idempotency watermark).
     */
    suspend fun shareLastReminderWeek(): String? = settings.first().shareLastReminderWeek

    /** Record that the week-close reminder for [weekStartIso] (ISO Monday) has been posted (B-055). */
    suspend fun setShareLastReminderWeek(weekStartIso: String) {
        dataStore.edit { prefs -> prefs[shareLastReminderWeekKey] = weekStartIso }
    }

    /**
     * Increment the anonymous local share-tap funnel counter (B-055). No personal data — just a
     * count of how many times the driver shared a card, for measuring the acquisition loop.
     */
    suspend fun incrementShareCount() {
        dataStore.edit { prefs ->
            val current = (prefs[shareCountKey] ?: 0).coerceAtLeast(0)
            prefs[shareCountKey] = if (current == Int.MAX_VALUE) current else current + 1
        }
    }

    /**
     * Increment the anonymous local fiscal-PDF-export funnel counter (B-052). No personal data — just
     * a count of how many times the driver exported a monthly fiscal report, for measuring the export
     * loop (credit applications / accountants).
     */
    suspend fun incrementFiscalExportCount() {
        dataStore.edit { prefs ->
            val current = (prefs[fiscalExportCountKey] ?: 0).coerceAtLeast(0)
            prefs[fiscalExportCountKey] = if (current == Int.MAX_VALUE) current else current + 1
        }
    }

    /**
     * Set the driver's benchmark [city] (B-043). Changing it should invalidate the cached benchmarks
     * (the caller — BenchmarksRepository — keys its cache on city and re-fetches on change).
     */
    suspend fun setCity(city: City) {
        dataStore.edit { prefs -> prefs[cityKey] = city.name; prefs[booleanPreferencesKey("city_chosen")] = true }
    }

    /**
     * Toggle the debug premium override (B-046). Additive over the real entitlement so a tester can
     * preview the percentile UI before a paywall exists. Default OFF.
     */
    suspend fun setDebugPremium(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[debugPremiumKey] = enabled }
    }

    /** Enable or disable capture/verdicts for a single platform. */
    suspend fun setPlatformEnabled(platform: Platform, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = SettingsSerialization
                .decodeEnabledPlatforms(prefs[enabledPlatformsKey])
                .toMutableSet()
            if (enabled) current.add(platform) else current.remove(platform)
            prefs[enabledPlatformsKey] = current.map { it.name }.toSet()
        }
    }

    /**
     * Set or clear the weekly net earnings goal in MXN (B-039). A null or non-positive [goalMxn]
     * clears the goal (no target). Drives goal-progress UI and the streak.
     */
    suspend fun setWeeklyNetGoal(goalMxn: Double?) {
        dataStore.edit { prefs ->
            if (goalMxn == null || goalMxn <= 0.0) {
                prefs.remove(weeklyNetGoalKey)
            } else {
                prefs[weeklyNetGoalKey] = goalMxn
            }
        }
    }

    /** Set which net rate decides the semáforo (B-079): IPK (net $/km) or IPH (net $/hr). */
    suspend fun setPreferredMetric(metric: PreferredMetric) {
        dataStore.edit { prefs -> prefs[preferredMetricKey] = metric.name }
    }

    /**
     * One DataStore transaction: capture can never observe a new pair with the old unit or basis.
     * A null [basis] keeps the stored one; pass it when the limits were converted to a new basis.
     */
    suspend fun saveLimits(threshold: PlatformThreshold, metric: PreferredMetric, basis: RateBasis? = null) {
        requireValidLimits(threshold)
        dataStore.edit { prefs ->
            writeLimits(prefs, threshold)
            prefs[preferredMetricKey] = metric.name
            if (basis != null) prefs[rateBasisKey] = basis.name
        }
    }

    /**
     * Switch the basis and save the limits converted with [LimitConversion] in the same transaction,
     * read from the stored values. [hourStep] is the per-hour limit editor's step. No-op when [basis]
     * is already the stored one.
     */
    suspend fun switchRateBasis(basis: RateBasis, costPerKm: java.math.BigDecimal, hourStep: java.math.BigDecimal) {
        dataStore.edit { prefs ->
            val current = prefs.toSettings()
            if (current.rateBasis == basis) return@edit
            val converted = LimitConversion.convert(current.effectiveThreshold, current.rateBasis, basis, costPerKm, hourStep)
            requireValidLimits(converted)
            writeLimits(prefs, converted)
            prefs[rateBasisKey] = basis.name
        }
    }

    private fun requireValidLimits(threshold: PlatformThreshold) {
        require(listOf(threshold.minPerKmMxn, threshold.redPerKmMxn, threshold.minPerHourMxn, threshold.redPerHourMxn).all { it.isFinite() && it >= 0 })
        require(threshold.minPerKmMxn > threshold.redPerKmMxn && threshold.minPerKmMxn <= 99.99)
        require(threshold.minPerHourMxn > threshold.redPerHourMxn)
    }

    private fun writeLimits(prefs: androidx.datastore.preferences.core.MutablePreferences, threshold: PlatformThreshold) {
        prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_KM)] = threshold.minPerKmMxn
        prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_KM_RED)] = threshold.redPerKmMxn
        prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_HOUR)] = threshold.minPerHourMxn
        prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_HOUR_RED)] = threshold.redPerHourMxn
    }

    /** Set the shared acceptance thresholds (B-076: one semáforo for every platform). */
    suspend fun setThreshold(threshold: PlatformThreshold) {
        dataStore.edit { prefs ->
            prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_KM)] =
                threshold.minPerKmMxn
            prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_HOUR)] =
                threshold.minPerHourMxn
            prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_KM_RED)] =
                threshold.redPerKmMxn
            prefs[doublePreferencesKey(SettingsSerialization.KEY_THRESHOLD_PER_HOUR_RED)] =
                threshold.redPerHourMxn
        }
    }

    private fun Preferences.toSettings(): Settings =
        SettingsSerialization.decode(
            enabledNames = this[enabledPlatformsKey],
            lookupInt = { key -> this[intPreferencesKey(key)] },
            lookupDouble = { key -> this[doublePreferencesKey(key)] },
            lookupBoolean = { key -> this[booleanPreferencesKey(key)] },
            lookupString = { key -> this[stringPreferencesKey(key)] },
        )
}
