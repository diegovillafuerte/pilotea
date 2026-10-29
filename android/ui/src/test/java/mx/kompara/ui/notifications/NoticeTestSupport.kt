package mx.kompara.ui.notifications

import android.app.Application
import android.content.*
import android.content.pm.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Shadows.shadowOf

internal class NoticePreferences : DataStore<Preferences> {
    override val data = MutableStateFlow(emptyPreferences())
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(data.value).also { data.value = it }
}
internal fun allowNotices(context: Context) {
    shadowOf(context as Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    shadowOf(context.packageManager).addResolveInfoForIntent(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName),
        ResolveInfo().apply { activityInfo = ActivityInfo().apply {
            packageName = context.packageName; name = "mx.kompara.app.MainActivity"; exported = true
        } })
}
