package mx.kompara.ui.stats

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.billing.EntitlementRepository
import mx.kompara.data.db.dao.LocalDataDao
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.auth.SessionState
import mx.kompara.sync.privacy.UploadPrivacy
import java.io.File
import javax.inject.Inject

class LocalHistoryEraser @Inject constructor(
    private val localData: LocalDataDao,
    private val settings: SettingsRepository,
    private val reader: ReaderReadinessRepository,
    private val uploads: UploadPrivacy,
    @ApplicationContext private val context: Context,
) {
    suspend fun erase() {
        uploads.revoke(deleting = true)
        reader.forgetHistory()
        localData.eraseHistory()
        settings.selectWeekStart(null)
        withContext(Dispatchers.IO) {
            for (directory in listOf("share_cards", "fiscal_reports")) {
                val file = File(context.cacheDir, directory)
                check(!file.exists() || file.deleteRecursively()) { "Could not erase export" }
            }
        }
    }
}

data class TusDatosState(
    val loaded: Boolean = false, val telemetry: Boolean = true, val contribution: Boolean = false,
    val signedIn: Boolean = false, val playSubscription: Boolean = false,
    val busy: Boolean = false, val result: String? = null, val error: String? = null,
    val contributionAvailable: Boolean = false,
)

fun deletionBody(playSubscription: Boolean): String =
    "Se borran tu cuenta, totales y resúmenes. Tu\u00a0historial queda en este teléfono." +
        if (playSubscription) "\nSi tienes una suscripción, cancélala en Google Play; borrar la cuenta no la cancela." else ""

@HiltViewModel
class TusDatosViewModel @Inject constructor(
    private val auth: AuthRepository,
    settings: SettingsRepository,
    private val uploads: UploadPrivacy,
    private val eraser: LocalHistoryEraser,
    billing: EntitlementRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val action = combine(busy, error, saved.getStateFlow<String?>("dataResult", null)) { b, e, r -> Triple(b, e, r) }
    private val refresh = MutableStateFlow(0)
    fun retry() { refresh.value++ }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest { combine(settings.settings, auth.sessionState, billing.hasPlaySubscription, action) { prefs, session, play, operation ->
        TusDatosState(true, prefs.telemetryEnabled, prefs.shareAggregates, session is SessionState.Authenticated,
            play, operation.first, operation.third, operation.second, uploads.contributionAvailable)
    } .catch { e ->
        if (e is CancellationException) throw e
        emit(TusDatosState(error = "No pudimos cargar tus datos. Inténtalo otra vez."))
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TusDatosState())

    private fun run(message: String, operation: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true; error.value = null
        viewModelScope.launch {
            try { operation() } catch (e: Exception) {
                if (e is CancellationException) throw e
                error.value = message
            } finally { busy.value = false }
        }
    }
    fun telemetry(value: Boolean) = run("No pudimos guardar el ajuste. Inténtalo otra vez.") { uploads.telemetry(value) }
    /** J5's second switch, "Sumar mis promedios a mi ciudad": not built until founder decision 1 (plan §8). */
    fun contribution(value: Boolean) = run("No pudimos guardar el ajuste. Inténtalo otra vez.") { uploads.contribution(value) }
    fun deleteAccount() = run("No pudimos eliminarla. Inténtalo otra vez.") {
        check(auth.sessionState.first() is SessionState.Authenticated)
        auth.deleteAccount()
        saved["dataResult"] = "Cuenta eliminada"
    }
    fun eraseLocal() = run("No pudimos borrar los datos. Inténtalo otra vez.") {
        eraser.erase(); saved["dataResult"] = "Datos del teléfono borrados"
    }
    fun clearResult() { saved["dataResult"] = null; error.value = null }
}
