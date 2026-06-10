package mx.kompara.metrics

import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.settings.PlatformThreshold
import org.junit.Assert.assertEquals
import org.junit.Test

class NetProfitEngineTest {

    private val engine = NetProfitEngine()

    private val profile = CostProfileEntity(
        updatedAt = 0L,
        fuelPerKmMxn = 1.5,
        maintenancePerKmMxn = 0.5,
        insurancePerDayMxn = 50.0,
        rentPerDayMxn = 200.0,
    )

    @Test
    fun `net subtracts marginal per-km costs only`() {
        // gross 100, 10 km, per-km cost = 2.0 -> net = 100 - 20 = 80
        val m = engine.evaluate(
            grossMxn = 100.0,
            distanceKm = 10.0,
            durationMin = 30.0,
            costProfile = profile,
            threshold = PlatformThreshold.DEFAULT,
        )
        assertEquals(80.0, m.netMxn, 0.0001)
        assertEquals(8.0, m.perKmMxn, 0.0001)        // 80 / 10
        assertEquals(160.0, m.perHourMxn, 0.0001)    // 80 / (30/60)
    }

    @Test
    fun `good verdict when both floors met`() {
        val m = engine.evaluate(
            grossMxn = 200.0,
            distanceKm = 10.0,
            durationMin = 30.0,
            costProfile = profile,
            threshold = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0),
        )
        assertEquals(Verdict.GOOD, m.verdict)
    }

    @Test
    fun `marginal verdict when only one floor met`() {
        // net 80 over 10km/30min -> perKm 8.0 (ok), perHour 160 (ok) ... raise km floor to fail it
        val m = engine.evaluate(
            grossMxn = 100.0,
            distanceKm = 10.0,
            durationMin = 30.0,
            costProfile = profile,
            threshold = PlatformThreshold(minPerKmMxn = 12.0, minPerHourMxn = 90.0),
        )
        assertEquals(Verdict.MARGINAL, m.verdict)
    }

    @Test
    fun `bad verdict when neither floor met`() {
        val m = engine.evaluate(
            grossMxn = 30.0,
            distanceKm = 10.0,
            durationMin = 60.0,
            costProfile = profile,
            threshold = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0),
        )
        assertEquals(Verdict.BAD, m.verdict)
    }

    @Test
    fun `null cost profile means gross equals net`() {
        val m = engine.evaluate(
            grossMxn = 100.0,
            distanceKm = 10.0,
            durationMin = 30.0,
            costProfile = null,
            threshold = PlatformThreshold.DEFAULT,
        )
        assertEquals(100.0, m.netMxn, 0.0001)
    }

    @Test
    fun `zero distance and duration do not divide by zero`() {
        val m = engine.evaluate(
            grossMxn = 50.0,
            distanceKm = 0.0,
            durationMin = 0.0,
            costProfile = profile,
            threshold = PlatformThreshold.DEFAULT,
        )
        assertEquals(0.0, m.perKmMxn, 0.0001)
        assertEquals(0.0, m.perHourMxn, 0.0001)
    }
}
