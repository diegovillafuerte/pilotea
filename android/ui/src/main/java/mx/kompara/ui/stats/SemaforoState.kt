package mx.kompara.ui.stats

import mx.kompara.data.service.*
import mx.kompara.ui.components.StatusGlyph
import mx.kompara.ui.format.spanishDate
import mx.kompara.ui.service.statusGlyph
import mx.kompara.ui.service.statusLabel
import java.time.Instant
import java.time.ZoneId

data class SemaforoApp(val name: String, val detail: String, val glyph: StatusGlyph)
enum class SemaforoAction { PERMISSION, BATTERY, CAPTURE, SETTINGS, RESUME }

/** J4: the state, one proof row per app, and when the reader is off its cause and one button. No pause (v4 plan 4.8). */
data class SemaforoState(
    val title: String,
    val glyph: StatusGlyph,
    val detail: String?,
    val consequence: String?,
    val apps: List<SemaforoApp>,
    val primary: SemaforoAction? = null,
    val since: String? = null,
) {
    val primaryLabel get() = when (primary) {
        SemaforoAction.PERMISSION -> "Volver a dar el permiso"
        SemaforoAction.BATTERY -> "Quitar restricción de batería"
        SemaforoAction.CAPTURE -> "Continuar"
        SemaforoAction.SETTINGS -> "Abrir ajustes"
        SemaforoAction.RESUME -> "Encender el semáforo"
        null -> null
    }
}

fun semaforoState(reader: ReaderReadiness, zone: ZoneId, needsCaptureConsent: Boolean = false): SemaforoState {
    fun clock(at: Long) = mx.kompara.ui.format.Formatters.formatClock(at, zone)
    fun relative(at: Long): String {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return when (date) {
            reader.date -> clock(at)
            reader.date.minusDays(1) -> "ayer ${clock(at)}"
            else -> "${date.format(spanishDate("d MMM"))} ${clock(at)}"
        }
    }
    // A pause can still come from the screen-capture fallback's own "Detener" (below Android 11). There is no
    // pausing in the launch set: a stopped reader is off, with its cause and one button that turns it back on.
    val off = reader.phase == ReaderPhase.OFF || reader.paused
    val hasHistory = reader.apps.values.any { it.lastReadAt != null }
    val primary = when {
        reader.paused -> SemaforoAction.RESUME
        reader.permission == false -> SemaforoAction.PERMISSION
        needsCaptureConsent -> SemaforoAction.CAPTURE
        off -> SemaforoAction.SETTINGS
        reader.batteryRestricted -> SemaforoAction.BATTERY
        else -> null
    }
    val detail = when {
        reader.paused -> STOPPED_TITLE
        reader.permission == false -> "Falta el permiso de Accesibilidad"
        needsCaptureConsent -> "Este teléfono necesita otro permiso"
        off -> "Dejamos de leer tus ofertas"
        // Also before the first offer: the battery button needs its cause (prepared on a phone that pauses apps).
        reader.batteryRestricted -> "Tu teléfono puede apagar el semáforo"
        reader.phase == ReaderPhase.PREPARED -> if (hasHistory) "Tu primera oferta de hoy lo confirma" else "Tu primera oferta lo confirma"
        else -> null
    }
    val appRows = reader.apps.map { (key, app) ->
        val name = when (key) { ReaderReadinessReducer.UBER -> "Uber"; ReaderReadinessReducer.DIDI -> "DiDi"; ReaderReadinessReducer.INDRIVE -> "inDrive"; else -> key }
        val (proof, glyph) = when {
            !app.supported -> (if (key == ReaderReadinessReducer.INDRIVE) "Aún no disponible" else "Por ahora no la leemos") to StatusGlyph.OFF
            reader.permission == false -> "Sin permiso, no leemos ofertas" to StatusGlyph.OFF
            off || needsCaptureConsent -> "Sin lectura de ofertas" to StatusGlyph.OFF
            app.verifiedToday -> "Leyó tu última oferta · ${clock(app.lastReadAt!!)}" to StatusGlyph.DONE
            app.lastReadAt != null -> "Última lectura ${relative(app.lastReadAt!!)}" to StatusGlyph.WAITING
            else -> "Esperando una oferta" to StatusGlyph.WAITING
        }
        SemaforoApp(name, proof, glyph)
    }
    return SemaforoState(reader.statusLabel(), reader.statusGlyph(), detail,
        // L3's words for the lost permission and for the driver's own stop; Ganancias says the same.
        when {
            reader.paused -> STOPPED_LINE
            reader.permission == false -> "Android a veces lo apaga, por ejemplo al ahorrar batería. Sin él, Kompara no ve tus ofertas."
            else -> null
        },
        appRows, primary, reader.incident?.takeIf { off && !reader.paused }?.let { mx.kompara.ui.format.Formatters.formatSince(it.firstSeenAt, reader.date, zone) })
}

/** A stored pause (the screen-capture fallback's own stop), on Ganancias (L3) and the Semáforo screen (J4) alike. */
const val STOPPED_TITLE = "Detuviste el semáforo"
const val STOPPED_LINE = "Enciéndelo cuando quieras; mientras tanto no leemos tus ofertas."
