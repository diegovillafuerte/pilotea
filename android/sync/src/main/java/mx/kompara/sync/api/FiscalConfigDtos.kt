package mx.kompara.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTO for the fiscal-config endpoint (B-051). Field names match the backend's response shape
 * exactly (GET /v1/config/fiscal). Money fields are plain JSON numbers (the backend coerces its
 * decimals to numbers for this endpoint).
 */
@Serializable
data class FiscalConfigResponse(
    val imssMonthlyThresholdMxn: Double,
    val minimumWageDailyMxn: Double,
    val year: Int,
    /** ISO-8601 timestamp string of the backend's last update. */
    val updatedAt: String,
)
