package mx.kompara.capture

import mx.kompara.parsers.snapshot.ParserNode
import mx.kompara.parsers.snapshot.ParserSnapshot
import mx.kompara.parsers.snapshot.toRectBox

/**
 * The `:capture` → `:parsers` adapter that TD-007 (B-029) calls for: converts the framework-coupled
 * [ScreenSnapshot] (whose node bounds are `android.graphics.Rect`) into the framework-free
 * [ParserSnapshot] the spec engine consumes.
 *
 * `:parsers` deliberately does not depend on `:capture` (it stays pure-JVM unit-testable), so this
 * edge lives here in `:capture`, which already depends on `:parsers`. The only framework touch point
 * — `Rect` → `RectBox` — is delegated to `:parsers`' own [toRectBox] helper, keeping the conversion
 * defined in exactly one place.
 *
 * @param versionCode the host app's `versionCode`, when known, so [mx.kompara.parsers.spec.SpecRegistry]
 *   can pick a version-matched spec. Null means "unknown", which specs treat as "don't exclude".
 */
fun ScreenSnapshot.toParserSnapshot(versionCode: Long? = null): ParserSnapshot =
    ParserSnapshot(
        packageName = packageName,
        timestampMs = timestampMs,
        versionCode = versionCode,
        nodes = nodes.map { it.toParserNode() },
    )

private fun SnapshotNode.toParserNode(): ParserNode =
    ParserNode(
        text = text,
        viewId = viewId,
        className = className,
        bounds = bounds.toRectBox(),
        depth = depth,
        index = index,
    )
