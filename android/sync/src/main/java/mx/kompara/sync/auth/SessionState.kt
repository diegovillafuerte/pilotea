package mx.kompara.sync.auth

import mx.kompara.sync.api.DriverDto

/**
 * The driver's authentication state, exposed as a [kotlinx.coroutines.flow.Flow]
 * by [AuthRepository].
 *
 * Anonymous-first: the app is fully usable in [Anonymous]; an account
 * ([Authenticated]) only unlocks sync/benchmarks/premium. [Unknown] is the
 * brief pre-read state before the persisted token has been loaded.
 */
sealed interface SessionState {
    /** Initial state before persisted auth has been read from DataStore. */
    data object Unknown : SessionState

    /** No account yet — reader + local stats work; sync/premium are gated. */
    data object Anonymous : SessionState

    /** A valid session token is held; [driver] is the last-known profile. */
    data class Authenticated(val driver: DriverDto) : SessionState
}
