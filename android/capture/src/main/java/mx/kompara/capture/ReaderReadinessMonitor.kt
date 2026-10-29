package mx.kompara.capture

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.kompara.data.db.dao.OfferPresentationDao
import mx.kompara.data.service.ReaderReadinessRepository
import mx.kompara.data.service.ReaderReadinessReducer
import mx.kompara.parsers.spec.ActiveSpecProvider
import mx.kompara.parsers.spec.LoadedSpecs
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Uses OS evidence, never infers permission revocation from battery restrictions or a missing bind. */
object ReaderEnvironment {
    fun permissionGranted(context: Context): Boolean =
        mx.kompara.data.service.ReaderDeviceEnvironment.permissionGranted(context)
}

/** OCR owns these apps; a bundled node spec is not proof that an OCR parser exists. */
object ReaderSupportAdapter {
    fun supported(loaded: LoadedSpecs): Set<String> =
        setOf(ReaderReadinessReducer.UBER, ReaderReadinessReducer.DIDI) - loaded.disabledPackages
}

@Singleton
class ReaderReadinessMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val readiness: ReaderReadinessRepository,
    private val specs: ActiveSpecProvider,
    private val presentations: OfferPresentationDao,
) {
    private var job: Job? = null
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        if (job != null) return
        specs.specs.onEach { readiness.supportedApps(ReaderSupportAdapter.supported(it)) }.launchIn(scope)
        readiness.state.map { it.date }.distinctUntilChanged().flatMapLatest { date ->
            val zone = ZoneId.systemDefault()
            presentations.observeSuppressedCount(date.atStartOfDay(zone).toInstant().toEpochMilli(),
                date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()).map { date to it }
        }.onEach { (date, count) -> readiness.suppressionCount(date, count) }.launchIn(scope)
        // Also observes midnight while UI is closed, permission loss without an orderly onDestroy,
        // and Android's battery flag on return. This never reads a host node or takes a screenshot.
        job = scope.launch {
            while (isActive) {
                readiness.observeEnvironment(ReaderEnvironment.permissionGranted(context),
                    mx.kompara.data.service.ReaderDeviceEnvironment.batteryRestricted(context))
                readiness.refresh()
                delay(1_000)
            }
        }
    }
}
