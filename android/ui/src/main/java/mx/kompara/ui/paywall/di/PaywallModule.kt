package mx.kompara.ui.paywall.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.ui.paywall.DataStoreGateFunnel
import mx.kompara.ui.paywall.GateFunnel
import javax.inject.Singleton

/**
 * Binds the paywall layer's abstractions (B-050). [GateFunnel] resolves to the DataStore + Logcat
 * recorder so the gated surfaces depend on the interface and tests can swap a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PaywallModule {

    @Binds
    @Singleton
    abstract fun bindGateFunnel(impl: DataStoreGateFunnel): GateFunnel
}
