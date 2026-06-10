package mx.kompara.capture

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coalesces accessibility-event bursts into at most one [ScreenSnapshot] per quiet window.
 *
 * `TYPE_WINDOW_CONTENT_CHANGED` arrives in storms (dozens of events while a card animates in), so
 * naively snapshotting per event would hammer the parser and miss the budget. The pipeline buffers
 * raw events into a [MutableSharedFlow], debounces by [debounceMs], and only then asks the injected
 * [SnapshotReader] for the current tree — yielding exactly one snapshot per burst.
 *
 * The service stays a thin adapter: it calls [submit] and collects [snapshots]. All timing and
 * threading live here, off the main thread on the injected default dispatcher, which keeps the unit
 * under test with virtual time.
 */
@Singleton
class EventPipeline(
    private val snapshotReader: SnapshotReader,
    private val dispatcher: CoroutineDispatcher,
    private val debounceMs: Long,
) {
    /**
     * Hilt-friendly constructor that supplies the production debounce window and binds work to the
     * default dispatcher. Tests use the primary constructor with a test dispatcher + virtual time.
     */
    @Inject
    constructor(
        snapshotReader: SnapshotReader,
        @mx.kompara.data.di.DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(snapshotReader, dispatcher, DEFAULT_DEBOUNCE_MS)

    private val events = MutableSharedFlow<CaptureEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _snapshots = MutableSharedFlow<ScreenSnapshot>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One snapshot per coalesced burst of host-app window events. */
    val snapshots: SharedFlow<ScreenSnapshot> = _snapshots.asSharedFlow()

    /**
     * The debounced snapshot stream as a cold [Flow]; useful in tests that want to drive the
     * pipeline deterministically. [start] wires this into [snapshots] for production collectors.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val debouncedSnapshots: Flow<ScreenSnapshot> =
        events
            .debounce(debounceMs)
            .map { snapshotReader.read(it.packageName, it.timestampMs) }
            .filterNotNull()
            .flowOn(dispatcher)

    /**
     * Begin forwarding coalesced snapshots into [snapshots] on [scope]. Call once when the service
     * connects. Returns the [scope] for convenience.
     */
    fun start(scope: CoroutineScope): CoroutineScope {
        debouncedSnapshots
            .onEach { _snapshots.emit(it) }
            .launchIn(scope)
        return scope
    }

    /** Feed one raw host-app event into the pipeline. Non-blocking; never drops the newest event. */
    fun submit(event: CaptureEvent) {
        events.tryEmit(event)
    }

    companion object {
        /**
         * Debounce window for content-changed storms. Mid-range hardware can flatten a tree well
         * inside the remaining budget; see the <50ms snapshot-latency target in techdebt.md.
         */
        const val DEFAULT_DEBOUNCE_MS = 80L
    }
}

/** A raw, framework-free accessibility event: just what the pipeline needs to coalesce + time. */
data class CaptureEvent(
    val packageName: String,
    val timestampMs: Long,
)

/** Convenience for constructing a [CoroutineScope] bound to a dispatcher with a supervisor job. */
internal fun pipelineScope(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + dispatcher)
