---
name: housekeeper
description: Systematic project health check across PM hygiene, documentation freshness, and codebase health. Finds drift, stale items, and inconsistencies. Use /housekeeper for a quick check or /housekeeper full to include tests and lint.
argument-hint: [quick | full]
---

# Housekeeper — Project Health Check

Audit project health across three dimensions. Report findings, then offer to fix what can be auto-fixed.

## Severity markers

- `[!!]` critical — must fix (test/lint failures, rule violations)
- `[+]` auto-fixable — skill can fix with user approval
- `[~]` warning — needs human judgment
- `[i]` advisory — nice to know
- `[ok]` passing — no issues

## Step 0: Determine mode

From $ARGUMENTS:
- **Empty or `quick`**: Skip tests and lint (fast, ~10 seconds)
- **`full`**: Run everything including the Android and backend checks (several minutes; Gradle dominates)

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

Kompara's CLAUDE.md holds only what is specific to Claude Code and pulls in the shared instructions from `AGENTS.md` with its `@AGENTS.md` line, so every session loads both. Audit them together, and name the file each finding is in.

### CMD-1: File references exist

Extract every file path and directory mentioned in CLAUDE.md and AGENTS.md (look for backtick-quoted paths like `docs/...`, `android/...`, `backend/...`, `pming/...`, `.claude/...`, `techdebt.md`). For each, verify the file/directory exists on disk. Skip paths outside the repo (`/tmp/...`, `/Applications/...`), placeholders (`.worktrees/<task-id>`), globs (`android/**`), and names that aren't repo paths: a file on the device (`kompara.db`), a path inside each module (`src/test`), a naming pattern (`kebab-case.ts`, `types.ts`). A file the text itself says only the main checkout has (`.claude/settings.local.json`) counts as present when `$MAIN_PATH` has it.
- Severity: `[!!]` critical if any referenced file doesn't exist — agents will fail on step 1
- Fix: Remove or update the stale reference

### CMD-2: Repo map vs reality

1. Run `git ls-tree -d --name-only HEAD` to list the top-level directories git tracks. Flag any that the `## Repo map` table in AGENTS.md doesn't list, leaving out the tooling directories `.claude` and `.github`. (CMD-1 already flags listed paths that don't exist.)
2. Compare the modules on the "Android modules:" line below that table with the `include(":…")` lines in `android/settings.gradle.kts`. Flag a module that is in one but not the other.
3. Check what that line says about which modules use Compose, Hilt and KSP against the `plugins {}` block of each `android/<module>/build.gradle.kts` (`libs.plugins.kotlin.compose`, `libs.plugins.hilt`, `libs.plugins.ksp`).
- Severity: `[~]` warning — agents get a wrong mental model of the codebase

### CMD-3: Opening description matches current state

Read the opening paragraph of AGENTS.md (CLAUDE.md shows it through the import). Check:
1. Each app it says the reader reads has a parser spec registered in `SPEC_NAMES` in `android/parsers/src/main/java/mx/kompara/parsers/spec/BundledSpecs.kt`. A registered spec for an app the paragraph doesn't name may be a platform that hasn't launched yet: report it as `[i]` advisory.
2. Does it present the Android app as the product? The Next.js web MVP in `src/` is legacy reference, not current.
3. Each thing it says the backend does still has a route module mounted in `backend/src/app.ts`.
- Severity: `[~]` warning

### CMD-4: "How to work" section is actionable

Read the workflow sections: "How to work (skills)" in CLAUDE.md and "How work is organized" in AGENTS.md. Check:
1. Every file referenced in the workflow steps exists
2. The steps verify with the "Verify before calling work done" table in AGENTS.md (Gradle from `android/`, pnpm from `backend/`), not with the legacy web app's root `pnpm test` and `pnpm lint` as the default gate
3. Skills mentioned actually exist in `.claude/skills/` or `.claude/commands/`. A name with a plugin prefix (`/codex:rescue`) comes from a plugin: check that this session lists it instead.
- Severity: `[!!]` critical if workflow points to nonexistent files; `[~]` warning for a skill that doesn't exist or a plugin skill this session doesn't list

### CMD-6: Verify table matches CI

AGENTS.md says its "Verify before calling work done" table mirrors CI.
1. For each workflow in `.github/workflows/`, list its gates (the `run:` steps other than installs, each with its job's `working-directory`) and the paths it triggers on
2. Compare them with the table row for those paths. The legacy `src/**` row has no workflow.
3. Flag a gate the row leaves out, a row command the workflow doesn't run, and a workflow with no row
4. Check the Java, Node and pnpm versions AGENTS.md names against the versions the workflows set up (`actions/setup-java`, `actions/setup-node`, `pnpm/action-setup`) and the Node range `backend/package.json` requires
- Severity: `[~]` warning — agents hand off work that CI then fails

### CMD-7: Line count

Count lines in CLAUDE.md and AGENTS.md together, since every session loads both. Warn if > 300 (research shows instruction quality degrades beyond this).
- Severity: `[i]` advisory

### CMD-8: Backend .env.example completeness

The backend is the part of Kompara that reads environment variables, and `backend/.env.example` documents them. The root `.env.example` belongs to the legacy web app: don't check it.
1. List the variables the backend reads outside its tests: `git grep -hoE 'env\.[A-Z][A-Z0-9_]+' -- 'backend/*.ts' ':!backend/*.test.ts' ':!backend/src/test/*' | sed 's/^env\.//' | sort -u`. The pattern catches `process.env.X` and the `env.X` reads of helpers that take the environment as a parameter, such as `senderFromEnv`. Add the variables a library reads by itself, which no `env.` line shows: an SDK client built without credentials reads them from the environment, like the bare `new Anthropic()` in `backend/src/imports/claude.ts`, which reads `ANTHROPIC_API_KEY`.
2. Read the keys in `backend/.env.example`. A commented-out key (`# KEY=`) counts as documented.
3. Flag variables read in code but missing from `backend/.env.example`
4. Flag if `backend/.env.example` doesn't exist at all
- Severity: `[~]` warning

### Display CLAUDE.md results

Use the standard format. Group passing checks. For critical findings, include the specific stale reference and what it should say.

## Step 3: Documentation Freshness

### DOC-1: Architecture docs cover every module

1. Android: for each module in `android/settings.gradle.kts`, grep `docs/android-technical-design.md` for `:<module>`. Flag modules not mentioned ANYWHERE in it.
2. Backend: for each directory under `backend/src/` (depth 1), check the fenced block under `## Layout` in `backend/README.md` lists it, as `<dir>/` or as a file inside it. Flag directories it doesn't list.
- Severity: `[~]` warning

### DOC-2: techdebt.md resolved items

Grep `techdebt.md` for `RESOLVED` entries. If > 3, suggest archiving.
- Severity: `[i]` advisory

### DOC-3: Untracked TODO/FIXME comments

Run: `git grep -nwE 'TODO|FIXME|HACK|XXX' -- 'android/*.kt' 'android/*.kts' 'android/*.java' 'backend/*.ts'`
`git grep` searches tracked files only, so Gradle `build/` output and `node_modules/` stay out. Keep `-w`: macOS `git grep` doesn't support `\b`.
For each hit, check if a corresponding entry exists in `techdebt.md`: one that names the file, or the task the comment names (`TODO(legal-B038)` → B-038, `TODO(techdebt TD-B034)` → B-034), and describes the same work. Report untracked ones.
- Severity: `[i]` advisory

### DOC-5: Source-mapped doc freshness

Kompara's docs carry no frontmatter. The **Doc Inventory** in `.claude/skills/ship/reviewers/docs.md` maps each doc to the source paths it describes, and `/ship` and `/rem` read the same inventory. Docs it leaves out on purpose (dated records, research, legacy reference) aren't checked. Run:

```bash
python3 .claude/skills/_shared/doc-freshness.py
```

It works from git alone and prints one line per inventory doc. A commit-message line `Doc-Verified: <doc> <commit>` records that the doc was checked against the code at `<commit>` and found accurate. A doc's baseline is the newest of the commit that wrote its current text and the verified commits where it already had that text. A doc is `STALE` when its sources at HEAD differ from its sources at the baseline; the files that differ are listed under it. The script also reports three inventory problems, each of which would otherwise make a doc skip this check in silence: `DEAD` (a source entry that matches no tracked file by the inventory's rules), `MISSING` (a listed doc that HEAD doesn't have), `UNMAPPED` (a doc under `docs/` that is in neither the inventory nor its left-out list).

- `STALE`: `[~]` warning — the doc may need updating. Report the doc, its baseline (date and commit), and the changed source files.
  Suggested fix: "Run `python3 .claude/skills/_shared/doc-freshness.py --diff <doc>`; its first line names the commit it checked (`Checked <commit>`). Read the doc as that commit has it (`git show <commit>:<doc>`) against those changes, and update it. If it is still accurate, record the check with `python3 .claude/skills/_shared/doc-freshness.py --verify <doc> <commit>`, which refuses if HEAD's or your copy of the doc differs from that commit's, and keeps anything staged out of its commit. `/rem docs-only` runs the same check and updates or verifies what it can."
- `DEAD`, `MISSING`, `UNMAPPED`: `[~]` warning. Suggested fix: "Update the Doc Inventory in `.claude/skills/ship/reviewers/docs.md`; `/ship` uses it to decide whether the Docs reviewer runs."
- If the script exits non-zero: `[?] Check skipped: <its error message>`.

### Display documentation results

Same format as previous sections. Group passing checks together.

## Step 4: Codebase Health

### CODE-1 & CODE-2: Tests and lint (full mode only)

If mode is `full`:
1. Android, from `android/`: `./gradlew verifyReadOnlyCapture assembleDebug testDebugUnitTest lintDebug` — report pass/fail with a failure summary (set `JAVA_HOME` first if it is empty; see AGENTS.md)
2. Backend, from `backend/`: `pnpm typecheck && pnpm test` — report pass/fail with a failure summary
- Severity: `[!!]` critical if either fails

If mode is `quick`: show `Skipped (use /housekeeper full to include)`

### CODE-3: Rule violations

Check the "Rules that are easy to break" in AGENTS.md that a search can catch (always, even in quick mode). Items 1 and 2 are a quick stand-in for `./gradlew verifyReadOnlyCapture`, which full mode runs (CODE-1); its token lists are in `android/build.gradle.kts`.

1. **Accessibility automation in production code.** Search for each token in the task's `bannedAutomationTokens`, one `-e` per token:
   `git grep -nF -e '<token>' -- 'android/*/src/*.kt' 'android/*/src/*.java' ':!android/*/src/test/*' ':!android/*/src/androidTest/*'`
2. **The read-only declaration.** Find the accessibility-service configs: the XML files under `android/*/src/*/res/xml/` that contain `<accessibility-service`. There must be at least one, since the task fails without one. Ignoring XML comments, as the task does, each keeps `android:isAccessibilityTool="false"` and declares none of the task's `bannedConfigTokens`.
3. **`FLAG_SECURE` on our windows:** `git grep -nw FLAG_SECURE` over the same production sources as item 1. Flag code that sets it, not a comment explaining why it isn't used.

- Severity: `[!!]` critical if any violations found

### CODE-4: Registration completeness

Two lists register code by hand, and both fail silently when they drift:
1. **Parser specs.** `SPEC_NAMES` in `android/parsers/src/main/java/mx/kompara/parsers/spec/BundledSpecs.kt` names every spec the app loads, and a name whose file is missing is skipped without an error. Compare it with `android/parsers/src/main/resources/specs/*.json`: flag a file whose name isn't in the list (it never loads) and a name with no file.
2. **Backend routes.** Every module in `backend/src/routes/` other than its `*.test.ts` files must be imported and mounted with `app.route(...)` in `backend/src/app.ts`. Flag one that isn't: its endpoints don't exist.
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
- Undocumented module: "Describe `:X` in `docs/android-technical-design.md`" or "Add `X/` to the Layout block in `backend/README.md`"
- Stale in_progress task: "Reset B-XXX to `todo` or pick it up with `/work B-XXX`"
- Rule violation: "Remove the `performGlobalAction(` call at `android/capture/src/main/java/mx/kompara/capture/Foo.kt:42` — capture stays read-only"

## Guidelines

- **Never modify files during Steps 1-4.** All changes happen in Step 5 after user approval.
- Use `git log -1 --format=%ci <file>` for timestamps, not filesystem `stat`.
- Use `$MAIN_PATH/pming/worker.sh cleanup` for stale worker detection (handles PID checks, expired pending entries).
- Run PM checks and doc checks in parallel where possible (they're independent).
- If a check fails to execute (e.g., a file doesn't exist), skip with `[?] Check skipped: <reason>` rather than stopping.
- Keep output scannable — one line per finding, details indented below.
