package mx.kompara.parsers.snapshot

import android.graphics.Rect

/**
 * The single Android-framework touch point in `:parsers`: converts an `android.graphics.Rect`
 * into the framework-free serializable [RectBox] surrogate, and vice-versa.
 *
 * `:parsers` does not depend on `:capture` yet (no inter-module edge — B-029 adds it), so the
 * `ScreenSnapshot.toParserSnapshot()` extension that the design calls for cannot live here
 * without an import cycle. Instead, B-029 will add a tiny adapter in `:capture` (which already
 * depends on `:parsers`) shaped like:
 *
 * ```
 * fun ScreenSnapshot.toParserSnapshot(versionCode: Long?) = ParserSnapshot(
 *     packageName = packageName,
 *     timestampMs = timestampMs,
 *     versionCode = versionCode,
 *     nodes = nodes.map { ParserNode(it.text, it.viewId, it.className, it.bounds.toRectBox(), it.depth, it.index) },
 * )
 * ```
 *
 * delegating the only framework dependency — the [Rect] → [RectBox] conversion — to the helpers
 * below. This keeps every other file in `:parsers` pure JVM Kotlin and unit-testable.
 */
fun Rect.toRectBox(): RectBox = RectBox(left = left, top = top, right = right, bottom = bottom)

/** Inverse of [toRectBox]; useful when replaying a fixture back through Android-typed code. */
fun RectBox.toRect(): Rect = Rect(left, top, right, bottom)
