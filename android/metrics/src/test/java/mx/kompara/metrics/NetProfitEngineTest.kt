package mx.kompara.metrics

import mx.kompara.data.settings.PlatformThreshold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetProfitEngineTest {

    private val engine = NetProfitEngine()

    // 2 MXN/km marginal cost (1.5 fuel + 0.5 maintenance).
    private val profile = CostProfile(fuelCostPerKm = 1.5, maintenancePerKm = 0.5)

    /** Floors that the worked example below clears on both dimensions. */
    private val lenient = PlatformThreshold(minPerKmMxn = 6.0, minPerHourMxn = 90.0)

    /** A fully-specified offer: 2 km pickup + 8 km trip = 10 km total; 6 + 24 = 30 min total. */
    private fun fullOffer(fare: Double? = 120.0) = TripOffer(
        platform = "uber",
        fareMxn = fare,
        pickupKm = 2.0,
        pickupMin = 6.0,
        tripKm = 8.0,
        tripMin = 24.0,
    )

    // ── Core math ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `totals fold pickup leg into trip leg`() {
        val m = engine.evaluate(fullOffer(), profile, lenient)
        assertEquals(10.0, m.totalKm!!, 1e-9)   // 2 + 8
        assertEquals(30.0, m.totalMin!!, 1e-9)  // 6 + 24
    }

    @Test
    fun `net subtracts only marginal per-km cost over total distance`() {
        // gross 120, total 10 km, 2 MXN/km -> net = 120 - 20 = 100
        val m = engine.evaluate(fullOffer(), profile, lenient)
        assertEquals(120.0, m.grossMxn!!, 1e-9)
        assertEquals(100.0, m.netMxn!!, 1e-9)
        assertEquals(10.0, m.netPerKm!!, 1e-9)        // 100 / 10
        assertEquals(12.0, m.grossPerKm!!, 1e-9)      // 120 / 10
        assertEquals(100.0 / 30.0, m.netPerMin!!, 1e-9)
        assertEquals(120.0 / 30.0, m.grossPerMin!!, 1e-9)
        assertEquals(200.0, m.netPerHour!!, 1e-9)     // 100 / (30/60)
        assertEquals(200.0, m.verdict.netPerHourEquivalent!!, 1e-9)
    }

    @Test
    fun `fixed daily costs never touch a single offer net`() {
        val withFixed = profile.copy(dailyFixedCosts = 500.0)
        val m = engine.evaluate(fullOffer(), withFixed, lenient)
        assertEquals(100.0, m.netMxn!!, 1e-9) // unchanged by the 500 fixed cost
    }

    @Test
    fun `zero cost profile makes net equal gross`() {
        val m = engine.evaluate(fullOffer(), CostProfile.ZERO, lenient)
        assertEquals(120.0, m.netMxn!!, 1e-9)
    }

    // ── Verdict branching: full data (both floors testable) ──────────────────────────────────

    @Test
    fun `GREEN when both floors met and data complete`() {
        // netPerKm 10, netPerHour 200; floors 6 / 90 -> both pass, no missing inputs
        val m = engine.evaluate(fullOffer(), profile, lenient)
        assertEquals(VerdictLevel.GREEN, m.verdict.level)
        assertTrue(m.verdict.missingInputs.isEmpty())
    }

    @Test
    fun `YELLOW when only per-km floor met`() {
        // raise the hour floor above 200 so only $/km passes
        val t = PlatformThreshold(minPerKmMxn = 6.0, minPerHourMxn = 250.0)
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `YELLOW when only per-hour floor met`() {
        // raise the km floor above 10 so only $/hr passes
        val t = PlatformThreshold(minPerKmMxn = 12.0, minPerHourMxn = 90.0)
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `RED when neither floor met`() {
        val t = PlatformThreshold(minPerKmMxn = 50.0, minPerHourMxn = 1000.0)
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.RED, m.verdict.level)
    }

    @Test
    fun `GREEN boundary is inclusive at exactly the floor`() {
        // netPerKm 10, netPerHour 200 -> floors set exactly equal should still pass (>=)
        val t = PlatformThreshold(minPerKmMxn = 10.0, minPerHourMxn = 200.0)
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.GREEN, m.verdict.level)
    }

    // ── Verdict branching: two-tier floors (yellow band between red and green) ───────────────

    @Test
    fun `YELLOW when both metrics land between their red and green floors`() {
        // netPerKm 10 in [9, 12); netPerHour 200 in [150, 250) -> both YELLOW -> YELLOW.
        val t = PlatformThreshold(
            minPerKmMxn = 12.0, minPerHourMxn = 250.0,
            redPerKmMxn = 9.0, redPerHourMxn = 150.0,
        )
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `RED only when both metrics fall below their red floors`() {
        // netPerKm 10 < 11; netPerHour 200 < 220 -> both RED -> RED.
        val t = PlatformThreshold(
            minPerKmMxn = 15.0, minPerHourMxn = 300.0,
            redPerKmMxn = 11.0, redPerHourMxn = 220.0,
        )
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.RED, m.verdict.level)
    }

    @Test
    fun `one GREEN metric lifts a below-red metric to overall YELLOW`() {
        // netPerKm 10 < red 11 -> RED; netPerHour 200 >= 90 -> GREEN; mixed -> YELLOW.
        val t = PlatformThreshold(
            minPerKmMxn = 15.0, minPerHourMxn = 90.0,
            redPerKmMxn = 11.0, redPerHourMxn = 67.5,
        )
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `red floor boundary is inclusive into the yellow band`() {
        // netPerKm exactly at the red floor reads YELLOW (>= red), not RED.
        val t = PlatformThreshold(
            minPerKmMxn = 15.0, minPerHourMxn = 250.0,
            redPerKmMxn = 10.0, redPerHourMxn = 150.0,
        )
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `red floor above green floor is clamped to green, never inverting the band`() {
        // Misconfigured: red 14 > green 6. Clamped red = 6, so netPerKm 10 >= 6 -> GREEN.
        val t = PlatformThreshold(
            minPerKmMxn = 6.0, minPerHourMxn = 90.0,
            redPerKmMxn = 14.0, redPerHourMxn = 67.5,
        )
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.GREEN, m.verdict.level)
    }

    @Test
    fun `default red floors derive from the green floors`() {
        val t = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0)
        assertEquals(6.0, t.redPerKmMxn, 1e-9)    // 8 * 0.75
        assertEquals(67.5, t.redPerHourMxn, 1e-9) // 90 * 0.75
    }

    @Test
    fun `verdict exposes per-metric levels for the UI explainer`() {
        // netPerKm 10 -> GREEN at floor 6; netPerHour 200 in [187.5, 250) -> YELLOW.
        val t = PlatformThreshold(minPerKmMxn = 6.0, minPerHourMxn = 250.0)
        val m = engine.evaluate(fullOffer(), profile, t)
        assertEquals(VerdictLevel.GREEN, m.verdict.netPerKmLevel)
        assertEquals(VerdictLevel.YELLOW, m.verdict.netPerHourLevel)
    }

    @Test
    fun `untestable metric exposes a null level`() {
        val offer = TripOffer(
            platform = "uber",
            fareMxn = 120.0,
            pickupKm = 2.0,
            pickupMin = null,
            tripKm = 8.0,
            tripMin = null,
        )
        val m = engine.evaluate(offer, profile, lenient)
        assertNull(m.verdict.netPerHourLevel)
        assertEquals(VerdictLevel.GREEN, m.verdict.netPerKmLevel)
    }

    // ── Verdict branching: missing fare ──────────────────────────────────────────────────────

    @Test
    fun `missing fare yields RED and null money fields`() {
        val m = engine.evaluate(fullOffer(fare = null), profile, lenient)
        assertEquals(VerdictLevel.RED, m.verdict.level)
        assertNull(m.grossMxn)
        assertNull(m.netMxn)
        assertNull(m.netPerKm)
        assertNull(m.netPerHour)
        assertNull(m.grossPerKm)
        assertTrue(m.verdict.missingInputs.contains("fareMxn"))
    }

    // ── Verdict branching: partial distance/time ─────────────────────────────────────────────

    @Test
    fun `partial data caps a would-be GREEN at YELLOW`() {
        // Everything passes, but pickup legs are missing -> provisional read -> YELLOW.
        val offer = TripOffer(
            platform = "uber",
            fareMxn = 120.0,
            pickupKm = null,
            pickupMin = null,
            tripKm = 8.0,
            tripMin = 24.0,
        )
        val m = engine.evaluate(offer, profile, lenient)
        // net = 120 - 2*8 = 104 over 8 km/24 min: perKm 13 (>=6), perHour 260 (>=90)
        assertEquals(104.0, m.netMxn!!, 1e-9)
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
        assertTrue(m.verdict.missingInputs.containsAll(listOf("pickupKm", "pickupMin")))
    }

    @Test
    fun `missing all distance makes per-km untestable but per-hour still judged`() {
        // No km at all; time present. Per-km floor cannot pass, so best case is YELLOW (hour ok).
        val offer = TripOffer(
            platform = "uber",
            fareMxn = 120.0,
            pickupKm = null,
            pickupMin = 6.0,
            tripKm = null,
            tripMin = 24.0,
        )
        val m = engine.evaluate(offer, profile, lenient)
        assertNull(m.totalKm)
        assertNull(m.netPerKm)
        assertEquals(240.0, m.netPerHour!!, 1e-9) // net == gross 120 (no km cost) over 0.5 h
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `missing all time makes per-hour untestable but per-km still judged`() {
        val offer = TripOffer(
            platform = "uber",
            fareMxn = 120.0,
            pickupKm = 2.0,
            pickupMin = null,
            tripKm = 8.0,
            tripMin = null,
        )
        val m = engine.evaluate(offer, profile, lenient)
        assertNull(m.totalMin)
        assertNull(m.netPerHour)
        assertEquals(10.0, m.netPerKm!!, 1e-9) // net 100 over 10 km
        assertEquals(VerdictLevel.YELLOW, m.verdict.level)
    }

    @Test
    fun `no distance and no time but fare present is RED`() {
        // Both floors untestable; fare known but nothing passes -> RED.
        val offer = TripOffer(
            platform = "uber",
            fareMxn = 120.0,
            pickupKm = null,
            pickupMin = null,
            tripKm = null,
            tripMin = null,
        )
        val m = engine.evaluate(offer, profile, lenient)
        assertNull(m.totalKm)
        assertNull(m.totalMin)
        assertEquals(120.0, m.netMxn!!, 1e-9) // net == gross (no km cost)
        assertEquals(VerdictLevel.RED, m.verdict.level)
        assertEquals(4, m.verdict.missingInputs.size) // all four legs
    }

    @Test
    fun `everything missing is RED with all inputs listed`() {
        val offer = TripOffer("uber", null, null, null, null, null)
        val m = engine.evaluate(offer, profile, lenient)
        assertEquals(VerdictLevel.RED, m.verdict.level)
        assertEquals(
            listOf("fareMxn", "pickupKm", "pickupMin", "tripKm", "tripMin"),
            m.verdict.missingInputs,
        )
    }

    @Test
    fun `zero total distance does not divide by zero`() {
        val offer = TripOffer("uber", fareMxn = 50.0, pickupKm = 0.0, pickupMin = 0.0, tripKm = 0.0, tripMin = 0.0)
        val m = engine.evaluate(offer, profile, lenient)
        assertEquals(0.0, m.totalKm!!, 1e-9)
        assertNull(m.netPerKm)   // denominator 0 -> null, not Infinity
        assertNull(m.netPerHour)
        assertEquals(VerdictLevel.RED, m.verdict.level) // nothing testable but fare known
    }

    // ── es-MX realistic numbers ──────────────────────────────────────────────────────────────

    @Test
    fun `realistic CDMX uber offer below the city median reads RED`() {
        // A typical lowball: $58 fare, 3 km dead + 9 km trip = 12 km, 8 + 22 = 30 min.
        // rendimiento 13 km/L at $24.5/L -> ~1.88 MXN/km fuel + 0.6 maint ~ 2.48 MXN/km.
        val fuel = CostProfile.fuelCostPerKmFrom(rendimientoKmPerLitre = 13.0, gasPricePerLitreMxn = 24.5)
        val cdmxProfile = CostProfile(fuelCostPerKm = fuel, maintenancePerKm = 0.6)
        val offer = TripOffer("uber", fareMxn = 58.0, pickupKm = 3.0, pickupMin = 8.0, tripKm = 9.0, tripMin = 22.0)
        // CDMX uber default floors: 8.05 MXN/km and 161 MXN/hr.
        val m = engine.evaluate(offer, cdmxProfile, PlatformThreshold(8.05, 161.0))
        // net ~ 58 - 2.48*12 = 28.2 over 12 km/30 min -> ~2.35 MXN/km, ~56 MXN/hr: both fail.
        assertTrue(m.netPerKm!! < 8.05)
        assertTrue(m.netPerHour!! < 161.0)
        assertEquals(VerdictLevel.RED, m.verdict.level)
    }

    @Test
    fun `realistic CDMX uber offer above the city median reads GREEN`() {
        val cdmxProfile = CostProfile(fuelCostPerKm = 1.9, maintenancePerKm = 0.6)
        // Short, well-paid: $140 fare, 1 km dead + 7 km trip = 8 km, 3 + 15 = 18 min.
        val offer = TripOffer("uber", fareMxn = 140.0, pickupKm = 1.0, pickupMin = 3.0, tripKm = 7.0, tripMin = 15.0)
        val m = engine.evaluate(offer, cdmxProfile, PlatformThreshold(8.05, 161.0))
        // net = 140 - 2.5*8 = 120 over 8 km/18 min -> 15 MXN/km, 400 MXN/hr: both pass.
        assertEquals(VerdictLevel.GREEN, m.verdict.level)
    }
}
