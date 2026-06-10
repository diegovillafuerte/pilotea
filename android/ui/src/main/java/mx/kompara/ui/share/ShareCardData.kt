package mx.kompara.ui.share

/**
 * The fully-resolved, render-ready content of a "Tu Semana / Tu Mes" share card (B-055) — the
 * acquisition-loop card a driver shares to a WhatsApp group.
 *
 * Pure data, built by [ShareCardComposer] from a period's stats + the (optional) percentile flex.
 * Everything is a pre-formatted, es-MX string so the [ShareCardRenderer] (Android Canvas) and any
 * preview composable draw the exact same content without re-running any logic. There are no peso
 * numbers left to format here: when [hideAmounts] is true the [netEarnings] line is already redacted
 * (null), so a renderer cannot accidentally leak an amount.
 *
 * @property periodLabel the es-MX period heading, e.g. "Semana del 1–7 jun" or "Junio 2026".
 * @property periodKind whether this is a week or month card (drives the headline string).
 * @property netEarnings the formatted net-earnings figure ("$3,450.00"), or null when redacted
 *   ([hideAmounts]) so no amount ever reaches the renderer.
 * @property trips formatted completed-trip count ("38 viajes"); always shown (never an amount).
 * @property hours formatted online hours ("22.5 h"), or null when the period has no hours.
 * @property percentileFlex the marketing flex line ("Top 22% en CDMX 🚀") when a favorable
 *   percentile exists, else null. **Intentionally NOT premium-gated** — it is the card's marketing
 *   hook and is shown even to free-tier drivers (see [ShareCardComposer]).
 * @property streakLine the streak brag ("🔥 4 semanas seguidas"), or null when there is no streak.
 * @property hideAmounts whether peso amounts are redacted on this card (mirrors the toggle). Carried
 *   so the renderer/preview can show the "amounts hidden" affordance.
 */
data class ShareCardData(
    val periodLabel: String,
    val periodKind: SharePeriodKind,
    val netEarnings: String?,
    val trips: String,
    val hours: String?,
    val percentileFlex: String?,
    val streakLine: String?,
    val hideAmounts: Boolean,
) {
    /**
     * True when the card has *something* to brag about beyond the bare trip count — a real
     * net amount, a percentile flex, or a streak. A redacted card with no percentile and no streak
     * is nearly empty; callers can use this to decide whether sharing is worthwhile.
     */
    val hasHighlight: Boolean
        get() = netEarnings != null || percentileFlex != null || streakLine != null
}

/** Whether a share card summarises a week or a month (B-055). Drives the headline copy. */
enum class SharePeriodKind { WEEK, MONTH }
