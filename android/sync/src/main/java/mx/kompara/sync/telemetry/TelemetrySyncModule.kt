package mx.kompara.sync.telemetry

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Singleton

/**
 * Adapts `:data`'s [SettingsRepository] to the [TelemetryConsent] seam the
 * [TelemetryUploader] depends on, so the uploader stays free of DataStore and
 * trivially unit-testable (B-034).
 */
@Module
@InstallIn(SingletonComponent::class)
object TelemetrySyncModule {

    @Provides
    @Singleton
    fun provideTelemetryConsent(settings: SettingsRepository): TelemetryConsent =
        TelemetryConsent { settings.isTelemetryEnabled() }
}
