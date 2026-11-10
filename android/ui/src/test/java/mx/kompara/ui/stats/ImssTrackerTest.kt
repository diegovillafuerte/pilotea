package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.fixtures.MonthFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The dormant IMSS PDF's payment evidence (the fiscal package); the tab itself reads ImssMonth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImssTrackerTest {
    @Test fun `factual statements are payments without kilometers or any legal calculation`() {
        val month = MonthFixture(ApplicationProvider.getApplicationContext()).month("E3b")
        val payments = ImssTracker.payments(month.money)
        assertEquals("5340", payments.single().amount!!.toPlainString())
        assertFalse(payments.single().estimated)
        assertNull(month.money.periodResult)
    }
    @Test fun `captured payments are estimates and unknown basis retains qualification`() {
        val month = MonthFixture(ApplicationProvider.getApplicationContext()).month()
        assertTrue(ImssTracker.payments(month.money).all { it.estimated })
        val raw = month.money.contributions.first().record.copy(paymentBasis = mx.kompara.data.accounting.PaymentBasis.UNCONFIRMED)
        val payment = ImssTracker.payments(month.money.copy(contributions = listOf(month.money.contributions.first().copy(record = raw)), paymentOnly = emptyList())).single()
        assertTrue(payment.unconfirmed)
        assertEquals("No confirmamos que sea tu pago", payment.sourceNote)
        assertNotNull(payment.amount)
    }
}
