package mx.kompara.ui.onboarding

/**
 * Records onboarding funnel step transitions. The make-or-break funnel (B-036) is the metric we
 * most need to watch, so every step records.
 *
 * v1 is local-only and carries NO personal data — just per-step integer counts — so it is safe to
 * collect with no network. A future task wires these counters into the anonymous telemetry uploader
 * (B-034); for now they live in DataStore and Logcat. Kept as an interface so the logic that drives
 * it (the screens) is testable against a fake recorder.
 */
interface OnboardingFunnel {
    /** Record that the driver reached [step]. Idempotent per call (it increments a counter). */
    suspend fun record(step: OnboardingStep)
}
