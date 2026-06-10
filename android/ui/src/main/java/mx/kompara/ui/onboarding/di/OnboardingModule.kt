package mx.kompara.ui.onboarding.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.ui.onboarding.DataStoreOnboardingFunnel
import mx.kompara.ui.onboarding.OnboardingFunnel
import javax.inject.Singleton

/**
 * Binds the onboarding layer's abstractions (B-036). [OnboardingFunnel] resolves to the
 * DataStore + Logcat recorder so screens can depend on the interface and tests can swap a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    @Singleton
    abstract fun bindOnboardingFunnel(impl: DataStoreOnboardingFunnel): OnboardingFunnel
}
