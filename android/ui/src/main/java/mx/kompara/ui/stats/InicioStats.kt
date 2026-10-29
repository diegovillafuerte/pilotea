package mx.kompara.ui.stats

import mx.kompara.data.accounting.CostConfirmation
import mx.kompara.data.accounting.RunningCostVersion
import mx.kompara.data.service.*
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.TodayMoneySnapshot
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class HoyMode { MONEY, FIRST_OFF, RECOVERY, FIRST_PREPARED, PREPARED, COSTS, EMPTY }
data class HoyOffer(val id: Long, val outcome: String, val detail: String, val rate: String?, val mark: OfferMarkState)
data class InicioUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val actionError: String? = null,
    val snapshot: TodayMoneySnapshot? = null,
    val reader: ReaderReadiness? = null,
    val mode: HoyMode = HoyMode.EMPTY,
    val statusDetail: String = "",
    val label: String = "Llevas hoy · aprox.",
    val amount: String? = null,
    val quietLine: String = "",
    val yesterday: String? = null,
    val offers: List<HoyOffer> = emptyList(),
    val partialDidi: Boolean = false,
    val typicalCost: RunningCostVersion? = null,
    val limits: ReglaModel? = null,
    val example: ReglaModel? = null,
) {
    val dayIso: String? get() = snapshot?.today?.money?.window?.start?.toString()
}

/** Projection only. All money, dates and lifecycle evidence belong to the shared snapshot. */
object InicioStats {
    fun today(snapshot: TodayMoneySnapshot, reader: ReaderReadiness, settings: Settings): InicioUiState {
        if (reader.permission == null) return InicioUiState()
        val day = snapshot.today
        val money = day.money
        val date = money.window.start
        val zone = money.window.zone
        val hasHistory = snapshot.hasHistory || reader.apps.values.any { it.lastReadAt != null }
        val hasTrips = money.trips > 0
        val typical = day.activeCost?.takeIf { it.confirmation == CostConfirmation.TYPICAL }
        val threshold = settings.effectiveThreshold
        val hour = settings.preferredMetric == PreferredMetric.IPH
        val limits = ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.READING),
            (if (hour) threshold.minPerHourMxn else threshold.minPerKmMxn).toBigDecimal(),
            (if (hour) threshold.redPerHourMxn else threshold.redPerKmMxn).toBigDecimal(),
            if (hour) ReglaUnit.HOUR else ReglaUnit.KM)
        // Explicitly labelled teaching offer. Reuse the engine so edited costs/limits/unit remain true.
        val sample = mx.kompara.metrics.NetProfitEngine().evaluate(
            mx.kompara.metrics.TripOffer("EXAMPLE", 148.0, 2.0, 4.0, 10.0, 20.0),
            day.activeCost?.inputs?.costPerKm, threshold, settings.preferredMetric)
        val sampleRate = (if (hour) sample.perHourDecimal else sample.perKmDecimal)?.toBigDecimalOrNull()
        val samplePosition = when (sample.verdict.level) {
            VerdictLevel.GREEN -> ReglaPosition.ABOVE
            VerdictLevel.YELLOW -> ReglaPosition.MEETS
            VerdictLevel.RED -> ReglaPosition.BELOW
            else -> null
        }
        val example = limits.copy(band = if (sampleRate != null && samplePosition != null) ReglaBand.Rated(samplePosition, sampleRate)
            else ReglaBand.Unavailable(ReglaUnavailable.UNCONFIRMED))
        fun clock(at: Long) = Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
        fun relative(at: Long): String {
            val atDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            return when (atDate) {
                date -> clock(at)
                date.minusDays(1) -> "ayer ${clock(at)}"
                else -> "${Formatters.formatDayLabel(atDate.toString())} ${clock(at)}"
            }
        }
        val lastRead = reader.apps.values.mapNotNull { it.lastReadAt }.maxOrNull()
        val detail = when (reader.phase) {
            ReaderPhase.OFF -> reader.incident?.let { "· desde ${relative(it.firstSeenAt)}" } ?: "falta el permiso"
            ReaderPhase.PREPARED -> if (lastRead != null && reader.incident == null &&
                Instant.ofEpochMilli(lastRead).atZone(zone).toLocalDate() == date.minusDays(1) && typical != null)
                "última lectura ${relative(lastRead)}" else "permiso listo"
            else -> if (reader.noRoomCount > 0) "no cupo el semáforo en ${reader.noRoomCount} ${if (reader.noRoomCount == 1) "oferta" else "ofertas"} hoy"
                else "${limits.goalLabel} · ${limits.minimumLabel}"
        }
        val mode = when {
            reader.phase == ReaderPhase.OFF -> if (hasHistory) HoyMode.RECOVERY else HoyMode.FIRST_OFF
            hasTrips -> HoyMode.MONEY
            reader.phase == ReaderPhase.PREPARED && !hasHistory -> HoyMode.FIRST_PREPARED
            reader.phase == ReaderPhase.PREPARED && typical != null -> HoyMode.COSTS
            reader.phase == ReaderPhase.PREPARED -> HoyMode.PREPARED
            else -> HoyMode.EMPTY
        }
        val unsupportedDidi = reader.apps[ReaderReadinessReducer.DIDI]?.supported == false &&
            reader.apps[ReaderReadinessReducer.UBER]?.supported == true
        val partial = unsupportedDidi && money.byApp.keys.none { it.equals("DIDI", true) }
        val count = if (money.trips == 1) "1 viaje tomado" else "${money.trips} viajes tomados"
        val underway = if (money.inProgress > 0) {
            if (money.trips == 1) " (en curso)" else " (${money.inProgress} en curso)"
        } else if (money.hoursOnline.signum() > 0) " en ${Formatters.formatDuration(money.hoursOnline.multiply(60.toBigDecimal()).setScale(0, java.math.RoundingMode.HALF_UP).toLong())}" else ""
        val energy = if (money.contributions.any { it.runningCostVersion?.inputs?.mode == mx.kompara.data.accounting.RunningCostMode.ELECTRIC }) "energía" else "gasolina"
        val typicalBasis = money.contributions.any { it.runningCostVersion?.confirmation == CostConfirmation.TYPICAL }
        val basis = if (typicalBasis) "con costos típicos" else if (money.carConfigured) "ya restamos $energy, desgaste y coche" else "ya restamos $energy y desgaste"
        val negative = money.periodResult?.signum() == -1
        val quiet = "$count$underway,\n$basis" +
            (if (negative && money.carConfigured) "\n(${Formatters.formatMxnWhole(money.carCost)} por día)" else "")
        val yesterday = snapshot.yesterday.money.takeIf { it.trips > 0 }?.let { previous ->
            previous.periodResult?.let { value ->
                "${Formatters.formatDatedRelativeLabel(previous.window.start, date)}, te quedaron ${Formatters.formatMxnWhole(value)} aprox."
            }
        }
        val rows = DayDetailBuilder.build(date.toString(), PeriodStats.EMPTY, day.shifts, day.trips, day.offers, emptyList()).offers.rows
        val offers = rows.take(if (reader.paused) 2 else 3).map { row ->
            val evaluation = row.evaluation
            val suppressed = day.presentations.any { it.sessionId == day.offers.first { offer -> offer.id == row.offerId }.sessionId && it.outcome == "SUPPRESSED" }
            val mark = when (row.verdict) {
                VerdictLevel.GREEN -> OfferMarkState.ABOVE
                VerdictLevel.YELLOW -> OfferMarkState.MEETS
                VerdictLevel.RED -> OfferMarkState.BELOW
                else -> OfferMarkState.UNKNOWN
            }
            val meaning = when {
                suppressed -> "sin semáforo en pantalla"
                evaluation?.unavailableReason == "FARE" -> "No pudimos leer la tarifa"
                mark == OfferMarkState.ABOVE -> "Arriba de tu meta"
                mark == OfferMarkState.MEETS -> "Cumple tu mínimo"
                mark == OfferMarkState.BELOW -> "Abajo de tu mínimo"
                evaluation?.unavailableReason == "TIME" -> "Falta tiempo"
                evaluation?.unavailableReason == "DISTANCE" -> "Falta distancia"
                else -> "Sin confirmar"
            }
            val rate = (if (evaluation?.unit == PreferredMetric.IPH.name) evaluation.perHour else evaluation?.perKm)?.toBigDecimalOrNull()?.takeIf { mark != OfferMarkState.UNKNOWN }?.let {
                if (evaluation?.unit == PreferredMetric.IPH.name) Formatters.formatPerHourWhole(it) else Formatters.formatPerKm(it)
            }
            HoyOffer(row.offerId, row.outcomeLabel, "${clock(row.seenAt)} · $meaning", rate, mark)
        }
        return InicioUiState(false, snapshot = snapshot, reader = reader, mode = mode, statusDetail = detail,
            label = if (partial) "Llevas hoy sin contar DiDi · aprox." else "Llevas hoy · aprox.",
            amount = money.periodResult?.takeIf { hasTrips }?.let(Formatters::formatMxnWhole),
            quietLine = quiet, yesterday = yesterday, offers = offers, partialDidi = unsupportedDidi, typicalCost = typical, limits = limits, example = example)
    }
}
