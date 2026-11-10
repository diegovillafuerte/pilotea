package mx.kompara.ui.screens

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mx.kompara.ui.theme.KomparaType

/** The v4 kit's sizes for Ganancias (kit.css `.st`, `.hero`, `.sec`, `.orow`, `.bars`, `.car`, `.sheet`, `.led`). */
internal object GananciasType {
    val status = KomparaType.meaning
    val statusStrong = KomparaType.meaning.copy(fontWeight = FontWeight.SemiBold)
    val heroLabel = KomparaType.meaning
    val quiet = KomparaType.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp)
    val weekFigure = KomparaType.dayDisplay.copy(fontSize = 56.sp, lineHeight = 58.sp)
    val section = KomparaType.section.copy(fontSize = 20.sp, lineHeight = 24.sp)
    val sectionCount = KomparaType.body.copy(fontSize = 14.sp, lineHeight = 18.sp)
    val app = KomparaType.bodyStrong.copy(lineHeight = 20.sp)
    val rowDetail = KomparaType.body.copy(fontSize = 14.sp, lineHeight = 18.sp)
    val rowRate = KomparaType.rate.copy(fontSize = 18.sp, lineHeight = 22.sp)
    val barLabel = KomparaType.rate.copy(fontSize = 13.sp, lineHeight = 16.sp)
    val axis = KomparaType.small.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val axisStrong = axis.copy(fontWeight = FontWeight.SemiBold)
    val carLabel = KomparaType.body.copy(fontSize = 14.sp, lineHeight = 18.sp)
    val carValue = KomparaType.headline.copy(fontSize = 22.sp, lineHeight = 26.sp)
    val sheetFigure = KomparaType.dayDisplay.copy(fontSize = 44.sp, lineHeight = 48.sp)
    val ledgerValue = KomparaType.rate.copy(fontSize = 18.sp, lineHeight = 22.sp)
    val ledgerTotal = KomparaType.rate.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val ledgerDetail = KomparaType.small
}
