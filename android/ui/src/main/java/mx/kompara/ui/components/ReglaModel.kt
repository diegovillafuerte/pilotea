package mx.kompara.ui.components

import androidx.compose.runtime.Immutable
import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.CounterOffers
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal

enum class ReglaUnit(val suffix: String, val spoken: String) {
    KM("/km", "por kilómetro"), HOUR("/h", "por hora"),
}

enum class ReglaPosition(val phrase: String) {
    ABOVE("Arriba de tu meta"), MEETS("Entre tu mínimo y tu meta"), BELOW("Abajo de tu mínimo"),
}

/** The unread pill's words. A missing fare, distance or time all read "Sin lectura" (plan 4.3). */
enum class ReglaUnavailable(val headline: String, val spoken: String = headline) {
    FARE("Sin lectura", "Sin lectura: no pudimos leer esta oferta"),
    TIME("Sin lectura", "Sin lectura: no pudimos leer esta oferta"),
    DISTANCE("Sin lectura", "Sin lectura: no pudimos leer esta oferta"),
    UNCONFIRMED("Sin confirmar"), READING("Leyendo oferta"),
}

/** An unread pill cannot carry a trip rate, a shape or counter-offers. */
@Immutable
sealed interface ReglaBand {
    data class Rated(val position: ReglaPosition, val rate: BigDecimal) : ReglaBand
    data class Unavailable(val input: ReglaUnavailable) : ReglaBand
}

/**
 * One immutable projection for the overlay, onboarding and limits previews. No capture dependency.
 * [counterOffers] are Pon Tu Precio's amounts (plan 4.4); only those still ahead of the verdict are drawn.
 */
@Immutable
data class ReglaModel(
    val band: ReglaBand,
    val unit: ReglaUnit = ReglaUnit.KM,
    val basis: RateBasis = RateBasis.DEFAULT,
    val counterOffers: CounterOffers? = null,
) {
    private val position: ReglaPosition? get() = (band as? ReglaBand.Rated)?.position
    val rateText: String? get() = (band as? ReglaBand.Rated)?.let {
        if (unit == ReglaUnit.KM) Formatters.formatMxn(it.rate) else Formatters.formatMxnWhole(it.rate)
    }
    val basisWord: String get() = if (basis == RateBasis.GROSS) "bruto" else "neto"

    /** Below the mínimo both amounts, between the limits only the meta's, otherwise none, whatever the arithmetic returned. */
    val trayMinimum: Int? get() = counterOffers?.toMinimum?.takeIf { position == ReglaPosition.BELOW }
    val trayGoal: Int? get() = counterOffers?.toGoal?.takeIf { position == ReglaPosition.BELOW || position == ReglaPosition.MEETS }
    val hasTray: Boolean get() = trayMinimum != null || trayGoal != null

    /** The chip's one accessible name. */
    val spokenBand: String get() = when (val b = band) {
        is ReglaBand.Unavailable -> b.input.spoken
        is ReglaBand.Rated -> "${b.position.phrase}: ${spoken(requireNotNull(rateText))} pesos ${unit.spoken}, $basisWord" + spokenTray()
    }

    private fun spoken(amount: String) = amount.replace("−", "menos ").replace("$", "")
    private fun spokenTray(): String {
        val minimum = trayMinimum?.let { spoken(Formatters.formatMxnWhole(it.toBigDecimal())) }
        val goal = trayGoal?.let { spoken(Formatters.formatMxnWhole(it.toBigDecimal())) }
        return when {
            minimum != null && goal != null -> ". Desde $minimum pesos alcanza tu mínimo; desde $goal, tu meta."
            minimum != null -> ". Desde $minimum pesos alcanza tu mínimo."
            goal != null -> ". Desde $goal pesos alcanza tu meta."
            else -> ""
        }
    }

    companion object {
        /** The in-app examples: a rated pill, without counter-offers. */
        fun rated(position: ReglaPosition, rate: BigDecimal, unit: ReglaUnit, basis: RateBasis): ReglaModel =
            ReglaModel(ReglaBand.Rated(position, rate), unit, basis)
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
