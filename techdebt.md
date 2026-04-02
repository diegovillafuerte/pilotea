# Tech Debt

Conscious deferrals. Each entry: date, severity, context, why deferred, when to fix.

## 2026-04-02 | Medium | Database schema placeholder

**Context:** `src/lib/db/schema.ts` and `migrations/0001_initial.sql` are empty placeholders.
**Why deferred:** Project initialization task (B-001) focuses on folder structure and dependency setup. Schema implementation is a separate task (B-002).
**When to fix:** Before any database-dependent feature work begins.

## 2026-04-02 | Medium | Auth, parsers, storage, WhatsApp, percentiles not implemented

**Context:** All `src/lib/` modules contain only TODO placeholder exports.
**Why deferred:** B-001 establishes the folder structure per the tech design. Each module will be implemented by its own task in the S-001 story.
**When to fix:** During their respective implementation tasks (B-002 through B-008).

## 2026-04-02 | Low | No test coverage for any feature

**Context:** `tests/e2e/upload-flow.spec.ts` is a TODO placeholder. No unit or integration tests exist.
**Why deferred:** No feature code to test yet. Tests will be written alongside feature implementation.
**When to fix:** As each feature is implemented in subsequent tasks.

## 2026-04-02 | Low | PWA icons missing

**Context:** `src/app/manifest.ts` references `/icons/icon-192.png` and `/icons/icon-512.png` but `public/icons/` only has `.gitkeep`.
**Why deferred:** Icons require design work. The manifest structure is correct for when icons are provided.
**When to fix:** During the UI/design implementation phase.

## 2026-04-02 | Low | ARCHITECTURE.md not yet created

**Context:** `CLAUDE.md` references `ARCHITECTURE.md` as TBD. The folder structure follows `docs/technical-design.md` section 4 but no standalone architecture doc exists yet.
**Why deferred:** Architecture will be documented once the initial features are implemented and patterns solidify.
**When to fix:** After the first 2-3 feature tasks are completed and the architecture patterns are established.

## 2026-04-02 | Low | ESLint does not enforce import boundaries

**Context:** ESLint config only extends `next/core-web-vitals` and `next/typescript`. No custom import boundary rules are configured.
**Why deferred:** No layer boundaries to enforce yet -- all modules are placeholders. Rules will be added as the architecture materializes.
**When to fix:** When the first cross-module imports appear (likely during auth or parser implementation).
