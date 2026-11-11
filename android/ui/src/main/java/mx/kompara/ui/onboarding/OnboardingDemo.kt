package mx.kompara.ui.onboarding

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.components.*
import java.math.BigDecimal
import java.math.MathContext

/** Authored teaching offers from the kit (S1–S3), evaluated on device; never inserted into history. */
enum class DemoOffer(val fare: Double, val km: Double, val minutes: Double, val pickup: Double) {
    GREEN(148.0, 12.0, 24.0, 2.0), YELLOW(120.0, 12.0, 27.0, 2.0), RED(62.0, 12.3, 30.0, 2.3);
    fun model(): ReglaModel {
        val cost = BigDecimal("24.50").divide(BigDecimal(12), MathContext.DECIMAL128).add(BigDecimal("0.80"))
        val result = NetProfitEngine().evaluate(TripOffer("EXAMPLE", fare, pickup, 4.0, km - pickup, minutes - 4),
            cost, PlatformThreshold(8.0, 200.0, 6.0, 150.0), PreferredMetric.IPK)
        val position = when (result.verdict.level) {
            VerdictLevel.GREEN -> ReglaPosition.ABOVE; VerdictLevel.YELLOW -> ReglaPosition.MEETS; else -> ReglaPosition.BELOW
        }
        return ReglaModel.rated(position, result.perKmDecimal!!.toBigDecimal(), ReglaUnit.KM, RateBasis.NET)
    }
}

/** P1 steps green, yellow, red once and rests on green. */
data class DemoPlayback(val selected: DemoOffer = DemoOffer.GREEN, val step: Int = 0, val stopped: Boolean = false) {
    fun tick(): DemoPlayback = if (stopped) this else if (step >= DemoOffer.entries.size - 1) copy(selected = DemoOffer.GREEN, stopped = true)
        else copy(selected = DemoOffer.entries[step + 1], step = step + 1)
}
