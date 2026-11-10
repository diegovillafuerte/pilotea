package mx.kompara.ui.fixtures

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import mx.kompara.data.accounting.RunningCostInputs
import mx.kompara.data.accounting.RunningCostMode
import mx.kompara.data.model.City
import mx.kompara.data.service.*
import mx.kompara.data.settings.PlatformThreshold
import mx.kompara.data.settings.PreferredMetric
import mx.kompara.data.settings.RateBasis
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.onboarding.*
import mx.kompara.ui.screens.*
import mx.kompara.ui.service.statusGlyph
import mx.kompara.ui.stats.*
import mx.kompara.ui.theme.KomparaTheme
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * J1–J7 and P1–P4 (WP-F) from the pinned v4 kit fixture (assets/v4/fixture.json), through the production
 * reducers. Debug only; never seeds production data.
 */
class SettingsFixture(context: Context) {
    val raw = JSONObject(context.assets.open("v4/fixture.json").bufferedReader().use { it.readText() })
    val zone: ZoneId = ZoneId.of("America/Mexico_City")
    val date: LocalDate = LocalDate.parse(raw.getString("today"))
    private fun JSONObject.decimal(key: String) = BigDecimal(get(key).toString())
    val inputs = raw.getJSONObject("costs").let {
        RunningCostInputs(RunningCostMode.GAS, it.decimal("wear_per_km"), it.decimal("fuel_price_per_l"), it.decimal("km_per_l"))
    }
    val cost: BigDecimal = requireNotNull(inputs.costPerKm)
    val car: BigDecimal = raw.getJSONObject("car_cost").decimal("weekly")
    /** The 19 Oct driver's limits: by km from `floors`, by hour from the kit's per-hour example. */
    val threshold = PlatformThreshold(raw.getJSONObject("floors").getDouble("verde_min_per_km"),
        raw.getJSONObject("floors_per_hour_example").getDouble("verde_min_per_hour"),
        raw.getJSONObject("floors").getDouble("rojo_below_per_km"), raw.getJSONObject("floors_per_hour_example").getDouble("rojo_below_per_hour"))
    val bruto: JSONObject = raw.getJSONObject("v4").getJSONObject("chip_basis").getJSONObject("bruto_limits_per_km")
    fun at(day: LocalDate, time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    /**
     * J4: Uber read the 14:32 offer, DiDi the 12:25 one (the kit's second-app offer); inDrive is not read. "stopped" is a
     * stored pause, the screen-capture fallback's own stop (below Android 11).
     */
    fun readiness(state: String = "working"): ReaderReadiness {
        val didi = raw.getJSONObject("v4").getJSONObject("today_second_app_offers").keys().asSequence().first { !it.startsWith("_") }
        val phase = when (state) { "off" -> ReaderPhase.OFF; "prepared" -> ReaderPhase.PREPARED; "stopped" -> ReaderPhase.PAUSED; else -> ReaderPhase.WORKING }
        val read = phase == ReaderPhase.WORKING
        return ReaderReadiness(phase, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, phase != ReaderPhase.OFF, false,
            linkedMapOf(ReaderReadinessReducer.UBER to AppReadiness(true, if (read) at(date, "14:32") else null, read),
                ReaderReadinessReducer.DIDI to AppReadiness(true, if (read) at(date, didi) else null, read),
                ReaderReadinessReducer.INDRIVE to AppReadiness(false, null, false)),
            if (phase == ReaderPhase.OFF) ReaderIncident(ReaderIncidentCause.PERMISSION_LOST, at(date.minusDays(1), "20:31")) else null,
            date, 0)
    }

    fun ajustes(): AjustesState = readiness().let { reader ->
        AjustesState(ThresholdEditor.summary(LimitDraft.from(threshold, PreferredMetric.IPK)), costsSummary(cost, car),
            settingsReaderText(reader), reader.statusGlyph(), accountSummary(null, City.entries.single { it.key == raw.getString("city").lowercase() }))
    }

    /** J2 is drawn after raising the mínimo to $6.50; J7 is the staged switch to bruto. */
    fun limits(frame: String): ThresholdsUiState {
        val saved = LimitDraft.from(threshold, PreferredMetric.IPK)
        val initial = LimitDraft.from(PlatformThreshold.DEFAULT, PreferredMetric.IPK)
        val draft = when (frame) {
            "J2" -> saved.edit(LimitField.MINIMUM, "6.50")
            "J7" -> ThresholdEditor.convert(saved, RateBasis.GROSS, cost)
            "hour" -> saved.copy(metric = PreferredMetric.IPH)
            else -> saved
        }
        return ThresholdsUiState(draft, saved, initial, ThresholdEditor.preview(draft, cost), draft.pair, cost = cost,
            switchedFrom = saved.takeIf { frame == "J7" })
    }

    fun costs(frame: String): CostEditorState {
        var editor = CostProfileInputs.from(inputs)
        if (frame == "electric") editor = editor.copy(isEv = true, kwhPer100Km = "18", costPerKwhMxn = "3.50")
        if (frame == "direct") editor = editor.copy(direct = true, directPerKm = Formatters.formatMxn(cost).removePrefix("$"))
        return CostEditorState(editor, editor, typical = false, car = "${Formatters.formatMxnWhole(car)} a la semana")
    }
}

@Composable
fun SettingsFixtureHost(frame: String) {
    val context = LocalContext.current
    val f = remember(context) { SettingsFixture(context) }
    Surface(Modifier.fillMaxSize(), color = KomparaTheme.colors.paper, contentColor = KomparaTheme.colors.ink) {
        when (frame) {
            // The shell draws Ajustes' header; the fixture draws the same one so the frame reads as the kit's.
            "J1" -> androidx.compose.foundation.layout.Column {
                mx.kompara.ui.nav.NavigationHeader("Ajustes", onBack = {}, onSettings = null)
                AjustesContent(state = f.ajustes())
            }
            "J2", "J7", "hour" -> ThresholdsContent(f.limits(frame), { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
            "J3", "electric", "direct" -> CostProfileContent(f.costs(frame), {}, {})
            "J4", "off", "prepared", "stopped" -> SemaforoContent(semaforoState(f.readiness(if (frame == "J4") "working" else frame), f.zone), {}, {}, {})
            "J5", "signed-out" -> TusDatosContent(TusDatosState(loaded = true, signedIn = frame == "J5"), externalDeletion = "https://kompara.mx/borrar")
            "erased" -> TusDatosContent(TusDatosState(loaded = true, signedIn = true, result = "Datos del teléfono borrados"))
            "J6" -> HelpContent(supportAvailable = true)
            "P1" -> PitchScreen({}, autoplay = false)
            "P2" -> DisclosureScreen({}, {})
            "P3", "missing", "granted" -> AccessibilityGrantScreen(PermissionReturnState(ready = frame == "granted", missing = frame == "missing"), {}, {})
            "P4", "battery-first" -> OemSurvivalScreen(true, {}, {}, recovered = frame == "P4")
        }
    }
}
