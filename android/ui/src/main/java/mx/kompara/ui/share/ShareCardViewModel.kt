package mx.kompara.ui.share

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mx.kompara.data.accounting.PeriodMoney
import mx.kompara.data.model.City
import mx.kompara.metrics.compare.ComparisonMetric
import mx.kompara.ui.stats.AppClock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ShareCardViewModel @Inject constructor(
    private val provider: ShareCardProvider,
    private val writer: ShareCardWriter,
    private val sharer: ShareCardSharer,
    private val renderer: ShareCardRenderer,
    private val saved: SavedStateHandle,
    clock: AppClock,
    private val zone: ZoneId,
) : ViewModel() {
    private val asOf = Instant.ofEpochMilli(saved.get<Long>("shareAsOf") ?: clock.nowMs().also { saved["shareAsOf"] = it })
    private var money: PeriodMoney? = null
    private var generation = 0
    private var renderJob: Job? = null
    private val _state = MutableStateFlow(ShareCardUiState())
    val state = _state.asStateFlow()
    init { render(saved["hideAmounts"] ?: true) }

    fun setHideAmounts(hide: Boolean) { saved["hideAmounts"] = hide; render(hide) }
    fun retry() = render(_state.value.hideAmounts)

    private fun render(hide: Boolean) {
        val revision = ++generation
        renderJob?.cancel()
        // Clear the old visible bitmap, caption and accessibility text before suspending.
        _state.value = ShareCardUiState(hideAmounts = hide)
        renderJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { writer.clear() }
                val period = SharePeriod.resolve(saved["weekStart"], saved["month"], asOf.atZone(zone).toLocalDate())
                val snapshot = money ?: provider.load(period, asOf, zone).also { money = it }
                val data = ShareCardComposer.compose(snapshot, period.kind, hide,
                    app = saved["app"] ?: "UBER",
                    city = City.entries.firstOrNull { it.key == saved.get<String>("city") } ?: City.CDMX,
                    metric = saved.get<String>("metric")?.takeIf { it.isNotBlank() }?.let(ComparisonMetric::valueOf) ?: ComparisonMetric.PER_TRIP)
                val bitmap = withContext(Dispatchers.Default) { renderer.render(data) }
                if (revision == generation) _state.value = ShareCardUiState(false, data, bitmap, hide)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == generation) _state.value = ShareCardUiState(loading = false, hideAmounts = hide, error = "No pudimos preparar tu resumen. Inténtalo otra vez.") }
        }
    }

    fun share(preferWhatsApp: Boolean = true) {
        val current = _state.value
        if (!current.canShare || current.sharing) return
        val revision = generation
        _state.value = current.copy(sharing = true, error = null)
        viewModelScope.launch {
            var uri: Uri? = null
            var launched = false
            try {
                // Keep the URI available to finally even when navigation cancels during file IO.
                withContext(NonCancellable + Dispatchers.IO) { uri = writer.write(requireNotNull(current.bitmap)) }
                ensureActive()
                if (revision != generation) return@launch
                launched = sharer.share(requireNotNull(uri), requireNotNull(current.data).caption, preferWhatsApp) != ShareTarget.FAILED
                if (!launched) error("No share target")
                sharer.recordShare()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == generation) _state.value = _state.value.copy(error = "No pudimos compartir. Inténtalo otra vez.") }
            finally {
                if (!launched) withContext(NonCancellable + Dispatchers.IO) { uri?.let(writer::discard) }
                if (revision == generation) _state.value = _state.value.copy(sharing = false)
            }
        }
    }
}

data class ShareCardUiState(
    val loading: Boolean = true,
    val data: ShareCardData? = null,
    val bitmap: Bitmap? = null,
    val hideAmounts: Boolean = true,
    val sharing: Boolean = false,
    val error: String? = null,
) { val canShare get() = !loading && bitmap != null && data != null }
