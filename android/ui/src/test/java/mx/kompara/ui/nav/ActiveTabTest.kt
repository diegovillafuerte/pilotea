package mx.kompara.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Resolution of the highlighted bottom-bar tab from the nav back stack (B-074 F5). The fix the test
 * guards: on a detail screen the parent tab stays lit (instead of falling back to Inicio), which is
 * what makes a re-tap able to pop that detail stack back to the tab root.
 */
class ActiveTabTest {

    private val graphRoot: String? = null // the NavGraph root entry has a null route

    @Test
    fun `empty back stack falls back to start`() {
        assertEquals(KomparaDestination.START, KomparaDestination.activeTab(emptyList()))
    }

    @Test
    fun `tab on top resolves to that tab`() {
        val stack = listOf(graphRoot, KomparaDestination.INICIO.route, KomparaDestination.AJUSTES.route)
        assertEquals(KomparaDestination.AJUSTES, KomparaDestination.activeTab(stack))
    }

    @Test
    fun `detail screen highlights the tab it was opened from`() {
        // Inicio → Ajustes → Ayuda (a detail route, not a tab).
        val stack = listOf(
            graphRoot,
            KomparaDestination.INICIO.route,
            KomparaDestination.AJUSTES.route,
            KomparaDestination.HELP_ROUTE,
        )
        assertEquals(KomparaDestination.AJUSTES, KomparaDestination.activeTab(stack))
    }

    @Test
    fun `detail screen with no tab below falls back to start`() {
        val stack = listOf(graphRoot, KomparaDestination.HELP_ROUTE)
        assertEquals(KomparaDestination.START, KomparaDestination.activeTab(stack))
    }

    @Test
    fun `topmost tab wins when several are in the stack`() {
        val stack = listOf(
            graphRoot,
            KomparaDestination.INICIO.route,
            KomparaDestination.FISCAL.route,
            KomparaDestination.HISTORY_ROUTE,
        )
        assertEquals(KomparaDestination.FISCAL, KomparaDestination.activeTab(stack))
    }
}
