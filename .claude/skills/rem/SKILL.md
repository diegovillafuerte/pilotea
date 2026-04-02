---
name: rem
description: Self-healing maintenance agent. Fixes PM drift, updates stale docs, syncs CLAUDE.md, triages techdebt, and commits changes. Runs autonomously (nightly) or manually. Use /rem for full run, /rem quick for PM-only, /rem dry for preview.
argument-hint: [quick | full | dry | docs-only]
---

# Rem: Self-Healing Harness Maintenance

Follow these phases exactly in order. Do NOT skip phases unless the mode excludes them.

## Phase 0: Setup

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

Determine mode from `$ARGUMENTS`:
- **empty or `full`**: Run all phases (default — used for nightly runs)
- **`quick`**: Phase 1 only (PM hygiene fixes, ~30 seconds)
- **`dry`**: Run all phases but make NO modifications — only show what would change
- **`docs-only`**: Phase 3 only (documentation freshness healing)

Record the current date (`date -u +%Y-%m-%d`) and start time. Initialize an empty report structure in memory:

```
Report sections:
- PM Hygiene fixes: []
- CLAUDE.md changes: []
- Docs updated: []
- Techdebt changes: []
- Memory flags: []
- Needs human attention: []
- Improvement opportunities: []
```

## Phase 1: PM Hygiene Healing

These are mechanical fixes — no LLM judgment needed.

### 1.1: Done tasks in wrong folder

Search `$MAIN_PATH/pming/tasks/pending/` for files where the YAML frontmatter has `status: done`. For each:
- Move the file to `$MAIN_PATH/pming/tasks/done/`
- Record in report: "Moved B-XXX to tasks/done/ (status was done)"

In `dry` mode: just record what would move, don't move.

### 1.2: Completed stories still in pending

For each story in `$MAIN_PATH/pming/stories/pending/`:
1. Read its frontmatter to get the list of task IDs (look for `tasks:` field or scan the body for `B-XXX` references)
2. Check if ALL referenced tasks have `status: done` or `status: cancelled` (check both `pending/` and `done/` folders)
3. If all resolved (done or cancelled): update the story's `status:` to `done`, move to `$MAIN_PATH/pming/stories/done/`
4. Record in report

### 1.3: Completed epics still in pending

Same logic as 1.2 but for epics in `$MAIN_PATH/pming/epics/pending/`. An epic is complete when all its stories are done.

### 1.4: Stale workers cleanup

```bash
$MAIN_PATH/pming/worker.sh cleanup
```

Record any cleaned entries in report.

### 1.5: Orphaned worktrees

List git worktrees. For each worktree with a branch like `work/B-XXX`:
- Check if B-XXX has `status: done`
- If done: `git worktree remove <path>` and `git branch -d work/B-XXX`
- Record in report

### 1.6: Report-only items (no auto-fix)

These need human judgment — just flag them in "Needs Human Attention":
- Tasks with `status: in_progress` but no git commits touching related files in the last 2 days (check via `git log --since="2 days ago"`)
- Tasks in `pending/` with no story or epic reference (orphaned)
- Non-done tasks sitting in `done/` folders

**If mode is `quick`: skip to Phase 6 (Commit & Report).**

## Phase 2: CLAUDE.md Sync

### 2.1: Gather actual state

Run these in parallel:
- List all skill directories: `ls $MAIN_PATH/.claude/skills/`
- List all command files: `find $MAIN_PATH/.claude/commands/ -name "*.md" -type f`
- List all src top-level directories: `ls -d $MAIN_PATH/src/*/`
- Read current CLAUDE.md: `$MAIN_PATH/CLAUDE.md`
- Extract all backtick-quoted file paths from CLAUDE.md and verify each exists

### 2.2: Detect drift

Compare:
- **Skills:** actual skills vs skills mentioned in CLAUDE.md (the "Other skills" line and any skill tables)
- **Directories:** actual `src/` directories vs the project structure table in CLAUDE.md
- **File refs:** backtick-quoted paths that don't exist on disk

### 2.3: Apply fixes

If any drift detected, spawn the **claude-md-sync subagent** (read prompt from `$MAIN_PATH/.claude/skills/rem/healers/claude-md-sync.md`):
1. Replace `{CLAUDE_MD}` with current CLAUDE.md content
2. Replace `{ACTUAL_SKILLS}` with the list of actual skills (name + description from each SKILL.md frontmatter)
3. Replace `{ACTUAL_SRC_DIRS}` with the list of actual `src/` directories
4. Replace `{DEAD_REFS}` with the list of backtick-quoted paths that don't exist

Launch as a single Agent call with `model: "opus"`.

When the subagent returns:
- Diff the result against the original CLAUDE.md
- **Safety cap:** count changed lines. If >15 lines changed, only apply the subagent's changes to the skill list and project structure table sections. Flag the rest in "Needs Human Attention".
- In `dry` mode: show the diff but don't apply
- Write the updated CLAUDE.md
- Record all changes in report

## Phase 3: Documentation Freshness Healing

This is the core differentiator — rem actually rewrites stale docs.

### 3.1: Identify stale docs

For each markdown file in `$MAIN_PATH/docs/` (recursive), read YAML frontmatter. Skip files without `sources:` and `last_verified:` fields.

For each doc with source mapping:
1. Parse the `sources:` list and `last_verified:` date. If `last_verified:` is missing or not a valid date, treat the doc as maximally stale (use `1970-01-01`) and flag in report: "docs/X.md: missing or malformed last_verified — treating as stale"
2. Get latest commit date across all source files:
   ```bash
   git log -1 --format=%ci -- <source1> <source2> ...
   ```
3. If the latest source commit is **after** `last_verified:`, mark the doc as stale
4. Identify which specific source files changed:
   ```bash
   git log --since="<last_verified>" --name-only --format="" -- <sources> | sort -u
   ```

### 3.2: Spawn doc-updater subagents

For each stale doc, prepare a subagent call:

1. Read the prompt template from `$MAIN_PATH/.claude/skills/rem/healers/doc-updater.md`
2. Read the current doc content
3. Get the source diffs since last verification:
   ```bash
   git log --since="<last_verified>" -p -- <changed_sources>
   ```
4. Replace `{DOC_CONTENT}` with the current doc
5. Replace `{SOURCE_DIFFS}` with the source diffs
6. Replace `{TODAY}` with today's date

**Launch ALL doc-updater agents in a single message** so they execute in parallel. Use `model: "opus"` for each.

### 3.3: Apply updates

For each subagent response:
1. Count the number of changed lines vs the original doc
2. **Safety check:** if >40% of lines changed, do NOT apply. Instead, flag in "Needs Human Attention": "docs/X.md: subagent wants to change N% of content — manual review needed"
3. If within threshold: write the updated doc, record in report
4. In `dry` mode: show the diff but don't write

For docs where sources haven't changed but `last_verified` is >30 days old: just bump `last_verified` to today (the doc is still accurate, just needs re-stamping).

**If mode is `docs-only`: skip to Phase 6 (Commit & Report).**

## Phase 4: Techdebt & Memory Hygiene

### 4.1: Techdebt — archive resolved entries

Read `$MAIN_PATH/techdebt.md`. Find any entries marked as `RESOLVED`. If there are more than 3 resolved entries in the active section, move them to a `## Resolved` section at the bottom of the file.

### 4.2: Techdebt — find untracked TODOs

Run:
```bash
grep -rn "TODO\|FIXME\|HACK" $MAIN_PATH/src/ --include="*.ts" | grep -v node_modules | grep -v dist
```

Compare against existing techdebt entries. For comments not yet tracked, spawn the **techdebt-triage subagent** (read prompt from `$MAIN_PATH/.claude/skills/rem/healers/techdebt-triage.md`):
1. Replace `{TECHDEBT_MD}` with current techdebt.md content
2. Replace `{UNTRACKED_COMMENTS}` with the list of untracked TODO/FIXME/HACK comments

Use `model: "opus"`.

If the subagent returns new entries (not "NO_NEW_ENTRIES"), append them to techdebt.md. Record in report.

In `dry` mode: show what would be added but don't append.

### 4.3: Techdebt — check for stale entries

For each techdebt entry that references a specific file path, verify the file exists. If the file was deleted or the referenced line no longer contains a TODO/FIXME, flag in "Needs Human Attention": "TD-XX may be resolved — referenced file/line changed"

### 4.4: Memory hygiene

Read `$MAIN_PATH/../.claude/projects/-Users-diegovillafuerte-ongoing-projects-pilotea/memory/MEMORY.md`. Note: the memory directory path may vary — if this path doesn't work, skip this step silently.

For each memory entry that references a file path in its description or linked file:
- Verify the path exists
- If it doesn't, flag in "Needs Human Attention": "Memory 'X' references path that no longer exists"

Do NOT auto-delete or modify memories. Memory removal requires human judgment.

## Phase 5: Self-Improvement Analysis

This phase is **report-only** — it never modifies files. It populates the "Improvement Opportunities" section.

### 5.1: New directories

```bash
git log --since="7 days ago" --diff-filter=A --name-only --format="" | grep "^src/" | cut -d'/' -f1-2 | sort -u
```

For each new `src/` directory, check if it's mentioned in `docs/ARCHITECTURE.md`. Flag any that aren't.

### 5.2: Recurring commit patterns

```bash
git log --since="7 days ago" --oneline | grep -i "fix lint\|fix test\|fix type" | wc -l
```

If >3 "fix lint/test/type" commits in the last week, suggest: "Consider adding a pre-commit hook or improving the relevant lint rule."

### 5.3: Skill consistency

Check if any skill referenced in `$MAIN_PATH/docs/guides/agentic-development.md` doesn't exist in `.claude/skills/` or `.claude/commands/`. Flag discrepancies.

### 5.4: Test count trend

```bash
pnpm test --reporter=verbose 2>&1 | tail -5
```

Extract the test count. Check if a previous rem report exists in `$MAIN_PATH/pming/reports/` and compare test counts. Report the delta.

## Phase 6: Commit & Report

### 6.1: Generate report

Write the report to `$MAIN_PATH/pming/reports/rem-$(date -u +%Y-%m-%d).md` using this format:

```markdown
# Rem Report — YYYY-MM-DD

Run at: HH:MM UTC | Mode: <mode> | Duration: <seconds>s

## Fixes Applied

### PM Hygiene
<list each fix, one bullet per action>

### CLAUDE.md Sync
<list each change made to CLAUDE.md>

### Documentation Updates
<list each doc updated, with a one-line summary of what changed>

### Techdebt
<list new entries added or entries archived>

## Needs Human Attention
<list items that require human judgment, with context>

## Improvement Opportunities
<list observations from Phase 5>

## Summary
Applied N fixes. Updated M docs. K items need human attention. J improvement suggestions.
```

If any section has no items, write "None" under it. Keep the section headers for consistency.

### 6.2: Commit (skip in dry mode)

In `dry` mode: display the report and a summary of all changes that would have been made. Then STOP.

In all other modes:

1. Stage all changed files **by name** (NEVER use `git add -A` or `git add .`):
   - Changed PM files (tasks, stories, epics moved between folders)
   - Updated docs (`docs/**/*.md`)
   - Updated CLAUDE.md (if changed)
   - Updated techdebt.md (if changed)
   - The report file itself
2. Check that no source code files (`src/`, `test/`, `admin/`) are staged — if any are, unstage them immediately. Rem must never commit source changes.
3. Commit:
   ```bash
   git commit -m "$(cat <<'EOF'
   rem: nightly maintenance — N fixes, M doc updates

   Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
   EOF
   )"
   ```
   Replace N and M with actual counts.
4. Push: `git push origin main`
5. Display a brief summary (3-5 lines) of what was done.

If the commit or push fails, display the error and STOP. Do not retry.

## Safety Guardrails

These are non-negotiable constraints. Violating any of them is a critical failure.

1. **Never modify source code.** Rem only touches: `docs/`, `pming/`, `CLAUDE.md`, `techdebt.md`, and its own report files. If you find yourself about to write to `src/`, `test/`, `admin/`, or any `.ts` file — STOP.
2. **Never delete files.** Only move PM files between `pending/` and `done/`. Never `rm` a doc, a task file, or anything else.
3. **Doc update size cap.** If a doc-updater subagent returns content that changes >40% of the original doc's lines, skip that doc and flag for human review.
4. **CLAUDE.md change cap.** Never apply more than 15 changed lines to CLAUDE.md in a single run.
5. **Dry-run mode.** When invoked as `/rem dry`, make ZERO modifications to any file. Only read and report.
6. **Idempotency.** Running rem twice in a row should produce no changes on the second run (all drift already fixed).
7. **No destructive git.** Never use `--force`, `--no-verify`, `--hard`, `clean -f`, or `branch -D`.
8. **No interactive prompts.** Rem runs autonomously. Never ask the user a question. If uncertain, skip the action and flag it in "Needs Human Attention".
