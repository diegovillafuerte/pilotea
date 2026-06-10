package mx.kompara.ui.paywall

/**
 * Which premium surface a gate event happened on (B-050). Used purely to bucket the conversion
 * counters by surface so we can see where the upsell lands. The [key] is the stable string used in the
 * DataStore counter key and the Logcat line (renaming a constant never silently resets a counter).
 */
enum class GateSurface(val key: String) {
    /** City percentile bars on the metric cards (Inicio / week summary). */
    BENCHMARKS("benchmarks"),

    /** The Comparar tab (built next wave). */
    COMPARE("compare"),

    /** History rows older than the free window. */
    HISTORY("history"),

    /** The Fiscal / IMSS tab. */
    FISCAL("fiscal"),

    /** Advanced recommendations slot. */
    RECOMMENDATIONS("recommendations"),

    /** The paywall screen opened not from a specific surface (e.g. a generic "upgrade" entry). */
    GENERIC("generic"),
}

/**
 * The conversion-funnel steps tracked per surface (B-050).
 *  - [GATE_SHOWN]   the tease-then-gate preview was rendered to a locked driver.
 *  - [PAYWALL_OPENED] the driver tapped the CTA and the paywall screen opened.
 *  - [TRIAL_STARTED] the driver tapped the trial CTA and the billing flow launched.
 */
enum class GateEvent(val key: String) {
    GATE_SHOWN("gate_shown"),
    PAYWALL_OPENED("paywall_opened"),
    TRIAL_STARTED("trial_started"),
}

/**
 * Records premium-gate conversion events. Mirrors the onboarding funnel (B-036): local-only, NO
 * personal data — just per-(surface, event) integer counts — so it is safe to collect with no network.
 * A future task can fold these into the anonymous telemetry uploader; for now they live in DataStore +
 * Logcat. An interface so the surfaces depend on it and tests swap a fake recorder.
 */
interface GateFunnel {
    /** Record one [event] on [surface]. Increments the matching counter. */
    suspend fun record(surface: GateSurface, event: GateEvent)
}

/**
 * Pure (Android-free) counter bookkeeping for the gate funnel, split out so the key naming and
 * increment maths are unit-tested on the plain JVM (mirrors [mx.kompara.ui.onboarding.FunnelCounters]).
 */
object GateCounters {

    /** Prefix applied to every gate counter key so they share a namespace in DataStore. */
    const val KEY_PREFIX = "gate_funnel_"

    /** Stable DataStore/preferences key for a (surface, event) count, e.g. `gate_funnel_fiscal_gate_shown`. */
    fun key(surface: GateSurface, event: GateEvent): String =
        "$KEY_PREFIX${surface.key}_${event.key}"

    /** Next value for a counter, guarding against a negative/overflowed prior value. */
    fun increment(current: Int?): Int {
        val base = (current ?: 0).coerceAtLeast(0)
        return if (base == Int.MAX_VALUE) base else base + 1
    }

    /** The line written to Logcat for a recorded event (no personal data). */
    fun logLine(surface: GateSurface, event: GateEvent, newCount: Int): String =
        "gate surface=${surface.key} event=${event.key} count=$newCount"
}
