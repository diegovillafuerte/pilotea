package mx.kompara.capture

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderEnvironmentTest {
    @Test fun `permission observation requires our exact enabled component not merely accessibility on`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "other.app/.Reader")
        assertFalse(ReaderEnvironment.permissionGranted(context))
        val component = ComponentName(context, KomparaAccessibilityService::class.java).flattenToString()
        Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "other.app/.Reader:$component")
        assertTrue(ReaderEnvironment.permissionGranted(context))
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        assertFalse(ReaderEnvironment.permissionGranted(context))
    }
}
