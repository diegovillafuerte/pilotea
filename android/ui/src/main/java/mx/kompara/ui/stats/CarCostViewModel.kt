package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.accounting.*
import mx.kompara.data.settings.*
import mx.kompara.metrics.rollup.RollupRecomputer
import java.time.*
import javax.inject.Inject

data class CarCostState(val open: Boolean = false, val amount: String = "", val from: LocalDate = LocalDate.now(),
    val today: LocalDate = from, val canRemove: Boolean = false, val legacy: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val confirmRemove: Boolean = false)

@HiltViewModel
class CarCostViewModel @Inject constructor(private val car: CarCostRepository,
    private val costs: CostProfileRepository, private val recomputer: RollupRecomputer,
    private val clock: AppClock, private val zone: ZoneId, private val handle: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(CarCostState())
    val state = mutable.asStateFlow()
    init { if (handle.get<Boolean>("carOpen") == true) open() }
    fun open() { viewModelScope.launch {
        try {
            val today = Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate()
            val versions = car.get()
            val current = CostSchedule(emptyList(), versions).carAt(today)?.takeIf { it.weeklyMxn != null }
            val scheduled = versions.filter { it.effectiveFrom > today }.groupBy { it.effectiveFrom }.values.map { rows -> rows.maxBy { it.id } }.filter { it.weeklyMxn != null }.minByOrNull { it.effectiveFrom }
            val selected = current ?: scheduled
            val old = costs.get(clock.nowMs())
            val legacy = if (versions.isEmpty()) old?.let { (it.insurancePerDayMxn + it.rentPerDayMxn).decimal().multiply(java.math.BigDecimal(7)) }?.takeIf { it.signum() > 0 } else null
            mutable.value = CarCostState(true, handle["carAmount"] ?: (selected?.weeklyMxn ?: legacy)?.stripTrailingZeros()?.toPlainString().orEmpty(),
                handle.get<String>("carFrom")?.let(LocalDate::parse) ?: selected?.effectiveFrom ?: today,
                today, selected != null, legacy != null)
            handle["carOpen"] = true
        } catch (e: Exception) { if (e is CancellationException) throw e
            mutable.value = state.value.copy(open = true, error = "No pudimos cargar lo del coche. Inténtalo otra vez.") }
    } }
    fun amount(text: String) { if (!state.value.saving) { handle["carAmount"] = text; mutable.value = state.value.copy(amount = text, error = null) } }
    fun date(date: LocalDate) { if (!state.value.saving) { handle["carFrom"] = date.toString(); mutable.value = state.value.copy(from = date, error = null) } }
    fun dismiss() { if (!state.value.saving) {
        handle["carOpen"] = false; handle.remove<String>("carAmount"); handle.remove<String>("carFrom")
        mutable.value = state.value.copy(open = false, confirmRemove = false)
    } }
    fun confirmRemove(show: Boolean) { if (!state.value.saving) mutable.value = state.value.copy(confirmRemove = show) }
    fun save(remove: Boolean = false) {
        val s = state.value
        if (s.saving || (remove && !s.canRemove)) return
        val value = carCostDecimal(s.amount)
        if (!remove && value == null) { mutable.value = s.copy(error = "Revisa este número"); return }
        mutable.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val now = clock.nowMs()
                val from = if (remove) Instant.ofEpochMilli(now).atZone(zone).toLocalDate() else s.from
                recomputer.editCosts(from) {
                    if (remove) car.remove(from, now) else car.save(value!!, from, now)
                }
                mutable.value = state.value.copy(saving = false); dismiss()
            } catch (e: Exception) { if (e is CancellationException) throw e
                mutable.value = state.value.copy(saving = false, confirmRemove = false, error = "No pudimos guardar lo del coche. Inténtalo otra vez.") }
        }
    }
}
