package mx.kompara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.ui.components.ButtonVariant
import mx.kompara.ui.components.KomparaButton
import mx.kompara.ui.components.StaticSkeleton
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

@Composable
fun CompararScreen(modifier: Modifier = Modifier, viewModel: CompararViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompararContent(state, viewModel::selectTab, viewModel::retry, modifier)
}

/** Comparar under the shell's header (K1, K2 and Tu ciudad's one honest state). */
@Composable
fun CompararContent(state: CompararUiState, onTab: (CompararTab) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        CompararTabs(state.tab, onTab)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            when (state.tab) {
                CompararTab.CIUDAD -> {
                    Headline("Tu ciudad todavía no abre")
                    Paragraph(AnnotatedString("Cuando haya suficientes conductores ${state.city?.let { "de $it" } ?: "de tu ciudad"}, aquí verás en qué " +
                        "lugar estás entre ellos: en total, por app, por día y por hora."))
                }
                CompararTab.TU -> when (val tu = state.tu) {
                    is TuPresentation.Ready -> TuReady(tu)
                    is TuPresentation.Gathering -> TuGathering(tu)
                    null -> if (state.error) {
                        Headline("No pudimos abrir tus viajes")
                        KomparaButton("Reintentar", onRetry, Modifier.padding(horizontal = 20.dp), variant = ButtonVariant.TEXT)
                    } else StaticSkeleton(Modifier.padding(20.dp).fillMaxWidth().height(120.dp))
                }
            }
        }
    }
}

/** The kit's `.per`: two text tabs over a rule, the chosen one underlined in green. */
@Composable
private fun CompararTabs(selected: CompararTab, onTab: (CompararTab) -> Unit) {
    val c = KomparaTheme.colors
    Box {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(1.dp).background(c.rule))
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            listOf(CompararTab.TU to "Tú", CompararTab.CIUDAD to "Tu ciudad").forEach { (tab, label) ->
                val on = tab == selected
                Box(Modifier.heightIn(min = 48.dp).width(IntrinsicSize.Max).selectable(on, role = Role.Tab, onClick = { onTab(tab) }),
                    contentAlignment = Alignment.CenterStart) {
                    Text(label, Modifier.padding(top = 8.dp, bottom = 11.dp), style = if (on) KomparaType.bodyStrong else KomparaType.body,
                        color = if (on) c.ink else c.inkSecondary)
                    if (on) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(c.activeNavigation))
                }
            }
        }
    }
}

@Composable
private fun TuGathering(tu: TuPresentation.Gathering) {
    Headline("Aún juntamos tus viajes")
    Paragraph(emphasis(tu.line))
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth().height(10.dp)
        .background(KomparaTheme.colors.rule, RoundedCornerShape(5.dp))) {
        Box(Modifier.fillMaxWidth(tu.progress).fillMaxHeight().background(KomparaTheme.colors.ink, RoundedCornerShape(5.dp)))
    }
    Text("Mientras tanto, el semáforo compara cada oferta con tu meta.", Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        style = small14, color = KomparaTheme.colors.inkSecondary)
}

@Composable
private fun TuReady(tu: TuPresentation.Ready) {
    Text(tu.header, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), style = small14, color = KomparaTheme.colors.inkSecondary)
    Question("¿Uber o DiDi?", tu.apps, top = 14.dp) { AppBars(tu.apps) }
    Question("¿Qué días?", tu.days) { Columns(tu.days, 64.dp) }
    Question("¿A qué hora?", tu.hours) { Columns(tu.hours, 60.dp) }
}

@Composable
private fun Question(title: String, question: TuQuestion, top: Dp = 22.dp, chart: @Composable () -> Unit) {
    Text(title, Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 4.dp), style = section20)
    Text(question.finding, Modifier.padding(horizontal = 20.dp), style = KomparaType.meaning, color = KomparaTheme.colors.ink)
    chart()
}

/** The kit's `.hb`: one row per app, best in ink, the rest in a lighter ink; an app under 3 trips is a hairline. */
@Composable
private fun AppBars(question: TuQuestion) {
    val c = KomparaTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val label = with(density) { question.bars.maxOf { measurer.measure(it.label, KomparaType.meaning).size.width }.toDp() }.coerceAtLeast(44.dp)
    val value = with(density) { (question.bars.mapNotNull { it.value }.maxOfOrNull { measurer.measure(it, KomparaType.compareLabel).size.width } ?: 0).toDp() }.coerceAtLeast(58.dp)
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp).clearAndSetSemantics { contentDescription = question.description },
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        question.bars.forEach { bar ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(bar.label, Modifier.width(label), style = KomparaType.meaning, color = c.ink)
                Box(Modifier.weight(1f).height(14.dp)) {
                    if (bar.fraction == null) Box(Modifier.width(2.dp).fillMaxHeight().background(c.rule))
                    else Box(Modifier.fillMaxWidth(bar.fraction).fillMaxHeight()
                        .background(if (bar.best) c.ink else c.inkTertiary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                }
                Text(bar.value.orEmpty(), Modifier.width(value), style = KomparaType.compareLabel, color = c.ink, textAlign = TextAlign.End)
            }
        }
    }
}

/**
 * The kit's `.bars` + `.axis`: one scale per chart, the best group in ink with its rate (and the worst's, quieter);
 * a group under 3 trips is a hairline. A rate sits above the bars it spans; at large type two labels that would
 * run into each other move apart.
 */
@Composable
private fun Columns(question: TuQuestion, height: Dp) {
    val c = KomparaTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val gap = 9.dp
    Column(Modifier.padding(horizontal = 20.dp).clearAndSetSemantics { contentDescription = question.description }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val count = question.bars.size
            val slot = (maxWidth - gap * (count - 1)) / count
            val tall = question.bars.map { if (it.fraction == null) 2.dp else (height * it.fraction).coerceAtLeast(2.dp) }
            fun left(i: Int) = (slot + gap) * i
            fun spanned(x: Dp, w: Dp) = question.bars.indices.filter { left(it) < x + w && x < left(it) + slot }
            // Each label is centred on its bar; two that would touch move apart by the same amount, inside the chart.
            val labelled = question.bars.indices.filter { question.bars[it].value != null }
            val sizes = labelled.map { i -> measurer.measure(question.bars[i].value!!, barLabel).size.let { with(density) { DpSize(it.width.toDp(), it.height.toDp()) } } }
            val xs = labelled.mapIndexed { k, i -> left(i) + slot / 2 - sizes[k].width / 2 }.toMutableList()
            fun fit(k: Int) { xs[k] = xs[k].coerceIn(0.dp, (maxWidth - sizes[k].width).coerceAtLeast(0.dp)) }
            xs.indices.forEach(::fit)
            if (xs.size == 2) {
                val (l, r) = if (xs[0] <= xs[1]) 0 to 1 else 1 to 0
                val need = xs[l] + sizes[l].width + 4.dp - xs[r]
                if (need > 0.dp) {
                    xs[l] -= need / 2; xs[r] += need / 2
                    if (xs[l] < 0.dp) { xs[r] -= xs[l]; xs[l] = 0.dp }
                    val over = xs[r] + sizes[r].width - maxWidth
                    if (over > 0.dp) { xs[l] -= over; xs[r] -= over }
                    xs.indices.forEach(::fit)
                }
            }
            // Each label's y is its bottom above the axis: over the bars it spans, and over the other label if they still meet.
            val placed = mutableListOf<Triple<Int, DpOffset, DpSize>>()
            labelled.indices.sortedBy { !question.bars[labelled[it]].best }.forEach { k ->
                val x = xs[k]; val size = sizes[k]
                var bottom = spanned(x, size.width).maxOf { tall[it] } + 5.dp
                placed.firstOrNull { (_, o, s) -> o.x < x + size.width && x < o.x + s.width }?.let { (_, o, s) -> bottom = maxOf(bottom, o.y + s.height + 2.dp) }
                placed += Triple(labelled[k], DpOffset(x, bottom), size)
            }
            // The kit keeps 26 dp over the bars at 1×; larger type takes what its labels need.
            val room = maxOf(with(density) { barLabel.fontSize.toDp() } + 13.dp, (placed.maxOfOrNull { (_, o, s) -> o.y + s.height } ?: 0.dp) - height)
            Box(Modifier.fillMaxWidth().height(height + room)) {
                question.bars.forEachIndexed { i, bar ->
                    Box(Modifier.offset(x = left(i), y = room + height - tall[i]).size(slot, tall[i])
                        .background(if (bar.best) c.ink else c.rule, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
                placed.forEach { (i, o, s) ->
                    Text(question.bars[i].value!!, Modifier.offset(x = o.x, y = room + height - o.y - s.height), style = barLabel,
                        color = if (question.bars[i].low) c.inkSecondary else c.ink, softWrap = false)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
        Row(Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            question.bars.forEach { bar ->
                Text(bar.label, Modifier.weight(1f), style = if (bar.best) axis.copy(fontWeight = FontWeight.SemiBold) else axis,
                    color = if (bar.best) c.ink else c.inkSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Headline(text: String) =
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), style = KomparaType.headline, color = KomparaTheme.colors.ink)

@Composable
private fun Paragraph(text: AnnotatedString) =
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), style = paragraph, color = KomparaTheme.colors.inkSecondary)

/** The kit's `<b>` inside a quieter line: ink, semibold. */
@Composable
internal fun emphasis(said: Emphasis, strong: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold)): AnnotatedString = buildAnnotatedString {
    append(said.lead)
    withStyle(strong.copy(color = KomparaTheme.colors.ink)) { append(said.strong) }
    append(said.tail)
}

private val section20: TextStyle = KomparaType.section.copy(fontSize = 20.sp, lineHeight = 24.sp)
private val paragraph: TextStyle = KomparaType.body.copy(lineHeight = 23.sp)
private val small14: TextStyle = KomparaType.body.copy(fontSize = 14.sp, lineHeight = 20.sp)
private val axis: TextStyle = KomparaType.small.copy(fontSize = 12.sp, lineHeight = 16.sp)
private val barLabel: TextStyle = KomparaType.compareLabel.copy(fontSize = 13.sp, lineHeight = 13.sp)
