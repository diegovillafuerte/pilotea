package mx.kompara.ui.fixtures

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import mx.kompara.ui.components.RootBottomBar
import mx.kompara.ui.components.SheetScaffold
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*

/** The IMSS tab's kit frames M1–M4 (M3 and M4 open their sheets over M1). Debug only; never production storage. */
@Composable
fun ImssFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember { ImssFixture(context) }
    val month = if (frame == "M2") fixture.m2() else fixture.m1()
    var sheet by remember(frame) { mutableStateOf(when (frame) { "M3" -> "rule"; "M4" -> "UBER"; else -> null }) }
    var draft by remember(frame) { mutableStateOf("1800") }
    Scaffold(topBar = { NavigationHeader("IMSS", null, {}) }, bottomBar = { RootBottomBar(2, {}) }) { padding ->
        ImssContent(ImssUiState(month.month, month.today, month), {}, {}, { sheet = it }, { sheet = "rule" }, {}, {},
            modifier = Modifier.padding(padding).consumeWindowInsets(padding))
    }
    when (val open = sheet) {
        null -> Unit
        "rule" -> SheetScaffold(RULE_TITLE, { sheet = null }) { ImssRuleContent({}) }
        else -> imssFixtureSheet(month, open, draft).let { state ->
            SheetScaffold(incentivesTitle(state), { sheet = null }) { IncentivesContent(state, { draft = it }, { sheet = null }, { sheet = null }) }
        }
    }
}

fun imssFixtureSheet(month: mx.kompara.metrics.imss.ImssMonth, platform: String, draft: String) =
    IncentiveSheet(platform, month.month, month.apps.firstOrNull { it.platform == platform }?.incentives, draft, current = month.current)
