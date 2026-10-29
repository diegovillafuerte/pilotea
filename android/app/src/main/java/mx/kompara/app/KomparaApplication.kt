package mx.kompara.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import mx.kompara.billing.EntitlementRepository
import mx.kompara.capture.OfferEventPipeline
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.capture.lifecycle.OfferEventLifecycleMapper
import mx.kompara.capture.lifecycle.TripLifecycleTracker
import mx.kompara.capture.telemetry.TelemetryCollector
import mx.kompara.sync.rollup.RollupWorker
import mx.kompara.sync.aggregate.AggregateSyncScheduler
import mx.kompara.sync.config.PaywallConfigRepository
import mx.kompara.sync.spec.SpecConfigRefreshWorker
import mx.kompara.sync.spec.SpecConfigRepository
import mx.kompara.sync.verification.VerificationStatusRepository
import mx.kompara.sync.telemetry.TelemetryScheduler
import mx.kompara.ui.fiscal.FiscalMonthEndScheduler
import mx.kompara.ui.onboarding.ServiceWatchdog
import mx.kompara.ui.share.WeekCloseScheduler
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
    @Inject lateinit var settingsRepository: mx.kompara.data.settings.SettingsRepository

    // B-043 consented aggregate sync + benchmark delivery.
    @Inject lateinit var aggregateSyncScheduler: AggregateSyncScheduler

    // B-039 auto trip-log bootstrap collaborators.
    @Inject lateinit var lifecycleMapper: OfferEventLifecycleMapper
    @Inject lateinit var tripLifecycleTracker: TripLifecycleTracker

    // B-033 OTA parser-config collaborators.
    @Inject lateinit var specConfigRepository: SpecConfigRepository
    @Inject lateinit var workManager: WorkManager

    // B-036 service-health watchdog (alerts when an OEM task killer disables the reader).
    @Inject lateinit var readerReadinessMonitor: mx.kompara.capture.ReaderReadinessMonitor

    @Inject lateinit var serviceWatchdog: ServiceWatchdog

    // B-049 Play Billing entitlement. Hydrates last-known (offline grace) then tracks live purchases.
    @Inject lateinit var entitlementRepository: EntitlementRepository

    // Factual month review: daily check plus catchup on app open.
    @Inject lateinit var fiscalMonthEndScheduler: FiscalMonthEndScheduler

    // B-050 remote paywall kill switch: refresh the cached flag on app open (TTL-gated, best-effort).
    @Inject lateinit var paywallConfigRepository: PaywallConfigRepository

    // PR-E import/data verification: refresh the cached /v1/me `verified` flag on app open (TTL-gated).
    @Inject lateinit var verificationStatusRepository: VerificationStatusRepository

    // Dated week summary: Monday-09:00 check plus catchup on app open.
    @Inject lateinit var weekCloseScheduler: WeekCloseScheduler

    @Inject lateinit var dailySummaryScheduler: mx.kompara.ui.notifications.DailySummaryScheduler
    @Inject lateinit var importReminderScheduler: mx.kompara.ui.notifications.ImportReminderScheduler

    // These collectors share the accessibility service's process. SupervisorJob alone only
    // isolates siblings; a handler is also required to contain an uncaught collector failure.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default +
        CoroutineExceptionHandler { context, error ->
            val component = context[CoroutineName]?.name ?: "application-worker"
            android.util.Log.e("KomparaCapture", "$component stopped after ${error.javaClass.simpleName}")
            context[kotlinx.coroutines.Job]?.cancel()
        })

    private fun componentScope(name: String) = CoroutineScope(appScope.coroutineContext + CoroutineName(name))

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { runCatching { mx.kompara.ui.export.ExportCache.prune(this@KomparaApplication) } }

        // Observe the offer-event flow and accumulate privacy-safe counters. No screen content
        // enters telemetry — see TelemetryCollector (B-034).
        appScope.launch(CoroutineName("parse-telemetry")) {
            telemetryCollector.collect(offerEventPipeline.offers)
        }
        // A restart must not re-arm work the driver revoked in Tus datos.
        appScope.launch(CoroutineName("sync-scheduling")) {
            if (settingsRepository.isTelemetryEnabled()) telemetryScheduler.ensurePeriodic()
            if (settingsRepository.isShareAggregatesEnabled()) aggregateSyncScheduler.ensurePeriodic()
        }

        // B-039: build the driver's automatic ledger from the capture stream. The tracker infers
        // offers/trips/shifts and triggers rollups on trip close. App-scoped so a transient failure
        // never crashes the app or stops capture, mirroring the telemetry collector.
        //
        // Two sources, merged: the node-path mapper (for any future accessibility-readable host) and
        // the OCR path (design §7.1 — the only source that sees real offer cards today, since
        // DiDi/inDrive/Uber all render them outside the node tree). The mapper drops OCR-owned
        // packages, so the two never both speak for one platform.
        appScope.launch(CoroutineName("trip-ledger")) {
            tripLifecycleTracker.collect(
                merge(lifecycleMapper.signals(), OcrLifecycleBus.signals),
            )
        }
        // Daily background recompute keeps aggregates fresh even when no trip closes (e.g. an open
        // shift crossing midnight); on-trip-close one-shots handle the prompt case.
        RollupWorker.schedulePeriodic(workManager)
        // B-081: refresh the trailing-window CAPTURED aggregates on app open so the ledger-repair
        // migration (v4, which purges phantom + uptime-dated rows) is reflected promptly — e.g. an
        // uptime-dated open shift that had over-counted online hours. Idempotent (trailing-window
        // recompute, REPLACE policy), mirroring the fiscal/week-close "ensureScheduled + runNow".
        RollupWorker.triggerOnce(workManager)

        // Fetch the freshest signed parser bundle now (so a kill switch applies this session), then
        // let the periodic worker keep it current. refresh() never throws and no-ops when nothing is
        // newer (B-033).
        appScope.launch { specConfigRepository.refresh() }
        SpecConfigRefreshWorker.schedule(workManager)

        // Watch reader-service health once onboarding completes; alert (notification + in-app
        // banner) if an OEM task killer disables the accessibility service mid-shift (B-036).
        readerReadinessMonitor.start(componentScope("reader-readiness"))
        serviceWatchdog.start(componentScope("reader-watchdog"))

        // B-049: hydrate last-known entitlement (offline grace) then track live Play purchases —
        // acknowledging + syncing to the backend. App-scoped so a transient failure never crashes
        // the app; when Play is unavailable the repo logs loudly and stays on the last-known value.
        entitlementRepository.start(componentScope("billing-entitlement"))

        // B-050: refresh the cached paywall kill switch so a promo flip applies this session. TTL-gated
        // and best-effort — a failed fetch keeps the last-known flag (or the gating-ON default), so a
        // network hiccup never accidentally unlocks premium.
        appScope.launch { paywallConfigRepository.refresh() }

        // PR-E: refresh the cached import/data-verification flag (GET /v1/me) so a fresh verify/revoke
        // applies this session. TTL-gated + best-effort — a failed fetch keeps the last-known flag
        // (sticky-positive), so a transient blip never re-locks a verified driver's benchmarks/compare.
        appScope.launch { verificationStatusRepository.refresh() }

        // Keep the factual month-review check enqueued, and run one now (the "next app open"
        // path) so a just-ended month's summary fires promptly. Both are idempotent via the per-month
        // watermark, so an extra run never double-posts.
        fiscalMonthEndScheduler.ensureScheduled()
        fiscalMonthEndScheduler.runNow()

        // Keep the weekly Monday-09:00 summary enqueued, and run one now (the "next app
        // open" path) so a just-closed week's reminder fires even if the device was off all Monday.
        // Both are idempotent via the per-week watermark, so an extra run never double-posts.
        weekCloseScheduler.ensureScheduled()
        weekCloseScheduler.runNow()
        dailySummaryScheduler.ensureScheduled()
        dailySummaryScheduler.runNow()
        importReminderScheduler.start(componentScope("import-reminder"))
    }
}
