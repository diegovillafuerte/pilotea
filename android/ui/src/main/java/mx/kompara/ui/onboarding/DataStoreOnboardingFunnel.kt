package mx.kompara.ui.onboarding

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore + Logcat implementation of [OnboardingFunnel]. Increments a per-step integer counter in
 * the shared preferences store and logs the transition. No network, no personal data (B-036).
 */
@Singleton
class DataStoreOnboardingFunnel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingFunnel {

    override suspend fun record(step: OnboardingStep) {
        val prefKey = intPreferencesKey(FunnelCounters.key(step))
        var newCount = 0
        dataStore.edit { prefs ->
            newCount = FunnelCounters.increment(prefs[prefKey])
            prefs[prefKey] = newCount
        }
        Log.i(TAG, FunnelCounters.logLine(step, newCount))
    }

    private companion object {
        const val TAG = "OnboardingFunnel"
    }
}
