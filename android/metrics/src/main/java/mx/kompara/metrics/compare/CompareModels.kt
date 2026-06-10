package mx.kompara.metrics.compare

/**
 * The per-metric axes the Comparar tab lines two platforms up on (B-047). Declaration order is the
 * order rows render. The economic rates lead (they're what a chofer optimises for); volume totals and
 * the captured-only acceptance rate follow.
 *
 * @property lowerIsBetter when true the WINNER is the platform with the SMALLER value (commission: a
 *   smaller cut is better). Every other metric is "higher wins". Keeping the inversion here means the
 *   [CompareCalculator] never special-cases a metric name.
 */
enum class CompareMetric(val lowerIsBetter: Boolean = false) {
    /** Net earnings per online hour ($/hora). */
    EARNINGS_PER_HOUR,

    /** Net earnings per km ($/km). */
    EARNINGS_PER_KM,

    /** Net earnings per completed trip ($/viaje). */
    EARNINGS_PER_TRIP,

    /** Net earnings for the week ($). */
    NET_EARNINGS,

    /** Completed trips in the week. */
    TOTAL_TRIPS,

    /** Fraction of offers accepted (captured path only). */
    ACCEPTANCE_RATE,

    /**
     * The platform's commission cut as a fraction. **Lower is better** — a smaller cut leaves the
     * chofer more. Not on the captured path's [PlatformMetrics] today (an imported week can carry it);
     * modelled here so the inversion rule is exercised and the metric is ready when imports supply it.
     */
    COMMISSION_PCT(lowerIsBetter = true),
}

/**
 * One platform's value for every [CompareMetric] this week. A null/absent value means the platform
 * doesn't report that metric (e.g. inDrive never reports hours → no $/hora), which makes the row "No
 * comparable" against any other platform.
 *
 * Built from a [mx.kompara.data.db.entity.WeeklyAggregateEntity] via [fromWeekly]; commission is left
 * null on the captured path and can be wired in when imports carry it.
 */
data class PlatformMetrics(
    /** [mx.kompara.data.model.Platform] name (e.g. "UBER"). Opaque to the calculator. */
    val platform: String,
    private val values: Map<CompareMetric, Double?>,
) {
    /** This platform's value for [metric], or null when it doesn't report it this week. */
    fun value(metric: CompareMetric): Double? = values[metric]

    companion object {
        /**
         * Assemble from explicit per-metric values (the captured path passes the five rate/volume
         * fields; commission is null unless an imported week supplies it).
         */
        fun of(
            platform: String,
            earningsPerHour: Double? = null,
            earningsPerKm: Double? = null,
            earningsPerTrip: Double? = null,
            netEarnings: Double? = null,
            totalTrips: Int? = null,
            acceptanceRate: Double? = null,
            commissionPct: Double? = null,
        ): PlatformMetrics = PlatformMetrics(
            platform = platform,
            values = mapOf(
                CompareMetric.EARNINGS_PER_HOUR to earningsPerHour,
                CompareMetric.EARNINGS_PER_KM to earningsPerKm,
                CompareMetric.EARNINGS_PER_TRIP to earningsPerTrip,
                CompareMetric.NET_EARNINGS to netEarnings,
                CompareMetric.TOTAL_TRIPS to totalTrips?.toDouble(),
                CompareMetric.ACCEPTANCE_RATE to acceptanceRate,
                CompareMetric.COMMISSION_PCT to commissionPct,
            ),
        )
    }
}

/** Which side of a comparison won a metric. */
enum class CompareWinner {
    /** Platform A (the first argument to [CompareCalculator.compare]) paid better. */
    A,

    /** Platform B paid better. */
    B,

    /** The two are equal (within [CompareCalculator.TIE_EPSILON]). */
    TIE,
}

/**
 * One metric's verdict between platforms A and B.
 *
 * @property valueA / [valueB] each platform's value, or null when it doesn't report the metric.
 * @property winner the better platform, or null when [comparable] is false.
 * @property pctDifference how much the winner leads the loser, relative to the loser (0..n); null on a
 *   tie-with-zero or when not comparable. 0.0 on an exact tie.
 * @property comparable true only when BOTH platforms reported the metric.
 * @property missingPlatform when not comparable, the platform missing the metric (drives the "inDrive
 *   no reporta horas" reason); null when neither or both are present.
 */
data class CompareRow(
    val metric: CompareMetric,
    val valueA: Double?,
    val valueB: Double?,
    val winner: CompareWinner?,
    val pctDifference: Double?,
    val comparable: Boolean,
    val missingPlatform: String?,
)

/**
 * The one-line headline ("DiDi te pagó 12 % más por km que Uber esta semana"), built from the most
 * material comparable metric. `:ui` turns this into the localized sentence.
 *
 * @property winnerPlatform / [loserPlatform] null on a tie (the sentence then reads "empate").
 * @property pctDifference null when the loser was 0 (lead is undefined) or on a tie (then 0.0).
 */
data class CompareVerdict(
    val metric: CompareMetric,
    val winner: CompareWinner,
    val winnerPlatform: String?,
    val loserPlatform: String?,
    val pctDifference: Double?,
)

/**
 * The full comparison: the named platforms, one [CompareRow] per metric (metric order), and the
 * headline [verdict] (null when none of $/km, $/hora, $/viaje compared this week).
 */
data class CompareResult(
    val platformA: String,
    val platformB: String,
    val rows: List<CompareRow>,
    val verdict: CompareVerdict?,
) {
    /** The comparable rows (both platforms reported) — what the screen renders as paired bars. */
    val comparableRows: List<CompareRow> get() = rows.filter { it.comparable }

    /** True when at least one metric compared (so there's something material to show). */
    val hasComparable: Boolean get() = rows.any { it.comparable }
}
