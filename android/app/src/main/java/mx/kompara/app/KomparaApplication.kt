package mx.kompara.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.kompara.capture.OfferEventPipeline
import mx.kompara.capture.telemetry.TelemetryCollector
import mx.kompara.sync.telemetry.TelemetryScheduler
import javax.inject.Inject

/**
 * Application entry point that bootstraps the Hilt dependency graph for the whole app.
 *
 * Also implements [Configuration.Provider] so WorkManager uses the Hilt
 * [HiltWorkerFactory] — required by the `@HiltWorker` telemetry upload worker
 * (B-034). And, in [onCreate], it starts the privacy-safe parse-health
 * telemetry collector observing the existing offer-event stream and schedules
 * the periodic upload. The collector runs on an application-scoped supervisor
 * scope so a transient failure never crashes the app or stops capture.
 */
@HiltAndroidApp
class KomparaApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // B-034 telemetry bootstrap collaborators.
    @Inject lateinit var offerEventPipeline: OfferEventPipeline
    @Inject lateinit var telemetryCollector: TelemetryCollector
    @Inject lateinit var telemetryScheduler: TelemetryScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Observe the offer-event flow and accumulate privacy-safe counters. No
        // screen content enters telemetry — see TelemetryCollector.
        appScope.launch {
            telemetryCollector.collect(offerEventPipeline.offers)
        }
        // Enqueue the idempotent 12h periodic upload (network-constrained, with backoff).
        telemetryScheduler.ensurePeriodic()
    }
}
