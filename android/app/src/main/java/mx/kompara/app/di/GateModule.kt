package mx.kompara.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import mx.kompara.app.BuildConfig
import mx.kompara.billing.DebugPremiumSource
import mx.kompara.billing.PaywallConfigSource
import mx.kompara.billing.VerificationSource
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.config.PaywallConfigRepository
import mx.kompara.sync.verification.VerificationStatusRepository
import javax.inject.Singleton

/**
 * Bridges `:billing`'s tier-gatekeeper seams ([DebugPremiumSource], [PaywallConfigSource]) to their
 * concrete sources (B-050). `:billing` declares both as interfaces so [mx.kompara.billing.TierGatekeeper]
 * never depends on `:data` or the HTTP stack; the app — which depends on all three — supplies the
 * implementations. Mirrors [BillingBackendModule]'s `:sync` → `:billing` bridge.
 *
 *  - [DebugPremiumSource] ← `:data`'s [SettingsRepository.settings] `debugPremium` flag (B-046). It is
 *    additive (only ever unlocks for demos) and OFF in release builds.
 *  - [PaywallConfigSource] ← `:sync`'s [PaywallConfigRepository], the cached remote kill switch which
 *    defaults to gating-ON so a failed fetch never unlocks premium.
 */
@Module
@InstallIn(SingletonComponent::class)
object GateModule {

    @Provides
    @Singleton
    fun provideDebugPremiumSource(settings: SettingsRepository): DebugPremiumSource =
        // The "debugPremium" override is a full gate bypass (GateStates.derive). Its "OFF in release"
        // guarantee must be STRUCTURAL, not merely UI-hidden: a persisted true flag surviving into a
        // release install (or a future unguarded writer) would otherwise silently unlock every premium
        // surface with no purchase. Enforce it here — the same place API_BASE_URL / DevAuthBypass are
        // BuildConfig-gated — so release never reads the flag.
        if (BuildConfig.DEBUG) {
            DebugPremiumSource { settings.settings.map { it.debugPremium } }
        } else {
            DebugPremiumSource { flowOf(false) }
        }

    @Provides
    @Singleton
    fun providePaywallConfigSource(repo: PaywallConfigRepository): PaywallConfigSource =
        PaywallConfigSource { repo.paywallEnabled }

    /**
     * The real import/data-verification source (PR-E — the gate is now LIVE): the cached `GET /v1/me`
     * `verified` flag from [VerificationStatusRepository]. Backed by a DataStore flow (a fast local
     * read, not a network round-trip) so it seeds the gatekeeper's `combine` immediately without
     * stalling the unrelated surfaces; it fails closed (unverified) only when nothing is cached, and is
     * sticky-positive against transient `/v1/me` failures. Now that the import wizard + Comparar entry +
     * share-target are reachable, an unverified premium driver gets a NEEDS_VERIFICATION CTA into the
     * import flow rather than a chicken-and-egg trap. During launch promo (`!paywallEnabled`) the gate
     * bypasses verification entirely, so this only bites once paid enforcement is on.
     */
    @Provides
    @Singleton
    fun provideVerificationSource(repository: VerificationStatusRepository): VerificationSource =
        VerificationSource { repository.verified }
}
