package mx.kompara.app

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mx.kompara.data.settings.Settings
import mx.kompara.data.settings.SettingsRepository
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import mx.kompara.ui.nav.KomparaRoot
import mx.kompara.ui.notifications.NoticeTarget
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mx.kompara.ui.theme.KomparaTheme
import mx.kompara.ui.theme.KomparaSystemBars

/**
 * Single-activity host. [@AndroidEntryPoint][AndroidEntryPoint] makes this a Hilt injection target
 * so screens deeper in `:ui` can resolve their ViewModels (which pull injected dependencies from
 * `:data`) end-to-end.
 *
 * The activity stays deliberately thin: the design system and the whole navigable shell live in
 * `:ui` ([KomparaRoot] + [KomparaTheme]); `:app` only wires modules together and launches the root.
 * [KomparaRoot] routes between the onboarding funnel (B-036) and the main three-tab shell.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var summaryDelivery: mx.kompara.ui.notifications.SummaryDelivery
    private var notice by mutableStateOf<NoticeTarget?>(null)
    private var openShareCard by mutableStateOf(false)
    private var openImport by mutableStateOf(false)

    private fun readLaunch(source: android.content.Intent?) {
        notice = NoticeTarget.parse(source?.data)
        openShareCard = source?.getBooleanExtra("mx.kompara.OPEN_SHARE_CARD", false) ?: false
        openImport = source?.getBooleanExtra(EXTRA_OPEN_IMPORT, false) ?: false
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaunch(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingNotice", notice?.uri?.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        mx.kompara.data.service.DrivingActivity.foreground(packageName)
        // A warm app open is a safe catchup opportunity even if WorkManager is in backoff.
        // The same durable outbox/mutex used by workers prevents duplicate delivery.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for (kind in listOf(mx.kompara.ui.notifications.NoticeKind.DAY, mx.kompara.ui.notifications.NoticeKind.WEEK,
                mx.kompara.ui.notifications.NoticeKind.MONTH, mx.kompara.ui.notifications.NoticeKind.IMPORT)) {
                try { summaryDelivery.deliver(kind) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* Scheduled workers retain retries. */ }
            }
        }
    }
    override fun onStop() {
        mx.kompara.data.service.DrivingActivity.unknown()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readLaunch(intent)
        if (savedInstanceState != null) notice = NoticeTarget.parse(savedInstanceState.getString("pendingNotice")?.let(android.net.Uri::parse))
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(Settings.DEFAULT)
            KomparaTheme(preference = settings.themePreference) {
                KomparaSystemBars(window)
                KomparaRoot(
                    navigateToShareCard = openShareCard,
                    navigateToImport = openImport,
                    notification = notice,
                    onNotificationConsumed = { notice = null; intent?.data = null },
                )
            }
        }
    }

    companion object {
        /** Set by [ShareImportActivity] to deep-link the import flow for a shared earnings file (PR-D3). */
        const val EXTRA_OPEN_IMPORT = "mx.kompara.extra.OPEN_IMPORT"
    }
}
