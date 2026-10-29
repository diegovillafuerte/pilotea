package mx.kompara.ui.theme

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat

/** Keep native bar icons legible when the app overrides the system's day/night setting. */
@Composable
fun KomparaSystemBars(window: Window) {
    val dark = KomparaTheme.colors == NightColors
    SideEffect {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
