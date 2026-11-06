package mx.kompara.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.parsers.model.HostRect
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * 0.2.15 pipeline rules for the card's own arithmetic (spec D.6): an identity-validated frame is
 * economically admitted where the outlier window would reject it; a contradictory frame never enters
 * that window; the TEMP DIAG line carries the validation as codes only, never an amount.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfferFramePipelineValidationTest {
    @Before fun foreground() {
        ScreenReaderState.setForegroundHost(UberOcrParser.UBER_PACKAGE, android.os.SystemClock.elapsedRealtime())
    }

    private fun fixture(): List<OcrBlock> {
        val json = JSONArray(javaClass.getResource("/uber_priority_s25_2026_10_29.json")!!.readText())
        return List(json.length()) { i ->
            val b = json.getJSONObject(i)
            OcrBlock(b.getString("text"), OcrBounds(b.getInt("left"), b.getInt("top"), b.getInt("right"), b.getInt("bottom")))
        }
    }

    private class Run(val events: List<List<OfferEvent>>, val logs: List<String>) {
        fun parsed(frame: Int) = events[frame].filterIsInstance<OfferEvent.Parsed>().singleOrNull()
        fun diag(frame: Int) = logs.filter { it.startsWith("TEMP DIAG frame") }[frame]
    }

    private fun run(frames: List<List<OcrBlock>>, stepMs: Long = 850, chips: List<mx.kompara.data.service.ScreenRect?> = emptyList()): Run = runBlocking {
        ShadowLog.clear()
        val events = mutableListOf<OfferEvent>()
        val collector = launch(Dispatchers.Unconfined) { OfferEventBus.events.collect { events += it } }
        val perFrame = mutableListOf<List<OfferEvent>>()
        try {
            val pipeline = OfferFramePipeline()
            for ((index, blocks) in frames.withIndex()) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(stepMs))
                val before = events.size
                pipeline.processOwnedFrame(blocks, chips.getOrNull(index), null, displayBounds = HostRect(0, 0, 1080, 2340), density = 3f,
                    foregroundPackage = UberOcrParser.UBER_PACKAGE)
                yield()
                perFrame += events.drop(before)
            }
        } finally { collector.cancelAndJoin() }
        Run(perFrame, ShadowLog.getLogsForTag("KomparaOCR").map { it.msg })
    }

    private val lost = { b: List<OcrBlock> -> b.map { if (it.text == "1 min (0.2 km)") it.copy(text = "1 min (2 km)") else it } }

    @Test fun `an identity-validated frame is admitted where the outlier window rejects it, a legacy frame is held`() {
        // f1's decimal-lost pickup (TOTAL row lost: legacy path) skews the window; the correct f2 then
        // reads 0.2 km against a 1.1 km median (5.5x). With its TOTAL row f2 proves its own legs.
        val validated = run(listOf(fixture(), lost(fixture()).withoutTotalRow(), fixture()))
        assertTrue("The validated frame publishes", validated.parsed(2)?.card?.fare == 79.45)
        assertTrue(validated.diag(2), "guardBypassedByIdentity=true" in validated.diag(2))
        val legacy = run(listOf(fixture(), lost(fixture()).withoutTotalRow(), fixture().withoutTotalRow()))
        assertTrue("Without the card's arithmetic the guard still holds the frame",
            legacy.parsed(2) == null && legacy.events[2].any { it is OfferEvent.LayoutUncertain })
        assertTrue(legacy.diag(2), "guardBypassedByIdentity=false" in legacy.diag(2))
    }

    @Test fun `a contradictory frame never seeds the outlier window`() {
        // Two contradictory $7945.00 frames would move the median to ~4012 if they were recorded; the
        // legacy-path $7945.00 garble after them is still judged against the offer's own 79.45 (all five
        // frames inside the guard's 3 s window).
        val garble = { b: List<OcrBlock> -> b.map { if (it.text == "$79.45") it.copy(text = "$7945.00") else it } }
        val result = run(listOf(fixture(), fixture(), garble(fixture()), garble(fixture()), garble(fixture()).withoutTotalRow()),
            stepMs = 500)
        assertTrue(result.diag(2), "paymentSuppressedByContradiction=true" in result.diag(2))
        assertTrue("Each contradictory frame publishes Sin lectura in the session",
            listOf(2, 3).all { result.parsed(it)?.card?.let { c -> c.fare == null && c.sessionId == result.parsed(0)!!.card.sessionId } == true })
        assertTrue("The legacy garble is still an outlier: held, never published",
            result.parsed(4) == null && result.events[4].any { it is OfferEvent.LayoutUncertain })
    }

    @Test fun `a checksum-recovered trip dimension completes the route and starts a session`() = runBlocking {
        // Real G0[20]: the trip row reads "dlZ min (4.2 km)"; 4.9 = 0.7 + 4.2 km proves the route, so its
        // minutes are TOTAL 20 - pickup 3 = 17 (RECOVERED) and this frame alone starts the offer.
        val g020 = UberS25RealCorpus.groups[0].frames[20]
        val events = mutableListOf<OfferEvent>()
        val collector = launch(Dispatchers.Unconfined) { OfferEventBus.events.collect { events += it } }
        try {
            ShadowLog.clear()
            OfferFramePipeline().processOwnedFrame(g020.blocks, g020.chip, null, displayBounds = HostRect(0, 0, 1080, 2340),
                density = 3f, foregroundPackage = UberOcrParser.UBER_PACKAGE)
            yield()
        } finally { collector.cancelAndJoin() }
        val card = events.filterIsInstance<OfferEvent.Parsed>().single().card
        assertTrue("$card", card.sessionId != null && card.fare == 71.04 && card.tripDurationMin == 17.0 &&
            card.tripDistanceKm == 4.2 && card.pickupEtaMin == 3.0 && card.pickupDistanceKm == 0.7)
        val diag = ShadowLog.getLogsForTag("KomparaOCR").map { it.msg }.single { it.startsWith("TEMP DIAG frame") }
        assertTrue(diag, "grade=1" in diag && "recoveryOperands=${(1 shl 2) or (1 shl 4)}" in diag && "minuteEval=false" in diag)
    }

    private val g0 = UberS25RealCorpus.groups[0].frames
    private fun List<OcrBlock>.retext(from: String, to: String) = also { require(any { it.text == from }) { from } }
        .map { if (it.text == from) it.copy(text = to) else it }
    private fun List<OcrBlock>.without(vararg texts: String) = also { b -> texts.forEach { t -> require(b.any { it.text == t }) { t } } }
        .filterNot { it.text in texts }

    @Test fun `a contradiction latches identity uncertainty until the next card is confirmed`() {
        // Codex round-2 sequence on real G0 geometry: card A ($111.75) confirmed; card B's first frame
        // misreads $84.47 as $84.41 (CONTRADICTORY); B's next frames lose headline and trip row
        // (UNVERIFIABLE, route-incomplete). A's payment must never return over B; two consistent B frames
        // then confirm B with its own economics.
        val unreadB = g0[7].blocks.without("\$84.47", "15 min (3.3 km)")
        val frames = listOf(g0[1].blocks, g0[6].blocks.retext("\$84.47", "\$84.41")) + List(4) { unreadB } +
            listOf(g0[8].blocks, g0[9].blocks)
        val chips = listOf(g0[1].chip, g0[6].chip) + List(4) { g0[7].chip } + listOf(g0[8].chip, g0[9].chip)
        val result = run(frames, chips = chips)
        val a = result.parsed(0)!!.card
        assertTrue("A is confirmed: $a", a.fare == 111.75 && a.sessionId != null)
        for (i in 1..5) {
            val card = result.parsed(i)?.card
            assertTrue("f$i must publish without payment, never A's: $card", card != null && card.fare == null)
        }
        assertTrue("B's first consistent frame only votes: ${result.parsed(6)?.card}", result.parsed(6)?.card?.fare == null)
        val b = result.parsed(7)!!.card
        assertTrue("B confirmed with its own economics: $b", b.sessionId != a.sessionId && b.fare == 84.47 &&
            b.pickupEtaMin == 4.0 && b.pickupDistanceKm == 1.0 && b.tripDurationMin == 15.0 && b.tripDistanceKm == 3.3)
    }

    @Test fun `a contradiction of the same card keeps payment withheld through unread frames until a consistent frame`() {
        // Mirror: A's own fare misread ($111.15 contradicts $8.87/km x 12.6 km), then A frames without the
        // headline (UNVERIFIABLE: route validated, fare unread) cannot clear the latch; the first CONFIDENT
        // CONSISTENT frame of A's trip leg re-establishes it (G0[4]'s heading still slides 61 px, so it is
        // uncertain; G0[5] is steady) and the confirmed fare resumes in the same session.
        val frames = listOf(g0[1].blocks, g0[2].blocks.retext("\$111.75", "\$111.15")) +
            List(3) { g0[3].blocks.without("\$111.75") } + listOf(g0[4].blocks, g0[5].blocks)
        val result = run(frames)
        val sid = result.parsed(0)!!.card.sessionId
        for (i in 1..4) {
            val card = result.parsed(i)?.card
            assertTrue("f$i withholds payment in A's session: $card", card != null && card.fare == null && card.sessionId == sid)
        }
        assertTrue("A moving frame never shows another payment", result.parsed(5)?.card?.fare in setOf(null, 111.75))
        val resumed = result.parsed(6)!!.card
        assertTrue("$resumed", resumed.fare == 111.75 && resumed.sessionId == sid && resumed.tripDistanceKm == 11.8)
    }

    @Test fun `the frame diagnostic carries the validation as codes and never an amount, token or text`() {
        val result = run(listOf(fixture(), fixture().withTotalRow("25 min • 2.6 km • $3O.56/km est.")))
        val consistent = result.diag(0)
        for (key in listOf("validationVersion=1", "grade=0", "selectionCertain=true", "fareCandidates=1",
            "completeRoute=true", "minuteEval=true", "minuteResidual=0", "distanceEval=true", "distanceResidualTenths=0",
            "moneyEval=true", "moneyWithinBound=true", "guardBypassedByIdentity=false",
            "paymentSuppressedByContradiction=false", "recoveryOperands=0", "repairBits=0")) {
            assertTrue("$key in $consistent", key in consistent)
        }
        // Every slot read as printed: 0b01 per slot.
        assertTrue(consistent, "provenanceBits=${0b0101010101010101}" in consistent)
        assertTrue(consistent, Regex("""moneyResidualPpm=\d+ """).containsMatchIn(consistent))
        val repaired = result.diag(1)
        assertTrue(repaired, "grade=1" in repaired && "repairBits=1" in repaired)
        for (line in result.logs) for (secret in listOf("79.45", "30.56", "3O.56", "Calle Falsa", "Avenida Ficticia"))
            assertFalse("$secret logged: $line", secret in line)
    }
}
