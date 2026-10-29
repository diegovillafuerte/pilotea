package mx.kompara.ui.components

import org.junit.Assert.*
import org.junit.Test

class V3LogicTest {
    @Test fun signedBarsShareAZeroOrigin() {
        val scale = BarScale.of(listOf(-100.0, 300.0, null))
        assertEquals(.25f, scale.fraction(0.0), 0f)
        assertEquals(0f, scale.fraction(-100.0), 0f)
        assertEquals(1f, scale.fraction(300.0), 0f)
        assertEquals(1.0, BarScale.of(listOf(null, 0.0)).span, 0.0)
    }
    @Test fun snackbarUsesFooterAndClearsEveryRow() {
        assertEquals(644f, snackbarTop(800f, 48f, 700f, emptyList())!!, 0f)
        assertEquals(736f, snackbarTop(800f, 48f, null, emptyList())!!, 0f)
        assertEquals(594f, snackbarTop(800f, 48f, 700f, listOf(OccupiedRow(650f, 710f)))!!, 0f)
        assertNull(snackbarTop(100f, 48f, null, listOf(OccupiedRow(0f, 100f))))
    }
}
