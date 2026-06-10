package mx.kompara.capture.lifecycle

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import mx.kompara.capture.EventPipeline
import mx.kompara.capture.HostVersionCodes
import mx.kompara.capture.OfferEventPipeline
import mx.kompara.capture.ScreenSnapshot
import mx.kompara.capture.SnapshotNode
import mx.kompara.capture.SnapshotReader
import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.scrub.SnapshotScrubber
import mx.kompara.parsers.spec.ActiveSpecProvider
import mx.kompara.parsers.spec.BundledSpecs
import mx.kompara.parsers.spec.LoadedSpecs
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Classification in [OfferEventLifecycleMapper] (B-039): a parsed card → OfferSeen; a non-card screen
 * → TripStateEntered or IdleStateEntered per [TripStateMarkers]. Uses the real bundled
 * pipeline (Robolectric for `android.graphics.Rect`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OfferEventLifecycleMapperTest {

    private val uber = "com.ubercab.driver"

    private fun offerPipeline(): OfferEventPipeline {
        val event = EventPipeline(SnapshotReader { _, _ -> null }, UnconfinedTestDispatcher(), 80L)
        val provider = object : ActiveSpecProvider {
            override val specs = MutableStateFlow(LoadedSpecs.bundled(BundledSpecs.load()))
        }
        return OfferEventPipeline(
            source = event,
            activeSpecs = provider,
            engine = SpecEngine(),
            scrubber = SnapshotScrubber(),
            versionCodes = HostVersionCodes { 1000L },
        )
    }

    private fun mapper() = OfferEventLifecycleMapper(
        eventPipeline = EventPipeline(SnapshotReader { _, _ -> null }, UnconfinedTestDispatcher(), 80L),
        offerPipeline = offerPipeline(),
        markers = TripStateMarkers.DEFAULT,
    )

    private fun node(text: String?, viewId: String? = null, top: Int = 0) = SnapshotNode(
        text = text,
        viewId = viewId,
        className = "android.view.View",
        bounds = Rect(0, top, 700, top + 50),
        depth = 3,
        index = top,
    )

    @Test
    fun `a parsed offer snapshot maps to OfferSeen`() {
        val snapshot = ScreenSnapshot(
            packageName = uber,
            timestampMs = 10L,
            nodes = listOf(
                node("UberX", top = 0),
                node("MX\$87.50", viewId = "com.ubercab.driver:id/upfront_fare", top = 100),
                node("5 min (2.1 km) de distancia", top = 300),
                node("25 min (12.4 km) de viaje", top = 400),
                node("Aceptar", top = 500),
            ),
        )
        val signal = mapper().classify(snapshot)
        assertTrue(signal is LifecycleSignal.OfferSeen)
    }

    @Test
    fun `a non-card screen with a trip marker maps to TripStateEntered`() {
        val snapshot = ScreenSnapshot(
            packageName = uber,
            timestampMs = 20L,
            nodes = listOf(
                node("Llegando", viewId = "com.ubercab.driver:id/navigation_panel", top = 0),
                node("4.2 km", top = 100),
            ),
        )
        val signal = mapper().classify(snapshot)
        assertTrue(signal is LifecycleSignal.TripStateEntered)
    }

    @Test
    fun `a non-card screen with no trip marker maps to IdleStateEntered`() {
        val snapshot = ScreenSnapshot(
            packageName = uber,
            timestampMs = 30L,
            nodes = listOf(
                node("Estás en línea", viewId = "com.ubercab.driver:id/home_status", top = 0),
            ),
        )
        val signal = mapper().classify(snapshot)
        assertTrue(signal is LifecycleSignal.IdleStateEntered)
    }
}
