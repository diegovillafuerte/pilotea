package mx.kompara.sync.referral

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the B-056 referral seam ([Referrals]) to its live implementation ([ReferralRepository]). The
 * "Invita y gana" ViewModel (in `:ui`) injects [Referrals] so it stays unit-testable with a plain
 * fake; Hilt resolves it to the real repository here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReferralModule {

    @Provides
    @Singleton
    fun provideReferrals(repository: ReferralRepository): Referrals = repository
}
