package mx.kompara.ui.components

import androidx.compose.runtime.Immutable
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal

enum class ReglaUnit(val suffix: String, val spoken: String) {
    KM("/km", "por km"), HOUR("/h", "por hora"),
}

enum class ReglaPosition(val phrase: String) {
    ABOVE("Arriba de tu meta"), MEETS("Cumple tu mínimo"), BELOW("Abajo de tu mínimo"),
}

enum class ReglaUnavailable(val headline: String, val reason: String = "sin cálculo") {
    FARE("Sin lectura", "falta la tarifa"), TIME("Falta tiempo"), DISTANCE("Falta distancia"),
    UNCONFIRMED("Sin confirmar"), READING("Leyendo oferta"),
}

/** A grey band cannot carry a trip rate, cue or categorical marker. */
@Immutable
sealed interface ReglaBand {
    data class Rated(val position: ReglaPosition, val rate: BigDecimal) : ReglaBand
    data class Unavailable(val input: ReglaUnavailable) : ReglaBand
}

/** One immutable projection for the overlay, onboarding and limits previews. No capture dependency. */
@Immutable
data class ReglaModel(
    val band: ReglaBand,
    val goal: BigDecimal,
    val minimum: BigDecimal,
    val unit: ReglaUnit = ReglaUnit.KM,
    val showLimitCents: Boolean = false,
) {
    val limitsHaveCents: Boolean get() = showLimitCents || listOf(goal, minimum).any { it.stripTrailingZeros().scale() > 0 }
    private fun limit(value: BigDecimal): String =
        (if (limitsHaveCents) Formatters.formatMxn(value) else Formatters.formatMxnWhole(value)) +
            if (unit == ReglaUnit.HOUR) unit.suffix else ""
    val goalLabel: String get() = "tu meta ${limit(goal)}"
    val minimumLabel: String get() = "tu mínimo ${limit(minimum)}"
    val rateText: String? get() = (band as? ReglaBand.Rated)?.let {
        if (unit == ReglaUnit.KM) Formatters.formatMxn(it.rate) else Formatters.formatMxnWhole(it.rate)
    }
    val spokenBand: String get() = when (val b = band) {
        is ReglaBand.Unavailable -> "${b.input.headline}: ${b.input.reason}"
        is ReglaBand.Rated -> "${b.position.phrase}: ${requireNotNull(rateText).replace("−", "menos ").replace("$", "")} pesos netos ${unit.spoken}"
    }
}

/** Durations are shared by the UI transition and the window's delayed detach. */
object ReglaMotion {
    const val ENTER_MS = 120
    const val REDUCED_ENTER_MS = 100
    const val EXIT_MS = 160
    const val RISE_DP = 16
    fun enterMs(reduced: Boolean): Int = if (reduced) REDUCED_ENTER_MS else ENTER_MS
    fun riseDp(reduced: Boolean): Int = if (reduced) 0 else RISE_DP
}
