package mx.kompara.ui.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.kompara.billing.EntitlementRepository
import mx.kompara.sync.referral.ReferralResult
import mx.kompara.sync.referral.Referrals
import javax.inject.Inject

/**
 * Drives the B-056 "Invita y gana" screen ([ReferralUiState]).
 *
 * On creation it resolves the session: signed-out drivers land on [ReferralUiState.SignedOut] (account
 * UI is deferred — techdebt TD-008; same pattern as B-045's import gate), signed-in drivers load their
 * code + stats ([ReferralUiState.Ready]). [redeem] runs the redemption state machine inline on the
 * Ready state: a code can be entered, submitted ([RedeemPhase.Submitting]), and resolves to
 * [RedeemPhase.Success] (with a refreshed grant + reloaded stats) or [RedeemPhase.Error] carrying the
 * backend's exact Spanish message.
 *
 * A [deepLinkCode] (from `kompara://referral?code=X`) is prefilled into the redeem field once the
 * driver is signed in, so a tapped link lands ready-to-redeem.
 *
 * The redeem field/phase live ON the [ReferralUiState.Ready] state so the big code/stats stay visible
 * while redeeming — there's no full-screen takeover for the redemption sub-flow.
 */
@HiltViewModel
class ReferralViewModel @Inject constructor(
    private val referrals: Referrals,
    private val entitlements: EntitlementRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferralUiState>(ReferralUiState.Loading)
    val uiState: StateFlow<ReferralUiState> = _uiState.asStateFlow()

    /** A code passed via deep link (`kompara://referral?code=X`), prefilled once the Ready state loads. */
    private var pendingDeepLinkCode: String? =
        savedStateHandle.get<String>(ARG_CODE)?.trim()?.takeIf { it.isNotEmpty() }

    init {
        load()
    }

    /** (Re)resolve the session and load the code + stats. Called on init and from the signed-out retry. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = ReferralUiState.Loading
            if (!referrals.isSignedIn()) {
                _uiState.value = ReferralUiState.SignedOut
                return@launch
            }
            _uiState.value = when (val res = referrals.getMine()) {
                is ReferralResult.Success -> ReferralUiState.Ready(
                    code = res.value.code,
                    redemptionsCount = res.value.redemptionsCount,
                    premiumDaysEarned = res.value.premiumDaysEarned,
                    redeemInput = pendingDeepLinkCode.orEmpty(),
                )
                is ReferralResult.Failure -> ReferralUiState.LoadError(res.message)
            }
            pendingDeepLinkCode = null
        }
    }

    /** Edit the redeem-code field (clears any prior error). */
    fun onRedeemInputChange(value: String) {
        _uiState.update { state ->
            if (state is ReferralUiState.Ready) {
                state.copy(redeemInput = value, redeem = RedeemPhase.Idle)
            } else {
                state
            }
        }
    }

    /** Submit the entered code for redemption. No-ops unless on a Ready state with a non-blank code. */
    fun redeem() {
        val ready = _uiState.value as? ReferralUiState.Ready ?: return
        if (ready.redeemInput.isBlank() || ready.redeem is RedeemPhase.Submitting) return

        _uiState.value = ready.copy(redeem = RedeemPhase.Submitting)
        viewModelScope.launch {
            when (val res = referrals.redeem(ready.redeemInput)) {
                is ReferralResult.Success -> {
                    // The redemption unlocked grant-based premium — refresh the entitlement so gated
                    // surfaces light up immediately, then reload the code/stats view.
                    runCatching { entitlements.refreshGrant() }
                    val current = _uiState.value
                    if (current is ReferralUiState.Ready) {
                        _uiState.value = current.copy(
                            redeem = RedeemPhase.Success(res.value.grantedDaysRedeemer),
                        )
                    }
                    reloadStats()
                }
                is ReferralResult.Failure -> {
                    val current = _uiState.value
                    if (current is ReferralUiState.Ready) {
                        _uiState.value = current.copy(redeem = RedeemPhase.Error(res.message))
                    }
                }
            }
        }
    }

    /** Reload code + stats after a successful redemption, preserving the redeem phase. */
    private fun reloadStats() {
        viewModelScope.launch {
            val res = referrals.getMine()
            val current = _uiState.value as? ReferralUiState.Ready ?: return@launch
            if (res is ReferralResult.Success) {
                _uiState.value = current.copy(
                    code = res.value.code,
                    redemptionsCount = res.value.redemptionsCount,
                    premiumDaysEarned = res.value.premiumDaysEarned,
                )
            }
        }
    }

    companion object {
        /** Optional deep-link nav argument: the referral code from `kompara://referral?code=X`. */
        const val ARG_CODE = "code"
    }
}
