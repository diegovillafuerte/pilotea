package mx.kompara.ui.stats

import mx.kompara.data.settings.LimitConversion
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.VerdictLevel
import mx.kompara.metrics.rate
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode

enum class LimitField { GOAL, MINIMUM }
data class LimitPair(val goal: String, val minimum: String)

/**
 * Raw text stays in the draft; neither typing, stepping, reset, unit nor basis selection writes settings.
 * The limits are kept in [basis] (v4 plan 4.2).
 */
data class LimitDraft(val km: LimitPair, val hour: LimitPair, val metric: PreferredMetric, val basis: RateBasis = RateBasis.NET) {
    val pair get() = if (metric == PreferredMetric.IPK) km else hour
    val unit get() = if (metric == PreferredMetric.IPK) ReglaUnit.KM else ReglaUnit.HOUR
    fun withPair(pair: LimitPair) = if (metric == PreferredMetric.IPK) copy(km = pair) else copy(hour = pair)
    fun edit(field: LimitField, text: String) = withPair(if (field == LimitField.GOAL) pair.copy(goal = text) else pair.copy(minimum = text))
    fun encode() = arrayListOf(km.goal, km.minimum, hour.goal, hour.minimum, metric.name, basis.name)
    /** Keep untouched legacy precision (including the other unit) instead of saving display rounding. */
    fun thresholdPreserving(saved: LimitDraft, original: PlatformThreshold): PlatformThreshold {
        fun value(text: String, previous: String, raw: Double) =
            if (ThresholdEditor.parse(text)?.compareTo(ThresholdEditor.parse(previous)) == 0) raw else ThresholdEditor.parse(text)!!.toDouble()
        return PlatformThreshold(value(km.goal, saved.km.goal, original.minPerKmMxn),
            value(hour.goal, saved.hour.goal, original.minPerHourMxn),
            value(km.minimum, saved.km.minimum, original.redPerKmMxn),
            value(hour.minimum, saved.hour.minimum, original.redPerHourMxn))
    }
    fun threshold(): PlatformThreshold = PlatformThreshold(km.goal.amount()!!.toDouble(), hour.goal.amount()!!.toDouble(),
        km.minimum.amount()!!.toDouble(), hour.minimum.amount()!!.toDouble())
    companion object {
        fun from(value: PlatformThreshold, metric: PreferredMetric, basis: RateBasis = RateBasis.NET) = LimitDraft(
            LimitPair(value.minPerKmMxn.toBigDecimal().input(), value.redPerKmMxn.toBigDecimal().input()),
            LimitPair(value.minPerHourMxn.toBigDecimal().input(), value.redPerHourMxn.toBigDecimal().input()), metric, basis)
        fun decode(values: List<String>) = LimitDraft(LimitPair(values[0], values[1]), LimitPair(values[2], values[3]),
            PreferredMetric.valueOf(values[4]), RateBasis.fromName(values.getOrNull(5)))
    }
}

private fun String.amount() = ThresholdEditor.parse(this)
private fun BigDecimal.input() = setScale(2, RoundingMode.HALF_UP).toPlainString()

object ThresholdEditor {
    val MAX_PER_KM = BigDecimal("99.99")
    /** The per-hour editor's step; a converted per-hour limit is rounded to it too. */
    val HOUR_STEP: BigDecimal = BigDecimal.TEN
    fun parse(text: String): BigDecimal? {
        val clean = text.trim().removePrefix("$").trim().replace(',', '.')
        if (!Regex("[0-9]+(?:\\.[0-9]{0,2})?").matches(clean)) return null
        return clean.toBigDecimalOrNull()?.takeIf { it.toDouble().isFinite() }
    }
    fun step(text: String, metric: PreferredMetric, increase: Boolean): String {
        val value = parse(text) ?: BigDecimal.ZERO
        val step = if (metric == PreferredMetric.IPK) LimitConversion.KM_STEP else HOUR_STEP
        val grid = value.divide(step, 0, if (increase) RoundingMode.FLOOR else RoundingMode.CEILING)
        val result = grid.add(if (increase) BigDecimal.ONE else BigDecimal.ONE.negate()).multiply(step).max(BigDecimal.ZERO)
        return (if (metric == PreferredMetric.IPK) result.min(MAX_PER_KM) else result).input()
    }
    fun valid(pair: LimitPair, metric: PreferredMetric): Boolean {
        val goal = parse(pair.goal) ?: return false
        val minimum = parse(pair.minimum) ?: return false
        return goal > minimum && (metric == PreferredMetric.IPH || goal <= MAX_PER_KM)
    }
    fun error(draft: LimitDraft, field: LimitField): String? {
        val pair = draft.pair
        val goal = parse(pair.goal)
        val minimum = parse(pair.minimum)
        if (goal == null || minimum == null) return "Revisa este número"
        if (draft.metric == PreferredMetric.IPK && (goal > MAX_PER_KM || minimum > MAX_PER_KM)) return "Escribe hasta $99.99"
        if (minimum >= goal) return if (field == LimitField.MINIMUM) "Tu mínimo debe ser menor que tu meta" else "Tu meta debe ser mayor que tu mínimo"
        return null
    }
    fun same(a: LimitPair, b: LimitPair) = sameAmount(a.goal, b.goal) && sameAmount(a.minimum, b.minimum)
    private fun sameAmount(a: String, b: String) = if (parse(a) != null && parse(b) != null) parse(a)!!.compareTo(parse(b)) == 0 else a == b
    fun dirty(draft: LimitDraft, saved: LimitDraft) = draft.metric != saved.metric || draft.basis != saved.basis ||
        !same(draft.km, saved.km) || !same(draft.hour, saved.hour)
    /** A limit without its unit: per km with cents; per hour whole unless the driver typed cents. */
    fun amount(text: String, unit: ReglaUnit): String = parse(text)?.let {
        // Preserve typed hourly cents too: whole hourly limits are compact, never silently rounded.
        if (unit == ReglaUnit.KM || it.stripTrailingZeros().scale() > 0) Formatters.formatMxn(it) else Formatters.formatMxnWhole(it)
    } ?: text
    fun limit(text: String, unit: ReglaUnit): String = parse(text)?.let { amount(text, unit) + unit.suffix } ?: text
    fun basisWord(basis: RateBasis) = if (basis == RateBasis.GROSS) "bruto" else "neto"
    /** J1's value: "$8.00 · $6.00 por km, neto". */
    fun summary(draft: LimitDraft) = "${amount(draft.pair.goal, draft.unit)} · ${amount(draft.pair.minimum, draft.unit)} " +
        "${if (draft.metric == PreferredMetric.IPK) "por km" else "por hora"}, ${basisWord(draft.basis)}"
    /** The same limits in [to]: WP-A's conversion, from the draft's last valid values. */
    fun convert(draft: LimitDraft, to: RateBasis, costPerKm: BigDecimal): LimitDraft =
        LimitDraft.from(LimitConversion.convert(draft.threshold(), draft.basis, to, costPerKm, HOUR_STEP), draft.metric, to)
    /** J2/J7's helper under the two selectors. */
    fun basisHelper(basis: RateBasis, metric: PreferredMetric, costPerKm: BigDecimal?): String = when {
        basis == RateBasis.NET -> "Neto: ya restamos tu costo" + (costPerKm?.let { " de ${Formatters.formatMxn(it)}" } ?: "") + " por km."
        metric == PreferredMetric.IPK -> "Bruto: la tarifa entre los km, sin restar tu costo."
        else -> "Bruto: la tarifa por hora, sin restar tu costo."
    }
    /** J7's line under the steppers: where staged, converted limits came from. */
    fun explanation(from: LimitDraft, costPerKm: BigDecimal): String {
        val pair = "${amount(from.pair.goal, from.unit)} y ${amount(from.pair.minimum, from.unit)} ${basisWord(from.basis)}"
        val cost = Formatters.formatMxn(costPerKm)
        return when {
            from.metric == PreferredMetric.IPH -> "Son tus $pair, convertidos con tu costo de $cost por km y redondeados."
            from.basis == RateBasis.NET -> "Son tus $pair más tu costo de $cost, redondeados."
            else -> "Son tus $pair menos tu costo de $cost, redondeados."
        }
    }
    fun changes(draft: LimitDraft, saved: LimitDraft): String = buildList {
        for (metric in PreferredMetric.entries) {
            val d = draft.copy(metric = metric); val s = saved.copy(metric = metric)
            if (!sameAmount(d.pair.goal, s.pair.goal)) add("Tu meta: ${limit(d.pair.goal, d.unit)}")
            if (!sameAmount(d.pair.minimum, s.pair.minimum)) add("Tu mínimo: ${limit(d.pair.minimum, d.unit)}")
        }
        if (draft.metric != saved.metric) add("Decides por: ${if (draft.metric == PreferredMetric.IPK) "Por km" else "Por hora"}")
        if (draft.basis != saved.basis) add("Muestra: ${if (draft.basis == RateBasis.NET) "Neto" else "Bruto"}")
    }.joinToString("\n")
    fun savedMessage(draft: LimitDraft, saved: LimitDraft, reset: Boolean): String {
        val d = draft.pair; val s = saved.copy(metric = draft.metric).pair
        val goal = limit(d.goal, draft.unit); val minimum = limit(d.minimum, draft.unit)
        val otherChanged = if (draft.metric == PreferredMetric.IPK) !same(draft.hour, saved.hour) else !same(draft.km, saved.km)
        return when {
            draft.basis != saved.basis -> "Guardado: tu meta $goal · tu mínimo $minimum"
            otherChanged -> "Guardado: ${changes(draft, saved).replace("\n", " · ") }"
            reset -> "Guardado: volviste a $goal y $minimum"
            draft.metric != saved.metric || (!sameAmount(d.goal, s.goal) && !sameAmount(d.minimum, s.minimum)) -> "Guardado: tu meta $goal · tu mínimo $minimum"
            !sameAmount(d.goal, s.goal) -> "Guardado: tu meta ahora es $goal"
            else -> "Guardado: tu mínimo ahora es $minimum"
        }
    }
    /**
     * Labelled teaching offer ($148, 12 km, 24 min), judged and shown by the same engine and basis as
     * capture, so the editor's example never disagrees with the real chip; never saved as a trip.
     */
    fun preview(draft: LimitDraft, costPerKm: BigDecimal?): ReglaModel {
        val result = NetProfitEngine().evaluate(TripOffer("EXAMPLE", 148.0, 2.0, 4.0, 10.0, 20.0), costPerKm, draft.threshold(), draft.metric, draft.basis)
        val position = when (result.verdict.level) { VerdictLevel.GREEN -> ReglaPosition.ABOVE; VerdictLevel.YELLOW -> ReglaPosition.MEETS; VerdictLevel.RED -> ReglaPosition.BELOW; else -> null }
        val rate = result.rate(draft.metric, draft.basis)
        return if (position != null && rate != null) ReglaModel.rated(position, rate, draft.unit, draft.basis)
            else ReglaModel(ReglaBand.Unavailable(ReglaUnavailable.UNCONFIRMED), draft.unit, draft.basis)
    }
}
