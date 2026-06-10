package mx.kompara.billing.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mx.kompara.billing.AndroidBillingLogger
import mx.kompara.billing.BillingClientFacade
import mx.kompara.billing.BillingDataStore
import mx.kompara.billing.BillingLogger
import mx.kompara.billing.EntitlementCache
import mx.kompara.billing.EntitlementStore
import mx.kompara.billing.PlayBillingClientImpl
import javax.inject.Singleton

/**
 * DI wiring for `:billing`.
 *
 * - [BillingClientFacade] → the real [PlayBillingClientImpl]. It self-reports
 *   [mx.kompara.billing.BillingConnectionResult.Unavailable] when Play isn't present, so the
 *   EntitlementRepository degrades to FREE without needing a separate fake binding here. (Tests
 *   inject [mx.kompara.billing.FakeBillingClient] directly.)
 * - [BillingLogger] → logcat.
 * - [SubscriptionBackend] → [SubscriptionBackend.NONE] by default; the `:app` module replaces this
 *   with a `:sync`-ApiClient-backed implementation (kept out of `:billing` to avoid coupling and
 *   editing shared `:sync` files).
 * - A dedicated preferences DataStore for the last-known entitlement (offline grace).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindBillingLogger(impl: AndroidBillingLogger): BillingLogger

    @Binds
    @Singleton
    abstract fun bindEntitlementCache(impl: EntitlementStore): EntitlementCache

    companion object {
        private const val BILLING_FILE = "kompara_billing"

        @Provides
        @Singleton
        fun provideBillingClientFacade(
            @ApplicationContext context: Context,
            logger: BillingLogger,
        ): BillingClientFacade = PlayBillingClientImpl(context, logger)

        @Provides
        @Singleton
        @BillingDataStore
        fun provideBillingDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                produceFile = {
                    java.io.File(context.filesDir, "datastore/$BILLING_FILE.preferences_pb")
                },
            )
    }
}
