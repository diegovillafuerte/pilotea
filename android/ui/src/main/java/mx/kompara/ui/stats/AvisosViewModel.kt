package mx.kompara.ui.stats

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

enum class NoticePreference(val title: String) {
    READER("Si el semáforo se apaga"), DAY("Tu resumen de hoy"), WEEK("Tu semana, los lunes"),
    IMPORT("Cuando toque traer tu resumen"), MONTH("Tu mes, para el IMSS");
    fun checked(settings: Settings) = when (this) {
        READER -> settings.readerAlertsEnabled; DAY -> settings.dailySummaryEnabled
        WEEK -> settings.shareWeeklyReminderEnabled; IMPORT -> settings.importReminderEnabled
        MONTH -> settings.fiscalMonthlySummaryEnabled
    }
}
data class AvisosState(val settings: Settings = Settings.DEFAULT, val allowed: Boolean = false,
    val loaded: Boolean = false, val error: String? = null,
    val blocked: Set<NoticePreference> = emptySet())

@HiltViewModel
class AvisosViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val allowed = MutableStateFlow(false)
    private val blocked = MutableStateFlow<Set<NoticePreference>>(emptySet())
    private val error = MutableStateFlow<String?>(null)
    private val refresh = MutableStateFlow(0)
    fun retry() { refresh.value++ }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state = refresh.flatMapLatest { combine(settings.settings, allowed, error, blocked) { prefs, system, failure, channelBlocks -> AvisosState(prefs, system, true, failure, channelBlocks) } .catch { e ->
        if (e is CancellationException) throw e
        emit(AvisosState(error = "No pudimos cargar tus avisos. Inténtalo otra vez."))
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvisosState())
    fun refreshSystem() {
        allowed.value = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        blocked.value = NoticePreference.entries.filter { preference ->
            val channel = if (preference == NoticePreference.READER) mx.kompara.ui.notifications.NoticeChannels.READER
                else mx.kompara.ui.notifications.NoticeChannels.SUMMARIES
            manager.getNotificationChannel(channel)?.importance == android.app.NotificationManager.IMPORTANCE_NONE
        }.toSet()
    }
    fun set(preference: NoticePreference, enabled: Boolean) {
        if (!allowed.value || preference in blocked.value) return
        viewModelScope.launch {
            try {
                when (preference) {
                    NoticePreference.READER -> settings.setReaderAlertsEnabled(enabled)
                    NoticePreference.DAY -> settings.setDailySummaryEnabled(enabled)
                    NoticePreference.WEEK -> settings.setShareWeeklyReminderEnabled(enabled)
                    NoticePreference.IMPORT -> settings.setImportReminderEnabled(enabled)
                    NoticePreference.MONTH -> settings.setFiscalMonthlySummaryEnabled(enabled)
                }; error.value = null
            } catch (e: Exception) { if (e is CancellationException) throw e; error.value = "No pudimos guardar el ajuste. Inténtalo otra vez." }
        }
    }
}
