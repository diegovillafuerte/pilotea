package mx.kompara.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the preferences [DataStore] that backs [mx.kompara.data.settings.SettingsRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val SETTINGS_FILE = "kompara_settings"

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
        )

    private fun Context.preferencesDataStoreFile(name: String) =
        java.io.File(filesDir, "datastore/$name.preferences_pb")
}
