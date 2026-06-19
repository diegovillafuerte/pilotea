package mx.kompara.sync.verification

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the verification-status preferences DataStore (separate from settings/auth/paywall). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VerificationDataStore

/**
 * DI wiring for the import/data-verification status (account-onboarding design §3). Provides a
 * dedicated preferences DataStore for the cached flag (isolated from the other stores) and exposes the
 * repository as the [VerificationSignals] seam the import + auth paths depend on. The repository itself
 * is constructor-injected (`@Inject`); the tier gatekeeper binds to its `verified` flow in `:app`'s
 * GateModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object VerificationModule {

    private const val VERIFICATION_FILE = "kompara_verification"

    @Provides
    @Singleton
    @VerificationDataStore
    fun provideVerificationDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { java.io.File(context.filesDir, "datastore/$VERIFICATION_FILE.preferences_pb") },
        )

    @Provides
    @Singleton
    fun provideVerificationSignals(repository: VerificationStatusRepository): VerificationSignals = repository
}
