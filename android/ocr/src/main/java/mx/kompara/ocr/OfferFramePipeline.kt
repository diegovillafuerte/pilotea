package mx.kompara.ocr

import android.os.SystemClock
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
class OfferFramePipeline(private val readiness: mx.kompara.data.service.ReaderReadinessRepository? = null) {

    private val didiParser = DidiOcrParser()
    private val uberParser = UberOcrParser()
    // Confirm payment only after assigning the shared overlay/ledger offer instance.
    private val fareStabilizer = FareStabilizer()
    // Drops a single gross OCR-outlier frame (a ×10 distance that slipped past decimal recovery, or a
    // gross fare garble) before it can reach the chip, the stabilizer, OR the B-039 ledger.
    private val frameOutlierGuard = FrameOutlierGuard()
    // B-039 OCR ledger: classifies each frame into offer/trip/idle lifecycle signals.
    private val sessions = OcrOfferSessionTracker()
    private var currentHost: String? = null
    private val lifecycleClassifier = OcrLifecycleClassifier()
    private var lastOfferKey: String? = null
    private var lastOfferPackage: String? = null
    private val diagnostics = OcrDiagnostics()

    /** Start before requesting a frame, so even a burst with no successful screenshots is reported. */
    fun onCaptureStart() { diagnostics.beginBurst() }

    /** Fixed reason codes and counts only; callers must never pass exception messages or OCR text. */
    fun captureBlocked(reason: String, consecutive: Int? = null) {
        diagnostics.blocked(reason, consecutive?.let { "consecutive=$it" } ?: "")
    }

    /**
     * Process one recognized frame. [chipRect] is the overlay chip's on-screen rect at capture time
     * (masked out so our own verdict text isn't re-parsed as the offer fare). [fixtureDir] is the base
     * dir for debug OCR fixtures, or null in release (no screen content is ever written then).
     */
    fun process(rawBlocks: List<OcrBlock>, chipRect: ScreenRect?, fixtureDir: File?,
        capturedAtMs: Long = System.currentTimeMillis(),
        hostObservation: mx.kompara.parsers.model.HostLayoutObservation? = null,
        token: Long = readiness?.captureToken() ?: 0L,
        displayBounds: mx.kompara.parsers.model.HostRect? = null,
        density: Float = 1f,
        accessibility: mx.kompara.capture.HostWindowEvidence? = null,
        appearance: HostAppearanceSamples? = null,
        foregroundPackage: String? = ScreenReaderState.freshForegroundHost(
            SystemClock.elapsedRealtime(), HOST_FOREGROUND_FRESHNESS_MS)) {
        diagnostics.frame(rawBlocks.size, chipRect != null)
        if (readiness?.canRead(token = token) == false) {
            diagnostics.blocked("pipeline readiness/canRead false or stale capture token")
            return
        }
        // Strip our own chip's text FIRST: the capture mirrors the whole screen including the floating
        // verdict chip, whose "MXN…"/"$…" amount otherwise gets parsed as the offer fare — the
        // self-capture loop that collapsed the live verdict to $0. [chipRect] is the rect snapshotted
        // when THIS frame was captured (a null rect = chip hidden ⇒ no-op).
        val blocks = ChipMask.maskOwnChip(rawBlocks, chipRect)
        val joined = blocks.joinToString(" | ") { it.text }
        // Recognized screen text stays out of logcat, including debug device diagnostics.

        // Parse → publish to the shared bus so the accessibility-service-hosted overlay shows the
        // verdict. Dedup identical offers; emit NoCard once the card actually leaves the screen —
        // [CardPresenceTracker] holds the verdict through OCR-garbled frames (B-077). Frames of
        // Kompara's own UI are never parsed (the simulator's mock cards and our own stats would
        // feed back into the pipeline).
        val ownUi = KomparaUiGuard.isOwnUi(joined)
        // Monotonic clock for presence/outlier math (a wall-clock jump must not hide a live card or pin
        // a stale one); wall clock only for event timestamps and fixture filenames.
        val now = SystemClock.elapsedRealtime()
        // OS foreground identity is the only parser selector. A capture from another app cannot
        // turn masked Uber text into a DiDi offer. The lanes snapshot and recheck this host.
        val hostPackage = foregroundPackage?.takeIf {
            it == UberOcrParser.UBER_PACKAGE || it == DidiOcrParser.DIDI_PACKAGE
        } ?: run {
            fareStabilizer.breakCandidateAgreement()
            sessions.miss(cardLike = true, nowMs = now)
            return
        }
        if (currentHost != null && currentHost != hostPackage) onCaptureEnd(capturedAtMs)
        currentHost = hostPackage
        val observedAbsent = hostObservation?.geometry?.let {
            it.capturedAtMs == capturedAtMs && it.isIdleHost && it.packageName == hostPackage &&
                (displayBounds == null || it.displayBounds == displayBounds)
        } == true
        val parsed = if (ownUi || observedAbsent) null else selectOfferCard(uberParser, didiParser,
            blocks, capturedAtMs, hostObservation, displayBounds = displayBounds, density = density,
            accessibility = accessibility, appearance = appearance, chipRect = chipRect,
            foregroundPackage = hostPackage)
        parsed?.let {
            if (it.hostGeometry == null) diagnostics.blocked("geometry missing",
                "display=$displayBounds capturedAtMs=$capturedAtMs ageMs=${System.currentTimeMillis() - capturedAtMs}")
        }
        val signature = !ownUi && !observedAbsent && when (hostPackage) {
            UberOcrParser.UBER_PACKAGE -> uberParser.hasCardSignature(blocks, hostConfirmed = true)
            else -> didiParser.hasCardSignature(blocks)
        }
        val decision = parsed?.takeIf { readiness?.canRead(it.platform, token) != false }
            ?.let { sessions.update(it, capturedAtMs, now) }
        if (decision?.replaced == true) {
            fareStabilizer.reset()
            frameOutlierGuard.reset()
        }
        val card = decision?.card?.takeIf {
            val accepted = frameOutlierGuard.accept(parsed, now)
            if (!accepted) diagnostics.blocked("OCR outlier withheld")
            accepted
        }
        var disappeared = false
        if (card == null) {
            fareStabilizer.breakCandidateAgreement()
            // Own UI and inaccessible/occluded evidence cannot corroborate host disappearance.
            val accessibleCard = accessibility?.let {
                it.packageName == hostPackage && it.observedCardBounds != null &&
                    capturedAtMs - it.capturedAtMs in 0..1_000
            } == true
            val obscured = sessions.obscuredBy(chipRect?.let {
                mx.kompara.parsers.model.HostRect(it.left, it.top, it.right, it.bottom)
                    .expanded(ChipMask.DEFAULT_MARGIN_PX)
            })
            disappeared = sessions.miss(signature || ownUi || accessibleCard || obscured, now, observedAbsent)
            if (!disappeared && chipRect != null && (sessions.isPresent() || parsed != null)) {
                OfferEventBus.tryEmit(OfferEvent.LayoutUncertain(hostPackage, capturedAtMs))
            }
        }
        diagnostics.reading(OcrReadingEvidence(blocks.size, ownUi, signature, parsed != null, parsed?.fare != null))
        if (signature && (parsed == null || parsed.fare == null)) {
            diagnostics.blocked("incomplete reading", "parsed=${parsed != null} paymentRead=${parsed?.fare != null}")
        }
        // One instance ID reaches both consumers; only confirmed payment is allowed onto either bus.
        // Confirmation uses monotonic time; session/event/ledger timestamps remain epoch time.
        val shownCard = card?.let { fareStabilizer.onParsed(it, now,
            confident = decision.confidence == OcrOfferSessionTracker.Confidence.CONFIDENT) }
        if (shownCard != null) {
            if (shownCard.fare != null) readiness?.readOffer(shownCard.platform,
                shownCard.sessionId ?: "${shownCard.platform}:$capturedAtMs", capturedAtMs, token)
            // Re-assert on EVERY successful parse, not only on a new offer: a Parsed on the bus
            // cancels any in-flight hide, so a stray NoCard from any other source can blank the
            // chip for at most one OCR frame (~300 ms). Downstream distinctUntilChanged collapses
            // identical verdicts, so steady re-assertion causes no re-render churn.
            OfferEventBus.tryEmit(
                OfferEvent.Parsed(shownCard.platform, capturedAtMs, shownCard),
            )
            diagnostics.publishedOffer()
            lastOfferPackage = shownCard.platform
            val key = "${shownCard.platform}_${shownCard.fare}_${shownCard.pickupDistanceKm}_${shownCard.tripDistanceKm}"
            if (key != lastOfferKey) {
                lastOfferKey = key
            }
        } else if (lastOfferKey != null) {
            if (disappeared) {
                diagnostics.blocked(if (ownUi) "hidden: Kompara foreground" else "hidden: offer no longer detected")
                // Hide the chip for whichever platform's card we last showed (NOT always DiDi).
                val gonePackage = lastOfferPackage ?: DidiOcrParser.DIDI_PACKAGE
                lastOfferKey = null
                lastOfferPackage = null
                // Card truly gone → forget the session so the next offer paints fresh and is judged
                // only against itself.
                sessions.reset()
                fareStabilizer.reset()
                frameOutlierGuard.reset()
                OfferEventBus.tryEmit(
                    OfferEvent.NoCard(
                        gonePackage,
                        capturedAtMs,
                        OfferEvent.Reason.NOT_AN_OFFER,
                    ),
                )
            }
        }

        // B-039 OCR ledger: drive the trip lifecycle (offer → accept → trip → close) from the same
        // frames. Transition-deduped inside the classifier, so this is a no-op on most frames. Never
        // for our own UI (the classifier would mis-read Kompara's screens as idle and close a trip).
        // Attribute + gate the ledger by the foreground host: the capture sees the WHOLE screen, so a
        // non-host app (WhatsApp, the shade) must NOT feed the trip ledger. The OS host snapshot
        // selected the parser above and also owns every lifecycle event in this frame.
        if (signature && (shownCard == null || shownCard.fare == null)) {
            readiness?.failedOffer(hostPackage, token)
        }
        if (!ownUi && !(signature && shownCard == null && !sessions.isPresent()) &&
            readiness?.canRead(hostPackage, token) != false) {
            // sessions.isPresent() is the DEBOUNCED "card on screen" state (held through garble),
            // so the offer session doesn't split on a single OCR dropout — same policy as the chip.
            // Both consumers receive the same stabilized card and immutable session identity.
            lifecycleClassifier.onFrame(joined, shownCard, sessions.isPresent(), hostPackage, capturedAtMs)
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

    /** Unknown OS visibility pauses evidence agreement without ending the durable offer. */
    fun onCaptureUncertain(nowWallMs: Long) {
        fareStabilizer.breakCandidateAgreement()
        sessions.miss(cardLike = true, nowMs = SystemClock.elapsedRealtime())
        currentHost?.let { OfferEventBus.tryEmit(OfferEvent.LayoutUncertain(it, nowWallMs)) }
    }

    /**
     * Close the capture session (screen lock / re-consent / driver leaves the host app). Never strands
     * a verdict — the overlay only hides on bus events, so if capture ends mid-offer we emit the NoCard
     * ourselves — and closes the OCR ledger session so a pending offer/open trip doesn't dangle. Resets
     * every stateful collaborator so the next session begins clean.
     */
    fun onCaptureEnd(nowWallMs: Long) {
        if (lastOfferKey != null) {
            diagnostics.blocked("hidden: capture ended")
            val gonePackage = lastOfferPackage ?: DidiOcrParser.DIDI_PACKAGE
            lastOfferKey = null
            lastOfferPackage = null
            OfferEventBus.tryEmit(
                OfferEvent.NoCard(gonePackage, nowWallMs, OfferEvent.Reason.NOT_AN_OFFER),
            )
        }
        lifecycleClassifier.onCaptureEnd(nowWallMs)?.let { OcrLifecycleBus.tryEmit(it) }
        sessions.reset()
        currentHost = null
        fareStabilizer.reset()
        frameOutlierGuard.reset()
        diagnostics.endBurst()
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
        // How recently the accessibility service must have seen a target app foreground for an OCR
        // frame (without a parsed card) to be attributed to the trip ledger. NEEDS ON-DEVICE CALIBRATION.
        private const val HOST_FOREGROUND_FRESHNESS_MS = 1_500L
    }
}
