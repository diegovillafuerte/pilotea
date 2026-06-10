package mx.kompara.ui.onboarding

/**
 * Pure (Android-free) counter bookkeeping for the onboarding funnel, split out from the
 * DataStore-backed [DataStoreOnboardingFunnel] so the key naming and increment maths are unit-tested
 * on the plain JVM.
 */
object FunnelCounters {

    /** Prefix applied to every funnel counter key so they share a namespace in DataStore. */
    const val KEY_PREFIX = "onboarding_funnel_"

    /** Stable DataStore/preferences key for a step's count. */
    fun key(step: OnboardingStep): String = "$KEY_PREFIX${step.key}"

    /** Next value for a counter, guarding against a negative/overflowed prior value. */
    fun increment(current: Int?): Int {
        val base = (current ?: 0).coerceAtLeast(0)
        return if (base == Int.MAX_VALUE) base else base + 1
    }

    /** The line written to Logcat for a recorded step (no personal data). */
    fun logLine(step: OnboardingStep, newCount: Int): String =
        "funnel step=${step.key} count=$newCount"
}
