# Tech Debt

Conscious deferrals. Each entry: date, severity, context, why deferred, when to fix.

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

## TD-001: Magic link tokens stored in plaintext
- **Date:** 2026-04-02
- **Severity:** medium
- **Context:** The `magic_links.token` column stores the raw magic link token. If the database is compromised, unexpired tokens could be used to hijack accounts. The `sessions` table already stores hashed tokens (`token_hash`), showing the correct pattern.
- **Why deferred:** The tech design spec (section 5.1) explicitly defines `magic_links.token` as `VARCHAR(64) UNIQUE`. Task B-002 requires the schema to match the spec exactly. Changing this would be a spec deviation.
- **When to fix:** Before launch. Change `token` to `token_hash`, store SHA-256 of the token, and compare hashes during magic link validation. Update the auth implementation (B-003 or equivalent) to hash tokens before storage and comparison.

## TD-003: Upload route coerces null metrics to zero for NOT NULL DB columns
- **Date:** 2026-04-02
- **Severity:** medium
- **Context:** `src/app/api/uploads/route.ts` lines 216-218 coerce null `net_earnings`/`gross_earnings` to `"0"` and null `total_trips` to `0` before inserting into the `weekly_data` table. These columns are `NOT NULL` in the schema. For the Uber PDF parser this is rarely an issue (those fields are almost always present), but the screenshot parser (`uber-screenshot.ts`) intentionally returns `total_trips: null` since that data is not available from a pie chart view. This creates weekly data rows with `total_trips = 0` which is misleading (zero trips vs. unknown trips).
- **Why deferred:** The upload route is pre-existing code from B-006. Fixing it requires either making these DB columns nullable (schema migration) or adding parser-specific handling in the upload route. Both are out of scope for B-011 (screenshot parser).
- **When to fix:** Before launch. Either make `total_trips`, `net_earnings`, `gross_earnings` nullable in the schema, or add logic in the upload route to distinguish "zero" from "unknown/null". Consider adding a `source_type` column to `weekly_data` to track whether data came from a full PDF or limited screenshot.

## TD-002: get_percentile edge case with zero-valued distributions
- **Date:** 2026-04-02
- **Severity:** low
- **Context:** The `get_percentile` SQL function uses `NULLIF(..., 0)` to avoid division by zero, but when a percentile bucket boundary is zero, the branch evaluates to NULL and PostgreSQL's `GREATEST`/`LEAST` ignore NULLs, potentially returning incorrect percentile values for zero-heavy metrics (e.g., tips, rewards).
- **Why deferred:** The function is copied verbatim from the tech design (section 5.2). The edge case only manifests with degenerate population data where bucket boundaries are zero, which is unlikely with real-world earnings data.
- **When to fix:** When implementing the percentiles engine (B-005 or equivalent). Add COALESCE guards around each interpolation branch to handle zero denominators gracefully.
