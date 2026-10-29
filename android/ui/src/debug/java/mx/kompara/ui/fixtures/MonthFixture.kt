package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import mx.kompara.data.accounting.*
import mx.kompara.metrics.monthMoneySnapshot
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import java.time.LocalDate

/** Aggregate test evidence stays at its declared date scope, never seeds a real installation. */
class MonthFixture(context: Context) {
    val fixture = V3Fixture(context)
    fun month(frame: String = "E3"): MonthPresentation {
        if (frame !in listOf("E3b", "E3c")) return MonthPresentation(monthMoneySnapshot(fixture.monthMoney.window,
            fixture.monthRows.flatMap { it.contributions.map { c -> c.record } }, fixture.schedule,
            history = fixture.raw.getJSONObject("weeks").keys().asSequence().map { java.time.YearMonth.from(LocalDate.parse(it)) }.toList()))
        val start = LocalDate.parse("2026-09-01")
        val imported = IncomeRecord("sep-import", "UBER", Coverage(LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-20"), false),
            MoneySource.PARSED_STATEMENT, fixture.import.getInt("paid").toBigDecimal(), null, fixture.import.getInt("trips"))
        val raw = fixture.raw.getJSONObject("alt_scenarios").getJSONObject("E3c_sept_mixed").getJSONObject("weeks")
        val records = if (frame == "E3c") listOf(
            fixture.record("sep21", raw.getJSONObject("2026-09-21"), start.withDayOfMonth(21), start.withDayOfMonth(27)),
            fixture.record("sep28", raw.getJSONObject("2026-09-28_partial"), start.withDayOfMonth(28), start.withDayOfMonth(30))) else emptyList()
        return MonthPresentation(monthMoneySnapshot(fixture.monthMoney.window.copy(start = start, endInclusive = start.withDayOfMonth(30)),
            records + imported, fixture.schedule), premium = true)
    }
}

/** E5 can only be composed from the debug source set; no production route or toggle exists. */
@Composable
fun ImssRuleExample(fixture: MonthFixture) {
    val raw = fixture.fixture.raw.getJSONObject("imss")
    val factor = raw.getDouble("example_factor").toBigDecimal()
    val reference = raw.getInt("min_monthly_2026").toBigDecimal()
    val payments = ImssTracker.payments(fixture.month().money)
    val values = payments.map { it.amount!!.multiply(factor) }
    Text("Ejemplo · regla pendiente · te pagó × $factor", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
    Spacer(Modifier.height(20.dp))
    Text("Para el IMSS cuentan", style = KomparaType.body)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        payments.forEachIndexed { index, payment -> Column {
            Text(appName(payment.app), style = KomparaType.small)
            Text(Formatters.formatMxnWhole(values[index]), style = KomparaType.number)
        } }
    }
    val color = KomparaTheme.colors.inkTertiary
    val ink = KomparaTheme.colors.ink
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.weight(1f).height(80.dp)) {
            val max = values.max().toFloat()
            val width = (size.width - 24.dp.toPx()) / 2
            values.forEachIndexed { index, value ->
                val h = size.height * value.toFloat() / max
                drawRect(color, Offset(index * (width + 24.dp.toPx()), size.height - h), Size(width, h))
            }
            val y = size.height * (1 - reference.toFloat() / max)
            drawLine(ink, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Column(Modifier.width(104.dp)) { Text("Salario mínimo", style = KomparaType.small); Text(Formatters.formatMxnWhole(reference), style = KomparaType.body) }
    }
    values.forEachIndexed { index, value -> if (value < reference) Text("${appName(payments[index].app)} · faltan ${Formatters.formatMxnWhole(reference - value)}", style = KomparaType.small) }
}
