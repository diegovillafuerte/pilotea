package mx.kompara.ui.stats

import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.data.db.entity.*
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.data.settings.Settings
import mx.kompara.metrics.DayMoneySnapshot
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.components.CalculationRow
import mx.kompara.ui.components.OfferMarkState
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.format.spanishDate
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-MX")

internal fun basisWord(basis: RateBasis) = if (basis == RateBasis.GROSS) "bruto" else "neto"
/** The words every list of rates carries: "por km, neto" (plan 4.2). */
internal fun unitWords(metric: PreferredMetric, basis: RateBasis) = "${if (metric == PreferredMetric.IPH) "por hora" else "por km"}, ${basisWord(basis)}"
internal fun formatRate(value: BigDecimal, metric: PreferredMetric) =
    if (metric == PreferredMetric.IPH) Formatters.formatPerHourWhole(value) else Formatters.formatPerKm(value)
/** "Lunes 19 oct", the title of a past day (L6). */
internal fun LocalDate.dayTitle(today: LocalDate): String = format(spanishDate(
    if (year == today.year) "EEEE d MMM" else "EEEE d MMM yyyy")).replaceFirstChar { it.titlecase(spanish) }

/**
 * One day as Ganancias shows it (v4 L1, L6): what the day's taken trips kept, its offers newest first,
 * and the offer sheet and calculation. A projection of one snapshot; nothing reloads money.
 */
data class DayPresentation(val snapshot: DayMoneySnapshot, val settings: Settings) {
    val money = snapshot.money
    val date: LocalDate = money.window.start
    val today: LocalDate = money.window.asOf.atZone(money.window.zone).toLocalDate()
    val isToday = date == today
    val rows = DayDetailBuilder.build(date.toString(), PeriodStats.EMPTY, snapshot.shifts, snapshot.trips, snapshot.offers, emptyList()).offers.rows
    val title = date.dayTitle(today)
    val label = if (isToday) "Hoy llevas" else "Ganaste"
    val amount: String? = money.tripContribution?.let(Formatters::formatMxnWhole)
    /** Only a day with taken trips opens Cómo se calculó. */
    val calculable = money.trips > 0
    val quietLine: String = when {
        money.trips == 0 -> if (isToday) "Aún no hay viajes hoy" else if (rows.isEmpty()) "No registramos ofertas este día" else "No tomaste ofertas este día"
        amount == null -> "${tripsLabel(money.trips)}\nFaltan datos para calcular lo que ${if (isToday) "llevas" else "ganaste"}"
        else -> tripsLabel(money.trips) + (money.hoursOnline.takeIf { it.signum() > 0 }?.let {
            " · " + Formatters.formatDuration(it.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toLong())
        } ?: "") + "\nYa restamos ${if (money.electric()) "energía" else "gasolina"} y desgaste"
    }
    val section = if (isToday) "Ofertas de hoy" else "Ofertas del día"
    val column = unitWords(settings.preferredMetric, settings.rateBasis)
    /** "Cómo se calculó · hoy", or the day's own title in lower case: "· lunes 19 oct". */
    val calculation get() = periodCalculation(money, if (isToday) "hoy" else title.replaceFirstChar { it.lowercase(spanish) }, label)

    fun clock(row: OfferRowModel) = Formatters.formatClock(row.seenAt, money.window.zone)
    fun detail(row: OfferRowModel) = "${clock(row)} · ${row.outcomeLabel}"
    /** Offers that did not become a counted trip are dimmed. */
    fun dim(row: OfferRowModel) = !row.counts
    private fun raw(row: OfferRowModel) = snapshot.offers.first { it.id == row.offerId }
    /**
     * A row is the record of what the semáforo said: its rate in the unit and basis it was judged in,
     * beside its frozen mark. The unit is in the suffix ("/h"); a basis other than the current one
     * follows the rate ("$12.33/km bruto"). None for an unread offer.
     */
    fun rate(row: OfferRowModel): String? = row.rate(raw(row), row.judgedMetric, row.judgedBasis)?.let {
        formatRate(it, row.judgedMetric) + if (row.judgedBasis != settings.rateBasis) " ${basisWord(row.judgedBasis)}" else ""
    }
    private fun suppressed(row: OfferRowModel): OfferPresentationEntity? {
        val session = raw(row).sessionId ?: return null
        return snapshot.presentations.firstOrNull { it.sessionId == session && it.outcome == "SUPPRESSED" }
    }

    /** The offer sheet (L7): its rates and limits in the basis and unit it was judged in. */
    fun offer(id: Long): OfferDetailModel? {
        val row = rows.firstOrNull { it.offerId == id } ?: return null
        val raw = raw(row)
        val evaluation = row.evaluation
        val metric = row.judgedMetric
        val basis = row.judgedBasis
        val legacy = raw.inputProvenance == "LEGACY_UNSPECIFIED"
        val cost = snapshot.runningCosts.firstOrNull { it.id == evaluation?.costVersionId }
        val rate = row.rate(raw, metric, basis)?.let {
            (if (metric == PreferredMetric.IPH) Formatters.formatMxnWhole(it) + " por hora" else Formatters.formatMxn(it) + " por km") + " " + basisWord(basis)
        }
        val suffix = row.historicalLimitSuffix(settings.effectiveThreshold, settings.preferredMetric, settings.rateBasis)
        val why = if (rate != null && suffix != null && evaluation != null) {
            fun limit(value: String) = value.toBigDecimal().let { if (metric == PreferredMetric.IPH && it.stripTrailingZeros().scale() <= 0) Formatters.formatMxnWhole(it) else Formatters.formatMxn(it) }
            val phrase = when (row.mark) {
                OfferMarkState.ABOVE -> "arriba de tu meta de ${limit(evaluation.goal)}"
                OfferMarkState.MEETS -> "entre tu mínimo de ${limit(evaluation.minimum)} y tu meta de ${limit(evaluation.goal)}"
                else -> "abajo de tu mínimo de ${limit(evaluation.minimum)}"
            }
            "$rate · $phrase$suffix"
        } else if (evaluation == null && row.verdict != null && row.verdict != VerdictLevel.UNKNOWN) "No conservamos tus límites de ese día" else row.meaning
        val km = raw.distanceKm?.takeUnless { legacy }
        val minutes = raw.durationMin?.takeIf { it > 0 && !legacy }
        val expense = km?.toBigDecimal()?.let { cost?.inputs?.costPerKm?.multiply(it) }
        val running = CalculationRow(if (cost?.inputs?.mode == RunningCostMode.ELECTRIC) "Energía y desgaste" else "Gasolina y desgaste",
            expense?.negate()?.let(Formatters::formatMxnWhole) ?: "—",
            if (km != null && cost?.inputs?.costPerKm != null) "${Formatters.formatKm(km)} × ${Formatters.formatMxn(cost.inputs.costPerKm!!)}"
            else "No conservamos todos los datos del cálculo")
        val otherMetric = if (metric == PreferredMetric.IPK) PreferredMetric.IPH else PreferredMetric.IPK
        val other = row.takeIf { it.mark != OfferMarkState.UNKNOWN }?.rate(raw, otherMetric, basis)?.let { value ->
            if (otherMetric == PreferredMetric.IPH) minutes?.let {
                CalculationRow("Por hora, ${basisWord(basis)}", Formatters.formatMxnWhole(value), "${BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()} min")
            } else km?.let { CalculationRow("Por km, ${basisWord(basis)}", Formatters.formatMxn(value), Formatters.formatKm(it)) }
        }
        val trip = snapshot.trips.firstOrNull { it.offerId == id }
        return OfferDetailModel(id, row.mark, "${clock(row)} · ${appName(raw.platform)} · ${row.outcomeLabel.replaceFirstChar { it.lowercase(spanish) }}",
            row.contributionLabel, evaluation?.contribution?.toBigDecimalOrNull()?.takeIf { row.contributionLabel != null }?.let(Formatters::formatMxnWhole),
            rate?.takeIf { why.startsWith(it) }, why,
            suppressed(row)?.let {
                "Sin semáforo: no cupo en la pantalla" + when (it.cause) {
                    "NAVIGATION", "NO_ROOM_NAVIGATION" -> " (navegación)"
                    "TALL_OFFER", "NO_ROOM_TALL_OFFER" -> " (oferta alta)"
                    else -> ""
                }
            },
            CalculationRow("Tarifa", raw.fareMxn?.takeUnless { legacy }?.let(Formatters::formatMxnWhole) ?: "—",
                if (raw.paymentBasis != "DRIVER_PAYMENT") "No confirmamos que sea tu pago" else null),
            running, other,
            trip?.id?.takeIf { row.outcome == OfferOutcome.ACCEPTED && trip.lifecycle in ELIGIBLE_TRIP_LIFECYCLES })
    }
}

internal val ELIGIBLE_TRIP_LIFECYCLES = setOf("ACCEPTED_ESTIMATE", "IN_PROGRESS", "FINISHED")
/** The trip counts in the day's money: under way, or taken and finished. */
internal val OfferRowModel.counts get() = inProgress || lifecycle == TripLifecycle.FINISHED && outcome == OfferOutcome.ACCEPTED
internal val OfferRowModel.mark get() = when (verdict) {
    VerdictLevel.GREEN -> OfferMarkState.ABOVE
    VerdictLevel.YELLOW -> OfferMarkState.MEETS
    VerdictLevel.RED -> OfferMarkState.BELOW
    else -> OfferMarkState.UNKNOWN
}
/** The unit and basis the row was judged in (old rows: per km, NET). */
internal val OfferRowModel.judgedMetric get() = if (evaluation?.unit == PreferredMetric.IPH.name) PreferredMetric.IPH else PreferredMetric.IPK
internal val OfferRowModel.judgedBasis get() = RateBasis.fromName(evaluation?.rateBasis)
internal val OfferRowModel.meaning get() = when {
    evaluation?.unavailableReason == "FARE" -> "No pudimos leer la tarifa"
    mark == OfferMarkState.ABOVE -> "Arriba de tu meta"
    mark == OfferMarkState.MEETS -> "Entre tu mínimo y tu meta"
    mark == OfferMarkState.BELOW -> "Abajo de tu mínimo"
    evaluation?.unavailableReason == "TIME" -> "Falta tiempo"
    evaluation?.unavailableReason == "DISTANCE" -> "Falta distancia"
    else -> "Sin confirmar"
}

/**
 * Any row's rate in either unit and basis: NET from its frozen judgement (fare − cost per km × km over
 * the same km or minutes), GROSS as its fare over the total km or hours. Null for an unrated offer.
 */
internal fun OfferRowModel.rate(raw: OfferEntity, metric: PreferredMetric, basis: RateBasis): BigDecimal? {
    val e = evaluation?.takeIf { mark != OfferMarkState.UNKNOWN } ?: return null
    return when (basis) {
        RateBasis.NET -> (if (metric == PreferredMetric.IPH) e.perHour else e.perKm)?.toBigDecimalOrNull()
        RateBasis.GROSS -> {
            val fare = fareMxn?.toBigDecimal() ?: return null
            if (metric == PreferredMetric.IPH) raw.durationMin?.takeIf { it > 0 }?.let { fare.multiply(BigDecimal(60)).divide(it.toBigDecimal(), MathContext.DECIMAL64) }
            else raw.distanceKm?.takeIf { it > 0 }?.let { fare.divide(it.toBigDecimal(), MathContext.DECIMAL64) }
        }
    }
}

/** The offer sheet (L7). [rate] is the bold start of [why] when the offer was rated. */
data class OfferDetailModel(
    val offerId: Long, val mark: OfferMarkState, val title: String, val label: String?, val amount: String?,
    val rate: String?, val why: String, val suppression: String?,
    val fare: CalculationRow, val running: CalculationRow, val other: CalculationRow?, val cancellableTripId: Long?,
)
