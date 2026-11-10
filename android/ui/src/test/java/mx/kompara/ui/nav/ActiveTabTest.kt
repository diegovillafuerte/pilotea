package mx.kompara.ui.nav

import org.junit.Assert.*
import org.junit.Test

class ActiveTabTest {
    @Test fun `exactly three roots in the specified order and Ganancias first`() {
        assertEquals(listOf("ganancias", "comparar", "imss"), KomparaDestination.barItems.map { it.route })
        assertEquals(KomparaDestination.GANANCIAS, KomparaDestination.START)
    }
    @Test fun `empty back stack falls back to Ganancias`() {
        assertEquals(KomparaDestination.GANANCIAS, KomparaDestination.activeTab(emptyList()))
    }
    @Test fun `settings and semaforo are drill ins owned by their opener`() {
        assertEquals(KomparaDestination.IMSS, KomparaDestination.activeTab(listOf(null, "ganancias", "imss", "ajustes", "semaforo")))
        assertEquals(KomparaDestination.COMPARAR, KomparaDestination.activeTab(listOf(null, "ganancias", "comparar", "ajustes", "help")))
    }
    @Test fun `a past day keeps Ganancias lit`() {
        assertEquals(KomparaDestination.GANANCIAS, KomparaDestination.activeTab(listOf(null, "ganancias", "day/{day}?offerId={offerId}&ledger={ledger}")))
    }
    @Test fun `detail without root falls back to Ganancias`() {
        assertEquals(KomparaDestination.START, KomparaDestination.activeTab(listOf(null, "help")))
    }
    @Test fun `bar appears only on the three roots and a past day including its argument template`() {
        for (route in listOf("ganancias", "comparar", "imss", "day", "day/{day}?offerId={offerId}")) assertTrue(route, KomparaDestination.showsBottomBar(route))
        for (route in listOf("ajustes", "semaforo", "thresholds", "reader-recovery", "reader-battery", "import", "share-card", "week/{weekStart}"))
            assertFalse(route, KomparaDestination.showsBottomBar(route))
    }
    @Test fun `roots and Ajustes take the shell header and other drill ins draw their own`() {
        assertEquals(listOf("Ganancias", "Comparar", "IMSS", "Ajustes"), listOf("ganancias", "comparar", "imss", "ajustes").map(KomparaDestination::headerTitle))
        for (route in listOf("semaforo", "day/{day}?offerId={offerId}&ledger={ledger}", "thresholds", "help?answer={answer}")) assertNull(route, KomparaDestination.headerTitle(route))
    }
    @Test fun `day route preserves date and selected record identity`() {
        assertEquals("day/2026-10-19?offerId=42", KomparaDestination.dayRoute("2026-10-19", 42))
        assertEquals("day/2026-10-19", KomparaDestination.dayRoute("2026-10-19"))
    }
}
