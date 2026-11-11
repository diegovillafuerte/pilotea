package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme

/** A5, the efficiency editor; Tus costos itself is J3 in [SettingsFixture]. */
class CostsFixture(context: Context) {
    val fixture = V3Fixture(context)
    fun editor(frame: String): CostEditorState {
        val inputs = CostProfileInputs.from(fixture.schedule.running.single().inputs)
        return CostEditorState(inputs, inputs, typical = frame == "A5")
    }
}

@Composable
fun CostsFixtureHost(frame: String) {
    val fixture = CostsFixture(androidx.compose.ui.platform.LocalContext.current)
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = KomparaTheme.colors.paper, contentColor = KomparaTheme.colors.ink) {
        EfficiencyContent(fixture.editor(frame), {}, {}, {}, {})
    }
}
