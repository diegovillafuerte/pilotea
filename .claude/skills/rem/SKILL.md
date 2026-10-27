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
- **`dry`**: Run all phases but make NO modifications to the repo — only show what would change
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
- Read current CLAUDE.md: `$MAIN_PATH/CLAUDE.md` (it imports the shared instructions from `$MAIN_PATH/AGENTS.md` with `@AGENTS.md`)
- Extract all backtick-quoted file paths from CLAUDE.md and verify each exists

### 2.2: Detect drift

Compare:
- **Skills:** actual skills vs skills mentioned in CLAUDE.md (the "Other skills" line and any skill tables)
- **File refs:** backtick-quoted paths that don't exist on disk

### 2.3: Apply fixes

If any drift detected, spawn the **claude-md-sync subagent** (read prompt from `$MAIN_PATH/.claude/skills/rem/healers/claude-md-sync.md`):
1. Replace `{CLAUDE_MD}` with current CLAUDE.md content
2. Replace `{ACTUAL_SKILLS}` with the list of actual skills (name + description from each SKILL.md frontmatter)
3. Replace `{DEAD_REFS}` with the list of backtick-quoted paths that don't exist

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

Kompara's docs carry no frontmatter. The **Doc Inventory** in `$MAIN_PATH/.claude/skills/ship/reviewers/docs.md` maps each doc to the source paths it describes, and `/ship` and `/housekeeper` read the same inventory. Docs it leaves out on purpose (dated records, research, legacy reference) are never checked. Run:

```bash
python3 "$MAIN_PATH/.claude/skills/_shared/doc-freshness.py"
```

It works from the git repo at `$MAIN_PATH`. A commit-message line `Doc-Verified: <doc> <commit>` records that the doc was checked against the code at `<commit>` and found accurate; rem writes those lines in Phase 6.2. A doc's baseline is the newest of the commit that wrote its current text and the verified commits where it already had that text. Each `STALE` line is a doc whose sources at HEAD differ from its sources at the baseline; the files that differ are listed under it. The report's last line names the HEAD commit it checked: keep it for Phase 3.2 and Phase 6.2.

- `DEAD`, `MISSING` and `UNMAPPED` lines are inventory problems. Rem doesn't edit the inventory, which belongs to `/ship`: add each line to "Needs Human Attention".
- If the script exits non-zero, add its error message to "Needs Human Attention" and skip the rest of this phase.

### 3.2: Spawn doc-updater subagents

Make a fresh directory for this run's files with `mktemp -d /tmp/kompara-rem.XXXXXX`, and use the path it prints as `$REM_TMP` below. It is outside the repo, so `dry` mode uses it too. Below, `<commit>` is the commit on the last line of the Phase 3.1 report and `<slug>` is the doc's path with each `/` replaced by `_`. For each stale doc, prepare a subagent call:

1. Write the changes to the doc's sources between its baseline and `<commit>` to a file, never into this conversation (they can run to thousands of lines):
   ```bash
   python3 "$MAIN_PATH/.claude/skills/_shared/doc-freshness.py" --diff <doc> <commit> > "$REM_TMP/<slug>.diff"
   ```
   If it exits non-zero or the file is empty, spawn no doc-updater for that doc: flag it in "Needs Human Attention" with the error, and don't record it as verified.
2. Save the doc as `<commit>` has it, so the doc-updater reviews exactly that text whatever happens to the working copy meanwhile: `git -C "$MAIN_PATH" show <commit>:<doc> > "$REM_TMP/<slug>.doc"`
3. Read the prompt template from `$MAIN_PATH/.claude/skills/rem/healers/doc-updater.md`
4. Replace `{DOC_PATH}` with `$REM_TMP/<slug>.doc`, `{DIFF_PATH}` with `$REM_TMP/<slug>.diff`, `{OUT_PATH}` with `$REM_TMP/<slug>.new`, and `{REPO}` with `$MAIN_PATH`

**Launch ALL doc-updater agents in a single message** so they execute in parallel. Use `model: "opus"` for each. Each one either writes the complete updated doc to `{OUT_PATH}` and replies `UPDATED: <summary>`, or replies `NO_CHANGES_NEEDED`.

### 3.3: Apply updates

For each subagent response:
1. `NO_CHANGES_NEEDED`: the doc still matches the code. Record it in the report as verified, and give it a `Doc-Verified:` line in the Phase 6.2 commit so the next run doesn't check it again.
2. `UPDATED` for `AGENTS.md`, `CLAUDE.md`, `docs/play-compliance.md` or `docs/privacy-policy.md`: don't apply it. Rem never edits `AGENTS.md`, and Phase 2 owns `CLAUDE.md`. The two compliance docs back the Play declarations: when code contradicts them, the code may be what's wrong, so a human decides which side changes. Flag in "Needs Human Attention": "<doc> is stale against its sources: <summary>"
3. `UPDATED` for any other doc in `docs/`:
   a. Count the number of changed lines vs the original doc (`<slug>.doc` against `<slug>.new`)
   b. **Safety check:** if >40% of lines changed, do NOT apply. Instead, flag in "Needs Human Attention": "docs/X.md: subagent wants to change N% of content — manual review needed"
   c. If within threshold: `python3 "$MAIN_PATH/.claude/skills/_shared/doc-freshness.py" --apply <doc> <commit> "$REM_TMP/<slug>.new"`. It writes and stages the update only while the index and the working copy hold exactly the reviewed text, so local edits are never overwritten, and never through a symlink. If it exits 2, nothing was written: flag in "Needs Human Attention": "<doc>: update not applied: <its message>". If it exits 3, the working copy was replaced but not staged, so the update stays out of this run's commit: flag in "Needs Human Attention": "<doc>: updated in the working copy but not staged: <its message>". Otherwise record in report
   d. In `dry` mode: show the diff but don't write
4. Any other reply, or `UPDATED` with no file at `{OUT_PATH}`: flag the doc in "Needs Human Attention" and don't record it as verified.

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
(cd "$MAIN_PATH/backend" && pnpm test 2>&1 | tail -5)
# Android unit tests recorded by the last Gradle run (rem does not run Gradle itself)
find "$MAIN_PATH/android" -path '*/build/test-results/testDebugUnitTest/*.xml' -exec grep -o '<testcase ' {} + | wc -l
```

Extract the test counts (backend from vitest, Android from the last Gradle run). Check if a previous rem report exists in `$MAIN_PATH/pming/reports/` and compare test counts. Report the delta.

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
<list each doc updated, with a one-line summary of what changed, then each doc verified as still accurate>

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
   - Not the updated docs: `--apply` (Phase 3.3) already staged exactly what the doc-updater wrote, and adding them again would re-read the working copy
   - Updated CLAUDE.md (if changed)
   - Updated techdebt.md (if changed)
   - The report file itself
2. Check that no source code files (`src/`, `test/`, `admin/`) are staged — if any are, unstage them immediately. Rem must never commit source changes.
   If Phase 3 ran, also check that `git -C "$MAIN_PATH" rev-parse HEAD` still prints the Phase 3.1 commit. If it doesn't, another commit landed during the run, and committing rem's doc updates on top of it would mark that commit's source changes as seen: don't commit. Display the report with this reason and STOP.
3. Commit:
   ```bash
   git commit -m "$(cat <<'EOF'
   rem: nightly maintenance — N fixes, M doc updates

   Doc-Verified: <doc> <commit>
   Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
   EOF
   )"
   ```
   Replace N and M with actual counts. Write one `Doc-Verified: <doc> <commit>` line for each doc the doc-updater found still accurate (Phase 3.3): its repo path, such as `docs/privacy-policy.md`, then the full commit from the last line of the Phase 3.1 report, the one every Phase 3.2 diff was taken at. Write none when there are none. The next run's freshness check reads these lines.
4. Push: `git push origin main`
5. Display a brief summary (3-5 lines) of what was done.

If the commit or push fails, display the error and STOP. Do not retry.

## Safety Guardrails

These are non-negotiable constraints. Violating any of them is a critical failure.

1. **Never modify source code.** Rem only touches: `docs/`, `pming/`, `CLAUDE.md`, `techdebt.md`, its own report files, and the run's scratch directory under `/tmp` (Phase 3.2). If you find yourself about to write to `src/`, `test/`, `admin/`, or any `.ts` file — STOP.
2. **Never delete files.** Only move PM files between `pending/` and `done/`. Never `rm` a doc, a task file, or anything else.
3. **Doc update size cap.** If a doc-updater subagent returns content that changes >40% of the original doc's lines, skip that doc and flag for human review.
4. **CLAUDE.md change cap.** Never apply more than 15 changed lines to CLAUDE.md in a single run.
5. **Dry-run mode.** When invoked as `/rem dry`, make ZERO modifications to any file outside the run's scratch directory under `/tmp`, where the doc-updaters put their proposals. Only read and report.
6. **Idempotency.** Running rem twice in a row should produce no changes on the second run (all drift already fixed).
7. **No destructive git.** Never use `--force`, `--no-verify`, `--hard`, `clean -f`, or `branch -D`.
8. **No interactive prompts.** Rem runs autonomously. Never ask the user a question. If uncertain, skip the action and flag it in "Needs Human Attention".
