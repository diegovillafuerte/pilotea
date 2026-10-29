package mx.kompara.ui.onboarding

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import mx.kompara.data.service.ReaderDeviceEnvironment

/** Capability resolution AND successful launch are required. No SDK-only promise about an OEM. */
object AccessibilitySettings {
    // AOSP Settings supports this optional action; it is not in the public SDK. Resolve first.
    const val DETAILS_ACTION = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    fun candidates(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= 26) add(Intent(DETAILS_ACTION)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ReaderDeviceEnvironment.component(context).flattenToString()))
        add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
    fun open(context: Context): Boolean {
        val opened = launchFirst(context, candidates(context)) ?: return false
        Toast.makeText(context, if (opened.action == DETAILS_ACTION)
            "Activa el interruptor" else "Busca «Kompara» y activa el interruptor", Toast.LENGTH_LONG).show()
        return true
    }
    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** Background restriction belongs to the app's battery settings, not the Doze exemption list. */
object BatterySettings {
    fun candidates(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
    )
    fun open(context: Context): Boolean = launchFirst(context, candidates(context)) != null
}

internal fun launchFirst(context: Context, candidates: List<Intent>): Intent? = candidates.firstOrNull { intent ->
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.resolveActivity(context.packageManager) != null && runCatching { context.startActivity(intent) }.isSuccess
}
