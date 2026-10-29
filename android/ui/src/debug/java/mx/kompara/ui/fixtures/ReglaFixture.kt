package mx.kompara.ui.fixtures

import mx.kompara.ui.components.*
import java.math.BigDecimal

/** Frozen offer states from the kit; fixture values never enter production. */
fun V3Fixture.regla(frame: String): ReglaModel {
    require(frame !in setOf("C6", "C11", "C13")) { "$frame has no verdict plate" }
    val name = when (frame) {
        "C2", "C9", "N9" -> "amarillo"; "C3" -> "rojo"; "C7" -> "rojo_negativo"; else -> "verde"
    }
    val goal = goal.toBigDecimal()
    val minimum = minimum.toBigDecimal()
    val rate = requireNotNull(chip(name).ratePerKm)
    val band = when (frame) {
        "C4", "N10" -> ReglaBand.Unavailable(ReglaUnavailable.FARE)
        "C5" -> ReglaBand.Unavailable(ReglaUnavailable.TIME)
        else -> ReglaBand.Rated(when {
            rate >= goal -> ReglaPosition.ABOVE
            rate >= minimum -> ReglaPosition.MEETS
            else -> ReglaPosition.BELOW
        }, rate)
    }
    return if (frame == "C5") ReglaModel(band, BigDecimal(200), BigDecimal(150), ReglaUnit.HOUR)
        else ReglaModel(band, goal, minimum)
}
