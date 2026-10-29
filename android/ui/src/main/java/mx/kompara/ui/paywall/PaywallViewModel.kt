package mx.kompara.ui.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.billing.*
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingClientFacade,
    private val entitlementRepository: EntitlementRepository,
    private val funnel: GateFunnel,
    private val sales: PremiumSalesSource,
    private val identity: PremiumIdentity,
    private val trials: PromotionalTrials,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PaywallUiState>(PaywallUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _message = MutableStateFlow<PaywallMessage?>(null)
    val message = _message.asStateFlow()
    val entitlement = entitlementRepository.entitlement
    val plan = entitlementRepository.plan
    private var surface = GateSurface.GENERIC
    private var load: Job? = null
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun loadOffer() {
        if (_busy.value) return
        load?.cancel()
        load = viewModelScope.launch {
            _uiState.value = PaywallUiState.Loading
            try {
                val evidence = sales.check(surface)
                if (!evidence.available) { _uiState.value = PaywallUiState.NoResult; return@launch }
                if (billing.ensureConnected() !is BillingConnectionResult.Connected) {
                    _uiState.value = PaywallUiState.PlayUnavailable; return@launch
                }
                val offer = billing.queryProductDetails().monthlyPurchase()
                if (offer == null) { _uiState.value = PaywallUiState.PlayUnavailable; return@launch }
                val account = identity.accountId()
                val trial = if (trials.supported && account != null) trials.eligibility(account) else TrialEligibility.UNAVAILABLE
                _uiState.value = PaywallUiState.Ready(offer, evidence, trial, account == null && trials.supported)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = PaywallUiState.Error
            }
        }
    }

    /** Check again immediately before sales. Returning from auth only reloads; never starts access. */
    fun purchase(activity: Activity, allowTrial: Boolean = true) {
        if (_busy.value) return
        val ready = _uiState.value as? PaywallUiState.Ready ?: return
        _busy.value = true
        viewModelScope.launch {
            try {
                if (!sales.check(surface).available) { _uiState.value = PaywallUiState.NoResult; return@launch }
                val account = identity.accountId()
                if (allowTrial && (ready.needsAccount || ready.trial == TrialEligibility.ELIGIBLE)) {
                    if (account == null) { _message.value = PaywallMessage.SignIn; return@launch }
                    if (!trials.supported || trials.eligibility(account) != TrialEligibility.ELIGIBLE) {
                        _message.value = PaywallMessage.OfferChanged; return@launch
                    }
                    entitlementRepository.acceptPromotionalTrial(trials.start(account))
                    funnel.record(surface, GateEvent.TRIAL_STARTED)
                    return@launch
                }
                if (plan.value.hasSubscription || plan.value.payment == PaymentStatus.PENDING) {
                    _message.value = PaywallMessage.AlreadyPremium; return@launch
                }
                val fresh = billing.queryProductDetails().monthlyPurchase()
                if (fresh != ready.product) { _message.value = PaywallMessage.OfferChanged; return@launch }
                when (billing.launchBillingFlow(activity, ready.product, account)) {
                    LaunchResult.Launched -> funnel.record(surface, GateEvent.PURCHASE_STARTED)
                    LaunchResult.AlreadyOwned -> { entitlementRepository.restore(); _message.value = PaywallMessage.AlreadyPremium }
                    LaunchResult.UserCanceled -> Unit
                    is LaunchResult.Error -> _message.value = PaywallMessage.LaunchFailed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = PaywallMessage.LaunchFailed
            } finally { _busy.value = false }
        }
    }
    fun restore() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _message.value = if (entitlementRepository.restore() is Entitlement.Premium) PaywallMessage.Restored else PaywallMessage.NothingToRestore
            } catch (e: Exception) { if (e is CancellationException) throw e; _message.value = PaywallMessage.RestoreFailed }
            finally { _busy.value = false }
        }
    }
    fun onOpened(surface: GateSurface) {
        this.surface = surface
        viewModelScope.launch { funnel.record(surface, GateEvent.PAYWALL_OPENED) }
        loadOffer()
    }
    fun consumeMessage() { _message.value = null }
}
sealed interface PaywallUiState {
    data object Loading : PaywallUiState
    data class Ready(val product: SubscriptionProduct, val evidence: PremiumEvidence,
        val trial: TrialEligibility = TrialEligibility.UNAVAILABLE, val needsAccount: Boolean = false) : PaywallUiState {
        val formattedPrice get() = product.formattedPrice
        val promotional get() = trial == TrialEligibility.ELIGIBLE || needsAccount
    }
    data object NoResult : PaywallUiState
    data object PlayUnavailable : PaywallUiState
    data object Error : PaywallUiState
}
enum class PaywallMessage {
    Restored, NothingToRestore, AlreadyPremium, LaunchFailed, RestoreFailed, SignIn, OfferChanged
}
