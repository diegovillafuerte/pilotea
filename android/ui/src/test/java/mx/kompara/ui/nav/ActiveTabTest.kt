package mx.kompara.ui.nav

import org.junit.Assert.*
import org.junit.Test

class ActiveTabTest {
    @Test fun `exactly three roots in the specified order`() {
        assertEquals(listOf("inicio", "ganancias", "comparar"), KomparaDestination.barItems.map { it.route })
    }
    @Test fun `empty back stack falls back to Hoy`() {
        assertEquals(KomparaDestination.INICIO, KomparaDestination.activeTab(emptyList()))
    }
    @Test fun `settings and reader are drill ins owned by their opener`() {
        assertEquals(KomparaDestination.INICIO, KomparaDestination.activeTab(listOf(null, "inicio", "ajustes", "lector")))
        assertEquals(KomparaDestination.COMPARAR, KomparaDestination.activeTab(listOf(null, "inicio", "comparar", "ajustes", "help")))
    }
    @Test fun `legacy routes keep the most recent root`() {
        assertEquals(KomparaDestination.GANANCIAS, KomparaDestination.activeTab(listOf(null, "inicio", "ganancias", "fiscal", "history")))
    }
    @Test fun `detail without root falls back to Hoy`() {
        assertEquals(KomparaDestination.START, KomparaDestination.activeTab(listOf(null, "help")))
    }
    @Test fun `bar appears only on roots and Tu dia including its argument template`() {
        for (route in listOf("inicio", "ganancias", "comparar", "day", "day/{day}?offerId={offerId}")) assertTrue(route, KomparaDestination.showsBottomBar(route))
        for (route in listOf("ajustes", "lector", "fiscal", "thresholds", "reader-recovery", "reader-battery", "import", "share-card")) assertFalse(route, KomparaDestination.showsBottomBar(route))
    }
    @Test fun `day route preserves date and selected record identity`() {
        assertEquals("day/2026-10-19?offerId=42", KomparaDestination.dayRoute("2026-10-19", 42))
        assertEquals("day/2026-10-19", KomparaDestination.dayRoute("2026-10-19"))
    }
}
