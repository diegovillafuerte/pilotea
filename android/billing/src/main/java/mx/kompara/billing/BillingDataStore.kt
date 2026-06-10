package mx.kompara.billing

import javax.inject.Qualifier

/** Qualifier for the billing-scoped preferences DataStore (separate file from settings/auth). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingDataStore
