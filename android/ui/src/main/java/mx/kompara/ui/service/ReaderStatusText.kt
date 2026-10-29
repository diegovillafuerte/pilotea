package mx.kompara.ui.service

import mx.kompara.data.service.ReaderPhase
import mx.kompara.data.service.ReaderReadiness
import mx.kompara.ui.components.StatusGlyph

fun ReaderReadiness.statusLabel(): String = when (phase) {
    ReaderPhase.OFF -> "Semáforo apagado"
    ReaderPhase.PREPARED -> "Semáforo preparado"
    ReaderPhase.WORKING -> "Semáforo funcionando"
    ReaderPhase.ATTENTION -> "Batería restringida · aún funciona"
    ReaderPhase.PAUSED -> "Semáforo en pausa"
}
fun ReaderReadiness.statusGlyph(): StatusGlyph = when (phase) {
    ReaderPhase.OFF -> StatusGlyph.OFF
    ReaderPhase.PREPARED -> StatusGlyph.WAITING
    ReaderPhase.WORKING -> StatusGlyph.HEALTHY
    ReaderPhase.ATTENTION -> StatusGlyph.ATTENTION
    ReaderPhase.PAUSED -> StatusGlyph.PAUSED
}
