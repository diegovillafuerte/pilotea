package mx.kompara.metrics.compare

import mx.kompara.data.accounting.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ComparisonEligibilityTest {
    private val week = LocalDate.parse("2026-10-12")
    private val record = IncomeRecord("s1", "UBER", Coverage(week, week.plusDays(6), false),
        MoneySource.SERVER_CONFIRMED_STATEMENT, "5062".toBigDecimal(), null, 34)
    private val proof = ConfirmedStatement(record, "cdmx", "proof-1", setOf(MoneyField.PAYMENT, MoneyField.TRIPS))
    private val cohort = StatementCohort("cdmx", "UBER", week, ComparisonMetric.PER_TRIP,
        ComparisonBasis.DRIVER_PAYMENT_BEFORE_COSTS, MoneySource.SERVER_CONFIRMED_STATEMENT, "cohort-1", true, false,
        1240, "131".toBigDecimal(), mapOf(10 to "95", 50 to "126", 70 to "142", 80 to "151", 90 to "170").mapValues { it.value.toBigDecimal() })
    @Test fun `same certified coverage yields interpolated rank without network or account state`() {
        assertEquals(78, ComparisonEligibility.rank(proof, cohort))
        assertEquals(78, ComparisonEligibility.rank(proof.copy(), cohort.copy()))
    }
    @Test fun `tails retain the bounded percentile convention including known zero pay`() {
        assertEquals(1, ComparisonEligibility.rank(proof.copy(record = record.copy(payment = "0".toBigDecimal())), cohort))
        assertEquals(99, ComparisonEligibility.rank(proof.copy(record = record.copy(payment = "99999".toBigDecimal())), cohort))
    }
    @Test fun `confirmation is per record fields app city source and exact week`() {
        listOf(proof.copy(city = "monterrey"), proof.copy(certificationId = ""), proof.copy(confirmedFields = setOf(MoneyField.PAYMENT)),
            proof.copy(record = record.copy(source = MoneySource.PARSED_STATEMENT)), proof.copy(record = record.copy(app = "DIDI")),
            proof.copy(record = record.copy(coverage = Coverage(week.plusDays(1), week.plusDays(6)))),
            proof.copy(record = record.copy(paymentBasis = PaymentBasis.UNCONFIRMED)), proof.copy(record = record.copy(inProgress = 1)),
            proof.copy(record = record.copy(fieldSources = record.fieldSources + (MoneyField.TRIPS to "different-work"))),
            proof.copy(record = record.copy(lifecycle = MoneyLifecycle.CANCELLED))).forEach { assertNull(ComparisonEligibility.rank(it, cohort)) }
    }
    @Test fun `no synthetic sample or uncertified wrong denominator basis or malformed distribution`() {
        listOf(cohort.copy(synthetic = true), cohort.copy(certificationId = ""), cohort.copy(completeCoverage = false),
            cohort.copy(basis = ComparisonBasis.RUNNING_COST_CONTRIBUTION), cohort.copy(metric = ComparisonMetric.PER_KM),
            cohort.copy(drivers = 19), cohort.copy(quantiles = mapOf(70 to "151".toBigDecimal(), 80 to "142".toBigDecimal())),
            cohort.copy(quantiles = mapOf(50 to "100".toBigDecimal())), cohort.copy(mean = "-1".toBigDecimal())).forEach {
            assertNull(ComparisonEligibility.rank(proof, it))
        }
    }
    @Test fun `null payment and zero trips unavailable while legitimate zero payment remains zero`() {
        assertNull(proof.copy(record = record.copy(payment = null)).value(week, "UBER", ComparisonMetric.PER_TRIP))
        assertNull(proof.copy(record = record.copy(trips = 0)).value(week, "UBER", ComparisonMetric.PER_TRIP))
        assertEquals(0, proof.copy(record = record.copy(payment = "0".toBigDecimal())).value(week, "UBER", ComparisonMetric.PER_TRIP)!!.signum())
    }
    @Test fun `statement hours require own matching coverage and never use captured duration`() {
        assertNull(paymentRate(record.copy(source = MoneySource.CAPTURED_ESTIMATE, minutes = "60".toBigDecimal()), ComparisonMetric.PER_HOUR))
        val hours = proof.copy(record = record.copy(minutes = "1560".toBigDecimal()), confirmedFields = setOf(MoneyField.PAYMENT, MoneyField.HOURS), onlineMinutes = "1560".toBigDecimal())
        assertEquals("194.6923076923076923076923076923077", hours.value(week, "UBER", ComparisonMetric.PER_HOUR)!!.toPlainString())
        assertNull(hours.copy(record = hours.record.copy(fieldSources = record.fieldSources + (MoneyField.HOURS to "reader"))).value(week, "UBER", ComparisonMetric.PER_HOUR))
    }
}
