package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.metrics.imss.ImssRule
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.format.DecimalGrouping
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.BarlowSemiCondensed
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

const val IMSS_VIGENCIA_URL = "https://www.imss.gob.mx/faq/vigencia-derechos"
/** Printed by the dormant IMSS PDF (the fiscal package); the tab itself sends the driver to the IMSS instead. */
const val IMSS_NON_REPRESENTATION = "El IMSS confirma tu vigencia. Kompara no lo representa."

@Composable
fun ImssScreen(modifier: Modifier = Modifier, viewModel: ImssViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val incentives by viewModel.incentiveSheet.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var linkFailed by remember { mutableStateOf(false) }
    val official = { linkFailed = runCatching { uri.openUri(IMSS_VIGENCIA_URL) }.isFailure }
    ImssContent(state, { viewModel.step(-1) }, { viewModel.step(1) }, viewModel::openIncentives, viewModel::openRule, official,
        viewModel::retry, modifier, linkFailed)
    if (sheet == ImssViewModel.RULE) SheetScaffold(RULE_TITLE, viewModel::closeSheet) { ImssRuleContent(official) }
    incentives?.let { sheetState ->
        SheetScaffold(incentivesTitle(sheetState), viewModel::closeSheet) {
            IncentivesContent(sheetState, viewModel::editDraft, viewModel::saveIncentives, viewModel::removeIncentives)
        }
    }
}

/** M1/M2 under the shell's header: the month between arrows, the rule, one block per app and the two rows. */
@Composable
fun ImssContent(state: ImssUiState, onPrevious: () -> Unit, onNext: () -> Unit, onIncentives: (String) -> Unit, onRule: () -> Unit,
    onOfficial: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier, linkFailed: Boolean = false) {
    val c = KomparaTheme.colors
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrevious) { DrawnIcon(R.drawable.v3_chev_l, description = stringResource(R.string.v3_previous_month), tint = c.ink) }
            Text(state.title, Modifier.weight(1f), style = monthStyle, color = c.ink, textAlign = TextAlign.Center)
            IconButton(onNext, enabled = state.canNext) {
                DrawnIcon(R.drawable.v3_chev, description = stringResource(R.string.v3_next_month), tint = if (state.canNext) c.ink else c.rule)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            val view = state.view
            Text(emphasis(view?.rule ?: ImssPresentation.rule(ImssRule.Y2026.need), ruleAmount),
                Modifier.padding(top = 14.dp, bottom = 2.dp), style = ruleStyle, color = c.ink)
            Text("Cuentan viajes, incentivos y bonos por referidos; las propinas no. La cuenta oficial la hace cada app.",
                Modifier.padding(top = 4.dp), style = fine, color = c.inkSecondary)
            when {
                view != null -> {
                    view.blocks.forEach { block -> AppBlock(block, view.need) { onIncentives(block.platform) } }
                    view.empty?.let { Text(it, Modifier.padding(top = 16.dp), style = KomparaType.body, color = c.inkSecondary) }
                    view.combined?.let { said ->
                        Column(Modifier.padding(top = 18.dp)) {
                            Rule(total = true)
                            Text(emphasis(said), Modifier.padding(top = 13.dp, bottom = 2.dp), style = both, color = c.ink)
                        }
                    }
                    Spacer(Modifier.height(if (view.combined != null) 6.dp else 22.dp))
                    LinkRow("¿Qué cambia si lo alcanzas?", onRule) { DrawnIcon(R.drawable.v3_chev, tint = c.inkTertiary) }
                    if (view.vigencia) LinkRow("Ver mi vigencia en el IMSS", onOfficial) {
                        Text("↗", style = KomparaType.body.copy(fontSize = 17.sp), color = c.inkTertiary)
                    }
                    if (linkFailed) Text("No pudimos abrir el sitio del IMSS. Inténtalo otra vez.", Modifier.padding(top = 12.dp),
                        style = KomparaType.small, color = c.inkSecondary)
                }
                state.error -> {
                    Text("No pudimos abrir este mes", Modifier.padding(top = 20.dp), style = KomparaType.headline, color = c.ink)
                    KomparaButton("Reintentar", onRetry, variant = ButtonVariant.TEXT)
                }
                else -> StaticSkeleton(Modifier.padding(top = 16.dp).fillMaxWidth().height(120.dp))
            }
        }
    }
}

/** The kit's `.app`: name and "de $21,938", the amount, a bar toward it, its parts (opens M4) and one sentence. */
@Composable
private fun AppBlock(block: ImssBlock, need: String, onIncentives: () -> Unit) {
    val c = KomparaTheme.colors
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(block.name, Modifier.alignByBaseline(), style = KomparaType.meaning.copy(fontWeight = FontWeight.SemiBold), color = c.ink)
            Text("de $need", Modifier.alignByBaseline(), style = small14, color = c.inkSecondary)
        }
        Text(block.amount, style = amount, color = c.ink)
        Box(Modifier.padding(top = 9.dp).fillMaxWidth().height(12.dp).background(c.rule, RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxWidth(block.fraction).fillMaxHeight().background(c.ink, RoundedCornerShape(6.dp)))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onIncentives), verticalAlignment = Alignment.CenterVertically) {
            Text(block.parts, Modifier.weight(1f), style = small14, color = c.ink)
            DrawnIcon(R.drawable.v3_chev, Modifier.size(20.dp), tint = c.inkTertiary)
        }
        block.said?.let { Text(emphasis(it), style = small14, color = c.inkSecondary) }
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = KomparaType.meaning, color = KomparaTheme.colors.ink)
            trailing()
        }
        Rule()
    }
}

const val RULE_TITLE = "¿Qué cambia si lo alcanzas?"

/** M3: the rule in three lines and where the amount comes from. */
@Composable
fun ImssRuleContent(onOfficial: () -> Unit, rule: ImssRule = ImssRule.Y2026) {
    Bullets(ImssRuleText.lines(rule))
    Text(emphasis(ImssRuleText.source(rule)), Modifier.padding(top = 16.dp), style = note, color = KomparaTheme.colors.inkSecondary)
    TextButton(onOfficial, Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 44.dp)) {
        Text("Ver mi vigencia en el IMSS ↗", style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
    }
}

fun incentivesTitle(sheet: IncentiveSheet): String = "Incentivos de ${appName(sheet.platform)} · ${monthName(sheet.month)}"

/** M4: one whole-peso amount per app and month, with Guardar and Quitar. */
@Composable
fun IncentivesContent(sheet: IncentiveSheet, onDraft: (String) -> Unit, onSave: () -> Unit, onRemove: () -> Unit) {
    val c = KomparaTheme.colors
    val app = appName(sheet.platform)
    Text(if (sheet.current) "Lo que $app te ha pagado este mes además de tus viajes." else "Lo que $app te pagó en ${monthName(sheet.month)} además de tus viajes.",
        Modifier.padding(top = 6.dp, bottom = 10.dp), style = KomparaType.meaning, color = c.inkSecondary)
    Column(Modifier.padding(top = 4.dp).fillMaxWidth().background(c.paperSecondary, RoundedCornerShape(10.dp))
        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
        Text(FIELD_LABEL, style = fine.copy(fontSize = 13.sp), color = c.inkSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$", style = field, color = c.ink)
            BasicTextField(sheet.draft, onDraft, Modifier.weight(1f).semantics { contentDescription = FIELD_LABEL }, enabled = !sheet.saving,
                textStyle = field.copy(color = c.ink), singleLine = true, visualTransformation = DecimalGrouping,
                cursorBrush = SolidColor(c.brand), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
    }
    Bullets(listOf(Emphasis("Suma promociones, incentivos y bonos por referidos."), Emphasis("", "No incluyas propinas."),
        Emphasis("Lo encuentras en tus ganancias, en la app de $app.")))
    if (sheet.error) InkNotice("No pudimos guardar tus incentivos. Inténtalo otra vez.")
    PrimaryButton("Guardar", onSave, Modifier.padding(top = 18.dp), enabled = sheet.amount != null && !sheet.saving)
    if (sheet.saved != null) TextButton(onRemove, Modifier.fillMaxWidth().heightIn(min = 44.dp), enabled = !sheet.saving) {
        Text("Quitar", style = KomparaType.bodyStrong, color = c.ink)
    }
}

/** The kit's sheet list: a short dash before each line. */
@Composable
private fun Bullets(lines: List<Emphasis>) {
    // The dash sits on the first line's middle at every font scale, as the kit's `top: 9px` does at 1×.
    val dash = with(LocalDensity.current) { (bullet.lineHeight / 2).toDp() } - 1.dp
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEach { line ->
            Row {
                Box(Modifier.padding(top = dash).size(6.dp, 2.dp).background(KomparaTheme.colors.inkSecondary))
                Text(emphasis(line), Modifier.padding(start = 10.dp), style = bullet, color = KomparaTheme.colors.ink)
            }
        }
    }
}

private const val FIELD_LABEL = "Incentivos y bonos por referidos"
private val ruleAmount = SpanStyle(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Bold, fontSize = 19.sp)
private val monthStyle: TextStyle = KomparaType.meaning.copy(fontWeight = FontWeight.SemiBold)
private val ruleStyle: TextStyle = KomparaType.body.copy(lineHeight = 22.sp)
private val fine: TextStyle = KomparaType.small.copy(fontSize = 12.5.sp, lineHeight = 17.5.sp)
private val small14: TextStyle = KomparaType.body.copy(fontSize = 14.sp, lineHeight = 19.6.sp)
private val amount: TextStyle = KomparaType.number.copy(fontWeight = FontWeight.Bold, lineHeight = 35.sp)
private val both: TextStyle = KomparaType.meaning.copy(lineHeight = 21.sp)
private val note: TextStyle = KomparaType.small.copy(lineHeight = 18.2.sp)
private val bullet: TextStyle = KomparaType.body.copy(fontSize = 15.5.sp, lineHeight = 21.7.sp)
private val field: TextStyle = KomparaType.number.copy(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
