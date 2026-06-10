package mx.kompara.data.db.entity

import androidx.room.Entity

/**
 * On-device cache of the backend's fiscal config (B-051) — the IMSS-threshold values the Fiscal tab
 * needs. The 2025 platform-work reform ties IMSS coverage to earning ≥ 1 monthly minimum wage per
 * platform per calendar month; both the minimum wage and the threshold are indexed yearly, so they
 * are remote-configured (`GET /v1/config/fiscal`) rather than baked into a release.
 *
 * **One row per fiscal [year]** (the primary key), so a new year is an additional row, not an
 * overwrite. The repository reads the highest-year row and, when the cache is empty (offline,
 * never-fetched), falls back to compile-time default constants so the UI never blocks.
 *
 * Additive table (DB v2→v3): nothing references it; existing data is untouched. See
 * [mx.kompara.data.db.KomparaMigrations.MIGRATION_2_3].
 */
@Entity(
    tableName = "fiscal_config",
    primaryKeys = ["year"],
)
data class FiscalConfigEntity(
    /** Fiscal year these values apply to (e.g. 2026). */
    val year: Int,

    /** General-zone daily minimum wage in MXN. */
    val minimumWageDailyMxn: Double,

    /** IMSS coverage threshold = one monthly minimum wage in MXN. */
    val imssMonthlyThresholdMxn: Double,

    /** Epoch millis of the backend's `updatedAt`, for display/debugging. */
    val updatedAt: Long,

    /** Epoch millis this row was cached on device, for TTL gating. */
    val fetchedAt: Long,
)
