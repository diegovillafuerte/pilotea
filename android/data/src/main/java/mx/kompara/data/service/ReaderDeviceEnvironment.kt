package mx.kompara.data.service

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Same OS evidence for foreground permission returns and background monitoring; binding is separate. */
object ReaderDeviceEnvironment {
    fun component(context: Context) = ComponentName(context.packageName, "mx.kompara.capture.KomparaAccessibilityService")
    fun permissionGranted(context: Context): Boolean {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            services.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    }
    /**
     * The battery step is needed when the app is background-restricted ("Restringida"), or on a Samsung when the app
     * is not exempt from battery optimization. Measured on a Galaxy S25 in the default "Optimizada" mode (2026-11-10):
     * Samsung's freezer stopped the reader ~6 s after each accessibility event, for minutes at a time, while
     * `isBackgroundRestricted` stayed false, and "Sin restricciones" put the app on the exemption list. Other makers
     * keep the restricted-only rule until someone measures them (TD-V4-11).
     */
    fun batteryRestricted(context: Context): Boolean = Build.VERSION.SDK_INT >= 28 &&
        context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true ||
        OemDetector.current() == OemProfile.SAMSUNG &&
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == false
}
