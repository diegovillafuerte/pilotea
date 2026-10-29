package mx.kompara.ui.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import mx.kompara.data.service.*
import mx.kompara.data.settings.SettingsRepository
import mx.kompara.ui.R
import mx.kompara.ui.notifications.*
import javax.inject.Inject
import javax.inject.Singleton

/** One incident owner for accessibility and both capture lanes. Polling retries Android denial
 * without consuming the incident or inventing another outage timestamp. */
@Singleton
class ServiceWatchdog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serviceStatus: ServiceStatusProvider,
    private val settingsRepository: SettingsRepository,
    private val readiness: ReaderReadinessRepository,
    private val channels: NoticeChannels,
    private val poster: NoticePoster,
) {
    private val machine = WatchdogStateMachine()
    private var started = false
    private val mutable = MutableStateFlow(WatchdogState.IDLE)
    val bannerState: StateFlow<WatchdogState> = mutable.asStateFlow()

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        flow {
            channels.migrate()
            while (true) { emit(Unit); delay(5_000) }
        }.combine(settingsRepository.settings) { _, prefs -> prefs }
            .combine(serviceStatus.readiness) { prefs, reader -> prefs to reader }
            .onEach { (prefs, reader) ->
                val shouldNotify = machine.onSample(prefs.onboardingCompleted, reader)
                mutable.value = machine.state
                if (!prefs.onboardingCompleted || !prefs.readerAlertsEnabled || reader.paused || reader.incident == null) {
                    poster.cancelReader()
                } else if (shouldNotify || poster.hasLegacyReader()) reader.alertIncident?.let { incident ->
                    // Permission/binding may have changed while preferences were loading.
                    if (readiness.state.value.alertIncident != incident) return@let
                    val body = if (incident.cause == ReaderIncidentCause.PERMISSION_LOST) R.string.notice_permission_lost
                        else R.string.notice_capture_stopped
                    if (poster.post(NoticeTarget(NoticeKind.READER), context.getString(R.string.notice_reader_title),
                            context.getString(body), incident.firstSeenAt)) readiness.markNotified(incident)
                }
            }.launchIn(scope)
    }
    companion object { const val CHANNEL_ID = NoticeChannels.READER }
}
