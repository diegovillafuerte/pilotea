package mx.kompara.ui.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.data.accounting.*
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.metrics.CancellationReceipt
import mx.kompara.metrics.OfferCorrections
import mx.kompara.metrics.PeriodMoneyRepository
import java.time.LocalDate
import javax.inject.Inject

/** One day's money and sheets (Hoy without a date, or a past day): row identity and sheet stack survive recreation. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val snapshots: TodaySnapshots,
    repository: PeriodMoneyRepository,
    settings: SettingsRepository,
    private val corrections: OfferCorrections,
    private val clock: AppClock,
) : ViewModel() {
    private val selectedDay = savedStateHandle.getStateFlow<String?>(ARG_DAY, null)
    val selectedOffer = savedStateHandle.getStateFlow<Long?>(SELECTED_OFFER, savedStateHandle.get<Long>("offerId")?.takeIf { it >= 0 })
    val ledger = savedStateHandle.getStateFlow(LEDGER, savedStateHandle.get<Boolean>("ledger") ?: false)
    val undoReceipt = savedStateHandle.getStateFlow<CancellationReceipt?>(UNDO, null)
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError = _actionError.asStateFlow()

    val uiState: StateFlow<DayDetailUiState> = combine(snapshots.state, selectedDay, settings.settings) { result, date, prefs -> Triple(result, date, prefs) }
        .mapLatest { (result, selected, prefs) ->
            val current = result.getOrNull() ?: return@mapLatest DayDetailUiState.Error(selected.orEmpty())
            val window = current.today.money.window
            val date = selected?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.coerceAtMost(window.start) ?: window.start
            try {
                val snapshot = when (date) {
                    window.start -> current.today
                    window.start.minusDays(1) -> current.yesterday
                    else -> repository.daySnapshot(PeriodWindow(date, date, window.asOf, window.zone))
                }
                DayDetailUiState.Loaded(DayPresentation(snapshot, prefs))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { DayDetailUiState.Error(date.toString()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayDetailUiState.Loading(selectedDay.value.orEmpty()))

    fun openOffer(id: Long) { savedStateHandle[SELECTED_OFFER] = id }
    fun closeOffer() { savedStateHandle[SELECTED_OFFER] = null }
    fun showLedger(show: Boolean) { savedStateHandle[LEDGER] = show }
    fun retry() = snapshots.retry()
    fun dismissMessage() { savedStateHandle[UNDO] = null; _actionError.value = null }
    fun cancelOffer() {
        val id = selectedOffer.value ?: return
        viewModelScope.launch {
            _actionError.value = null
            try {
                val receipt = corrections.cancel(id, clock.nowMs())
                if (receipt == null) _actionError.value = "No pudimos quitar este viaje. Revisa su estado."
                else savedStateHandle[UNDO] = receipt
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _actionError.value = "No pudimos guardar el cambio. Inténtalo otra vez." }
        }
    }
    fun undo() {
        val receipt = undoReceipt.value ?: return
        viewModelScope.launch {
            _actionError.value = null
            try {
                if (!corrections.undo(receipt)) _actionError.value = "El viaje cambió. Revisa su estado."
                savedStateHandle[UNDO] = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _actionError.value = "No pudimos guardar el cambio. Inténtalo otra vez." }
        }
    }
    companion object {
        const val ARG_DAY = "day"
        private const val SELECTED_OFFER = "selectedOffer"
        private const val LEDGER = "showLedger"
        private const val UNDO = "cancellationUndo"
    }
}

sealed interface DayDetailUiState {
    val dayIso: String
    data class Loading(override val dayIso: String) : DayDetailUiState
    data class Error(override val dayIso: String) : DayDetailUiState
    data class Loaded(val day: DayPresentation) : DayDetailUiState {
        override val dayIso: String get() = day.date.toString()
    }
}
