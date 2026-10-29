package mx.kompara.ui.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
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

@Singleton
class NoticePoster @Inject constructor(@ApplicationContext private val context: Context) {
    fun allowed(channel: String): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(channel)?.let { it.importance != NotificationManager.IMPORTANCE_NONE } == true

    fun build(target: NoticeTarget, title: String, body: String, at: Long): Notification? {
        val channel = if (target.kind == NoticeKind.READER) NoticeChannels.READER else NoticeChannels.SUMMARIES
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = target.uri
        } ?: return null
        val tap = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_kompara_reader).setContentTitle(title).setWhen(at).build()
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_kompara_reader).setColor(0xFF187A36.toInt())
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setWhen(at).setShowWhen(true).setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setPriority(if (target.kind == NoticeKind.READER) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .addExtras(android.os.Bundle().apply { putBoolean("notice_v3", true) })
            .setAutoCancel(true).setContentIntent(tap).build()
    }
    @SuppressLint("MissingPermission")
    fun post(target: NoticeTarget, title: String, body: String, at: Long): Boolean {
        val channel = if (target.kind == NoticeKind.READER) NoticeChannels.READER else NoticeChannels.SUMMARIES
        if (!allowed(channel)) return false
        val notice = build(target, title, body, at) ?: return false
        return runCatching { if (target.kind == NoticeKind.READER) NotificationManagerCompat.from(context).notify(ID, notice)
            else NotificationManagerCompat.from(context).notify(tag(target), ID, notice) }.isSuccess
    }
    fun hasLegacyReader(): Boolean = context.getSystemService(NotificationManager::class.java).activeNotifications.any {
        it.id == ID && it.tag == null && !it.notification.extras.getBoolean("notice_v3")
    }
    fun cancelReader() { NotificationManagerCompat.from(context).cancel(ID) }
    fun cancelSummaries(kind: NoticeKind) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("kompara:${kind.name}:") == true }.forEach { manager.cancel(it.tag, it.id) }
    }
    private fun tag(target: NoticeTarget) = "kompara:${target.kind.name}:${target.period}"
    companion object { const val ID = 4036 }
}
