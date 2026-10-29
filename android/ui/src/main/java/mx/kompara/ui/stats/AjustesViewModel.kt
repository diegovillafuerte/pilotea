package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.billing.Entitlement
import mx.kompara.billing.EntitlementRepository
import mx.kompara.data.accounting.*
import mx.kompara.data.model.City
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
import mx.kompara.sync.auth.*
import mx.kompara.ui.components.StatusGlyph
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.service.statusGlyph
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class AjustesState(
    val account: String = "Entrar con WhatsApp", val accountDetail: String? = "Guarda tus costos y tu meta",
    val signedIn: Boolean = false, val plan: String = "Plan gratis", val planDetail: String? = null,
    val limits: String = "", val costs: String = "", val car: String = "Sin agregar",
    val reader: String = "", val glyph: StatusGlyph = StatusGlyph.WAITING,
    val firstPermission: Boolean = false, val theme: ThemePreference = ThemePreference.AUTOMATIC,
)
fun costConfirmationText(version: RunningCostVersion, today: LocalDate, zone: ZoneId): String {
    if (version.confirmation == CostConfirmation.TYPICAL) return "típicos, sin confirmar"
    val date = version.confirmedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: return "confirmados"
    return when (date) {
        today -> "confirmados hoy"
        today.minusDays(1) -> "confirmados ayer"
        else -> "confirmados el ${costDate(date, today)}"
    }
}
fun costDate(date: LocalDate, today: LocalDate = date) = date.format(DateTimeFormatter.ofPattern(
    if (date.year == today.year) "EEEE d MMM" else "EEEE d MMM yyyy", Locale.forLanguageTag("es-MX")))
fun settingsReaderText(reader: ReaderReadiness): String = when (reader.phase) {
    ReaderPhase.OFF -> if (reader.permission == false) "Apagado · falta el permiso" else "Apagado · sin lectura"
    ReaderPhase.PREPARED -> if (reader.permission == true) "Preparado · permiso listo" else "Comprobando el permiso"
    ReaderPhase.WORKING -> "Funcionando"
    ReaderPhase.ATTENTION -> "Batería restringida · aún funciona"
    ReaderPhase.PAUSED -> "En pausa · se reanuda mañana"
}

@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    costs: CostProfileRepository,
    car: CarCostRepository,
    reader: ReaderReadinessRepository,
    auth: AuthRepository,
    entitlement: EntitlementRepository,
    clock: AppClock,
    zone: ZoneId,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val failure = error.asStateFlow()
    private val refresh = MutableStateFlow(0)
    private val money = combine(costs.versions, car.versions, clock.moments()) { running, fixed, now ->
        val schedule = CostSchedule(running.map { it.toDomain() }, fixed.map { it.toDomain() })
        val today = now.atZone(zone).toLocalDate()
        val active = schedule.runningAt(now.toEpochMilli())
        val costLabel = active?.inputs?.costPerKm?.let { "${Formatters.formatMxn(it)} por km · ${costConfirmationText(active, today, zone)}" } ?: "Sin confirmar"
        val current = schedule.carAt(today)
        val next = schedule.car.filter { it.effectiveFrom > today }.minWithOrNull(compareBy<CarCostVersion> { it.effectiveFrom }.thenByDescending { it.id })
        val carLabel = current?.weeklyMxn?.let { "${Formatters.formatMxnWhole(it)} a la semana" } ?: "Sin agregar"
        costLabel to (carLabel + (next?.let { " · ${it.weeklyMxn?.let(Formatters::formatMxnWhole) ?: "Sin cargo"} desde ${costDate(it.effectiveFrom, today)}" } ?: ""))
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest { combine(settingsRepository.settings, money, reader.state, auth.sessionState, entitlement.entitlement) { prefs, amounts, ready, session, plan ->
        val driver = (session as? SessionState.Authenticated)?.driver
        AjustesState(
            account = driver?.phone?.let { phone -> if (phone.startsWith("+52") && phone.length == 13) "+52 ${phone.substring(3, 5)} ${phone.substring(5, 9)} ${phone.substring(9)}" else phone } ?: "Entrar con WhatsApp",
            accountDetail = if (driver == null) "Guarda tus costos y tu meta" else City.fromKey(driver.city)?.displayName,
            signedIn = driver != null, plan = if (plan is Entitlement.Premium) "Tu plan" else "Plan gratis",
            planDetail = (plan as? Entitlement.Premium)?.let { p -> p.expiresAt?.let { "Premium hasta ${costDate(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), ready.date)}" } ?: "Premium" },
            limits = ThresholdEditor.pairLabel(LimitDraft.from(prefs.effectiveThreshold, prefs.preferredMetric)),
            costs = amounts.first, car = amounts.second, reader = settingsReaderText(ready), glyph = ready.statusGlyph(),
            firstPermission = ready.permission == false && ready.apps.values.none { it.lastReadAt != null } && ready.incident == null,
            theme = prefs.themePreference,
        )
    }.map<AjustesState, AjustesState?> { it }.catch { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        error.value = "No pudimos cargar tus ajustes. Inténtalo otra vez."; emit(null)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun retry() { error.value = null; refresh.value++ }
    val preferences = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings.DEFAULT)
    private fun persist(action: suspend () -> Unit) { viewModelScope.launch {
        try { action(); error.value = null } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error.value = "No pudimos guardar el ajuste. Inténtalo otra vez."
        }
    } }
    fun setThemePreference(value: ThemePreference) = persist { settingsRepository.setThemePreference(value) }
    fun setFiscalMonthlySummaryEnabled(value: Boolean) = persist { settingsRepository.setFiscalMonthlySummaryEnabled(value) }
    fun setShareWeeklyReminderEnabled(value: Boolean) = persist { settingsRepository.setShareWeeklyReminderEnabled(value) }
    fun setDebugPremium(value: Boolean) = persist { settingsRepository.setDebugPremium(value) }
}
