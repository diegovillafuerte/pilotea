package mx.kompara.ui.contract.v4

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.text.TextLayoutResult
import mx.kompara.testing.BaselineComparison
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Reviewed v4 baselines live in `src/test/snapshots/v4`, apart from the v3 ones. Record them with
 * `./gradlew :ui:testDebugUnitTest -Pv4.recordScreenshots=true --tests '<YourTest>'`, then open each PNG
 * next to its kit frame. Recording v4 never rewrites a v3 baseline, and `-Pv3.recordScreenshots` never
 * rewrites a v4 one.
 */
internal object V4Snapshot {
    /** `<screen>_<frame>_<day|night>_<width>_<fontScale>`: the frame id between underscores is what the contract finds. */
    fun name(screen: String, frame: String, dark: Boolean, width: Int, fontScale: Float) =
        "${screen}_${frame}_${if (dark) "night" else "day"}_${width}_$fontScale"

    /** The activity's content at [width] px (1 dp = 1 px under the mdpi qualifier the screen tests use). */
    fun capture(activity: ComponentActivity, width: Int): Bitmap {
        val view = activity.findViewById<android.view.View>(android.R.id.content)
        return Bitmap.createBitmap(width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
    }

    /** Every composed label keeps its whole text, below the scroll viewport too. */
    fun assertNothingClipped(compose: ComposeTestRule, name: String) {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.forEach { r ->
                    assertFalse("$name clipped ${r.layoutInput.text}", r.didOverflowHeight || (0 until r.lineCount).any { r.getLineRight(it) > r.size.width + 1f })
                }
            }
    }

    fun assertMatchesBaseline(name: String, bitmap: Bitmap) {
        val output = File("build/outputs/v4-screenshots/$name.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val baseline = File("src/test/snapshots/v4/$name.png")
        if (System.getProperty("v4.recordScreenshots") == "true") {
            baseline.parentFile!!.mkdirs(); output.copyTo(baseline, overwrite = true)
        } else {
            assertTrue("Missing reviewed v4 baseline $baseline; record it with -Pv4.recordScreenshots=true", baseline.exists())
            BaselineComparison.assertMatches(BitmapFactory.decodeFile(baseline.path), bitmap, output)
        }
    }
}
