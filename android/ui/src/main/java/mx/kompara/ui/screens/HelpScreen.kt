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
import androidx.compose.ui.unit.sp
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

internal data class HelpAnswer(val id: String, val question: String, val lines: List<String>)
internal val helpAnswers = listOf(
    HelpAnswer("semaforo", "¿Por qué no me sale el semáforo?", listOf(
        "Falta el permiso de Accesibilidad: Ajustes › Semáforo › Volver a dar el permiso.",
        "La batería lo apagó: quita la restricción.")),
    HelpAnswer("colores", "¿Qué significan los colores?", listOf(
        "Verde: arriba de tu meta.", "Amarillo: entre tu mínimo y tu meta.", "Rojo: abajo de tu mínimo.",
        "Sin lectura: faltó la tarifa, la distancia o el tiempo.")),
    HelpAnswer("lectura", "¿Qué lee Kompara?", listOf(
        "Lee cada oferta con el permiso de accesibilidad de Android: toma tarifa, distancia y tiempo de una captura de pantalla y la borra.",
        "Guarda tus cifras en este teléfono, nunca imágenes ni datos del pasajero.",
        "Manda conteos de lectura (apágalos en Tus datos) y, si tú eliges, totales, tu aporte al promedio, archivos o reportes.")),
    HelpAnswer("apps", "¿Me pueden bloquear Uber o DiDi?", listOf(
        "Uber y DiDi no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.")),
    HelpAnswer("precio", "¿Cuánto cuesta?", listOf("Hoy todo lo que ves en Kompara es gratis.")),
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
    // J6: the first question opens unless a link asked for another one.
    var expanded by rememberSaveable { mutableStateOf<String?>(initialAnswer?.takeIf { id -> helpAnswers.any { it.id == id } } ?: helpAnswers.first().id) }
    ScreenScaffold("Ayuda", modifier, ScreenKind.DRILL, onBack, footer = if (supportAvailable) ({
        PrimaryButton("Escríbenos por WhatsApp", support)
    }) else null) {
        Spacer(Modifier.height(8.dp))
        helpAnswers.forEach { answer ->
            val open = expanded == answer.id
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { expanded = if (open) null else answer.id }
                .semantics(mergeDescendants = true) { stateDescription = if (open) "Abierta" else "Cerrada" }
                .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(answer.question, Modifier.weight(1f), style = KomparaType.body)
                DrawnIcon(R.drawable.v3_chev, Modifier.size(20.dp).rotate(if (open) -90f else 90f), tint = KomparaTheme.colors.inkTertiary)
            }
            if (open) Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                answer.lines.forEach { Text(it, style = KomparaType.small.copy(fontSize = 14.5.sp, lineHeight = 21.sp), color = KomparaTheme.colors.inkSecondary) }
            }
            Rule()
        }
        failure?.let { InkNotice(it) }
    }
}
