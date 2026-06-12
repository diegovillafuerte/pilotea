package mx.kompara.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KomparaUiGuardTest {

    @Test
    fun `kompara shell screen is own UI`() {
        // Real OCR of the Inicio tab with the chip overlay attached (2026-06-12 live session).
        val text = "Aún no hay números | No conviene | \$0.02/km | ganancia neta | " +
            "Inicio | Comparar | Lector | Fiscal | \$0.14 | Ajustes"
        assertTrue(KomparaUiGuard.isOwnUi(text))
    }

    @Test
    fun `didi offer card is not own UI`() {
        val text = "\$132.59 | Tarjeta bancaria verificada | 6min (1.2km) | " +
            "Pisos Europeos - Avenida Horacio | 39min (17.3km) | Aceptar"
        assertFalse(KomparaUiGuard.isOwnUi(text))
    }
}
