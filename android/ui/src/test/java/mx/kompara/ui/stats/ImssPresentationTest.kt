package mx.kompara.ui.stats

import androidx.test.core.app.ApplicationProvider
import mx.kompara.metrics.imss.ImssApp
import mx.kompara.metrics.imss.ImssMonth
import mx.kompara.metrics.imss.ImssRule
import mx.kompara.metrics.imss.ImssState
import mx.kompara.ui.fixtures.ImssFixture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/** IMSS (plan 4.7): every state and sentence, the kit's figures first. No line certifies coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImssPresentationTest {
    private val kit get() = ImssFixture(ApplicationProvider.getApplicationContext())
    private val october = YearMonth.of(2026, 10)
    private fun month(today: LocalDate, trips: Map<String, Int>, incentives: Map<String, Int> = emptyMap(), month: YearMonth = october) =
        ImssPresentation.of(ImssMonth.of(month, today, trips.mapValues { BigDecimal(it.value) }, incentives))
    private val certifies = mx.kompara.ui.contract.v4.V4Contract.coverage
    private fun ImssPresentation.everything() = listOf(title, rule.text, empty.orEmpty(), combined?.text.orEmpty()) +
        blocks.flatMap { listOf(it.amount, it.parts, it.said?.text.orEmpty()) }

    @Test fun `the fixture's rule is the one the screen states`() {
        assertEquals(ImssRule.Y2026, kit.rule)
        assertEquals(BigDecimal("21938"), ImssRule.Y2026.need)
    }

    @Test fun `M1 the month under way with two apps`() {
        val m1 = ImssPresentation.of(kit.m1())
        assertEquals("Octubre · quedan 12 días", m1.title)
        assertEquals("Una app te da IMSS completo si en el mes te paga \$21,938 o más.", m1.rule.text); assertEquals("\$21,938", m1.rule.strong)
        val (uber, didi) = m1.blocks
        assertEquals(listOf("Uber", "\$14,287", "Viajes \$12,487 · Incentivos \$1,800"), listOf(uber.name, uber.amount, uber.parts))
        assertEquals("A este ritmo cierras en unos \$22,200. Lo alcanzas.", uber.said!!.text); assertEquals("Lo alcanzas.", uber.said!!.strong)
        assertEquals(listOf("DiDi", "\$7,942", "Viajes \$7,942 · Incentivos: agregar"), listOf(didi.name, didi.amount, didi.parts))
        assertEquals("A este ritmo cierras en unos \$13,000. Te faltarían \$9,000.", didi.said!!.text); assertEquals("Te faltarían \$9,000.", didi.said!!.strong)
        assertEquals(listOf(65.1f, 36.2f), m1.blocks.map { Math.round(it.fraction * 1000) / 10f })
        assertEquals("Entre las dos llevas \$22,229. En una sola app ya lo habrías alcanzado.", m1.combined!!.text); assertEquals("\$22,229", m1.combined!!.strong)
        assertNull(m1.empty); assertFalse(m1.vigencia); assertEquals("\$21,938", m1.need)
        assertFalse(m1.everything().any(certifies::containsMatchIn))
    }

    @Test fun `M2 one app over the amount sends the driver to the IMSS`() {
        val m2 = ImssPresentation.of(kit.m2())
        assertEquals("Octubre · quedan 4 días", m2.title)
        val uber = m2.blocks.single()
        assertEquals("\$22,410", uber.amount); assertEquals("Viajes \$20,610 · Incentivos \$1,800", uber.parts); assertEquals(1f, uber.fraction)
        assertEquals("Ya pasaste los \$21,938 con Uber, según lo que leímos y lo que escribiste. Confírmalo en el IMSS.", uber.said!!.text)
        assertEquals("Ya pasaste los \$21,938 con Uber", uber.said!!.strong)
        assertTrue(m2.vigencia); assertNull(m2.combined)
        assertFalse(m2.everything().any(certifies::containsMatchIn))
    }

    @Test fun `over without typed incentives counts only what was read`() {
        val p = month(LocalDate.of(2026, 10, 27), mapOf("DIDI" to 22_000))
        assertEquals("Ya pasaste los \$21,938 con DiDi, según lo que leímos. Confírmalo en el IMSS.", p.blocks.single().said!!.text)
        assertEquals("Viajes \$22,000 · Incentivos: agregar", p.blocks.single().parts)
    }

    @Test fun `a past month is just its name and a shortfall to the peso`() {
        val p = month(LocalDate.of(2026, 10, 19), mapOf("UBER" to 11_487), mapOf("UBER" to 800), YearMonth.of(2026, 9))
        assertEquals("Septiembre", p.title)
        assertEquals("Te faltaron \$9,651.", p.blocks.single().said!!.text); assertEquals("Te faltaron \$9,651.", p.blocks.single().said!!.strong)
        assertEquals("Diciembre 2025", ImssPresentation.title(YearMonth.of(2025, 12), LocalDate.of(2026, 1, 3)))
        assertTrue(month(LocalDate.of(2026, 10, 19), mapOf("UBER" to 23_000), month = YearMonth.of(2026, 9)).vigencia)
    }

    @Test fun `the first six days show the amount without a pace`() {
        val sixth = month(LocalDate.of(2026, 10, 6), mapOf("UBER" to 5_000))
        assertNull(sixth.blocks.single().said); assertEquals("\$5,000", sixth.blocks.single().amount)
        // day 7: 5,000 / 7 × 31 = 22,142.86 → a pace that reaches rounds up: unos $22,200
        assertEquals("A este ritmo cierras en unos \$22,200. Lo alcanzas.", month(LocalDate.of(2026, 10, 7), mapOf("UBER" to 5_000)).blocks.single().said!!.text)
    }

    @Test fun `days left read queda 1 día and último día`() {
        assertEquals("Octubre · queda 1 día", ImssPresentation.title(october, LocalDate.of(2026, 10, 30)))
        assertEquals("Octubre · último día", ImssPresentation.title(october, LocalDate.of(2026, 10, 31)))
        assertEquals("Noviembre · quedan 29 días", ImssPresentation.title(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 1)))
    }

    @Test fun `an empty month says so`() {
        val p = month(LocalDate.of(2026, 10, 19), emptyMap())
        assertEquals("Aún no hay viajes en octubre.", p.empty)
        assertTrue(p.blocks.isEmpty()); assertNull(p.combined); assertFalse(p.vigencia)
        val typedOnly = month(LocalDate.of(2026, 10, 19), emptyMap(), mapOf("UBER" to 500))
        assertNull(typedOnly.empty) // a block is there
        assertEquals("Viajes \$0 · Incentivos \$500", typedOnly.blocks.single().parts); assertNull(typedOnly.blocks.single().said)
        assertEquals("No hubo viajes en septiembre.", month(LocalDate.of(2026, 10, 19), emptyMap(), month = YearMonth.of(2026, 9)).empty)
    }

    @Test fun `an app that drove the month before can take this month's incentives`() {
        val p = ImssPresentation.of(ImssMonth.of(october, LocalDate.of(2026, 10, 19), mapOf("UBER" to BigDecimal(12_487)), emptyMap(),
            lastMonth = setOf("DIDI")))
        val didi = p.blocks[1]
        assertEquals(listOf("DiDi", "\$0", "Viajes \$0 · Incentivos: agregar"), listOf(didi.name, didi.amount, didi.parts))
        assertNull(didi.said); assertNull(p.empty)
        val onlyBefore = ImssPresentation.of(ImssMonth.of(october, LocalDate.of(2026, 10, 19), emptyMap(), emptyMap(), lastMonth = setOf("DIDI")))
        assertNull(onlyBefore.empty); assertEquals(listOf("\$0"), onlyBefore.blocks.map { it.amount })
    }

    @Test fun `a pace near the amount rounds toward its own verdict and a shortfall is never zero`() {
        fun said(pace: Int, state: ImssState, shortBy: Int?) = ImssPresentation.of(ImssMonth(october, LocalDate.of(2026, 10, 19), BigDecimal(21_938),
            listOf(ImssApp("UBER", BigDecimal(13_000), null, BigDecimal(13_000), BigDecimal(pace), state, shortBy?.let(::BigDecimal))))).blocks.single().said!!.text
        assertEquals("A este ritmo cierras en unos \$22,000. Lo alcanzas.", said(21_940, ImssState.WILL_REACH, null))
        assertEquals("A este ritmo cierras en unos \$21,900. Te faltarían \$100.", said(21_900, ImssState.WILL_FALL_SHORT, 38))
    }

    @Test fun `the combined line is claimed only when the sum it prints reaches the amount`() {
        fun combined(indrive: String) = ImssPresentation.of(ImssMonth.of(october, LocalDate.of(2026, 10, 19),
            mapOf("UBER" to BigDecimal("10000.40"), "DIDI" to BigDecimal("10000.40"), "INDRIVE" to BigDecimal(indrive)), emptyMap())).combined
        assertNull(combined("1937.40")) // 21,938.20 raw, but the blocks print 10,000 + 10,000 + 1,937 = 21,937 under "de $21,938"
        assertEquals("Entre las tres llevas \$21,938. En una sola app ya lo habrías alcanzado.", combined("1938.40")!!.text)
    }

    @Test fun `the combined line adds the printed amounts and a closed month says juntaste`() {
        val trips = mapOf("UBER" to BigDecimal("11000.4"), "DIDI" to BigDecimal("10999.4")) // 21,999.8 raw; printed 11,000 + 10,999
        val open = ImssPresentation.of(ImssMonth.of(october, LocalDate.of(2026, 10, 19), trips, emptyMap()))
        assertEquals("Entre las dos llevas \$21,999. En una sola app ya lo habrías alcanzado.", open.combined!!.text)
        val closed = ImssPresentation.of(ImssMonth.of(YearMonth.of(2026, 9), LocalDate.of(2026, 10, 19), trips, emptyMap()))
        assertEquals("Entre las dos juntaste \$21,999. En una sola app ya lo habrías alcanzado.", closed.combined!!.text)
    }

    @Test fun `three apps under the amount that together reach it`() {
        val p = month(LocalDate.of(2026, 10, 19), mapOf("UBER" to 9_000, "DIDI" to 8_000, "INDRIVE" to 6_000))
        assertEquals(listOf("Uber", "DiDi", "inDrive"), p.blocks.map { it.name })
        assertEquals("Entre las tres llevas \$23,000. En una sola app ya lo habrías alcanzado.", p.combined!!.text)
    }

    @Test fun `no combined line once one app is over or when the sum falls short`() {
        assertNull(month(LocalDate.of(2026, 10, 19), mapOf("UBER" to 22_000, "DIDI" to 3_000)).combined)
        assertNull(month(LocalDate.of(2026, 10, 19), mapOf("UBER" to 9_000, "DIDI" to 3_000)).combined)
    }

    @Test fun `M3 states the rule's numbers once`() {
        assertEquals(listOf("Si una app te paga \$21,938 o más en el mes, calificas para la cobertura completa del IMSS con esa app.",
            "Cuentan tus viajes, tus incentivos y tus bonos por referidos. Las propinas no.",
            "Cada app cuenta por separado y cada mes empieza de cero."), ImssRuleText.lines(ImssRule.Y2026).map { it.text })
        assertEquals("De dónde sale la cifra. A lo que te paga la app se le quita 48 % de gastos y luego 16 % de IVA. " +
            "Lo que queda debe llegar a \$9,582, el salario mínimo mensual de CDMX en 2026.", ImssRuleText.source(ImssRule.Y2026).text)
    }
}
