package mx.kompara.capture

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mx.kompara.data.db.entity.CostProfileEntity
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.OfferMetrics
import mx.kompara.metrics.Verdict
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
        val profile = CostProfileEntity(
            updatedAt = 0L,
            fuelPerKmMxn = 1.0,
            maintenancePerKmMxn = 0.0,
            insurancePerDayMxn = 0.0,
            rentPerDayMxn = 0.0,
        )

        val metrics: OfferMetrics = pipeline.evaluate(offer) { o ->
            engine.evaluate(
                grossMxn = o.fareMxn,
                distanceKm = o.distanceKm,
                durationMin = o.durationMin,
                costProfile = profile,
                threshold = PlatformThreshold(minPerKmMxn = 8.0, minPerHourMxn = 90.0),
            )
        }

        // net = 120 - 1*10 = 110; perKm = 11 (>=8), perHour = 220 (>=90) -> GOOD
        assertEquals(110.0, metrics.netMxn, 0.0001)
        assertEquals(Verdict.GOOD, metrics.verdict)
    }
}
