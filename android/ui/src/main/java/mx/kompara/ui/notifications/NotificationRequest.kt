package mx.kompara.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/**
 * Shows the system dialog when [eligible] on API 33+ without the permission. [onAnswered] records the ask from the
 * dialog's result, granted or declined: at most once per answered dialog, not strictly once per install, since a
 * process lost while the dialog is up leaves it unrecorded and the next visit asks again. The in-flight flag lives in saved state: a configuration change while the dialog is up neither
 * launches a second request nor loses the answer, which the activity result registry delivers to the new launcher.
 */
@Composable
fun NotificationPermissionRequest(eligible: Boolean, onAnswered: () -> Unit) {
    val context = LocalContext.current
    var inFlight by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        inFlight = false
        onAnswered()
    }
    LaunchedEffect(eligible) {
        if (eligible && !inFlight && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            inFlight = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
