package mx.kompara.ui.paywall

import mx.kompara.billing.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TuPlanStateTest {
    private val zone = ZoneId.of("America/Mexico_City")
    private fun date(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
    @Test fun `unknown expiry and generic grant never invent trial grace or banked days`() {
        val state = TuPlanState.from(Entitlement.Premium(false), PlanStatus(), "MX$80.00", zone)
        assertEquals("Tienes Premium", state.title); assertNull(state.detail); assertTrue(state.dates.isEmpty())
        val grace = TuPlanState.from(Entitlement.Premium(false), PlanStatus(PaymentStatus.GRACE), null, zone)
        assertTrue(grace.dates.isEmpty()); assertFalse(grace.subscribe); assertTrue(grace.manage)
    }
    @Test fun `actual dates respect local timezone and pending retains only known prior access`() {
        assertEquals("9 de noviembre", planDate(Instant.parse("2026-11-10T01:00:00Z").toEpochMilli(), zone))
        val until = date("2026-11-09")
        val pending = TuPlanState.from(Entitlement.Premium(false, until), PlanStatus(PaymentStatus.PENDING, grantUntil = until), "$79", zone)
        assertEquals(listOf("Tus días gratis" to "9 de noviembre"), pending.dates)
        assertFalse(pending.subscribe); assertFalse(pending.manage)
        assertTrue(TuPlanState.from(Entitlement.Free, PlanStatus(PaymentStatus.PENDING), "$79", zone).dates.isEmpty())
    }
    @Test fun `cancelled subscription keeps its real date and management instead of a sales page`() {
        val state = TuPlanState.from(Entitlement.Premium(false, date("2026-11-09")), PlanStatus(PaymentStatus.CANCELED), "$79", zone)
        assertEquals("Premium hasta el lunes 9 de noviembre", state.title)
        assertTrue(state.manage); assertFalse(state.subscribe); assertTrue(state.detail!!.contains("Cancelaste"))
        val expired = TuPlanState.from(Entitlement.Free, PlanStatus(PaymentStatus.EXPIRED), "$79", zone)
        assertEquals("Plan gratis", expired.title); assertEquals("Tu acceso a Premium terminó.", expired.detail)
    }
}
