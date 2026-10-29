package mx.kompara.sync.aggregate

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.auth.AuthRepository
import javax.inject.Singleton

/**
 * Adapts `:data`'s [SettingsRepository] and `:sync`'s [AuthRepository] to the [AggregateConsent] /
 * [SessionGate] seams the [AggregateUploader] depends on, so the uploader stays free of DataStore and
 * auth internals and is trivially unit-testable (B-043).
 */
@Module
@InstallIn(SingletonComponent::class)
object AggregateSyncModule {

    @Provides
    @Singleton
    fun provideAggregateConsent(settings: SettingsRepository): AggregateConsent =
        AggregateConsent { StatementContributionContract.canUpload(settings.isShareAggregatesEnabled()) }

    @Provides
    @Singleton
    fun provideSessionGate(auth: AuthRepository): SessionGate =
        SessionGate { auth.currentToken() != null }
}

/** The legacy endpoint accepts captured/unscoped aggregates. It cannot satisfy the v3 consent. */
object StatementContributionContract {
    fun canUpload(consented: Boolean): Boolean = consented && CERTIFIED_STATEMENT_UPLOAD_SUPPORTED
    private const val CERTIFIED_STATEMENT_UPLOAD_SUPPORTED = false
}
