package mx.kompara.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the B-034 telemetry + fixture-report endpoints. Field names match
 * the backend's zod schemas exactly.
 *
 * Privacy note: [TelemetryCounterBody] is integer counters + host/spec
 * identifiers only — no screen text. [FixtureReportBody.snapshot] carries the
 * already-PII-scrubbed snapshot structure (the on-device SnapshotScrubber ran
 * before it reached the queue).
 */

/** Body for POST /v1/telemetry — one accumulated counter bucket. */
@Serializable
data class TelemetryCounterBody(
    val hostPackage: String,
    val hostVersion: String,
    val specVersion: Int,
    val attempts: Int,
    val successes: Int,
    val failures: Int,
    /** UTC day `YYYY-MM-DD`. */
    val day: String,
)

/** A scrubbed accessibility node (structural fields only; text already masked). */
@Serializable
data class FixtureNodeDto(
    val text: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val bounds: FixtureBoundsDto? = null,
    val depth: Int = 0,
    val index: Int = 0,
)

@Serializable
data class FixtureBoundsDto(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

/** The scrubbed snapshot shape the backend validates and stores. */
@Serializable
data class FixtureSnapshotDto(
    val packageName: String,
    val timestampMs: Long = 0L,
    val versionCode: Long? = null,
    val nodes: List<FixtureNodeDto> = emptyList(),
)

/** Body for POST /v1/fixture-reports. [consent] MUST be true (explicit per-report). */
@Serializable
data class FixtureReportBody(
    val consent: Boolean,
    val hostPackage: String,
    val hostVersion: String? = null,
    val specVersion: Int? = null,
    val reason: String? = null,
    val snapshot: FixtureSnapshotDto,
)
