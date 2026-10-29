package mx.kompara.app

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import mx.kompara.data.service.ReaderDeviceEnvironment
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.ocr.OcrCaptureService

/**
 * Requests MediaProjection consent and starts the OCR capture service — the API <30 capture lane and
 * the fallback when the silent-screenshot lane can't run (B-091). On API 30+ the accessibility service
 * already reads the screen silently via [mx.kompara.ocr.SilentScreenshotLane]; if that lane is active
 * we must NOT also request MediaProjection, or the driver would grant a redundant second capture of
 * the same screen and see the system cast indicator for nothing.
 */
class OcrConsentActivity : ComponentActivity() {
    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            OcrCaptureService.start(this, result.resultCode, data)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The silent lane already owns capture on 30+ — don't stack a MediaProjection session on top.
        if (!ReaderDeviceEnvironment.permissionGranted(this) ||
            ScreenReaderState.silentLaneActive.value || ScreenReaderState.running.value ||
            (Build.VERSION.SDK_INT >= 30 && !ScreenReaderState.silentLaneFailed.value)) {
            finish()
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        // The Activity Result registry redelivers the existing prompt after recreation.
        if (savedInstanceState == null) launcher.launch(mpm.createScreenCaptureIntent())
    }
}
