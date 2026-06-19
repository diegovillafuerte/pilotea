package mx.kompara.sync.imports

import mx.kompara.data.db.dao.AggregateDao
import mx.kompara.data.db.entity.AggregateSource
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.sync.aggregate.SessionGate
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.ApiException
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.api.ImportMetrics
import mx.kompara.sync.api.ImportResponse
import mx.kompara.sync.verification.VerificationSignals
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The data-side contract the B-045 import flow's ViewModel ([mx.kompara.ui.imports.ImportViewModel],
 * in `:ui`) depends on. A seam (not the concrete [ImportRepository]) so the ViewModel is unit-testable
 * in `:ui` with a plain fake — no Ktor/MockEngine on the `:ui` test classpath. The Hilt binding
 * ([ImportModule]) provides [ImportRepository] as the live implementation.
 */
interface Importer {
    /** True when a session token is held — uploads require an account. */
    suspend fun isSignedIn(): Boolean

    /** Dry-run preview: parse + return metrics, persist nothing. Validates file count. */
    suspend fun preview(platform: String, uploadType: String, files: List<ImportFile>): ImportResponse

    /** Confirmed import: upload for real, then backfill local history. Validates file count. */
    suspend fun confirm(platform: String, uploadType: String, files: List<ImportFile>): ImportResponse
}

/**
 * Drives the B-045 import flow on the data side: uploads a weekly summary to the B-044 endpoint
 * (dry-run preview, then confirmed import) and, on a confirmed import, backfills the parsed week into
 * the LOCAL Room weekly-aggregate table so it appears in history immediately with an `importado`
 * badge.
 *
 * **Captured-protection contract (mirrors [AggregateSource]'s reconciliation rule):** an imported
 * week is written as a row with `source = IMPORTED`. The weekly_aggregates primary key is
 * `(platform, weekStart, source)`, so an IMPORTED upsert can NEVER collide with — and therefore never
 * overwrite — a CAPTURED row for the same platform/week. The two coexist; [mx.kompara.ui.stats.
 * HistoryWeeks] prefers the imported (realized) numbers at read time. This is the same "captured beats
 * imported" guarantee the backend enforces server-side, upheld here structurally by the key.
 *
 * **Idempotency:** re-importing the same platform/week writes the same `(platform, weekStart,
 * IMPORTED)` key; the DAO's REPLACE upsert overwrites the prior imported row in place, so a
 * double-import leaves exactly one imported row (the latest numbers win).
 *
 * The repository never throws for an expected failure mode: a signed-out upload surfaces as a 401
 * [ApiException] (the UI maps it to the "necesitas una cuenta" screen), and a parse/validation
 * failure surfaces as an [ApiException] carrying the backend's exact Spanish error string.
 */
@Singleton
class ImportRepository @Inject constructor(
    private val api: ApiClient,
    private val aggregateDao: AggregateDao,
    private val session: SessionGate,
    private val clock: Clock = Clock.systemUTC(),
    private val verification: VerificationSignals = VerificationSignals.NONE,
) : Importer {

    /** True when a session token is held — uploads require an account. */
    override suspend fun isSignedIn(): Boolean = session.isSignedIn()

    /**
     * Dry-run preview: upload [files] for [platform]/[uploadType] and return the parsed metrics
     * WITHOUT persisting anything (server-side or locally). Validates the file count first.
     */
    override suspend fun preview(
        platform: String,
        uploadType: String,
        files: List<ImportFile>,
    ): ImportResponse {
        validateFileCount(platform, files)
        return api.importWeek(platform, uploadType, files, dryRun = true)
    }

    /**
     * Confirmed import: upload [files] for real, then backfill the parsed week into local Room
     * aggregates (source=IMPORTED). Returns the backend response. Validates the file count first.
     */
    override suspend fun confirm(
        platform: String,
        uploadType: String,
        files: List<ImportFile>,
    ): ImportResponse {
        validateFileCount(platform, files)
        // Snapshot the verification session BEFORE the upload: if a logout/account-switch lands while
        // the import is in flight, the mark below is discarded (it would belong to a session that's gone).
        val generation = verification.sessionGeneration()
        val response = api.importWeek(platform, uploadType, files, dryRun = false)
        upsertLocal(platform, response.metrics)
        // A successful non-dry-run import IS the verification event (account-onboarding design §3):
        // mark verified now so the benchmarks/compare gate unlocks immediately, no extra round-trip.
        verification.markVerified(generation)
        return response
    }

    /**
     * Write the imported week into local Room as a `source = IMPORTED` row. Scoped to IMPORTED so a
     * CAPTURED row for the same platform/week is never touched (different primary key). Null money /
     * count fields default to 0 to satisfy the entity's non-null columns, matching the backend's own
     * "earnings default to 0" upsert coercion.
     */
    private suspend fun upsertLocal(platform: String, metrics: ImportMetrics) {
        val row = WeeklyAggregateEntity(
            platform = platformEnumName(platform),
            weekStart = metrics.weekStart,
            source = AggregateSource.IMPORTED.name,
            netEarningsMxn = metrics.netEarnings ?: 0.0,
            grossEarningsMxn = metrics.grossEarnings ?: 0.0,
            totalTrips = metrics.totalTrips ?: 0,
            totalKm = metrics.totalKm ?: 0.0,
            hoursOnline = metrics.hoursOnline ?: 0.0,
            earningsPerTrip = metrics.earningsPerTrip,
            earningsPerKm = metrics.earningsPerKm,
            earningsPerHour = metrics.earningsPerHour,
            tripsPerHour = metrics.tripsPerHour,
            // Acceptance rate is a capture-only metric; an imported summary never carries it.
            acceptanceRate = null,
            computedAt = clock.millis(),
            // A fresh import is not-yet-synced; the B-043 uploader will pick it up as dirty.
            lastSyncedAt = null,
        )
        aggregateDao.upsertWeekly(listOf(row))
    }

    /**
     * Enforce the per-platform file-count contract before hitting the network: DiDi needs exactly 2
     * captures (earnings + tablero), every other platform exactly 1. Throws an [ApiException]
     * carrying the backend's own Spanish DiDi message so the UI shows one consistent string whether
     * the check trips locally or server-side.
     */
    private fun validateFileCount(platform: String, files: List<ImportFile>) {
        val expected = if (platform == PLATFORM_DIDI) 2 else 1
        if (files.size != expected) {
            val message = if (platform == PLATFORM_DIDI) {
                "DiDi requiere 2 capturas de pantalla: la pantalla de ganancias y el tablero. " +
                    "Por favor sube ambas imagenes."
            } else {
                "Selecciona exactamente un archivo para subir."
            }
            throw ApiException(status = 422, message = message)
        }
    }

    /** Map the lower-case wire platform ("uber") to the Room/enum name ("UBER") captured rows use. */
    private fun platformEnumName(wirePlatform: String): String =
        when (wirePlatform) {
            PLATFORM_UBER -> Platform.UBER.name
            PLATFORM_DIDI -> Platform.DIDI.name
            PLATFORM_INDRIVE -> Platform.INDRIVE.name
            else -> Platform.UNKNOWN.name
        }

    private companion object {
        const val PLATFORM_UBER = "uber"
        const val PLATFORM_DIDI = "didi"
        const val PLATFORM_INDRIVE = "indrive"
    }
}
