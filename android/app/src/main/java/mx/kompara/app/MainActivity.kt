package mx.kompara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import mx.kompara.overlay.simulator.simulatorDestination
import mx.kompara.ui.nav.KomparaRoot
import mx.kompara.ui.share.WeekCloseNotifier
import mx.kompara.ui.theme.KomparaTheme

/**
 * Single-activity host. [@AndroidEntryPoint][AndroidEntryPoint] makes this a Hilt injection target
 * so screens deeper in `:ui` can resolve their ViewModels (which pull injected dependencies from
 * `:data`) end-to-end.
 *
 * The activity stays deliberately thin: the design system and the whole navigable shell live in
 * `:ui` ([KomparaRoot] + [KomparaTheme]); `:app` only wires modules together and launches the root.
 * [KomparaRoot] routes between the onboarding funnel (B-036) and the main 5-tab shell.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // B-055: the Monday week-close notification launches us with this extra to deep-link the
        // share-card preview. Read once at startup; KomparaRoot honours it after onboarding.
        val openShareCard = intent?.getBooleanExtra(WeekCloseNotifier.EXTRA_OPEN_SHARE_CARD, false) ?: false
        // PR-D3: ShareImportActivity re-roots us with this extra after the driver shares an earnings
        // file into Kompara; the file is already staged in SharedImportBuffer, so we just deep-link the
        // import flow (post-onboarding) and the ImportViewModel picks the staged file up.
        val openImport = intent?.getBooleanExtra(EXTRA_OPEN_IMPORT, false) ?: false
        setContent {
            KomparaTheme {
                // `:app` is the only module that depends on both `:ui` (nav shell) and `:overlay`
                // (the verdict chip the simulator embeds), so it injects the simulator route here.
                // KomparaRoot threads it through to the main shell once onboarding completes (B-036).
                KomparaRoot(
                    navigateToShareCard = openShareCard,
                    navigateToImport = openImport,
                    registerExtraDestinations = { simulatorDestination() },
                )
            }
        }
    }

    companion object {
        /** Set by [ShareImportActivity] to deep-link the import flow for a shared earnings file (PR-D3). */
        const val EXTRA_OPEN_IMPORT = "mx.kompara.extra.OPEN_IMPORT"
    }
}
