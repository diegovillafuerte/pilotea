package mx.kompara.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import mx.kompara.parsers.snapshot.ParserSnapshot
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Records real offer-card snapshots for fixture capture. No-op unless explicitly enabled. */
interface SnapshotRecorder {
    fun record(snapshot: ParserSnapshot, outcome: String)
}

/** Default for tests and any non-recording context. */
object NoOpSnapshotRecorder : SnapshotRecorder {
    override fun record(snapshot: ParserSnapshot, outcome: String) = Unit
}

/**
 * Debug-only fixture recorder. Writes PII-scrubbed [ParserSnapshot]s of real offer cards to the
 * app's external files dir so they can be pulled with `adb pull` and turned into parser fixtures —
 * the reliable way to capture live cards, since `uiautomator dump` fails on the animated countdown
 * ring and our event-driven reader does not.
 *
 * No-op in release builds (gated on the debuggable flag, so no BuildConfig dependency). Records only
 * snapshots that look like an offer card (contain a currency token plus a distance/time unit) so a
 * busy host home screen doesn't flood storage. The input is already scrubbed by
 * [OfferEventPipeline], so names/plates/addresses are masked while the numeric offer data we tune
 * the spec against survives.
 *
 * Pull with: `adb pull /sdcard/Android/data/mx.kompara.app/files/fixtures ~/kompara-fixtures/`
 */
@Singleton
class FileSnapshotRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) : SnapshotRecorder {
    private val enabled: Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val json = Json { prettyPrint = true }

    override fun record(snapshot: ParserSnapshot, outcome: String) {
        if (!enabled || !looksLikeOffer(snapshot)) return
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "fixtures").apply { mkdirs() }
            val safePkg = snapshot.packageName.replace('.', '_')
            File(dir, "${safePkg}_${snapshot.timestampMs}_$outcome.json")
                .writeText(json.encodeToString(ParserSnapshot.serializer(), snapshot))
        }
    }

    private fun looksLikeOffer(snapshot: ParserSnapshot): Boolean {
        val text = snapshot.nodes.asSequence().mapNotNull { it.text }.joinToString(" ")
        return text.contains("$") &&
            (text.contains("km", ignoreCase = true) || text.contains("min", ignoreCase = true))
    }
}
