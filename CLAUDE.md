# Kompara

Earnings analytics for ride-hailing drivers in Mexico. **Pivoted 2026-06-10 to a native Android rebuild** (Kotlin + Jetpack Compose): the hook feature reads Uber/DiDi trip-offer cards in real time via AccessibilityService and overlays an instant profitability verdict (net $/km, $/min, traffic light) before the driver accepts — like Ruta Rentable, StopClub, and GigU. The reader is free; benchmarks/compare/history are the paid layer.

Current roadmap: epics E-005+ in `pming/`. The Next.js web MVP (epics E-001–E-004, now superseded) remains in `src/` as the reference implementation until E-010 sunsets it — its parsers, percentile engine, and metric definitions get ported, not discarded. Key docs: `docs/competitive-analysis.md` (rivals, legal posture) and `docs/android-technical-design.md` (capture architecture, Play policy strategy).

## How to work

The project uses a markdown-based PM system in `pming/` (epics → stories → tasks) and Claude Code skills for workflow automation.

**Using skills (preferred):**
1. `/tasks` — see what's available
2. `/work B-XXX` — pick up a task in an isolated worktree (or `/work` to auto-select next)
3. Implement the task
4. `/ship` — adversarial review, commit, merge, push
5. `/done` — mark complete, advance to next

**Working manually:**
1. Find a task in `pming/tasks/pending/` — read its description and acceptance criteria
2. Implement the change
3. Run `pnpm test` — all tests must pass
4. Run `pnpm lint` — must be clean
5. Commit with a descriptive message

**Other skills:** `/plan-session` (daily/weekly planning), `/roadmap` (bird's-eye view), `/housekeeper` (project health check), `/rem` (self-healing maintenance), `/task` (create new work items), `/seenext` (preview next task), `/solve` (diagnose and fix bugs), `/work-me` (walk through manual ops tasks), `/canary` (post-deploy health check), `/status` (quick production dashboard), `/rollback` (emergency production recovery), `/cso` (security audit), `/migration-check` (DB migration safety review), `/telemetry` (harness analytics).

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

## Architecture

**Android (forward):** see `docs/android-technical-design.md` — modules `:capture` (AccessibilityService), `:parsers` (declarative specs + remote config), `:overlay`, `:metrics`, `:data` (Room), `:sync`, `:ui`, `:billing`. All capture/parsing/verdicts on-device (legal + Play-policy posture); thin greenfield backend for auth, benchmarks, imports.

**Web (legacy reference):** Next.js 15 monolith (App Router) deployed on Render. React frontend + API routes in a single deployable unit.

**Layers and boundaries:**
- `src/app/` — Next.js pages and API routes. Pages can import from `components/`, `hooks/`, `lib/`. API routes can import from `lib/`.
- `src/lib/` — Shared library code. Modules: `db/` (Drizzle ORM), `auth/` (sessions, magic links), `parsers/` (Claude Vision extraction), `storage/` (R2), `whatsapp/` (Twilio), `percentiles/` (stats engine). Lib modules should not import from `app/` or `components/`.
- `src/components/` — React UI components. Can import from `hooks/` and `lib/constants.ts`. Should not import from `lib/db/` or `lib/auth/` directly.
- `src/hooks/` — React hooks. Can import from `lib/`.

**External services:** Render Postgres, Cloudflare R2, Claude API (Vision), Twilio WhatsApp.

See `docs/technical-design.md` for full architecture details.

## Commands

```bash
pnpm install          # install deps
pnpm build            # compile TypeScript (tsc)
pnpm dev              # start dev server
pnpm test             # run all tests
pnpm lint             # eslint
```

**Android (native, forward) — a build env IS present on this machine; don't assume otherwise.** The Gradle project lives in `android/` (not the repo root). The JDK is **not on `PATH`** — it's Android Studio's bundled JBR (OpenJDK 21); the SDK is at `~/Library/Android/sdk`. Claude sessions in this repo get `JAVA_HOME`/`ANDROID_HOME` auto-set via `.claude/settings.local.json` (gitignored). If building from a context that lacks them:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd android && ./gradlew :ocr:testDebugUnitTest :capture:testDebugUnitTest   # unit tests
```

## Tech stack

- **Framework:** Next.js 15 (App Router), React 19, TypeScript (strict)
- **Styling:** Tailwind CSS 4
- **Database:** Render Postgres + Drizzle ORM
- **File storage:** Cloudflare R2 (S3-compatible)
- **AI/OCR:** Claude Sonnet API (vision)
- **Auth:** Custom WhatsApp magic links via Twilio + jose JWT
- **Validation:** Zod
- **Testing:** Vitest (unit/integration), Playwright (e2e)
- **Hosting:** Render Web Service

## File conventions

- One export per file where practical
- Files named `kebab-case.ts`
- Types co-located in `types.ts` within each module
- Tests mirror source structure

## Tech debt

Track tech debt in `techdebt.md`. Whenever you make a conscious decision to defer work — security hardening, missing validation, known shortcuts, manual setup not yet done — add an entry. Each entry needs: date, severity, context, why deferred, and when to fix. Review and update `techdebt.md` when working on related areas.

## Documentation map

| Question | Read |
|---|---|
| Android architecture & capture design | `docs/android-technical-design.md` |
| Competitors & legal posture | `docs/competitive-analysis.md` |
| Web-era system design (reference) | `docs/technical-design.md` |
| Business context | `docs/project-context.md` |
| Tech debt tracking | `techdebt.md` |
| Debugging lessons & playbook | `docs/debugging-lessons.md` |
| PM system quick reference | `pming/skills.md` |
