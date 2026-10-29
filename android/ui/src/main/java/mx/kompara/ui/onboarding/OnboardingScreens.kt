package mx.kompara.ui.onboarding

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.theme.BarlowSemiCondensed
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType
import mx.kompara.ui.format.Formatters

/** Native insets/IME belong here; OS screens and keyboards are never painted by Kompara. */
@Composable
fun OnboardingFrame(back: (() -> Unit)?, progress: Float? = null, modifier: Modifier = Modifier,
    brand: Boolean = false, title: String? = null, close: Boolean = false, footer: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = KomparaTheme.colors
    CompositionLocalProvider(LocalContentColor provides c.ink) {
        Column(modifier.fillMaxSize().background(c.paper).safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = if (title == null) 8.dp else 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (back != null) IconButton(back, Modifier.size(48.dp)) { DrawnIcon(if (close) R.drawable.v3_close else R.drawable.v3_back, description = if (close) "Cerrar" else "Atrás") }
                else if (brand) Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = c.brand)) { append("K") }; append("ompara")
                }, Modifier.padding(start = 12.dp), style = KomparaType.title.copy(fontFamily = BarlowSemiCondensed, fontWeight = FontWeight.Bold))
                if (title != null) Text(title, style = KomparaType.title)
            }
            Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(2.dp)) {
                if (progress != null) { Box(Modifier.fillMaxSize().background(c.rule)); Box(Modifier.fillMaxWidth(progress).height(2.dp).background(c.ink)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 16.dp), content = content)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), content = footer)
        }
    }
}

@Composable
internal fun OnboardingTextAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Text(text, style = KomparaType.bodyStrong, color = KomparaTheme.colors.ink)
    }
}
@Composable private fun Title(text: String) = Text(text, style = KomparaType.headline)
@Composable private fun Subtitle(text: String) = Text(text, Modifier.padding(top = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
@Composable private fun ErrorLine(error: String?) { if (error != null) Text(error, Modifier.padding(vertical = 8.dp), style = KomparaType.body) }

@Composable
fun PitchScreen(onFinished: () -> Unit, onSignIn: () -> Unit = {}, modifier: Modifier = Modifier,
    autoplay: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    var selected by rememberSaveable { mutableStateOf(DemoOffer.GREEN) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var stopped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoplay, stopped) {
        if (autoplay && !stopped) while (!stopped) {
            delay(1_500)
            val next = DemoPlayback(selected, step, stopped).tick()
            selected = next.selected; step = next.step; stopped = next.stopped
        }
    }
    OnboardingFrame(null, modifier = modifier, brand = true, footer = {
        PrimaryButton("Empezar", onFinished)
        // Restore is not an available server capability. Do not advertise a backup that cannot return.
        OnboardingTextAction("Entrar con WhatsApp", onSignIn)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Antes de aceptar, ve cuánto te deja.")
            Text("Ejemplo del semáforo", Modifier.padding(top = 8.dp), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
            Text("neto: ya restamos gasolina y desgaste", style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
        }
        Spacer(Modifier.height(10.dp))
        DemoHost(selected)
        Column(Modifier.selectableGroup().padding(horizontal = 20.dp)) {
            DemoOffer.entries.forEach { offer ->
                val phrase = when (offer) { DemoOffer.GREEN -> "Arriba de tu meta"; DemoOffer.YELLOW -> "Cumple tu mínimo"; DemoOffer.RED -> "Abajo de tu mínimo"; DemoOffer.UNREAD -> "Sin lectura" }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == offer, role = Role.Tab,
                    onClick = { selected = offer; stopped = true }), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfferMark(when (offer) { DemoOffer.GREEN -> OfferMarkState.ABOVE; DemoOffer.YELLOW -> OfferMarkState.MEETS; DemoOffer.RED -> OfferMarkState.BELOW; DemoOffer.UNREAD -> OfferMarkState.UNKNOWN })
                    Text(when (offer) { DemoOffer.GREEN -> "▲"; DemoOffer.YELLOW -> "◆"; DemoOffer.RED -> "▼"; else -> "" },
                        Modifier.width(14.dp).clearAndSetSemantics {}, style = KomparaType.small,
                        color = when (offer) { DemoOffer.GREEN -> KomparaTheme.colors.verde; DemoOffer.YELLOW -> KomparaTheme.colors.amarillo; DemoOffer.RED -> KomparaTheme.colors.rojo; else -> KomparaTheme.colors.ink })
                    Text(phrase, style = if (selected == offer) KomparaType.bodyStrong else KomparaType.body,
                        color = if (selected == offer) KomparaTheme.colors.ink else KomparaTheme.colors.inkSecondary)
                }
            }
        }
    }
}

/** Inert labelled host illustration. Only the four legend rows change the example. */
@Composable private fun DemoHost(offer: DemoOffer) {
    Box(Modifier.fillMaxWidth().background(Color(0xFFEBEEF4))) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            for (x in listOf(.15f, .4f, .68f, .85f)) drawLine(Color.White, Offset(size.width*x, 0f), Offset(size.width*x, size.height), 8.dp.toPx())
            for (y in listOf(.2f, .48f, .85f)) drawLine(Color.White, Offset(0f, size.height*y), Offset(size.width, size.height*y), 8.dp.toPx())
            drawLine(Color(0xFF333D49), Offset(size.width*.245f, 0f), Offset(size.width*.245f, size.height), 7.dp.toPx())
            drawCircle(Color.White, 8.dp.toPx(), Offset(size.width*.245f, 96.dp.toPx()))
            drawCircle(Color(0xFF2979E9), 5.dp.toPx(), Offset(size.width*.245f, 96.dp.toPx()))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                Regla(offer.model(), Modifier.testTag("demo-regla"))
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    Text("Viaje", Modifier.background(Color(0xFF111110), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 4.dp),
                        style = KomparaType.bodyStrong, color = Color.White)
                    Spacer(Modifier.width(20.dp))
                    Canvas(Modifier.size(32.dp).clearAndSetSemantics {}) {
                        drawCircle(Color(0xFFDDDDDD), style = Stroke(4.dp.toPx()))
                        drawArc(Color(0xFF111110), 45f, 270f, false, style = Stroke(4.dp.toPx()))
                    }
                }
                Text(offer.fare?.let { Formatters.formatMxn(it) } ?: "Sin tarifa", style = KomparaType.periodFigure, color = Color(0xFF111110))
                val km = if (offer.km == 12.0) "12" else "12.3"
                val pickup = if (offer.pickup == 2.0) "2" else "2.3"
                Text("$km km en total ($pickup para recoger) · ${offer.minutes.toInt()} min", Modifier.padding(top = 4.dp), style = KomparaType.meaning, color = Color(0xFF111110))
            }
        }
    }
}

// B-038 release review remains required for the four-line disclosure.
val readerDisclosureLines = listOf(
    "Lee" to "cada oferta con el permiso de accesibilidad de Android: toma tarifa, distancia y tiempo de una captura de pantalla y la borra.",
    "Guarda" to "tus cifras en este teléfono, nunca imágenes ni datos del pasajero.",
    "Manda" to "conteos de lectura (apágalos en Tus datos) y, si tú eliges, totales, tu aporte al promedio, archivos o reportes.",
    "Uber y DiDi" to "no son parte de Kompara y podrían no permitir apps como esta. Kompara nunca toca su app.",
)
@Composable
fun DisclosureScreen(onAccept: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier,
    onBack: () -> Unit = onDecline, error: String? = null) {
    OnboardingFrame(onBack, .5f, modifier, footer = {
        ErrorLine(error); PrimaryButton("Acepto", onAccept); OnboardingTextAction("No acepto", onDecline)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Lo que hace Kompara"); Subtitle("Para mostrarte lo que deja una oferta.")
            Spacer(Modifier.height(20.dp))
            readerDisclosureLines.forEachIndexed { i, (lead, body) ->
                if (i > 0) Rule()
                Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$lead ") }; append(body) },
                    Modifier.padding(vertical = 16.dp), style = KomparaType.body)
            }
        }
    }
}

@Composable
fun AccessibilityGrantScreen(state: PermissionReturnState, onOpenSettings: () -> Unit, onSkip: () -> Unit,
    modifier: Modifier = Modifier, onBack: () -> Unit = onSkip, error: String? = null) {
    OnboardingFrame(onBack, 1f, modifier, footer = {
        ErrorLine(error); PrimaryButton("Abrir ajustes", onOpenSettings, enabled = !state.ready)
        OnboardingTextAction("Ahora no", onSkip)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Enciende Kompara en Accesibilidad")
            Subtitle("Es el permiso que deja a Kompara leer tus ofertas.")
            Spacer(Modifier.height(24.dp))
            NumberedStep(1, "Busca ", "Kompara", " en la lista y tócalo", "Puede estar en «Apps instaladas»\no «Apps descargadas»")
            Spacer(Modifier.height(20.dp)); NumberedStep(2, "Activa el interruptor")
            Spacer(Modifier.height(20.dp)); NumberedStep(3, "Toca ", "Permitir", " y regresa aquí")
            Text("Android te va a preguntar esto:", Modifier.padding(top = 24.dp, bottom = 8.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            PermissionIllustration()
            Text("Android lo pregunta igual a toda app de accesibilidad. Kompara lo usa para leer tus ofertas; nunca toca botones ni acepta viajes por ti.",
                Modifier.padding(top = 16.dp), style = KomparaType.body)
            Spacer(Modifier.height(20.dp))
            StatusLine(if (state.ready) "Permiso listo" else if (state.missing) "Semáforo apagado · aún falta el permiso" else "Semáforo apagado",
                if (state.ready) StatusGlyph.DONE else StatusGlyph.OFF, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
@Composable private fun NumberedStep(number: Int, start: String, bold: String = "", end: String = "", detail: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number.", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString { append(start); withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }; append(end) }, style = KomparaType.body)
            if (detail != null) Text(detail, Modifier.padding(top = 4.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        }
    }
}
@Composable private fun PermissionIllustration() {
    val c = KomparaTheme.colors
    Column(Modifier.fillMaxWidth().background(c.paperSecondary, RoundedCornerShape(8.dp)).padding(12.dp)
        .clearAndSetSemantics { role = Role.Image; contentDescription = "EJEMPLO. Android pregunta: ¿Permitir que Kompara tenga control total de tu dispositivo? Rechazar o Permitir. Es una imagen, no un control." }) {
        Text("EJEMPLO", Modifier.background(c.inkSecondary, RoundedCornerShape(3.dp)).padding(horizontal = 6.dp),
            style = KomparaType.smallStrong.copy(fontSize = 11.sp, lineHeight = 16.sp), color = c.paper)
        Column(Modifier.widthIn(max = 262.dp).align(Alignment.CenterHorizontally).background(c.paper, RoundedCornerShape(12.dp)).padding(14.dp)) {
            Text("¿Permitir que Kompara tenga control total de tu dispositivo?", style = KomparaType.body.copy(fontFamily = FontFamily.SansSerif))
            Text("Rechazar     Permitir", Modifier.align(Alignment.End).padding(top = 10.dp), style = KomparaType.small.copy(fontFamily = FontFamily.SansSerif), color = c.inkTertiary)
        }
    }
}

@Composable
fun OemSurvivalScreen(permissionGranted: Boolean, onOpenBattery: () -> Unit, onContinue: () -> Unit,
    modifier: Modifier = Modifier, onBack: () -> Unit = onContinue, recovered: Boolean = false, error: String? = null, working: Boolean = false) {
    OnboardingFrame(onBack, modifier = modifier, footer = {
        ErrorLine(error); PrimaryButton("Abrir ajustes", onOpenBattery); OnboardingTextAction("Ahora no", onContinue)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Que tu teléfono no apague Kompara")
            Spacer(Modifier.height(32.dp))
            NumberedStep(1, "En ", "Aplicaciones", ", busca Kompara y tócalo")
            Spacer(Modifier.height(24.dp)); NumberedStep(2, "Toca ", "Batería")
            Spacer(Modifier.height(24.dp)); NumberedStep(3, "Elige ", "Sin restricciones")
            Spacer(Modifier.height(40.dp))
            StatusLine(if (!permissionGranted) "Semáforo apagado · aún falta el permiso" else if (working) "Semáforo funcionando" else "Semáforo preparado",
                if (!permissionGranted) StatusGlyph.OFF else if (working) StatusGlyph.HEALTHY else StatusGlyph.WAITING)
            if (permissionGranted && !working) Row(Modifier.padding(start = 26.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (recovered) StatusIcon(StatusGlyph.DONE)
                Text(if (recovered) "permiso listo" else "La primera oferta confirmará la lectura", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
            }
        }
    }
}

@Composable fun CaptureConsentScreen(open: () -> Unit, skip: () -> Unit, error: String? = null) {
    OnboardingFrame(skip, footer = { ErrorLine(error); PrimaryButton("Continuar", open); OnboardingTextAction("Ahora no", skip) }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Este teléfono necesita otro permiso")
            Subtitle("Android te pedirá compartir la pantalla para leer tus ofertas. Kompara toma tarifa, distancia y tiempo; no guarda imágenes ni toca botones.")
        }
    }
}
