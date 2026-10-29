package mx.kompara.ui.imports

import mx.kompara.sync.api.ImportMetrics
import mx.kompara.sync.api.ImportFile
import mx.kompara.sync.api.ImportResponse
import mx.kompara.sync.imports.retainedJson
import mx.kompara.data.imports.ImportDraftEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ImportFormattingTest {
    private val metrics = ImportMetrics("2026-09-14", netEarnings = 5340.0, totalTrips = 29, hoursOnline = 26.0)
    @Test fun `localized correction keeps decimal cents signed payments and missing km`() {
        val original = ImportCorrection.from(metrics)
        listOf("$5,340.25" to 5340.25, "5340,25" to 5340.25, "−$25.50" to -25.5, "0" to 0.0).forEach { (input, expected) ->
            val corrected = original.copy(payment = input).apply(metrics)
            assertEquals(expected, corrected.netEarnings!!, .0); assertNull(corrected.totalKm)
        }
        listOf("", "1.234", "NaN", "1e8").forEach { assertTrue(runCatching { original.copy(payment = it).apply(metrics) }.isFailure) }
        assertTrue(runCatching { original.copy(hours = "-1").apply(metrics) }.isFailure)
    }
    @Test fun `retained input uses known source and period but never guesses unread dates`() {
        val file = ImportFile("arbitrary-name.pdf", "application/pdf", byteArrayOf(1))
        val draft = ImportDraftEntity("id", "uber", "pdf", 1,
            previewJson = ImportResponse(metrics = metrics, dataCompleteness = .9, dryRun = true).retainedJson())
        fun rows(d: ImportDraftEntity?, files: List<ImportFile> = listOf(file)) = retainedImportRows(ImportUiState.Error(d, files, "error"))
        assertEquals("Uber · PDF semanal · 14 – 20 sep", rows(draft).single().label)
        assertEquals("Uber · PDF semanal", rows(draft.copy(previewJson = null)).single().label)
        assertEquals("arbitrary-name.pdf", rows(null).single().label)
        val didi = rows(draft.copy(platform = "didi", uploadType = "screenshot", previewJson = null), listOf(file, file))
        assertEquals(listOf("DiDi · captura", "DiDi · captura"), didi.map { it.label })
    }
    @Test fun `period and source copy never distributes a boundary week or labels screenshot as PDF`() {
        assertEquals("Se guarda en septiembre como una semana, sin repartir por día.", importConsequence(LocalDate.parse("2026-09-14")))
        assertEquals("Se guarda como una semana, sin repartir entre meses ni por día.", importConsequence(LocalDate.parse("2026-09-28")))
        assertEquals("según tu captura de Uber", importSourceLabel("uber", "screenshot"))
    }
}
