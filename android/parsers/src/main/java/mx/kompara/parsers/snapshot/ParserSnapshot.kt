package mx.kompara.parsers.snapshot

import kotlinx.serialization.Serializable

/**
 * Android-framework-free, serializable mirror of the capture module's `ScreenSnapshot`/
 * `SnapshotNode` (android-technical-design.md §1). `:parsers` deliberately does NOT depend on
 * `:capture` — B-029 wires them with a `ScreenSnapshot.toParserSnapshot()` adapter
 * (see [mx.kompara.parsers.snapshot.SnapshotMapper]). Keeping this model pure Kotlin makes the
 * whole spec engine and fixture harness unit-testable on the JVM with no Robolectric.
 *
 * Node bounds are represented as [RectBox] (a serializable surrogate) rather than
 * `android.graphics.Rect`, so a snapshot round-trips through JSON fixtures losslessly.
 */
@Serializable
data class ParserSnapshot(
    val packageName: String,
    val timestampMs: Long,
    /**
     * Host-app versionCode the snapshot was captured under, when known. Specs select on a
     * [mx.kompara.parsers.spec.VersionRange] so a host UI rewrite shipping under a new version
     * code can be matched by a different spec without any Kotlin change.
     */
    val versionCode: Long? = null,
    val nodes: List<ParserNode> = emptyList(),
)

/**
 * One flattened accessibility node. `depth`/`index` preserve tree order so relative-position
 * extractor hints ("the value after the 'Tarifa' label") work without absolute child indexing.
 */
@Serializable
data class ParserNode(
    val text: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val bounds: RectBox = RectBox(),
    val depth: Int = 0,
    val index: Int = 0,
)

/**
 * Serializable surrogate for `android.graphics.Rect`. Stored as four ints so geometry can be
 * used as a tiebreaker signal (never a primary one) and so fixtures stay framework-free.
 */
@Serializable
data class RectBox(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}
