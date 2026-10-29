package mx.kompara.ui.stats

import kotlinx.coroutines.flow.distinctUntilChanged
import mx.kompara.data.settings.SettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** One durable selection for earnings and comparison. A null selection follows the current week. */
@Singleton
class SelectedPeriod @Inject constructor(private val settings: SettingsRepository) {
    val week = settings.selectedWeekStart.distinctUntilChanged()
    val month = settings.selectedMonth.distinctUntilChanged()
    suspend fun selectMonth(month: java.time.YearMonth?, today: LocalDate) = settings.selectMonth(
        month?.toString(), lastClosedWeek(month ?: java.time.YearMonth.from(today), today).toString())
    suspend fun select(date: LocalDate) = settings.selectWeekStart(date.monday().toString())
    suspend fun followCurrentWeek() = settings.selectWeekStart(null)
    companion object {
        fun resolveMonth(saved: String?, today: LocalDate): java.time.YearMonth = saved?.let {
            runCatching { java.time.YearMonth.parse(it).coerceAtMost(java.time.YearMonth.from(today)) }.getOrNull()
        } ?: java.time.YearMonth.from(today)

        /** Last Sunday inside this month which has ended; never a future/partial week. */
        fun lastClosedWeek(month: java.time.YearMonth, today: LocalDate): LocalDate =
            minOf(month.atEndOfMonth(), today.minusDays(1))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).minusDays(6)

        fun resolve(saved: String?, today: LocalDate): LocalDate = saved?.let {
            runCatching { LocalDate.parse(it).monday().coerceAtMost(today.monday()) }.getOrNull()
        } ?: today.monday()
    }
}

fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
