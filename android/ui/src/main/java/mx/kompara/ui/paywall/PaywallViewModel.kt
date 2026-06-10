package mx.kompara.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import mx.kompara.billing.BillingClientFacade
import mx.kompara.billing.BillingConnectionResult
import mx.kompara.billing.BillingProducts
import mx.kompara.billing.Entitlement
import mx.kompara.billing.EntitlementRepository
import mx.kompara.billing.LaunchResult
import mx.kompara.billing.SubscriptionProduct
import javax.inject.Inject

/**
 * Backs the paywall screen (B-050).
 *
 * On open it queries Play [SubscriptionProduct]s so the price + trial copy are read from
 * [com.android.billingclient.api.ProductDetails] at runtime (never hardcoded). When Play is unavailable
 * (emulator without Play, [mx.kompara.billing.FakeBillingClient] fallback, no Play Store) it surfaces a
 * graceful [PaywallUiState.PlayUnavailable] state instead of a dead button.
 *
 * The trial CTA launches the Play billing flow via [BillingClientFacade.launchBillingFlow]; the
 * resulting purchase arrives asynchronously on the entitlement pipeline (already running app-scoped),
 * so this VM does not itself watch purchases — it just kicks the flow and records the funnel event.
 * "Restaurar compras" re-queries owned purchases via [EntitlementRepository.restore].
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingClientFacade,
    private val entitlementRepository: EntitlementRepository,
    private val funnel: GateFunnel,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading)
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    /** One-shot UI signals (a snackbar message), consumed by the screen. */
    private val _message = MutableStateFlow<PaywallMessage?>(null)
    val message: StateFlow<PaywallMessage?> = _message.asStateFlow()

    init {
        loadOffer()
    }

    /** (Re)query the available offer from Play. Sets [PaywallUiState.PlayUnavailable] when it can't. */
    fun loadOffer() {
        viewModelScope.launch {
            _uiState.value = PaywallUiState.Loading
            when (billing.ensureConnected()) {
                is BillingConnectionResult.Unavailable -> {
                    _uiState.value = PaywallUiState.PlayUnavailable
                    return@launch
                }
                BillingConnectionResult.Connected -> Unit
            }
            val offer = runCatching { billing.queryProductDetails() }
                .getOrDefault(emptyList())
                .pickTrialOrCheapest()
            _uiState.value = if (offer == null) {
                PaywallUiState.PlayUnavailable
            } else {
                PaywallUiState.Ready(
                    formattedPrice = offer.formattedPrice,
                    hasFreeTrial = offer.hasFreeTrial,
                    product = offer,
                )
            }
        }
    }

    /**
     * Launch the Play billing flow for the loaded offer. No-op (with a message) when Play is
     * unavailable or no offer loaded. Records [GateEvent.TRIAL_STARTED] for [surface] when the flow
     * launches.
     */
    fun startTrial(activity: Activity, surface: GateSurface) {
        val ready = _uiState.value as? PaywallUiState.Ready ?: run {
            _message.value = PaywallMessage.PlayUnavailable
            return
        }
        viewModelScope.launch {
            when (val result = billing.launchBillingFlow(activity, ready.product, obfuscatedAccountId = null)) {
                LaunchResult.Launched -> funnel.record(surface, GateEvent.TRIAL_STARTED)
                LaunchResult.AlreadyOwned -> {
                    // Re-sync so the entitlement reflects the already-owned sub immediately.
                    runCatching { entitlementRepository.restore() }
                    _message.value = PaywallMessage.AlreadyPremium
                }
                LaunchResult.UserCanceled -> Unit
                is LaunchResult.Error -> _message.value = PaywallMessage.LaunchFailed
            }
        }
    }

    /** Restore owned purchases (after reinstall / device change). Reports the outcome via [message]. */
    fun restore() {
        viewModelScope.launch {
            val entitlement = runCatching { entitlementRepository.restore() }.getOrNull()
            _message.value = if (entitlement is Entitlement.Premium) {
                PaywallMessage.Restored
            } else {
                PaywallMessage.NothingToRestore
            }
        }
    }

    /** Record that the paywall was opened from [surface] (called once by the screen on entry). */
    fun onOpened(surface: GateSurface) {
        viewModelScope.launch { funnel.record(surface, GateEvent.PAYWALL_OPENED) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** Prefer the trial offer; otherwise the cheapest premium offer Play returned. */
    private fun List<SubscriptionProduct>.pickTrialOrCheapest(): SubscriptionProduct? {
        val premium = filter { it.productId == BillingProducts.PREMIUM_SUBSCRIPTION }
        return premium.firstOrNull { it.hasFreeTrial } ?: premium.minByOrNull { it.priceAmountMicros }
    }
}

/** Render state for the paywall screen. */
sealed interface PaywallUiState {
    /** Querying Play for the offer. */
    data object Loading : PaywallUiState

    /**
     * Offer loaded — price + trial copy come from Play at runtime.
     *
     * @param formattedPrice localized recurring price from Play (e.g. "$79.00").
     * @param hasFreeTrial whether the loaded offer opens with a free-trial phase.
     * @param product the offer to launch the billing flow with.
     */
    data class Ready(
        val formattedPrice: String,
        val hasFreeTrial: Boolean,
        val product: SubscriptionProduct,
    ) : PaywallUiState

    /** Play Billing isn't available on this device/build — show a graceful explanation, no dead CTA. */
    data object PlayUnavailable : PaywallUiState
}

/** One-shot paywall messages surfaced as a snackbar. */
enum class PaywallMessage {
    Restored,
    NothingToRestore,
    AlreadyPremium,
    LaunchFailed,
    PlayUnavailable,
}
