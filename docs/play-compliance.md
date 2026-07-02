# Google Play compliance posture — the #1 launch risk

> **Status:** engineering posture LOCKED and enforced in code (B-091). Store-listing copy and the
> privacy-policy text still need MX counsel sign-off (B-038). This document is the canonical statement
> of *why* the reader is built the way it is; do not weaken it without re-running the risk review.

## The risk (ranked #1, above anonymity and platform-ToS)

Kompara is **commercial screen-reading**: an `AccessibilityService` plus a floating overlay that
reads another app's screen. That exact combination is the heuristic signature Google Play Protect
uses to flag **banking trojans**. We cannot hide behind the "accessibility tool" exemption — that is
reserved for genuine disability apps (`isAccessibilityTool="true"`), which we cannot truthfully claim.

If Google auto-suspends the developer account on policy grounds, the suspension is often
**unappealable** — and then the entity, the OPSEC, and every downstream plan is moot. This sits
**above** the founder-anonymity and Uber/DiDi-lawsuit questions in the risk stack.

## The bright line: autonomous action

The single line separating a *permitted assistant* from an *auto-banned trojan* is **autonomous
action**. Everything below flows from staying on the permitted side of it, forever.

### 1. No automation, ever — read-only forever

The reader **informs, it never acts.** No auto-accept, no auto-decline, no tapping on the driver's
behalf. Overlay-and-inform only.

Auto-refuse is exactly the feature that drew Uber's lawsuit against StopClub and is Google's #1
termination trigger. v1 is already read-only; the commitment is to **keep it that way** and resist
adding auto-decline even when drivers ask for it.

**Enforced in code.** `./gradlew verifyReadOnlyCapture` (wired into every module's `check` and run in
CI) fails the build if any of `performAction(`, `dispatchGesture(`, `performGlobalAction(`, or
`GLOBAL_ACTION_` appear in production source, or if the accessibility-service config loses
`isAccessibilityTool="false"` or gains a gesture/key/touch-exploration capability. This makes
read-only a **build invariant**, not a convention a future PR can quietly erode. See
[android/build.gradle.kts](../android/build.gradle.kts).

### 2. Everything stays on the device — nothing stored, nothing transmitted

The capture pipeline is **detect → silent screenshot → on-device OCR → overlay**, with the frame
processed **in memory** and nothing about the offer or the passenger stored or transmitted:

- Frames are OCR'd by bundled ML Kit (no network) and the bitmap is **recycled immediately** after
  recognition. No screenshot is ever written to disk, cached, or uploaded.
- The offer ledger persists only **derived numbers** (fare, distance, duration, verdict, timestamps)
  — never raw OCR text, screen captures, passenger names, ratings, or pickup/dropoff addresses.
- Raw OCR text and fixture dumps are written **only in debug builds** (`BuildConfig.DEBUG`); release
  builds store no screen content. Fixture snapshots that CAN be uploaded are PII-scrubbed and require
  explicit per-report consent.
- No third-party analytics/crash SDKs (no Firebase, Sentry, Crashlytics, etc.).

This is what makes the Data Safety declaration ("**no data collected** from the screen") *true*, and
keeps the app out of the malware bucket. The one code path shared by both capture lanes,
[OfferFramePipeline](../android/ocr/src/main/java/mx/kompara/ocr/OfferFramePipeline.kt), never writes
screen content in release.

### 3. The compliant accessibility wrapper

- **`isAccessibilityTool="false"`** — Kompara is occupational decision-support, not an assistive tool
  for a disability. Declaring `false` is the honest choice and is enforced by the build guard.
- **Play Console AccessibilityService API declaration** — framed as driver decision-support /
  distraction-reduction, with a demo video. Precedent: Mystro, StopClub, Ruta Rentable, GigU all live
  on Play with this model.
- **Prominent in-app disclosure + consent, shown immediately before the permission grant** — a
  standalone screen ([DisclosureScreen](../android/ui/src/main/java/mx/kompara/ui/onboarding/OnboardingScreens.kt))
  stating what is read, why, that processing is on-device, and the platform-ToS risk. It is a **hard
  gate**: [DisclosureStateMachine](../android/ui/src/main/java/mx/kompara/ui/onboarding/DisclosureDecision.kt)
  only lets the funnel deep-link to `Settings.ACTION_ACCESSIBILITY_SETTINGS` after an explicit
  **Accept**; **Decline** routes to a limited-info screen and never reaches the grant.
- **Matching privacy policy** — see [docs/privacy-policy.md](privacy-policy.md) (DRAFT, B-038).

## The capture lane (B-091)

On **API 30+** the primary lane is the accessibility service's own silent
`AccessibilityService.takeScreenshot()` →
[SilentScreenshotLane](../android/ocr/src/main/java/mx/kompara/ocr/SilentScreenshotLane.kt): **no
MediaProjection consent prompt, no persistent screen-cast indicator, survives screen lock, and drops
the FGS-`mediaProjection` declaration burden.** It captures **only while a target host app
(Uber/DiDi/inDrive) is foreground**, gated by the accessibility service's own foreground-host signal,
so it never screenshots an unrelated app. This is the cleanest possible Play accessibility lane and is
the StopClub/GigU-validated model (they read DiDi's SurfaceView via screenshot + OCR, not nodes).

On **API <30**, and as an automatic fallback if an OEM breaks `takeScreenshot`, the app falls back to
the existing MediaProjection lane
([OcrCaptureService](../android/ocr/src/main/java/mx/kompara/ocr/OcrCaptureService.kt)) — per-session
user-initiated consent, prominent disclosure, and a foreground-service notification. Both lanes feed
the **same** `OfferFramePipeline`, so behavior can't drift.

## What we will NOT do

- ❌ Auto-accept / auto-decline / any tap on the host app's behalf (banning trigger).
- ❌ `isAccessibilityTool="true"` (false claim → policy violation).
- ❌ `FLAG_SECURE` on our own UI (would break the driver's own screenshots; not needed).
- ❌ Storing or transmitting screen content, offer text, or passenger data.
- ❌ Third-party analytics/crash SDKs that would make "no data collected" untrue.

## On-device validation still owed

The silent-screenshot lane is implemented and unit-tested for its gating/fallback logic, but the
`takeScreenshot` → hardware-buffer → OCR path and the OEM rate-limit behavior can only be validated on
a real device. Tracked as a flip-gate in [techdebt.md](../techdebt.md) (TD-040).
