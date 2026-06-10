package mx.kompara.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.metrics.CostProfile
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.VerdictLevel
import mx.kompara.parsers.OfferParser
import mx.kompara.parsers.ParsedOffer
import mx.kompara.data.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfferPipelineTest {

    private val fakeParser = object : OfferParser {
        override val platform = Platform.UBER
        override fun parse(textLines: List<String>): ParsedOffer? =
            if (textLines.isEmpty()) null
            else ParsedOffer(Platform.UBER, fareMxn = 120.0, distanceKm = 10.0, durationMin = 30.0)
    }

    private val engine = NetProfitEngine()

    @Test
    fun `parse runs the injected parser on the injected dispatcher`() = runTest {
        val pipeline = OfferPipeline(fakeParser, engine, StandardTestDispatcher(testScheduler))

        assertNull(pipeline.parse(emptyList()))

        val parsed = pipeline.parse(listOf("Uber"))
        assertNotNull(parsed)
        assertEquals(Platform.UBER, parsed!!.platform)
    }

    @Test
    fun `evaluate delegates to the supplied scorer`() = runTest {
        val pipeline = OfferPipeline(fakeParser, engine, StandardTestDispatcher(testScheduler))
        val offer = ParsedOffer(Platform.UBER, fareMxn = 120.0, distanceKm = 10.0, durationMin = 30.0)
        val profile = CostProfile(fuelCostPerKm = 1.0, maintenancePerKm = 0.0)

        val metrics: OfferMetrics = pipeline.evaluate(offer) { o ->
            engine.evaluate(
                offer = TripOffer(
                    platform = o.platform.name,
                    fareMxn = o.fareMxn,
                    pickupKm = null,
                    pickupMin = null,
                    tripKm = o.distanceKm,
                    tripMin = o.durationMin,
                ),
                costProfile = profile,
                threshold = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0),
            )
        }

        // net = 120 - 1*10 = 110; netPerKm = 11 (>=8), netPerHour = 220 (>=90).
        // pickup legs are unknown so the read is provisional -> capped at YELLOW.
        assertEquals(110.0, metrics.netMxn!!, 0.0001)
        assertEquals(VerdictLevel.YELLOW, metrics.verdict.level)
    }
}
