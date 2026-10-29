package mx.kompara.ui.stats

import mx.kompara.data.accounting.*
import java.math.BigDecimal

/** Empty/malformed inputs are unavailable, never a free kilometre. */
fun costDecimal(text: String): BigDecimal? = text.trim().removePrefix("$").trim()
    .takeIf { it.matches(Regex("[0-9]+([.,][0-9]{1,6})?")) }
    ?.replace(',', '.')?.toBigDecimalOrNull()

/** Weekly pesos accept es-MX grouped paste as well as the decimal keyboard's raw digits. */
fun carCostDecimal(text: String): BigDecimal? {
    val value = text.trim().removePrefix("$").trim()
    return if (value.matches(Regex("[1-9][0-9]{0,2}(,[0-9]{3})+(\\.[0-9]{1,6})?"))) value.replace(",", "").toBigDecimalOrNull()
        else costDecimal(text)
}

/** Raw draft fields also retain the inactive fuel mode across switches/recreation. */
data class CostProfileInputs(
    val isEv: Boolean = false,
    val rendimientoKmPerLitre: String = "",
    val gasPricePerLitreMxn: String = "",
    val kwhPer100Km: String = "",
    val costPerKwhMxn: String = "",
    val maintenancePerKmMxn: String = "",
    val direct: Boolean = false,
    val directPerKm: String = "",
) {
    fun runningCosts(): RunningCostInputs? {
        val mode = if (direct) RunningCostMode.DIRECT else if (isEv) RunningCostMode.ELECTRIC else RunningCostMode.GAS
        if (mode == RunningCostMode.GAS && costDecimal(rendimientoKmPerLitre)?.let { it >= BigDecimal(4) && it <= BigDecimal(40) } != true) return null
        return RunningCostInputs(mode,
            if (direct) costDecimal(maintenancePerKmMxn) ?: BigDecimal.ZERO else costDecimal(maintenancePerKmMxn) ?: return null,
            costDecimal(gasPricePerLitreMxn), costDecimal(rendimientoKmPerLitre),
            costDecimal(kwhPer100Km), costDecimal(costPerKwhMxn), costDecimal(directPerKm))
            .takeIf { it.costPerKm != null }
    }
    fun encode() = arrayListOf(isEv.toString(), rendimientoKmPerLitre, gasPricePerLitreMxn, kwhPer100Km,
        costPerKwhMxn, maintenancePerKmMxn, direct.toString(), directPerKm)
    companion object {
        fun decode(v: ArrayList<String>) = CostProfileInputs(v[0].toBoolean(), v[1], v[2], v[3], v[4], v[5], v[6].toBoolean(), v[7])
        fun from(v: RunningCostInputs) = CostProfileInputs(v.mode == RunningCostMode.ELECTRIC,
            v.kmPerLitre?.stripTrailingZeros()?.toPlainString().orEmpty(), v.fuelPricePerLitre?.toPlainString().orEmpty(),
            v.kwhPer100Km?.toPlainString().orEmpty(), v.pricePerKwh?.toPlainString().orEmpty(), v.wearPerKm.toPlainString(),
            v.mode == RunningCostMode.DIRECT || v.mode == RunningCostMode.LEGACY,
            (if (v.mode == RunningCostMode.LEGACY) v.costPerKm else v.directPerKm)?.toPlainString().orEmpty())
    }
}

object CostPreview {
    val SAMPLE_KM = BigDecimal(12)
    fun compute(inputs: CostProfileInputs): CostPreviewResult? = inputs.runningCosts()?.costPerKm?.let {
        CostPreviewResult(it, it.multiply(SAMPLE_KM))
    }
}
data class CostPreviewResult(val perKm: BigDecimal, val sampleCost: BigDecimal)
enum class CostField(val title: String) {
    FUEL("Combustible"), EFFICIENCY("Rendimiento"), PRICE("Precio de la gasolina"),
    WEAR("Desgaste"), CONSUMPTION("Consumo de energía"), TARIFF("Precio de la energía"), DIRECT("Escribe tu costo por km")
}
