package mx.kompara.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mx.kompara.billing.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

fun planDate(millis: Long, zone: ZoneId, weekday: Boolean = false): String = Instant.ofEpochMilli(millis).atZone(zone)
    .format(DateTimeFormatter.ofPattern(if (weekday) "EEEE d 'de' MMMM" else "d 'de' MMMM", Locale.forLanguageTag("es-MX")))

data class TuPlanState(
    val title: String,
    val detail: String? = null,
    val payment: PaymentStatus = PaymentStatus.NONE,
    val dates: List<Pair<String, String>> = emptyList(),
    val price: String? = null,
    val subscribe: Boolean = false,
    val manage: Boolean = false,
    val message: String? = null,
    val busy: Boolean = false,
) {
    companion object {
        fun from(access: Entitlement, plan: PlanStatus, price: String?, zone: ZoneId): TuPlanState {
            val premium = access as? Entitlement.Premium
            val dates = buildList {
                if (plan.payment == PaymentStatus.GRACE && premium != null) {
                    plan.paidUntil?.let { add("Periodo de gracia" to planDate(it, zone)) }
                    if (plan.bankedDays != null && plan.bankedUntil != null) add("Después, ${plan.bankedDays} días gratis" to planDate(requireNotNull(plan.bankedUntil), zone))
                }
                if (plan.payment == PaymentStatus.PENDING) premium?.expiresAt?.let {
                    add((if (plan.grantUntil == it) "Tus días gratis" else "Tu acceso actual") to planDate(it, zone))
                }
            }
            val title = when (plan.payment) {
                PaymentStatus.PENDING -> "Pago en proceso"
                PaymentStatus.GRACE, PaymentStatus.HOLD -> "Tu pago necesita atención"
                else -> premium?.expiresAt?.let { "Premium hasta el ${planDate(it, zone, true)}" }
                    ?: if (premium != null) "Tienes Premium" else "Plan gratis"
            }
            val detail = when {
                plan.payment == PaymentStatus.PENDING -> "Google Play está confirmando tu pago"
                plan.payment == PaymentStatus.GRACE || plan.payment == PaymentStatus.HOLD -> "Google Play no pudo cobrar el mes"
                premium != null && plan.promotionalTrialUntil != null && plan.referralDays != null ->
                    "Prueba gratis hasta el ${planDate(requireNotNull(plan.promotionalTrialUntil), zone)} y ${plan.referralDays} días más por invitar."
                plan.payment == PaymentStatus.CANCELED && premium != null -> "Cancelaste la renovación. Conservas el acceso hasta que termine el periodo pagado."
                premium == null && plan.payment == PaymentStatus.EXPIRED -> "Tu acceso a Premium terminó."
                else -> null
            }
            return TuPlanState(title, detail, plan.payment, dates, price,
                subscribe = !plan.hasSubscription && plan.payment != PaymentStatus.PENDING && price != null,
                manage = plan.hasSubscription)
        }
    }
}
@HiltViewModel
class TuPlanViewModel @Inject constructor(
    private val repository: EntitlementRepository,
    private val billing: BillingClientFacade,
    private val zone: ZoneId,
) : ViewModel() {
    private val price = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    val state = combine(repository.entitlement, repository.plan, price, message, busy) { entitlement, plan, cost, notice, working ->
        TuPlanState.from(entitlement, plan, cost, zone).copy(message = notice, busy = working)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TuPlanState("Tu plan"))
    init { refresh() }
    fun refresh() { viewModelScope.launch {
        try {
            repository.refreshGrant()
            price.value = billing.queryProductDetails().monthlyPurchase()?.formattedPrice
        } catch (e: Exception) { if (e is CancellationException) throw e }
    } }
    fun restore() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                message.value = if (repository.restore() is Entitlement.Premium) "Compra restaurada" else "No encontramos una compra activa."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message.value = "No pudimos consultar tu compra. Conservas el acceso guardado."
            } finally { busy.value = false }
        }
    }
}
