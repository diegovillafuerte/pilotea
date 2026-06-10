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
import mx.kompara.billing.EntitlementRepository
import mx.kompara.capture.OfferEventPipeline
import mx.kompara.capture.lifecycle.OfferEventLifecycleMapper
import mx.kompara.capture.lifecycle.TripLifecycleTracker
import mx.kompara.capture.telemetry.TelemetryCollector
import mx.kompara.sync.rollup.RollupWorker
import mx.kompara.sync.aggregate.AggregateSyncScheduler
import mx.kompara.sync.config.PaywallConfigRepository
import mx.kompara.sync.spec.SpecConfigRefreshWorker
import mx.kompara.sync.spec.SpecConfigRepository
import mx.kompara.sync.telemetry.TelemetryScheduler
import mx.kompara.ui.fiscal.FiscalMonthEndScheduler
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

    // B-043 consented aggregate sync + benchmark delivery.
    @Inject lateinit var aggregateSyncScheduler: AggregateSyncScheduler

    // B-039 auto trip-log bootstrap collaborators.
    @Inject lateinit var lifecycleMapper: OfferEventLifecycleMapper
    @Inject lateinit var tripLifecycleTracker: TripLifecycleTracker

    // B-033 OTA parser-config collaborators.
    @Inject lateinit var specConfigRepository: SpecConfigRepository
    @Inject lateinit var workManager: WorkManager

    // B-036 service-health watchdog (alerts when an OEM task killer disables the reader).
    @Inject lateinit var serviceWatchdog: ServiceWatchdog

    // B-049 Play Billing entitlement. Hydrates last-known (offline grace) then tracks live purchases.
    @Inject lateinit var entitlementRepository: EntitlementRepository

    // B-051 month-end IMSS summary: daily check + a run on this app open.
    @Inject lateinit var fiscalMonthEndScheduler: FiscalMonthEndScheduler

    // B-050 remote paywall kill switch: refresh the cached flag on app open (TTL-gated, best-effort).
    @Inject lateinit var paywallConfigRepository: PaywallConfigRepository

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

        // B-043: enqueue the idempotent 12h periodic consented aggregate sync + benchmark refresh.
        // The upload leg short-circuits unless signed-in AND opted-in; the benchmark download leg
        // (TTL-gated, offline-tolerant) keeps the percentile cache fresh regardless.
        aggregateSyncScheduler.ensurePeriodic()

        // B-039: build the driver's automatic ledger from the same capture stream. The tracker
        // observes coalesced snapshots (hot SharedFlow → emits nothing until the service connects),
        // infers offers/trips/shifts, and triggers rollups on trip close. App-scoped so a transient
        // failure never crashes the app or stops capture, mirroring the telemetry collector.
        appScope.launch {
            tripLifecycleTracker.collect(lifecycleMapper.signals())
        }
        // Daily background recompute keeps aggregates fresh even when no trip closes (e.g. an open
        // shift crossing midnight); on-trip-close one-shots handle the prompt case.
        RollupWorker.schedulePeriodic(workManager)

        // Fetch the freshest signed parser bundle now (so a kill switch applies this session), then
        // let the periodic worker keep it current. refresh() never throws and no-ops when nothing is
        // newer (B-033).
        appScope.launch { specConfigRepository.refresh() }
        SpecConfigRefreshWorker.schedule(workManager)

        // Watch reader-service health once onboarding completes; alert (notification + in-app
        // banner) if an OEM task killer disables the accessibility service mid-shift (B-036).
        serviceWatchdog.start(appScope)

        // B-049: hydrate last-known entitlement (offline grace) then track live Play purchases —
        // acknowledging + syncing to the backend. App-scoped so a transient failure never crashes
        // the app; when Play is unavailable the repo logs loudly and stays on the last-known value.
        entitlementRepository.start(appScope)

        // B-050: refresh the cached paywall kill switch so a promo flip applies this session. TTL-gated
        // and best-effort — a failed fetch keeps the last-known flag (or the gating-ON default), so a
        // network hiccup never accidentally unlocks premium.
        appScope.launch { paywallConfigRepository.refresh() }

        // B-051: keep the daily month-end IMSS check enqueued, and run one now (the "next app open"
        // path) so a just-ended month's summary fires promptly. Both are idempotent via the per-month
        // watermark, so an extra run never double-posts.
        fiscalMonthEndScheduler.ensureScheduled()
        fiscalMonthEndScheduler.runNow()
    }
}
