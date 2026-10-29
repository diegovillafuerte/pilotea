package mx.kompara.ui.fixtures

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaType

/** Explicit weekFrame intent only. Production screens never access this fixture. */
@Composable
fun WeekFixtureHost(frame: String) {
    val context = LocalContext.current
    val week = remember(frame) { WeekFixture(context).week(frame) }
    var sheet by rememberSaveable(frame) { mutableStateOf(when (frame) {
        "E1b", "E1c" -> "days"; "E1d" -> "calculation"; "E1e" -> "statement:UBER"; "E1f" -> "apps"; "E6" -> "picker"; else -> null
    }) }
    Scaffold(bottomBar = { RootBottomBar(1, {}) }) { padding ->
        Column(Modifier.padding(padding)) {
            Text("Ganancias", Modifier.padding(20.dp), style = KomparaType.rootTitle)
            WeekSummaryContent(WeekSummaryUiState(week.start, frame == "E7", week, offline = frame == "E7b"),
                { sheet = it }, {}, {}, {}, {}, {}, {})
        }
    }
    sheet?.let { id -> SheetScaffold(weekSheetTitle(id, week), { sheet = null }) { WeekSheetContent(id, week, {}, { sheet = null }, {}) } }
}
