package mx.kompara.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/**
 * Backs the home screen. Reads settings reactively from [SettingsRepository] (in `:data`).
 *
 * Note: `:ui` deliberately depends only on `:data` and `:metrics`, never on `:capture`
 * internals — the capture service talks to the persistence layer, and the UI reads the same
 * layer, so the two stay decoupled (acceptance criterion: `:ui` cannot depend on `:capture`).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: Flow<Settings> = settingsRepository.settings
}
