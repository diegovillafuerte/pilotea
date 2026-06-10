package mx.kompara.sync.aggregate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import mx.kompara.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot consent-prompt state for the aggregate-sharing opt-in (B-043).
 *
 * The product asks for aggregate-sharing consent with an explicit prompt
 * ("Compartir mis promedios semanales de forma anónima para comparativas"). This holder exposes
 * WHEN to show that prompt and the actions to resolve it, so the **Inicio** screen (owned by `:ui`,
 * a sibling task this wave) can render it later WITHOUT this task touching `:ui`. Per the task's
 * conflict-avoidance note, the Ajustes toggle composable is also deferred to the `:ui` sibling — this
 * file is the domain seam they bind to.
 *
 * **Show-once semantics.** [shouldPrompt] emits true only while the driver has neither opted in nor
 * dismissed the prompt. [acceptSharing] opts in (flips [mx.kompara.data.settings.Settings.shareAggregates]
 * on and arms the sync); [dismiss] records that the prompt was shown so it isn't nagged again.
 * Dismissal is tracked via a settings flag so it survives process death.
 *
 * USAGE (for the `:ui` Inicio sibling, B-04x): collect [shouldPrompt]; when true, render the prompt
 * card; on "Compartir" call [acceptSharing]; on "Ahora no" call [dismiss]. After opting in, trigger
 * an immediate sync via [AggregateSyncScheduler.syncNow] (the ViewModel already holds the scheduler).
 */
@Singleton
class ConsentPrompter @Inject constructor(
    private val settings: SettingsRepository,
) {
    /**
     * Whether the aggregate-sharing prompt should be shown right now: true only while the driver has
     * neither already opted in NOR previously dismissed the prompt. A cold [Flow] that re-emits on
     * settings changes, so the prompt disappears the moment the driver acts.
     */
    val shouldPrompt: Flow<Boolean> = settings.settings.map { s ->
        !s.shareAggregates && !s.aggregatePromptDismissed
    }

    /** The driver opted in: turn on sharing. (The caller arms an immediate sync afterwards.) */
    suspend fun acceptSharing() {
        settings.setShareAggregates(true)
    }

    /** The driver declined for now: record the dismissal so the prompt isn't shown again. */
    suspend fun dismiss() {
        settings.setAggregatePromptDismissed(true)
    }
}
