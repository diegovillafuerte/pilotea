package mx.kompara.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** C6 is passive: no click, drag, dismiss or consent action. The window passes touches through. */
@Composable
fun ReaderStoppedTab(modifier: Modifier = Modifier) {
    val scale = LocalDensity.current.fontScale
    Column(modifier.width((240 * scale).dp).heightIn(min = (72 * scale).dp)
        .shadow(10.dp, RoundedCornerShape((14 * scale).dp))
        .background(KomparaTheme.colors.chipInk, RoundedCornerShape((14 * scale).dp))
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = (14 * scale).dp, vertical = (7 * scale).dp), verticalArrangement = Arrangement.Center) {
        BasicText("Semáforo apagado", style = KomparaType.section.copy(fontSize = 22.sp, lineHeight = 22.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = KomparaTheme.colors.onChip,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)))
        val detailStyle = KomparaType.small.copy(lineHeight = 15.sp, color = KomparaTheme.colors.onChip.copy(alpha = .68f),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
        BasicText("Dejamos de leer tus ofertas", style = detailStyle)
        BasicText("Abre Kompara al estacionarte", style = detailStyle)
    }
}

