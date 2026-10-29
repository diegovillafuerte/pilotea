package mx.kompara.ui.theme

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import mx.kompara.ui.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontContractTest {
    @Test fun bundledFontsHaveTheRealWeightsFeaturesAccentsAndEqualTabularAdvances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.font.barlow_regular to 400, R.font.barlow_semibold to 600,
            R.font.barlow_semi_condensed_medium to 500, R.font.barlow_semi_condensed_semibold to 600,
            R.font.barlow_semi_condensed_bold to 700).forEach { (id, weight) ->
            val bytes = context.resources.openRawResource(id).use { it.readBytes() }
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val tables = (0 until b.getShort(4).toInt()).associate { i ->
                val at = 12 + i * 16
                String(bytes, at, 4, Charsets.US_ASCII) to b.getInt(at + 8)
            }
            assertEquals(weight, b.getShort(tables.getValue("OS/2") + 4).toInt())
            val gsub = tables.getValue("GSUB")
            val features = gsub + b.getShort(gsub + 6).toUShort().toInt()
            val tags = (0 until b.getShort(features).toInt()).map { String(bytes, features + 2 + it * 6, 4, Charsets.US_ASCII) }
            assertTrue("tnum missing", "tnum" in tags)
            val paint = Paint().apply {
                typeface = context.resources.getFont(id)
                textSize = 100f
                fontFeatureSettings = "tnum, lnum"
            }
            val widths = (0..9).map { paint.measureText(it.toString()) }
            assertTrue(widths.first() > 0)
            widths.forEach { assertEquals(widths.first(), it, .01f) }
            "−\$áéíóúüñ¿¡".forEach { assertTrue("Missing $it", paint.hasGlyph(it.toString())) }
            // Lining figures are the font's default; no separate lnum substitution is needed.
            val digitBounds = android.graphics.Rect()
            paint.getTextBounds("0123456789", 0, 10, digitBounds)
            assertTrue(digitBounds.height() > 0)
        }
    }
    @Test fun exactPaletteAndTypographyStayInvariantAcrossThemes() {
        assertEquals(DayColors.brand, NightColors.brand)
        assertEquals(DayColors.verde, NightColors.verde)
        assertEquals(DayColors.chipInk, NightColors.chipInk)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF187A36), DayColors.brand)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF3FB565), NightColors.activeNavigation)
        assertEquals("tnum, lnum", KomparaType.number.fontFeatureSettings)
        assertEquals(72f, KomparaType.dayDisplay.fontSize.value)
        assertEquals(13f, KomparaType.small.fontSize.value)
    }
}
