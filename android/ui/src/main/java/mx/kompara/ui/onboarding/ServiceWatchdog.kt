package mx.kompara.ui.onboarding

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mx.kompara.data.service.ServiceStatusProvider
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches reader-service health once onboarding is complete and, when the bound accessibility
 * service is killed (typically by an OEM task killer), (1) posts a notification on the "estado del
 * lector" channel with a re-enable path and (2) exposes an in-app [WatchdogState] banner that the
 * Inicio screen collects (B-036).
 *
 * Reads service health through the `:data` [ServiceStatusProvider] abstraction (bound to
 * `:capture`'s ServiceStateRepository) so `:ui` never depends on `:capture`. The transition logic
 * lives in the pure [WatchdogStateMachine]; this class only does the Android plumbing (channels,
 * POST_NOTIFICATIONS guard, deep-link intent).
 */
@Singleton
class ServiceWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceStatus: ServiceStatusProvider,
    private val settingsRepository: SettingsRepository,
) {
    private val machine = WatchdogStateMachine()

    private val _bannerState = MutableStateFlow(WatchdogState.IDLE)

    /** The in-app banner state for the Inicio screen to collect. */
    val bannerState: StateFlow<WatchdogState> = _bannerState.asStateFlow()

    /**
     * Begin observing. Combines onboarding-completion with the live connected flow, drives the
     * state machine, mirrors the result into [bannerState], and posts a notification on the
     * healthy→dropped edge. Idempotent enough to call once from application start; runs on [scope].
     */
    fun start(scope: CoroutineScope) {
        ensureChannel()
        combine(
            settingsRepository.settings.map { it.onboardingCompleted }.distinctUntilChanged(),
            serviceStatus.connected,
        ) { armed, connected -> armed to connected }
            .onEach { (armed, connected) ->
                val shouldNotify = machine.onSample(armed = armed, connected = connected)
                _bannerState.value = machine.state
                if (shouldNotify) postReaderDownNotification()
            }
            .launchIn(scope)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.watchdog_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.watchdog_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun postReaderDownNotification() {
        // On 33+ posting requires the runtime POST_NOTIFICATIONS permission (requested contextually
        // on the onboarding done screen). If it isn't granted we silently skip — the in-app banner
        // still surfaces the same prompt, so the driver is never left without a re-enable path.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kompara_reader)
            .setContentTitle(context.getString(R.string.watchdog_notif_title))
            .setContentText(context.getString(R.string.watchdog_notif_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(AccessibilitySettings.pendingIntent(context))
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        /** Notification channel for reader-health alerts. */
        const val CHANNEL_ID = "estado_del_lector"
        private const val NOTIFICATION_ID = 4036
    }
}
