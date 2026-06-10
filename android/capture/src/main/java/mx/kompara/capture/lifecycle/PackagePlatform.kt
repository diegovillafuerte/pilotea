package mx.kompara.capture.lifecycle

import mx.kompara.data.model.Platform

/**
 * Maps a host package id to the canonical [Platform] for trip-log persistence (B-039).
 *
 * A tiny, local copy of the same substring-fallback logic `:overlay` uses (`OfferMapping.platformOf`)
 * — duplicated rather than shared because `:capture` must NOT depend on `:overlay` (siblings own it,
 * and the dependency would point the wrong way). Kept pure so it's trivially testable.
 */
internal object PackagePlatform {

    fun of(packageName: String): Platform {
        val key = packageName.trim().lowercase()
        return when {
            key.contains("uber") -> Platform.UBER
            key.contains("didi") -> Platform.DIDI
            key.contains("indrive") || key.contains("indriver") -> Platform.INDRIVE
            else -> Platform.UNKNOWN
        }
    }
}
