package mx.kompara.ui.fixtures

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.kompara.ui.components.ReaderStoppedTab
import mx.kompara.ui.components.Regla
import mx.kompara.ui.theme.KomparaType

/** Debug host illustration only. Its frozen coordinates never authorize live overlay placement. */
internal data class ReglaScene(
    val cardTop: Int? = 545,
    val plateLeft: Float = 141f,
    val plateTop: Float = 417f,
    val navigation: Boolean = false,
    val compact: Boolean = false,
    val fontScale: Float = 1f,
    val stopped: Boolean = false,
    val suppressed: Boolean = false,
    val permissionLost: Boolean = false,
    val fare: String = "$148.00",
    val clock: String = "14:32",
) {
    val hasVerdict get() = !stopped && !suppressed && !permissionLost
}

internal fun reglaScene(frame: String): ReglaScene = when (frame) {
    "C1", "N8" -> ReglaScene()
    "C2", "N9" -> ReglaScene(fare = "$120.00", clock = "14:50")
    "C3" -> ReglaScene(fare = "$62.00", clock = "12:10")
    "C4", "N10" -> ReglaScene(fare = "—", clock = "11:48")
    "C6" -> ReglaScene(cardTop = null, plateTop = 457f, stopped = true, clock = "8:01")
    "C7" -> ReglaScene(fare = "$22.00", clock = "12:10")
    "C8" -> ReglaScene(cardTop = 316, plateLeft = 12f, plateTop = 147.5f)
    "C9" -> ReglaScene(plateTop = 243f, navigation = true, fare = "$120.00", clock = "14:50")
    "C10" -> ReglaScene(cardTop = 469, plateLeft = 69f, plateTop = 307.4f, fontScale = 1.3f)
    "C11" -> ReglaScene(cardTop = 260, navigation = true, suppressed = true, fare = "$62.00", clock = "12:10")
    "C12" -> ReglaScene(cardTop = 342, plateTop = 246f, compact = true)
    "C13" -> ReglaScene(cardTop = null, permissionLost = true, clock = "8:01")
    else -> error("No overlay scene for $frame")
}

@Composable
fun ReglaFixtureHost(frame: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fixture = remember(context) { V3Fixture(context) }
    val scene = reglaScene(frame)
    val density = LocalDensity.current
    val dark = frame.startsWith("N")
    // Use the actual component for both objects. The host is a non-interactive illustration, not
    // a fake Kompara navigation/notification surface or a second verdict renderer.
    Row(modifier.horizontalScroll(rememberScrollState())) {
        Box(Modifier.requiredWidth(393.dp).height(852.dp).testTag("host-$frame")) {
            ReglaHostIllustration(frame, scene, dark)
            CompositionLocalProvider(LocalDensity provides Density(density.density, maxOf(density.fontScale, scene.fontScale))) {
                val position = Modifier.offset(scene.plateLeft.dp, scene.plateTop.dp)
                when {
                    scene.stopped -> ReaderStoppedTab(position.testTag("stopped-tab"))
                    scene.hasVerdict -> Regla(fixture.regla(frame), position.testTag("regla"), compact = scene.compact, darkHost = dark)
                    // C11 retains the host offer; C13 retains the idle host. Neither paints a chip.
                }
            }
        }
    }
}

@Composable
private fun ReglaHostIllustration(frame: String, scene: ReglaScene, dark: Boolean) {
    val ink = if (dark) Color(0xFFE8EDF1) else Color(0xFF111111)
    val paper = if (dark) Color(0xFF1F2329) else Color.White
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF262C33) else Color(0xFFECEFF5))) {
        Canvas(Modifier.fillMaxSize()) {
            val road = if (dark) Color(0xFF414853) else Color.White
            for (y in listOf(190f, 280f, 385f, 515f, 640f, 760f)) {
                drawLine(road, Offset(0f, y.dp.toPx()), Offset(size.width, (y - 80).dp.toPx()), 9.dp.toPx())
            }
            drawLine(road, Offset(85.dp.toPx(), 128.dp.toPx()), Offset(300.dp.toPx(), size.height), 15.dp.toPx())
            drawLine(road, Offset(335.dp.toPx(), 128.dp.toPx()), Offset(130.dp.toPx(), size.height), 10.dp.toPx())
            val driver = when (frame) { "C9" -> Offset(96f, 508f); "C12" -> Offset(196f, 190f); else -> Offset(96f, 684f) }
            drawCircle(ink, (if (frame == "C12") 37 else 19).dp.toPx(), Offset(driver.x.dp.toPx(), driver.y.dp.toPx()))
            drawCircle(paper, (if (frame == "C12") 25 else 14).dp.toPx(), Offset(driver.x.dp.toPx(), driver.y.dp.toPx()))
            val chevron = androidx.compose.ui.graphics.Path().apply {
                moveTo(driver.x.dp.toPx(), (driver.y - 10).dp.toPx())
                lineTo((driver.x + 9).dp.toPx(), (driver.y + 7).dp.toPx())
                lineTo(driver.x.dp.toPx(), (driver.y + 3).dp.toPx())
                lineTo((driver.x - 9).dp.toPx(), (driver.y + 7).dp.toPx()); close()
            }
            drawPath(chevron, ink)
            if (scene.cardTop != null && frame != "C12") {
                val pickupY = when (frame) { "C8", "C10" -> 284f; "C9" -> 497f; else -> 392f }
                val route = androidx.compose.ui.graphics.Path().apply {
                    moveTo(96.dp.toPx(), (if (frame == "C9") 508 else 684).dp.toPx())
                    if (frame != "C9") lineTo(96.dp.toPx(), pickupY.dp.toPx())
                    lineTo(218.dp.toPx(), pickupY.dp.toPx())
                    lineTo(218.dp.toPx(), (when (frame) { "C8" -> 156; "C9" -> 380; else -> 264 }).dp.toPx())
                    lineTo(330.dp.toPx(), (when (frame) { "C8" -> 156; "C9" -> 380; else -> 264 }).dp.toPx())
                }
                drawPath(route, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx()))
                if (frame != "C9") drawCircle(Color(0xFF4C8DFF), 8.dp.toPx(), Offset(218.dp.toPx(), pickupY.dp.toPx()))
                val destinationY = when (frame) { "C8" -> 156f; "C9" -> 380f; else -> 264f }
                drawRect(ink, Offset(321.dp.toPx(), (destinationY - 9).dp.toPx()), androidx.compose.ui.geometry.Size(18.dp.toPx(), 18.dp.toPx()))
            }
        }
        Text("Av. Insurgentes Sur", Modifier.offset(114.dp, 205.dp), color = ink.copy(alpha = .6f), fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().height(44.dp).background(paper).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(scene.clock + if (scene.stopped || scene.permissionLost) "   K" else "", color = ink, fontSize = 14.sp)
            Text(if (scene.cardTop == null) "100%" else "82%", color = ink, fontSize = 14.sp)
        }
        if (scene.navigation) {
            Column(Modifier.offset(12.dp, 84.dp).width(369.dp).height(96.dp)
                .background(Color(0xFF111111), RoundedCornerShape(12.dp)).padding(16.dp).testTag("navigation-banner")) {
                Text("←  Av. Insurgentes Sur", color = Color.White, fontSize = 22.sp)
                Text("2 min · 1.2 km", color = Color.White, fontSize = 16.sp)
            }
        } else {
            Row(Modifier.offset(12.dp, 80.dp).width(369.dp).height(48.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(48.dp).background(paper, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Text("≡", color = ink, fontSize = 24.sp)
                }
                Box(Modifier.width(94.dp).height(40.dp).background(ink, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Text("En línea", color = paper, fontSize = 18.sp)
                }
                Box(Modifier.size(48.dp).background(paper, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Text("⌕", color = ink, fontSize = 24.sp)
                }
            }
        }
        scene.cardTop?.let { top ->
            Column(Modifier.offset(y = top.dp).width(393.dp).height((838 - top).dp)
                .background(paper, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).padding(20.dp).testTag("host-offer"),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Viaje", color = ink, fontSize = 14.sp)
                if (frame in setOf("C4", "N10")) {
                    Box(Modifier.width(144.dp).height(36.dp).padding(vertical = 5.dp).background(ink.copy(alpha = .12f), RoundedCornerShape(6.dp)))
                } else Text(scene.fare, color = ink, style = KomparaType.number.copy(fontSize = if (frame == "C10") 47.sp else 36.sp))
                if (frame in setOf("C8", "C11")) Text(if (frame == "C8") "$12.33/km" else "$5.04/km", color = ink.copy(alpha = .65f), fontSize = 16.sp)
                val yellow = frame in setOf("C2", "C9", "N9")
                val red = frame in setOf("C3", "C7", "C11")
                Text(if (frame in setOf("C4", "N10")) "4 min (1.5 km) de distancia" else if (red) "12 min (5.2 km) de distancia" else if (yellow) "5 min (2.0 km) de distancia" else "6 min (2.0 km) de distancia", color = ink, fontSize = 16.sp)
                Text("Av. Insurgentes Sur, Crédito Constructor", color = ink.copy(alpha = .65f), fontSize = 14.sp)
                Text(if (frame in setOf("C4", "N10")) "15 min (6.0 km) de viaje" else if (yellow) "22 min (10.0 km) de viaje" else if (red) "18 min (7.1 km) de viaje" else "18 min (10.0 km) de viaje", color = ink, fontSize = 16.sp)
                Text("Plaza Carso, Polanco", color = ink.copy(alpha = .65f), fontSize = 14.sp)
                if (frame in setOf("C8", "C11", "C12")) {
                    Text("Pasajero 4.92 ★", color = ink, fontSize = 15.sp)
                    Text("Pago con tarjeta", color = ink, fontSize = 15.sp)
                    if (frame == "C11") Text("Recoger en la entrada principal", color = ink, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(48.dp).background(ink, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("Aceptar", color = paper, fontSize = 18.sp)
                }
                if (frame in setOf("C8", "C11")) Text("Rechazar", Modifier.align(Alignment.CenterHorizontally), color = ink, fontSize = 16.sp)
            }
        }
    }
}
