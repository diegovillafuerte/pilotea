package mx.kompara.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kompara brand palette. The product is a traffic-light: every screen is glanceable in a moving
 * car, often at night and often in direct sunlight. Colours are therefore highly saturated with
 * strong luminance contrast so the verde/amarillo/rojo verdict reads instantly on a phone propped
 * on the dashboard.
 *
 * The verdict triple ([VerdictGreen]/[VerdictYellow]/[VerdictRed]) is the heart of the system and
 * is shared by [mx.kompara.ui.components.VerdictBadge], metric accents and progress fills. Values
 * are tuned to keep ~4.5:1 contrast against white text (green/red) and dark text (yellow).
 */

// --- Verdict (semáforo) ----------------------------------------------------
/** Verde — el viaje conviene. */
val VerdictGreen = Color(0xFF1B8A3A)
/** Amarillo — viaje regular / marginal. Texto oscuro encima para contraste. */
val VerdictYellow = Color(0xFFF2B705)
/** Rojo — el viaje no conviene. */
val VerdictRed = Color(0xFFD32F2F)

/** "On" colours: el texto/icono que va encima de cada color de veredicto. */
val OnVerdictGreen = Color(0xFFFFFFFF)
val OnVerdictYellow = Color(0xFF1A1300)
val OnVerdictRed = Color(0xFFFFFFFF)

// --- Brand primary ---------------------------------------------------------
// El verde de veredicto es también el color de marca: la promesa de Kompara es "esto te conviene".
val BrandGreen = Color(0xFF12A150)
val BrandGreenDark = Color(0xFF0E7A3C)
val BrandGreenContainerDark = Color(0xFF06351C)
val BrandGreenContainerLight = Color(0xFFB8F0CC)

// --- Neutrals (modo oscuro por defecto: los choferes trabajan de noche) -----
val SurfaceDark = Color(0xFF121417)
val SurfaceContainerDark = Color(0xFF1C1F23)
val SurfaceVariantDark = Color(0xFF2A2E33)
val OnSurfaceDark = Color(0xFFF2F3F5)
val OnSurfaceVariantDark = Color(0xFFBFC4CA)
val OutlineDark = Color(0xFF44494F)

val SurfaceLight = Color(0xFFFAFBFC)
val SurfaceContainerLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEAEDF0)
val OnSurfaceLight = Color(0xFF14181C)
val OnSurfaceVariantLight = Color(0xFF454B52)
val OutlineLight = Color(0xFFC3C9CF)
