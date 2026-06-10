package mx.kompara.sync.telemetry

import kotlinx.serialization.json.Json
import mx.kompara.data.db.dao.FixtureReportDao
import mx.kompara.data.db.dao.TelemetryCounterDao
import mx.kompara.data.db.entity.FixtureReportEntity
import mx.kompara.data.db.entity.TelemetryCounterEntity
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.FixtureReportBody
import mx.kompara.sync.api.FixtureSnapshotDto
import mx.kompara.sync.api.TelemetryCounterBody
import mx.kompara.parsers.snapshot.ParserSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the anonymous-telemetry consent toggle. A thin seam over
 * [mx.kompara.data.settings.SettingsRepository] so [TelemetryUploader] stays
 * unit-testable without a DataStore (the Hilt binding adapts the repository).
 */
fun interface TelemetryConsent {
    /** Whether anonymous parse-health counters may be uploaded right now. */
    suspend fun isEnabled(): Boolean
}

/**
 * Uploads queued telemetry to the backend and deletes it on ack (B-034).
 *
 * Two independent queues, each store-and-forward and offline-tolerant:
 *  1. **Counters** — anonymous parse-health buckets ([TelemetryCounterEntity]),
 *     POSTed to `/v1/telemetry`. Gated by the `telemetryEnabled` consent toggle
 *     (default ON; the counters carry no personal data). When consent is off the
 *     upload short-circuits and nothing leaves the device.
 *  2. **Fixture reports** — explicitly-consented, PII-scrubbed snapshots
 *     ([FixtureReportEntity]), POSTed to `/v1/fixture-reports`. NOT gated by the
 *     telemetry toggle — each row already carries its own per-report consent
 *     (re-checked here as a belt-and-braces guard); a non-consented row is
 *     dropped without upload.
 *
 * Failure handling: each item is deleted only AFTER its POST returns 2xx
 * (delete-on-ack), and a counter is deleted only if its values are unchanged
 * since upload (so increments that arrived mid-flight aren't lost). A transport
 * failure leaves the queue intact for the next run; the worker reports retry.
 */
@Singleton
class TelemetryUploader @Inject constructor(
    private val counterDao: TelemetryCounterDao,
    private val fixtureDao: FixtureReportDao,
    private val api: ApiClient,
    private val consent: TelemetryConsent,
    private val json: Json,
) {

    /** Aggregate outcome of one flush — drives the worker's success/retry decision. */
    data class Outcome(
        val countersUploaded: Int,
        val fixturesUploaded: Int,
        val failed: Boolean,
    )

    /**
     * Flush both queues. Returns an [Outcome]; [Outcome.failed] is true if any
     * upload hit a transport/5xx error so the caller can request a retry.
     */
    suspend fun flush(): Outcome {
        var counters = 0
        var fixtures = 0
        var failed = false

        if (consent.isEnabled()) {
            val rows = counterDao.all()
            val acked = mutableListOf<TelemetryCounterEntity>()
            for (row in rows) {
                val sent = runCatching {
                    api.uploadTelemetryCounter(row.toBody())
                }
                if (sent.isSuccess) {
                    acked += row
                    counters++
                } else if (sent.isPermanentFailure()) {
                    // A 4xx (e.g. validation) won't fix itself on retry — drop it so a
                    // single poison row can't wedge the whole queue forever.
                    acked += row
                } else {
                    failed = true
                    break // stop on the first transient failure; retry next run
                }
            }
            if (acked.isNotEmpty()) counterDao.deleteAcked(acked)
        }
        // Consent off → counters untouched on device; nothing uploaded.

        // Fixture reports always attempt (own per-report consent), oldest first.
        val reports = fixtureDao.oldest(FIXTURE_BATCH)
        for (report in reports) {
            if (!report.consented) {
                // Defensive: a row should never lack consent, but never upload one that does.
                fixtureDao.delete(report)
                continue
            }
            val sent = runCatching { api.uploadFixtureReport(report.toBody()) }
            if (sent.isSuccess) {
                fixtureDao.delete(report)
                fixtures++
            } else if (sent.isPermanentFailure()) {
                fixtureDao.delete(report)
            } else {
                failed = true
                break
            }
        }

        return Outcome(countersUploaded = counters, fixturesUploaded = fixtures, failed = failed)
    }

    private fun TelemetryCounterEntity.toBody(): TelemetryCounterBody = TelemetryCounterBody(
        hostPackage = hostPackage,
        // The backend keys on a host VERSION string; we send the numeric code as text.
        hostVersion = hostVersionCode.toString(),
        specVersion = specVersion,
        attempts = attempts.toInt(),
        successes = successes.toInt(),
        failures = failures.toInt(),
        day = dayUtc,
    )

    private fun FixtureReportEntity.toBody(): FixtureReportBody {
        val snapshot = json.decodeFromString(ParserSnapshot.serializer(), snapshotJson)
        return FixtureReportBody(
            consent = true,
            hostPackage = hostPackage,
            hostVersion = hostVersionCode?.toString(),
            specVersion = specVersion,
            reason = reason,
            snapshot = FixtureSnapshotDto(
                packageName = snapshot.packageName,
                timestampMs = snapshot.timestampMs,
                versionCode = snapshot.versionCode,
                nodes = snapshot.nodes.map {
                    mx.kompara.sync.api.FixtureNodeDto(
                        text = it.text,
                        viewId = it.viewId,
                        className = it.className,
                        bounds = mx.kompara.sync.api.FixtureBoundsDto(
                            left = it.bounds.left,
                            top = it.bounds.top,
                            right = it.bounds.right,
                            bottom = it.bounds.bottom,
                        ),
                        depth = it.depth,
                        index = it.index,
                    )
                },
            ),
        )
    }

    /** A 4xx is permanent (won't recover on retry); 5xx / transport errors are transient. */
    private fun Result<Unit>.isPermanentFailure(): Boolean {
        val ex = exceptionOrNull() ?: return false
        return ex is ApiException && ex.status in 400..499
    }

    private companion object {
        const val FIXTURE_BATCH = 20
    }
}
