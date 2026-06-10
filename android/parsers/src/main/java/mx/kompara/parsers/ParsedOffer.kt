package mx.kompara.parsers

import mx.kompara.data.model.Platform

/**
 * The structured result of parsing a host-app offer card. Money in MXN, distance in km,
 * time in minutes. Produced by an [OfferParser] from a flattened node tree.
 */
data class ParsedOffer(
    val platform: Platform,
    val fareMxn: Double,
    val distanceKm: Double,
    val durationMin: Double,
    val pickupKm: Double? = null,
    val surge: Boolean = false,
)
