package mx.kompara.ui.stats

/**
 * The Fiscal tab's payment evidence per app. The IMSS rule itself lives in
 * [mx.kompara.metrics.imss.ImssMonth] (v4); this keeps no second rule.
 */
object ImssTracker {

    /** Payment evidence only; no net-as-tax-base, thresholds, coverage or future projection. */
    fun payments(money: mx.kompara.data.accounting.PeriodMoney): List<RecordedAppPayment> =
        (money.contributions + money.paymentOnly).map { it.record }.groupBy { it.app }.toSortedMap(
            compareBy<String> { if (it == "UBER") "0" else it }
        ).map { (app, records) ->
            RecordedAppPayment(app,
                if (records.any { it.payment == null }) null else records.fold(java.math.BigDecimal.ZERO) { total, it -> total + it.payment!! },
                records.any { it.source == mx.kompara.data.accounting.MoneySource.CAPTURED_ESTIMATE },
                records.any { it.paymentBasis == mx.kompara.data.accounting.PaymentBasis.UNCONFIRMED },
                records.filter { !it.coverage.dated }.map { it.coverage })
        }
}

/** An observed payment is not evidence of enrollment or a legally defined income base. */
data class RecordedAppPayment(val app: String, val amount: java.math.BigDecimal?, val estimated: Boolean,
    val unconfirmed: Boolean, val undatedScopes: List<mx.kompara.data.accounting.Coverage>)
