package mx.kompara.ui.stats

import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.metrics.NetProfitEngine
import mx.kompara.metrics.TripOffer
import mx.kompara.metrics.VerdictLevel
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import java.math.RoundingMode

enum class LimitField { GOAL, MINIMUM }
data class LimitPair(val goal: String, val minimum: String)

/** Raw text stays in the draft; neither typing, stepping, reset nor unit selection writes settings. */
data class LimitDraft(val km: LimitPair, val hour: LimitPair, val metric: PreferredMetric) {
    val pair get() = if (metric == PreferredMetric.IPK) km else hour
    val unit get() = if (metric == PreferredMetric.IPK) ReglaUnit.KM else ReglaUnit.HOUR
    fun withPair(pair: LimitPair) = if (metric == PreferredMetric.IPK) copy(km = pair) else copy(hour = pair)
    fun edit(field: LimitField, text: String) = withPair(if (field == LimitField.GOAL) pair.copy(goal = text) else pair.copy(minimum = text))
    fun encode() = arrayListOf(km.goal, km.minimum, hour.goal, hour.minimum, metric.name)
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
        fun from(value: PlatformThreshold, metric: PreferredMetric) = LimitDraft(
            LimitPair(value.minPerKmMxn.toBigDecimal().input(), value.redPerKmMxn.toBigDecimal().input()),
            LimitPair(value.minPerHourMxn.toBigDecimal().input(), value.redPerHourMxn.toBigDecimal().input()), metric)
        fun decode(values: List<String>) = LimitDraft(LimitPair(values[0], values[1]), LimitPair(values[2], values[3]), PreferredMetric.valueOf(values[4]))
    }
}

private fun String.amount() = ThresholdEditor.parse(this)
private fun BigDecimal.input() = setScale(2, RoundingMode.HALF_UP).toPlainString()

object ThresholdEditor {
    val MAX_PER_KM = BigDecimal("99.99")
    fun parse(text: String): BigDecimal? {
        val clean = text.trim().removePrefix("$").trim().replace(',', '.')
        if (!Regex("[0-9]+(?:\\.[0-9]{0,2})?").matches(clean)) return null
        return clean.toBigDecimalOrNull()?.takeIf { it.toDouble().isFinite() }
    }
    fun step(text: String, metric: PreferredMetric, increase: Boolean): String {
        val value = parse(text) ?: BigDecimal.ZERO
        val step = if (metric == PreferredMetric.IPK) BigDecimal("0.50") else BigDecimal.TEN
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
    fun dirty(draft: LimitDraft, saved: LimitDraft) = draft.metric != saved.metric || !same(draft.km, saved.km) || !same(draft.hour, saved.hour)
    fun limit(text: String, unit: ReglaUnit): String = parse(text)?.let {
        // Preserve typed hourly cents too: whole hourly limits are compact, never silently rounded.
        (if (unit == ReglaUnit.KM || it.stripTrailingZeros().scale() > 0) Formatters.formatMxn(it) else Formatters.formatMxnWhole(it)) + unit.suffix
    } ?: text
    fun pairLabel(draft: LimitDraft) = "${limit(draft.pair.goal, draft.unit)} · ${limit(draft.pair.minimum, draft.unit)}"
    fun changes(draft: LimitDraft, saved: LimitDraft): String = buildList {
        for (metric in PreferredMetric.entries) {
            val d = draft.copy(metric = metric); val s = saved.copy(metric = metric)
            if (!sameAmount(d.pair.goal, s.pair.goal)) add("Tu meta: ${limit(d.pair.goal, d.unit)}")
            if (!sameAmount(d.pair.minimum, s.pair.minimum)) add("Tu mínimo: ${limit(d.pair.minimum, d.unit)}")
        }
        if (draft.metric != saved.metric) add("Decides por: ${if (draft.metric == PreferredMetric.IPK) "Por km" else "Por hora"}")
    }.joinToString("\n")
    fun savedMessage(draft: LimitDraft, saved: LimitDraft, reset: Boolean): String {
        val d = draft.pair; val s = saved.copy(metric = draft.metric).pair
        val goal = limit(d.goal, draft.unit); val minimum = limit(d.minimum, draft.unit)
        val otherChanged = if (draft.metric == PreferredMetric.IPK) !same(draft.hour, saved.hour) else !same(draft.km, saved.km)
        return when {
            otherChanged -> "Guardado: ${changes(draft, saved).replace("\n", " · ") }"
            reset -> "Guardado: volviste a $goal y $minimum"
            draft.metric != saved.metric || (!sameAmount(d.goal, s.goal) && !sameAmount(d.minimum, s.minimum)) -> "Guardado: tu meta $goal · tu mínimo $minimum"
            !sameAmount(d.goal, s.goal) -> "Guardado: tu meta ahora es $goal"
            else -> "Guardado: tu mínimo ahora es $minimum"
        }
    }
    /** Labelled teaching offer, calculated by the same engine as capture; never saved as a trip. */
    fun preview(draft: LimitDraft, costPerKm: BigDecimal?): ReglaModel {
        val result = NetProfitEngine().evaluate(TripOffer("EXAMPLE", 148.0, 2.0, 4.0, 10.0, 20.0), costPerKm, draft.threshold(), draft.metric)
        val position = when (result.verdict.level) { VerdictLevel.GREEN -> ReglaPosition.ABOVE; VerdictLevel.YELLOW -> ReglaPosition.MEETS; VerdictLevel.RED -> ReglaPosition.BELOW; else -> null }
        val rate = (if (draft.metric == PreferredMetric.IPK) result.perKmDecimal else result.perHourDecimal)?.toBigDecimalOrNull()
        return ReglaModel(if (position != null && rate != null) ReglaBand.Rated(position, rate) else ReglaBand.Unavailable(ReglaUnavailable.UNCONFIRMED),
            parse(draft.pair.goal)!!, parse(draft.pair.minimum)!!, draft.unit, showLimitCents = draft.metric == PreferredMetric.IPK)
    }
}
