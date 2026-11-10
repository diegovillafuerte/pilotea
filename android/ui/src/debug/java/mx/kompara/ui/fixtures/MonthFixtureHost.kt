package mx.kompara.ui.fixtures

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import mx.kompara.ui.components.*
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*

/** The factual IMSS frames E4/E5; the month summary they sat beside is gone (docs/v4-simple-launch-plan.md §3). */
@Composable
fun MonthFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember { MonthFixture(context) }
    val month = fixture.month(frame)
    var sheet by remember(frame) { mutableStateOf<String?>(null) }
    val state = FiscalUiState(month.month, false, month)
    Column {
        NavigationHeader("IMSS del mes", {}, null)
        FiscalContent(state, { sheet = it }, {}, {}, {}, {}, {}, example = if (frame == "E5") ({ ImssRuleExample(fixture) }) else null)
    }
    sheet?.let { name -> SheetScaffold(if (name == "picker") "Elegir mes" else "¿Cómo funciona?", { sheet = null }) {
        if (name == "picker") MonthPickerContent(month.choices, month.month, month.today, {}, {}) else ImssExplanation {}
    } }
}
