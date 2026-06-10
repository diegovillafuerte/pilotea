package mx.kompara.metrics.compare

import kotlin.math.abs

/**
 * The pure cross-platform comparison engine behind the Comparar tab (B-047). Given two platforms'
 * weekly figures it produces one [CompareRow] per metric — who paid better and by how much — plus a
 * one-line [CompareVerdict] for the most material metric. Android-free so the winner/inversion/pct
 * maths are unit-tested on the plain JVM; `:ui` formats the result and paints the bars.
 *
 * ## Rules (the contract the tests pin)
 *  - **Comparable only when BOTH platforms report the metric.** A one-sided null (one platform never
 *    reports hours, e.g. inDrive) yields a [CompareRow] with `winner = null` and `comparable = false`
 *    so the screen can dim it and say "No comparable".
 *  - **Winner = the higher value, EXCEPT [CompareMetric.COMMISSION_PCT] where lower wins** (a smaller
 *    cut is better). The inversion lives on the metric ([CompareMetric.lowerIsBetter]) so adding a
 *    future "lower is better" metric is a one-line change, not a special case here.
 *  - **A tie** (equal values, within [TIE_EPSILON]) is [CompareWinner.TIE] with a 0 % difference.
 *  - **Percent difference is relative to the LOSER** — "DiDi paid 12 % more per km than Uber" means
 *    `(winner - loser) / loser`. When the loser is 0 the percentage is undefined ([CompareRow.pctDifference] = null)
 *    but the row is still comparable and has a winner (any positive value beats 0).
 *  - **The verdict picks the single most material comparable metric**, in priority order: $/km, then
 *    $/hora, then $/viaje (the fields a captured week always has). If none of those three is
 *    comparable, there is no verdict line ([CompareResult.verdict] = null) even when other rows compare.
 */
object CompareCalculator {

    /** Values within this absolute delta are treated as equal (a [CompareWinner.TIE]). */
    const val TIE_EPSILON: Double = 1e-9

    /** Metrics the verdict line will consider, most-material first. */
    private val VERDICT_PRIORITY: List<CompareMetric> = listOf(
        CompareMetric.EARNINGS_PER_KM,
        CompareMetric.EARNINGS_PER_HOUR,
        CompareMetric.EARNINGS_PER_TRIP,
    )

    /**
     * Compare [a] against [b] across every [CompareMetric], in metric declaration order. The two
     * inputs name the platforms (A and B) and carry a value-per-metric map (a null/absent entry means
     * "this platform doesn't report that metric this week").
     */
    fun compare(a: PlatformMetrics, b: PlatformMetrics): CompareResult {
        val rows = CompareMetric.entries.map { metric -> rowFor(metric, a, b) }
        val verdict = verdictFor(rows, a.platform, b.platform)
        return CompareResult(platformA = a.platform, platformB = b.platform, rows = rows, verdict = verdict)
    }

    private fun rowFor(metric: CompareMetric, a: PlatformMetrics, b: PlatformMetrics): CompareRow {
        val valueA = a.value(metric)
        val valueB = b.value(metric)
        if (valueA == null || valueB == null) {
            // One-sided (or no) data — not comparable. Reason names the platform that's missing it.
            val missing = when {
                valueA == null && valueB == null -> null
                valueA == null -> a.platform
                else -> b.platform
            }
            return CompareRow(
                metric = metric,
                valueA = valueA,
                valueB = valueB,
                winner = null,
                pctDifference = null,
                comparable = false,
                missingPlatform = missing,
            )
        }

        val winner = winnerOf(metric, valueA, valueB)
        val pct = pctDifference(winner, valueA, valueB)
        return CompareRow(
            metric = metric,
            valueA = valueA,
            valueB = valueB,
            winner = winner,
            pctDifference = pct,
            comparable = true,
            missingPlatform = null,
        )
    }

    private fun winnerOf(metric: CompareMetric, valueA: Double, valueB: Double): CompareWinner {
        if (abs(valueA - valueB) <= TIE_EPSILON) return CompareWinner.TIE
        val aBeatsB = if (metric.lowerIsBetter) valueA < valueB else valueA > valueB
        return if (aBeatsB) CompareWinner.A else CompareWinner.B
    }

    /**
     * Percent the winner leads the loser by, relative to the loser: `(winner - loser) / loser`. Always
     * non-negative. Null when there's no winner (a tie) or the loser is 0 (the ratio is undefined).
     */
    private fun pctDifference(winner: CompareWinner, valueA: Double, valueB: Double): Double? {
        val (winnerValue, loserValue) = when (winner) {
            CompareWinner.A -> valueA to valueB
            CompareWinner.B -> valueB to valueA
            CompareWinner.TIE -> return 0.0
        }
        if (loserValue == 0.0) return null
        return abs(winnerValue - loserValue) / abs(loserValue)
    }

    /**
     * The headline verdict: the most material comparable metric ($/km > $/hora > $/viaje). Null when
     * none of those three compared this week (so the screen shows the rows but no summary line).
     */
    private fun verdictFor(
        rows: List<CompareRow>,
        platformA: String,
        platformB: String,
    ): CompareVerdict? {
        val byMetric = rows.associateBy { it.metric }
        val chosen = VERDICT_PRIORITY.firstNotNullOfOrNull { metric ->
            byMetric[metric]?.takeIf { it.comparable }
        } ?: return null

        val winnerPlatform = when (chosen.winner) {
            CompareWinner.A -> platformA
            CompareWinner.B -> platformB
            CompareWinner.TIE, null -> null
        }
        return CompareVerdict(
            metric = chosen.metric,
            winner = chosen.winner ?: CompareWinner.TIE,
            winnerPlatform = winnerPlatform,
            loserPlatform = when (chosen.winner) {
                CompareWinner.A -> platformB
                CompareWinner.B -> platformA
                CompareWinner.TIE, null -> null
            },
            pctDifference = chosen.pctDifference,
        )
    }
}
