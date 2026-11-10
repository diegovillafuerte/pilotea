package mx.kompara.ui.fixtures

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mx.kompara.ui.R
import mx.kompara.ui.components.*
import mx.kompara.ui.format.Formatters
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaType

/** Explicit debug activity, no production navigation or seeded repository. */
class V3ComponentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KomparaTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
            if (intent.hasExtra("shareFrame")) ShareFixtureHost(intent.getStringExtra("shareFrame") ?: "H3", !intent.getBooleanExtra("showAmounts", false))
            else if (intent.hasExtra("planFrame")) PlanFixtureHost(intent.getStringExtra("planFrame") ?: "H1")
            else if (intent.hasExtra("importFrame")) ImportFixtureHost(intent.getStringExtra("importFrame") ?: "H4")
            else if (intent.hasExtra("onboardingFrame")) OnboardingFixtureHost(intent.getStringExtra("onboardingFrame") ?: "A1")
            else if (intent.hasExtra("privacyFrame")) PrivacyFixtureHost(intent.getStringExtra("privacyFrame") ?: "G6")
            else if (intent.hasExtra("costsFrame")) CostsFixtureHost(intent.getStringExtra("costsFrame") ?: "G1")
            else if (intent.hasExtra("limitsFrame")) LimitsFixtureHost(intent.getStringExtra("limitsFrame") ?: "G2b")
            else if (intent.hasExtra("compareFrame")) CompareFixtureHost(intent.getStringExtra("compareFrame") ?: "K1")
            else if (intent.hasExtra("imssFrame")) ImssFixtureHost(intent.getStringExtra("imssFrame") ?: "M1")
            else if (intent.hasExtra("weekFrame")) WeekFixtureHost(intent.getStringExtra("weekFrame") ?: "E1")
            else V3ComponentHost(intent.getStringExtra("frame") ?: "D1")
        } }
    }
}

@Composable
fun V3ComponentHost(frame: String, modifier: Modifier = Modifier) {
    if (frame.startsWith("C") || frame in listOf("N8", "N9", "N10")) {
        ReglaFixtureHost(frame, modifier)
        return
    }
    val context = LocalContext.current
    val data = remember(context) { V3Fixture(context) }
    var selected by remember(frame) { mutableIntStateOf(0) }
    var showLedger by remember { mutableStateOf(false) }
    val title = when (frame) { "D1" -> "Tu día"; "E1" -> "Ganancias"; "F1" -> "Comparar"; "G2" -> "Tu meta y tu mínimo"; "G4" -> "Semáforo"; "H5" -> "Importar"; else -> "Hoy" }
    ScreenScaffold(title, modifier, kind = if (frame in listOf("E1", "F1")) ScreenKind.ROOT else ScreenKind.DRILL,
        bottomBar = { if (frame in listOf("D1", "E1", "F1")) RootBottomBar(if (frame == "F1") 1 else 0, { selected = it }) }) {
        when (frame) {
            "D1" -> {
                PeriodHero("Llevas hoy · aprox.", Formatters.formatMxnWhole(data.closedDay),
                    "9 viajes tomados en ${Formatters.formatDuration(490)}, ya restamos gasolina, desgaste y coche", { showLedger = true })
                Spacer(Modifier.height(32.dp))
                Text("Ofertas de hoy · 13", style = KomparaType.section)
                Text(stringResource(R.string.v3_rate_basis), style = KomparaType.small, color = KomparaTheme.colors.inkSecondary)
                OfferRow("La tomaste", "14:32 · Arriba de tu meta", data.chipRate("verde"), OfferMarkState.ABOVE, { showLedger = true })
                Rule()
                OfferRow("Se fue sin tomar", "12:10 · Abajo de tu mínimo", data.chipRate("rojo"), OfferMarkState.BELOW, { showLedger = true })
                Rule()
                OfferRow("Sin lectura", "11:48 · No pudimos leer la tarifa", null, OfferMarkState.UNKNOWN, { showLedger = true })
                Spacer(Modifier.height(20.dp))
                GreyButton("Ver todas las ofertas de hoy", { showLedger = true })
            }
            "E1" -> {
                TextTabs(listOf("Semana", "Mes"), selected, { selected = it })
                PeriodSelector("12 – 18 oct · semana pasada", PeriodUnit.WEEK, { showLedger = true }, { selected = 0 }, { selected = 1 }, true, true)
                PeriodHero("Te quedaron esa semana · aprox.", Formatters.formatMxnWhole(data.weekNet),
                    "62 viajes tomados, ya restamos gasolina, desgaste y coche", { showLedger = true }, day = false,
                    plot = { PeriodBars(data.days, onClick = { showLedger = true }) })
                ListRow("Por app", detail = "DiDi te dejó ${Formatters.formatMxn(data.rate("didi").subtract(data.rate("uber")))} más por km", onClick = { showLedger = true })
                Rule()
                ListRow("Cuadrar con tu resumen de Uber", onClick = { showLedger = true })
            }
            "F1" -> {
                TextTabs(listOf("Tus apps", "Tu ciudad"), selected, { selected = it })
                Text("DiDi te dejó ${Formatters.formatMxn(data.rate("didi").subtract(data.rate("uber")))} más por km", style = KomparaType.headline)
                HeroQuietLine("Manejaste cada app a distintas horas", { showLedger = true })
                Spacer(Modifier.height(32.dp))
                CompareBars(listOf("didi", "uber").map { BarValue(if (it == "didi") "DiDi" else "Uber", data.rate(it).toDouble(), "${Formatters.formatPerKm(data.rate(it))} sin el coche") })
                Spacer(Modifier.height(32.dp))
                Text("Ya restamos gasolina y desgaste; lo del coche cuesta igual en las dos apps.", style = KomparaType.body)
            }
            "G2" -> {
                var goal by remember { mutableStateOf(data.goal) }
                var minimum by remember { mutableStateOf(data.minimum) }
                TextTabs(listOf("Por km", "Por hora"), selected, { selected = it })
                LimitControl("Tu meta ·", goal, "/km", { goal = it }, { goal = "7.50" }, { goal = "8.50" }, helper = "Toca la cifra para escribirla.")
                Rule()
                LimitControl("Tu mínimo ·", minimum, "/km", { minimum = it }, { minimum = "5.50" }, { minimum = "6.50" }, cue = OfferMarkState.BELOW)
                Spacer(Modifier.height(32.dp))
                PrimaryButton(stringResource(R.string.v3_save), { showLedger = true })
                PrimaryButton(stringResource(R.string.v3_save), {}, enabled = false)
                StateLegend(OfferMarkState.MEETS)
            }
            "G4" -> {
                ListRow("Tu meta y tu mínimo", value = "$${data.goal}/km · $${data.minimum}/km", onClick = { showLedger = true })
                listOf(
                    StatusGlyph.HEALTHY to "Semáforo funcionando",
                    StatusGlyph.WAITING to "Semáforo preparado",
                    StatusGlyph.OFF to "Semáforo apagado",
                    StatusGlyph.PAUSED to "Semáforo en pausa",
                    StatusGlyph.DONE to "Lectura comprobada · 14:32",
                    StatusGlyph.ATTENTION to "Batería restringida · aún funciona",
                ).forEach { (glyph, text) -> StatusLine(text, glyph) }
                SaveSnackbarContent("Guardado: tu mínimo ahora es $6.50/km")
            }
            "H5" -> {
                Text("Te pagó la app", style = KomparaType.headline)
                Text(Formatters.formatMxnWhole(data.import.getDouble("paid")), style = KomparaType.number)
                Text("14 – 20 sep · PDF de Uber", style = KomparaType.body, color = KomparaTheme.colors.inkSecondary)
                Spacer(Modifier.height(24.dp))
                CalculationLedger(listOf(
                    CalculationRow("Viajes", data.import.getInt("trips").toString()),
                    CalculationRow("Horas en línea", "${data.import.getInt("hours")} h"),
                    CalculationRow("Comisión", Formatters.formatPercent(data.import.getDouble("commission_pct") / 100)),
                ))
                CalculationLedger(listOf(CalculationRow("Te quedaron", "—", detail = "faltan los kilómetros")))
                Text("Se guarda en septiembre como una semana, sin repartir por día.", style = KomparaType.small)
                ListRow("Corregir lo leído", onClick = { showLedger = true })
                PrimaryButton("Guardar", { showLedger = true })
            }
        }
    }
    if (showLedger) CalculationSheet(listOf(CalculationRow("Llevas hoy · aprox.", Formatters.formatMxnWhole(data.today), total = true)), { showLedger = false })
}

@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ComponentsDay() { KomparaTheme(darkTheme = false) { V3ComponentHost("D1") } }
@Preview(widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun ComponentsNight() { KomparaTheme(darkTheme = true) { V3ComponentHost("D1") } }
