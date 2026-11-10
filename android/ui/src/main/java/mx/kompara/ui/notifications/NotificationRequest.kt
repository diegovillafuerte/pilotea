package mx.kompara.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import mx.kompara.data.service.ReaderPhase
import mx.kompara.data.service.ReaderReadiness

/**
 * The two launch notices need POST_NOTIFICATIONS from API 33. 0.2.20 asked from Hoy's "Avisarme si se apaga"; the
 * launch set has no such row, so Ganancias asks once per install, with the system's own dialog, after a first run that
 * left the reader on. A decline is never asked again automatically.
 */
object NotificationAsk {
    fun eligible(onboarded: Boolean, asked: Boolean, reader: ReaderReadiness) = onboarded && !asked &&
        reader.permission == true && reader.phase != ReaderPhase.OFF && !reader.paused
}

/** Shows the system dialog when [eligible] on API 33+ without the permission; [onAsked] records it first. */
@Composable
fun NotificationPermissionRequest(eligible: Boolean, onAsked: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(eligible) {
        if (eligible && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            onAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
