package mx.kompara.data.service

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reading evidence is separate from permission, binding, capture capability and presentation. */
enum class ReaderPhase { OFF, PREPARED, WORKING, ATTENTION, PAUSED }
enum class ReaderIncidentCause { PERMISSION_LOST, SERVICE_DISCONNECTED, CAPTURE_STOPPED }
data class ReaderIncident(val cause: ReaderIncidentCause, val firstSeenAt: Long, val notified: Boolean = false)
data class AppReadProof(val at: Long, val binding: String, val session: String, val failures: Int = 0)
data class ReaderMemory(
    val everBound: Boolean = false,
    val pauseUntil: LocalDate? = null,
    val lastRead: Map<String, AppReadProof> = emptyMap(),
    val incident: ReaderIncident? = null,
    val suppressionDate: LocalDate? = null,
    val suppressionCount: Int = 0,
)
data class ReaderSignals(
    val permission: Boolean? = null,
    val binding: String? = null,
    val disconnected: Boolean = false,
    val captureRunning: Boolean? = null,
    val batteryRestricted: Boolean = false,
    val supportedApps: Set<String> = emptySet(),
)
data class AppReadiness(val supported: Boolean, val lastReadAt: Long?, val verifiedToday: Boolean)
data class ReaderReadiness(
    val phase: ReaderPhase,
    val permission: Boolean?,
    val bound: Boolean,
    val captureRunning: Boolean,
    val batteryRestricted: Boolean,
    val apps: Map<String, AppReadiness>,
    val incident: ReaderIncident?,
    val date: LocalDate,
    val noRoomCount: Int,
) {
    val paused get() = phase == ReaderPhase.PAUSED
    val canRead get() = !paused && permission == true && bound
    val canShowStoppedTab get() = canRead && incident?.cause == ReaderIncidentCause.CAPTURE_STOPPED
    val alertIncident get() = incident?.takeUnless { paused }
}

object ReaderReadinessReducer {
    const val UBER = "com.ubercab.driver"
    const val DIDI = "com.didiglobal.driver"
    const val INDRIVE = "sinet.startup.inDriver"
    const val FAILURE_LIMIT = 3
    val apps = setOf(UBER, DIDI, INDRIVE)

    fun reduce(memory: ReaderMemory, signals: ReaderSignals, now: Long, zone: ZoneId): ReaderReadiness {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val paused = memory.pauseUntil?.let { date < it } == true
        val appStates = apps.associateWith { app ->
            val proof = memory.lastRead[app]
            AppReadiness(app in signals.supportedApps, proof?.at,
                app in signals.supportedApps && proof != null && proof.at <= now &&
                    proof.binding == signals.binding && proof.failures < FAILURE_LIMIT &&
                    Instant.ofEpochMilli(proof.at).atZone(zone).toLocalDate() == date)
        }
        val working = signals.permission == true && signals.binding != null &&
            signals.captureRunning != false && appStates.values.any { it.verifiedToday }
        val phase = when {
            paused -> ReaderPhase.PAUSED
            signals.permission == false || signals.disconnected ||
                memory.incident != null -> ReaderPhase.OFF
            working && signals.batteryRestricted -> ReaderPhase.ATTENTION
            working -> ReaderPhase.WORKING
            else -> ReaderPhase.PREPARED
        }
        return ReaderReadiness(phase, signals.permission, signals.binding != null,
            signals.captureRunning == true, signals.batteryRestricted, appStates, memory.incident,
            date, if (!paused && memory.suppressionDate == date) memory.suppressionCount.coerceAtLeast(0) else 0)
    }
}
