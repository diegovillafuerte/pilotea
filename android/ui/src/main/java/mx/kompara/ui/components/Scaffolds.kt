package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

enum class ScreenKind { ROOT, DRILL, TASK }

/** Insets come from Android; 72/64dp headers are content heights, not status-bar coordinates. */
@Composable
fun ScreenScaffold(title: String, modifier: Modifier = Modifier, kind: ScreenKind = ScreenKind.ROOT,
    onNavigateUp: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {}, footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    Scaffold(modifier, containerColor = KomparaTheme.colors.paper,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .fillMaxWidth().heightIn(min = if (kind == ScreenKind.ROOT) 72.dp else 64.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (onNavigateUp != null) IconButton(onClick = onNavigateUp, modifier = Modifier.size(48.dp)) {
                    DrawnIcon(if (kind == ScreenKind.TASK) R.drawable.v3_close else R.drawable.v3_back,
                        description = stringResource(if (kind == ScreenKind.TASK) R.string.v3_close else R.string.v3_back))
                }
                Text(title, Modifier.weight(1f), style = if (kind == ScreenKind.ROOT) KomparaType.rootTitle else KomparaType.title)
                actions()
            }
        }, bottomBar = bottomBar,
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp), content = content)
            if (footer != null) Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), content = footer)
        }
    }
}

@Composable
fun RootScaffold(title: String, selectedTab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) =
    ScreenScaffold(title, modifier, actions = actions, bottomBar = { RootBottomBar(selectedTab, onTab) }, content = content)

@Composable
fun DrillScaffold(title: String, onBack: () -> Unit, modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) =
    ScreenScaffold(title, modifier, ScreenKind.DRILL, onBack, bottomBar = bottomBar, content = content)

@Composable
fun TaskScaffold(title: String, onClose: () -> Unit, modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    ScreenScaffold(title, modifier, ScreenKind.TASK, onClose, footer = footer, content = content)

/** The three tabs as the v4 kit's `.nav` draws them: 72 dp with its rule, the active tab in green under a 44 × 3 dp marker. */
@Composable
fun RootBottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(R.string.v3_earnings, R.string.v3_compare, R.string.v4_imss)
    val icons = listOf(R.drawable.v4_bar, R.drawable.v4_cmp, R.drawable.v4_imss)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val weights = labels.map { resource ->
        if (density.fontScale <= 1.3f) 1f else
            measurer.measure(stringResource(resource), KomparaType.smallStrong).size.width.toFloat() + with(density) { 8.dp.toPx() }
    }
    Column(modifier.background(KomparaTheme.colors.paper).navigationBarsPadding()) {
        Rule()
        Row(Modifier.fillMaxWidth().heightIn(min = 71.dp).selectableGroup()) {
            labels.forEachIndexed { i, resource ->
                val active = i == selected
                val c = if (active) KomparaTheme.colors.activeNavigation else KomparaTheme.colors.inkTertiary
                Box(Modifier.weight(weights[i]).heightIn(min = 71.dp).selectable(active, role = Role.Tab, onClick = { onSelect(i) }),
                    contentAlignment = Alignment.Center) {
                    // The marker starts on the rule above it, as the kit's `top: -1px` does.
                    if (active) Box(Modifier.align(Alignment.TopCenter).offset(y = (-1).dp).size(44.dp, 3.dp).background(c))
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        DrawnIcon(icons[i], tint = c)
                        Text(stringResource(resource), style = if (active) KomparaType.smallStrong else KomparaType.small,
                            color = if (active) c else KomparaTheme.colors.inkSecondary)
                    }
                }
            }
        }
    }
}
