package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.paywall.GateSurface
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate
import java.time.YearMonth

const val IMSS_VIGENCIA_URL = "https://www.imss.gob.mx/faq/vigencia-derechos"
const val IMSS_NON_REPRESENTATION = "El IMSS confirma tu vigencia. Kompara no lo representa."

@Composable
fun FiscalScreen(onUpgrade: (GateSurface) -> Unit, onImport: () -> Unit, viewModel: FiscalViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val gate by viewModel.exportGate.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var linkFailed by remember { mutableStateOf(false) }
    FiscalContent(state, viewModel::openSheet, { viewModel.step(-1) }, { viewModel.step(1) }, {
        linkFailed = runCatching { uri.openUri(IMSS_VIGENCIA_URL) }.isFailure
    }, { if (gate.isLocked) onUpgrade(GateSurface.FISCAL) else viewModel.exportPdf() }, viewModel::retry, linkFailed)
    val month = state.period
    if (sheet != null && month != null) SheetScaffold(if (sheet == "picker") "Elegir mes" else "¿Cómo funciona?", viewModel::closeSheet) {
        if (sheet == "picker") MonthPickerContent(month.choices, month.month, month.today, viewModel::selectMonth, onImport)
        else ImssExplanation { runCatching { uri.openUri(IMSS_VIGENCIA_URL) }.onFailure { linkFailed = true } }
    }
}

@Composable
fun FiscalContent(state: FiscalUiState, onSheet: (String) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onOfficial: () -> Unit, onPdf: () -> Unit, onRetry: () -> Unit, linkFailed: Boolean = false,
    modifier: Modifier = Modifier, example: (@Composable () -> Unit)? = null) {
    val month = state.period
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        PeriodSelector(month?.selector?.removeSuffix(" · antes de Kompara") ?: monthName(state.month).replaceFirstChar { it.titlecase() }, PeriodUnit.MONTH,
            { onSheet("picker") }, onPrevious, onNext, canPrevious = true, canNext = month?.open == false, stepArrows = true, spreadSteps = true)
        Spacer(Modifier.height(20.dp))
        when {
            state.loading -> StaticSkeleton(Modifier.fillMaxWidth().height(120.dp))
            state.error -> { Text("No pudimos abrir este mes", style = KomparaType.headline); KomparaButton("Reintentar", onRetry, variant = ButtonVariant.TEXT) }
            example != null -> example()
            state.isEmpty -> Text("Todavía no hay pagos registrados este mes", style = KomparaType.headline)
            else -> {
                Text(if (state.payments.any { it.estimated }) "Lo que te pagó cada app · aprox." else "Lo que te pagó cada app · según tus resúmenes", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(12.dp))
                state.payments.forEach { pay ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(appName(pay.app), style = KomparaType.body)
                        Text(pay.amount?.let(Formatters::formatMxnWhole) ?: "—", style = KomparaType.number)
                    }
                    pay.sourceNote?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
                    pay.undatedScopes.forEach { scope -> Text("${monthRange(scope.start, scope.endInclusive)} · sin detalle por día", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        EarningsDoor("¿Cómo funciona?", R.drawable.v3_info) { onSheet("explanation") }
        if (state.canExport) EarningsDoor(if (state.exporting) "Preparando PDF" else "Resumen en PDF", R.drawable.v3_doc,
            "Premium · no es constancia ni asesoría", onClick = onPdf)
        Text(IMSS_NON_REPRESENTATION, Modifier.padding(top = 16.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        if (state.exportError) Text("No pudimos crear el PDF. Inténtalo otra vez.", Modifier.padding(top = 12.dp), style = KomparaType.small)
        if (linkFailed) Text("No pudimos abrir el sitio del IMSS. Inténtalo otra vez.", Modifier.padding(top = 12.dp), style = KomparaType.small)
        Spacer(Modifier.heightIn(min = 32.dp).weight(1f))
        PrimaryButton("Consultar mi vigencia en el IMSS", onOfficial)
    }
}

@Composable
fun ImssExplanation(onOfficial: () -> Unit) {
    listOf("Lo que te pagó cada app es nuestra estimación con tus registros.",
        "El monto que cuenta para el IMSS y el monto mínimo los define la ley, no Kompara.",
        "Aquí no te decimos si ya te toca: consúltalo directo.").forEach { Text("• $it", Modifier.padding(bottom = 16.dp), style = KomparaType.body) }
    ListRow("Consultar mi vigencia en el IMSS", onClick = onOfficial)
}

@Composable
fun EarningsDoor(label: String, icon: Int, detail: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Rule()
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrawnIcon(icon)
            Column(Modifier.weight(1f)) {
                Text(label, style = KomparaType.body)
                detail?.let { Text(it, style = KomparaType.small, color = KomparaTheme.colors.inkSecondary) }
            }
            DrawnIcon(R.drawable.v3_chev)
        }
    }
}

@Composable
fun MonthPickerContent(choices: List<YearMonth>, selected: YearMonth, today: LocalDate, onSelect: (YearMonth) -> Unit, onImport: () -> Unit) {
    choices.forEach { month ->
        Rule()
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListRow(monthLabel(month, today) + if (month == YearMonth.from(today)) " · en curso" else "", Modifier.weight(1f), onClick = { onSelect(month) })
            if (month == selected) StatusIcon(StatusGlyph.DONE)
        }
    }
    Spacer(Modifier.height(24.dp))
    ListRow("Traer un resumen", onClick = onImport)
}
