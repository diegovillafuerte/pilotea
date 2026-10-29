package mx.kompara.sync.imports

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mx.kompara.data.accounting.*
import mx.kompara.data.imports.*
import mx.kompara.sync.aggregate.SessionGate
import mx.kompara.sync.api.*
import java.security.MessageDigest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One retained parse, local review/correction, then an atomic local-history save. */
interface Importer {
    suspend fun isSignedIn(): Boolean
    suspend fun restore(): ImportDraftEntity?
    suspend fun open(id: String): ImportDraftEntity?
    suspend fun retain(platform: String, uploadType: String, files: List<ImportFile>, parentId: String? = null): ImportDraftEntity
    suspend fun files(id: String): List<ImportFile>
    suspend fun consent(id: String): ImportDraftEntity
    suspend fun read(id: String): ImportDraftEntity
    suspend fun correct(id: String, metrics: ImportMetrics): ImportDraftEntity
    suspend fun save(id: String, useStatement: Boolean): ImportStatementEntity
    suspend fun discard(id: String)
}

internal val importJson = Json { ignoreUnknownKeys = true }
fun ImportResponse.retainedJson(): String = importJson.encodeToString(this)
fun ImportMetrics.retainedJson(): String = importJson.encodeToString(this)
fun ImportDraftEntity.response(): ImportResponse? = previewJson?.let { importJson.decodeFromString<ImportResponse>(it) }
fun ImportDraftEntity.metrics(): ImportMetrics? = correctionJson?.let { importJson.decodeFromString<ImportMetrics>(it) } ?: response()?.metrics

@Singleton
class ImportRepository @Inject constructor(
    private val api: ApiClient,
    private val store: LocalImportStore,
    private val session: SessionGate,
    private val clock: Clock = Clock.systemUTC(),
) : Importer {
    private val mutex = Mutex()
    override suspend fun isSignedIn() = session.isSignedIn()
    override suspend fun restore() = store.pending()
    override suspend fun open(id: String) = store.draft(id)
    override suspend fun files(id: String) = store.files(id).map { ImportFile(it.name, it.mime, it.bytes) }

    override suspend fun retain(platform: String, uploadType: String, files: List<ImportFile>, parentId: String?): ImportDraftEntity = mutex.withLock {
        require(platform in setOf("uber", "didi"))
        require(files.size in 1..2 && files.all { it.bytes.isNotEmpty() && it.bytes.size <= 10 * 1024 * 1024 && it.mimeType in setOf("application/pdf", "image/png", "image/jpeg", "image/webp") })
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$platform:$uploadType".toByteArray())
        val fingerprints = files.map { f ->
            f.mimeType + ":" + MessageDigest.getInstance("SHA-256").digest(f.bytes).joinToString("") { "%02x".format(it) }
        }
        require(fingerprints.distinct().size == files.size) { "Elige dos capturas diferentes." }
        fingerprints.sorted().forEach { digest.update(it.toByteArray()) }
        val id = digest.digest().joinToString("") { "%02x".format(it) }
        val existing = store.draft(id)
        if (existing != null && existing.stage != "DISCARDED") return@withLock existing
        // A discarded successful parse remains usable; bytes are retained again only for retry.
        val draft = existing?.copy(stage = if (existing.previewJson == null) "READY" else "REVIEW", consentAt = null)
            ?: ImportDraftEntity(id, platform, uploadType, clock.millis(), parentId = parentId)
        store.retain(draft, files.mapIndexed { i, f -> ImportFileEntity(id, i, f.fileName, f.mimeType, f.bytes) })
        draft
    }
    override suspend fun consent(id: String) = mutex.withLock {
        requireDraft(id).copy(consentAt = clock.millis()).also { store.update(it) }
    }
    override suspend fun read(id: String): ImportDraftEntity = mutex.withLock {
        val draft = requireDraft(id)
        if (draft.previewJson != null) return@withLock draft
        check(draft.consentAt != null) { "Falta aceptar la lectura del archivo." }
        if (!session.isSignedIn()) throw ApiException(401, "Entra con WhatsApp para leer tu resumen.")
        val files = files(id)
        if (draft.platform == "didi" && files.size != 2) throw ApiException(422, "Agrega las dos capturas de DiDi para leerlas juntas.")
        require(files.size == if (draft.platform == "didi") 2 else 1)
        store.update(draft.copy(stage = "READING", error = null))
        try {
            // This is the only network parse. Never call the legacy non-dry-run endpoint.
            val response = api.importWeek(draft.platform, draft.uploadType, files, dryRun = true)
            validateImportMetrics(response.metrics)
            check(store.draft(id) != null)
            draft.copy(previewJson = importJson.encodeToString(response), stage = "REVIEW", error = null).also { store.update(it) }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            store.draft(id)?.let { store.update(it.copy(stage = "ERROR", error = e.message)) }
            throw e
        }
    }
    override suspend fun correct(id: String, metrics: ImportMetrics): ImportDraftEntity = mutex.withLock {
        val draft = requireDraft(id)
        check(draft.stage != "SAVED")
        val original = checkNotNull(draft.response()).metrics
        validateImportMetrics(metrics)
        // Only read fields can be corrected. No optimistic zeros or newly invented distance.
        require(original.netEarnings != null || metrics.netEarnings == null)
        require(original.totalTrips != null || metrics.totalTrips == null)
        require(original.hoursOnline != null || metrics.hoursOnline == null)
        require(original.totalKm != null || metrics.totalKm == null)
        draft.copy(correctionJson = importJson.encodeToString(metrics), stage = "REVIEW").also { store.update(it) }
    }
    override suspend fun save(id: String, useStatement: Boolean): ImportStatementEntity = mutex.withLock {
        val draft = requireDraft(id)
        val metrics = checkNotNull(draft.metrics())
        validateImportMetrics(metrics)
        val start = LocalDate.parse(metrics.weekStart)
        val row = ImportStatementEntity(id, draft.platform.uppercase(), start.toString(), start.plusDays(6).toString(),
            metrics.netEarnings?.toBigDecimal()?.toPlainString(), metrics.totalTrips,
            metrics.totalKm?.toBigDecimal()?.toPlainString(), metrics.hoursOnline?.toBigDecimal()?.toPlainString(),
            if (draft.correctionJson != null && draft.metrics() != draft.response()?.metrics) MoneySource.USER_CORRECTED_IMPORT.name else MoneySource.PARSED_STATEMENT.name,
            draft.uploadType, clock.millis())
        val fields = buildList {
            if (row.payment != null) add(MoneyField.PAYMENT)
            if (row.trips != null) add(MoneyField.TRIPS)
            if (row.km != null) add(MoneyField.DISTANCE)
            if (row.hours != null) add(MoneyField.HOURS)
        }
        val selection = if (useStatement && MoneyField.PAYMENT in fields) ImportSelectionEntity(row.platform, row.start, row.id, fields.joinToString(",") { it.name }) else null
        store.commit(draft, row, selection)
        row
    }
    override suspend fun discard(id: String) = mutex.withLock { store.discard(id) }
    private suspend fun requireDraft(id: String) = checkNotNull(store.draft(id)) { "El borrador ya no está en este teléfono." }
}

fun validateImportMetrics(m: ImportMetrics) {
    require(runCatching { LocalDate.parse(m.weekStart).dayOfWeek == DayOfWeek.MONDAY }.getOrDefault(false)) { "Revisa la fecha de la semana." }
    require(listOf(m.grossEarnings, m.totalKm, m.hoursOnline, m.platformCommissionPct).all { it == null || it.isFinite() && it >= 0 }) { "Revisa este número" }
    require(m.netEarnings == null || m.netEarnings.isFinite()) { "Revisa este número" }
    require(m.totalTrips == null || m.totalTrips >= 0) { "Revisa este número" }
    require(m.hoursOnline == null || m.hoursOnline <= 168) { "Revisa este número" }
}
