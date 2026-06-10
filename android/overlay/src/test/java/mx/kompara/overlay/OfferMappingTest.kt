package mx.kompara.overlay

import mx.kompara.data.model.Platform
import mx.kompara.parsers.model.OfferCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the mechanical, null-safe OfferCard -> TripOffer mapping and platform resolution. */
class OfferMappingTest {

    @Test
    fun `maps every field across the rename`() {
        val card = OfferCard(
            platform = "com.ubercab.driver",
            fare = 87.5,
            pickupDistanceKm = 2.1,
            pickupEtaMin = 5.0,
            tripDistanceKm = 12.4,
            tripDurationMin = 25.0,
        )
        val trip = OfferMapping.toTripOffer(card)
        assertEquals("uber", trip.platform)
        assertEquals(87.5, trip.fareMxn!!, 1e-9)
        assertEquals(2.1, trip.pickupKm!!, 1e-9)
        assertEquals(5.0, trip.pickupMin!!, 1e-9)
        assertEquals(12.4, trip.tripKm!!, 1e-9)
        assertEquals(25.0, trip.tripMin!!, 1e-9)
    }

    @Test
    fun `nulls pass through untouched`() {
        val card = OfferCard(platform = "com.sdu.didi.gsui")
        val trip = OfferMapping.toTripOffer(card)
        assertEquals("didi", trip.platform)
        assertNull(trip.fareMxn)
        assertNull(trip.pickupKm)
        assertNull(trip.pickupMin)
        assertNull(trip.tripKm)
        assertNull(trip.tripMin)
    }

    @Test
    fun `partial card keeps present fields and nulls the rest`() {
        val card = OfferCard(
            platform = "uber",
            fare = 50.0,
            tripDistanceKm = 8.0,
            // pickup legs + trip duration missing
        )
        val trip = OfferMapping.toTripOffer(card)
        assertEquals(50.0, trip.fareMxn!!, 1e-9)
        assertEquals(8.0, trip.tripKm!!, 1e-9)
        assertNull(trip.pickupKm)
        assertNull(trip.pickupMin)
        assertNull(trip.tripMin)
    }

    @Test
    fun `platform resolution handles packages, names, and unknowns`() {
        assertEquals(Platform.UBER, OfferMapping.platformOf("com.ubercab.driver"))
        assertEquals(Platform.DIDI, OfferMapping.platformOf("com.sdu.didi.gsui"))
        assertEquals(Platform.UBER, OfferMapping.platformOf("Uber"))
        assertEquals(Platform.DIDI, OfferMapping.platformOf("DiDi"))
        assertEquals(Platform.INDRIVE, OfferMapping.platformOf("sinet.startup.inDriver"))
        assertEquals(Platform.UNKNOWN, OfferMapping.platformOf("com.example.notarideapp"))
    }
}
