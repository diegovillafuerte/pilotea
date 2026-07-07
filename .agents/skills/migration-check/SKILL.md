---
name: migration-check
description: Review pending database migrations for safety — backward compatibility, data loss risk, lock duration, and rollback plan. Use before running pnpm db:migrate on production data.
argument-hint: [optional: path to specific migration file]
---

# Migration Check: Database Migration Safety Review

Review pending migrations for production safety before applying them.

## Step 1: Find pending migrations

If $ARGUMENTS specifies a file path, review only that migration.

Otherwise, find new migrations:
1. List all files in `src/db/migrations/` sorted by name (numbered: 001_, 002_, etc.)
2. Check the most recent production deploy commit: `mcp__render__list_deploys` (latest 1) to get the deployed commit SHA
3. Find migrations added since that commit: `git diff --name-only --diff-filter=A <deployed_sha>..HEAD -- src/db/migrations/`
4. If that fails (no deploy info), fall back to: `git diff --name-only --diff-filter=A HEAD~5..HEAD -- src/db/migrations/`

If no new migrations found, report "No pending migrations." and STOP.

## Step 2: Review each migration

Read the full migration file. For each SQL operation found, evaluate:

### 2.1: Operation classification

| Operation | Default Risk | Notes |
|---|---|---|
| `CREATE TABLE` | LOW | Generally safe |
| `ADD COLUMN` (nullable) | LOW | Safe — no lock on existing rows |
| `ADD COLUMN` (NOT NULL + default) | MEDIUM | Postgres 11+ rewrites metadata only, but verify |
| `ADD COLUMN` (NOT NULL, no default) | HIGH | Fails if table has existing rows |
| `DROP COLUMN` | CRITICAL | Data loss — irreversible |
| `DROP TABLE` | CRITICAL | Data loss — irreversible |
| `ALTER COLUMN` (type change) | HIGH | Can fail on existing data, locks table |
| `RENAME COLUMN/TABLE` | HIGH | Breaks running code during deploy |
| `CREATE INDEX` | MEDIUM | Can lock table — check for CONCURRENTLY |
| `CREATE INDEX CONCURRENTLY` | LOW | Non-blocking, safe |
| `DROP INDEX` | LOW | May degrade query performance |
| `TRUNCATE` | CRITICAL | Data loss |
| `INSERT/UPDATE/DELETE` (data migration) | MEDIUM | Verify correctness, check volume |

### 2.2: Safety checklist

For each operation:

- **Backward compatibility:** Can the currently deployed code run against the new schema? During the deploy window, old code talks to new schema.
- **Data loss:** Any destructive operation without a backup or confirmation step?
- **Lock duration:** Does this lock a table that receives writes? For tables with >10k rows, ALTER TABLE without CONCURRENTLY is risky.
- **Rollback plan:** Does a `down()` function exist? Does it correctly reverse the `up()`? For destructive operations, is data recoverable?
- **Data migration correctness:** If transforming existing data, is the logic correct? Are edge cases handled (nulls, empty strings, zero values)?
- **Null handling:** Adding NOT NULL to existing column? Will fail if rows have nulls.
- **Foreign keys:** New FK constraints — do all existing rows satisfy them?

### 2.3: Banking-specific checks

For a financial product, apply extra scrutiny to:

- **Ledger tables** (`ledger_entries`, `accounts`, `account_balances`): Any modification is CRITICAL by default. Double-entry integrity must be preserved.
- **Transaction records** (`transactions`, `payments`, `billing_*`): Immutable audit trail — modifications should only ADD, never ALTER or DELETE.
- **PII columns** (`users.phone`, `users.curp`, identity data): Compliance implications for schema changes. Flag for privacy review.
- **Product tables** (`credit_card_accounts`, `credit_decisions`): Changes may affect active customer accounts.

## Step 3: Report

```
## Migration Review — [date]

### [filename]: [brief description]

**Operations:**
1. CREATE TABLE card_transactions — LOW risk
2. ADD COLUMN users.preferred_name (nullable) — LOW risk
3. CREATE INDEX idx_transactions_user_id — MEDIUM risk (not CONCURRENTLY)

| Check                  | Status   | Details                                       |
|------------------------|----------|-----------------------------------------------|
| Backward compatibility | OK       | New table + nullable column, no breaking changes |
| Data loss              | OK       | No destructive operations                     |
| Lock duration          | WARN     | CREATE INDEX on users may lock (~Xk rows)     |
| Rollback plan          | OK       | down() drops table and column                 |
| Data migration         | N/A      | No data transformation                        |
| Financial tables       | OK       | Does not touch ledger or billing tables        |
| PII impact             | OK       | No PII columns affected                       |

**Verdict: SAFE / CAUTION / DANGEROUS**
```

**Verdict logic:**
- **SAFE:** All checks pass, no warnings
- **CAUTION:** Warnings present but manageable — proceed carefully, test on staging first
- **DANGEROUS:** Data loss risk, financial table modifications, or no rollback plan — requires manual review and staging test before production

### Recommendations

For CAUTION/DANGEROUS verdicts, provide specific remediation:
- "Use CREATE INDEX CONCURRENTLY to avoid locking"
- "Add a down() function that reverses this migration"
- "Split into two migrations: first add nullable column, then backfill, then add NOT NULL constraint"
- "Test on staging with production-like data volume first"

## Rules

- Read-only — never run migrations, only review them
- For DANGEROUS verdicts, always recommend staging test first
- If the migration touches financial tables, always flag for extra review regardless of operation type
- Recommend splitting large migrations (>3 operations on different tables) into phases
- Check that `down()` actually reverses `up()` — don't just check it exists
