package mx.kompara.capture

import mx.kompara.parsers.model.HostRect
import java.util.concurrent.atomic.AtomicReference

/** OS window metadata only. No node labels or screenshot contents are retained here. */
data class VisibleHostWindow(
    val id: Int,
    val packageName: String?,
    val bounds: HostRect?,
    val layer: Int,
    val application: Boolean,
    val pictureInPicture: Boolean = false,
    val accessibilityOverlay: Boolean = false,
)

/**
 * Pixel ownership for ONE selected OS window, measured around a capture. Whole OCR blocks must
 * fit inside that window and avoid every window above it. A block crossing an edge is discarded;
 * its text cannot be safely divided after recognition. Missing bounds never imply full-screen.
 */
data class HostWindowOwnership(
    val packageName: String,
    val capturedAtMs: Long,
    val displayBounds: HostRect,
    val selectedWindowId: Int,
    val windowBounds: HostRect,
    val excludedBounds: List<HostRect> = emptyList(),
    val placementExcludedBounds: List<HostRect> = excludedBounds,
) {
    fun owns(bounds: HostRect): Boolean = bounds.valid && windowBounds.contains(bounds) &&
        excludedBounds.none { it.overlaps(bounds) }

    fun validFor(host: String, atMs: Long, display: HostRect?): Boolean =
        packageName == host && atMs - capturedAtMs in 0..1_000 &&
            (display == null || display == displayBounds) && displayBounds.contains(windowBounds) &&
            windowBounds.valid && excludedBounds.all { it.valid && displayBounds.contains(it) } &&
            placementExcludedBounds.all { it in excludedBounds }

    /** Publication needs the same selected window and exclusions after asynchronous OCR finishes. */
    fun samePixels(other: HostWindowOwnership?): Boolean = other != null &&
        copy(capturedAtMs = other.capturedAtMs) == other

    companion object {
        fun fromWindows(host: String, capturedAtMs: Long, display: HostRect, selectedWindowId: Int,
            windows: List<VisibleHostWindow>, ownOverlayPackage: String? = null): HostWindowOwnership? {
            if (!display.valid || windows.isEmpty()) return null
            val selected = windows.singleOrNull { it.id == selectedWindowId } ?: return null
            if (selected.packageName != host || !selected.application || selected.pictureInPicture) return null
            val bounds = selected.bounds?.intersection(display)?.takeIf { it.valid } ?: return null
            val excluded = mutableListOf<HostRect>()
            val placementExcluded = mutableListOf<HostRect>()
            for (window in windows) {
                if (window.id == selected.id) continue
                // An unlocated visible window could cover any pixel, even if its root is unreadable.
                val otherBounds = window.bounds?.takeIf { it.valid } ?: return null
                if (window.layer == selected.layer && otherBounds.overlaps(bounds)) return null
                if (window.layer > selected.layer) otherBounds.intersection(display)?.let {
                    excluded += it
                    // Our own known accessibility window masks OCR, but must not repel its own chip.
                    // A foreign or unreadable root never receives this placement exception.
                    if (!(ownOverlayPackage != null && window.packageName == ownOverlayPackage &&
                        window.accessibilityOverlay)) placementExcluded += it
                }
            }
            return HostWindowOwnership(host, capturedAtMs, display, selected.id, bounds,
                sortedBounds(excluded), sortedBounds(placementExcluded))
        }

        private fun sortedBounds(bounds: List<HostRect>) = bounds.distinct()
            .sortedWith(compareBy({ it.top }, { it.left }, { it.bottom }, { it.right }))
    }
}

/** The service supplies a live, read-only OS-window probe to both screenshot capture lanes. */
object HostWindowOwnershipSource {
    private data class Probe(val owner: Any, val read: (String, Long) -> HostWindowOwnership?)
    private val probe = AtomicReference<Probe?>(null)

    fun attach(owner: Any, read: (String, Long) -> HostWindowOwnership?) { probe.set(Probe(owner, read)) }
    fun detach(owner: Any) { probe.get()?.takeIf { it.owner === owner }?.let { probe.compareAndSet(it, null) } }
    fun current(host: String, atMs: Long): HostWindowOwnership? = probe.get()?.let {
        runCatching { it.read(host, atMs) }.getOrNull()
    }
}
