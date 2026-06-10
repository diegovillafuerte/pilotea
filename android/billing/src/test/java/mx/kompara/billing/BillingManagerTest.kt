package mx.kompara.billing

import org.junit.Assert.assertFalse
import org.junit.Test

class BillingManagerTest {

    @Test
    fun `everyone is free tier until billing is implemented`() {
        assertFalse(BillingManager().isPremium())
    }
}
