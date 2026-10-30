package mx.kompara.overlay

import mx.kompara.parsers.model.HostGeometry
import mx.kompara.parsers.model.HostOrientation
import mx.kompara.parsers.model.HostRect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Independently observed destination for the Uber Priority S25 layout, recorded in
 * uber_priority_s25_2026_10_29.json. This is a layout contract, not a general screen-percentage
 * guess: the display, upper anchors, both route rows, close and action must all match after one
 * rigid downward translation. Different host layouts/font sizes receive no early-placement grant.
 * Coordinates below use the reference display's pixels at density 3, scaled together at runtime.
 */
internal object UberSlideInEnvelope {
    data class Match(val highestCardTop: Int, val atDestination: Boolean, val movingControls: List<HostRect>)

    fun match(g: HostGeometry, v: OverlayViewport): Match? {
        if (g.packageName != "com.ubercab.driver" || v.orientation != HostOrientation.PORTRAIT ||
            g.isIdleHost || !v.density.isFinite() || v.density <= 0f) return null
        val scale = v.density / 3f
        fun px(value: Int) = (value * scale).roundToInt()
        if (abs(v.bounds.width - px(1080)) > v.dp(1) ||
            abs(v.bounds.height - px(2340)) > v.dp(1) || g.offerCard.bottom != v.bounds.bottom) return null
        val evidence = g.ocrEvidence ?: return null
        val tolerance = v.dp(8)
        val close = g.protectedControls.firstOrNull {
            abs(it.left - v.bounds.left - px(837)) <= tolerance &&
                abs(it.width - px(146)) <= tolerance && abs(it.height - px(143)) <= tolerance
        } ?: return null
        val translation = close.top - v.bounds.top - px(650)
        if (translation < -tolerance) return null
        fun expected(l: Int, t: Int, r: Int, b: Int) = HostRect(v.bounds.left + px(l),
            v.bounds.top + px(t) + translation, v.bounds.left + px(r), v.bounds.top + px(b) + translation)
        fun near(a: HostRect, b: HostRect) = abs(a.left - b.left) <= tolerance &&
            abs(a.top - b.top) <= tolerance && abs(a.right - b.right) <= tolerance &&
            abs(a.bottom - b.bottom) <= tolerance
        fun containsAll(actual: List<HostRect>, expected: List<HostRect>) =
            expected.all { wanted -> actual.any { near(it, wanted) } }
        if (!containsAll(evidence.upperAnchors, listOf(expected(96, 653, 502, 746),
                expected(530, 653, 785, 746), expected(837, 650, 983, 793))) ||
            !containsAll(evidence.routeAnchors, listOf(expected(190, 1420, 510, 1477),
                expected(190, 1625, 541, 1682))) ||
            !containsAll(g.protectedControls, listOf(expected(837, 650, 983, 793),
                expected(442, 1989, 640, 2046))) ||
            abs(g.offerCard.top - v.bounds.top - px(605) - translation) > tolerance) return null
        // Include the matching tolerance above the independently observed destination. Every
        // possible intermediate sheet position is below this ceiling during its upward slide.
        return Match(v.bounds.top + px(605) - tolerance, abs(translation) <= tolerance,
            listOf(close, g.protectedControls.first { near(it, expected(442, 1989, 640, 2046)) }))
    }
}
