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
        AggregateConsent { settings.isShareAggregatesEnabled() }

    @Provides
    @Singleton
    fun provideSessionGate(auth: AuthRepository): SessionGate =
        SessionGate { auth.currentToken() != null }
}
