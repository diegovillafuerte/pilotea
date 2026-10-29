package mx.kompara.metrics.compare

import mx.kompara.data.imports.ImportStatementEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StoredStatementEvidenceTest {
    private val week = LocalDate.parse("2026-10-12")
    private val row = ImportStatementEntity("saved", "UBER", week.toString(), "2026-10-18",
        "5062", 34, null, "24", "SERVER_CONFIRMED_STATEMENT", "pdf", 1,
        "server-proof", "cdmx", "PAYMENT,TRIPS,HOURS")

    @Test fun `only explicit server proof carries confirmed fields and online hours`() {
        val proof = row.confirmedStatement()!!
        assertEquals("server-proof", proof.certificationId)
        assertEquals("cdmx", proof.city)
        assertEquals(1440, proof.onlineMinutes!!.toInt())
        assertEquals(148.8823529, proof.value(week, "UBER", ComparisonMetric.PER_TRIP)!!.toDouble(), .000001)
        assertNull(proof.value(week.minusWeeks(1), "UBER", ComparisonMetric.PER_TRIP))
        assertNull(proof.value(week, "DIDI", ComparisonMetric.PER_TRIP))
        assertNull(row.copy(confirmedFields = "PAYMENT,TRIPS").confirmedStatement()!!.onlineMinutes)
    }

    @Test fun `local imports missing proof and unknown fields cannot acquire certification`() {
        for (bad in listOf(row.copy(source = "PARSED_STATEMENT"), row.copy(source = "USER_CORRECTED_IMPORT"),
            row.copy(certificationId = null), row.copy(certificationId = " "), row.copy(certificationCity = null),
            row.copy(confirmedFields = null), row.copy(confirmedFields = "PAYMENT,FUTURE_FIELD"))) {
            assertNull(bad.confirmedStatement())
        }
        val partial = row.copy(confirmedFields = "PAYMENT").confirmedStatement()!!
        assertNull(partial.value(week, "UBER", ComparisonMetric.PER_TRIP))
        assertNull(partial.value(week, "UBER", ComparisonMetric.PER_HOUR))
    }
}
