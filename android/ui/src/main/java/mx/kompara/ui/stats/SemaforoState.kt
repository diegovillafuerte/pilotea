package mx.kompara.ui.stats

import mx.kompara.data.db.dao.SuppressedOffer
import mx.kompara.data.service.*
import mx.kompara.data.settings.Settings
import mx.kompara.ui.components.StatusGlyph
import mx.kompara.ui.service.statusGlyph
import mx.kompara.ui.service.statusLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SemaforoApp(val name: String, val detail: String, val glyph: StatusGlyph, val unavailable: Boolean)
enum class SemaforoAction { PERMISSION, BATTERY, RESUME, CAPTURE, SETTINGS }
data class SemaforoState(
    val title: String,
    val glyph: StatusGlyph,
    val detail: String?,
    val consequence: String?,
    val limits: String,
    val apps: List<SemaforoApp>,
    val date: LocalDate,
    val suppressed: List<SuppressedOffer> = emptyList(),
    val suppressionDetail: String? = null,
    val primary: SemaforoAction? = null,
    val canPause: Boolean = false,
    val since: String? = null,
) {
    val primaryLabel get() = when (primary) {
        SemaforoAction.PERMISSION -> "Volver a dar el permiso"
        SemaforoAction.BATTERY -> "Quitar restricción de batería"
        SemaforoAction.RESUME -> "Reanudar el semáforo"
        SemaforoAction.CAPTURE -> "Continuar"
        SemaforoAction.SETTINGS -> "Abrir ajustes"
        null -> null
    }
}

fun semaforoState(reader: ReaderReadiness, settings: Settings, suppressed: List<SuppressedOffer>, zone: ZoneId,
    needsCaptureConsent: Boolean = false): SemaforoState {
    fun clock(at: Long) = Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    fun relative(at: Long): String {
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return when (date) {
            reader.date -> clock(at)
            reader.date.minusDays(1) -> "ayer ${clock(at)}"
            else -> "${date.format(DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("es-MX")))} ${clock(at)}"
        }
    }
    val off = reader.phase == ReaderPhase.OFF
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
        reader.paused -> "Se reanuda mañana"
        reader.permission == false -> "Falta el permiso de Accesibilidad"
        needsCaptureConsent -> "Este teléfono necesita otro permiso"
        off -> "Dejamos de leer tus ofertas"
        reader.phase == ReaderPhase.ATTENTION -> "Tu teléfono puede apagar el semáforo"
        reader.phase == ReaderPhase.PREPARED -> if (hasHistory) "Tu primera oferta de hoy lo confirma" else "Tu primera oferta lo confirma"
        else -> null
    }
    val appRows = reader.apps.map { (key, app) ->
        val name = when (key) { ReaderReadinessReducer.UBER -> "Uber"; ReaderReadinessReducer.DIDI -> "DiDi"; ReaderReadinessReducer.INDRIVE -> "inDrive"; else -> key }
        val currentDate = app.lastReadAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == reader.date } == true
        val (proof, glyph) = when {
            !app.supported -> (if (key == ReaderReadinessReducer.INDRIVE) "Aún no disponible" else "Por ahora no la leemos") to StatusGlyph.OFF
            reader.permission == false -> "Sin permiso, no leemos ofertas" to StatusGlyph.OFF
            off || needsCaptureConsent -> "Sin lectura de ofertas" to StatusGlyph.OFF
            app.verifiedToday || (reader.paused && currentDate) -> "Lectura comprobada · ${clock(app.lastReadAt!!)}" to StatusGlyph.DONE
            reader.paused -> "Sin ofertas leídas hoy" to StatusGlyph.WAITING
            app.lastReadAt != null -> "Última lectura ${relative(app.lastReadAt!!)}" to StatusGlyph.WAITING
            else -> "Esperando una oferta" to StatusGlyph.WAITING
        }
        SemaforoApp(name, proof, glyph, !app.supported || off)
    }
    val records = suppressed.filter { Instant.ofEpochMilli(it.seenAt).atZone(zone).toLocalDate() == reader.date }
        .sortedByDescending { it.seenAt }.takeUnless { reader.paused || off }.orEmpty()
    val latest = records.firstOrNull()
    val reason = latest?.let {
        "${clock(it.seenAt)} · no cupo en la pantalla" + when (it.cause) {
            "NAVIGATION", "NO_ROOM_NAVIGATION" -> " (navegación)"
            "TALL_OFFER", "NO_ROOM_TALL_OFFER" -> " (oferta alta)"
            else -> ""
        }
    }
    return SemaforoState(reader.statusLabel(), reader.statusGlyph(), detail,
        if (reader.permission == false) "Sin él, Kompara no ve tus ofertas." else null,
        ThresholdEditor.pairLabel(LimitDraft.from(settings.effectiveThreshold, settings.preferredMetric)), appRows, reader.date,
        records, reason, primary, !reader.paused && !off && reader.permission == true,
        reader.incident?.takeIf { off }?.let { "desde ${relative(it.firstSeenAt)}" })
}
