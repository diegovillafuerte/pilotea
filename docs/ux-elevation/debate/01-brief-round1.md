# Kompara — product & UX elevation. ROUND 1: independent analysis

You are a principal product designer who has shipped driver-facing mobile apps at Uber/DiDi scale and
has run usability sessions with Mexican ride-hailing drivers. You are reviewing **Kompara**, a native
Android app (Kotlin + Jetpack Compose) for Uber/DiDi/inDrive drivers in Mexico. Your job in this round:
produce a ruthless, independent diagnosis of the WHOLE product and a concrete proposal that elevates
the concept and makes every screen radically simpler — the explicit bar is **"comparable in use and
feel to the Uber Driver app"** (its structure and discipline, not its brand).

This is a DESIGN exercise only. No code. But every proposal must be buildable in Compose using the
existing token system (emerald primary, slate neutrals, verdict triple verde/amarillo/rojo, Inter,
dark by default). Tokens may be refined; they are not replaced wholesale.

## What the product is today (read these — they are the ground truth)

Repo root: /Users/panama/pilotea

1. **Complete screen inventory with real es-MX copy** (START HERE, it is exhaustive):
   `SCRATCH/inventory.md`
2. Design law & tokens: `docs/design-principles.md`, `design-system/README.md`
3. Known gaps & benchmark scorecard: `docs/benchmark-ux-gaps.md`, `docs/claude-design-redo-brief.md`,
   `docs/design-system-audit.md`
4. Market & rivals (StopClub/GigU, Ruta Rentable, Gridwise, DecideRider, Uber's own $/km badge pilot
   in Mexico): `docs/competitive-analysis.md`
5. The one screen already redesigned on paper (weekly benchmarking hub): `docs/comparar-redesign-spec.md`
6. Hard product constraints: `docs/play-compliance.md`, `docs/android-technical-design.md` (§0–§1 only)
7. Visual of today's app (HTML recreation, dark theme): `design-system/ui_kits/android-app/index.html`
   and `onboarding.html`; one template per screen under `design-system/templates/*/`
8. Source of truth if the inventory leaves you unsure: `android/ui/src/main/java/mx/kompara/ui/screens/*.kt`,
   `android/ui/src/main/java/mx/kompara/ui/onboarding/OnboardingScreens.kt`,
   `android/overlay/src/main/java/mx/kompara/overlay/VerdictChipUi.kt`,
   `android/ui/src/main/res/values/strings.xml`

Do NOT read `pming/`, `src/` (legacy web), `node_modules/`, `android/build/`.

## Non-negotiable constraints (design inside them)

- **Read-only reader, forever.** No auto-accept/decline, no tapping on the driver's behalf (Google Play
  policy + Uber-vs-StopClub litigation). The overlay informs; it never acts.
- **Everything on-device.** No screen content stored or transmitted; no third-party analytics SDKs.
  A prominent disclosure screen must precede the accessibility permission grant.
- **Freemium:** the reader + live verdict are free; benchmarks/compare/history/fiscal are the paid
  layer (~$59–99 MXN/mo, trial without card). You MAY propose moving the paywall line if it makes the
  product clearer — say so explicitly.
- **Data reality:** city benchmarks exist only per WEEK (no daily/hourly population data). The driver's
  own trips are captured per offer/trip, so personal day/hour stats are possible.
- **Driver context:** phone on a windshield mount, often at night; reads in 1-second glances; Android
  phones ~360–400dp wide; prepaid data; WhatsApp-native; low-to-mid tech comfort; es-MX with `tú`.
- **No platform logos/marks** (Uber/DiDi/inDrive) anywhere in the UI.
- Uber is now piloting a GROSS $/km badge on its own offer card in Mexico. Kompara's wedge is NET
  (after the driver's real costs) + configurable thresholds + multi-app + everything after the trip.

## What to deliver

Write ONE markdown document to: `OUTPUT_PATH`

Structure it exactly like this:

### 1. Diagnosis (ruthless, evidence-based)
The 10–15 biggest problems with the current product concept and UX, ranked by severity. For each:
the problem in one sentence, the evidence (name the screen and QUOTE the real copy/metric from the
inventory), why it hurts a driver, severity (critical/high/medium). Cover at minimum: information
architecture (is a 5-tab bar right? is "Lector" a tab?), jargon and copy, density and number of
numbers per screen, redundancy across screens, missing/weak states, onboarding length and the
permission ask, paywall placement, the overlay chip itself, consistency, and anything that would
make a driver uninstall in the first 5 minutes.

### 2. The elevated concept
- The one-sentence promise (es-MX, driver's words) and the one-sentence promise (English, for us).
- The driver's day as a timeline (before the shift → the offer moment → during the shift → end of
  shift → week close → month/fiscal) and what Kompara does at each moment. Name the 3 moments that
  matter most and that the app must be built around.
- What to steal from the Uber Driver app (structure: one hero number, one primary action, map/
  status-first home, earnings as a timeline, trip card discipline, settings tucked away) and what NOT
  to copy.
- What to CUT entirely from the current product (be brave; list features/screens and the reason).

### 3. Information architecture
Proposed top-level navigation: number of tabs, names (es-MX), order, icon, and the one-line purpose
of each. Then a fate table mapping EVERY current screen/surface from the inventory →
keep / merge into X / move to settings / kill — with the reason.

### 4. Screen-by-screen spec (every surface in the NEW IA, including drill-ins)
For each: purpose (one line) · the hero (the one number/visual) · the ONE primary action · layout
top-to-bottom (every element, with real es-MX copy for titles/labels/buttons) · secondary actions ·
states (empty / loading / locked / error / first-run) · what you removed vs today and why · the
1-second glance test (what a driver gets from a glance at 60cm).

### 5. The offer moment (the real product)
The overlay chip and everything that happens inside the host app: states, exact content per state,
size/position, tap and long-press behaviors, what the driver must get in ONE second, how "why" is
explained, how thresholds are adjusted without leaving Uber/DiDi, and the reader-down/degraded
states. Also the post-trip and end-of-shift moments (notifications? summary?).

### 6. Onboarding
Target step count (state it), each step's single ask and copy, where signup sits and why, how the
accessibility permission and the DiDi screen-capture consent are framed, when the driver first sees
value (the "aha") and how to get there faster. Compare to GigU's phone → WhatsApp code → name flow.

### 7. Monetization & paywall placement
Where the lock sits in the new IA, the tease pattern (blur / preview / count-down), the trial
framing, the price surface, and how a free user still feels the app is complete.

### 8. Scorecard
Rate TODAY's app and YOUR proposal, 1–10 each, on: first-use clarity · glanceability · IA simplicity ·
copy quality · consistency · trust/transparency · delight · "feels like Uber-class". One sentence of
justification per cell. Be honest about where your proposal still falls short of the bar.

### 9. Open questions
The 5 things you would test with real drivers before committing, and your default answer for each.

Rules: prefer deleting to adding; every screen must survive the 1-second glance test; use real es-MX
copy (tú, no jargon like IPK/IPH, no corporate tone); never propose auto-actions; name files/screens
from the inventory so a reader can trace every claim. Length: as long as it needs to be, no padding.
