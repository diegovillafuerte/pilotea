package mx.kompara.sync.config

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

/** Qualifier for the paywall-config preferences DataStore (separate from settings/auth). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PaywallConfigDataStore

/**
 * DI wiring for the remote paywall kill switch (B-050). Provides a dedicated preferences DataStore for
 * the cached flag so it's isolated from the settings/auth stores; the repository itself is
 * constructor-injected (`@Inject`) and needs no binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
object PaywallConfigModule {

    private const val PAYWALL_CONFIG_FILE = "kompara_paywall_config"

    @Provides
    @Singleton
    @PaywallConfigDataStore
    fun providePaywallConfigDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { java.io.File(context.filesDir, "datastore/$PAYWALL_CONFIG_FILE.preferences_pb") },
        )
}
