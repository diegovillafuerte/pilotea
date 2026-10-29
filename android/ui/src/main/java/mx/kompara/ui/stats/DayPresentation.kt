package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import mx.kompara.data.db.entity.*
import mx.kompara.data.settings.Settings
import mx.kompara.metrics.DayMoneySnapshot
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.components.CalculationRow
import mx.kompara.ui.components.OfferMarkState
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-MX")
internal fun LocalDate.dated(today: LocalDate): String = format(DateTimeFormatter.ofPattern(
    if (year == today.year) "EEEE d MMM" else "EEEE d MMM yyyy", spanish))
internal fun PeriodMoney.isToday() = window.start == window.asOf.atZone(window.zone).toLocalDate()
internal fun PeriodMoney.dayLabel() = if (isToday()) "Llevas hoy · aprox."
    else "Te quedaron el ${window.start.dated(window.asOf.atZone(window.zone).toLocalDate())} · aprox."
internal fun PeriodMoney.tripCount() = if (trips == 1) "1 viaje tomado" else "$trips viajes tomados"
internal fun PeriodMoney.energy() = if (contributions.any { it.runningCostVersion?.inputs?.mode == RunningCostMode.ELECTRIC }) "energía" else "gasolina"
internal fun PeriodMoney.typical() = contributions.any { it.runningCostVersion?.confirmation == CostConfirmation.TYPICAL }
internal fun PeriodMoney.basis() = if (typical()) "con costos típicos" else if (carConfigured) "ya restamos ${energy()}, desgaste y coche" else "ya restamos ${energy()} y desgaste"

/** A projection of one transaction. Detail and ledger never independently reload money. */
data class DayPresentation(val snapshot: DayMoneySnapshot, val settings: Settings) {
    val money = snapshot.money
    val date = money.window.start
    val today = money.window.asOf.atZone(money.window.zone).toLocalDate()
    val rows = DayDetailBuilder.build(date.toString(), money.toPeriodStats(), snapshot.shifts, snapshot.trips, snapshot.offers, emptyList()).offers.rows
    val missedAbove = rows.filter { it.notTaken && it.verdict == VerdictLevel.GREEN }
    val title = if (date == today) "Hoy · ${date.format(DateTimeFormatter.ofPattern("d MMM", spanish))}"
        else date.dated(today).replaceFirstChar { it.titlecase(spanish) }
    val label = money.dayLabel()
    val amount = money.periodResult?.takeIf { money.trips > 0 }?.let(Formatters::formatMxnWhole)
    val quietLine = money.tripCount() +
        (if (money.hoursOnline.signum() > 0) " en ${Formatters.formatDuration(money.hoursOnline.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toLong())}" else "") +
        (if (money.inProgress > 0) " " + Formatters.unbroken("(${money.inProgress} en curso)") else "") + ",\n${money.basis()}"
    val emptyMessage = when {
        rows.isEmpty() -> if (date == today) "Aún no registramos ofertas hoy" else "No registramos ofertas este día"
        money.trips == 0 -> if (date == today) "No tomaste ofertas hoy" else "No tomaste ofertas este día"
        money.periodResult == null -> if (date == today) "Aún no podemos calcular cuánto llevas" else "Aún no podemos calcular cuánto te quedó"
        else -> null
    }
    val section = if (date == today) "Ofertas de hoy · ${rows.size}" else "Ofertas del día · ${rows.size}"
    // A driver can change units during a day. A mixed column must not label hourly values as km.
    val column = when (rows.mapNotNull { it.evaluation?.unit }.distinct().singleOrNull()) {
        "IPK" -> "te deja por km, sin el coche"
        "IPH" -> "te deja por hora, sin el coche"
        else -> "te deja, sin el coche"
    }
    fun clock(row: OfferRowModel) = Instant.ofEpochMilli(row.seenAt).atZone(money.window.zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    fun suppressed(row: OfferRowModel): OfferPresentationEntity? {
        val session = snapshot.offers.firstOrNull { it.id == row.offerId }?.sessionId ?: return null
        return snapshot.presentations.firstOrNull { it.sessionId == session && it.outcome == "SUPPRESSED" }
    }
    fun meaning(row: OfferRowModel) = if (suppressed(row) != null) "sin semáforo en pantalla" else row.meaning
    fun offer(id: Long): OfferDetailModel? {
        val row = rows.firstOrNull { it.offerId == id } ?: return null
        val raw = snapshot.offers.first { it.id == id }
        val evaluation = row.evaluation
        val cost = snapshot.runningCosts.firstOrNull { it.id == evaluation?.costVersionId }
        val historical = row.historicalLimitSuffix(settings.effectiveThreshold)
        val why = if (row.rate != null && historical != null && evaluation != null) {
            val goal = row.verdict == VerdictLevel.GREEN
            "${row.rate} · ${row.meaning.replaceFirstChar { it.lowercase(spanish) }} de " +
                Formatters.formatLimit((if (goal) evaluation.goal else evaluation.minimum).toBigDecimal(), evaluation.unit == "IPH") + historical
        } else if (evaluation == null && row.verdict != null && row.verdict != VerdictLevel.UNKNOWN) "No conservamos tus límites de ese día" else row.meaning
        val contribution = evaluation?.contribution?.toBigDecimalOrNull()?.takeIf { row.contributionLabel != null }
        // Never recompute a historical offer using today's cost. Even cancelled/unknown records keep the raw read.
        val expense = raw.distanceKm?.toBigDecimal()?.let { km -> cost?.inputs?.costPerKm?.multiply(km) }
        val arithmetic = if (raw.distanceKm != null && cost?.inputs?.costPerKm != null && raw.inputProvenance != "LEGACY_UNSPECIFIED") {
            Formatters.formatKm(raw.distanceKm!!) + (raw.pickupKm?.let { " (${String.format(Locale.US, "%.1f", it)} para recoger)" } ?: "") +
                " × ${Formatters.formatMxn(cost.inputs.costPerKm!!)}"
        } else "No conservamos todos los datos del cálculo"
        val carBasis = if (money.carConfigured) ", con el coche" else ""
        val dayText = if (money.isToday()) "Llevas hoy$carBasis" else "Ese día, ${date.dated(today)}, te quedaron$carBasis"
        val suppressed = suppressed(row)?.let {
            "Sin semáforo: no cupo en la pantalla" + when (it.cause) {
                "NAVIGATION", "NO_ROOM_NAVIGATION" -> " (navegación)"
                "TALL_OFFER", "NO_ROOM_TALL_OFFER" -> " (oferta alta)"
                else -> ""
            }
        }
        val trip = snapshot.trips.firstOrNull { it.offerId == id }
        return OfferDetailModel(id, row.mark, "Oferta de las ${clock(row)} · ${if (row.inProgress) "en curso" else row.outcomeLabel}",
            row.contributionLabel, contribution?.let(Formatters::formatMxnWhole), why,
            raw.durationMin?.takeIf { it > 0 && raw.inputProvenance != "LEGACY_UNSPECIFIED" }?.let { minutes ->
                listOfNotNull(evaluation?.perHour?.toBigDecimalOrNull()?.let(Formatters::formatPerHourWhole),
                    "${BigDecimal.valueOf(minutes).stripTrailingZeros().toPlainString()} min").joinToString(" · ")
            }, suppressed,
            CalculationRow("Tarifa de la oferta", raw.fareMxn?.takeUnless { raw.inputProvenance == "LEGACY_UNSPECIFIED" }?.let(Formatters::formatMxnWhole) ?: "—",
                if (row.notTaken) "lo que ofrecía la app" else if (raw.paymentBasis != "DRIVER_PAYMENT") "No confirmamos que sea tu pago" else "el pago final puede cambiar"),
            CalculationRow(if (cost?.inputs?.mode == RunningCostMode.ELECTRIC) "Energía y desgaste" else "Gasolina y desgaste",
                expense?.negate()?.let(Formatters::formatMxnWhole) ?: "—", arithmetic),
            "$dayText: ${amount ?: "—"}",
            if (money.carConfigured) "Se resta por día (${Formatters.formatMxnWhole(money.carCost)}), no por viaje" else "Ya restamos ${money.energy()} y desgaste",
            trip?.id?.takeIf { row.outcome == OfferOutcome.ACCEPTED && trip.lifecycle in ELIGIBLE_TRIP_LIFECYCLES },
            cost?.confirmation == CostConfirmation.TYPICAL)
    }
}

internal val ELIGIBLE_TRIP_LIFECYCLES = setOf("ACCEPTED_ESTIMATE", "IN_PROGRESS", "FINISHED")
internal val OfferRowModel.notTaken get() = outcome in setOf(OfferOutcome.NOT_TAKEN, OfferOutcome.DECLINED, OfferOutcome.EXPIRED)
internal val OfferRowModel.mark get() = when (verdict) {
    VerdictLevel.GREEN -> OfferMarkState.ABOVE
    VerdictLevel.YELLOW -> OfferMarkState.MEETS
    VerdictLevel.RED -> OfferMarkState.BELOW
    else -> OfferMarkState.UNKNOWN
}
internal val OfferRowModel.meaning get() = when {
    evaluation?.unavailableReason == "FARE" -> "No pudimos leer la tarifa"
    mark == OfferMarkState.ABOVE -> "Arriba de tu meta"
    mark == OfferMarkState.MEETS -> "Cumple tu mínimo"
    mark == OfferMarkState.BELOW -> "Abajo de tu mínimo"
    evaluation?.unavailableReason == "TIME" -> "Falta tiempo"
    evaluation?.unavailableReason == "DISTANCE" -> "Falta distancia"
    else -> "Sin confirmar"
}
internal val OfferRowModel.rate: String? get() = evaluation?.let { e ->
    (if (e.unit == "IPH") e.perHour else e.perKm)?.toBigDecimalOrNull()?.takeIf { mark != OfferMarkState.UNKNOWN }?.let {
        if (e.unit == "IPH") Formatters.formatPerHourWhole(it) else Formatters.formatPerKm(it)
    }
}

data class OfferDetailModel(
    val offerId: Long, val mark: OfferMarkState, val title: String, val label: String?, val amount: String?,
    val why: String, val timeRate: String?, val suppression: String?, val fare: CalculationRow, val running: CalculationRow,
    val dayLine: String, val carLine: String, val cancellableTripId: Long?, val typical: Boolean,
)

data class DayCalculation(val title: String, val rows: List<CalculationRow>, val notes: List<String>)
fun dayCalculation(money: PeriodMoney): DayCalculation {
    val before = if (money.carConfigured) "antes de ${money.energy()}, desgaste y coche" else "antes de ${money.energy()} y desgaste"
    val km = money.contributions.map { it.record.km }.let { values -> if (values.any { it == null }) null else values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add) }
    val rows = buildList {
        add(CalculationRow(money.tripCount(), money.payment?.let(Formatters::formatMxnWhole) ?: "—", (if (money.inProgress > 0) "${money.inProgress} en curso · " else "") + before))
        add(CalculationRow(money.energy().replaceFirstChar { it.titlecase() } + " y desgaste", money.runningExpense?.negate()?.let(Formatters::formatMxnWhole) ?: "—", km?.let { Formatters.formatKm(it.toDouble()) } ?: "Faltan kilómetros"))
        if (money.carConfigured) add(CalculationRow("Lo del coche", Formatters.formatMxnWhole(money.carCost.negate()), "${Formatters.formatMxnWhole(money.carCost.multiply(BigDecimal(7)))} ÷ 7 días"))
        add(CalculationRow(money.dayLabel(), money.periodResult?.let(Formatters::formatMxnWhole) ?: "—", total = true))
    }
    val roundedParts = if (money.payment != null && money.runningExpense != null) money.payment!!.setScale(0, RoundingMode.HALF_UP)
        .subtract(money.runningExpense!!.setScale(0, RoundingMode.HALF_UP)).subtract(money.carCost.setScale(0, RoundingMode.HALF_UP)) else null
    return DayCalculation("Cómo se calculó · ${if (money.isToday()) "hoy" else Formatters.formatDayLabel(money.window.start.toString())}", rows, buildList {
        if (roundedParts != null && money.periodResult != null && roundedParts.compareTo(money.periodResult!!.setScale(0, RoundingMode.HALF_UP)) != 0)
            add("El total se calcula antes de redondear cada cifra.")
        if (money.typical()) add("Con costos típicos")
        if (money.contributions.any { it.runningCostVersion?.correctsVersionId != null })
            add("Aplicaste el rendimiento corregido a estos viajes. Conservamos el precio de cada fecha.")
        if (money.unknownHistoricalCost) add("No conservamos tus costos de ese día")
        add("El pago final lo confirma tu resumen de la app. Antes de impuestos.")
    })
}
