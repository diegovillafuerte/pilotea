package mx.kompara.ocr

import android.os.SystemClock
import mx.kompara.capture.OfferEvent
import mx.kompara.capture.OfferEventBus
import mx.kompara.capture.lifecycle.OcrLifecycleBus
import mx.kompara.data.service.ScreenReaderState
import mx.kompara.data.service.ScreenRect
import mx.kompara.parsers.model.HostGeometrySource
import mx.kompara.parsers.model.HostRect
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
    /** Visibility belongs to capture; identity, payment and lifecycle belong to each host. */
    private class HostSession {
        val fare = FareStabilizer()
        val outliers = FrameOutlierGuard()
        val offers = OcrOfferSessionTracker()
        val lifecycle = OcrLifecycleClassifier()
        var lastCardObservedAtMs = Long.MIN_VALUE
        var lastCardBounds: HostRect? = null
        var lastCardDisplay: HostRect? = null
        var semanticCardWindowId: Int? = null
    }
    private val hosts = mutableMapOf<String, HostSession>()
    private var currentHost: String? = null
    private val active get() = hosts.getValue(requireNotNull(currentHost))
    private val fareStabilizer get() = active.fare
    private val frameOutlierGuard get() = active.outliers
    private val sessions get() = active.offers
    private val lifecycleClassifier get() = active.lifecycle
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
            SystemClock.elapsedRealtime(), HOST_FOREGROUND_FRESHNESS_MS),
        windowOwnership: mx.kompara.capture.HostWindowOwnership? = null) {
        diagnostics.frame(rawBlocks.size, chipRect != null)
        if (readiness?.canRead(token = token) == false) {
            diagnostics.blocked("pipeline readiness/canRead false or stale capture token")
            return
        }
        // Monotonic clock for presence/outlier math (a wall-clock jump must not hide a live card or pin
        // a stale one); wall clock only for event timestamps and fixture filenames.
        val now = SystemClock.elapsedRealtime()
        // OS foreground identity is the only parser selector. A capture from another app cannot
        // turn masked Uber text into a DiDi offer. The lanes snapshot and recheck this host.
        val hostPackage = foregroundPackage?.takeIf {
            it == UberOcrParser.UBER_PACKAGE || it == DidiOcrParser.DIDI_PACKAGE
        } ?: run {
            onCaptureUncertain(capturedAtMs)
            return
        }
        if (currentHost != null && currentHost != hostPackage) onCaptureEnd(capturedAtMs)
        currentHost = hostPackage
        hosts.getOrPut(hostPackage) { HostSession() }
        // Every consumer, including absence/trip classification and debug fixtures, sees only
        // pixels owned by the selected OS window. Unknown ownership is loss of observation.
        val ownedBlocks = ownedOcrBlocks(rawBlocks, windowOwnership, hostPackage, capturedAtMs,
            displayBounds) ?: run {
            onCaptureUncertain(capturedAtMs)
            return
        }
        val blocks = ChipMask.maskOwnChip(ownedBlocks, chipRect)
        val joined = blocks.joinToString(" | ") { it.text }
        val ownUi = KomparaUiGuard.isOwnUi(joined)
        val ownedAccessibility = accessibility?.ownedBy(requireNotNull(windowOwnership))
        // IDLE_HOST is also synthesized for the stopped-reader tab when nodes expose no card.
        // Only an independently observed full layout can override the screenshot itself.
        val offerObservation = hostObservation?.takeUnless {
            it.geometry.isIdleHost && it.geometry.source != HostGeometrySource.HOST_LAYOUT
        }
        val observedAbsent = offerObservation?.geometry?.let {
            it.capturedAtMs == capturedAtMs && it.isIdleHost && it.packageName == hostPackage &&
                (displayBounds == null || it.displayBounds == displayBounds)
        } == true
        val parsed = if (ownUi || observedAbsent) null else selectOfferCard(uberParser, didiParser,
            blocks, capturedAtMs, offerObservation, displayBounds = displayBounds, density = density,
            accessibility = ownedAccessibility, appearance = appearance, chipRect = chipRect,
            foregroundPackage = hostPackage)?.let {
                it.copy(hostGeometry = it.hostGeometry?.withinOwnership(requireNotNull(windowOwnership)))
            }
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
            active.lastCardBounds = null
            active.semanticCardWindowId = null
        }
        val nodeBlocks = ownedAccessibility?.textBounds.orEmpty().map {
            OcrBlock(it.text, OcrBounds(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom))
        }
        val nodeCard = when (hostPackage) {
            UberOcrParser.UBER_PACKAGE -> uberParser.parse(nodeBlocks, hostConfirmed = true)
            else -> didiParser.parse(nodeBlocks)
        }
        if (signature) {
            active.lastCardObservedAtMs = maxOf(active.lastCardObservedAtMs, capturedAtMs)
            parsed?.hostGeometry?.let {
                active.lastCardBounds = it.offerCard
                active.lastCardDisplay = it.displayBounds
            }
            // A fresh poll alone proves nothing: Uber's semanticless card coexists with idle
            // chrome. Remember semantics only when the node offer matches this pixel offer.
            val pixelIdentity = parsed?.let { OcrOfferIdentity.from(it) }
            val matchingNodeOffer = parsed != null && pixelIdentity != null && nodeCard != null &&
                pixelIdentity == OcrOfferIdentity.from(nodeCard) && parsed.fare == nodeCard.fare &&
                parsed.contentBounds?.let { content ->
                    ownedAccessibility?.observedCardBounds?.contains(
                        HostRect(content.left, content.top, content.right, content.bottom)) != false
                } == true
            active.semanticCardWindowId = ownedAccessibility?.windowId?.takeIf { matchingNodeOffer }
        }
        val markers = OcrTripMarkers.DEFAULT
        fun cardRegionText(evidence: List<OcrBlock>) = evidence.filter { block ->
            active.lastCardDisplay == displayBounds && active.lastCardBounds?.contains(
                HostRect(block.bounds.left, block.bounds.top, block.bounds.right, block.bounds.bottom)) == true
        }.joinToString(" | ") { it.text }
        // A live signature vetoes ALL transition evidence from this frame. Outside the former
        // card, only device-confirmed B-084 transitions count (including the accepted header).
        // Pass the scoped decision downstream, never whole-window text that can reintroduce guesses.
        val pixelTransition = if (signature || ownUi) OcrLifecycleTransition.UNKNOWN else {
            markers.scopedTransition(joined, cardRegionText(blocks), allowWindowIdle = !sessions.isPresent())
        }
        val lifecycleNodes = ownedAccessibility?.takeIf {
            active.semanticCardWindowId != null && it.windowId == active.semanticCardWindowId &&
                it.capturedAtMs > active.lastCardObservedAtMs && it.observedCardBounds == null && nodeCard == null
        }
        val transition = if (!ownUi && !signature && pixelTransition == OcrLifecycleTransition.UNKNOWN &&
            lifecycleNodes != null) {
            // Matching semantic-card disappearance is independent absence evidence, but cannot
            // widen uncalibrated navigation markers. Keep exclusions from BOTH owned sources.
            markers.scopedTransition("$joined | ${nodeBlocks.joinToString(" | ") { it.text }}",
                cardRegionText(nodeBlocks), allowWindowIdle = true)
        } else pixelTransition
        val clearAbsence = observedAbsent || (!ownUi && !signature && transition != OcrLifecycleTransition.UNKNOWN)
        val card = decision?.card?.takeIf {
            frameOutlierGuard.accept(parsed, now)
        }
        var disappeared = false
        if (card == null) {
            fareStabilizer.breakCandidateAgreement()
            disappeared = sessions.miss(cardLike = true, nowMs = now, observedGone = clearAbsence)
            if (!disappeared && chipRect != null && (sessions.isPresent() || parsed != null)) {
                OfferEventBus.tryEmit(OfferEvent.LayoutUncertain(hostPackage, capturedAtMs))
            }
        }
        if (disappeared) {
            fareStabilizer.reset()
            frameOutlierGuard.reset()
            // Keep only the accepted offer's region for later in-region trip evidence. The offer
            // identity/payment are retired; guesses still cannot act elsewhere in the window.
            if (transition != OcrLifecycleTransition.ACCEPTED && transition != OcrLifecycleTransition.UNDERWAY) {
                active.lastCardBounds = null
            }
            active.semanticCardWindowId = null
        }
        if (!signature && (observedAbsent || transition == OcrLifecycleTransition.IDLE ||
                transition == OcrLifecycleTransition.CANCELLED)) {
            // Completion/cancellation also expires a retained accepted-card region, even though
            // acceptance already retired the offer session and miss() can no longer disappear it.
            active.lastCardBounds = null
            active.lastCardDisplay = null
        }
        diagnostics.reading(OcrReadingEvidence(blocks.size, ownUi, signature, parsed != null, parsed?.fare != null))
        if (signature && (parsed == null || parsed.fare == null)) {
            diagnostics.blocked("incomplete reading", "parsed=${parsed != null} paymentRead=${parsed?.fare != null}")
        }
        // One instance ID reaches both consumers; only confirmed payment is allowed onto either bus.
        // Confirmation uses monotonic time; session/event/ledger timestamps remain epoch time.
        val shownCard = card?.let { fareStabilizer.onParsed(it, now,
            confident = decision.confidence == OcrOfferSessionTracker.Confidence.CONFIDENT,
            identityUnresolved = decision.identityUnresolved) }
        if (parsed != null && shownCard == null) {
            diagnostics.unpublishedOffer(when {
                decision == null -> OcrPublicationRejectionReason.PARSED_READINESS_BLOCKED
                decision.card == null -> decision.rejection ?: OcrPublicationRejectionReason.PARSED_SESSION_UNCONFIRMED
                else -> OcrPublicationRejectionReason.PARSED_OCR_OUTLIER
            })
        }
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
                        OfferEvent.Reason.CONFIRMED_ABSENCE,
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
            lifecycleClassifier.onFrame("", shownCard, signature || sessions.isPresent(), hostPackage, capturedAtMs,
                observedIdle = observedAbsent, transition = transition)
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
        currentHost?.let {
            fareStabilizer.breakCandidateAgreement()
            sessions.suspendObservation()
            OfferEventBus.tryEmit(OfferEvent.LayoutUncertain(it, nowWallMs))
        }
    }

    /**
     * End visibility, not the offer. Departure/lock/capture interruption hides immediately and
     * breaks consecutive agreement. A return to either host resumes its own durable session;
     * only observed absence or corroborated replacement retires it.
     */
    fun onCaptureEnd(nowWallMs: Long) {
        currentHost?.let { host ->
            OfferEventBus.tryEmit(OfferEvent.NoCard(host, nowWallMs, OfferEvent.Reason.NOT_VISIBLE))
            fareStabilizer.breakCandidateAgreement()
            sessions.suspendObservation()
            lifecycleClassifier.onObservationLost(nowWallMs)?.let { OcrLifecycleBus.tryEmit(it) }
        }
        lastOfferKey = null
        lastOfferPackage = null
        currentHost = null
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
