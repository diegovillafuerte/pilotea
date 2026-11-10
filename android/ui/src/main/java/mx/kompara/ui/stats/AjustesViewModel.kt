package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import mx.kompara.data.accounting.*
import mx.kompara.data.model.City
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
import mx.kompara.sync.auth.*
import mx.kompara.ui.components.StatusGlyph
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.format.spanishDate
import mx.kompara.ui.service.statusGlyph
import java.time.*
import javax.inject.Inject

/** J1's six rows: the value each row shows (v4 plan 4.8). */
data class AjustesState(
    val limits: String = "", val costs: String = "", val reader: String = "", val glyph: StatusGlyph = StatusGlyph.WAITING,
    val account: String = "Entrar con WhatsApp", val signedIn: Boolean = false, val firstPermission: Boolean = false,
)
/** J1's Tus costos value: "$2.84 por km · coche $1,500 a la semana"; the car only when a weekly amount is in force. */
fun costsSummary(costPerKm: java.math.BigDecimal?, weeklyCar: java.math.BigDecimal?): String =
    (costPerKm?.let { "${Formatters.formatMxn(it)} por km" } ?: "Sin confirmar") +
        (weeklyCar?.let { " · coche ${Formatters.formatMxnWhole(it)} a la semana" } ?: "")
/** J1's Tu cuenta value: the phone (or "Entrar con WhatsApp") and the driver's city once known. */
fun accountSummary(phone: String?, city: City?): String = listOfNotNull(
    phone?.let { if (it.startsWith("+52") && it.length == 13) "+52 ${it.substring(3, 5)} ${it.substring(5, 9)} ${it.substring(9)}" else it } ?: "Entrar con WhatsApp",
    city?.displayName).joinToString(" · ")
fun costDate(date: LocalDate, today: LocalDate = date) = date.format(spanishDate(
    if (date.year == today.year) "EEEE d MMM" else "EEEE d MMM yyyy"))
fun settingsReaderText(reader: ReaderReadiness): String = when (reader.phase) {
    ReaderPhase.OFF -> if (reader.permission == false) "Apagado · falta el permiso" else "Apagado · sin lectura"
    ReaderPhase.PREPARED -> if (reader.permission == true) "Preparado · permiso listo" else "Comprobando el permiso"
    ReaderPhase.WORKING -> "Funcionando"
    ReaderPhase.ATTENTION -> "Batería con restricciones · aún funciona"
    ReaderPhase.PAUSED -> "Apagado · lo detuviste"
}

@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    costs: CostProfileRepository,
    car: CarCostRepository,
    reader: ReaderReadinessRepository,
    auth: AuthRepository,
    clock: AppClock,
    zone: ZoneId,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val failure = error.asStateFlow()
    private val refresh = MutableStateFlow(0)
    private val money = combine(costs.versions, car.versions, clock.moments()) { running, fixed, now ->
        val schedule = CostSchedule(running.map { it.toDomain() }, fixed.map { it.toDomain() })
        costsSummary(schedule.runningAt(now.toEpochMilli())?.inputs?.costPerKm, schedule.carAt(now.atZone(zone).toLocalDate())?.weeklyMxn)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest { combine(settingsRepository.settings, money, reader.state, auth.sessionState) { prefs, costsLabel, ready, session ->
        val driver = (session as? SessionState.Authenticated)?.driver
        AjustesState(
            limits = ThresholdEditor.summary(LimitDraft.from(prefs.effectiveThreshold, prefs.preferredMetric, prefs.rateBasis)),
            costs = costsLabel, reader = settingsReaderText(ready), glyph = ready.statusGlyph(),
            account = accountSummary(driver?.phone, driver?.city?.let { City.fromKey(it) } ?: prefs.city.takeIf { prefs.cityChosen }),
            signedIn = driver != null,
            firstPermission = ready.permission == false && ready.apps.values.none { it.lastReadAt != null } && ready.incident == null,
        )
    }.map<AjustesState, AjustesState?> { it }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        error.value = "No pudimos cargar tus ajustes. Inténtalo otra vez."; emit(null)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun retry() { error.value = null; refresh.value++ }
}
