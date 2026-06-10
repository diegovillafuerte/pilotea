package mx.kompara.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Read-only [AccessibilityService] bound to the Uber Driver and DiDi Conductor apps.
 *
 * It is intentionally thin: it forwards each qualifying [AccessibilityEvent] into the injectable
 * [EventPipeline] (which coalesces bursts and reads snapshots off the main thread) and reports its
 * connected/disconnected state to [ServiceStateRepository]. All capture logic lives in those
 * collaborators so it stays unit-testable without an emulator.
 *
 * READ-ONLY (legal requirement): this class never calls `performAction` on any node or event. It
 * only ever reads the active-window tree via [WindowSnapshotSource].
 */
@AndroidEntryPoint
class KomparaAccessibilityService : AccessibilityService() {

    @Inject lateinit var pipeline: EventPipeline

    @Inject lateinit var serviceState: ServiceStateRepository

    @Inject lateinit var snapshotSource: WindowSnapshotSource

    private val scope = pipelineScope(kotlinx.coroutines.Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Feed the pipeline from the live active window. Read-only: rootInActiveWindow only reads.
        snapshotSource.attach(WindowSnapshotSource.RootProvider { rootInActiveWindow })
        pipeline.start(scope)
        serviceState.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        // The accessibility-service XML already filters packageNames, but guard again so a
        // misconfiguration can never leak non-target events into the pipeline.
        if (pkg != UBER_DRIVER_PACKAGE && pkg != DIDI_DRIVER_PACKAGE) return

        pipeline.submit(CaptureEvent(packageName = pkg, timestampMs = event.eventTime))
    }

    override fun onInterrupt() {
        // No-op: nothing to interrupt in a read-only pipeline.
    }

    override fun onDestroy() {
        serviceState.setConnected(false)
        snapshotSource.detach()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        // NOTE: package IDs need on-device verification before launch (see techdebt.md). These are
        // the current best-known production IDs for the MX driver apps.
        const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        const val DIDI_DRIVER_PACKAGE = "com.sdu.didi.gsui"
    }
}
