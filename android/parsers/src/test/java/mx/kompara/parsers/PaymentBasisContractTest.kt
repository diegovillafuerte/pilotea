package mx.kompara.parsers

import mx.kompara.parsers.engine.SpecEngine
import mx.kompara.parsers.snapshot.DemoSnapshots
import mx.kompara.parsers.spec.BundledSpecs
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentBasisContractTest {
    @Test fun `supported accessibility offer variants carry estimated driver payment`() {
        for (demo in DemoSnapshots.all()) {
            val snapshot = DemoSnapshots.load(demo)
            val spec = BundledSpecs.registry().specFor(snapshot)!!
            val card = SpecEngine().evaluate(snapshot, spec)!!
            assertEquals("DRIVER_PAYMENT", card.paymentBasis)
            val withoutFare = snapshot.copy(nodes = snapshot.nodes.filterNot { it.text?.contains("$") == true })
            assertEquals(null, SpecEngine().evaluate(withoutFare, spec)!!.fare)
        }
    }

    @Test fun `an unrelated accept action is not an accessibility offer`() {
        val snapshot = DemoSnapshots.load(DemoSnapshots.UBER.first())
        val spec = BundledSpecs.registry().specFor(snapshot)!!
        val dialog = snapshot.copy(nodes = snapshot.nodes.filter { it.text == "Aceptar" })
        assertEquals(null, SpecEngine().evaluate(dialog, spec))
    }

    @Test fun `bare fare stays unconfirmed unless its parser contract certifies driver payment`() {
        val snapshot = DemoSnapshots.load(DemoSnapshots.UBER.first())
        val spec = BundledSpecs.registry().specFor(snapshot)!!
        val engine = SpecEngine()
        val unknown = engine.evaluate(snapshot, spec.copy(paymentBasis = "UNCONFIRMED"))!!
        val certified = engine.evaluate(snapshot, spec.copy(paymentBasis = "DRIVER_PAYMENT"))!!
        assertEquals("UNCONFIRMED", unknown.paymentBasis)
        assertEquals("DRIVER_PAYMENT", certified.paymentBasis)
        assertEquals(unknown.fare, certified.fare)
    }
}
