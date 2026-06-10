package mx.kompara.capture.telemetry

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mx.kompara.capture.HostVersionCodes
import mx.kompara.capture.OfferEvent
import mx.kompara.data.db.dao.TelemetryCounterDao
import mx.kompara.data.db.entity.TelemetryCounterEntity
import mx.kompara.parsers.model.OfferCard
import mx.kompara.parsers.spec.CardDetector
import mx.kompara.parsers.spec.ParserSpec
import mx.kompara.parsers.spec.SpecRegistry
import mx.kompara.parsers.spec.TextPattern
import mx.kompara.parsers.spec.VersionRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Counter-aggregation math for [TelemetryCollector] (B-034). Uses an in-memory fake DAO so the
 * increment folding is exercised on the plain JVM with no Room/Android.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryCollectorTest {

    private val uber = "com.ubercab.driver"
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-06-10T08:00:00Z"), ZoneOffset.UTC)
    private val day = "2026-06-10"

    /** A registry whose Uber spec is specVersion 7, valid for the version we report. */
    private fun registry(): SpecRegistry = SpecRegistry(
        listOf(
            ParserSpec(
                targetPackage = uber,
                versionCodeRange = VersionRange(min = 1, max = 1_000),
                specVersion = 7,
                cardDetector = CardDetector(anyOf = listOf(TextPattern("Viaje"))),
            ),
        ),
    )

    private fun collector(
        dao: TelemetryCounterDao,
        versionCode: Long? = 500L,
    ): TelemetryCollector = TelemetryCollector(
        dao = dao,
        registry = registry(),
        versionCodes = HostVersionCodes { if (it == uber) versionCode else null },
        clock = fixedClock,
    )

    @Test
    fun `parsed events count attempts and successes against variant and total`() = runTest {
        val dao = FakeCounterDao()
        val collector = collector(dao)

        // Two surge parses + one plain (null variant ⇒ aggregates into _total directly).
        collector.record(OfferEvent.Parsed(uber, 1L, OfferCard(platform = uber, variant = "surge")))
        collector.record(OfferEvent.Parsed(uber, 2L, OfferCard(platform = uber, variant = "surge")))
        collector.record(OfferEvent.Parsed(uber, 3L, OfferCard(platform = uber, variant = null)))

        val surge = dao.get(uber, 500L, 7, "surge", day)!!
        assertEquals(2L, surge.attempts)
        assertEquals(2L, surge.successes)
        assertEquals(0L, surge.failures)

        // _total = the two surge successes folded up + the one plain success = 3.
        val total = dao.get(uber, 500L, 7, TelemetryCounterEntity.TOTAL, day)!!
        assertEquals(3L, total.attempts)
        assertEquals(3L, total.successes)
        assertEquals(0L, total.failures)
    }

    @Test
    fun `NOT_AN_OFFER counts a failure on the total bucket`() = runTest {
        val dao = FakeCounterDao()
        val collector = collector(dao)

        collector.record(OfferEvent.NoCard(uber, 1L, OfferEvent.Reason.NOT_AN_OFFER))
        collector.record(OfferEvent.NoCard(uber, 2L, OfferEvent.Reason.NOT_AN_OFFER))

        val total = dao.get(uber, 500L, 7, TelemetryCounterEntity.TOTAL, day)!!
        assertEquals(2L, total.attempts)
        assertEquals(0L, total.successes)
        assertEquals(2L, total.failures)
    }

    @Test
    fun `NO_SPEC events are ignored - they are not parser breakages`() = runTest {
        val dao = FakeCounterDao()
        val collector = collector(dao, versionCode = 500L)

        collector.record(OfferEvent.NoCard("com.unknown.app", 1L, OfferEvent.Reason.NO_SPEC))

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `unknown host version falls back to 0 and missing spec to spec version 0`() = runTest {
        val dao = FakeCounterDao()
        // No version resolvable, and the host has no spec → versionCode 0, specVersion 0.
        val collector = TelemetryCollector(
            dao = dao,
            registry = SpecRegistry(emptyList()),
            versionCodes = HostVersionCodes { null },
            clock = fixedClock,
        )

        collector.record(OfferEvent.NoCard(uber, 1L, OfferEvent.Reason.NOT_AN_OFFER))

        val total = dao.get(uber, 0L, 0, TelemetryCounterEntity.TOTAL, day)!!
        assertEquals(1L, total.attempts)
        assertEquals(1L, total.failures)
    }

    @Test
    fun `mixed success and failure on the same bucket aggregate correctly`() = runTest {
        val dao = FakeCounterDao()
        val collector = collector(dao)

        collector.record(OfferEvent.Parsed(uber, 1L, OfferCard(platform = uber, variant = null)))
        collector.record(OfferEvent.NoCard(uber, 2L, OfferEvent.Reason.NOT_AN_OFFER))
        collector.record(OfferEvent.Parsed(uber, 3L, OfferCard(platform = uber, variant = null)))

        val total = dao.get(uber, 500L, 7, TelemetryCounterEntity.TOTAL, day)!!
        assertEquals(3L, total.attempts)
        assertEquals(2L, total.successes)
        assertEquals(1L, total.failures)
        assertNull(dao.get(uber, 500L, 7, "surge", day))
    }
}

/**
 * In-memory [TelemetryCounterDao] that implements the same upsert-increment contract as the Room
 * DAO (the @Transaction default is overridden here to operate on the map).
 */
private class FakeCounterDao : TelemetryCounterDao {
    data class Key(
        val pkg: String,
        val version: Long,
        val spec: Int,
        val variant: String,
        val day: String,
    )

    val rows = mutableMapOf<Key, TelemetryCounterEntity>()

    fun get(pkg: String, version: Long, spec: Int, variant: String, day: String): TelemetryCounterEntity? =
        rows[Key(pkg, version, spec, variant, day)]

    override suspend fun increment(
        hostPackage: String,
        hostVersionCode: Long,
        specVersion: Int,
        variant: String,
        dayUtc: String,
        attempts: Long,
        successes: Long,
        failures: Long,
    ) {
        val key = Key(hostPackage, hostVersionCode, specVersion, variant, dayUtc)
        val current = rows[key] ?: TelemetryCounterEntity(
            hostPackage = hostPackage,
            hostVersionCode = hostVersionCode,
            specVersion = specVersion,
            variant = variant,
            dayUtc = dayUtc,
        )
        rows[key] = current.copy(
            attempts = current.attempts + attempts,
            successes = current.successes + successes,
            failures = current.failures + failures,
        )
    }

    override suspend fun insertIgnore(row: TelemetryCounterEntity) {
        val key = Key(row.hostPackage, row.hostVersionCode, row.specVersion, row.variant, row.dayUtc)
        rows.putIfAbsent(key, row)
    }

    override suspend fun addDeltas(
        hostPackage: String,
        hostVersionCode: Long,
        specVersion: Int,
        variant: String,
        dayUtc: String,
        attempts: Long,
        successes: Long,
        failures: Long,
    ) {
        val key = Key(hostPackage, hostVersionCode, specVersion, variant, dayUtc)
        rows[key]?.let {
            rows[key] = it.copy(
                attempts = it.attempts + attempts,
                successes = it.successes + successes,
                failures = it.failures + failures,
            )
        }
    }

    override suspend fun all(): List<TelemetryCounterEntity> = rows.values.toList()
    override suspend fun count(): Int = rows.size

    override suspend fun deleteIfUnchanged(
        hostPackage: String,
        hostVersionCode: Long,
        specVersion: Int,
        variant: String,
        dayUtc: String,
        attempts: Long,
        successes: Long,
        failures: Long,
    ): Int {
        val key = Key(hostPackage, hostVersionCode, specVersion, variant, dayUtc)
        val row = rows[key] ?: return 0
        if (row.attempts == attempts && row.successes == successes && row.failures == failures) {
            rows.remove(key)
            return 1
        }
        return 0
    }
}
