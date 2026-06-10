package mx.kompara.billing

import javax.inject.Inject

/**
 * Subscription state gate (Play Billing — android-technical-design.md §0). Empty-but-wired:
 * the reader is free, premium features check [isPremium]. Real Play Billing integration lands
 * in a later task; for now everyone is on the free tier.
 */
class BillingManager @Inject constructor() {
    /** Whether the user has an active premium subscription. Always false until Billing lands. */
    fun isPremium(): Boolean = false
}
