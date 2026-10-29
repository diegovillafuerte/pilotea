package mx.kompara.data.service

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

/** Same OS evidence for foreground permission returns and background monitoring; binding is separate. */
object ReaderDeviceEnvironment {
    fun component(context: Context) = ComponentName(context.packageName, "mx.kompara.capture.KomparaAccessibilityService")
    fun permissionGranted(context: Context): Boolean {
        val services = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1 &&
            services.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == component(context) }
    }
    fun batteryRestricted(context: Context): Boolean = Build.VERSION.SDK_INT >= 28 &&
        context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
}
