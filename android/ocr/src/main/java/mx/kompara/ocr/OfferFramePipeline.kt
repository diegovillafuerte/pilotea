package mx.kompara.ocr

import android.os.SystemClock
import android.util.Log
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import java.io.File

/**
 * The shared, capture-source-agnostic frame processor for the OCR reader. Given the recognized text
 * blocks of ONE screen frame, it parses the offer card, stabilizes/guards it, publishes the verdict
 * to [OfferEventBus], and drives the B-039 trip ledger via [OcrLifecycleBus] — exactly the logic
 * that used to live inline in [OcrCaptureService].
 *
 * It is deliberately independent of WHERE the frame came from, so the two capture lanes share one
 * code path and can never drift:
 *  - [OcrCaptureService]      — MediaProjection + VirtualDisplay (API <30, and the fallback).
 *  - [SilentScreenshotLane]   — AccessibilityService.takeScreenshot (API 30+, the primary lane, no
 *                               projection consent, no cast indicator — the clean Play a11y lane).
 *
 * PRIVACY (Play data-safety posture): this class receives only in-memory OCR text; it never persists
 * screen content in release builds. The one file write (debug OCR fixtures) is gated on a non-null
 * [fixtureDir], which the lanes supply only in debug builds.
 *
 * Not thread-safe: callers must invoke [process]/[onCaptureEnd] from a single serialized context
 * (both lanes already do — the MediaProjection lane via its generation-tagged mutex, the screenshot
 * lane via its sequential capture loop).
 */
class OfferFramePipeline {

    private val didiParser = DidiOcrParser()
    private val uberParser = UberOcrParser()
    private val presence = CardPresenceTracker()
    // Smooths the OCR fare across frames of one offer so a single garbled frame (MXN137.28→37.28)
    // can't flip the chip's number/colour. Feeds ONLY the overlay chip; the B-039 ledger gets the raw
    // card so a stabilized value can't spawn a duplicate trip record.
    private val fareStabilizer = FareStabilizer()
    // Drops a single gross OCR-outlier frame (a ×10 distance that slipped past decimal recovery, or a
    // gross fare garble) before it can reach the chip, the stabilizer, OR the B-039 ledger.
    private val frameOutlierGuard = FrameOutlierGuard()
    // B-039 OCR ledger: classifies each frame into offer/trip/idle lifecycle signals.
    private val lifecycleClassifier = OcrLifecycleClassifier()
    private var lastOfferKey: String? = null
    private var lastOfferPackage: String? = null

    /**
     * Process one recognized frame. [chipRect] is the overlay chip's on-screen rect at capture time
     * (masked out so our own verdict text isn't re-parsed as the offer fare). [fixtureDir] is the base
     * dir for debug OCR fixtures, or null in release (no screen content is ever written then).
     */
    fun process(rawBlocks: List<OcrBlock>, chipRect: ScreenRect?, fixtureDir: File?) {
        // Strip our own chip's text FIRST: the capture mirrors the whole screen including the floating
        // verdict chip, whose "MXN…"/"$…" amount otherwise gets parsed as the offer fare — the
        // self-capture loop that collapsed the live verdict to $0. [chipRect] is the rect snapshotted
        // when THIS frame was captured (a null rect = chip hidden ⇒ no-op).
        val blocks = ChipMask.maskOwnChip(rawBlocks, chipRect)
        val joined = blocks.joinToString(" | ") { it.text }
        // Raw OCR text is screen content — log in debug builds only (Play data-safety posture);
        // release logcat must never expose it.
        if (BuildConfig.DEBUG) Log.d(TAG, "OCR(${blocks.size}): $joined")

        // Parse → publish to the shared bus so the accessibility-service-hosted overlay shows the
        // verdict. Dedup identical offers; emit NoCard once the card actually leaves the screen —
        // [CardPresenceTracker] holds the verdict through OCR-garbled frames (B-077). Frames of
        // Kompara's own UI are never parsed (the simulator's mock cards and our own stats would
        // feed back into the pipeline).
        val ownUi = KomparaUiGuard.isOwnUi(joined)
        // Monotonic clock for presence/outlier math (a wall-clock jump must not hide a live card or pin
        // a stale one); wall clock only for event timestamps and fixture filenames.
        val now = SystemClock.elapsedRealtime()
        // Pick the card (and the platform) for this frame; see [selectOfferCard] — Uber first, with a
        // cross-app hold so a garbled Uber frame can't be mis-attributed to DiDi via a "$0.00" pill.
        val parsed = if (ownUi) null else selectOfferCard(uberParser, didiParser, blocks)
        // Drop a single gross OCR outlier (a ×10 distance, a gross fare garble) before it reaches the
        // chip OR the ledger; the signature below still holds the verdict across it, as for any garble.
        val card = parsed?.takeIf { frameOutlierGuard.accept(it, now) }
        // The offer-card signature (fare + leg) survives the OCR garble that fails a full parse;
        // shared by the presence tracker (hold the chip) and the lifecycle classifier (hold OFFER).
        val signature = !ownUi &&
            (uberParser.hasCardSignature(blocks) || didiParser.hasCardSignature(blocks))
        // Replace the raw parsed fare with the stabilized one so a one-frame OCR garble can't flip
        // the verdict. Keyed on the trip leg (stable), not the fare. Null when no card this frame.
        val shownCard = card?.let { fareStabilizer.onParsed(it, System.currentTimeMillis()) }
        if (shownCard != null) {
            presence.onParsed(now)
            // Re-assert on EVERY successful parse, not only on a new offer: a Parsed on the bus
            // cancels any in-flight hide, so a stray NoCard from any other source can blank the
            // chip for at most one OCR frame (~300 ms). Downstream distinctUntilChanged collapses
            // identical verdicts, so steady re-assertion causes no re-render churn.
            OfferEventBus.tryEmit(
                OfferEvent.Parsed(shownCard.platform, System.currentTimeMillis(), shownCard),
            )
            lastOfferPackage = shownCard.platform
            val key = "${shownCard.platform}_${shownCard.fare}_${shownCard.pickupDistanceKm}_${shownCard.tripDistanceKm}"
            if (key != lastOfferKey) {
                lastOfferKey = key
                // Fare/distance are screen-derived — debug-only log (see process()'s note).
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "emitted ${shownCard.platform} offer: fare=${shownCard.fare} pickup=${shownCard.pickupDistanceKm}km trip=${shownCard.tripDistanceKm}km")
                }
            }
        } else if (lastOfferKey != null) {
            if (presence.onMiss(signature, now)) {
                // Hide the chip for whichever platform's card we last showed (NOT always DiDi).
                val gonePackage = lastOfferPackage ?: DidiOcrParser.DIDI_PACKAGE
                lastOfferKey = null
                lastOfferPackage = null
                // Card truly gone → forget the session so the next offer paints fresh and is judged
                // only against itself.
                fareStabilizer.reset()
                frameOutlierGuard.reset()
                OfferEventBus.tryEmit(
                    OfferEvent.NoCard(
                        gonePackage,
                        System.currentTimeMillis(),
                        OfferEvent.Reason.NOT_AN_OFFER,
                    ),
                )
            }
        }

        // B-039 OCR ledger: drive the trip lifecycle (offer → accept → trip → close) from the same
        // frames. Transition-deduped inside the classifier, so this is a no-op on most frames. Never
        // for our own UI (the classifier would mis-read Kompara's screens as idle and close a trip).
        // Attribute + gate the ledger by the foreground host: the capture sees the WHOLE screen, so a
        // non-host app (WhatsApp, the shade) must NOT feed the trip ledger. A parsed card's platform is
        // the definitive host; otherwise use the accessibility service's fresh foreground target app
        // (B-039 §7.1). Null → a non-host screen → skip.
        val hostPackage = card?.platform
            ?: ScreenReaderState.freshForegroundHost(SystemClock.elapsedRealtime(), HOST_FOREGROUND_FRESHNESS_MS)
        if (!ownUi && hostPackage != null) {
            // presence.isPresent() is the DEBOUNCED "card on screen" state (held through garble),
            // so the offer session doesn't split on a single OCR dropout — same policy as the chip.
            // The ledger gets the RAW card — fare-stabilization is a chip-display concern.
            lifecycleClassifier.onFrame(joined, card, presence.isPresent(), hostPackage, System.currentTimeMillis())
                ?.let { OcrLifecycleBus.tryEmit(it) }
        }

        // Record offer frames for fixture building / spec hardening — debug builds only (never our
        // own UI). Release builds must store NO screen content, per the Play data-safety posture.
        if (BuildConfig.DEBUG && fixtureDir != null && !ownUi && looksLikeOffer(joined)) {
            runCatching {
                val dir = File(fixtureDir, "ocr-fixtures").apply { mkdirs() }
                File(dir, "ocr_${System.currentTimeMillis()}.json").writeText(blocksToJson(blocks))
            }
        }
    }

    /**
     * Close the capture session (screen lock / re-consent / driver leaves the host app). Never strands
     * a verdict — the overlay only hides on bus events, so if capture ends mid-offer we emit the NoCard
     * ourselves — and closes the OCR ledger session so a pending offer/open trip doesn't dangle. Resets
     * every stateful collaborator so the next session begins clean.
     */
    fun onCaptureEnd(nowWallMs: Long) {
        if (lastOfferKey != null) {
            val gonePackage = lastOfferPackage ?: DidiOcrParser.DIDI_PACKAGE
            lastOfferKey = null
            lastOfferPackage = null
            OfferEventBus.tryEmit(
                OfferEvent.NoCard(gonePackage, nowWallMs, OfferEvent.Reason.NOT_AN_OFFER),
            )
        }
        lifecycleClassifier.onCaptureEnd(nowWallMs)?.let { OcrLifecycleBus.tryEmit(it) }
        presence.reset()
        fareStabilizer.reset()
        frameOutlierGuard.reset()
    }

    private fun looksLikeOffer(text: String): Boolean =
        (text.contains("$") || text.contains("MXN", true)) &&
            (text.contains("km", true) || text.contains("min", true))

    private fun blocksToJson(blocks: List<OcrBlock>): String =
        blocks.joinToString(prefix = "[\n", postfix = "\n]", separator = ",\n") { b ->
            val t = b.text.replace("\\", "\\\\").replace("\"", "\\\"")
            "  {\"text\":\"$t\",\"left\":${b.bounds.left},\"top\":${b.bounds.top}," +
                "\"right\":${b.bounds.right},\"bottom\":${b.bounds.bottom}}"
        }

    companion object {
        private const val TAG = "KomparaOCR"
        // How recently the accessibility service must have seen a target app foreground for an OCR
        // frame (without a parsed card) to be attributed to the trip ledger. NEEDS ON-DEVICE CALIBRATION.
        private const val HOST_FOREGROUND_FRESHNESS_MS = 8_000L
    }
}
