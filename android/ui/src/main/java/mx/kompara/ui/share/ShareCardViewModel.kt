package mx.kompara.ui.share

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject

/**
 * Backs the share-card preview screen (B-055): loads the current week's [ShareCardData], renders it
 * to a preview [Bitmap], and exposes the hide-amounts + story/landscape toggles and the "Compartir"
 * action. Toggling hide-amounts re-composes the data (and persists the new default) and re-renders;
 * toggling the variant only re-renders.
 *
 * Bitmap rendering runs off the main thread. The share action writes the PNG to the FileProvider
 * cache, fires the WhatsApp-preferred share sheet, and bumps the anonymous local funnel counter.
 */
@HiltViewModel
class ShareCardViewModel @Inject constructor(
    private val provider: ShareCardProvider,
    private val writer: ShareCardWriter,
    private val sharer: ShareCardSharer,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareCardUiState.LOADING)
    val state: StateFlow<ShareCardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hide = settingsRepository.isShareHideAmounts()
            loadAndRender(hideAmounts = hide, variant = _state.value.variant)
        }
    }

    /** Flip the hide-amounts toggle: persist as the new default, re-compose and re-render. */
    fun setHideAmounts(hide: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShareHideAmounts(hide)
            loadAndRender(hideAmounts = hide, variant = _state.value.variant)
        }
    }

    /** Switch the story/landscape variant: re-render the already-loaded data at the new aspect. */
    fun setVariant(variant: ShareCardVariant) {
        val data = _state.value.data ?: return
        viewModelScope.launch {
            val bitmap = renderOffThread(data, variant)
            _state.value = _state.value.copy(variant = variant, bitmap = bitmap)
        }
    }

    /** Write + share the current card; bumps the funnel counter. */
    fun share() {
        val current = _state.value
        val bitmap = current.bitmap ?: return
        val data = current.data ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { writer.write(bitmap, current.variant) }
            sharer.share(uri, caption = shareCaption(data))
            sharer.recordShare()
        }
    }

    private suspend fun loadAndRender(hideAmounts: Boolean, variant: ShareCardVariant) {
        val data = provider.currentWeekCard(hideAmountsOverride = hideAmounts)
        val bitmap = renderOffThread(data, variant)
        _state.value = ShareCardUiState(
            loading = false,
            data = data,
            variant = variant,
            bitmap = bitmap,
            hideAmounts = hideAmounts,
        )
    }

    private suspend fun renderOffThread(data: ShareCardData, variant: ShareCardVariant): Bitmap =
        withContext(Dispatchers.Default) { ShareCardRenderer.render(data, variant) }

    /** The text body that rides along with the image share (B-055). */
    private fun shareCaption(data: ShareCardData): String =
        listOfNotNull(
            data.periodLabel,
            data.percentileFlex,
            "Hecho con Kompara · descárgala gratis",
        ).joinToString("\n")
}

/** Immutable render state for the share-card preview screen (B-055). */
data class ShareCardUiState(
    val loading: Boolean,
    val data: ShareCardData?,
    val variant: ShareCardVariant,
    val bitmap: Bitmap?,
    val hideAmounts: Boolean,
) {
    companion object {
        val LOADING = ShareCardUiState(
            loading = true,
            data = null,
            variant = ShareCardVariant.STORY,
            bitmap = null,
            hideAmounts = false,
        )
    }
}
