package mx.kompara.ocr

import android.app.Notification
import android.content.Context
import android.content.Intent

/** Only the MediaProjection lane needs this mandatory foreground-service notice. */
internal fun captureNotification(context: Context): Notification {
    val stop = android.app.PendingIntent.getService(context, 42,
        Intent(context, OcrCaptureService::class.java).setAction(OcrCaptureService.ACTION_STOP),
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
    val public = Notification.Builder(context, "ocr_capture")
        .setContentTitle(context.getString(R.string.capture_active)).setSmallIcon(android.R.drawable.ic_menu_view).build()
    return Notification.Builder(context, "ocr_capture")
        .setContentTitle(context.getString(R.string.capture_active))
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
        .addAction(Notification.Action.Builder(null, context.getString(R.string.capture_stop), stop).build())
        .build()
}
