package mx.kompara.ocr

import org.junit.Assert.*
import org.junit.Test

/** Adversarial evidence independence; all text is synthetic and contains no addresses. */
class IndependentEvidenceReviewTest {
    private fun card(offset: Int = 0, fare: String = "MXN79.45") = listOf(
        OcrBlock("Uber Priority", OcrBounds(96, 300 + offset, 502, 380 + offset)),
        OcrBlock("Exclusivo", OcrBounds(530, 300 + offset, 785, 380 + offset)),
        OcrBlock(fare, OcrBounds(96, 420 + offset, 499, 540 + offset)),
        OcrBlock("A 1 min (0.2 km)", OcrBounds(190, 600 + offset, 510, 640 + offset)),
        OcrBlock("Viaje: 24 min (2.4 km)", OcrBounds(190, 800 + offset, 741, 840 + offset)),
        OcrBlock("Aceptar", OcrBounds(442, 1000 + offset, 640, 1040 + offset)),
    )

    @Test fun `missing primary heading alone is not two independent stack replacement signals`() {
        val parser = UberOcrParser()
        val stacked = card() + card(1100, "MXN149.45")
        val original = requireNotNull(parser.parse(stacked, hostConfirmed = true))
        assertEquals(2, original.instanceEvidence!!.visibleCards)
        val missingHeading = stacked.filterNot { it.text == "Uber Priority" && it.bounds.top == 300 }
        val partial = requireNotNull(parser.parse(missingHeading, hostConfirmed = true))
        assertEquals("Both host actions remain visible", 2, missingHeading.count { it.text == "Aceptar" })
        val sessions = OcrOfferSessionTracker()
        val first = requireNotNull(sessions.update(original, 1000).card)
        repeat(3) { index ->
            val next = sessions.update(partial, 1900L + index * 900)
            assertEquals("One lost heading cannot manufacture two independent replacement votes",
                first.sessionId, next.card!!.sessionId)
        }
    }

    @Test fun `malformed route recovery cannot supply payment revision agreement`() {
        val parser = UberOcrParser()
        val sessions = OcrOfferSessionTracker()
        val payments = FareStabilizer()
        fun sample(blocks: List<OcrBlock>, at: Long): Double? {
            val parsed = requireNotNull(parser.parse(blocks, hostConfirmed = true))
            val decision = sessions.update(parsed, at)
            return payments.onParsed(requireNotNull(decision.card), at,
                decision.confidence == OcrOfferSessionTracker.Confidence.CONFIDENT).fare
        }
        assertEquals(79.45, sample(card(), 1000)!!, 0.001)
        val malformed = card(fare = "MXN99.45").map {
            if (it.text == "A 1 min (0.2 km)") it.copy(text = "A 1 min 0.2 km)") else it
        }
        assertEquals(79.45, sample(malformed, 1900)!!, 0.001)
        assertEquals("A repaired route is the first confident revision sample", 79.45,
            sample(card(fare = "MXN99.45"), 2800)!!, 0.001)
        assertEquals(99.45, sample(card(fare = "MXN99.45"), 3700)!!, 0.001)
    }
}
