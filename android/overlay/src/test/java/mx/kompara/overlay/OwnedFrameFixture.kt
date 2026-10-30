package mx.kompara.overlay

import mx.kompara.capture.HostWindowEvidence
import mx.kompara.capture.HostWindowOwnership
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.ocr.HostAppearanceSamples
import mx.kompara.ocr.OcrBlock
import mx.kompara.ocr.OfferFramePipeline
import mx.kompara.parsers.model.HostLayoutObservation
import mx.kompara.parsers.model.HostRect
import java.io.File

/** Existing single-host fixture captures explicitly own their complete display. */
internal fun OfferFramePipeline.processOwnedFrame(
    rawBlocks: List<OcrBlock>, chipRect: ScreenRect?, fixtureDir: File?,
    capturedAtMs: Long = System.currentTimeMillis(),
    hostObservation: HostLayoutObservation? = null,
    token: Long = 0L,
    displayBounds: HostRect? = null,
    density: Float = 1f,
    accessibility: HostWindowEvidence? = null,
    appearance: HostAppearanceSamples? = null,
    foregroundPackage: String? = ScreenReaderState.freshForegroundHost(android.os.SystemClock.elapsedRealtime(), 1_500),
) {
    val display = displayBounds ?: HostRect(0, 0, 1080, 2340)
    process(rawBlocks, chipRect, fixtureDir, capturedAtMs, hostObservation, token, displayBounds,
        density, accessibility, appearance, foregroundPackage,
        windowOwnership = foregroundPackage?.let { HostWindowOwnership(it, capturedAtMs, display, 1, display) })
}
