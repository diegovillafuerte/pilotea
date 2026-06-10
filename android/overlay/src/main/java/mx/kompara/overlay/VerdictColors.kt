package mx.kompara.overlay

import androidx.compose.ui.graphics.Color
import mx.kompara.metrics.VerdictLevel

/**
 * Pure mapping from a [VerdictLevel] to its traffic-light colour. Kept out of the Composable so it
 * is unit-testable on the JVM without Robolectric/instrumentation.
 */
object VerdictColors {
    val Good = Color(0xFF2E7D32)
    val Marginal = Color(0xFFF9A825)
    val Bad = Color(0xFFC62828)

    fun forVerdict(level: VerdictLevel): Color = when (level) {
        VerdictLevel.GREEN -> Good
        VerdictLevel.YELLOW -> Marginal
        VerdictLevel.RED -> Bad
    }
}
