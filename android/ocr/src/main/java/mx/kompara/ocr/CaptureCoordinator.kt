package mx.kompara.ocr

import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ScreenReaderState
import javax.inject.Inject
import javax.inject.Singleton

/** The only OCR writer of shared capture readiness; stale lane teardown never writes it directly. */
@Singleton
class CaptureCoordinator @Inject constructor(readiness: ReaderReadinessRepository) {
    internal val ownership = CaptureOwnership { state, intentional, failed ->
        val running = state.owner != null
        ScreenReaderState.setSilentLaneFailed(state.silentFailed)
        ScreenReaderState.setSilentLaneActive(state.owner?.session?.lane == CaptureLane.SILENT)
        ScreenReaderState.setRunning(running)
        readiness.captureRunning(running, intentionalStop = intentional)
        if (failed) readiness.captureFailed()
    }
}
