package mx.kompara.ui.paywall

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore + Logcat implementation of [GateFunnel] (B-050). Increments a per-(surface, event) integer
 * counter in the shared preferences store and logs the transition. No network, no personal data —
 * mirrors [mx.kompara.ui.onboarding.DataStoreOnboardingFunnel].
 */
@Singleton
class DataStoreGateFunnel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : GateFunnel {

    override suspend fun record(surface: GateSurface, event: GateEvent) {
        val prefKey = intPreferencesKey(GateCounters.key(surface, event))
        var newCount = 0
        dataStore.edit { prefs ->
            newCount = GateCounters.increment(prefs[prefKey])
            prefs[prefKey] = newCount
        }
        Log.i(TAG, GateCounters.logLine(surface, event, newCount))
    }

    private companion object {
        const val TAG = "GateFunnel"
    }
}
