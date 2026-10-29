package mx.kompara.ui.stats

import mx.kompara.data.db.entity.DailyAggregateEntity
import mx.kompara.data.db.entity.WeeklyAggregateEntity
import mx.kompara.data.model.Platform
import mx.kompara.metrics.imss.CoverageStatus
import mx.kompara.metrics.imss.DailyMonthInput
import mx.kompara.metrics.imss.ImssCalculator
import mx.kompara.metrics.imss.PlatformImssStatus
import mx.kompara.metrics.imss.WeeklyMonthInput
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure glue between the rollup rows + fiscal config and the Fiscal tab's render state (B-051). Maps
 * Room entities into the [ImssCalculator]'s plain inputs, runs the per-platform calculation, and
 * keeps only the platforms the driver actually has enabled, so the screen has nothing to compute.
 *
 * Kept Android-free so the whole month→sections mapping is unit-tested without Room or Compose.
 */
object ImssTracker {

    /** Payment evidence only; no net-as-tax-base, thresholds, coverage or future projection. */
    fun payments(money: mx.kompara.data.accounting.PeriodMoney): List<RecordedAppPayment> =
        (money.contributions + money.paymentOnly).map { it.record }.groupBy { it.app }.toSortedMap(
            compareBy<String> { if (it == "UBER") "0" else it }
        ).map { (app, records) ->
            RecordedAppPayment(app,
                if (records.any { it.payment == null }) null else records.fold(java.math.BigDecimal.ZERO) { total, it -> total + it.payment!! },
                records.any { it.source == mx.kompara.data.accounting.MoneySource.CAPTURED_ESTIMATE },
                records.any { it.paymentBasis == mx.kompara.data.accounting.PaymentBasis.UNCONFIRMED },
                records.filter { !it.coverage.dated }.map { it.coverage })
        }

    private val calc = ImssCalculator()

    /**
     * Build the per-platform IMSS sections for [month] from [daily]/[weekly] rollup rows against
     * [thresholdMxn], anchored at [today]. Only platforms in [enabledPlatforms] are shown, and only
     * those that have any data this month (so an enabled-but-idle platform doesn't clutter the tab).
     * Sections are ordered by the enabled-platform declaration order for a stable UI.
     */
    fun sectionsFor(
        month: YearMonth,
        thresholdMxn: Double,
        daily: List<DailyAggregateEntity>,
        weekly: List<WeeklyAggregateEntity>,
        today: LocalDate,
        enabledPlatforms: Set<Platform>,
    ): List<PlatformImssStatus> {
        val dailyInputs = daily.map { DailyMonthInput(it.platform, it.day, it.netEarningsMxn) }
        val weeklyInputs = weekly.map { WeeklyMonthInput(it.platform, it.weekStart, it.netEarningsMxn) }

        val byPlatform = calc.statusesFor(month, thresholdMxn, dailyInputs, weeklyInputs, today)
            .associateBy { it.platform }

        // Show enabled platforms that have any data this month, in enabled-order.
        return enabledPlatforms
            .map { it.name }
            .mapNotNull { name -> byPlatform[name] }
            .filter { it.netSoFarMxn > 0.0 || it.daysRemaining > 0 }
    }

    /** Maps a [CoverageStatus] to the chip-label resource the screen renders. */
    fun statusLabelKey(status: CoverageStatus): CoverageLabel = when (status) {
        CoverageStatus.COVERED -> CoverageLabel.COVERED
        CoverageStatus.ON_TRACK -> CoverageLabel.ON_TRACK
        CoverageStatus.UNLIKELY -> CoverageLabel.UNLIKELY
    }
}

/** UI-facing coverage label (decoupled from the metrics enum so strings live in :ui). */
enum class CoverageLabel { COVERED, ON_TRACK, UNLIKELY }

/** An observed payment is not evidence of enrollment or a legally defined income base. */
data class RecordedAppPayment(val app: String, val amount: java.math.BigDecimal?, val estimated: Boolean,
    val unconfirmed: Boolean, val undatedScopes: List<mx.kompara.data.accounting.Coverage>) {
    val sourceNote: String? get() = if (!unconfirmed) null else
        if (estimated) "No confirmamos que sea tu pago" else "Pago leído del resumen · sin confirmar"
}
