# Kompara

Earnings analytics for ride-hailing drivers in Mexico. The product is a native Android app (Kotlin + Jetpack Compose) that reads Uber and DiDi trip-offer cards as they appear and overlays an instant profitability verdict (net $/km, $/min, traffic light) before the driver accepts, like Ruta Rentable, StopClub and GigU. The reader is free; benchmarks, compare and history are the paid layer. A small TypeScript backend handles accounts, earnings imports and benchmarks.

These are the instructions every coding agent shares: Claude Code loads this file through `CLAUDE.md`, and Codex reads it directly. Tool-specific material (Claude Code skills, model routing) lives in `CLAUDE.md`.

## Repo map

| Path | What it is |
|---|---|
| `android/` | **The app.** Gradle project: run Gradle from here, never from the repo root. |
| `backend/` | **The API.** TypeScript with Hono, Drizzle and Postgres: auth (OTP, sessions, device auth), earnings imports (Uber, DiDi and inDrive screenshot and PDF parsers), benchmarks and percentiles, referrals, app config, signed parser-spec bundles. Has its own `package.json`, lockfile and `migrations/`. |
| `design-system/` | Design tokens, components and UI kits, kept in sync with claude.ai/design. Read it for UI work. |
| `docs/` | Design docs, specs and runbooks (see the documentation map below). |
| `pming/` | The markdown PM system: epics → stories → tasks. Quick reference: `pming/skills.md`. |
| `techdebt.md` | Deliberately deferred work. |
| `src/`, root `package.json`, `migrations/`, `seed/`, `tests/`, `public/` | **Legacy** Next.js web MVP (epics E-001–E-004). Reference only until E-010 retires it: its parsers, percentile engine and metric definitions get ported, not discarded. Don't read or change it unless the task names it. |

Android modules: `:app` (the application), `:capture` (the AccessibilityService), `:ocr` (on-device OCR of offer cards), `:parsers` (declarative parser specs and remote config), `:overlay` (the verdict chip), `:metrics`, `:data` (Room), `:sync`, `:ui` (screens), `:billing`. Compose is used in `:app`, `:overlay` and `:ui`; every module except `:ocr` uses Hilt and KSP. Package root `mx.kompara`, application ID `mx.kompara.app`. Architecture and capture design: `docs/android-technical-design.md`.

## Build, test, verify

### Java for Gradle

Gradle needs a JDK, and the shell usually has none on `PATH` (`which java` finds only the macOS stub, `JAVA_HOME` is empty). That is a false "no build environment": Android Studio bundles JDK 21. When `JAVA_HOME` is unset, run Gradle with:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

Claude Code sessions started in the main checkout get both from `.claude/settings.local.json`; worktrees don't have that file. CI uses Temurin 21.

### Android (from `android/`)

```bash
./gradlew verifyReadOnlyCapture          # Play-policy guard: no accessibility automation in production code (B-091)
./gradlew assembleDebug                  # debug APK
./gradlew testDebugUnitTest              # all unit tests
./gradlew lintDebug                      # Android lint
./gradlew :parsers:testDebugUnitTest     # one module's unit tests, for fast iteration
./gradlew installDebug                   # install on the connected device
./gradlew :data:connectedDebugAndroidTest   # Room instrumented tests; needs a device or emulator
```

### Backend (from `backend/`)

Use Node 24 and pnpm 10, as CI does (`package.json` requires Node 24 or newer).

```bash
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test                                # vitest
pnpm dev                                 # local server
```

Migrations live in `backend/migrations/` (drizzle-kit: `pnpm db:generate`, `pnpm db:migrate`). Get a migration safety review before any migration touches production data.

### Legacy web (repo root)

`pnpm lint` and `pnpm test`, only when a task changes `src/`.

### Verify before calling work done

Run the row for every area the change touches. These mirror CI (`.github/workflows/`).

| Changed | Run |
|---|---|
| `android/**` | from `android/`: `./gradlew verifyReadOnlyCapture assembleDebug testDebugUnitTest lintDebug` |
| `backend/**` | from `backend/`: `pnpm typecheck && pnpm test` |
| `src/**` or root web config | from the repo root: `pnpm lint && pnpm test` |
| only docs, `pming/` or `design-system/` | nothing to run |

While iterating, run only the touched module's tests; run the full row before handing off. Changes to capture, OCR or the overlay also need a check on the device when one is connected (`adb devices`); if none is, say so instead of claiming it works.

### On the device

```bash
adb logcat -v time 'KomparaCapture:V' 'KomparaOCR:V' 'KomparaBilling:V' 'GateFunnel:V' 'OnboardingFunnel:V' '*:S'
```

The Room database (`kompara.db`) runs in WAL mode. Pull all three files, or recent rows look missing:

```bash
for f in kompara.db kompara.db-wal kompara.db-shm; do adb exec-out run-as mx.kompara.app cat databases/$f > /tmp/k.$f; done
```

## Rules that are easy to break

- **Capture stays read-only.** No accessibility actions, gestures or global actions in production code, and the accessibility-service config keeps `android:isAccessibilityTool="false"`. `./gradlew verifyReadOnlyCapture` enforces this; background in `docs/play-compliance.md`.
- **Everything stays on the device.** Capture, parsing and verdicts run on the phone. That is the legal and Play-policy posture.
- **Never `FLAG_SECURE`** on our windows: drivers screenshot the app, and the share card is the point.

## How work is organized

Work items live in `pming/` (epics → stories → tasks); each task file carries its description and acceptance criteria. A task is built on its own branch in a worktree under `.worktrees/<task-id>`, verified with the checks above, and reviewed before it merges. In Claude Code, the `/work`, `/ship` and `/done` skills drive this (see `CLAUDE.md`).

### When to stop and ask

- The spec is ambiguous or contradictory → add a `<!-- QUESTION: ... -->` comment to the task file and STOP
- You want to deviate from the spec → add a `<!-- DEVIATION: ... -->` comment explaining why and STOP
- A previous phase left something broken or incomplete → report it and STOP
- **Never stop for implementation decisions** (naming, patterns, library choices). Decide and move on.

## Engineering guidelines

Behavioral guidelines to reduce common LLM coding mistakes. They bias toward caution over speed — for trivial tasks, use judgment. During autonomous runs, surface questions via the task-file comment rules in "When to stop and ask" instead of blocking.

### 1. Think before coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity first

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: every changed line should trace directly to the user's request.

### 4. Goal-driven execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Conventions

- **Kotlin:** match the module's existing patterns. Hilt for dependency injection, Compose for UI, Room in `:data`. Unit tests go in the module's `src/test`; Room instrumented tests in `android/data/src/androidTest`.
- **TypeScript** (`backend/` and the legacy web app): files named `kebab-case.ts`, Zod for validation, one export per file where practical, types co-located in `types.ts` within each module. Backend tests sit next to their source as `*.test.ts`.
- **Tech debt:** whenever you consciously defer work (security hardening, missing validation, a known shortcut, manual setup not done yet), add an entry to `techdebt.md` with date, severity, context, why deferred and when to fix. Parallel sessions collide on `TD-` numbers, so read the latest ID right before adding one. Review and update `techdebt.md` when working on related areas.

## Documentation map

| Question | Read |
|---|---|
| Android architecture and capture design | `docs/android-technical-design.md` |
| Play policy and the read-only rule | `docs/play-compliance.md` |
| Debugging lessons and playbook | `docs/debugging-lessons.md` |
| Competitors and legal posture | `docs/competitive-analysis.md` |
| Design principles and the design system | `docs/design-principles.md`, `design-system/README.md` |
| Launch and deploy | `docs/launch-runbook.md`, `docs/render-deploy.md` |
| Business context | `docs/project-context.md` |
| Web-era system design (legacy reference) | `docs/technical-design.md` |
| Tech debt | `techdebt.md` |
| PM system quick reference | `pming/skills.md` |
