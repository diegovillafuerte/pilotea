package mx.kompara.sync.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
import mx.kompara.sync.api.DeviceIdProvider
import mx.kompara.sync.api.SessionInvalidator
import mx.kompara.sync.api.TokenProvider
import java.util.UUID
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

/** Qualifier for the debug-only offline login bypass flag (BuildConfig.DEBUG). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DevAuthBypass

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

    // MUST match AuthRepository.KEY_DRIVER_JSON so clearing the expired session also drops the cached
    // profile (both keys gate SessionState.Authenticated).
    private const val KEY_DRIVER_JSON = "driver_profile_json"

    // MUST match AuthRepository.KEY_DEVICE_ID so the device-id provider and the
    // repository share the one stable anonymous install id.
    private const val KEY_DEVICE_ID = "anonymous_device_id"

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

    /**
     * Enables the offline login bypass in [mx.kompara.sync.auth.AuthRepository] for debug builds
     * only (TD-022). Release builds get `false`, so the fixed dev code has no effect in production.
     */
    @Provides
    @Singleton
    @DevAuthBypass
    fun provideDevAuthBypass(): Boolean = BuildConfig.DEBUG

    @Provides
    @Singleton
    fun provideTokenProvider(
        @AuthDataStore dataStore: DataStore<Preferences>,
    ): TokenProvider = TokenProvider {
        dataStore.data.first()[stringPreferencesKey(KEY_SESSION_TOKEN)]
    }

    /**
     * Clears the persisted token + cached driver when a bearer call 401s (expired/revoked session,
     * B-069). Writes the SAME DataStore keys as [mx.kompara.sync.auth.AuthRepository] directly (not via
     * the repository) to keep the graph acyclic — ApiClient depends on this, and AuthRepository depends
     * on ApiClient. Dropping both keys flips [mx.kompara.sync.auth.SessionState] to Anonymous, so the
     * root gate routes to the signup flow. The device id is intentionally retained.
     */
    @Provides
    @Singleton
    fun provideSessionInvalidator(
        @AuthDataStore dataStore: DataStore<Preferences>,
    ): SessionInvalidator = SessionInvalidator {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(KEY_SESSION_TOKEN))
            prefs.remove(stringPreferencesKey(KEY_DRIVER_JSON))
        }
    }

    /**
     * Supplies the stable anonymous device id, reading the SAME DataStore key as
     * [mx.kompara.sync.auth.AuthRepository]. Reads directly from DataStore (not
     * via AuthRepository) to keep the graph acyclic — ApiClient depends on this
     * provider, and AuthRepository depends on ApiClient. Generates + persists the
     * id if it hasn't been created yet (mirrors AuthRepository's first-run logic),
     * so a device-authed call works even before the first AuthRepository.deviceId().
     */
    @Provides
    @Singleton
    fun provideDeviceIdProvider(
        @AuthDataStore dataStore: DataStore<Preferences>,
    ): DeviceIdProvider = DeviceIdProvider {
        val key = stringPreferencesKey(KEY_DEVICE_ID)
        dataStore.data.first()[key] ?: run {
            val generated = UUID.randomUUID().toString()
            dataStore.edit { it[key] = generated }
            generated
        }
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
        deviceIdProvider: DeviceIdProvider,
        sessionInvalidator: SessionInvalidator,
    ): ApiClient = ApiClient(http, baseUrl, tokenProvider, deviceIdProvider, sessionInvalidator)
}
