package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.kompara.data.model.City
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.auth.AuthRepository
import mx.kompara.sync.auth.SessionState
import javax.inject.Inject

/** What the account screen needs to render, all from a single source of truth. */
data class AccountUiState(
    /** Read-only WhatsApp phone (E.164) of the signed-in driver; empty while loading. */
    val phone: String = "",
    /** Editable display name. */
    val nameInput: String = "",
    /** Editable benchmark city. */
    val city: City = City.DEFAULT,
    /** A save / delete call is in flight. */
    val busy: Boolean = false,
    /** True after a profile save succeeds (drives the "Guardado" confirmation). */
    val saved: Boolean = false,
    /** Non-null when the last save or delete failed. */
    val error: AccountError? = null,
)

/** What went wrong, mapped to Spanish by the UI. */
enum class AccountError {
    /** PATCH /v1/me failed (network/server). */
    SAVE_FAILED,

    /** DELETE /v1/me failed (network/server). */
    DELETE_FAILED,
}

/**
 * Backs the "Tu cuenta" screen (B-069): shows the signed-in driver's phone, lets them edit name +
 * city (PATCH /v1/me, mirroring the city into settings exactly like the signup profile step), and
 * exposes cerrar-sesión and delete-account actions.
 *
 * Logout and delete both clear local auth via [AuthRepository], which flips [SessionState] so the
 * app root re-gates to the signup flow — no explicit navigation needed here.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = auth.sessionState.first()
            if (session is SessionState.Authenticated) {
                val driver = session.driver
                _state.update {
                    it.copy(
                        phone = driver.phone,
                        nameInput = driver.name.orEmpty(),
                        city = City.fromKey(driver.city) ?: settings.currentCity(),
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _state.update { it.copy(nameInput = value, error = null, saved = false) }
    }

    fun onCityChange(value: City) {
        _state.update { it.copy(city = value, error = null, saved = false) }
    }

    /** Persist name + city to the account (PATCH /v1/me) and mirror the city into settings. */
    fun save() {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null, saved = false) }
        viewModelScope.launch {
            runCatching {
                auth.updateProfile(
                    name = s.nameInput.trim().ifEmpty { null },
                    city = s.city.key,
                )
                settings.setCity(s.city)
            }
                .onSuccess { _state.update { it.copy(busy = false, saved = true) } }
                .onFailure { _state.update { it.copy(busy = false, error = AccountError.SAVE_FAILED) } }
        }
    }

    /** Revoke the session and clear local auth; the root gate then routes to signup. */
    fun logout() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch { auth.logout() }
    }

    /** Permanently delete the account; on success the root gate routes to signup. */
    fun deleteAccount() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { auth.deleteAccount() }
                .onFailure { _state.update { it.copy(busy = false, error = AccountError.DELETE_FAILED) } }
        }
    }
}
