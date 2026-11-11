package mx.kompara.ui.fixtures

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.components.RootBottomBar
import mx.kompara.ui.nav.NavigationHeader
import mx.kompara.ui.screens.CompararContent
import mx.kompara.ui.stats.*

/** Comparar's kit frames: K1, K2 and Tu ciudad's one state ("ciudad"). Debug only; never production storage. */
fun compareFixtureState(fixture: TuFixture, frame: String): CompararUiState = when (frame) {
    "K2" -> CompararUiState(tu = TuPresentation.of(fixture.k2(), PreferredMetric.IPK, RateBasis.NET))
    "ciudad" -> CompararUiState(CompararTab.CIUDAD, city = fixture.raw.getString("city"))
    else -> CompararUiState(tu = TuPresentation.of(fixture.k1(), PreferredMetric.IPK, RateBasis.NET))
}

@Composable
fun CompareFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember { TuFixture(context) }
    var state by remember(frame) { mutableStateOf(compareFixtureState(fixture, frame)) }
    Scaffold(topBar = { NavigationHeader("Comparar", null, {}) }, bottomBar = { RootBottomBar(1, {}) }) { padding ->
        CompararContent(state, { tab -> state = compareFixtureState(fixture, if (tab == CompararTab.CIUDAD) "ciudad" else frame.takeIf { it != "ciudad" } ?: "K1") }, {},
            Modifier.padding(padding).consumeWindowInsets(padding))
    }
}
