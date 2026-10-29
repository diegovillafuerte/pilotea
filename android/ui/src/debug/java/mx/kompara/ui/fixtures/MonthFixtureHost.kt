package mx.kompara.ui.fixtures

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*

@Composable
fun MonthFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember { MonthFixture(context) }
    val month = fixture.month(frame)
    var sheet by remember(frame) { mutableStateOf(when (frame) { "E3d" -> "weeks"; "D3b" -> "calculation"; else -> null }) }
    val state = FiscalUiState(month.month, false, month)
    var anchors by remember { mutableStateOf(WeekSheetAnchors()) }
    val height = LocalView.current.rootView.height.toFloat()
    Scaffold(bottomBar = { if (frame !in listOf("E4", "E5")) RootBottomBar(1, {}) }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
            NavigationHeader(if (frame in listOf("E4", "E5")) "IMSS del mes" else "Ganancias", if (frame in listOf("E4", "E5")) ({}) else null, {})
            if (frame in listOf("E4", "E5")) FiscalContent(state, { sheet = it }, {}, {}, {}, {}, {},
                example = if (frame == "E5") ({ ImssRuleExample(fixture) }) else null)
            else MonthSummaryContent(state, { sheet = it }, {}, {}, {}, {}, {}, {}, {},
                onAnchor = { name, bottom -> anchors = anchors.updated(name, bottom) })
        }
    }
    sheet?.let { name -> SheetScaffold(if (name == "explanation") "¿Cómo funciona?" else monthSheetTitle(name, month), { sheet = null },
        monthSheetSize(name, height, anchors)) {
        if (name == "explanation") ImssExplanation {} else MonthSheetContent(name, month, {}, {}, {})
    } }
}
