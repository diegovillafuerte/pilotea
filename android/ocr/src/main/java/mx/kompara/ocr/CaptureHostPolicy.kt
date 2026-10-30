package mx.kompara.ocr

import mx.kompara.data.service.HostForegroundObservation

/** Capture visibility is separate from durable offer presence. Shared by both capture lanes. */
internal object CaptureHostPolicy {
    fun mustEnd(observation: HostForegroundObservation, interactive: Boolean, readingAllowed: Boolean): Boolean =
        observation == HostForegroundObservation.NonHost || !interactive || !readingAllowed
}
