package mx.kompara.ui.onboarding

/**
 * The driver's response to the prominent disclosure screen. The disclosure is a hard gate: nothing
 * deep-links to Settings until the driver has explicitly [ACCEPTED]; [DECLINED] routes to a limited
 * info screen and never proceeds to the accessibility grant (Play prominent-disclosure standard,
 * B-036 / legal posture B-038).
 */
enum class DisclosureDecision {
    /** No choice made yet — show the disclosure, both buttons live. */
    PENDING,

    /** "Aceptar y continuar" — proceed to the accessibility-grant walkthrough. */
    ACCEPTED,

    /** "No acepto" — route to the limited info screen; do NOT deep-link to Settings. */
    DECLINED,
}

/**
 * Pure state machine for the disclosure screen. Holds the current [DisclosureDecision] and enforces
 * the legal invariant that the funnel may only advance to the accessibility grant once the driver
 * has accepted. Split out from the composable so the accept/decline transitions are unit-tested
 * without Compose.
 */
class DisclosureStateMachine(initial: DisclosureDecision = DisclosureDecision.PENDING) {

    var decision: DisclosureDecision = initial
        private set

    /** Record acceptance. Returns true if this transition newly granted consent. */
    fun accept(): Boolean {
        if (decision == DisclosureDecision.ACCEPTED) return false
        decision = DisclosureDecision.ACCEPTED
        return true
    }

    /** Record decline. Returns true if this transition newly recorded a decline. */
    fun decline(): Boolean {
        if (decision == DisclosureDecision.DECLINED) return false
        decision = DisclosureDecision.DECLINED
        return true
    }

    /** Whether the funnel may advance past the disclosure to the accessibility grant. */
    val mayProceedToAccessibility: Boolean
        get() = decision == DisclosureDecision.ACCEPTED

    /** Whether the driver should be shown the limited info screen instead of proceeding. */
    val shouldShowLimitedInfo: Boolean
        get() = decision == DisclosureDecision.DECLINED
}
