package mx.kompara.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import mx.kompara.billing.Capability
import mx.kompara.billing.GateState
import mx.kompara.billing.TierGatekeeper
import mx.kompara.ui.paywall.GateFunnel
import javax.inject.Inject

/**
 * Minimal backing for the Comparar tab (B-050). The comparison content itself is built in the NEXT wave;
 * this only exposes the premium [gateState] for [Capability.COMPARE] and the [gateFunnel] so the tab can
 * render a tease-then-gate [mx.kompara.ui.paywall.PaywallGate] today (a documented hook for the future
 * content). When the gate unlocks (premium / debug / kill-switch promo) the screen shows the
 * "coming soon" placeholder unblurred.
 */
@HiltViewModel
class CompararViewModel @Inject constructor(
    tierGatekeeper: TierGatekeeper,
    val gateFunnel: GateFunnel,
) : ViewModel() {

    val gateState: StateFlow<GateState> =
        tierGatekeeper.gateFor(Capability.COMPARE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState.LOCKED)
}
