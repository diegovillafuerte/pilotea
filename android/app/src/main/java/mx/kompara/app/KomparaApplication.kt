package mx.kompara.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.kompara.capture.OfferEventPipeline
import mx.kompara.capture.telemetry.TelemetryCollector
import mx.kompara.sync.spec.SpecConfigRefreshWorker
import mx.kompara.sync.spec.SpecConfigRepository
import mx.kompara.sync.telemetry.TelemetryScheduler
import mx.kompara.ui.onboarding.ServiceWatchdog
import javax.inject.Inject

/**
 * Application entry point that bootstraps the Hilt dependency graph for the whole app.
 *
 * Implements [Configuration.Provider] so WorkManager builds workers via the injected
 * [HiltWorkerFactory] — required by the `@HiltWorker` background workers (the telemetry upload
 * worker, B-034, and the OTA parser-config refresh worker, B-033).
 *
 * In [onCreate] it:
 *  - starts the privacy-safe parse-health telemetry collector observing the existing offer-event
 *    stream and schedules the periodic upload (B-034). The collector runs on an application-scoped
 *    supervisor scope so a transient failure never crashes the app or stops capture;
 *  - fires a one-shot [SpecConfigRepository.refresh] so a fresh spec / kill switch is picked up this
 *    session, and enqueues the ~6h periodic [SpecConfigRefreshWorker] for the background cadence
 *    (B-033). Both are best-effort: a failed fetch retains the last-known-good specs.
 */
@HiltAndroidApp
class KomparaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // B-034 telemetry bootstrap collaborators.
    @Inject lateinit var offerEventPipeline: OfferEventPipeline
    @Inject lateinit var telemetryCollector: TelemetryCollector
    @Inject lateinit var telemetryScheduler: TelemetryScheduler

    // B-033 OTA parser-config collaborators.
    @Inject lateinit var specConfigRepository: SpecConfigRepository
    @Inject lateinit var workManager: WorkManager

    // B-036 service-health watchdog (alerts when an OEM task killer disables the reader).
    @Inject lateinit var serviceWatchdog: ServiceWatchdog

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Observe the offer-event flow and accumulate privacy-safe counters. No screen content
        // enters telemetry — see TelemetryCollector (B-034).
        appScope.launch {
            telemetryCollector.collect(offerEventPipeline.offers)
        }
        // Enqueue the idempotent 12h periodic telemetry upload (network-constrained, with backoff).
        telemetryScheduler.ensurePeriodic()

        // Fetch the freshest signed parser bundle now (so a kill switch applies this session), then
        // let the periodic worker keep it current. refresh() never throws and no-ops when nothing is
        // newer (B-033).
        appScope.launch { specConfigRepository.refresh() }
        SpecConfigRefreshWorker.schedule(workManager)

        // Watch reader-service health once onboarding completes; alert (notification + in-app
        // banner) if an OEM task killer disables the accessibility service mid-shift (B-036).
        serviceWatchdog.start(appScope)
    }
}
