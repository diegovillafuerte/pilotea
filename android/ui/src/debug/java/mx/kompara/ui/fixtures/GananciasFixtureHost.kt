package mx.kompara.ui.fixtures

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import mx.kompara.ui.components.SheetContent
import mx.kompara.ui.nav.KomparaDestination
import mx.kompara.ui.nav.KomparaShell
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.DayDetailUiState
import mx.kompara.ui.theme.KomparaTheme

/**
 * One Ganancias frame (L1–L8, "empty", "short", "stopped") in the real shell, from [GananciasFixture]. The offer and
 * calculation sheets are drawn in the window over the shell, as the OS sheet would sit; the explicit
 * `gananciasFrame` debug intent only.
 */
@Composable
fun GananciasFixtureHost(frame: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fixture = remember(context) { GananciasFixture(context) }
    val nav = rememberNavController()
    val week = frame in setOf("L4", "L5", "short")
    Box(modifier) {
        KomparaShell(nav) {
            composable(KomparaDestination.GANANCIAS.route) {
                GananciasContent(fixture.ganancias(frame), if (week) 1 else 0, DayDetailUiState.Loaded(fixture.dayPresentation(frame)),
                    fixture.weekState(if (week) frame else "L5"), GananciasActions())
            }
            composable("${KomparaDestination.DAY_DETAIL_ROUTE}/{day}?offerId={offerId}&ledger={ledger}", arguments = listOf(
                navArgument("day") { type = NavType.StringType },
                navArgument("offerId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("ledger") { type = NavType.BoolType; defaultValue = false })) {
                val day = fixture.dayPresentation("L6")
                Column(Modifier.fillMaxSize()) {
                    NavigationHeader(day.title, {}, null)
                    DayState(DayDetailUiState.Loaded(day), {}) { DayContent(it, {}, {}, Modifier.weight(1f)) }
                }
            }
        }
        if (frame == "L7" || frame == "L8") {
            val day = fixture.dayPresentation("L1")
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = KomparaTheme.colors.paper) {
                Column {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp)
                        .background(KomparaTheme.colors.rule, RoundedCornerShape(2.dp)))
                    if (frame == "L7") {
                        val model = day.offer(day.rows.first { day.clock(it) == "14:32" }.offerId)!!
                        SheetContent(model.title, heading = { OfferDetailHeading(model) }) { OfferDetailContent(model, {}, {}) }
                    } else SheetContent(day.calculation.title) { PeriodCalculationContent(day.calculation) }
                }
            }
        }
    }
    LaunchedEffect(frame) {
        if (frame == "L6") nav.navigate(KomparaDestination.dayRoute(fixture.date.toString()))
    }
}
