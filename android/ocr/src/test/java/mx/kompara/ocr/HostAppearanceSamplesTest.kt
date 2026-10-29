package mx.kompara.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HostAppearanceSamplesTest {
    private val geometry = HostGeometry("com.ubercab.driver", "test", 1000, HostRect(0, 0, 393, 852),
        HostOrientation.PORTRAIT, HostRect(0, 80, 393, 180), HostRect(0, 545, 393, 838), emptyList(), emptyList())
    private fun frame(map: Int, card: Int = Color.WHITE) = Bitmap.createBitmap(393, 852, Bitmap.Config.ARGB_8888).apply {
        eraseColor(map)
        Canvas(this).apply {
            drawRect(0f, 0f, 393f, 180f, Paint().apply { color = Color.BLACK })
            drawRect(0f, 545f, 393f, 852f, Paint().apply { color = card })
        }
    }

    @Test fun `host map pixels decide treatment independently of bright fare and black banner`() {
        for ((color, expected) in listOf(Color.rgb(38, 44, 51) to HostAppearance.DARK, Color.rgb(236, 239, 245) to HostAppearance.LIGHT)) {
            val bitmap = frame(color)
            assertEquals(expected, HostAppearanceSamples.capture(bitmap, 1000, null).appearance(geometry))
            bitmap.recycle()
        }
    }

    @Test fun `captured chip and map markers do not contaminate the host evidence`() {
        val bitmap = frame(Color.WHITE)
        val chip = ScreenRect(0, 180, 300, 545)
        Canvas(bitmap).drawRect(0f, 180f, 300f, 545f, Paint().apply { color = Color.BLACK })
        assertEquals(HostAppearance.DARK, HostAppearanceSamples.capture(bitmap, 1000, null).appearance(geometry))
        assertEquals(HostAppearance.LIGHT, HostAppearanceSamples.capture(bitmap, 1000, chip).appearance(geometry))
        assertEquals(HostAppearance.LIGHT, HostAppearanceSamples.capture(bitmap, 1000, null).appearance(
            geometry.copy(markers = listOf(MapMarker(MapMarkerKind.DRIVER, HostRect(0, 180, 300, 545))))))
        bitmap.recycle()
    }

    @Test fun `ambiguous insufficient stale and rotated evidence remain unknown`() {
        val bitmap = frame(Color.GRAY)
        val samples = HostAppearanceSamples.capture(bitmap, 1000, null)
        assertEquals(HostAppearance.UNKNOWN, samples.appearance(geometry))
        bitmap.eraseColor(Color.BLACK)
        val dark = HostAppearanceSamples.capture(bitmap, 1000, null)
        assertEquals(HostAppearance.UNKNOWN, dark.appearance(geometry.copy(capturedAtMs = 1001)))
        assertEquals(HostAppearance.UNKNOWN, dark.appearance(geometry.copy(displayBounds = HostRect(0, 0, 852, 393))))
        assertEquals(HostAppearance.UNKNOWN, dark.appearance(geometry.copy(offerCard = HostRect(0, 185, 393, 838))))
        bitmap.recycle()
    }

    @Test fun `OCR offer carries its own frame appearance and never inherits a previous frame`() {
        fun box(text: String, top: Int) = OcrBlock(text, OcrBounds(20, top, 373, top + 24))
        val blocks = listOf(box("UberX Exclusivo", 500), box("MXN148.00", 540),
            box("A 4 min (2.0 km)", 600), box("Viaje: 20 min (10.0 km)", 650), box("Aceptar", 780))
        val bitmap = frame(Color.BLACK)
        val samples = HostAppearanceSamples.capture(bitmap, 1000, null)
        fun select(time: Long, evidence: HostAppearanceSamples?) = selectOfferCard(UberOcrParser(), DidiOcrParser(), blocks,
            time, displayBounds = geometry.displayBounds, appearance = evidence)!!.hostGeometry!!.appearance
        assertEquals(HostAppearance.DARK, select(1000, samples))
        assertEquals(HostAppearance.UNKNOWN, select(1001, samples))
        assertEquals(HostAppearance.UNKNOWN, select(1000, null))
        bitmap.recycle()
    }
}
