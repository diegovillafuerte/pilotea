package mx.kompara.ui.stats

import mx.kompara.data.accounting.CostConfirmation
import mx.kompara.data.service.ReaderPhase
import mx.kompara.data.service.ReaderReadiness
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.Settings
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.TodayMoneySnapshot
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.VerdictLevel
import mx.kompara.metrics.rate
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.service.statusLabel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Hoy's body: the day (L1), the first day (L2), or the semáforo off (L3). */
enum class HoyMode { LOADING, ERROR, OFF, FIRST_DAY, DAY }

/** The one status line at the top of the tab; it opens the Semáforo screen unless the tab already shows the cause. */
data class GananciasStatus(val title: String, val detail: String?, val glyph: StatusGlyph, val opens: Boolean)

/** L3: the cause and its primary button; [recovery] opens the permission flow, otherwise the Semáforo screen. */
data class ReaderOff(val headline: String, val body: String, val primary: String, val recovery: Boolean)

/** "Ayer, lunes 19 oct, ganaste $1,240." — a dated line that opens that day; [amount] is drawn strong. */
data class DayLine(val prefix: String, val amount: String, val suffix: String, val day: LocalDate) {
    val text get() = prefix + amount + suffix
}

/** L2: the two defaults, one tap from being changed, and the example chip. */
data class FirstDay(val limits: String, val limitsDetail: String, val cost: String?, val costDetail: String?, val example: ReglaModel)

data class GananciasUiState(
    val mode: HoyMode = HoyMode.LOADING,
    val status: GananciasStatus? = null,
    val off: ReaderOff? = null,
    /** Yesterday's line when it had trips: under an empty Hoy and under the cause in L3. */
    val yesterday: DayLine? = null,
    /** L3's line about today. */
    val today: DayLine? = null,
    val todayUnread: Boolean = false,
    val firstDay: FirstDay? = null,
)

/** Projection only: the reader's state, the shared day snapshot and the settings (plan 4.5). */
object GananciasStats {
    const val SECONDARY = "Que no se vuelva a apagar"

    fun of(snapshot: TodayMoneySnapshot, reader: ReaderReadiness, settings: Settings): GananciasUiState {
        if (reader.permission == null) return GananciasUiState()
        val money = snapshot.today.money
        val date = money.window.start
        val zone = money.window.zone
        val hasHistory = snapshot.hasHistory || reader.apps.values.any { it.lastReadAt != null }
        fun clock(at: Long) = Formatters.formatClock(at, zone)
        fun relative(at: Long) = when (val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()) {
            date -> clock(at)
            date.minusDays(1) -> "ayer ${clock(at)}"
            else -> "${Formatters.formatDayLabel(day.toString())} ${clock(at)}"
        }
        val status = when (reader.phase) {
            ReaderPhase.OFF -> GananciasStatus("Semáforo apagado", reader.incident?.let { "desde ${relative(it.firstSeenAt)}" }
                ?: "falta el permiso".takeIf { reader.permission == false }, StatusGlyph.OFF, opens = false)
            ReaderPhase.WORKING -> GananciasStatus("Semáforo funcionando", null, StatusGlyph.HEALTHY, true)
            ReaderPhase.ATTENTION -> GananciasStatus(reader.statusLabel(), null, StatusGlyph.ATTENTION, true)
            // A 0.2.x pause is cleared at startup and nothing sets one any more (plan §3).
            ReaderPhase.PREPARED, ReaderPhase.PAUSED -> GananciasStatus("Semáforo preparado", "permiso listo", StatusGlyph.WAITING, true)
        }
        val yesterday = snapshot.yesterday.money.takeIf { it.trips > 0 }?.tripContribution?.let { value ->
            DayLine("${Formatters.formatDatedRelativeLabel(date.minusDays(1), date)}, ganaste ", Formatters.formatMxnWhole(value), ".", date.minusDays(1))
        }
        if (reader.phase == ReaderPhase.OFF) {
            val off = when {
                reader.permission == false && !hasHistory -> ReaderOff("Activa el semáforo",
                    "Sin el permiso de Accesibilidad, Kompara no ve tus ofertas.", "Activar el semáforo", true)
                reader.permission == false -> ReaderOff("Falta el permiso de Accesibilidad",
                    "Android a veces lo apaga, por ejemplo al ahorrar batería. Sin él, Kompara no ve tus ofertas.", "Volver a dar el permiso", true)
                else -> ReaderOff("Dejamos de leer tus ofertas",
                    "Revisa el estado del semáforo para volver a leer tus ofertas.", "Revisar el semáforo", false)
            }
            val unread = snapshot.today.offers.isEmpty()
            return GananciasUiState(HoyMode.OFF, status, off, yesterday,
                money.tripContribution?.takeUnless { unread }?.let { DayLine("Hoy llevas ", Formatters.formatMxnWhole(it), ".", date) }, unread)
        }
        if (!hasHistory) return GananciasUiState(HoyMode.FIRST_DAY, status, yesterday = yesterday, firstDay = firstDay(snapshot, settings))
        return GananciasUiState(HoyMode.DAY, status, yesterday = yesterday)
    }

    private fun firstDay(snapshot: TodayMoneySnapshot, settings: Settings): FirstDay {
        val hour = settings.preferredMetric == PreferredMetric.IPH
        val threshold = settings.effectiveThreshold
        val goal = (if (hour) threshold.minPerHourMxn else threshold.minPerKmMxn).toBigDecimal()
        val minimum = (if (hour) threshold.redPerHourMxn else threshold.redPerKmMxn).toBigDecimal()
        fun limit(value: BigDecimal) = if (hour && value.stripTrailingZeros().scale() <= 0) Formatters.formatMxnWhole(value) else Formatters.formatMxn(value)
        val cost = snapshot.today.activeCost
        // The labelled teaching offer ($148, 12 km, 24 min), judged by the engine so edited costs, limits, unit and basis stay true.
        val sample = NetProfitEngine().evaluate(TripOffer("EXAMPLE", 148.0, 2.0, 4.0, 10.0, 20.0),
            cost?.inputs?.costPerKm, threshold, settings.preferredMetric, settings.rateBasis)
        val position = when (sample.verdict.level) {
            VerdictLevel.GREEN -> ReglaPosition.ABOVE
            VerdictLevel.YELLOW -> ReglaPosition.MEETS
            VerdictLevel.RED -> ReglaPosition.BELOW
            else -> null
        }
        val rate = sample.rate(settings.preferredMetric, settings.rateBasis)
        val unit = if (hour) ReglaUnit.HOUR else ReglaUnit.KM
        val example = if (rate != null && position != null) ReglaModel.rated(position, rate, unit, settings.rateBasis)
            else ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.UNCONFIRMED), unit, settings.rateBasis)
        val words = unitWords(settings.preferredMetric, settings.rateBasis).replaceFirstChar { it.uppercase() }
        return FirstDay("Tu meta ${limit(goal)} · tu mínimo ${limit(minimum)}",
            words + if (settings.threshold == null) ". Valores iniciales" else "",
            cost?.inputs?.costPerKm?.let { "Manejar te cuesta ${Formatters.formatMxn(it)} por km" },
            "Costo típico de un sedán".takeIf { cost?.confirmation == CostConfirmation.TYPICAL }, example)
    }
}
