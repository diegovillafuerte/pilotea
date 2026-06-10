package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/**
 * Backs the Ajustes toggles. Currently the month-end IMSS summary switch (B-051); other settings join
 * here as they land. Reactive read so the switch reflects the persisted value, suspending write.
 */
@HiltViewModel
class AjustesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Whether the month-end IMSS summary notification is enabled (B-051; default ON). */
    val fiscalMonthlySummaryEnabled: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.fiscalMonthlySummaryEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setFiscalMonthlySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFiscalMonthlySummaryEnabled(enabled) }
    }

    /** Whether the Monday week-close share reminder is enabled (B-055; default ON). */
    val shareWeeklyReminderEnabled: StateFlow<Boolean> =
        settingsRepository.settings
            .map { it.shareWeeklyReminderEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setShareWeeklyReminderEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShareWeeklyReminderEnabled(enabled) }
    }
}
