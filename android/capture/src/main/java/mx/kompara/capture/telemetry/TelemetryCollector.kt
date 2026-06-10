package mx.kompara.capture.telemetry

import kotlinx.coroutines.flow.Flow
import mx.kompara.capture.HostVersionCodes
import mx.kompara.capture.OfferEvent
import mx.kompara.data.db.dao.TelemetryCounterDao
import mx.kompara.data.db.entity.TelemetryCounterEntity
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.spec.SpecRegistry
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy-safe parse-health telemetry collector (B-034).
 *
 * Observes the existing [OfferEvent] stream (without modifying the pipeline that
 * produces it) and folds each outcome into per-`(hostPackage, hostVersionCode,
 * specVersion, variant, dayUtc)` counters in Room via [TelemetryCounterDao]. The
 * point is to learn that Uber/DiDi shipped a UI change our spec can't read —
 * surfaced as a failure-rate spike for a host version — BEFORE drivers complain.
 *
 * **No screen content ever enters telemetry, by construction.** This class only
 * ever passes to the DAO: host identifiers (package + version code resolved from
 * the OS), a spec revision (an int), a UTC day string, a spec-defined variant
 * tag, and integer deltas. It NEVER reads [OfferEvent.Parsed.card]'s extracted
 * fields or `raw` map. The counter entity has no column that can hold text, so
 * even a future careless edit can't leak a fare or a passenger name.
 *
 * Outcome → counter mapping:
 *  - [OfferEvent.Parsed]                  → attempt + success, variant = card.variant ?: _total
 *  - [OfferEvent.NoCard] NOT_AN_OFFER     → attempt + failure (a spec matched but couldn't read it —
 *                                            the breakage signal)
 *  - [OfferEvent.NoCard] NO_SPEC          → ignored (no spec targets this screen; not a breakage)
 */
@Singleton
class TelemetryCollector @Inject constructor(
    private val dao: TelemetryCounterDao,
    private val registry: SpecRegistry,
    private val versionCodes: HostVersionCodes,
    private val clock: Clock = Clock.systemUTC(),
) {

    /** Collect the whole [events] flow forever, recording each outcome. Started from an initializer. */
    suspend fun collect(events: Flow<OfferEvent>) {
        events.collect { record(it) }
    }

    /** Fold a single [event] into the counters. Pure side effect on the DAO; never throws upward. */
    suspend fun record(event: OfferEvent) {
        val packageName = event.packageName
        val versionCode = versionCodes.versionCodeOf(packageName) ?: UNKNOWN_VERSION
        val day = dayUtc()

        when (event) {
            is OfferEvent.Parsed -> {
                val specVersion = specVersionFor(packageName, versionCode)
                val variant = event.card.variant ?: TelemetryCounterEntity.TOTAL
                // One success against the recognized variant…
                dao.increment(
                    hostPackage = packageName,
                    hostVersionCode = versionCode,
                    specVersion = specVersion,
                    variant = variant,
                    dayUtc = day,
                    attempts = 1,
                    successes = 1,
                )
                // …and one against the package-level aggregate (when the variant differs from it),
                // so the host/version totals the alerting reads are complete regardless of variant.
                if (variant != TelemetryCounterEntity.TOTAL) {
                    dao.increment(
                        hostPackage = packageName,
                        hostVersionCode = versionCode,
                        specVersion = specVersion,
                        variant = TelemetryCounterEntity.TOTAL,
                        dayUtc = day,
                        attempts = 1,
                        successes = 1,
                    )
                }
            }

            is OfferEvent.NoCard -> when (event.reason) {
                OfferEvent.Reason.NOT_AN_OFFER -> {
                    val specVersion = specVersionFor(packageName, versionCode)
                    dao.increment(
                        hostPackage = packageName,
                        hostVersionCode = versionCode,
                        specVersion = specVersion,
                        variant = TelemetryCounterEntity.TOTAL,
                        dayUtc = day,
                        attempts = 1,
                        failures = 1,
                    )
                }
                // No spec targets this screen → not a parser breakage, don't pollute the rates.
                OfferEvent.Reason.NO_SPEC -> Unit
            }
        }
    }

    /**
     * The active spec revision for this host+version, or 0 when none. Resolved via the registry
     * using a minimal snapshot (the registry only reads package + versionCode), so the collector
     * never has to touch the pipeline's internal spec selection.
     */
    private fun specVersionFor(packageName: String, versionCode: Long): Int {
        val probe = ParserSnapshot(packageName = packageName, timestampMs = 0L, versionCode = versionCode)
        return registry.specFor(probe)?.specVersion ?: NO_SPEC_VERSION
    }

    private fun dayUtc(): String =
        DAY_FORMAT.format(clock.instant().atZone(ZoneOffset.UTC).toLocalDate())

    private companion object {
        const val UNKNOWN_VERSION = 0L
        const val NO_SPEC_VERSION = 0
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
