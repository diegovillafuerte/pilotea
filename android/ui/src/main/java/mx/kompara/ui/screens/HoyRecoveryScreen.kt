package mx.kompara.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.*
import mx.kompara.ui.onboarding.*

/** Return to the actual opener; Hoy derives B2/B2r/B3/B3n from saved history and live evidence. */
@Composable
fun HoyRecoveryScreen(onDone: () -> Unit) {
    OnboardingNavGraph(onComplete = onDone, entry = OnboardingEntry.RECOVERY)
}

@Composable
fun HoyBatteryScreen(onDone: () -> Unit) {
    OnboardingNavGraph(onComplete = onDone, entry = OnboardingEntry.BATTERY)
}

/** User-initiated host launch/settings only; no interaction with an offer or host controls. */
object HoyHostLinks {
    fun openUber(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage("com.ubercab.driver")
        if (launch != null && runCatching { context.startActivity(launch) }.isSuccess) return
        Toast.makeText(context, "No encontramos Uber Driver en este teléfono.", Toast.LENGTH_LONG).show()
    }
    fun notifications(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        runCatching { context.startActivity(intent) }.recoverCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
        }
    }
}
