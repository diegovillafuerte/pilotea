package mx.kompara.sync.privacy

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.data.db.dao.LocalDataDao
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.sync.aggregate.AggregateSyncScheduler
import mx.kompara.sync.telemetry.TelemetryScheduler
import javax.inject.Inject
import javax.inject.Singleton

interface AccountUploadControl {
    suspend fun revoke(deleting: Boolean)
    companion object { val NONE = object : AccountUploadControl { override suspend fun revoke(deleting: Boolean) {} } }
}

/** Persist the denial before cancelling work: a restarted worker observes the same decision. */
@Singleton
class UploadPrivacy @Inject constructor(
    private val settings: SettingsRepository,
    private val aggregate: AggregateSyncScheduler,
    private val telemetry: TelemetryScheduler,
    private val localData: LocalDataDao,
) : AccountUploadControl {
    suspend fun contribution(enabled: Boolean) {
        settings.setShareAggregates(enabled)
        if (!enabled) aggregate.cancelPending()
        // S11's certified-source gate stays closed; consent alone never enables legacy uploads.
    }
    suspend fun telemetry(enabled: Boolean) {
        settings.setTelemetryEnabled(enabled)
        if (enabled) telemetry.ensurePeriodic() else telemetry.cancelPending()
    }
    override suspend fun revoke(deleting: Boolean) {
        contribution(false)
        if (deleting) {
            telemetry(false)
            localData.reports()
            localData.counters()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AccountPrivacyModule {
    @Binds abstract fun control(implementation: UploadPrivacy): AccountUploadControl
}
