package mx.kompara.metrics.imss

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.data.accounting.PeriodWindow
import mx.kompara.data.db.KomparaDatabase
import mx.kompara.metrics.PeriodMoneyRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The IMSS tab's month (M1): the ledger's taken trips by app plus the typed incentives, and which apps
 * drove the month before, one transaction.
 */
@Singleton
class ImssMonthRepository @Inject constructor(
    private val database: KomparaDatabase,
    private val periods: PeriodMoneyRepository,
) {
    /** [asOf] is "now": it sets today, and no trip after it counts. */
    fun observe(month: YearMonth, asOf: Instant, zone: ZoneId): Flow<ImssMonth> =
        database.invalidationTracker.createFlow("offers", "trips", "monthly_incentives").map { snapshot(month, asOf, zone) }

    suspend fun snapshot(month: YearMonth, asOf: Instant, zone: ZoneId): ImssMonth = database.withTransaction {
        val money = periods.daySnapshot(PeriodWindow(month.atDay(1), month.atEndOfMonth(), asOf, zone)).money
        val trips = money.byApp.filterValues { it.trips > 0 }.mapValues { it.value.payment ?: BigDecimal.ZERO }
        val before = month.minusMonths(1)
        val lastMonth = periods.daySnapshot(PeriodWindow(before.atDay(1), before.atEndOfMonth(), asOf, zone)).money
            .byApp.filterValues { it.trips > 0 }.keys
        val incentives = database.monthlyIncentiveDao().forMonth(month.toString()).associate { it.platform to it.amountMxn }
        ImssMonth.of(month, asOf.atZone(zone).toLocalDate(), trips, incentives, lastMonth = lastMonth)
    }
}
