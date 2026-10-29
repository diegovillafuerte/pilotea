package mx.kompara.ui.share

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import mx.kompara.ui.export.*
import mx.kompara.ui.fixtures.ShareFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareCardRendererTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun `every preview pixel and encoded byte is the export including redaction`() {
        val h = ExportHarness()
        val writer = ShareCardWriter(h.cache)
        for (frame in listOf("H3", "week", "month", "historical")) for (hide in listOf(true, false)) {
            val state = ShareFixture(context).state(frame, hide)
            val bitmap = requireNotNull(state.bitmap)
            val uri = writer.write(bitmap)
            val encoded = ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            assertArrayEquals(encoded, bytes)
            assertTrue(bitmap.sameAs(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)))
            assertEquals(1080, bitmap.width); assertEquals(1350, bitmap.height)
            File("build/outputs/v3-screenshots/share_bitmap_${frame}_${hide}.png").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        }
    }
    @Test fun `hidden bitmap is independent of withheld amounts hours and public-average bar geometry`() {
        val f = ShareFixture(context)
        val base = f.card()
        val changed = ShareCardComposer.compose(f.comparison.current.money, SharePeriodKind.WEEK,
            statements = listOf(f.proof.copy(onlineMinutes = "12345".toBigDecimal())),
            cohorts = listOf(f.comparison.cohort.copy(mean = "9999".toBigDecimal())))
        assertEquals(base, changed)
        val renderer = ShareCardRenderer(context)
        assertTrue(renderer.render(base).sameAs(renderer.render(changed)))
        assertFalse(renderer.render(base).sameAs(renderer.render(f.card(hide = false))))
    }
    @Test fun `negative sparse and long historical strings render without truncating footer`() {
        val renderer = ShareCardRenderer(context)
        val base = ShareFixture(context).card("historical", false)
        listOf(base.copy(netEarnings = "−$1,234,567", periodLabel = "29 dic 2025 – 4 ene 2026"),
            base.copy(netEarnings = null, hours = null),
            ShareFixture(context).card().copy(periodLabel = "29 dic 2025 – 4 ene 2026")).forEach {
            assertFalse(renderer.render(it).isRecycled)
        }
    }
    @Test fun `replacement hiding and expiry remove private files and revoke access`() {
        val h = ExportHarness(); val writer = ShareCardWriter(h.cache)
        val uri = writer.write(ShareFixture(context).state(hide = false).bitmap!!)
        h.provider.openFile(uri, "r").use { assertNotNull(it) }
        assertThrows(java.io.FileNotFoundException::class.java) { h.provider.openFile(uri, "rw") }
        val hidden = writer.write(ShareFixture(context).state().bitmap!!)
        assertThrows(java.io.FileNotFoundException::class.java) { h.provider.openFile(uri, "r") }
        writer.clear()
        assertThrows(java.io.FileNotFoundException::class.java) { h.provider.openFile(hidden, "r") }
        val expired = writer.write(ShareFixture(context).state(hide = false).bitmap!!)
        val file = File(context.cacheDir, "share_cards").listFiles()!!.single()
        file.setLastModified(System.currentTimeMillis() - ExportCache.MAX_AGE_MS)
        assertThrows(java.io.FileNotFoundException::class.java) { h.provider.openFile(expired, "r") }
        assertFalse(file.exists())
        assertTrue(File(context.cacheDir, "share_cards").listFiles()!!.isEmpty())
    }
}
