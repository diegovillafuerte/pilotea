package mx.kompara.data.model

/**
 * Rideshare platforms Kompara reads offers from.
 *
 * Launch scope is Uber + DiDi; inDrive is a fast-follow (see android-technical-design.md §0).
 * Stored as the [name] string in Room and DataStore so adding a platform never renumbers
 * existing persisted values.
 */
enum class Platform {
    UBER,
    DIDI,
    INDRIVE,
    UNKNOWN,
}
