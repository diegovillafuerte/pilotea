package mx.kompara.ui.stats

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.metrics.VerdictLevel

/**
 * Everything the day-detail screen renders for one local day (B-040 req 2): the per-shift timeline,
 * the offer funnel (seen vs taken vs declined, with each offer's captured verdict), and the
 * best-hour blocks. Assembled purely from already-fetched rows by [DayDetailBuilder] so the math is
 * unit-testable without Room.
 */
data class DayDetail(
    val dayIso: String,
    val period: PeriodStats,
    val shifts: List<ShiftTimelineItem>,
    val offers: OfferFunnel,
    val bestHours: List<HourBlock>,
    val completeness: CompletenessHint,
)

/** One shift on the timeline: its window, trip count and net. */
data class ShiftTimelineItem(
    val shiftId: Long,
    val startedAt: Long,
    /** Null while the shift is still open. */
    val endedAt: Long?,
    val tripCount: Int,
    val netMxn: Double,
)

/** Aggregate offer outcomes for the day plus the per-offer rows (with verdicts). */
data class OfferFunnel(
    val seen: Int,
    val taken: Int,
    val declined: Int,
    val pending: Int,
    val rows: List<OfferRow>,
)

/** One captured offer in the day's funnel, with its persisted verdict (null if never evaluated). */
data class OfferRow(
    val offerId: Long,
    val seenAt: Long,
    val platform: String,
    val fareMxn: Double,
    val outcome: OfferOutcome,
    val verdict: VerdictLevel?,
)

/**
 * Folds the day's raw rows into a [DayDetail]. The caller supplies the rows already scoped to the
 * day window and the [marginalCostPerKm] from the active cost profile.
 */
object DayDetailBuilder {

    fun build(
        dayIso: String,
        period: PeriodStats,
        shifts: List<ShiftEntity>,
        trips: List<TripEntity>,
        offers: List<OfferEntity>,
        bestHours: List<HourBlock>,
    ): DayDetail {
        val tripCountByShift = trips
            .filter { it.endedAt != null }
            .groupingBy { it.shiftId }
            .eachCount()

        val timeline = shifts
            .sortedBy { it.startedAt }
            .map { shift ->
                ShiftTimelineItem(
                    shiftId = shift.id,
                    startedAt = shift.startedAt,
                    endedAt = shift.endedAt,
                    // Prefer the shift's own counters; fall back to counting the day's trips if the
                    // counters lag (incremental close races the rollup).
                    tripCount = if (shift.tripCount > 0) shift.tripCount else (tripCountByShift[shift.id] ?: 0),
                    netMxn = shift.netMxn,
                )
            }

        val rows = offers.map { it.toRow() }.sortedBy { it.seenAt }
        val funnel = OfferFunnel(
            seen = rows.size,
            taken = rows.count { it.outcome == OfferOutcome.ACCEPTED },
            declined = rows.count { it.outcome == OfferOutcome.DECLINED || it.outcome == OfferOutcome.EXPIRED },
            pending = rows.count { it.outcome == OfferOutcome.PENDING },
            rows = rows,
        )

        return DayDetail(
            dayIso = dayIso,
            period = period,
            shifts = timeline,
            offers = funnel,
            bestHours = bestHours,
            completeness = CompletenessHints.hintFor(
                hasTrips = period.totalTrips > 0,
                hoursOnline = period.hoursOnline,
                tripsEstimated = true,
            ),
        )
    }

    private fun OfferEntity.toRow(): OfferRow = OfferRow(
        offerId = id,
        seenAt = seenAt,
        platform = platform,
        fareMxn = fareMxn,
        outcome = runCatching { OfferOutcome.valueOf(outcome) }.getOrDefault(OfferOutcome.PENDING),
        verdict = verdict?.let { name -> runCatching { VerdictLevel.valueOf(name) }.getOrNull() },
    )
}
