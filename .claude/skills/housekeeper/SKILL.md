---
name: housekeeper
description: Systematic project health check across PM hygiene, documentation freshness, and codebase health. Finds drift, stale items, and inconsistencies. Use /housekeeper for a quick check or /housekeeper full to include tests and lint.
argument-hint: [quick | full]
---

# Housekeeper — Project Health Check

Audit project health across three dimensions. Report findings, then offer to fix what can be auto-fixed.

## Severity markers

- `[!!]` critical — must fix (test/lint failures, boundary violations)
- `[+]` auto-fixable — skill can fix with user approval
- `[~]` warning — needs human judgment
- `[i]` advisory — nice to know
- `[ok]` passing — no issues

## Step 0: Determine mode

From $ARGUMENTS:
- **Empty or `quick`**: Skip `pnpm test` and `pnpm lint` (fast, ~10 seconds)
- **`full`**: Run everything including tests and lint (~60 seconds)

Resolve main worktree path for all steps:
```
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

## Step 1: PM System Hygiene

### 1.1: Gather state

Read frontmatter (id, title, status, priority, epic, story, category) from ALL files:
- `$MAIN_PATH/pming/tasks/pending/B-*.md` and `$MAIN_PATH/pming/tasks/done/B-*.md`
- `$MAIN_PATH/pming/stories/pending/S-*.md` and `$MAIN_PATH/pming/stories/done/S-*.md`
- `$MAIN_PATH/pming/epics/pending/E-*.md` and `$MAIN_PATH/pming/epics/done/E-*.md`
- Active workers: `$MAIN_PATH/pming/worker.sh list --json`

### 1.2: Run checks

**PM-1: Completed stories still in pending/**
For each story in `stories/pending/`: find all tasks matching `story: S-XXX` across both `pending/` and `done/`. If ALL tasks have `status: done`, flag.
- Severity: `[+]` auto-fixable
- Fix: Update `status: done` in frontmatter, move to `stories/done/`

**PM-2: Completed epics still in pending/**
For each epic in `epics/pending/`: find all stories matching `epic: E-XXX` across both `pending/` and `done/`. If ALL stories have `status: done`, flag.
- Severity: `[+]` auto-fixable
- Fix: Update `status: done` in frontmatter, move to `epics/done/`

**PM-3: Done tasks in wrong folder**
Tasks in `tasks/pending/` with `status: done` in frontmatter.
- Severity: `[+]` auto-fixable
- Fix: Move to `tasks/done/`

**PM-4: Non-done tasks in done folder**
Tasks in `tasks/done/` with status other than `done` or `cancelled`.
- Severity: `[~]` warning (may be intentional revert)

**PM-5: Stale workers**
Run `$MAIN_PATH/pming/worker.sh cleanup` to detect and remove stale entries (dead PIDs, expired pending claims, legacy entries).
- Severity: `[+]` auto-fixable
- Fix: `$MAIN_PATH/pming/worker.sh cleanup`

**PM-6: Stale in_progress tasks**
Tasks with `status: in_progress` that have NO matching entry in `worker.sh list --json` AND no git commits in the last 2 days (check `git log --since="2 days ago" --all --oneline`).
- Severity: `[~]` warning

**PM-7: Stale active stories**
Stories with `status: active` where ALL tasks are `done` or `backlog` (none `in_progress` or `todo`).
- Severity: `[~]` warning

**PM-8: Orphaned tasks**
Tasks in `tasks/pending/` with no `story` and no `epic` field in frontmatter. Tasks with only `story` but also having `epic` through the story's own frontmatter are fine — only flag truly orphaned ones.
- Severity: `[~]` warning

### 1.3: Display PM results

Show each check on one line with severity marker and count. For checks with findings, indent the details below. Group passing checks: `[ok] PM-4, PM-6, PM-8: No issues`.

## Step 2: CLAUDE.md Sharpness Audit

CLAUDE.md is the front door for every agent session. It must be razor-sharp: every instruction currently true, every reference pointing to something that exists, every description matching reality. A stale CLAUDE.md actively misleads agents and causes wasted work.

### CMD-1: File references exist

Extract every file path and directory mentioned in CLAUDE.md (look for backtick-quoted paths like `TASKS.md`, `BLUEPRINT.md`, `docs/...`, `src/...`, `pming/...`, `techdebt.md`). For each, verify the file/directory exists on disk.
- Severity: `[!!]` critical if any referenced file doesn't exist — agents will fail on step 1
- Fix: Remove or update the stale reference

### CMD-2: Project structure tree vs reality

1. Extract the fenced code block under `## Project structure` in CLAUDE.md
2. Run `find src -type d -maxdepth 3 | sort` and `find test -type d -maxdepth 3 | sort`
3. Flag directories present on disk but missing from the tree
4. Flag directories listed in the tree but not on disk
- Severity: `[~]` warning — agents get a wrong mental model of the codebase

### CMD-3: Opening description matches current state

Read the first paragraph of CLAUDE.md. Check:
1. Does it mention the currently active flow? (grep `src/flows/*/definition.ts` for flow directories, compare)
2. Does it say "mocked" when integrations are real (or vice versa)?
3. Does it reference V1 concepts as current?
- Severity: `[~]` warning

### CMD-4: "How to work" section is actionable

Read the "How to work" section. Check:
1. Every file referenced in the workflow steps exists
2. Instructions reference the pming/ system (not legacy TASKS.md/BLUEPRINT.md)
3. Skills mentioned actually exist in `.claude/skills/`
- Severity: `[!!]` critical if workflow points to nonexistent files

### CMD-5: Architectural decisions reference current concepts

Scan all AD-N entries. For each:
1. If it references specific state names (e.g., PRE_QUAL, LOAN_CONFIG), check if those states exist in any active flow definition.ts
2. If it references specific file paths, verify they exist
3. Flag ADs that reference only V1/archived concepts with no current equivalent
- Severity: `[i]` advisory — stale ADs cause confusion but aren't blocking

### CMD-6: Lint rules section matches eslint.config.js

1. Read `eslint.config.js` and extract all custom boundary rules
2. Read the "Custom lint rules" section in CLAUDE.md
3. Flag rules documented but not implemented, or implemented but not documented
4. Check the section heading — it should NOT say "to implement" if rules are implemented
- Severity: `[~]` warning

### CMD-7: Line count

Count lines in CLAUDE.md. Warn if > 300 (research shows instruction quality degrades beyond this).
- Severity: `[i]` advisory

### CMD-8: .env.example completeness

1. Run `grep -roh 'process\.env\.\w\+' src/ | sort -u` to find all env vars used in code
2. Read `.env.example` (if it exists)
3. Flag env vars used in code but missing from .env.example
4. Flag if .env.example doesn't exist at all
- Severity: `[~]` warning

### Display CLAUDE.md results

Use the standard format. Group passing checks. For critical findings, include the specific stale reference and what it should say.

## Step 3: Documentation Freshness

### DOC-1: ARCHITECTURE.md vs actual src/ layout

1. List all directories under `src/` (depth 1) and under `src/engine/` (depth 1)
2. Grep ARCHITECTURE.md for each directory name
3. Flag directories present in `src/` but not mentioned ANYWHERE in ARCHITECTURE.md

Note: ARCHITECTURE.md also has `sources:` frontmatter, so its overall freshness is tracked by DOC-5. DOC-1 still adds value by catching directories that exist in src/ but aren't mentioned at all in the doc.
- Severity: `[~]` warning

### DOC-2: techdebt.md resolved items

Grep `techdebt.md` for `RESOLVED` entries. If > 3, suggest archiving.
- Severity: `[i]` advisory

### DOC-3: Untracked TODO/FIXME comments

Run: `grep -rn "TODO\|FIXME\|HACK\|XXX" src/ --include="*.ts" | grep -v node_modules`
For each hit, check if a corresponding entry exists in `techdebt.md`. Report untracked ones.
- Severity: `[i]` advisory

### DOC-4: Flow docs freshness

For each flow directory in `src/flows/*/`:
1. Get last commit date of `definition.ts`: `git log -1 --format=%ci <path>`
2. Get last commit dates of `knowledge-base.md` and `tone.md`
3. If `definition.ts` is newer by > 1 day, flag.
- Severity: `[i]` advisory

### DOC-5: Source-mapped doc freshness

Docs in `docs/` have YAML frontmatter with `sources:` (file paths/glob patterns) and `last_verified:` (date). This check compares the doc's last_verified date against the latest commit date of its source files.

1. For each markdown file in `docs/` (recursively, excluding `docs/literature/`):
   a. Read the YAML frontmatter. If no `sources:` field, skip (evergreen doc).
   b. Parse the `sources:` list into file paths/glob patterns.
   c. For each source pattern:
      - If it ends with `/`, it's a directory — find all `.ts` files within it
      - Otherwise, treat as a specific file path
   d. Get the latest commit date across ALL matched source files:
      `git log -1 --format=%ci -- <file1> <file2> ...`
   e. Compare against `last_verified:` date from frontmatter.
   f. If the latest source commit is AFTER `last_verified`, flag the doc as potentially stale.

2. For each stale doc, report:
   - Doc path
   - `last_verified` date
   - Latest source change date
   - Which source files changed (list the ones with commits after last_verified)

- Severity: `[~]` warning — the doc may need updating
- Suggested fix: "Read <doc> and compare against changes in <source files>. Update doc content and set `last_verified` to today's date."

### Display documentation results

Same format as previous sections. Group passing checks together.

## Step 4: Codebase Health

### CODE-1 & CODE-2: Tests and lint (full mode only)

If mode is `full`:
1. Run `pnpm test` — report pass/fail with failure summary
2. Run `pnpm lint` — report pass/fail with violation count
- Severity: `[!!]` critical if either fails

If mode is `quick`: show `Skipped (use /housekeeper full to include)`

### CODE-3: Architecture boundary violations

Run these greps (always, even in quick mode):

1. **Engine importing from flows:**
   `grep -rn "from.*flows/" src/engine/ --include="*.ts"`
2. **Flows importing engine internals** (anything beyond `engine/types` and `engine/machine/types`):
   `grep -rn "from.*engine/" src/flows/ --include="*.ts"` — then filter OUT lines matching `engine/types` or `engine/machine/types`
3. **Engine importing from orchestrator config:**
   `grep -rn "from.*orchestrator/" src/engine/ --include="*.ts"`

- Severity: `[!!]` critical if any violations found

### CODE-4: Flow registration completeness

1. Glob `src/flows/*/definition.ts` to find all flow definitions
2. Read `src/orchestrator/config.ts` and check that each flow directory name appears in an agent's `flow_ids`
3. Flag flows that exist but aren't referenced
- Severity: `[~]` warning

### CODE-5: Orphaned worktrees

1. Run `git worktree list` — get all worktrees beyond the main one
2. For each worktree:
   - Extract the branch name
   - Check if the corresponding task (from branch name `work/B-XXX`) is in `pming/tasks/done/`
   - If the task is done but worktree still exists: flag
3. Also run `$MAIN_PATH/pming/worker.sh validate` to cross-check `.workers` entries vs worktrees vs task statuses
4. Also check `.worktrees/` directory for directories not tracked by `git worktree list` (fully orphaned)
- Severity: `[+]` auto-fixable
- Fix: `git worktree remove <path>` for listed worktrees; `rm -rf` for fully orphaned directories; `$MAIN_PATH/pming/worker.sh cleanup` for stale entries

### Display codebase results

Same format as previous sections.

## Step 5: Summary and Auto-Fix Offer

### 5.1: Show summary

```
## Health Summary

PM Hygiene:       N auto-fixable, N warnings
CLAUDE.md:        N critical, N warnings, N advisories
Documentation:    N warnings, N advisories
Codebase:         N critical, N warnings

Total: N findings (N auto-fixable, N critical, N warnings, N advisories)
```

### 5.2: Offer auto-fix

If there are auto-fixable issues (`[+]` items), list them and ask:

```
I can automatically fix N issues:
  1. Move S-002, S-006 to stories/done/ and update status
  2. Move B-039 to tasks/done/
  3. Remove 1 stale worker entry from .workers

Fix these now?
```

If user confirms: execute the fixes. Then re-run ONLY the affected checks to confirm they now pass.

If no auto-fixable issues: skip this step.

### 5.3: Suggest follow-ups

For non-auto-fixable issues, provide one concrete actionable suggestion per finding. Examples:
- Undocumented module: "Add a Package Responsibilities section for `src/X/` to ARCHITECTURE.md"
- Stale in_progress task: "Reset B-XXX to `todo` or pick it up with `/work B-XXX`"
- Boundary violation: "Fix import in `src/engine/foo.ts:42` — engine must not import from flows"

## Guidelines

- **Never modify files during Steps 1-4.** All changes happen in Step 5 after user approval.
- Use `git log -1 --format=%ci <file>` for timestamps, not filesystem `stat`.
- Use `$MAIN_PATH/pming/worker.sh cleanup` for stale worker detection (handles PID checks, expired pending entries).
- Run PM checks and doc checks in parallel where possible (they're independent).
- If a check fails to execute (e.g., a file doesn't exist), skip with `[?] Check skipped: <reason>` rather than stopping.
- Keep output scannable — one line per finding, details indented below.
