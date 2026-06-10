package mx.kompara.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Reads/writes the last-known [Entitlement] for offline grace. An interface so [EntitlementRepository]
 * can be unit-tested with an in-memory fake (no DataStore on the test classpath).
 */
interface EntitlementCache {
    suspend fun read(): Entitlement
    suspend fun write(entitlement: Entitlement)
}

/**
 * Persists the last-known [Entitlement] in DataStore so premium survives an offline launch (grace).
 *
 * Only the minimal derived state is stored (tier + trial + expiry) — never the purchase token. On a
 * cold start with no connectivity, [read] returns the persisted value (subject to the grace window
 * applied by [EntitlementRepository]); a successful Play/backend refresh overwrites it via [write].
 */
class EntitlementStore @Inject constructor(
    @param:BillingDataStore private val dataStore: DataStore<Preferences>,
) : EntitlementCache {
    /** Read the last-known persisted entitlement, defaulting to [Entitlement.Free]. */
    override suspend fun read(): Entitlement {
        val prefs = dataStore.data.first()
        return when (prefs[tierKey]) {
            TIER_PREMIUM -> Entitlement.Premium(
                trial = prefs[trialKey] ?: false,
                expiresAt = prefs[expiresAtKey],
            )
            else -> Entitlement.Free
        }
    }

    /** Overwrite the persisted entitlement. */
    override suspend fun write(entitlement: Entitlement) {
        dataStore.edit { prefs ->
            when (entitlement) {
                is Entitlement.Premium -> {
                    prefs[tierKey] = TIER_PREMIUM
                    prefs[trialKey] = entitlement.trial
                    if (entitlement.expiresAt != null) {
                        prefs[expiresAtKey] = entitlement.expiresAt
                    } else {
                        prefs.remove(expiresAtKey)
                    }
                }
                Entitlement.Free -> {
                    prefs[tierKey] = TIER_FREE
                    prefs.remove(trialKey)
                    prefs.remove(expiresAtKey)
                }
            }
        }
    }

    private val tierKey = stringPreferencesKey(KEY_TIER)
    private val trialKey = booleanPreferencesKey(KEY_TRIAL)
    private val expiresAtKey = longPreferencesKey(KEY_EXPIRES_AT)

    private companion object {
        const val KEY_TIER = "entitlement_tier"
        const val KEY_TRIAL = "entitlement_trial"
        const val KEY_EXPIRES_AT = "entitlement_expires_at"
        const val TIER_PREMIUM = "premium"
        const val TIER_FREE = "free"
    }
}
