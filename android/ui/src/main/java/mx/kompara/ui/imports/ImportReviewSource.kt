package mx.kompara.ui.imports

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.data.accounting.*
import mx.kompara.data.imports.ImportDraftEntity
import mx.kompara.metrics.PeriodMoneyRepository
import mx.kompara.sync.imports.metrics
import mx.kompara.ui.stats.AppClock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun interface ImportReviewSource { suspend fun overlap(draft: ImportDraftEntity): ImportOverlap? }

@Module
@InstallIn(SingletonComponent::class)
object ImportReviewModule {
    @Provides fun source(money: PeriodMoneyRepository, clock: AppClock): ImportReviewSource = ImportReviewSource { draft ->
        val metrics = checkNotNull(draft.metrics())
        val start = LocalDate.parse(metrics.weekStart)
        val snapshot = money.weekSnapshot(PeriodWindow(start, start.plusDays(6), Instant.ofEpochMilli(clock.nowMs()), ZoneId.of("America/Mexico_City")))
        val contributions = (snapshot.money.contributions + snapshot.money.paymentOnly).filter { it.record.app.equals(draft.platform, true) }
        if (contributions.isEmpty()) null else ImportOverlap(contributions.map { it.record },
            if (contributions.any { it.runningExpense == null }) null else contributions.fold(BigDecimal.ZERO) { sum, it -> sum + it.runningExpense!! },
            if (contributions.all { it.record.source == MoneySource.CAPTURED_ESTIMATE }) "Lector · aprox." else "Resumen guardado")
    }
}
