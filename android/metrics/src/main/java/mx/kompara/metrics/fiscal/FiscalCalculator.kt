package mx.kompara.metrics.fiscal

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Pure, deterministic fiscal summary for the "régimen de plataformas" withholding picture (B-052):
 * per calendar month, per platform, it folds the day/week aggregate rows into gross, net, estimated
 * ISR/IVA withholdings (via [PlatformWithholdingRates]), and the YTD accumulation across the months
 * up to and including the target.
 *
 * **No Android, no IO, no clock** — every input (rows, target month, "year start") is passed in, so the
 * whole matrix (rate application, missing-commission approximation, YTD math, month boundaries,
 * mixed captured+imported months) is unit-testable on the JVM. The [mx.kompara.ui] layer maps Room
 * entities into the plain [FiscalMonthInput] rows and renders the returned [FiscalMonthSummary].
 *
 * ## Gross vs. net (and the missing-commission flag)
 * The withholding base is **gross fares** (`grossMxn`). "Fiscal net" here is *not* the driver's
 * take-home after their own costs (fuel, etc. are not deductible under this regime and are excluded by
 * design) — it is **gross minus the platform's commission** where we can observe it. We infer
 * commission as `gross − net` from the aggregate rows (the captured-path `net` is gross minus marginal
 * cost; the imported-path `net` is the platform's reported net). When a platform reports no usable
 * net split (e.g. an import that only carried gross, or `net >= gross`), commission can't be inferred:
 * we set fiscal net = gross and raise [PlatformFiscalSummary.commissionApproximated] so the UI/PDF can
 * footnote it honestly. The withholdings are unaffected — they're computed on gross either way.
 *
 * ## Ordering & coverage
 * Platforms are returned sorted by name for a stable UI; a platform with no rows in the month is
 * omitted. YTD sums every in-year month from January through the target month (inclusive).
 */
class FiscalCalculator {

    /**
     * Build the [FiscalMonthSummary] for [month] from [rows] (all platforms, any month — the
     * calculator filters), applying the [rates] to gross. [ytdRows] should span the year-to-date
     * window (Jan 1 of [month]'s year through the end of [month]); rows outside that window are
     * ignored for the YTD totals. Pass the same list for both when the caller already scoped the query.
     */
    fun summarize(
        month: YearMonth,
        rows: List<FiscalMonthInput>,
        ytdRows: List<FiscalMonthInput> = rows,
        rates: PlatformWithholdingRatesSnapshot = PlatformWithholdingRatesSnapshot.DEFAULT,
    ): FiscalMonthSummary {
        val monthRows = rows.filter { inMonth(it.day, month) }
        val platforms = monthRows.map { it.platform }.distinct().sorted()

        val perPlatform = platforms.map { platform ->
            platformSummary(platform, monthRows.filter { it.platform == platform }, rates)
        }

        val ytdStart = YearMonth.of(month.year, 1)
        val ytdInWindow = ytdRows.filter { row ->
            val ym = yearMonthOf(row.day) ?: return@filter false
            !ym.isBefore(ytdStart) && !ym.isAfter(month)
        }
        val ytd = totals(
            ytdInWindow.map { it.grossMxn },
            ytdInWindow.map { it.netMxnOrGross() },
            rates,
        )

        return FiscalMonthSummary(
            month = monthKey(month),
            year = month.year,
            ratesYear = rates.year,
            platforms = perPlatform,
            monthTotals = totals(
                perPlatform.map { it.grossMxn },
                perPlatform.map { it.fiscalNetMxn },
                rates,
            ),
            ytdTotals = ytd,
        )
    }

    private fun platformSummary(
        platform: String,
        rows: List<FiscalMonthInput>,
        rates: PlatformWithholdingRatesSnapshot,
    ): PlatformFiscalSummary {
        val gross = rows.sumOf { it.grossMxn }
        // Commission is inferable only where net is a usable split of gross (0 < net <= gross). If any
        // row in the platform lacks that, we flag the whole platform's net as an approximation.
        val anyMissingSplit = rows.any { !it.hasUsableNetSplit() }
        val fiscalNet = if (anyMissingSplit) gross else rows.sumOf { it.netMxnOrGross() }
        val commission = (gross - fiscalNet).coerceAtLeast(0.0)

        val isr = gross * rates.isrRate
        val iva = gross * rates.ivaRate
        return PlatformFiscalSummary(
            platform = platform,
            grossMxn = gross,
            fiscalNetMxn = fiscalNet,
            commissionMxn = commission,
            commissionApproximated = anyMissingSplit,
            estimatedIsrMxn = isr,
            estimatedIvaMxn = iva,
        )
    }

    private fun totals(
        grosses: List<Double>,
        nets: List<Double>,
        rates: PlatformWithholdingRatesSnapshot,
    ): FiscalTotals {
        val gross = grosses.sum()
        val net = nets.sum()
        val isr = gross * rates.isrRate
        val iva = gross * rates.ivaRate
        return FiscalTotals(
            grossMxn = gross,
            fiscalNetMxn = net,
            estimatedIsrMxn = isr,
            estimatedIvaMxn = iva,
        )
    }

    private fun inMonth(dayIso: String, month: YearMonth): Boolean =
        yearMonthOf(dayIso) == month

    private fun yearMonthOf(dayIso: String): YearMonth? =
        runCatching { YearMonth.from(LocalDate.parse(dayIso, ISO_DATE)) }.getOrNull()

    private fun monthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    private companion object {
        val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

/**
 * One platform/day aggregate row, the calculator's plain input. [grossMxn] is the withholding base;
 * [netMxn] is the platform's reported (or captured) net — used to infer commission. A day is the ISO
 * date (yyyy-MM-dd, local zone), matching the aggregate tables. Daily rows are preferred; the UI maps
 * a straddling weekly row by pro-rating it into the month (mirrors B-051's month-net rule) before
 * handing it here as day-bucketed inputs.
 */
data class FiscalMonthInput(
    val platform: String,
    val day: String,
    val grossMxn: Double,
    val netMxn: Double,
) {
    /**
     * True when net is a usable split of gross (commission inferable): `0 < net <= gross`. A net of 0
     * means the row carried only gross (e.g. a gross-only import) — *not* a 100%-commission day — so
     * we treat it as "no usable split" and flag the platform's net as an approximation.
     */
    fun hasUsableNetSplit(): Boolean = grossMxn > 0.0 && netMxn > 0.0 && netMxn <= grossMxn

    /** Net to use for the fiscal-net sum: the reported net when it's a usable split, else gross. */
    fun netMxnOrGross(): Double = if (hasUsableNetSplit()) netMxn else grossMxn
}

/**
 * An immutable rate snapshot the calculator applies, defaulting to [PlatformWithholdingRates]. Passed
 * in (rather than read statically) so a future per-platform or per-year rate set is a data change, and
 * so tests can drive fixture rates without touching the constants.
 */
data class PlatformWithholdingRatesSnapshot(
    val isrRate: Double,
    val ivaRate: Double,
    val year: Int,
) {
    companion object {
        val DEFAULT = PlatformWithholdingRatesSnapshot(
            isrRate = PlatformWithholdingRates.ISR_RATE_TRANSPORT,
            ivaRate = PlatformWithholdingRates.IVA_RATE_TRANSPORT,
            year = PlatformWithholdingRates.RATES_YEAR,
        )
    }
}

/** The full fiscal picture for one month: per-platform rows + month totals + YTD totals. */
data class FiscalMonthSummary(
    /** The summarized month, "yyyy-MM". */
    val month: String,
    val year: Int,
    /** The fiscal year the applied rates describe (surfaced so a stale rate is visible). */
    val ratesYear: Int,
    val platforms: List<PlatformFiscalSummary>,
    val monthTotals: FiscalTotals,
    val ytdTotals: FiscalTotals,
) {
    /** No platform had any gross this month. */
    val isEmpty: Boolean get() = platforms.isEmpty()

    /** True when any shown platform's commission/net is an approximation (footnote trigger). */
    val anyCommissionApproximated: Boolean get() = platforms.any { it.commissionApproximated }
}

/** One platform's monthly fiscal line. */
data class PlatformFiscalSummary(
    val platform: String,
    /** Gross fares — the withholding base. */
    val grossMxn: Double,
    /** Gross minus platform commission (NOT the driver's after-cost take-home). */
    val fiscalNetMxn: Double,
    /** Inferred platform commission (gross − fiscalNet); 0 when net split is unavailable. */
    val commissionMxn: Double,
    /** True when net/commission couldn't be inferred and net was set to gross (documented approx). */
    val commissionApproximated: Boolean,
    /** Estimated ISR withheld on gross (LISR art. 113-A). */
    val estimatedIsrMxn: Double,
    /** Estimated IVA withheld on gross (LIVA art. 18-J). */
    val estimatedIvaMxn: Double,
) {
    /** Total estimated withholdings (ISR + IVA) for this platform this month. */
    val totalWithheldMxn: Double get() = estimatedIsrMxn + estimatedIvaMxn
}

/** Aggregated totals across platforms (or across the YTD window). */
data class FiscalTotals(
    val grossMxn: Double,
    val fiscalNetMxn: Double,
    val estimatedIsrMxn: Double,
    val estimatedIvaMxn: Double,
) {
    val totalWithheldMxn: Double get() = estimatedIsrMxn + estimatedIvaMxn

    companion object {
        val ZERO = FiscalTotals(0.0, 0.0, 0.0, 0.0)
    }
}
