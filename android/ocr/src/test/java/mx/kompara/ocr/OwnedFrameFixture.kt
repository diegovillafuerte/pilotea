package mx.kompara.ocr

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

/**
 * 0.2.15: a misread frame whose TOTAL row ("N min • X km • $R/km est.") OCR also lost takes the
 * legacy path, so a synthetic single-field misread still reaches the downstream policy under test.
 * With its TOTAL row the card's own arithmetic proves such a frame contradictory (UberModernParseTest).
 */
internal fun List<OcrBlock>.withoutTotalRow() = filterNot { it.text.contains("/km est") }

/** 0.2.15: a genuine card change (another offer, a live fare change) keeps its TOTAL row consistent. */
internal fun List<OcrBlock>.withTotalRow(total: String) = map { if (it.text.contains("/km est")) it.copy(text = total) else it }
