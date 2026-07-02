package mx.kompara.ui.share

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import mx.kompara.ui.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android plumbing for the Monday week-close share reminder (B-055) — the dedicated "resúmenes"
 * channel, the POST_NOTIFICATIONS guard, and a tap that deep-links into the share-card preview. Mirrors
 * [mx.kompara.ui.fiscal.FiscalMonthEndNotifier]'s channel/guard pattern. The *decision* to post is the
 * pure [WeekCloseDecision]; this class only renders and opens the preview.
 *
 * The content intent re-launches the app's launcher activity with [EXTRA_OPEN_SHARE_CARD] set; the
 * root composable reads that flag and navigates to the share-card route (the same one-shot deep-link
 * mechanism the reader-trial flow uses), so `:ui` never has to reference `:app`'s activity class.
 */
@Singleton
class WeekCloseNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Post the "tu resumen está listo" reminder, guarded by the runtime notification permission. */
    // Guarded below with checkSelfPermission; lint can't follow the guard into the runCatching lambda.
    @SuppressLint("MissingPermission")
    fun post() {
        ensureChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // No permission → the Inicio header share icon still surfaces the card; never block.
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_kompara_reader)
            .setContentTitle(context.getString(R.string.share_week_close_notif_title))
            .setContentText(context.getString(R.string.share_week_close_notif_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.share_week_close_notif_body)),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openShareCardIntent())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun openShareCardIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_SHARE_CARD, true)
            }
            ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, REQUEST_CODE, launch, flags)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.share_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.share_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** Notification channel id for the weekly share reminders ("resúmenes"). */
        const val CHANNEL_ID = "resumenes"

        /** Activity intent extra read by the root composable to deep-link to the share card. */
        const val EXTRA_OPEN_SHARE_CARD = "mx.kompara.OPEN_SHARE_CARD"

        private const val NOTIFICATION_ID = 5500
        private const val REQUEST_CODE = 5501
    }
}
