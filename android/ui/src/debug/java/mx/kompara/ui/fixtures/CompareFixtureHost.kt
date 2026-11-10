package mx.kompara.ui.fixtures

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import mx.kompara.ui.components.*
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*

/** Explicit compareFrame intent only. These certification examples never enter production storage. */
@Composable
fun CompareFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember { CompareFixture(context) }
    var state by remember { mutableStateOf(fixture.state(frame)) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(bottomBar = { RootBottomBar(1, {}) }) { padding ->
        Column(Modifier.padding(padding)) {
            mx.kompara.ui.nav.NavigationHeader("Comparar", null, {})
            CompararContent(state, { state = fixture.state(if (it == CompareScope.APPS) "F1" else "F2") },
                { sheet = it }, { state = fixture.state("F5") }, { state = fixture.state("F2") }, {}, {}, {})
        }
    }
    sheet?.let { id -> SheetScaffold(compareSheetTitle(id), { sheet = null }) {
        CompareSheetContent(id, state, { sheet = it }, { metric ->
            val choice = state.choice.let { if (it.scope == CompareScope.APPS) it.copy(appsMetric = metric) else it.copy(cityMetric = metric) }
            val c = ComparisonBuilder.build(fixture.current, fixture.previous, choice, listOf(fixture.statement), listOf(fixture.cohort), true)
            state = state.copy(choice = c.choice, comparison = c); sheet = null
        }, { state = fixture.state(if (it == "DIDI") "F4" else "F2") }, {}, {}, { state = state.copy(contributing = it) }, {})
    } }
}
