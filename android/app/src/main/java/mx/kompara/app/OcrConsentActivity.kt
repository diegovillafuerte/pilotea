package mx.kompara.app

import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import mx.kompara.ocr.OcrCaptureService

/**
 * Requests MediaProjection consent and starts the OCR capture service. Proof-of-concept entry point
 * for the SurfaceView platforms (DiDi/inDrive) — launchable for testing; the production trigger
 * (a Lector-screen button + per-session consent UX) lands with B-059/B-062.
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
        val mpm = getSystemService(MediaProjectionManager::class.java)
        launcher.launch(mpm.createScreenCaptureIntent())
    }
}
