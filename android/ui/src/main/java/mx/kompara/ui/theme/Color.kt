package mx.kompara.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kompara brand palette. The product is a traffic-light: every screen is glanceable in a moving
 * car, often at night and often in direct sunlight. Colours are therefore highly saturated with
 * strong luminance contrast so the verde/amarillo/rojo verdict reads instantly on a phone propped
 * on the dashboard.
 *
 * The palette is the canonical Kompara design system, shared with the marketing website: emerald
 * brand primary + slate neutrals (the website's Tailwind `@theme` tokens). The verdict triple
 * ([VerdictGreen]/[VerdictYellow]/[VerdictRed]) is the heart of the system and is shared by
 * [mx.kompara.ui.components.VerdictBadge], metric accents and progress fills. Values are tuned to
 * keep ~4.5:1 contrast against white text (green/red) and dark text (yellow).
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

// --- Brand primary (emerald) -----------------------------------------------
// The brand green = the website's emerald primary (the canonical design system). One token, used as
// the Material primary AND the verdict chip's brand strip, so the app and the website read as one.
val BrandGreen = Color(0xFF059669) // website primary-600 — dark-theme primary + chip brand strip
val BrandGreenDark = Color(0xFF047857) // website primary-700 — light-theme primary
val BrandGreenContainerDark = Color(0xFF064E3B) // website primary-900
val BrandGreenContainerLight = Color(0xFFD1FAE5) // website primary-100

// --- Neutrals: the website's slate scale (modo oscuro por defecto: choferes de noche) -----------
val SurfaceDark = Color(0xFF0F172A) // slate-900
val SurfaceContainerDark = Color(0xFF1E293B) // slate-800
val SurfaceVariantDark = Color(0xFF334155) // slate-700
val OnSurfaceDark = Color(0xFFF8FAFC) // slate-50
val OnSurfaceVariantDark = Color(0xFFCBD5E1) // slate-300
val OutlineDark = Color(0xFF475569) // slate-600

val SurfaceLight = Color(0xFFF8FAFC) // slate-50
val SurfaceContainerLight = Color(0xFFFFFFFF) // white
val SurfaceVariantLight = Color(0xFFE2E8F0) // slate-200
val OnSurfaceLight = Color(0xFF0F172A) // slate-900
val OnSurfaceVariantLight = Color(0xFF475569) // slate-600
val OutlineLight = Color(0xFFCBD5E1) // slate-300
