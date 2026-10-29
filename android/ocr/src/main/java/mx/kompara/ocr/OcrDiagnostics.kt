package mx.kompara.ocr

import android.os.SystemClock
import android.util.Log

/** Coarse reading evidence only: no recognized text or extracted payment/route values. */
internal data class OcrReadingEvidence(
    val blocks: Int,
    val ownUi: Boolean,
    val signature: Boolean,
    val parsed: Boolean,
    val paymentRead: Boolean,
) {
    fun rejection(): OcrRejectionReason? = when {
        ownUi -> OcrRejectionReason.OWN_UI
        blocks == 0 -> OcrRejectionReason.EMPTY_OCR
        !signature -> OcrRejectionReason.NO_OFFER_SIGNATURE
        !parsed || !paymentRead -> OcrRejectionReason.SIGNATURE_UNPARSED
        else -> null
    }
}

internal enum class OcrRejectionReason { EMPTY_OCR, NO_OFFER_SIGNATURE, SIGNATURE_UNPARSED, OWN_UI }

/** Log only reason codes and measured geometry, never recognized screen text. */
internal class OcrDiagnostics(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val write: (String) -> Unit = { Log.d("KomparaOCR", it) },
) {
    private val lastLoggedAt = mutableMapOf<String, Long>()
    private var burstActive = false
    private var frames = 0
    private var ocrBlocks = 0
    private var signatures = 0
    private var parsed = 0
    private var publishedOffers = 0
    private var chipVisibleFrames = 0
    private val hidden = linkedMapOf<String, Int>()

    @Synchronized fun beginBurst() {
        if (burstActive) return
        burstActive = true
        frames = 0
        ocrBlocks = 0
        signatures = 0
        parsed = 0
        publishedOffers = 0
        chipVisibleFrames = 0
        hidden.clear()
    }

    @Synchronized fun frame(blocks: Int, chipVisible: Boolean) {
        beginBurst()
        frames++
        ocrBlocks += blocks
        if (chipVisible) chipVisibleFrames++
    }

    @Synchronized fun reading(evidence: OcrReadingEvidence) {
        if (evidence.signature) signatures++
        if (evidence.parsed) parsed++
        evidence.rejection()?.let {
            blocked(it.name, "blocks=${evidence.blocks} ownUi=${evidence.ownUi} " +
                "signature=${evidence.signature} parsed=${evidence.parsed} paymentRead=${evidence.paymentRead}")
        }
    }

    @Synchronized fun publishedOffer() { publishedOffers++ }

    @Synchronized fun endBurst() {
        if (!burstActive) return
        // Exactly one summary at the host-window boundary (never one per polling tick). Reason
        // counters include suppressed messages, so throttling cannot hide repeated rejection.
        write("burst: frames=$frames ocrBlocks=$ocrBlocks signature=$signatures parsed=$parsed " +
            "publishedOffers=$publishedOffers chipVisibleFrames=$chipVisibleFrames " +
            hidden.entries.joinToString(prefix = "hidden{", postfix = "}") { "${it.key}=${it.value}" })
        burstActive = false
    }

    @Synchronized fun blocked(reason: String, details: String = "") {
        if (burstActive) hidden[reason] = (hidden[reason] ?: 0) + 1
        val time = now()
        val previous = lastLoggedAt[reason]
        if (previous != null && time - previous in 0 until 5_000L) return
        lastLoggedAt[reason] = time
        write("offer visibility: $reason${if (details.isEmpty()) "" else "; $details"}")
    }
}
