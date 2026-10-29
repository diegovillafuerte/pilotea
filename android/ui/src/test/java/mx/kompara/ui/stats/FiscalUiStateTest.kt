package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.MonthFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FiscalUiStateTest {
    @Test fun `loading and absent payments cannot export`() {
        val state = FiscalUiState(YearMonth.of(2026, 10))
        assertFalse(state.canExport)
        assertTrue(state.isEmpty)
        assertFalse(state.copy(loading = false).canExport)
    }
    @Test fun `factual statements can export without kilometers or any legal calculation`() {
        val month = MonthFixture(ApplicationProvider.getApplicationContext()).month("E3b")
        val state = FiscalUiState(month.month, false, month)
        assertTrue(state.canExport)
        assertFalse(state.isEmpty)
        assertEquals("5340", state.payments.single().amount!!.toPlainString())
        assertFalse(state.payments.single().estimated)
        assertNull(month.money.periodResult)
    }
    @Test fun `captured payments are estimates and unknown basis retains qualification`() {
        val month = MonthFixture(ApplicationProvider.getApplicationContext()).month()
        val state = FiscalUiState(month.month, false, month)
        assertTrue(state.payments.all { it.estimated })
        assertFalse(state.copy(error = true).canExport)
        val raw = month.money.contributions.first().record.copy(paymentBasis = mx.kompara.data.accounting.PaymentBasis.UNCONFIRMED)
        val payment = ImssTracker.payments(month.money.copy(contributions = listOf(month.money.contributions.first().copy(record = raw)), paymentOnly = emptyList())).single()
        assertTrue(payment.unconfirmed)
        assertEquals("No confirmamos que sea tu pago", payment.sourceNote)
        assertNotNull(payment.amount)
    }
}
