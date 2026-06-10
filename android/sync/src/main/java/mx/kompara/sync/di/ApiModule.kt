package mx.kompara.sync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import mx.kompara.sync.BuildConfig
import mx.kompara.sync.api.ApiClient
import mx.kompara.sync.api.TokenProvider
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifier for the auth-scoped preferences DataStore (separate from settings). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthDataStore

/** Qualifier for the backend base URL string (BuildConfig.API_BASE_URL). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiBaseUrl

/**
 * DI wiring for the `:sync` HTTP + auth layer.
 *
 * Provides the Ktor [HttpClient] (OkHttp engine + JSON), the base URL, a lenient
 * [Json], the auth [DataStore], and the [TokenProvider]. The token provider
 * reads the persisted token directly from DataStore (rather than from
 * AuthRepository) to keep the dependency graph acyclic — ApiClient depends on
 * the provider, and AuthRepository depends on ApiClient.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    private const val AUTH_FILE = "kompara_auth"
    private const val KEY_SESSION_TOKEN = "session_token"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @AuthDataStore
    fun provideAuthDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { java.io.File(context.filesDir, "datastore/$AUTH_FILE.preferences_pb") },
        )

    @Provides
    @Singleton
    @ApiBaseUrl
    fun provideBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun provideTokenProvider(
        @AuthDataStore dataStore: DataStore<Preferences>,
    ): TokenProvider = TokenProvider {
        dataStore.data.first()[stringPreferencesKey(KEY_SESSION_TOKEN)]
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
    }

    @Provides
    @Singleton
    fun provideApiClient(
        http: HttpClient,
        @ApiBaseUrl baseUrl: String,
        tokenProvider: TokenProvider,
    ): ApiClient = ApiClient(http, baseUrl, tokenProvider)
}
