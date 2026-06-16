package mx.kompara.ocr

/**
 * Recover a decimal point that OCR dropped from a ride-hail *distance* reading.
 *
 * Uber Driver and DiDi Conductor both render trip/pickup distances with EXACTLY one decimal place
 * ("0.4 km", "1.0 km", "13.0 km", "45.6 km") — never a separator-less integer. So a km distance token
 * that survives OCR with no decimal separator and two or more digits ("130", "10", "04") is always a
 * dropped-decimal misread, and the point belongs immediately before the final digit:
 *   "130" → "13.0",  "10" → "1.0",  "04" → "0.4",  "456" → "45.6".
 * Left uncorrected this is a 10× error: a 13.0 km trip read as 130 km makes net $/km look ~10× too low
 * and paints a great ride RED — live capture 2026-06-15, fixture `ocr_1781569264865.json`
 * ("Viaje: 51 min (130 km)" + "A5 min (L0 km)" for a real 13.0 km / 1.0 km Uber offer).
 *
 * [normalized] must already be letter-substituted (O→0, l/I→1) and have any comma normalized to a '.';
 * this only reinserts a *missing* point, so a token that still carries its separator is returned
 * untouched. Single-digit tokens are left alone — a bare "5" is ambiguous (5.0? 0.5?) and is never how
 * a one-decimal distance renders. METERS must NOT be passed here: "862m" is a genuine integer, not a
 * dropped decimal — callers apply this only on the km branch.
 */
internal fun recoverDroppedDistanceDecimal(normalized: String): String =
    if (!normalized.contains('.') && normalized.length >= 2 && normalized.all { it.isDigit() }) {
        normalized.substring(0, normalized.length - 1) + "." + normalized.last()
    } else {
        normalized
    }
