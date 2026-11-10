package mx.kompara.ui.stats

import mx.kompara.data.db.entity.OfferEntity
import mx.kompara.data.db.entity.OfferOutcome
import mx.kompara.data.db.entity.ShiftEntity
import mx.kompara.data.db.entity.TripEntity
import mx.kompara.metrics.VerdictLevel
import mx.kompara.data.db.entity.TripLifecycle
import mx.kompara.data.db.entity.OfferEvaluation
import mx.kompara.data.db.entity.hasItemConfirmation
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis

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
    val rows: List<OfferRowModel>,
)

/** One captured offer in the day's funnel, with its persisted verdict (null if never evaluated). */
data class OfferRowModel(
    val offerId: Long,
    val seenAt: Long,
    val platform: String,
    val fareMxn: Double?,
    val outcome: OfferOutcome,
    val verdict: VerdictLevel?,
    val lifecycle: TripLifecycle? = null,
    val itemConfirmed: Boolean = false,
    val evaluation: OfferEvaluation? = null,
) {
    val inProgress: Boolean get() = lifecycle == TripLifecycle.ACCEPTED_ESTIMATE || lifecycle == TripLifecycle.IN_PROGRESS
    val outcomeLabel: String get() = when {
        lifecycle == TripLifecycle.CANCELLED || outcome == OfferOutcome.CANCELLED -> "Se canceló"
        inProgress -> "En curso"
        evaluation?.unavailableReason == "FARE" -> "Sin lectura"
        lifecycle == TripLifecycle.FINISHED && outcome == OfferOutcome.ACCEPTED -> "La tomaste"
        outcome in setOf(OfferOutcome.NOT_TAKEN, OfferOutcome.DECLINED, OfferOutcome.EXPIRED) -> "Se fue sin tomar"
        else -> "Sin confirmar"
    }
    val contributionLabel: String? get() = when {
        evaluation?.contribution == null -> null
        inProgress -> "Te deja"
        lifecycle == TripLifecycle.FINISHED && outcome == OfferOutcome.ACCEPTED -> "Te dejó"
        outcome in setOf(OfferOutcome.NOT_TAKEN, OfferOutcome.DECLINED, OfferOutcome.EXPIRED) -> "Te habría dejado"
        else -> null
    }

    /**
     * “A esa hora” when a limit the sentence names (green: the goal; yellow: both; red: the minimum),
     * the unit or the basis it was judged in is not the driver's current one.
     */
    fun historicalLimitSuffix(current: PlatformThreshold, unit: PreferredMetric, basis: RateBasis): String? {
        val snapshot = evaluation ?: return null
        val km = snapshot.unit == PreferredMetric.IPK.name
        if (verdict == null || verdict == VerdictLevel.UNKNOWN) return null
        fun same(saved: String, now: Double) = saved.toBigDecimal().compareTo(now.toBigDecimal()) == 0
        val goal = same(snapshot.goal, if (km) current.minPerKmMxn else current.minPerHourMxn)
        val minimum = same(snapshot.minimum, if (km) current.redPerKmMxn else current.redPerHourMxn)
        val named = when (verdict) { VerdictLevel.GREEN -> goal; VerdictLevel.RED -> minimum; else -> goal && minimum }
        return if (named && snapshot.unit == unit.name && RateBasis.fromName(snapshot.rateBasis) == basis) "" else " a esa hora"
    }
}

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

        val rows = offers.map { offer ->
            val trip = trips.firstOrNull { it.offerId == offer.id }
            offer.toRow().copy(lifecycle = trip?.let { runCatching { TripLifecycle.valueOf(it.lifecycle) }.getOrNull() },
                itemConfirmed = trip?.hasItemConfirmation == true)
        }.sortedByDescending { it.seenAt }
        val funnel = OfferFunnel(
            seen = rows.size,
            taken = rows.count { it.outcome == OfferOutcome.ACCEPTED },
            declined = rows.count { it.outcome in setOf(OfferOutcome.DECLINED, OfferOutcome.EXPIRED, OfferOutcome.NOT_TAKEN) },
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

    private fun OfferEntity.toRow(): OfferRowModel = OfferRowModel(
        offerId = id,
        seenAt = seenAt,
        platform = platform,
        fareMxn = fareMxn.takeUnless { inputProvenance == "LEGACY_UNSPECIFIED" || paymentBasis != "DRIVER_PAYMENT" },
        evaluation = evaluation,
        outcome = runCatching { OfferOutcome.valueOf(outcome) }.getOrDefault(OfferOutcome.PENDING),
        verdict = verdict?.let { name -> runCatching { VerdictLevel.valueOf(name) }.getOrNull() },
    )
}
