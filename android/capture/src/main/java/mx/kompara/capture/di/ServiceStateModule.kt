package mx.kompara.capture.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.capture.ServiceStateRepository
import mx.kompara.data.service.ServiceStatusProvider
import javax.inject.Singleton

/**
 * Exposes the capture-layer [ServiceStateRepository] to the rest of the app as the `:data`
 * [ServiceStatusProvider] abstraction.
 *
 * This is the seam that lets `:ui` (onboarding accessibility-grant detection, the watchdog) observe
 * reader health without ever depending on `:capture` — they inject [ServiceStatusProvider], which
 * Hilt resolves to the same singleton the accessibility service flips on connect/disconnect.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceStateModule {

    @Binds
    @Singleton
    abstract fun bindServiceStatusProvider(impl: ServiceStateRepository): ServiceStatusProvider
}
