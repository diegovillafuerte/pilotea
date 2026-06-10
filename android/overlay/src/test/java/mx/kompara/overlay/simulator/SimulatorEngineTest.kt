package mx.kompara.overlay.simulator

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.metrics.CostProfile
import mx.kompara.metrics.VerdictLevel
import mx.kompara.parsers.snapshot.DemoSnapshots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the offer simulator runs the demo fixtures through the **real** pipeline (spec engine →
 * mapping → metrics) rather than rendering hardcoded verdicts, and that changing the driver's
 * threshold re-grades the demo offers live.
 */
class SimulatorEngineTest {

    private val engine = SimulatorEngine.bundled()
    private val zeroCosts = CostProfile.ZERO
    private val defaultThreshold = PlatformThreshold.DEFAULT // 8 $/km, 90 $/h

    // --- Fixtures load + parse end-to-end ------------------------------------------------------

    @Test
    fun everyDemoFixtureLoadsAndParsesThroughRealPipeline() {
        for (offer in DemoSnapshots.all()) {
            val result = engine.evaluate(offer, zeroCosts, defaultThreshold)
            // The spec engine actually recognized the snapshot and extracted a fare — not a stub.
            assertNotNull("fare for ${offer.id}", result.card.fare)
            assertTrue("visible text for ${offer.id}", result.visibleText.isNotEmpty())
            // A real verdict (with real net numbers) came out the far end.
            assertNotNull("verdict for ${offer.id}", result.metrics.verdict.level)
        }
    }

    // --- The three economic shapes land where the guided script promises ------------------------

    @Test
    fun goodOffersAreGreenMarginalAreYellowBadAreRedAtDefaultThreshold() {
        for (offer in DemoSnapshots.all()) {
            val level = engine.evaluate(offer, zeroCosts, defaultThreshold).metrics.verdict.level
            val expected = when (offer.shape) {
                DemoSnapshots.Shape.GOOD -> VerdictLevel.GREEN
                DemoSnapshots.Shape.MARGINAL -> VerdictLevel.YELLOW
                DemoSnapshots.Shape.BAD -> VerdictLevel.RED
            }
            assertEquals("verdict for ${offer.id}", expected, level)
        }
    }

    // --- Threshold playground actually re-grades (no hardcoded verdicts) ------------------------

    @Test
    fun thresholdFlipsVerdict() {
        // A marginal offer is YELLOW at the default floor: it clears the $/h floor but not $/km.
        val marginal = DemoSnapshots.UBER.first { it.shape == DemoSnapshots.Shape.MARGINAL }

        val atDefault = engine.evaluate(marginal, zeroCosts, defaultThreshold).metrics.verdict.level
        assertEquals(VerdictLevel.YELLOW, atDefault)

        // Drop the $/km floor well below the offer's net $/km → now BOTH floors pass → GREEN.
        val lowFloor = PlatformThreshold(minPerKmMxn = 3.0, minPerHourMxn = 60.0)
        val atLowFloor = engine.evaluate(marginal, zeroCosts, lowFloor).metrics.verdict.level
        assertEquals(VerdictLevel.GREEN, atLowFloor)

        // Raise the $/km floor above the offer's net $/km → neither floor passes → RED.
        val highFloor = PlatformThreshold(minPerKmMxn = 20.0, minPerHourMxn = 200.0)
        val atHighFloor = engine.evaluate(marginal, zeroCosts, highFloor).metrics.verdict.level
        assertEquals(VerdictLevel.RED, atHighFloor)
    }

    @Test
    fun costProfileShrinksNetSoAGreenCanFallToRed() {
        val good = DemoSnapshots.UBER.first { it.shape == DemoSnapshots.Shape.GOOD }
        val greenAtZeroCost = engine.evaluate(good, zeroCosts, defaultThreshold).metrics.verdict.level
        assertEquals(VerdictLevel.GREEN, greenAtZeroCost)

        // Punitively high marginal cost per km eats the net so it can no longer clear the floors.
        val expensive = CostProfile(fuelCostPerKm = 12.0, maintenancePerKm = 6.0)
        val degraded = engine.evaluate(good, expensive, defaultThreshold).metrics
        assertTrue(
            "net $/km should drop below the floor under heavy costs",
            (degraded.netPerKm ?: Double.MAX_VALUE) < defaultThreshold.minPerKmMxn,
        )
        assertEquals(VerdictLevel.RED, degraded.verdict.level)
    }
}
