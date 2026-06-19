package mx.kompara.sync.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.kompara.sync.verification.VerificationStatusRepository
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * [ApiModule.provideSessionInvalidator] (PR-E): a 401-driven session invalidation must clear the cached
 * import/data-verification flag as well as the auth keys — otherwise account A's `verified=true` would
 * leak into account B after a re-auth on the same device (a resale vector). The invalidator clears the
 * verification DataStore directly (it can't depend on the repository — that would be a Hilt cycle).
 */
class SessionInvalidatorTest {

    private lateinit var authFile: File
    private lateinit var verifFile: File

    @Before fun setUp() {
        authFile = File.createTempFile("auth", ".preferences_pb").also { it.delete() }
        verifFile = File.createTempFile("verif", ".preferences_pb").also { it.delete() }
    }

    @After fun tearDown() {
        authFile.delete()
        verifFile.delete()
    }

    @Test
    fun `onSessionExpired clears the auth keys AND the verification cache`() = runTest {
        val authDs: DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { authFile })
        val verifDs: DataStore<Preferences> = PreferenceDataStoreFactory.create(produceFile = { verifFile })

        authDs.edit {
            it[stringPreferencesKey("session_token")] = "a-token"
            it[stringPreferencesKey("driver_profile_json")] = "{}"
        }
        verifDs.edit { it[VerificationStatusRepository.KEY_VERIFIED] = true }

        ApiModule.provideSessionInvalidator(authDs, verifDs).onSessionExpired()

        assertNull(authDs.data.first()[stringPreferencesKey("session_token")])
        assertNull(authDs.data.first()[stringPreferencesKey("driver_profile_json")])
        // The leak this guards against: account A's verified flag must not survive the re-auth.
        assertNull(verifDs.data.first()[VerificationStatusRepository.KEY_VERIFIED])
        assertTrue(verifFile.exists()) // store file remains; only the keys are dropped
    }
}
