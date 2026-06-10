package mx.kompara.capture.telemetry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mx.kompara.data.db.dao.FixtureReportDao
import mx.kompara.data.db.entity.FixtureReportEntity
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * [FixtureReporter] tests (B-034): explicit-consent gate, PII scrubbing before persistence, and the
 * "no raw screen content leaks" property asserted against a deliberately poisoned snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FixtureReporterTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-10T08:00:00Z"), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun reporter(dao: FixtureReportDao) =
        FixtureReporter(dao = dao, scrubber = SnapshotScrubber(), json = json, clock = clock)

    /** A snapshot packed with PII the scrubber must mask: phone, plate, name, street address. */
    private fun poisonedSnapshot() = ParserSnapshot(
        packageName = "com.ubercab.driver",
        timestampMs = 1L,
        versionCode = 500L,
        nodes = listOf(
            ParserNode(text = "Pasajero: Juan Pérez García", depth = 0, index = 0),
            ParserNode(text = "Llamar 55 1234 5678", depth = 1, index = 0),
            ParserNode(text = "Placa ABC-12-34", depth = 1, index = 1),
            ParserNode(text = "Recoger en Av. Reforma 222", depth = 1, index = 2),
            ParserNode(text = "Maria Lopez", depth = 1, index = 3),
        ),
    )

    @Test
    fun `report without consent is rejected and stores nothing`() = runTest {
        val dao = FakeFixtureDao()
        val result = reporter(dao).report(
            snapshot = poisonedSnapshot(),
            reason = "NOT_AN_OFFER",
            specVersion = 7,
            consented = false,
        )
        assertEquals(FixtureReporter.Result.ConsentRequired, result)
        assertTrue(dao.inserted.isEmpty())
    }

    @Test
    fun `consented report is scrubbed and queued`() = runTest {
        val dao = FakeFixtureDao()
        val result = reporter(dao).report(
            snapshot = poisonedSnapshot(),
            reason = "NOT_AN_OFFER",
            specVersion = 7,
            consented = true,
        )
        assertTrue(result is FixtureReporter.Result.Queued)
        assertEquals(1, dao.inserted.size)
        val row = dao.inserted.single()
        assertEquals("com.ubercab.driver", row.hostPackage)
        assertEquals(500L, row.hostVersionCode)
        assertEquals(7, row.specVersion)
        assertEquals("NOT_AN_OFFER", row.reason)
        assertTrue(row.consented)
        assertEquals(clock.millis(), row.createdAt)
    }

    @Test
    fun `queued payload contains no raw PII - it is scrubbed by construction`() = runTest {
        val dao = FakeFixtureDao()
        reporter(dao).report(
            snapshot = poisonedSnapshot(),
            reason = "NOT_AN_OFFER",
            consented = true,
        )
        val payload = dao.inserted.single().snapshotJson

        // The exact PII tokens from the poisoned snapshot must NOT survive into the stored payload.
        for (leak in listOf("Juan", "Pérez", "García", "55 1234 5678", "5512345678", "ABC-12-34", "Reforma 222", "Maria", "Lopez")) {
            assertFalse("payload leaked PII token: $leak\n$payload", payload.contains(leak))
        }
        // And the scrubber's masks ARE present, proving the text went through it.
        assertTrue(payload.contains(SnapshotScrubber.NAME_MASK))
        assertTrue(payload.contains(SnapshotScrubber.PHONE_MASK))
        assertTrue(payload.contains(SnapshotScrubber.PLATE_MASK))

        // Structural fields (package, viewId-free bounds) survive so the fixture is replayable.
        val decoded = json.decodeFromString(ParserSnapshot.serializer(), payload)
        assertEquals("com.ubercab.driver", decoded.packageName)
        assertEquals(5, decoded.nodes.size)
    }
}

/** In-memory [FixtureReportDao] recording inserts. */
private class FakeFixtureDao : FixtureReportDao {
    val inserted = mutableListOf<FixtureReportEntity>()
    private var nextId = 1L

    override suspend fun insert(report: FixtureReportEntity): Long {
        val id = nextId++
        inserted += report.copy(id = id)
        return id
    }

    override suspend fun oldest(limit: Int): List<FixtureReportEntity> =
        inserted.sortedBy { it.createdAt }.take(limit)

    override suspend fun count(): Int = inserted.size

    override suspend fun delete(report: FixtureReportEntity) {
        inserted.removeAll { it.id == report.id }
    }

    override suspend fun deleteById(id: Long) {
        inserted.removeAll { it.id == id }
    }
}
