package mx.kompara.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.HostWindowOwnership
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.LifecycleSignal
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** B-084 screen wording; header/dialog coordinates are modeled, not measured S25 captures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OwnedLifecycleEvidenceTest {
    private val host = UberOcrParser.UBER_PACKAGE
    private val display = HostRect(0, 0, 1080, 2340)
    private val chip = ScreenRect(325, 242, 1040, 572)
    private val header = block("Agenda de viajes", 140, 220)

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { index -> json.getJSONObject(index).let {
            OcrBlock(it.getString("text"), OcrBounds(it.getInt("left"), it.getInt("top"),
                it.getInt("right"), it.getInt("bottom")))
        } }
    }

    private inner class Replay {
        val pipeline = OfferFramePipeline()
        val events = mutableListOf<OfferEvent>()
        val signals = mutableListOf<LifecycleSignal>()
        val parsed get() = events.filterIsInstance<OfferEvent.Parsed>()
        private var at = 1_000L

        suspend fun frame(blocks: List<OcrBlock>, mask: ScreenRect? = chip,
            window: HostRect = display, excluded: List<HostRect> = emptyList()) {
            pipeline.process(blocks, mask, null, capturedAtMs = at, displayBounds = display,
                density = 3f, foregroundPackage = host,
                windowOwnership = HostWindowOwnership(host, at, display, 1, window, excluded))
            at += 900
            yield()
        }

        fun assertHeld() {
            assertTrue("Unproven lifecycle text cannot confirm disappearance",
                events.none { it is OfferEvent.NoCard && it.reason == OfferEvent.Reason.CONFIRMED_ABSENCE })
            assertTrue("No accepted, cancelled or idle lifecycle transition", signals.all { it is LifecycleSignal.OfferSeen })
            assertEquals(1, parsed.map { it.card.sessionId }.distinct().size)
            assertTrue(parsed.all { it.card.fare == 79.45 })
        }
    }

    private fun replay(body: suspend Replay.() -> Unit): Replay = runBlocking {
        val replay = Replay()
        val events = launch(Dispatchers.Unconfined) { OfferEventBus.events.collect { replay.events += it } }
        val signals = launch(Dispatchers.Unconfined) { OcrLifecycleBus.signals.collect { replay.signals += it } }
        try { replay.body() } finally { events.cancelAndJoin(); signals.cancelAndJoin() }
        replay
    }

    @Test fun `owned accepted header above former card retires offer exactly once`() {
        val result = replay {
            frame(fixture())
            frame(listOf(header))
            frame(listOf(header), mask = null)
        }
        assertEquals(1, result.signals.filterIsInstance<LifecycleSignal.TripStateEntered>().size)
        assertEquals(1, result.events.filterIsInstance<OfferEvent.NoCard>()
            .count { it.reason == OfferEvent.Reason.CONFIRMED_ABSENCE })
        assertTrue(result.signals.none { it is LifecycleSignal.IdleStateEntered })
    }

    @Test fun `accepted header outside selected host window cannot retire offer`() {
        replay {
            frame(fixture())
            frame(listOf(header), window = HostRect(0, 605, 1080, 2340))
            frame(fixture())
            assertEquals(2, parsed.size)
            assertHeld()
        }
    }

    @Test fun `accepted header in a foreign covering window cannot retire offer`() {
        replay {
            frame(fixture())
            frame(listOf(header), excluded = listOf(HostRect(0, 0, 1080, 240)))
            frame(fixture())
            assertEquals(2, parsed.size)
            assertHeld()
        }
    }

    @Test fun `accepted header masked by own chip cannot retire offer`() {
        replay {
            frame(fixture())
            frame(listOf(OcrBlock("Agenda de viajes", OcrBounds(350, 300, 900, 380))))
            frame(fixture())
            assertEquals(2, parsed.size)
            assertHeld()
        }
    }

    @Test fun `surviving offer signature vetoes accepted header elsewhere in window`() {
        replay {
            frame(fixture())
            frame(fixture() + header)
            frame(fixture())
            assertEquals(3, parsed.size)
            assertHeld()
        }
    }

    @Test fun `idle header outside former card remains ambiguous background`() {
        replay {
            frame(fixture())
            frame(listOf(block("Buscando viajes", 140, 220)))
            frame(fixture())
            assertEquals(2, parsed.size)
            assertHeld()
        }
    }

    @Test fun `Uber cancellation exclusion above card vetoes trip words inside card until real idle`() {
        // B-084:133-145: these are prompts, not proof that cancellation completed.
        replay {
            frame(fixture())
            frame(listOf(block("Acepté el viaje por error | ¿Quieres cancelar este viaje?", 140, 220),
                block("CANCELAR VIAJE | CONTINUAR CON EL VIAJE", 1900, 2000)))
            assertHeld()
            frame(listOf(block("Buscando viajes", 1900, 2000)), mask = null)
            assertEquals(1, signals.filterIsInstance<LifecycleSignal.IdleStateEntered>().size)
            assertTrue(signals.none { it is LifecycleSignal.TripStateEntered || it is LifecycleSignal.TripCancelled })
            frame(fixture())
            assertEquals(2, parsed.size)
            assertNotEquals(parsed.first().card.sessionId, parsed.last().card.sessionId)
        }
    }

    @Test fun `Uber cancel confirmation does not end an already accepted trip`() {
        replay {
            frame(fixture())
            frame(listOf(block("Agenda de viajes", 700, 780)))
            frame(listOf(block("Acepté el viaje por error | ¿Quieres cancelar este viaje?", 140, 220),
                block("CANCELAR VIAJE | CONTINUAR CON EL VIAJE", 1900, 2000)))
            assertEquals(1, signals.filterIsInstance<LifecycleSignal.TripStateEntered>().size)
            assertTrue(signals.none { it is LifecycleSignal.TripCancelled || it is LifecycleSignal.IdleStateEntered })
        }
    }

    private fun block(text: String, top: Int, bottom: Int) =
        OcrBlock(text, OcrBounds(96, top, 980, bottom))
}
