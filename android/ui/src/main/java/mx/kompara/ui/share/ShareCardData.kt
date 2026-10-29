package mx.kompara.ui.share

/** Only disclosure-safe content reaches Canvas, captions and accessibility. No raw private payload. */
data class ShareCardData(
    val periodLabel: String,
    val periodKind: SharePeriodKind,
    val finding: String,
    val source: String,
    val basis: String,
    val trips: String,
    val confirmed: Boolean = false,
    val netLabel: String? = null,
    val netEarnings: String? = null,
    val hoursLabel: String? = null,
    val hours: String? = null,
    val bars: List<ShareCardBar> = emptyList(),
    val hideAmounts: Boolean = true,
) {
    init {
        require(!hideAmounts || (netEarnings == null && hours == null && bars.isEmpty()))
    }
    val description: String get() = listOfNotNull(
        "Kompara · $periodLabel", finding, source,
        netEarnings?.let { "$netLabel $it" },
        bars.takeIf { it.isNotEmpty() }?.joinToString(". ") { "${it.label} ${it.amount}" },
        basis, "Viajes $trips", hours?.let { "$hoursLabel $it" },
        "Semana confirmada con mi resumen".takeIf { confirmed }, "kompara.mx",
    ).joinToString(". ")
    val caption: String get() = description
}
data class ShareCardBar(val label: String, val amount: String, val fraction: Float, val own: Boolean)
enum class SharePeriodKind { WEEK, MONTH }
