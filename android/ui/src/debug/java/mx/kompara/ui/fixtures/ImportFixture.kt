package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mx.kompara.data.accounting.*
import mx.kompara.data.imports.ImportDraftEntity
import mx.kompara.sync.api.*
import mx.kompara.sync.imports.*
import mx.kompara.ui.imports.*
import java.time.LocalDate

/** No fixture state enters a repository. Amounts come from the read-only kit fixture. */
class ImportFixture(context: Context) {
    val fixture = V3Fixture(context)
    private val raw = fixture.import
    private val metrics = ImportMetrics("2026-09-14", netEarnings = raw.getDouble("paid"), totalTrips = raw.getInt("trips"),
        hoursOnline = raw.getDouble("hours"), platformCommissionPct = raw.getDouble("commission_pct"))
    private fun draft(metrics: ImportMetrics = this.metrics, platform: String = "uber") = ImportDraftEntity("fixture", platform,
        if (platform == "uber") "pdf" else "screenshot", 1, 2,
        ImportResponse(metrics = metrics, dataCompleteness = .9, dryRun = true).retainedJson(), stage = "REVIEW")
    fun frame(id: String): ImportUiState {
        val review = ImportUiState.Review(draft())
        return when(id) {
            "H4" -> ImportUiState.Picking
            "H5" -> review
            "H5b" -> {
                val raw = fixture.raw.getJSONObject("h5b_partial_import")
                val m = raw.getJSONObject("statement")
                val start = LocalDate.parse(raw.getString("uber_week"))
                val captured = fixture.record("partial-uber", fixture.raw.getJSONObject("weeks").getJSONObject(start.toString()).getJSONObject("by_app").getJSONObject("uber"), start.plusDays(3), start.plusDays(6))
                val contribution = PeriodAccounting.contribution(captured, fixture.schedule, fixture.zone)
                ImportUiState.Reconcile(ImportUiState.Review(draft(ImportMetrics(start.toString(), netEarnings = m.getDouble("paid"), totalTrips = m.getInt("trips"), hoursOnline = m.getDouble("hours"))),
                    ImportOverlap(listOf(captured), contribution.runningExpense)))
            }
            // H5c's first-image values are explicitly drawn in the frame; absent in fixture.json.
            // Current production API requires both images, so it uses “Guardada” until it can parse.
            "H5c" -> ImportUiState.Missing(draft(ImportMetrics("2026-10-05", netEarnings = 3100.0, totalTrips = 29), "didi"), parsed = true)
            "missing" -> ImportUiState.Missing(ImportDraftEntity("pending", "didi", "screenshot", 1), false)
            "H5d" -> ImportUiState.Correcting(review, ImportCorrection.from(metrics))
            "H5e" -> ImportUiState.Error(draft().copy(previewJson = null), listOf(ImportFile("Uber · PDF semanal · 14 – 20 sep", "application/pdf", ByteArray((1.2 * 1024 * 1024).toInt()))), "Sin conexión o el servidor no respondió")
            "unreadable" -> ImportUiState.Error(null, emptyList(), "No pudimos leer este archivo", retryable = false)
            "reading" -> ImportUiState.Reading()
            "auth" -> ImportUiState.SignedOut()
            else -> error("Unknown import fixture $id")
        }
    }
}

@Composable fun ImportFixtureHost(frame: String) {
    val context = LocalContext.current
    val fixture = remember { ImportFixture(context) }
    ImportContent(fixture.frame(frame), ImportActions())
}
