package mx.kompara.ui.fixtures

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import java.math.BigDecimal

/** Component scenarios only. Host placement is S5; fixture coordinates never enter production. */
fun V3Fixture.regla(frame: String): ReglaModel {
    val name = when (frame) {
        "C2", "N9" -> "amarillo"; "C3" -> "rojo"; "C7" -> "rojo_negativo"; else -> "verde"
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

@Composable
fun ReglaFixtureHost(frame: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fixture = remember(context) { V3Fixture(context) }
    val dark = frame.startsWith("N")
    // Horizontal inspection at extreme font scales does not pretend the chip fits a live host.
    Box(modifier.fillMaxSize().background(if (dark) Color(0xFF262C33) else Color(0xFFEBEEF4)), contentAlignment = Alignment.Center) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Regla(fixture.regla(frame), Modifier.testTag("regla"), compact = frame == "C12", darkHost = dark)
        }
    }
}
