package mx.kompara.ocr

import mx.kompara.capture.HostWindowEvidence
import mx.kompara.capture.HostWindowOwnership
import mx.kompara.parsers.model.HostRect
import mx.kompara.parsers.model.HostGeometry

/** Null is unknown ownership; an empty list is a known host window with no readable OCR. */
internal fun ownedOcrBlocks(blocks: List<OcrBlock>, ownership: HostWindowOwnership?,
    host: String, capturedAtMs: Long, displayBounds: HostRect?): List<OcrBlock>? {
    if (ownership?.validFor(host, capturedAtMs, displayBounds) != true) return null
    return blocks.filter { b -> ownership.owns(HostRect(b.bounds.left, b.bounds.top, b.bounds.right, b.bounds.bottom)) }
}

/** Same-package dialogs and obscured nodes are not allowed to re-introduce excluded OCR pixels. */
internal fun HostWindowEvidence.ownedBy(ownership: HostWindowOwnership): HostWindowEvidence? {
    if (windowId != ownership.selectedWindowId ||
        !ownership.validFor(packageName, ownership.capturedAtMs, displayBounds) ||
        ownership.capturedAtMs - capturedAtMs !in 0..1_000) return null
    return copy(
        textBounds = textBounds.filter { ownership.owns(it.bounds) },
        observedCardBounds = observedCardBounds?.takeIf(ownership::owns),
        protectedControls = protectedControls.filter(ownership::owns),
        markers = markers.filter { ownership.owns(it.outerBounds) },
    )
}

/** A split pane's unused screen space and other windows are also forbidden overlay positions. */
internal fun HostGeometry.withinOwnership(ownership: HostWindowOwnership): HostGeometry {
    val d = ownership.displayBounds
    val w = ownership.windowBounds
    val outside = listOf(
        HostRect(d.left, d.top, d.right, w.top),
        HostRect(d.left, w.bottom, d.right, d.bottom),
        HostRect(d.left, w.top, w.left, w.bottom),
        HostRect(w.right, w.top, d.right, w.bottom),
    ).filter { it.valid }
    return copy(protectedControls = (protectedControls + outside + ownership.placementExcludedBounds).distinct())
}
