package mx.kompara.ui.referral

/**
 * The B-056 "Invita y gana" screen state (rendered by `ReferralScreen`, driven by [ReferralViewModel]).
 *
 * Flow: [Loading] → [SignedOut] (terminal until the driver makes an account) OR [Ready] (code + stats,
 * with the redeem sub-flow living on it) OR [LoadError] (the initial load failed; retryable).
 */
sealed interface ReferralUiState {

    /** Pre-load: session + code not yet resolved. */
    data object Loading : ReferralUiState

    /**
     * No account → no referral code. Account UI is deferred (techdebt TD-008); this state explains
     * that and offers to go back. Copy is static Spanish in strings.xml.
     */
    data object SignedOut : ReferralUiState

    /** The initial code/stats load failed (transport). [message] is the Spanish error; retryable. */
    data class LoadError(val message: String) : ReferralUiState

    /**
     * Signed in: the driver's own code + stats, plus the inline redeem sub-flow.
     *
     * @param code the driver's shareable referral code.
     * @param redemptionsCount how many drivers have redeemed this code.
     * @param premiumDaysEarned total premium days the driver has earned as a referrer.
     * @param redeemInput the current text in the "ingresa el código" field.
     * @param redeem the redemption sub-flow phase.
     */
    data class Ready(
        val code: String,
        val redemptionsCount: Int,
        val premiumDaysEarned: Int,
        val redeemInput: String = "",
        val redeem: RedeemPhase = RedeemPhase.Idle,
    ) : ReferralUiState
}

/** The redemption sub-flow phase, carried on [ReferralUiState.Ready]. */
sealed interface RedeemPhase {
    /** No redemption in progress. */
    data object Idle : RedeemPhase

    /** A redemption request is in flight. */
    data object Submitting : RedeemPhase

    /** Redemption succeeded; [grantedDays] premium days were added to the driver. */
    data class Success(val grantedDays: Int) : RedeemPhase

    /** Redemption failed. [message] is the exact Spanish backend (or transport) error. */
    data class Error(val message: String) : RedeemPhase
}
