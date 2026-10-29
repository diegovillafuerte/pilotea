package mx.kompara.ui.export

import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import mx.kompara.ui.R
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowContentResolver

class ExportHarness {
    val context: Context = ApplicationProvider.getApplicationContext()
    val provider = ExportFileProvider()
    val cache: ExportCache
    init {
        val info = ProviderInfo().apply {
            authority = "${context.packageName}.fileprovider"
            name = ExportFileProvider::class.java.name
            packageName = context.packageName
            grantUriPermissions = true
            exported = false
            metaData = Bundle().apply { putInt("android.support.FILE_PROVIDER_PATHS", R.xml.file_paths) }
        }
        Shadows.shadowOf(context.packageManager).addOrUpdateProvider(info)
        provider.attachInfo(context, info)
        ShadowContentResolver.registerProviderInternal(info.authority, provider)
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        cache = ExportCache(context, WorkManager.getInstance(context))
        ExportCache.DIRECTORIES.forEach(cache::clear)
    }
}
