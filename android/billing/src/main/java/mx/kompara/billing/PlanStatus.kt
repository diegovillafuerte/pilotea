package mx.kompara.billing

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Billing facts, separate from the merged access grant. Never infer dates or banked days. */
data class PlanStatus(
    val payment: PaymentStatus = PaymentStatus.NONE,
    val paidUntil: Long? = null,
    val grantUntil: Long? = null,
    val promotionalTrialUntil: Long? = null,
    val referralDays: Int? = null,
    val bankedDays: Int? = null,
    val bankedUntil: Long? = null,
) {
    val hasSubscription get() = payment in setOf(PaymentStatus.ACTIVE, PaymentStatus.CANCELED, PaymentStatus.GRACE, PaymentStatus.HOLD)
}
enum class PaymentStatus { NONE, ACTIVE, CANCELED, GRACE, HOLD, EXPIRED, PENDING }

/** No entitlement can hide missing information or the driver's own totals. */
object FreeDetailWindow {
    fun week(start: LocalDate, today: LocalDate): Boolean {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start >= monday.minusWeeks(3) && start <= monday
    }
    fun month(month: YearMonth, today: LocalDate) = month == YearMonth.from(today)
    fun weekLocked(start: LocalDate, today: LocalDate, available: Boolean, premium: Boolean) = available && !premium && !week(start, today)
    fun monthLocked(month: YearMonth, today: LocalDate, available: Boolean, premium: Boolean) = available && !premium && !month(month, today)
}

/** An explicit server promotion, never the card-required Play trial-7d offer. */
interface PromotionalTrials {
    val supported: Boolean
    suspend fun eligibility(accountId: String): TrialEligibility
    suspend fun start(accountId: String): PromotionalGrant
    companion object {
        // No deployed eligibility/start/banked-days contract. Deliberately not a remote toggle.
        val UNSUPPORTED = object : PromotionalTrials {
            override val supported = false
            override suspend fun eligibility(accountId: String) = TrialEligibility.UNAVAILABLE
            override suspend fun start(accountId: String): PromotionalGrant = error("Promotional trials unavailable")
        }
    }
}
enum class TrialEligibility { ELIGIBLE, USED, ACTIVE, UNAVAILABLE }
data class PromotionalGrant(val expiresAt: Long)

/** Only a regular, single-price monthly base plan can claim 'Google Play cobra hoy'. */
fun List<SubscriptionProduct>.monthlyPurchase(): SubscriptionProduct? = firstOrNull {
    it.productId == BillingProducts.PREMIUM_SUBSCRIPTION && it.basePlanId == BillingProducts.MONTHLY_BASE_PLAN &&
        it.offerId == null && !it.hasFreeTrial && it.billingPeriod == "P1M" && it.priceAmountMicros > 0 &&
        it.formattedPrice.isNotBlank() && it.priceCurrencyCode.isNotBlank()
}
