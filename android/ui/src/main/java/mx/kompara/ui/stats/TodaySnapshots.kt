package mx.kompara.ui.stats

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.metrics.TodayMoneySnapshot
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Re-read the wall clock on every tick, including after sleep or a manual clock change. */
fun AppClock.moments(): Flow<Instant> = flow {
    while (currentCoroutineContext().isActive) {
        val now = nowMs()
        emit(Instant.ofEpochMilli(now))
        delay(1_000L - Math.floorMod(now, 1_000L))
    }
}

/** One as-of/Room transaction stream for Hoy and today's existing drill-in. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TodaySnapshots @Inject constructor(clock: AppClock, money: PeriodMoneyRepository, zone: ZoneId) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refresh = MutableStateFlow(0)
    val state: SharedFlow<Result<TodayMoneySnapshot>> = refresh.flatMapLatest {
        clock.moments().flatMapLatest { money.observeToday(it, zone) }
            .map { Result.success(it) }
            .catch { emit(Result.failure(it)) }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)
    fun retry() { refresh.value++ }
}
