package mx.kompara.ui.onboarding

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
    brand: Boolean = false, title: String? = null, close: Boolean = false, contentTop: Dp = 24.dp,
    footer: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = contentTop, bottom = 16.dp), content = content)
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

/** P1: the product in five seconds. The example steps once through its three colours (v4 plan 4.9). */
@Composable
fun PitchScreen(onFinished: () -> Unit, modifier: Modifier = Modifier,
    autoplay: Boolean = ValueAnimator.areAnimatorsEnabled()) {
    var selected by rememberSaveable { mutableStateOf(DemoOffer.GREEN) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var stopped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoplay) {
        if (autoplay) while (!stopped) {
            delay(1_500)
            val next = DemoPlayback(selected, step, stopped).tick()
            selected = next.selected; step = next.step; stopped = next.stopped
        }
    }
    OnboardingFrame(null, modifier = modifier, brand = true, contentTop = 6.dp, footer = { PrimaryButton("Empezar", onFinished) }) {
        Column(Modifier.padding(horizontal = 20.dp)) { Title("Antes de aceptar, ve cuánto te deja.") }
        DemoHost(selected, Modifier.padding(top = 14.dp))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
            listOf(OfferMarkState.ABOVE to "Arriba de tu meta", OfferMarkState.MEETS to "Entre tu mínimo y tu meta",
                OfferMarkState.BELOW to "Abajo de tu mínimo").forEach { (state, phrase) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VerdictCue(state, Modifier.size(15.dp, 14.dp))
                    Text(phrase, style = KomparaType.body)
                }
                Rule()
            }
            Text("El número es neto: lo que te deja por km. Ya restamos gasolina y desgaste.", Modifier.padding(top = 8.dp),
                style = KomparaType.small.copy(fontSize = 14.sp, lineHeight = 20.sp), color = KomparaTheme.colors.inkSecondary)
        }
    }
}

/** The ride app, redrawn without its brand (the kit's `.demo`): inert, always in the host's own light colours. */
@Composable private fun DemoHost(offer: DemoOffer, modifier: Modifier = Modifier) {
    val map = Color(0xFFECEEF3); val road = Color.White; val route = Color(0xFF1B2430)
    val card = Color.White; val ink = Color(0xFF111111)
    Box(modifier.fillMaxWidth().height(262.dp).clipToBounds().background(map)) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            fun dp(v: Int) = v.dp.toPx()
            for ((x, w) in listOf(86 to 9, 196 to 8, 292 to 8)) drawRect(road, Offset(dp(x), 0f), Size(dp(w), size.height))
            for ((y, h) in listOf(30 to 8, 142 to 10)) drawRect(road, Offset(0f, dp(y)), Size(size.width, dp(h)))
            for ((o, s) in listOf(Offset(dp(196), dp(30)) to Size(dp(8), dp(70)), Offset(dp(196), dp(30)) to Size(dp(104), dp(8)))) {
                drawRoundRect(road, Offset(o.x - dp(2), o.y - dp(2)), Size(s.width + dp(4), s.height + dp(4)), CornerRadius(dp(6)))
                drawRoundRect(route, o, s, CornerRadius(dp(4)))
            }
            drawCircle(road, dp(11), Offset(dp(300), dp(34))); drawCircle(route, dp(8), Offset(dp(300), dp(34)))
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 122.dp).fillMaxWidth().height(200.dp)
            .background(card, RoundedCornerShape(22.dp)).border(2.dp, ink, RoundedCornerShape(22.dp))
            .padding(start = 16.dp, top = 14.dp, end = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("Viaje", Modifier.background(ink, RoundedCornerShape(8.dp)).padding(horizontal = 11.dp, vertical = 4.dp),
                    style = KomparaType.bodyStrong.copy(fontFamily = FontFamily.SansSerif, fontSize = 15.sp), color = card)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(46.dp, 44.dp).background(Color(0xFFF1F1F1), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    DrawnIcon(R.drawable.v3_close, Modifier.size(20.dp), tint = ink)
                }
            }
            Text(Formatters.formatMxn(offer.fare), Modifier.offset(y = (-8).dp), style = TextStyle(fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1).sp), color = ink)
            Text("${offer.minutes.toInt()} min · ${offer.km} km", Modifier.offset(y = (-6).dp),
                style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.5.sp, lineHeight = 21.sp), color = Color(0xFF5B5B5B))
        }
        // The chip sits 12 dp above the card's corner, as the overlay places it over a real offer.
        Box(Modifier.padding(start = 10.dp).height(110.dp), contentAlignment = Alignment.BottomStart) {
            Regla(offer.model(), Modifier.testTag("demo-regla"))
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
    OnboardingFrame(onBack, .5f, modifier, contentTop = 20.dp, footer = {
        ErrorLine(error); PrimaryButton("Acepto", onAccept); OnboardingTextAction("No acepto", onDecline)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Lo que hace Kompara"); Subtitle("Para mostrarte lo que deja una oferta.")
            Spacer(Modifier.height(14.dp))
            readerDisclosureLines.forEach { (lead, body) ->
                Rule()
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = KomparaTheme.colors.ink)) { append("$lead ") }; append(body)
                }, Modifier.padding(vertical = 13.dp), style = KomparaType.meaning.copy(lineHeight = 21.sp), color = KomparaTheme.colors.inkSecondary)
            }
        }
    }
}

@Composable
fun AccessibilityGrantScreen(state: PermissionReturnState, onOpenSettings: () -> Unit, onSkip: () -> Unit,
    modifier: Modifier = Modifier, onBack: () -> Unit = onSkip, error: String? = null) {
    OnboardingFrame(onBack, 1f, modifier, contentTop = 20.dp, footer = {
        ErrorLine(error); PrimaryButton("Abrir ajustes", onOpenSettings, enabled = !state.ready)
        OnboardingTextAction("Ahora no", onSkip)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Enciende Kompara en Accesibilidad")
            Subtitle("Es el permiso que deja a Kompara leer tus ofertas.")
            Spacer(Modifier.height(18.dp))
            NumberedStep(1, steps("Busca ", "Kompara", " en la lista y tócalo"), "Puede estar en «Apps instaladas» o «Apps descargadas»")
            Spacer(Modifier.height(11.dp)); NumberedStep(2, steps("Activa el interruptor"))
            Spacer(Modifier.height(11.dp)); NumberedStep(3, steps("Toca ", "Permitir", " y regresa aquí"))
            Text("Android te va a preguntar esto:", Modifier.padding(top = 18.dp, bottom = 8.dp), style = Small, color = KomparaTheme.colors.inkSecondary)
            PermissionIllustration()
            Text("Android lo pregunta igual a toda app de accesibilidad. Kompara lo usa para leer tus ofertas; nunca toca botones ni acepta viajes por ti.",
                Modifier.padding(top = 12.dp), style = Small, color = KomparaTheme.colors.inkSecondary)
            Spacer(Modifier.height(20.dp))
            StatusLine(if (state.ready) "Permiso listo" else if (state.missing) "Semáforo apagado · aún falta el permiso" else "Semáforo apagado",
                if (state.ready) StatusGlyph.DONE else StatusGlyph.OFF, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
/** Alternating plain and bold runs: "En ", "Aplicaciones", ", busca ", "Kompara"… */
private fun steps(vararg runs: String) = buildAnnotatedString {
    runs.forEachIndexed { i, run -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(run) } else append(run) }
}
private val Small = KomparaType.small.copy(fontSize = 14.sp, lineHeight = 20.sp)
/** The kit's `.steps`: the number in a 22 dp column, 16 sp text, a 14 sp detail under it. */
@Composable private fun NumberedStep(number: Int, text: androidx.compose.ui.text.AnnotatedString, detail: String? = null) {
    Row {
        Text("$number.", Modifier.width(22.dp), style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
        Column(Modifier.weight(1f)) {
            Text(text, style = KomparaType.body)
            if (detail != null) Text(detail, Modifier.padding(top = 2.dp), style = Small, color = KomparaTheme.colors.inkSecondary)
        }
    }
}
@Composable private fun PermissionIllustration() {
    val c = KomparaTheme.colors
    Column(Modifier.fillMaxWidth().background(c.paperSecondary, RoundedCornerShape(10.dp)).padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
        .clearAndSetSemantics { role = Role.Image; contentDescription = "EJEMPLO. Android pregunta: ¿Permitir que Kompara tenga control total de tu dispositivo? Rechazar o Permitir. Es una imagen, no un control." }) {
        Text("EJEMPLO", Modifier.border(1.dp, c.inkTertiary, RoundedCornerShape(3.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            style = KomparaType.smallStrong.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = .9.sp), color = c.inkSecondary)
        Column(Modifier.padding(top = 10.dp).fillMaxWidth().shadow(1.dp, RoundedCornerShape(10.dp)).background(c.paper, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("¿Permitir que Kompara tenga control total de tu dispositivo?", style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 19.sp))
            Row(Modifier.align(Alignment.End).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("Rechazar", "Permitir").forEach { Text(it, style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp), color = c.inkTertiary) }
            }
        }
    }
}

@Composable
fun OemSurvivalScreen(permissionGranted: Boolean, onOpenBattery: () -> Unit, onContinue: () -> Unit,
    modifier: Modifier = Modifier, onBack: () -> Unit = onContinue, recovered: Boolean = false, error: String? = null, working: Boolean = false) {
    OnboardingFrame(onBack, modifier = modifier, contentTop = 8.dp, footer = {
        ErrorLine(error); PrimaryButton("Abrir ajustes", onOpenBattery); OnboardingTextAction("Ahora no", onContinue)
    }) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Title("Que tu teléfono no apague Kompara")
            Spacer(Modifier.height(18.dp))
            NumberedStep(1, steps("En ", "Aplicaciones", ", busca ", "Kompara", " y tócalo"))
            Spacer(Modifier.height(11.dp)); NumberedStep(2, steps("Toca ", "Batería"))
            Spacer(Modifier.height(11.dp)); NumberedStep(3, steps("Elige ", "Sin restricciones"))
            Spacer(Modifier.height(22.dp))
            if (!permissionGranted || working) StatusLine(if (!permissionGranted) "Semáforo apagado · aún falta el permiso" else "Semáforo funcionando",
                if (!permissionGranted) StatusGlyph.OFF else StatusGlyph.HEALTHY)
            else {
                Row(Modifier.heightIn(min = 44.dp).padding(start = 3.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    StatusIcon(StatusGlyph.WAITING)
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Semáforo preparado") }
                        if (recovered) withStyle(SpanStyle(color = KomparaTheme.colors.inkSecondary)) { append(" · permiso listo") }
                    }, style = KomparaType.meaning)
                }
                if (!recovered) Text("La primera oferta confirmará la lectura", Modifier.padding(start = 30.dp),
                    style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
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
