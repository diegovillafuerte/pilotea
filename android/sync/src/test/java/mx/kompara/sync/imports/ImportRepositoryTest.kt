package mx.kompara.sync.imports

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import mx.kompara.data.imports.*
import mx.kompara.sync.aggregate.SessionGate
import mx.kompara.sync.api.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MemoryImportStore : LocalImportStore {
    val drafts = linkedMapOf<String, ImportDraftEntity>()
    val inputs = mutableMapOf<String, List<ImportFileEntity>>()
    val statements = mutableMapOf<String, ImportStatementEntity>()
    val selections = mutableMapOf<String, ImportSelectionEntity>()
    var failSave = false
    override suspend fun draft(id: String) = drafts[id]
    override suspend fun pending() = drafts.values.lastOrNull { it.stage !in listOf("SAVED", "DISCARDED") }
    override suspend fun files(id: String) = inputs[id].orEmpty()
    override suspend fun retain(draft: ImportDraftEntity, files: List<ImportFileEntity>) { drafts[draft.id] = draft; inputs[draft.id] = files }
    override suspend fun update(draft: ImportDraftEntity) { drafts[draft.id] = draft }
    override suspend fun commit(draft: ImportDraftEntity, statement: ImportStatementEntity, selection: ImportSelectionEntity?) {
        if (failSave) error("disk")
        if (drafts[draft.id]?.stage == "SAVED") return
        statements[statement.id] = statement; selection?.let { selections["${it.platform}:${it.start}"] = it }
        drafts[draft.id] = draft.copy(stage = "SAVED"); inputs.remove(draft.id)
    }
    override suspend fun discard(id: String) { drafts[id]?.let { drafts[id] = it.copy(stage = "DISCARDED") }; inputs.remove(id) }
}

class ImportRepositoryTest {
    private val m = ImportMetrics("2026-09-14", netEarnings = 5340.0, grossEarnings = 7000.0, totalTrips = 29, hoursOnline = 26.0, platformCommissionPct = 24.0)
    private val pdf = ImportFile("semana.pdf", "application/pdf", byteArrayOf(1, 2, 3))
    private val urls = mutableListOf<String>()
    private fun repo(store: MemoryImportStore, signedIn: Boolean = true, response: ImportMetrics = m, status: HttpStatusCode = HttpStatusCode.OK): ImportRepository {
        val engine = MockEngine { req ->
            urls += req.url.toString()
            respond(if (status == HttpStatusCode.OK) importJson.encodeToString(ImportResponse(metrics = response, dataCompleteness = .95, dryRun = true)) else """{"error":"No pudimos leer este archivo"}""",
                status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return ImportRepository(ApiClient(HttpClient(engine) { install(ContentNegotiation) { json(importJson) } }, "http://test.local", { "token" }, { "device" }),
            store, SessionGate { signedIn }, Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC))
    }
    @Test fun `selection and consent write no money and do not parse`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val draft = repo.retain("uber", "pdf", listOf(pdf))
        assertNull(draft.consentAt); assertEquals(pdf, repo.files(draft.id).single())
        assertTrue(runCatching { repo.read(draft.id) }.isFailure)
        repo.consent(draft.id)
        assertTrue(urls.isEmpty()); assertTrue(store.statements.isEmpty())
    }
    @Test fun `review corrections duplicate Save and repeated read use exactly one dry run`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val draft = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(draft.id)
        val parsed = repo.read(draft.id)
        val corrected = repo.correct(draft.id, m.copy(netEarnings = 5400.5))
        assertEquals(parsed.previewJson, corrected.previewJson)
        repo.read(draft.id); repo.save(draft.id, true); repo.save(draft.id, true)
        assertEquals(1, urls.size); assertTrue(urls.single().contains("dry_run=true"))
        val row = store.statements.values.single()
        assertEquals("5400.5", row.payment); assertNull(row.km); assertEquals("USER_CORRECTED_IMPORT", row.source)
        assertEquals(setOf("PAYMENT", "TRIPS", "HOURS"), store.selections.values.single().fields.split(',').toSet())
        assertEquals("SAVED", store.drafts[draft.id]!!.stage)
        assertTrue(repo.files(draft.id).isEmpty())
    }
    @Test fun `recreating repository restores preview corrections and consent without another parse`() = runTest {
        val store = MemoryImportStore(); val first = repo(store)
        val draft = first.retain("uber", "pdf", listOf(pdf)); first.consent(draft.id); first.read(draft.id)
        first.correct(draft.id, m.copy(totalTrips = 30))
        val next = repo(store)
        assertEquals(30, next.restore()!!.metrics()!!.totalTrips)
        next.read(draft.id); next.save(draft.id, false)
        assertEquals(1, urls.size); assertEquals(30, store.statements.values.single().trips)
    }
    @Test fun `concurrent reads share retained result and concurrent saves are idempotent`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val draft = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(draft.id)
        val a = async { repo.read(draft.id) }; val b = async { repo.read(draft.id) }
        assertEquals(a.await().previewJson, b.await().previewJson)
        val c = async { repo.save(draft.id, false) }; val d = async { repo.save(draft.id, false) }; c.await(); d.await()
        assertEquals(1, urls.size); assertEquals(1, store.statements.size)
    }
    @Test fun `failed upload keeps same bytes and consent for retry`() = runTest {
        val store = MemoryImportStore(); val failing = repo(store, status = HttpStatusCode.ServiceUnavailable)
        val draft = failing.retain("uber", "pdf", listOf(pdf)); failing.consent(draft.id)
        assertTrue(runCatching { failing.read(draft.id) }.exceptionOrNull() is ApiException)
        assertEquals("ERROR", store.drafts[draft.id]!!.stage)
        val retry = repo(store); assertEquals(pdf, retry.files(draft.id).single())
        retry.read(draft.id); retry.save(draft.id, false)
        assertEquals(2, urls.size); assertEquals(1, store.statements.size)
    }
    @Test fun `save failure retains parsed result and retries without upload`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val draft = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(draft.id); repo.read(draft.id)
        store.failSave = true; assertTrue(runCatching { repo.save(draft.id, true) }.isFailure)
        assertTrue(store.statements.isEmpty()); assertNotNull(repo.restore()!!.previewJson)
        store.failSave = false; repo.save(draft.id, true); assertEquals(1, urls.size)
    }
    @Test fun `signed out cannot parse and does not lose selected share`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store, signedIn = false)
        val draft = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(draft.id)
        val error = runCatching { repo.read(draft.id) }.exceptionOrNull() as ApiException
        assertEquals(401, error.status); assertEquals(pdf, repo.files(draft.id).single()); assertTrue(urls.isEmpty())
    }
    @Test fun `missing DiDi image retains input and new pair gets separate parse identity`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val image = pdf.copy(mimeType = "image/png")
        val first = repo.retain("didi", "screenshot", listOf(image)); repo.consent(first.id)
        assertEquals(422, (runCatching { repo.read(first.id) }.exceptionOrNull() as ApiException).status)
        val pair = repo.retain("didi", "screenshot", listOf(image, image.copy(bytes = byteArrayOf(4))), first.id)
        assertNotEquals(first.id, pair.id); assertEquals(first.id, pair.parentId); assertNotNull(store.drafts[first.id])
        repo.consent(pair.id); repo.read(pair.id); assertEquals(1, urls.size); assertTrue(store.statements.isEmpty())
    }
    @Test fun `unknown versus zero and distinct apps survive save without verification or gross substitution`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store, response = m.copy(netEarnings = null, totalTrips = null, hoursOnline = 0.0))
        val draft = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(draft.id); repo.read(draft.id)
        val row = repo.save(draft.id, true)
        assertNull(row.payment); assertNull(row.trips); assertEquals("0.0", row.hours)
        assertFalse(row.income().tripsKnown); assertEquals("PARSED_STATEMENT", row.source); assertTrue(store.selections.isEmpty())
    }
    @Test fun `invalid corrections and adding a missing distance never change original`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val draft = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(draft.id); val original = repo.read(draft.id)
        listOf(m.copy(totalTrips = -1), m.copy(totalKm = 5.0), m.copy(hoursOnline = 169.0), m.copy(netEarnings = Double.NaN), m.copy(weekStart = "2026-09-15")).forEach {
            assertTrue(runCatching { repo.correct(draft.id, it) }.isFailure)
        }
        assertEquals(original, repo.restore())
    }
    @Test fun `discard cleans local files and reselecting same parsed content does not parse twice`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val d = repo.retain("uber", "pdf", listOf(pdf)); repo.consent(d.id); repo.read(d.id); repo.discard(d.id)
        assertNull(repo.restore()); assertTrue(repo.files(d.id).isEmpty())
        val again = repo.retain("uber", "pdf", listOf(pdf.copy(fileName = "renamed.pdf")))
        assertEquals(d.id, again.id); assertNull(again.consentAt)
        repo.consent(again.id); repo.read(again.id); assertEquals(1, urls.size)
    }
    @Test fun `same input set in reverse order and renamed files reuses one parse`() = runTest {
        val store = MemoryImportStore(); val repo = repo(store)
        val images = listOf(pdf.copy(mimeType = "image/png"), pdf.copy(mimeType = "image/png", bytes = byteArrayOf(4)))
        val first = repo.retain("didi", "screenshot", images); repo.consent(first.id); repo.read(first.id)
        val again = repo.retain("didi", "screenshot", images.reversed().map { it.copy(fileName = "renamed.png") })
        repo.read(again.id)
        assertEquals(first.id, again.id); assertEquals(1, urls.size)
        assertTrue(runCatching { repo.retain("didi", "screenshot", listOf(images[0], images[0])) }.isFailure)
    }

}
