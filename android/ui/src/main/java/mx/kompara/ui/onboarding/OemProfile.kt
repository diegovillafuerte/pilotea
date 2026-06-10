package mx.kompara.ui.onboarding

/**
 * The set of OEM "survival kit" profiles. Xiaomi/Oppo/Vivo task killers dominate Mexican driver
 * handsets and will silently kill a bound accessibility service unless the user whitelists autostart
 * and battery (dontkillmyapp.com patterns). Each profile carries the tailored steps shown on the OEM
 * survival-kit onboarding screen; [GENERIC] is the safe fallback for unrecognised manufacturers.
 *
 * The step copy is referenced by string-resource id so it stays in `strings.xml` (Spanish, B-036).
 * The mapping from `Build.MANUFACTURER` to a profile is pure (see [OemDetector]) so it is unit-tested
 * without a device.
 */
enum class OemProfile {
    XIAOMI,
    OPPO,
    VIVO,
    SAMSUNG,
    HUAWEI,
    GENERIC,
}
