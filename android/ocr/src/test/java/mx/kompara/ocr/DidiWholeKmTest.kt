package mx.kompara.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 0.2.20: DiDi prints whole kilometres with no decimal on some cards (`59 min (30 km)`), and OCR can
 * also drop the point from a decimal (`31 min (50 km)` among `31 min (5.0 km)`). A km token with no
 * separator is the number as printed unless that is more than the row's own minutes (faster than
 * 60 km/h); then OCR dropped the point. Per row, per frame, no memory. Unread minutes keep the
 * printed value and metres are never touched.
 */
class DidiWholeKmTest {
    private val parser = DidiOcrParser()

    private fun card(fare: String, pickup: String, trip: String) = parser.parse(listOf(
        block(fare, 83, 973, 543, 1085),
        block(pickup, 139, 1692, 441, 1745),
        block(trip, 156, 1902, 514, 1960),
    ))!!

    /** The trip row of an otherwise ordinary card. */
    private fun trip(row: String) = card("\$99.00", "5 min (1.2 km)", row)

    private fun assertTripKm(row: String, km: Double) = assertEquals(row, km, trip(row).tripDistanceKm!!, 0.001)

    // DidiIconGlueTest's geometry: a row at [left], its address line in the text column at 152.
    private fun gluedCard(text: String, left: Int) = parser.parse(listOf(
        block("\$81.90", 84, 972, 481, 1086),
        block(text, left, 1700, left + 420, 1756),
        block("Calle Inventada 123", 152, 1770, 900, 1818),
    ))!!

    @Test fun `a 30 km trip on a June card reads 30, not 3`() {
        val read = card("\$180.79", "5 min (1.2 km)", "59 min (30 km)")
        assertEquals(1.2, read.pickupDistanceKm!!, 0.001)
        assertEquals(30.0, read.tripDistanceKm!!, 0.001)
        assertEquals(59.0, read.tripDurationMin!!, 0.001)
    }

    @Test fun `a 16 km trip behind a metres pickup reads 16, not 1_6`() {
        val read = card("\$75.13", "4 min (749 m)", "25 min (16 km)")
        assertEquals(0.749, read.pickupDistanceKm!!, 0.001)
        assertEquals(16.0, read.tripDistanceKm!!, 0.001)
    }

    @Test fun `a 13 km trip reads 13, not 1_3`() {
        val read = card("\$139.39", "7 min (1.5 km)", "55 min (13 km)")
        assertEquals(1.5, read.pickupDistanceKm!!, 0.001)
        assertEquals(13.0, read.tripDistanceKm!!, 0.001)
    }

    @Test fun `whole km up to the row's own minutes stay as printed`() {
        assertTripKm("30 min (30 km)", 30.0) // exactly 60 km/h
        assertTripKm("20 min (20 km)", 20.0)
        assertTripKm("120 min (105 km)", 105.0) // three digits follow the same rule
    }

    /** 21 km in 21 minutes computes 60.00000000000001 km/h: the rule compares km to minutes, not a speed. */
    @Test fun `km equal to minutes stays as printed where a speed would round above 60`() {
        assertTripKm("21 min (21 km)", 21.0)
        assertTripKm("11 min (11 km)", 11.0)
    }

    @Test fun `unread minutes keep the printed value`() {
        val read = trip("— min (30 km)")
        assertEquals(30.0, read.tripDistanceKm!!, 0.001)
        assertNull(read.tripDurationMin)
    }

    @Test fun `the rule sees the minutes after the icon is stripped`() {
        val read = gluedCard("925 min (16 km)", 97)
        assertEquals(25.0, read.pickupEtaMin!!, 0.001)
        assertEquals(16.0, read.pickupDistanceKm!!, 0.001)
    }

    @Test fun `questioned minutes that stay unread keep the printed value`() {
        val read = gluedCard("900 min (12 km)", 156)
        assertNull(read.pickupEtaMin)
        assertEquals(12.0, read.pickupDistanceKm!!, 0.001)
    }

    /** Decoding on the raw 912 minutes would give 14.0; the row's true 12 minutes say the point was lost. */
    @Test fun `a stripped icon can prove a dropped point`() {
        val read = gluedCard("912 min (14 km)", 97)
        assertEquals(12.0, read.pickupEtaMin!!, 0.001)
        assertEquals(1.4, read.pickupDistanceKm!!, 0.001)
    }

    @Test fun `a real dropped point is still restored`() {
        assertTripKm("31 min (50 km)", 5.0)
        assertTripKm("47 min (122 km)", 12.2)
    }

    @Test fun `just past the row's minutes the point goes back`() {
        assertTripKm("29 min (30 km)", 3.0)
        assertTripKm("19 min (20 km)", 2.0)
    }

    @Test fun `both legs of a card are decided on their own minutes`() {
        val read = card("\$50.63", "5 min (16km)", "59 min (30 km)")
        assertEquals(1.6, read.pickupDistanceKm!!, 0.001)
        assertEquals(30.0, read.tripDistanceKm!!, 0.001)
    }

    @Test fun `a single digit is never given a point`() {
        assertTripKm("8 min (2 km)", 2.0)
        assertTripKm("1 min (9 km)", 9.0)
    }

    @Test fun `a printed decimal is never touched`() {
        assertTripKm("9 min (16.4 km)", 16.4)
    }

    @Test fun `metres are never touched`() {
        assertTripKm("25 min (16 m)", 0.016)
    }

    private fun block(text: String, l: Int, t: Int, r: Int, b: Int) = OcrBlock(text, OcrBounds(l, t, r, b))
}
