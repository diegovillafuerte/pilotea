package mx.kompara.parsers

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.snapshot.DemoSnapshots
import mx.kompara.parsers.spec.BundledSpecs
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentBasisContractTest {
    @Test fun `bare fare stays unconfirmed unless its parser contract certifies driver payment`() {
        val snapshot = DemoSnapshots.load(DemoSnapshots.UBER.first())
        val spec = BundledSpecs.registry().specFor(snapshot)!!
        val engine = SpecEngine()
        val unknown = engine.evaluate(snapshot, spec)!!
        val certified = engine.evaluate(snapshot, spec.copy(paymentBasis = "DRIVER_PAYMENT"))!!
        assertEquals("UNCONFIRMED", unknown.paymentBasis)
        assertEquals("DRIVER_PAYMENT", certified.paymentBasis)
        assertEquals(unknown.fare, certified.fare)
    }
}
