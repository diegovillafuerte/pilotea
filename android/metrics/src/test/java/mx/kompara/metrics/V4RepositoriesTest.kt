package mx.kompara.metrics

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.data.settings.CarCostRepository
import mx.kompara.data.settings.CostProfileRepository
import mx.kompara.data.settings.MonthlyIncentiveRepository
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.imss.ImssMonthRepository
import mx.kompara.metrics.imss.ImssState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** The v4 data seams on a real Room database: frozen basis, IMSS month, the driver's slices and the car bill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V4RepositoriesTest {
    @get:Rule val folder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("America/Mexico_City")
    private val asOf = LocalDateTime.of(2026, 10, 19, 14, 40).atZone(zone).toInstant()
    private lateinit var db: KomparaDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsRepository
    private lateinit var costs: CostProfileRepository
    private lateinit var periods: PeriodMoneyRepository
    private var nextId = 1L

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, KomparaDatabase::class.java).build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("s.preferences_pb") })
        costs = CostProfileRepository(db.costProfileDao())
        costs.saveRunningCosts(RunningCostInputs.TYPICAL, effectiveFrom = 0, recordedAt = 0) // 24.50 ÷ 12 + 0.80 per km
        periods = PeriodMoneyRepository(db, costs, CarCostRepository(db.carCostDao()))
    }

    @After fun tearDown() { db.close(); scope.cancel() }

    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private suspend fun taken(app: String, local: String, fare: Double, km: Double, minutes: Double = 20.0, accepted: Boolean = true) {
        val id = nextId++
        db.offerDao().insert(OfferEntity(id = id, seenAt = at(local) - 60_000, platform = app, fareMxn = fare, distanceKm = km,
            durationMin = minutes, sessionId = "s$id", outcome = if (accepted) "ACCEPTED" else "NOT_TAKEN"))
        db.tripDao().insert(TripEntity(id = id, offerId = id, startedAt = at(local), endedAt = at(local) + 600_000, platform = app,
            grossMxn = fare, distanceKm = km, durationMin = minutes))
    }

    @Test fun `OfferRecorder freezes the basis it judged in`() = runBlocking {
        val recorder = OfferRecorder(db.offerDao(), costs, settings, NetProfitEngine())
        settings.saveLimits(PlatformThreshold(11.0, 280.0, 9.0, 230.0), PreferredMetric.IPK, RateBasis.GROSS)
        val seen = at("2026-10-19T14:32")
        // $90.05 for 22.9 km, read in two frames: the second fills the time and is re-judged with the frozen basis
        val first = recorder.record("bid", seen, TripOffer("DIDI", 90.05, 2.9, null, 20.0, 36.0), "OCR", false)
        assertEquals(RateBasis.GROSS.name, first.evaluation!!.rateBasis)
        assertEquals("RED", first.verdict) // 90.05 / 22.9 = 3.93 < 9
        settings.saveLimits(PlatformThreshold(8.0, 200.0, 6.0, 150.0), PreferredMetric.IPK, RateBasis.NET)
        val enriched = recorder.record("bid", seen, TripOffer("DIDI", 90.05, 2.9, 4.0, 20.0, 36.0), "OCR", false, seen + 1_000)
        assertEquals(RateBasis.GROSS.name, enriched.evaluation!!.rateBasis); assertEquals("11.0", enriched.evaluation!!.goal)
        assertEquals(RateBasis.GROSS, enriched.recordedMetrics().verdict.rateBasis)
        assertEquals(CounterOffers(207, 252), enriched.counterOffers()) // 9 × 22.9 = 206.1 → 207; 11 × 22.9 = 251.9 → 252
        // a new offer takes today's NET setting: $96 for 10.4 km is 6.39 neto per km, so only the goal's 113
        val next = recorder.record("next", seen + 60_000, TripOffer("DIDI", 96.0, 0.4, 2.0, 10.0, 20.0), "OCR", false)
        assertEquals(RateBasis.NET.name, next.evaluation!!.rateBasis); assertEquals("YELLOW", next.verdict)
        assertEquals(CounterOffers(null, 113), next.counterOffers())
    }

    @Test fun `the IMSS month counts the month's taken trips by app plus the typed incentives`() = runBlocking {
        taken("UBER", "2026-10-02T09:00", 12_000.0, 900.0)
        taken("UBER", "2026-10-19T08:00", 487.0, 30.0) // today, before now
        taken("DIDI", "2026-10-05T18:00", 7_942.0, 600.0)
        taken("UBER", "2026-09-30T23:00", 5_000.0, 300.0) // September: out
        taken("UBER", "2026-10-19T20:00", 900.0, 30.0) // after now: out
        taken("DIDI", "2026-10-06T10:00", 300.0, 20.0, accepted = false) // not taken: out
        MonthlyIncentiveRepository(db.monthlyIncentiveDao()).set("UBER", YearMonth.of(2026, 10), 1_800, 0)
        val month = ImssMonthRepository(db, periods).observe(YearMonth.of(2026, 10), asOf, zone).first()
        assertEquals(BigDecimal("21938"), month.need)
        val (uber, didi) = month.apps
        assertEquals("UBER", uber.platform); assertEquals(0, BigDecimal(12_487).compareTo(uber.trips))
        assertEquals(0, BigDecimal(14_287).compareTo(uber.counted)); assertEquals(ImssState.WILL_REACH, uber.state)
        assertEquals("DIDI", didi.platform); assertEquals(0, BigDecimal(7_942).compareTo(didi.counted)); assertEquals(ImssState.WILL_FALL_SHORT, didi.state)
        assertEquals(true, month.combinedReaches)
    }

    @Test fun `an app with trips in the month before has an IMSS block this month`() = runBlocking {
        taken("UBER", "2026-10-02T09:00", 1_000.0, 50.0)
        taken("DIDI", "2026-09-12T09:00", 800.0, 40.0) // September only
        taken("INDRIVE", "2026-08-20T09:00", 500.0, 20.0) // two months back: no block
        val month = ImssMonthRepository(db, periods).observe(YearMonth.of(2026, 10), asOf, zone).first()
        assertEquals(listOf("UBER", "DIDI"), month.apps.map { it.platform })
        assertEquals(0, BigDecimal.ZERO.compareTo(month.apps[1].counted)); assertEquals(ImssState.NO_PACE_YET, month.apps[1].state)
    }

    @Test fun `the slices read the 28 days before today`() = runBlocking {
        taken("UBER", "2026-10-02T09:00", 100.0, 10.0)
        taken("UBER", "2026-10-03T19:00", 300.0, 20.0)
        taken("UBER", "2026-10-19T08:00", 900.0, 10.0) // today: out
        taken("DIDI", "2026-09-20T09:00", 100.0, 10.0) // 29 days ago: out
        val slices = periods.observeSlices(asOf, zone).first()
        assertEquals(LocalDate.of(2026, 9, 21), slices.from); assertEquals(LocalDate.of(2026, 10, 18), slices.to)
        assertEquals(2, slices.trips); assertEquals(setOf("UBER"), slices.byApp.keys)
        // gross 400 / 30 km
        assertEquals(BigDecimal("13.33"), slices.byApp.getValue("UBER").rate(PreferredMetric.IPK, RateBasis.GROSS)!!.setScale(2, RoundingMode.HALF_UP))
    }

    @Test fun `the week carries its car bill`() = runBlocking {
        CarCostRepository(db.carCostDao()).save(BigDecimal(1500), LocalDate.of(2026, 9, 28), 0)
        taken("UBER", "2026-10-12T09:00", 1_000.0, 10.0) // Monday: 1,000 − 28.42 = 971.58
        taken("UBER", "2026-10-13T09:00", 600.0, 10.0) // Tuesday: 571.58 → 1,543.17 ≥ 1,500
        val week = periods.weekSnapshot(PeriodWindow(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), asOf, zone))
        assertEquals(0, BigDecimal(1500).compareTo(week.car.bill)); assertEquals(DayOfWeek.TUESDAY, week.car.coveredOn)
        assertEquals(BigDecimal("43.17"), week.car.yours!!.setScale(2, RoundingMode.HALF_UP))
        assertEquals(0, week.money.tripContribution!!.compareTo(week.car.earned)) // the bill never changes the week's figure
    }
}
