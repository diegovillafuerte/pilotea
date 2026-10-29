package mx.kompara.ui.onboarding

/**
 * The ordered stages of the onboarding funnel. The order is the funnel order: drop-off between any
 * two consecutive steps is the number we ultimately care about, so [ordinal] doubles as the funnel
 * position. The [key] is the stable string logged to Logcat and used as the DataStore counter key
 * (so renaming an enum constant never silently resets a counter).
 */
enum class OnboardingStep(val key: String) {
    /** Funnel started — interactive demo shown. */
    PITCH("pitch"),

    /** Signup flow shown (phone → WhatsApp code, optional). */
    SIGNUP("signup"),

    /** OTP verified — the account exists and the session is live. */
    SIGNUP_DONE("signup_done"),

    /** Prominent disclosure screen shown (Play-policy + legal posture). */
    DISCLOSURE("disclosure"),

    /** Driver tapped "Acepto" on the disclosure. */
    DISCLOSURE_ACCEPTED("disclosure_accepted"),

    /** Driver tapped "No acepto" — continues to Hoy with the reader off. */
    DISCLOSURE_DECLINED("disclosure_declined"),

    /** Accessibility-grant walkthrough shown. */
    ACCESSIBILITY("accessibility"),

    /** Android reports permission granted; service binding is separate. */
    ACCESSIBILITY_GRANTED("accessibility_granted"),

    /** OEM survival-kit screen shown. */
    OEM("oem"),

    /** Onboarding completed or skipped; Hoy is available. */
    DONE("done"),
}
