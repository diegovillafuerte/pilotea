package mx.kompara.ui.fiscal

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.data.model.Platform
import mx.kompara.metrics.imss.MonthEndDecision
import mx.kompara.ui.R
import mx.kompara.ui.stats.platformChipLabel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android plumbing for the month-end IMSS summary notification (B-051) — the dedicated "fiscal"
 * channel, the POST_NOTIFICATIONS guard, and posting one notification per platform. Mirrors
 * [mx.kompara.ui.onboarding.ServiceWatchdog]'s channel/guard pattern exactly so the two stay
 * consistent. The *decision* (covered / not, which platforms) is the pure
 * [mx.kompara.metrics.imss.MonthEndSummary]; this class only renders it.
 */
@Singleton
class FiscalMonthEndNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Ensure the fiscal channel exists, then post one notification per [decisions] entry. */
    fun post(decisions: List<MonthEndDecision>) {
        if (decisions.isEmpty()) return
        ensureChannel()

        // On 33+ posting requires the runtime POST_NOTIFICATIONS permission (requested contextually
        // during onboarding). If it isn't granted we silently skip — the Fiscal tab still shows the
        // same per-platform coverage, so the driver is never left without the information.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        decisions.forEach { decision -> postOne(decision) }
    }

    // POST_NOTIFICATIONS is guarded in post() before any postOne() call; lint can't follow the guard
    // across the method boundary + runCatching lambda, so suppress the false positive here.
    @SuppressLint("MissingPermission")
    private fun postOne(decision: MonthEndDecision) {
        val platformLabel = context.getString(platformChipLabel(platformOf(decision.platform)))
        val title = if (decision.covered) {
            context.getString(R.string.fiscal_notif_covered_title, platformLabel)
        } else {
            context.getString(R.string.fiscal_notif_uncovered_title, platformLabel)
        }
        val body = if (decision.covered) {
            context.getString(R.string.fiscal_notif_covered_body, platformLabel)
        } else {
            context.getString(R.string.fiscal_notif_uncovered_body, platformLabel)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kompara_reader)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching {
            // Stable per-platform id so re-posting the same platform updates rather than stacks.
            NotificationManagerCompat.from(context).notify(notificationId(decision.platform), notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.fiscal_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.fiscal_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun platformOf(name: String): Platform =
        runCatching { Platform.valueOf(name) }.getOrDefault(Platform.UNKNOWN)

    private fun notificationId(platform: String): Int = NOTIFICATION_ID_BASE + platform.hashCode()

    companion object {
        /** Notification channel for month-end IMSS coverage summaries. */
        const val CHANNEL_ID = "fiscal"
        private const val NOTIFICATION_ID_BASE = 5100
    }
}
