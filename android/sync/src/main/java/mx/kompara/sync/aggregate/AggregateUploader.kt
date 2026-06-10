package mx.kompara.sync.aggregate

import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.sync.api.AggregateUploadBody
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.ApiException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether consented aggregate sharing is currently enabled (B-043). A thin seam over
 * [mx.kompara.data.settings.SettingsRepository] so [AggregateUploader] stays unit-testable without a
 * DataStore (the Hilt binding adapts the repository).
 */
fun interface AggregateConsent {
    /** Whether the driver has opted in to sharing anonymous weekly aggregates. */
    suspend fun isEnabled(): Boolean
}

/**
 * Whether the driver is signed in (has a session token). Aggregate sync requires a session — the
 * backend keys the upload to the authenticated driver — so the uploader short-circuits when this is
 * false. A seam (not a direct AuthRepository dependency) keeps the uploader unit-testable.
 */
fun interface SessionGate {
    /** Whether there is a current session token. */
    suspend fun isSignedIn(): Boolean
}

/**
 * Uploads consented weekly aggregates to the backend (B-043).
 *
 * **The consented data exchange, up direction.** When — and only when — the driver is BOTH signed in
 * AND has opted in ([AggregateConsent]), this pushes the driver's "dirty" weekly aggregates (never
 * synced, or recomputed since last sync) to `POST /v1/aggregates`. BOTH sources (captured + imported)
 * are sent, each marked with its `source`, so the population data reflects every consented week.
 *
 * **Privacy invariant.** The wire payload ([AggregateUploadBody]) is built ONLY from derived
 * aggregate fields — earnings totals + the five efficiency metrics, keyed by platform/week. No
 * offer-level, trip-level, or raw-text data is ever included (asserted against the serialized HTTP
 * body in [AggregateUploaderTest]). Raw capture data never leaves the device.
 *
 * **Dirty-row tracking.** A row is uploaded only if it's dirty (see [AggregateDao.dirtyForSync]);
 * on a 2xx ack it's stamped with [WeeklyAggregateEntity.lastSyncedAt] = the time the worker observed
 * it, so an unchanged row is skipped next run and a recomputed row (whose `computedAt` advanced) is
 * re-queued. Delete-on-ack semantics aren't used (the rows are the driver's own ledger); we stamp
 * the watermark instead.
 *
 * **Failure handling.** A 4xx is permanent (won't recover on retry) → the row is stamped synced so a
 * single poison row can't wedge the queue forever. A 5xx / transport error is transient → stop, leave
 * the row dirty, and report failed so the worker retries with backoff. Offline tolerance comes from
 * the enqueue-time network constraint plus retry.
 */
@Singleton
class AggregateUploader @Inject constructor(
    private val aggregateDao: AggregateDao,
    private val api: ApiClient,
    private val consent: AggregateConsent,
    private val session: SessionGate,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Outcome of one sync pass — drives the worker's success/retry decision. */
    data class Outcome(
        val uploaded: Int,
        val failed: Boolean,
        /** True when the pass did nothing because consent was off or the driver was signed out. */
        val shortCircuited: Boolean,
    )

    /**
     * Push all dirty aggregates if signed-in AND consented; otherwise short-circuit (nothing leaves
     * the device). Returns an [Outcome]; [Outcome.failed] is true if any upload hit a transient error
     * so the worker can request a retry.
     */
    suspend fun sync(): Outcome {
        // Both gates must pass. Signed-out OR not-consented → nothing is read or sent.
        if (!session.isSignedIn() || !consent.isEnabled()) {
            return Outcome(uploaded = 0, failed = false, shortCircuited = true)
        }

        val dirty = aggregateDao.dirtyForSync()
        var uploaded = 0
        var failed = false

        for (row in dirty) {
            // Capture the observation time BEFORE the upload so a concurrent recompute that bumps
            // computedAt after this point re-marks the row dirty (computedAt > lastSyncedAt).
            val observedAt = clock.millis()
            val result = runCatching { api.pushAggregate(row.toBody()) }
            when {
                result.isSuccess -> {
                    aggregateDao.markSynced(row.platform, row.weekStart, row.source, observedAt)
                    uploaded++
                }
                result.isPermanentFailure() -> {
                    // 4xx won't fix itself; stamp synced so one bad row can't wedge the queue.
                    aggregateDao.markSynced(row.platform, row.weekStart, row.source, observedAt)
                }
                else -> {
                    failed = true
                    break // transient (5xx/transport) → retry next run
                }
            }
        }

        return Outcome(uploaded = uploaded, failed = failed, shortCircuited = false)
    }

    /**
     * Map a Room weekly row to the wire payload. ONLY derived aggregate fields cross over: earnings
     * totals + the five efficiency metrics. Decimals are formatted as plain strings to match the
     * backend's decimalString coercion. Platform is lower-cased to the backend's enum
     * ("UBER" → "uber"); source is lower-cased ("CAPTURED" → "captured").
     */
    private fun WeeklyAggregateEntity.toBody(): AggregateUploadBody = AggregateUploadBody(
        platform = platform.lowercase(),
        weekStart = weekStart,
        netEarnings = decimal(netEarningsMxn),
        grossEarnings = decimal(grossEarningsMxn),
        totalTrips = totalTrips,
        totalKm = decimal(totalKm),
        hoursOnline = decimal(hoursOnline),
        earningsPerTrip = earningsPerTrip?.let { decimal(it) },
        earningsPerKm = earningsPerKm?.let { decimal(it) },
        earningsPerHour = earningsPerHour?.let { decimal(it) },
        tripsPerHour = tripsPerHour?.let { decimal(it) },
        source = source.lowercase(),
    )

    /** A 4xx is permanent (won't recover on retry); 5xx / transport errors are transient. */
    private fun Result<Unit>.isPermanentFailure(): Boolean {
        val ex = exceptionOrNull() ?: return false
        return ex is ApiException && ex.status in 400..499
    }

    private companion object {
        /** Format a metric as a fixed 2-decimal string (matches the backend DECIMAL(_,2) columns). */
        fun decimal(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
    }
}
