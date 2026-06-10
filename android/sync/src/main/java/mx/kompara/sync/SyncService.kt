package mx.kompara.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import mx.kompara.data.di.IoDispatcher
import javax.inject.Inject

/**
 * Consented aggregate sync to the thin backend (android-technical-design.md §4). No-op until the
 * backend and WorkManager rollup jobs land; present here to prove the DI graph wires `:sync`
 * against `:data` and an injected IO dispatcher.
 */
class SyncService @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** Push consented aggregates. Returns the number of records synced (0 while a no-op). */
    suspend fun syncNow(): Int = withContext(dispatcher) {
        // TODO(B-0xx): implement consented aggregate sync once the backend exists.
        0
    }
}
