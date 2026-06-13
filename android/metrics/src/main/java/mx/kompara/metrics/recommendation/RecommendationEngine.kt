package mx.kompara.metrics.recommendation

import java.util.Locale
import kotlin.math.roundToInt

/**
 * The on-device rule-based recommendations engine (B-048) — the port-and-enrich of the web MVP's
 * `src/lib/recommendations/engine.ts`, with capture-powered rules the web never had.
 *
 * ## What it is
 * A pure function: feed it a [RecommendationContext] for one driver-week and it returns up to
 * [MAX_RECOMMENDATIONS] [Recommendation]s, priority-ordered (warnings → actionable → praise). No IO,
 * no Android, no clock — the `:ui` viewmodel assembles the context from the repos and this just
 * decides. That keeps every rule exhaustively unit-testable against crafted fixture weeks.
 *
 * ## The rules
 * Each rule has a **guard** so it NEVER fires on insufficient data (the cardinal sin of a tips engine
 * is confidently advising on three trips). Ported from the web:
 *  - [streakPraise] — 4+ consecutive weeks of data.
 *  - [highEarningsPerHour] — top quartile city $/hr ("top X% en CDMX"). *Premium* (percentile).
 *  - [lowEarningsPerTrip] — below the city p25 on $/trip. *Premium* (percentile).
 *  - [highCommission] — commission worse than the city benchmark. *Premium* (percentile).
 *  - [crossPlatform] — platform A beats B on $/km by a wide margin → shift hours. *Premium*.
 * New, capture-powered (all *free* — part of the hook):
 *  - [missedGoodOffers] — declined offers that met the goal: "$X que dejaste ir".
 *  - [bestHours] — the driver's most lucrative block: "tus mejores horas: Vie 19–23".
 *  - [acceptanceGuidance] — very low acceptance + below goal → loosen; accepting reds → raise floor.
 *
 * ## Premium partition (rationale)
 * Anything that needs the **city population benchmarks** (the four percentile rules) or the
 * **cross-platform** comparison is premium: those are the comparative-intelligence surfaces B-046/
 * B-050 gate behind the paywall, and a recommendation that quietly leaks "you're in the top 8% in
 * CDMX" would undercut that gate. Everything derived purely from the driver's *own* captured data —
 * the streak, the goal, their best hours, their acceptance behaviour — is free, because that data is
 * the reader's free hook and costs us nothing comparative to surface. [Recommendation.premium]
 * carries the flag; the engine still *computes* premium tips (so the gated card can tease them), and
 * the `:ui` layer decides whether to show the real copy or the locked stand-in.
 */
class RecommendationEngine {

    /**
     * Evaluate every rule against [context] and return the top [MAX_RECOMMENDATIONS], priority-ordered.
     *
     * Selection: collect all rules that fire, sort by [Recommendation.priority] (stable — ties keep
     * the rule declaration order below, which is itself a sensible secondary ranking), and take the
     * first [MAX_RECOMMENDATIONS]. A warning always outranks an actionable tip, which always outranks
     * praise, so a driver leaking money sees that first.
     */
    fun recommend(context: RecommendationContext): List<Recommendation> {
        val fired = buildList {
            // Order here is the stable tie-breaker within a priority band.
            highCommission(context)?.let(::add)
            lowEarningsPerTrip(context)?.let(::add)
            missedGoodOffers(context)?.let(::add)
            acceptanceGuidance(context)?.let(::add)
            crossPlatform(context)?.let(::add)
            bestHours(context)?.let(::add)
            highEarningsPerHour(context)?.let(::add)
            streakPraise(context)?.let(::add)
        }
        return fired
            .sortedBy { it.priority } // stable sort keeps declaration order within a band
            .take(MAX_RECOMMENDATIONS)
    }

    // ─── Ported web rules ──────────────────────────────────────────────────────────────────────

    /** Praise a 4+ week streak. Free (own data). Mirrors the web `streak_weeks >= 4` rule. */
    private fun streakPraise(c: RecommendationContext): Recommendation? {
        if (c.streakWeeks < STREAK_MIN_WEEKS) return null
        return Recommendation(
            id = "streak_praise",
            type = RecommendationType.POSITIVE,
            title = "¡${c.streakWeeks} semanas seguidas!",
            body = "Llevas ${c.streakWeeks} semanas registrando tus datos sin parar. Esa constancia es la que te" +
                " da números reales para decidir.",
            premium = false,
        )
    }

    /**
     * Praise a top-quartile city $/hr. Premium (percentile). Mirrors the web "top X%" rule; needs a
     * non-synthetic-aware display percentile ≥ 75 AND enough hours to be a real read.
     */
    private fun highEarningsPerHour(c: RecommendationContext): Recommendation? {
        if (c.hoursOnline < MIN_HOURS) return null
        val pct = c.percentile(METRIC_EPH) ?: return null
        if (pct.displayPercentile < TOP_QUARTILE_DISPLAY) return null
        val cityLabel = c.city ?: DEFAULT_CITY_LABEL
        return Recommendation(
            id = "high_eph_percentile",
            type = RecommendationType.POSITIVE,
            title = "Estás en el top ${pct.topPercent}%",
            body = "Tu ingreso por hora está en el top ${pct.topPercent}% de los choferes en $cityLabel." +
                " Vas muy bien — sigue manejando en tus mejores bloques.",
            premium = true,
        )
    }

    /**
     * Warn when city $/trip is in the bottom quartile. Premium (percentile). Mirrors the web
     * "below p25" rule, rephrased actionably.
     */
    private fun lowEarningsPerTrip(c: RecommendationContext): Recommendation? {
        if (c.totalTrips < MIN_TRIPS) return null
        val pct = c.percentile(METRIC_EPT) ?: return null
        if (pct.displayPercentile >= BOTTOM_QUARTILE_DISPLAY) return null
        return Recommendation(
            id = "low_ept_percentile",
            type = RecommendationType.INFO,
            title = "Tu pago por viaje es bajo",
            body = "Tu ingreso por viaje está por debajo de la mitad de los choferes en tu ciudad. Rechaza" +
                " los viajes cortos en horas pico y deja que la app te traiga uno mejor.",
            premium = true,
        )
    }

    /**
     * Warn when the platform commission is worse than the city benchmark. Premium (percentile +
     * commission). Mirrors the web high-commission rule; only imported weeks carry [commissionPct].
     */
    private fun highCommission(c: RecommendationContext): Recommendation? {
        val commission = c.commissionPct ?: return null
        val pct = c.percentile(METRIC_COMMISSION) ?: return null
        // displayPercentile is inverted for commission (high display = low commission = good), so a
        // LOW display percentile means the driver's commission is worse than the city.
        if (pct.displayPercentile >= COMMISSION_BAD_DISPLAY) return null
        return Recommendation(
            id = "high_commission",
            type = RecommendationType.WARNING,
            title = "Tu comisión está alta",
            body = "Esta semana la plataforma se quedó con el ${formatPct(commission)} — más que la mayoría" +
                " de los choferes en tu ciudad. Revisa si tuviste cancelaciones o ajustes que te la subieron.",
            premium = true,
        )
    }

    /**
     * Suggest shifting hours when one platform pays materially more per km. Premium (cross-platform).
     * Mirrors the web cross-platform rule: needs 2+ platforms and a spread over [CROSS_PLATFORM_MIN_GAP].
     */
    private fun crossPlatform(c: RecommendationContext): Recommendation? {
        val rates = c.crossPlatform.filter { it.netPerKm > 0.0 }
        if (rates.size < 2) return null
        val best = rates.maxBy { it.netPerKm }
        val worst = rates.minBy { it.netPerKm }
        if (best.platform == worst.platform) return null
        val gapPct = (best.netPerKm - worst.netPerKm) / worst.netPerKm * 100.0
        if (gapPct < CROSS_PLATFORM_MIN_GAP) return null
        return Recommendation(
            id = "cross_platform",
            type = RecommendationType.INFO,
            title = "${platformLabel(best.platform)} te paga más por km",
            body = "${platformLabel(best.platform)} te deja ${gapPct.roundToInt()}% más por kilómetro que" +
                " ${platformLabel(worst.platform)} esta semana. Dale prioridad en tus horas pico.",
            premium = true,
        )
    }

    // ─── New capture-powered rules ───────────────────────────────────────────────────────────────

    /**
     * The money the driver left on the table: declined offers the engine had judged GREEN. Free (own
     * data). Fires only when at least [MISSED_MIN_OFFERS] good offers were declined, summing their fares.
     */
    private fun missedGoodOffers(c: RecommendationContext): Recommendation? {
        val missed = c.offers.filter { it.declined && it.verdictGreen }
        if (missed.size < MISSED_MIN_OFFERS) return null
        val left = missed.sumOf { it.fareMxn }
        if (left <= 0.0) return null
        return Recommendation(
            id = "missed_good_offers",
            type = RecommendationType.WARNING,
            title = "Dejaste ir buenos viajes",
            body = "Rechazaste ${missed.size} ofertas que sí te convenían — ${formatMxn(left)} que se te" +
                " fueron. Cuando Kompara te marque verde, tómalo con confianza.",
            premium = false,
        )
    }

    /**
     * Tell the driver their single most lucrative block. Free (own data). Fires only when the block has
     * enough trips to be a pattern and a non-trivial net.
     */
    private fun bestHours(c: RecommendationContext): Recommendation? {
        val block = c.bestHour ?: return null
        if (block.tripCount < BEST_HOUR_MIN_TRIPS) return null
        if (block.netMxn < BEST_HOUR_MIN_NET) return null
        val day = dayLabel(block.dayOfWeek) ?: return null
        val endHour = (block.hour + 1) % 24
        return Recommendation(
            id = "best_hours",
            type = RecommendationType.INFO,
            title = "Tus mejores horas",
            body = "Tu mejor bloque fue el $day de ${formatHour(block.hour)} a ${formatHour(endHour)}:" +
                " ${formatMxn(block.netMxn)} netos. Maneja más en ese horario.",
            premium = false,
        )
    }

    /**
     * Guidance on the acceptance threshold from captured behaviour. Free (own data). Two mutually
     * exclusive sub-rules, in priority order:
     *  - **Too strict**: very low acceptance AND the goal wasn't met → loosen the floor to catch work.
     *  - **Too loose**: accepting RED offers (took offers that weren't worth it) → raise the floor.
     * Needs enough resolved offers to judge ([ACCEPTANCE_MIN_OFFERS]).
     */
    private fun acceptanceGuidance(c: RecommendationContext): Recommendation? {
        val resolved = c.offers.count { it.accepted || it.declined }
        if (resolved < ACCEPTANCE_MIN_OFFERS) return null

        // Too loose: the driver accepted offers the engine judged NOT green (a red/yellow) — raising
        // their floor would stop them taking work that doesn't pay. Checked first: it's a money leak.
        val acceptedReds = c.offers.count { it.accepted && !it.verdictGreen }
        if (acceptedReds >= ACCEPTED_REDS_MIN) {
            return Recommendation(
                id = "acceptance_raise_floor",
                type = RecommendationType.WARNING,
                title = "Estás tomando viajes en rojo",
                body = "Aceptaste $acceptedReds viajes que Kompara marcó en rojo. Sube tu mínimo en Ajustes" +
                    " para que la app filtre los que no valen la pena.",
                premium = false,
            )
        }

        // Too strict: very low acceptance AND missed the goal — loosening lets more work through.
        val acceptance = c.acceptanceRate
        if (acceptance != null && acceptance < ACCEPTANCE_LOW && c.goalReached == false) {
            return Recommendation(
                id = "acceptance_loosen",
                type = RecommendationType.INFO,
                title = "Estás rechazando casi todo",
                body = "Aceptaste solo el ${formatPct(acceptance * 100.0)} de tus ofertas y no llegaste a tu" +
                    " meta. Baja un poco tu mínimo para no quedarte sin viajes en las horas flojas.",
                premium = false,
            )
        }
        return null
    }

    // ─── Formatting helpers (kept local so copy + math live together) ────────────────────────────

    private fun formatMxn(value: Double): String = "$${value.roundToInt()}"

    private fun formatPct(value: Double): String =
        if (value == value.roundToInt().toDouble()) "${value.roundToInt()}%"
        else String.format(Locale.US, "%.1f%%", value)

    private fun formatHour(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

    /** Spanish day label for an ISO day-of-week (1 = Monday). null when out of range. */
    private fun dayLabel(dow: Int): String? = when (dow) {
        1 -> "lunes"; 2 -> "martes"; 3 -> "miércoles"; 4 -> "jueves"
        5 -> "viernes"; 6 -> "sábado"; 7 -> "domingo"; else -> null
    }

    /** Title-case platform name (UBER → Uber). */
    private fun platformLabel(platform: String): String =
        platform.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }

    companion object {
        /** Hard cap on cards shown — 3, matching the web engine and the Inicio "Consejos" slot. */
        const val MAX_RECOMMENDATIONS = 3

        // Backend metric keys (must match PercentileCalculator / population_stats).
        const val METRIC_EPH = "earnings_per_hour"
        const val METRIC_EPT = "earnings_per_trip"
        const val METRIC_COMMISSION = "platform_commission_pct"

        // Guards / thresholds.
        const val STREAK_MIN_WEEKS = 4
        const val MIN_HOURS = 3.0
        const val MIN_TRIPS = 5
        const val TOP_QUARTILE_DISPLAY = 75
        const val BOTTOM_QUARTILE_DISPLAY = 25
        const val COMMISSION_BAD_DISPLAY = 30
        const val CROSS_PLATFORM_MIN_GAP = 15.0
        const val MISSED_MIN_OFFERS = 2
        const val BEST_HOUR_MIN_TRIPS = 3
        const val BEST_HOUR_MIN_NET = 50.0
        const val ACCEPTANCE_MIN_OFFERS = 10
        const val ACCEPTANCE_LOW = 0.30
        const val ACCEPTED_REDS_MIN = 3

        const val DEFAULT_CITY_LABEL = "tu ciudad"
    }
}
