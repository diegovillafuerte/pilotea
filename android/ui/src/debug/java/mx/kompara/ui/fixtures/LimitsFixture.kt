package mx.kompara.ui.fixtures

import android.content.Context
import mx.kompara.data.db.dao.SuppressedOffer
import mx.kompara.data.service.*
import mx.kompara.data.settings.*
import mx.kompara.ui.stats.*
import java.time.LocalDate

/** G2/G4 alternate states use the same raw fixture and production reducers as the screens. */
class LimitsFixture(context: Context) {
    val fixture = V3Fixture(context)
    private val threshold = PlatformThreshold(fixture.raw.getJSONObject("floors").getDouble("verde_min_per_km"), 200.0,
        fixture.raw.getJSONObject("floors").getDouble("rojo_below_per_km"), 150.0)
    fun editor(frame: String): ThresholdsUiState {
        val saved = LimitDraft.from(threshold, PreferredMetric.IPK)
        val draft = when (frame) {
            "G2" -> saved.edit(LimitField.GOAL, "9.50")
            "G2c", "G2d", "G2e" -> saved.edit(LimitField.MINIMUM, "6.50")
            "hour" -> saved.copy(metric = PreferredMetric.IPH)
            else -> saved
        }
        return ThresholdsUiState(draft, saved, saved, ThresholdEditor.preview(draft, fixture.cost), confirmExit = frame == "G2e")
    }
    fun at(day: LocalDate, time: String) = day.atTime(java.time.LocalTime.parse(time)).atZone(fixture.zone).toInstant().toEpochMilli()
    fun readiness(frame: String): ReaderReadiness {
        val date = if (frame == "G4h") fixture.date.plusDays(1) else fixture.date
        val phase = when (frame) { "G4b" -> ReaderPhase.ATTENTION; "G4d" -> ReaderPhase.PAUSED; "G4e", "G4f" -> ReaderPhase.PREPARED; "G4h" -> ReaderPhase.OFF; else -> ReaderPhase.WORKING }
        val proof = when (frame) { "G4e" -> null; "G4f" -> at(date.minusDays(1), "20:10"); "G4d" -> at(date, "12:10"); "G4g" -> at(date, "10:05"); else -> at(date, "14:32") }
        return ReaderReadiness(phase, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, frame == "G4b",
            linkedMapOf(ReaderReadinessReducer.UBER to AppReadiness(true, proof, phase in listOf(ReaderPhase.WORKING, ReaderPhase.ATTENTION)),
                ReaderReadinessReducer.DIDI to AppReadiness(frame != "G4g", null, false), ReaderReadinessReducer.INDRIVE to AppReadiness(false, null, false)),
            if (frame == "G4h") ReaderIncident(ReaderIncidentCause.PERMISSION_LOST, at(date.minusDays(1), "20:31")) else null,
            date, if (frame == "G4c") 1 else 0)
    }
    fun reader(frame: String): SemaforoState = semaforoState(readiness(frame), Settings.DEFAULT.copy(threshold =
        if (frame == "G4i") threshold.copy(redPerKmMxn = 6.5) else threshold),
        if (frame == "G4c") listOf(SuppressedOffer(12, at(fixture.date, "12:10"), "NO_ROOM_NAVIGATION")) else emptyList(), fixture.zone)
}

@androidx.compose.runtime.Composable
fun LimitsFixtureHost(frame: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fixture = androidx.compose.runtime.remember(context) { LimitsFixture(context) }
    if (frame.startsWith("G2") || frame == "hour") mx.kompara.ui.screens.ThresholdsContent(fixture.editor(frame), { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
    else mx.kompara.ui.screens.SemaforoContent(fixture.reader(frame), {}, {}, {}, {}, {}, { _, _ -> },
        snackbar = if (frame == "G4i") androidx.compose.runtime.remember { mx.kompara.ui.components.SaveSnackbarState().apply { saved("Guardado: tu mínimo ahora es $6.50/km") } } else null)
}
