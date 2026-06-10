package mx.kompara.ui.format

import java.text.NumberFormat
import java.util.Locale

/**
 * es-MX number/currency formatting for everything a chofer sees. Kept Android-free so the rules
 * are unit-testable on the JVM (the composables only ever consume the returned strings).
 *
 * All formatting is pinned to [MX] so the output is identical regardless of the device locale —
 * a driver in Mexico always sees "$1,234.56", "12.3 km", "$185.50/h".
 */
object Formatters {
    private val MX: Locale = Locale.forLanguageTag("es-MX")

    /**
     * Pesos with the Mexican peso sign and two decimals, e.g. `formatMxn(1234.56)` → "$1,234.56".
     * Uses a plain "$" prefix (not "MX$") since the whole app is single-currency.
     */
    fun formatMxn(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$" + nf.format(amount)
    }

    /**
     * Distance in kilometres with one decimal and a "km" suffix, e.g. `formatKm(12.34)` → "12.3 km".
     */
    fun formatKm(km: Double): String {
        val nf = NumberFormat.getNumberInstance(MX).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return nf.format(km) + " km"
    }

    /**
     * A per-hour rate in pesos, e.g. `formatPerHour(185.5)` → "$185.50/h". Reuses [formatMxn] so
     * the money part stays consistent.
     */
    fun formatPerHour(amountPerHour: Double): String = formatMxn(amountPerHour) + "/h"
}
