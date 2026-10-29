package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.kompara.data.accounting.*
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.screens.*
import mx.kompara.ui.stats.*
import mx.kompara.ui.service.statusGlyph
import mx.kompara.ui.theme.KomparaTheme

/** Only this debug source set supplies the reference identity and alternate cost states. */
class CostsFixture(context: Context) {
    val fixture = V3Fixture(context)
    private val reader = LimitsFixture(context)
    fun editor(frame: String): CostEditorState {
        var inputs = CostProfileInputs.from(fixture.schedule.running.single().inputs)
        if (frame == "electric") inputs = inputs.copy(isEv = true, kwhPer100Km = "18", costPerKwhMxn = "3.50")
        if (frame == "direct") inputs = inputs.copy(direct = true, directPerKm = "2.84")
        return CostEditorState(inputs, inputs, typical = frame == "A5")
    }
    fun settings(frame: String): AjustesState {
        val first = frame == "G1e" || frame == "G1n"
        val readiness = reader.readiness(when(frame) { "G1e", "G1n" -> "G4e"; "G1b" -> "G4h"; else -> "G4" })
            .let { if (frame == "G1n") it.copy(phase = mx.kompara.data.service.ReaderPhase.OFF, permission = false) else it }
        val costs = fixture.schedule.running.single().let { if (first) it.copy(confirmation = CostConfirmation.TYPICAL) else it }
        return AjustesState(if (first) "Entrar con WhatsApp" else "+52 55 1234 5678", if (first) "Guarda tus costos y tu meta" else "CDMX y Edomex", !first,
            limits = "$${fixture.goal}/km · $${fixture.minimum}/km",
            costs = "${Formatters.formatMxn(fixture.cost)} por km · ${costConfirmationText(costs, readiness.date, fixture.zone)}",
            car = if (first) "Sin agregar" else "${Formatters.formatMxnWhole(fixture.schedule.car.single().weeklyMxn!!)} a la semana",
            reader = settingsReaderText(readiness), glyph = readiness.statusGlyph())
    }
    fun car() = fixture.schedule.car.single().let { CarCostState(open = true, amount = it.weeklyMxn!!.toPlainString(), from = it.effectiveFrom,
        today = fixture.date, canRemove = true) }
}

@Composable
fun CostsFixtureHost(frame: String) {
    val fixture = CostsFixture(androidx.compose.ui.platform.LocalContext.current)
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = KomparaTheme.colors.paper, contentColor = KomparaTheme.colors.ink) {
    when (frame) {
        "A5" -> EfficiencyContent(fixture.editor(frame), {}, {}, {}, {})
        "G3", "electric", "direct" -> CostProfileContent(fixture.editor(frame), {}, {})
        "G5" -> Box(Modifier.fillMaxSize()) {
            Column { TextSettingsHeader(); AjustesContent(state = fixture.settings("G1")) }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .35f)))
            androidx.compose.material3.Surface(Modifier.align(androidx.compose.ui.Alignment.BottomCenter), color = KomparaTheme.colors.paper,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                Column { Box(Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(vertical = 12.dp).size(32.dp, 4.dp).background(KomparaTheme.colors.rule))
                    SheetContent("Lo del coche") { CarCostContent(fixture.car(), {}, {}, {}, {}) } }
            }
        }
        else -> Column { TextSettingsHeader(); AjustesContent(state = fixture.settings(frame)) }
    }
}
}
@Composable private fun TextSettingsHeader() {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        DrawnIcon(mx.kompara.ui.R.drawable.v3_back, Modifier.padding(12.dp))
        androidx.compose.material3.Text("Ajustes", style = mx.kompara.ui.theme.KomparaType.title)
    }
}
