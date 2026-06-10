package mx.kompara.overlay.di

import android.content.Context
import android.view.WindowManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the framework collaborators the [mx.kompara.overlay.OverlayController] needs.
 *
 * The [WindowManager] is resolved from the application context. A `TYPE_ACCESSIBILITY_OVERLAY`
 * window does not need a service-bound window token (that is the whole reason we use it instead of
 * `TYPE_APPLICATION_OVERLAY` — no `SYSTEM_ALERT_WINDOW` permission), so the application
 * WindowManager is sufficient; the accessibility service is still the only caller that attaches it.
 */
@Module
@InstallIn(SingletonComponent::class)
object OverlayModule {

    @Provides
    @Singleton
    fun provideWindowManager(@ApplicationContext context: Context): WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
}
