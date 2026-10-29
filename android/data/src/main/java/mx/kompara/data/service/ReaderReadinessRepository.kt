package mx.kompara.data.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface ReaderMemoryStore {
    fun load(): ReaderMemory
    fun save(memory: ReaderMemory)
}

/** One process owner; only evidence, pause and incident metadata survive a process restart.
 * Live permission/binding are deliberately re-observed. No screen content is persisted here. */
@Singleton
class ReaderReadinessRepository(
    private val store: ReaderMemoryStore,
    private val clock: Clock,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        PreferencesReaderMemoryStore(context.getSharedPreferences("reader_readiness", Context.MODE_PRIVATE)),
        Clock.systemDefaultZone(),
    )

    private var memory = store.load()
    private var signals = ReaderSignals()
    private var generation = 0L
    private val mutable = MutableStateFlow(project())
    val state: StateFlow<ReaderReadiness> = mutable.asStateFlow()

    private fun project() = ReaderReadinessReducer.reduce(memory, signals, clock.millis(), clock.zone)
    private fun publish(next: ReaderMemory = memory) {
        if (next != memory) { store.save(next); memory = next }
        mutable.value = project()
    }
    @Synchronized fun refresh() {
        val wasPaused = mutable.value.paused
        publish()
        if (wasPaused != mutable.value.paused) generation++
    }
    @Synchronized fun captureToken(): Long = generation
    @Synchronized fun canRead(app: String? = null, token: Long = generation): Boolean {
        refresh()
        return token == generation && state.value.canRead && (app == null || app in signals.supportedApps)
    }
    @Synchronized fun observeEnvironment(permission: Boolean, batteryRestricted: Boolean) {
        if (signals.permission == true && !permission) generation++
        val restored = signals.permission == false && permission
        signals = signals.copy(permission = permission, batteryRestricted = batteryRestricted,
            binding = signals.binding.takeIf { permission },
            disconnected = if (restored) false else signals.disconnected,
            captureRunning = if (permission) signals.captureRunning else false)
        val cause = when {
            !permission && memory.everBound -> ReaderIncidentCause.PERMISSION_LOST
            permission && memory.incident?.cause == ReaderIncidentCause.PERMISSION_LOST -> null
            else -> memory.incident?.cause
        }
        publish(withIncident(cause))
    }
    @Synchronized fun bound() {
        generation++
        signals = signals.copy(permission = true, binding = UUID.randomUUID().toString(),
            disconnected = false, captureRunning = null)
        // Permission restored is prepared until a new host read, regardless of yesterday's history.
        publish(memory.copy(everBound = true, incident = null))
    }
    @Synchronized fun disconnected(permission: Boolean) {
        generation++
        signals = signals.copy(permission = permission, binding = null, disconnected = true, captureRunning = false)
        publish(withIncident(if (permission) ReaderIncidentCause.SERVICE_DISCONNECTED else ReaderIncidentCause.PERMISSION_LOST))
    }
    @Synchronized fun captureRunning(running: Boolean, intentionalStop: Boolean = false) {
        val stopped = signals.captureRunning == true && !running && signals.binding != null && signals.permission == true
        signals = signals.copy(captureRunning = running)
        if (stopped) generation++
        publish(when {
            stopped && !intentionalStop -> withIncident(ReaderIncidentCause.CAPTURE_STOPPED)
            running && memory.incident?.cause == ReaderIncidentCause.CAPTURE_STOPPED -> memory.copy(incident = null)
            else -> memory
        })
    }
    @Synchronized fun supportedApps(apps: Set<String>) {
        if (signals.supportedApps != apps) generation++
        signals = signals.copy(supportedApps = apps.intersect(ReaderReadinessReducer.apps))
        publish()
    }
    @Synchronized fun readOffer(app: String, session: String, at: Long, token: Long) {
        if (!canRead(app, token) || at > clock.millis()) return
        val old = memory.lastRead[app]
        if (old?.session == session && old.binding == signals.binding && old.failures == 0 &&
            java.time.Instant.ofEpochMilli(old.at).atZone(clock.zone).toLocalDate() ==
            java.time.Instant.ofEpochMilli(at).atZone(clock.zone).toLocalDate()) return
        if (old != null && at < old.at) return
        publish(memory.copy(lastRead = memory.lastRead + (app to AppReadProof(at, requireNotNull(signals.binding), session))))
    }
    /** Only a failed offer-shaped frame counts; an idle map is not a parser failure. */
    @Synchronized fun failedOffer(app: String, token: Long) {
        if (!canRead(app, token)) return
        val old = memory.lastRead[app] ?: return
        publish(memory.copy(lastRead = memory.lastRead + (app to old.copy(failures = (old.failures + 1).coerceAtMost(ReaderReadinessReducer.FAILURE_LIMIT)))))
    }
    @Synchronized fun pauseUntilTomorrow() {
        generation++
        publish(memory.copy(pauseUntil = LocalDate.now(clock).plusDays(1)))
    }
    /** Explicit local erase invalidates queued reads and removes their persisted proof. */
    @Synchronized fun forgetHistoryAndPause() {
        generation++
        publish(memory.copy(lastRead = emptyMap(), suppressionCount = 0, suppressionDate = null,
            pauseUntil = LocalDate.now(clock).plusDays(1)))
    }
    @Synchronized fun resume() {
        generation++
        publish(memory.copy(pauseUntil = null))
    }
    @Synchronized fun suppressionCount(date: LocalDate, count: Int) {
        publish(memory.copy(suppressionDate = date, suppressionCount = count.coerceAtLeast(0)))
    }
    /** Mark only a successfully delivered notice. Denial must not consume an incident. */
    @Synchronized fun markNotified(incident: ReaderIncident) {
        if (!state.value.paused && memory.incident == incident) publish(memory.copy(incident = incident.copy(notified = true)))
    }
    private fun withIncident(cause: ReaderIncidentCause?): ReaderMemory = memory.copy(incident = when {
        cause == null -> null
        memory.incident?.cause == cause -> memory.incident
        else -> ReaderIncident(cause, clock.millis())
    })
}

class PreferencesReaderMemoryStore(private val prefs: SharedPreferences) : ReaderMemoryStore {
    override fun load(): ReaderMemory = runCatching {
        val json = JSONObject(prefs.getString("evidence", "{}") ?: "{}")
        val reads = json.optJSONObject("reads") ?: JSONObject()
        ReaderMemory(json.optBoolean("everBound"), json.optString("pause").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            reads.keys().asSequence().associateWith { app -> reads.getJSONObject(app).let {
                AppReadProof(it.getLong("at"), it.getString("binding"), it.getString("session"), it.optInt("failures"))
            } }, json.optJSONObject("incident")?.let {
                ReaderIncident(ReaderIncidentCause.valueOf(it.getString("cause")), it.getLong("at"), it.optBoolean("notified"))
            }, json.optString("date").takeIf { it.isNotEmpty() }?.let(LocalDate::parse), json.optInt("count"))
    }.getOrDefault(ReaderMemory())
    override fun save(memory: ReaderMemory) {
        val reads = JSONObject()
        memory.lastRead.forEach { (app, proof) -> reads.put(app, JSONObject().put("at", proof.at)
            .put("binding", proof.binding).put("session", proof.session).put("failures", proof.failures)) }
        val json = JSONObject().put("everBound", memory.everBound).put("pause", memory.pauseUntil?.toString())
            .put("reads", reads).put("date", memory.suppressionDate?.toString()).put("count", memory.suppressionCount)
        memory.incident?.let { json.put("incident", JSONObject().put("cause", it.cause.name).put("at", it.firstSeenAt).put("notified", it.notified)) }
        prefs.edit().putString("evidence", json.toString()).apply()
    }
}
