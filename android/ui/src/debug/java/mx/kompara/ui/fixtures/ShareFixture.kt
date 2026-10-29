package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mx.kompara.data.accounting.MoneyField
import mx.kompara.ui.share.*

/** Certified H3 proof is isolated to debug fixtures; production imports cannot supply it yet. */
class ShareFixture(private val context: Context) {
    val comparison = CompareFixture(context)
    val proof = comparison.statement.let { proof ->
        val source = comparison.fixture.raw.getJSONObject("weeks").getJSONObject("2026-10-12").getJSONObject("statement_uber")
        proof.copy(confirmedFields = proof.confirmedFields + MoneyField.HOURS,
            record = proof.record.copy(fieldSources = proof.record.fieldSources + (MoneyField.HOURS to proof.record.id)),
            onlineMinutes = source.getDouble("hours").toBigDecimal().multiply(60.toBigDecimal()))
    }
    fun card(frame: String = "H3", hide: Boolean = true): ShareCardData = when (frame) {
        "H3" -> ShareCardComposer.compose(comparison.current.money, SharePeriodKind.WEEK, hide,
            statements = listOf(proof), cohorts = listOf(comparison.cohort))
        "month" -> ShareCardComposer.compose(MonthFixture(context).month().money, SharePeriodKind.MONTH, hide)
        "historical" -> weekShareData(WeekFixture(context).week("E2").money, hide)
        else -> weekShareData(comparison.current.money, hide)
    }
    fun state(frame: String = "H3", hide: Boolean = true): ShareCardUiState = card(frame, hide).let {
        ShareCardUiState(false, it, ShareCardRenderer(context).render(it), hide)
    }
}
@Composable fun ShareFixtureHost(frame: String = "H3", hide: Boolean = true) {
    val context = LocalContext.current
    val state = remember(frame, hide) { ShareFixture(context).state(frame, hide) }
    ShareCardContent(state)
}
