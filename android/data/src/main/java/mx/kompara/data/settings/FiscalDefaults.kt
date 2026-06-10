package mx.kompara.data.settings

/**
 * Compile-time fallback values for the IMSS threshold tracker (B-051), used when the remote
 * `fiscal_config` (GET /v1/config/fiscal) has never been fetched or the device is offline. The
 * authoritative values come from the backend so a new year is a remote-config change, not an app
 * update — these constants only keep the Fiscal tab usable before the first successful fetch.
 *
 * 2026 research-grade defaults (verify against the official CONASAMI resolution — see techdebt):
 *  - General-zone daily minimum wage: MXN $278.80/day.
 *  - IMSS monthly threshold: MXN $8,364.00 (one monthly minimum wage; $278.80 × 30), the
 *    reform-reporting figure the 2025 platform-work reform uses for per-platform coverage.
 *
 * Keep this in sync with `backend/seed/fiscal-config.ts`.
 */
object FiscalDefaults {

    /** Fiscal year the defaults below describe. */
    const val DEFAULT_YEAR: Int = 2026

    /** General-zone daily minimum wage in MXN (CONASAMI). */
    const val DEFAULT_MINIMUM_WAGE_DAILY_MXN: Double = 278.80

    /** IMSS coverage threshold = one monthly minimum wage in MXN. */
    const val DEFAULT_IMSS_MONTHLY_THRESHOLD_MXN: Double = 8364.0
}
