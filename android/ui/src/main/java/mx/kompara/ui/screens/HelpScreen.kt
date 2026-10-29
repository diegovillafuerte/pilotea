package mx.kompara.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import mx.kompara.ui.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

internal data class HelpAnswer(val id: String, val question: String, val lines: List<String>)
internal val helpAnswers = listOf(
    HelpAnswer("semaforo", "¿Por qué no me sale el semáforo?", listOf(
        "Falta el permiso de Accesibilidad: Ajustes › Semáforo › Volver a dar el permiso.",
        "La batería lo apagó: quita la restricción.",
        "No cupo en la pantalla (navegación, oferta alta): no tienes que hacer nada; queda en Tu día.",
        "Lo pausaste: Reanudar el semáforo en Hoy.")),
    HelpAnswer("colores", "¿Qué significan los colores?", listOf(
        "Verde: arriba de tu meta.", "Amarillo: cumple tu mínimo.", "Rojo: abajo de tu mínimo.",
        "Gris: faltó un dato, como la tarifa (te decimos cuál).")),
    HelpAnswer("lectura", "¿Qué lee Kompara?", listOf(
        "Lee cada oferta con el permiso de accesibilidad de Android: toma tarifa, distancia y tiempo de una captura de pantalla y la borra.",
        "Guarda tus cifras en este teléfono, nunca imágenes ni datos del pasajero.",
        "Manda conteos de lectura (apágalos en Tus datos) y, si tú eliges, totales, tu aporte al promedio, archivos o reportes.")),
    HelpAnswer("apps", "¿Me pueden bloquear Uber o DiDi?", listOf(
        "Uber y DiDi no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.")),
    HelpAnswer("precio", "¿Cuánto cuesta?", listOf(
        "El semáforo, tus límites, tus costos y tus totales son gratis. Consulta el precio de Premium en Tu plan.")),
)

@Composable
fun HelpScreen(modifier: Modifier = Modifier, initialAnswer: String? = null, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var failure by rememberSaveable { mutableStateOf<String?>(null) }
    HelpContent(modifier, initialAnswer, onBack ?: {}, PrivacyLinks.support != null, support = {
        PrivacyLinks.support?.let { if (!PrivacyLinks.open(context, it)) failure = "No pudimos abrir WhatsApp. Inténtalo otra vez." }
    }, failure = failure)
}

@Composable
internal fun HelpContent(modifier: Modifier = Modifier, initialAnswer: String? = null, onBack: () -> Unit = {},
    supportAvailable: Boolean = false, support: () -> Unit = {}, failure: String? = null) {
    var expanded by rememberSaveable { mutableStateOf(initialAnswer?.takeIf { id -> helpAnswers.any { it.id == id } }) }
    ScreenScaffold("Ayuda", modifier, ScreenKind.DRILL, onBack, footer = if (supportAvailable) ({
        PrimaryButton("Escríbenos por WhatsApp", support)
    }) else null) {
        Spacer(Modifier.height(24.dp))
        helpAnswers.forEach { answer ->
            val open = expanded == answer.id
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { expanded = if (open) null else answer.id }
                .semantics(mergeDescendants = true) { stateDescription = if (open) "Abierta" else "Cerrada" }
                .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(answer.question, Modifier.weight(1f), style = KomparaType.body)
                DrawnIcon(R.drawable.v3_chev, Modifier.rotate(if (open) -90f else 90f))
            }
            if (open) Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                answer.lines.forEach { Text(it, style = KomparaType.body, color = KomparaTheme.colors.inkSecondary) }
            }
            Rule()
        }
        failure?.let { InkNotice(it) }
    }
}
