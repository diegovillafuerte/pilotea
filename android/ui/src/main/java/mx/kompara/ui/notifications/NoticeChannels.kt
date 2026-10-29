package mx.kompara.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoticeChannels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val mutex = Mutex()
    /** Rename in place to retain Android's importance/sound/block choices. The obsolete fiscal
     * channel's block becomes the month preference before it is removed, never a new permission. */
    suspend fun migrate() = mutex.withLock {
        val manager = context.getSystemService(NotificationManager::class.java)
        val prefs = context.getSharedPreferences("notice_channels", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("v3", false)) {
            if (manager.getNotificationChannel("fiscal")?.importance == NotificationManager.IMPORTANCE_NONE) {
                settings.setFiscalMonthlySummaryEnabled(false)
            }
            val retired = listOf("ocr_reader_state", "ocr_reader_state_v2")
            if (retired.any { manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }) {
                settings.setReaderAlertsEnabled(false)
            }
            manager.cancel(43) // retired OCR stopped notice; watchdog owns the incident
            manager.cancel(5500) // undated legacy week/share notice
            manager.activeNotifications.filter { it.notification.channelId == "fiscal" }.forEach { manager.cancel(it.tag, it.id) }
            (retired + "fiscal").forEach(manager::deleteNotificationChannel)
            check(prefs.edit().putBoolean("v3", true).commit())
        }
        manager.createNotificationChannel(NotificationChannel(READER, context.getString(R.string.watchdog_channel_name),
            NotificationManager.IMPORTANCE_HIGH).apply { description = context.getString(R.string.watchdog_channel_desc) })
        manager.createNotificationChannel(NotificationChannel(SUMMARIES, context.getString(R.string.share_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT).apply { description = context.getString(R.string.share_channel_desc) })
    }
    companion object {
        // Stable IDs preserve user choices across the v3 rename.
        const val READER = "estado_del_lector"
        const val SUMMARIES = "resumenes"
    }
}
