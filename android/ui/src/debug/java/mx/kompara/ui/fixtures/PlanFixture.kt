package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import mx.kompara.billing.*
import mx.kompara.ui.auth.SignupContent
import mx.kompara.ui.auth.SignupUiState
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.paywall.*
import mx.kompara.ui.referral.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Only debug supplies unsupported promotional/banked facts; all dates derive from the kit JSON. */
class PlanFixture(context: Context) {
    private val fixture = V3Fixture(context)
    private val plan = fixture.raw.getJSONObject("plan")
    private val until = LocalDate.parse(plan.getString("premium_until"))
    private val trial = LocalDate.parse(plan.getString("trial_until"))
    private val purchase = LocalDate.parse(plan.getString("purchase_date"))
    private val grace = purchase.plusMonths(1).plusDays(plan.getInt("grace_days").toLong())
    val bankedDays = ChronoUnit.DAYS.between(purchase, until).toInt()
    private fun LocalDate.millis() = atStartOfDay(fixture.zone).toInstant().toEpochMilli()
    val price = Formatters.formatMxnWhole(plan.getInt("price_monthly").toBigDecimal())
    val product = SubscriptionProduct("kompara_premium", "monthly", null, "debug-only", price, "MXN", plan.getLong("price_monthly") * 1_000_000, false)
    // Uber's confirmed pay per trip that week, as the retired Comparar city view showed it.
    private val perTrip = CompareFixture(context).let { it.statement.value(it.current.money.window.start, "UBER", mx.kompara.metrics.compare.ComparisonMetric.PER_TRIP) }
    val offer = PaywallUiState.Ready(product, PremiumEvidence(listOf("El promedio de tu ciudad", "El detalle de cada semana", "Tu semana y tu mes en PDF"),
        "Por viaje, Uber te pagó", Formatters.formatMxnWhole(requireNotNull(perTrip))), TrialEligibility.ELIGIBLE)
    val reminder = planDate(until.millis(), fixture.zone)
    fun state(frame: String): TuPlanState {
        val status = when (frame) {
            "H2c" -> PlanStatus(PaymentStatus.GRACE, grace.millis(), bankedDays = bankedDays, bankedUntil = grace.plusDays(bankedDays.toLong()).millis())
            "H2d" -> PlanStatus(PaymentStatus.PENDING, grantUntil = until.millis())
            "pending-empty" -> PlanStatus(PaymentStatus.PENDING)
            "canceled" -> PlanStatus(PaymentStatus.CANCELED, until.millis())
            "expired" -> PlanStatus(PaymentStatus.EXPIRED)
            else -> PlanStatus(grantUntil = until.millis(), promotionalTrialUntil = trial.millis(), referralDays = plan.getInt("referral_days"))
        }
        val access = if (frame in listOf("pending-empty", "expired")) Entitlement.Free else Entitlement.Premium(false, until.millis())
        return TuPlanState.from(access, status, price, fixture.zone)
    }
    val referral = ReferralUiState.Ready("KOMP2345", 2, 14, rewardDaysReferrer = plan.getInt("referral_days"), rewardDaysRedeemer = plan.getInt("referral_days"))
}
@Composable fun PlanFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember(context) { PlanFixture(context) }
    when (frame) {
        "H1" -> PaywallContent(fixture.offer, legalAvailable = true)
        "H1b" -> SignupContent(SignupUiState(phoneInput = "5512345678"), premium = true, requestFocus = false)
        "H2b" -> PaywallContent(fixture.offer, legalAvailable = true, purchaseExplanation = true, bankedDays = fixture.bankedDays, reminderDate = fixture.reminder)
        "unavailable" -> PaywallContent(PaywallUiState.PlayUnavailable)
        "no-result" -> PaywallContent(PaywallUiState.NoResult)
        "referral", "redeem" -> ReferralContent(fixture.referral.copy(expanded = frame == "redeem"))
        else -> TuPlanContent(fixture.state(frame))
    }
}
