package mx.kompara.ui.paywall

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import mx.kompara.billing.PromotionalTrials
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.auth.SessionState
import mx.kompara.ui.stats.*
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Evidence of a result purchase actually reveals; never substitute an illustrative own amount. */
data class PremiumEvidence(
    val benefits: List<String> = emptyList(),
    val headline: String = "¿Qué te muestra Premium?",
    val ownAmount: String? = null,
) { val available get() = benefits.isNotEmpty() }
fun interface PremiumSalesSource { suspend fun check(surface: GateSurface): PremiumEvidence }
fun interface PremiumIdentity { suspend fun accountId(): String? }
class LocalPremiumSales @Inject constructor(
    private val periods: SelectedPeriod, private val money: PeriodMoneyRepository,
    private val clock: AppClock, private val zone: ZoneId,
) : PremiumSalesSource {
    override suspend fun check(surface: GateSurface): PremiumEvidence {
        val now = Instant.ofEpochMilli(clock.nowMs()); val today = now.atZone(zone).toLocalDate()
        val start = SelectedPeriod.resolve(periods.week.first(), today)
        val week = money.weekSnapshot(PeriodWindow(start, start.plusDays(6), now, zone))
        val month = SelectedPeriod.resolveMonth(periods.month.first(), today)
        val monthly = money.monthSnapshot(PeriodWindow(month.atDay(1), month.atEndOfMonth(), now, zone))
        val history = if (surface == GateSurface.GENERIC) (week.history.mapNotNull { runCatching { java.time.LocalDate.parse(it.weekStart) }.getOrNull() } + start).distinct() else listOf(start)
        var detail = false
        for (date in history.filter { !HistoryWeeks.isFree(it, today) && it <= today }) {
            if (WeekPresentation(money.weekSnapshot(PeriodWindow(date, date.plusDays(6), now, zone))).breakdownAvailable) { detail = true; break }
        }
        val pdf = ImssTracker.payments(monthly.money).isNotEmpty()
        if (surface == GateSurface.MONTH_HISTORY) return PremiumEvidence(
            if (MonthPresentation(monthly).locked) listOf("El detalle de cada mes") else emptyList(), "El detalle de tus meses")
        // Current cohort/import adapters cannot certify a paid city result (S11/TD-053).
        // A comparison entry must not sell unrelated data to compensate for an absent cohort.
        if (surface in setOf(GateSurface.COMPARE, GateSurface.BENCHMARKS, GateSurface.RECOMMENDATIONS)) return PremiumEvidence()
        val benefits = buildList {
            if (detail) add("El detalle de cada semana")
            if (pdf && surface != GateSurface.HISTORY) add("Tu mes en PDF")
        }
        return PremiumEvidence(benefits, if (surface == GateSurface.HISTORY) "El detalle de tus semanas" else "Tus registros, con más detalle")
    }
}
class AuthPremiumIdentity @Inject constructor(private val auth: AuthRepository) : PremiumIdentity {
    override suspend fun accountId(): String? = (auth.sessionState.first { it !is SessionState.Unknown } as? SessionState.Authenticated)?.driver?.id
}
@Module @InstallIn(SingletonComponent::class)
abstract class PremiumModule {
    @Binds abstract fun sales(impl: LocalPremiumSales): PremiumSalesSource
    @Binds abstract fun identity(impl: AuthPremiumIdentity): PremiumIdentity
    companion object {
        @Provides fun trials(): PromotionalTrials = PromotionalTrials.UNSUPPORTED
    }
}
