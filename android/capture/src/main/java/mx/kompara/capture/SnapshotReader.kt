package mx.kompara.capture

/**
 * Supplies the current window snapshot on demand.
 *
 * The [EventPipeline] depends on this abstraction rather than the live service so the debounce
 * logic can be tested with a fake reader. On device the implementation is backed by the service's
 * `rootInActiveWindow` (see [KomparaAccessibilityService]).
 */
fun interface SnapshotReader {
    /**
     * Reads the current snapshot for [packageName] at [timestampMs], or null if no window content
     * is currently available (e.g. the service lost its active window).
     */
    fun read(packageName: String, timestampMs: Long): ScreenSnapshot?
}
