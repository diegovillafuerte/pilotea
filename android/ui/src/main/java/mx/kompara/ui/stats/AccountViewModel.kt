package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.model.City
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.auth.SessionState
import mx.kompara.ui.format.Formatters
import java.math.BigDecimal
import javax.inject.Inject

data class AccountUiState(
    val loaded: Boolean = false, val phone: String = "", val name: String = "", val city: City = City.DEFAULT,
    val citySource: String? = null, val goal: String = "$8.00/km", val minimum: String = "$6.00/km",
    val busy: Boolean = false, val message: String? = null, val error: String? = null,
    val sheet: String? = null, val pendingCity: City? = null, val nameDraft: String = "",
    // Only supplied by a validated configuration. The fixture is not a live city proposal.
    val suggestedGoal: String? = null,
)
fun cityConfirmation(state: AccountUiState, city: City): String =
    "Te comparamos con ${city.displayName} desde esta semana. Tu meta sigue en ${state.goal}" +
        (state.suggestedGoal?.let { "; allá se sugiere $it." } ?: "; tu mínimo, en ${state.minimum}.")

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val operation = combine(busy, error, message) { b, e, m -> Triple(b, e, m) }
    private val draft = combine(saved.getStateFlow<String?>("profileSheet", null),
        saved.getStateFlow<String?>("profileCity", null), saved.getStateFlow("profileName", "")) { sheet, city, name ->
        Triple(sheet, city?.let(City::fromKey), name)
    }
    private val refresh = MutableStateFlow(0)
    fun retry() { refresh.value++ }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest { combine(auth.sessionState, settings.settings, operation, draft) { session, prefs, action, editor ->
        val driver = (session as? SessionState.Authenticated)?.driver
        val rule = prefs.effectiveThreshold
        val hour = prefs.preferredMetric == mx.kompara.data.settings.PreferredMetric.IPH
        AccountUiState(loaded = true, phone = driver?.phone.orEmpty(), name = driver?.name.orEmpty(), city = prefs.city,
            citySource = if (prefs.cityChosen) "elegida por ti" else if (driver?.city == null) "valor inicial" else null,
            goal = Formatters.formatMxn(BigDecimal.valueOf(if (hour) rule.minPerHourMxn else rule.minPerKmMxn)) + if (hour) "/h" else "/km",
            minimum = Formatters.formatMxn(BigDecimal.valueOf(if (hour) rule.redPerHourMxn else rule.redPerKmMxn)) + if (hour) "/h" else "/km",
            busy = action.first, error = action.second, message = action.third, sheet = editor.first,
            pendingCity = editor.second, nameDraft = editor.third)
    } .catch { e ->
        if (e is CancellationException) throw e
        emit(AccountUiState(error = "No pudimos cargar tu perfil. Inténtalo otra vez."))
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun open(sheet: String) { if (!busy.value) { saved["profileSheet"] = sheet; saved["profileName"] = state.value.name; message.value = null } }
    fun close() { if (!busy.value) { saved["profileSheet"] = null; saved["profileCity"] = null } }
    fun chooseCity(city: City?) { if (!busy.value) saved["profileCity"] = city?.key }
    fun name(value: String) { saved["profileName"] = value }
    private fun run(failure: String, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null; message.value = null
        viewModelScope.launch {
            try { action(); saved["profileSheet"] = null; saved["profileCity"] = null }
            catch (e: Exception) { if (e is CancellationException) throw e; error.value = failure; saved["profileCity"] = null; if (saved.get<String>("profileSheet") == "logout") saved["profileSheet"] = null }
            finally { busy.value = false }
        }
    }
    fun confirmCity() {
        val city = state.value.pendingCity ?: return
        run("No pudimos guardar tu ciudad. Inténtalo otra vez.") {
            if (auth.sessionState.first() is SessionState.Authenticated) auth.updateProfile(city = city.key)
            settings.setCity(city) // Never writes either saved threshold or selected unit.
            message.value = "Ciudad cambiada a ${city.displayName}"
        }
    }
    fun saveName() = run("No pudimos guardar tu nombre. Inténtalo otra vez.") {
        val name = state.value.nameDraft.trim()
        require(name.length in 1..100)
        auth.updateProfile(name = name)
        message.value = "Nombre guardado"
    }
    fun logout() = run("No pudimos cerrar sesión. Inténtalo otra vez.") {
        auth.logout(); message.value = "Sesión cerrada"
    }
}
