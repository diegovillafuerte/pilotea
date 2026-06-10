package mx.kompara.capture.telemetry

import kotlinx.serialization.json.Json
import mx.kompara.capture.di.TelemetryJson
import mx.kompara.data.db.dao.FixtureReportDao
import mx.kompara.data.db.entity.FixtureReportEntity
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.snapshot.ParserSnapshot
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Reportar tarjeta no leída" API (B-034).
 *
 * When the driver hits a card the parser couldn't read, they can explicitly
 * report it so it feeds the parser-spec corpus (B-028) and we can ship a fixed
 * spec. This is the on-device half: take the offending snapshot, scrub PII out
 * of it with [SnapshotScrubber], and queue a [FixtureReportEntity] for the
 * `:sync` uploader to store-and-forward to `POST /v1/fixture-reports`.
 *
 * **Consent is mandatory and explicit, per report.** Fixture reports — unlike
 * the anonymous parse counters — carry the (scrubbed) screen structure, so they
 * are NEVER sent on the always-on telemetry toggle. [report] requires the caller
 * to pass [consented] = true (the UI button in B-036/B-040 gates this behind a
 * confirmation); a false flag is rejected and nothing is stored.
 *
 * The button + ViewModel land with B-036/B-040; this task exposes the API + tests.
 */
@Singleton
class FixtureReporter @Inject constructor(
    private val dao: FixtureReportDao,
    private val scrubber: SnapshotScrubber,
    @TelemetryJson private val json: Json,
    private val clock: Clock = Clock.systemUTC(),
) {

    /**
     * Outcome of a report attempt: queued, or rejected because the caller did not pass explicit
     * per-report consent.
     */
    sealed interface Result {
        /** Queued for upload; [id] is the local row id. */
        data class Queued(val id: Long) : Result

        /** Rejected: [report] was called without explicit consent. Nothing was stored. */
        data object ConsentRequired : Result
    }

    /**
     * Scrub [snapshot] and queue it as a consented fixture report.
     *
     * @param snapshot the raw [ParserSnapshot] of the card the parser failed to read. Scrubbed here
     *   before anything is persisted — callers may pass an un-scrubbed snapshot safely.
     * @param reason why no card was produced (NO_SPEC / NOT_AN_OFFER / OTHER).
     * @param specVersion the spec revision active at failure time, if any.
     * @param consented MUST be true — the explicit per-report consent flag. A false value
     *   short-circuits with [Result.ConsentRequired] and stores nothing.
     */
    suspend fun report(
        snapshot: ParserSnapshot,
        reason: String,
        specVersion: Int? = null,
        consented: Boolean,
    ): Result {
        if (!consented) return Result.ConsentRequired

        val scrubbed = scrubber.scrub(snapshot)
        val entity = FixtureReportEntity(
            hostPackage = scrubbed.packageName,
            hostVersionCode = scrubbed.versionCode,
            specVersion = specVersion,
            reason = reason,
            snapshotJson = json.encodeToString(ParserSnapshot.serializer(), scrubbed),
            consented = true,
            createdAt = clock.millis(),
        )
        val id = dao.insert(entity)
        return Result.Queued(id)
    }
}
