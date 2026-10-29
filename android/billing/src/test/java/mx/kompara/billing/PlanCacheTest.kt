package mx.kompara.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlanCacheTest {
    private class Memory : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    @Test fun `plan and grant dates survive recreation without inferring unsupported fields`() = runTest {
        val memory = Memory(); val store = EntitlementStore(memory)
        val plan = PlanStatus(PaymentStatus.PENDING, paidUntil = 1234, grantUntil = 5678, promotionalTrialUntil = 4500)
        store.writePlan(plan); store.write(Entitlement.Premium(false, 5678))
        val reopened = EntitlementStore(memory)
        assertEquals(plan, reopened.readPlan()); assertEquals(Entitlement.Premium(false, 5678), reopened.read())
        assertNull(reopened.readPlan().bankedDays)
        store.writePlan(PlanStatus(PaymentStatus.EXPIRED)); store.write(Entitlement.Free)
        assertEquals(PlanStatus(PaymentStatus.EXPIRED), reopened.readPlan()); assertEquals(Entitlement.Free, reopened.read())
    }
}
