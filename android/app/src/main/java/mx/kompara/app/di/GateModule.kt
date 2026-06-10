package mx.kompara.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import mx.kompara.billing.DebugPremiumSource
import mx.kompara.billing.PaywallConfigSource
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.config.PaywallConfigRepository
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
        DebugPremiumSource { settings.settings.map { it.debugPremium } }

    @Provides
    @Singleton
    fun providePaywallConfigSource(repo: PaywallConfigRepository): PaywallConfigSource =
        PaywallConfigSource { repo.paywallEnabled }
}
