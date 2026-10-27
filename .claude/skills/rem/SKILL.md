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

Unless the mode is `dry`, check two things before changing anything. If either fails, display its message and STOP:
- `$MAIN_PATH` is on `main`: `git -C "$MAIN_PATH" symbolic-ref --short HEAD` prints `main`. Rem commits there and pushes `main` (Phase 6.2), so on any other branch its commit would never reach main. Message: "rem: $MAIN_PATH is on <branch>, not main; switch it to main, then run rem again".
- Nothing is staged there yet: `git -C "$MAIN_PATH" diff --cached --quiet` exits 0. Otherwise someone else's work is staged and rem's commit would carry it. Message: "rem: changes are already staged in $MAIN_PATH; commit or unstage them, then run rem again".

Then record the run's start commit, the one `main` points to: `git -C "$MAIN_PATH" rev-parse refs/heads/main`. Phase 6.2 commits on top of it, and only while `main` still points there.

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
- **Safety cap:** count changed lines. If >15 lines changed, only apply the subagent's changes to the skill lists (the "How to work (skills)" steps and the "Other skills" line). Flag the rest in "Needs Human Attention".
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

Run the same search as `/housekeeper` DOC-3. `git grep` searches tracked files only, so Gradle `build/` output and `node_modules/` stay out; keep `-w`, since macOS `git grep` doesn't support `\b`.
```bash
git -C "$MAIN_PATH" grep -nwE 'TODO|FIXME|HACK|XXX' -- 'android/*.kt' 'android/*.kts' 'android/*.java' 'backend/*.ts'
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

Claude Code keeps this project's memory in `$HOME/.claude/projects/<name>/memory/`, where `<name>` is `$MAIN_PATH` with every character other than a letter or digit replaced by `-` (`/home/me/kompara` becomes `-home-me-kompara`):

```bash
MEMORY_DIR="$HOME/.claude/projects/$(printf '%s' "$MAIN_PATH" | sed 's/[^A-Za-z0-9]/-/g')/memory"
```

Read `$MEMORY_DIR/MEMORY.md`. If it doesn't exist, add "Memory check skipped: no MEMORY.md in <MEMORY_DIR>" to "Needs Human Attention" and go on to Phase 5.

For each memory entry that references a file path in its description or linked file (a linked memory file is relative to `$MEMORY_DIR`, a repo path to `$MAIN_PATH`):
- Verify the path exists
- If it doesn't, flag in "Needs Human Attention": "Memory 'X' references path that no longer exists"

Do NOT auto-delete or modify memories. Memory removal requires human judgment.

## Phase 5: Self-Improvement Analysis

This phase is **report-only** — it never modifies files. It populates the "Improvement Opportunities" section.

### 5.1: New modules and backend directories

```bash
git -C "$MAIN_PATH" log --since="7 days ago" --diff-filter=A --name-only --format="" -- android backend/src | grep -oE '^(android/[^/]+/build\.gradle\.kts|backend/src/[^/]+/)' | sort -u
```

An `android/<module>/build.gradle.kts` line is a new Android module: check that `$MAIN_PATH/docs/android-technical-design.md` mentions `:<module>`. A `backend/src/<dir>/` line is a backend directory that gained files: check that the fenced block under `## Layout` in `$MAIN_PATH/backend/README.md` lists it. Flag any that aren't. This is `/housekeeper` DOC-1, limited to the week's additions.

### 5.2: Recurring commit patterns

```bash
git log --since="7 days ago" --oneline | grep -i "fix lint\|fix test\|fix type" | wc -l
```

If >3 "fix lint/test/type" commits in the last week, suggest: "Consider adding a pre-commit hook or improving the relevant lint rule."

### 5.3: Skill consistency

Check if any skill referenced in `$MAIN_PATH/pming/skills.md`, the PM system's guide to its skills, doesn't exist in `.claude/skills/` or `.claude/commands/`. Flag discrepancies. (Phase 2 already keeps the skills CLAUDE.md lists in sync.)

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

1. Stage all changed files **by name** with `git -C "$MAIN_PATH" add -- <path>...` (NEVER use `git add -A` or `git add .`):
   - Changed PM files (tasks, stories, epics moved between folders)
   - Not the updated docs: `--apply` (Phase 3.3) already staged exactly what the doc-updater wrote, and adding them again would re-read the working copy
   - Updated CLAUDE.md (if changed)
   - Updated techdebt.md (if changed)
   - The report file itself
2. Commit what this run staged, and nothing else, by running this block as one command. Fill it in first:
   - `START`: the start commit Phase 0 recorded. `PINNED`: the commit on the last line of the Phase 3.1 report. `REM_TMP`: the Phase 3.2 scratch directory. Leave `PINNED` and `REM_TMP` empty if Phase 3 didn't run.
   - The `printf` into `$REM_LIST`: every path this run staged, one argument each. That is the step 1 files, each moved PM file under both its old and its new path, and the docs Phase 3.3 applied.
   - The message: replace N and M with actual counts. Write one `Doc-Verified: <doc> <commit>` argument for each doc the doc-updater found still accurate (Phase 3.3): its repo path, such as `docs/privacy-policy.md`, then the full commit from the last line of the Phase 3.1 report, the one every Phase 3.2 diff was taken at. Write none when there are none. The next run's freshness check reads these lines. For the `Co-Authored-By:` line, use the one this session's commit attribution gives, which names the model running rem; if it gives none, leave it out.
   ```bash
   START=<the commit Phase 0 recorded>
   PINNED=<the commit on the last line of the Phase 3.1 report; empty if Phase 3 didn't run>
   REM_TMP=<the Phase 3.2 scratch directory; empty if Phase 3 didn't run>
   REM_LIST=$(mktemp /tmp/kompara-rem-paths.XXXXXX)
   printf '%s\n' <each path this run staged, both sides of a move> > "$REM_LIST"
   REM_MSG=$(mktemp /tmp/kompara-rem-msg.XXXXXX)
   printf '%s\n' "rem: nightly maintenance — N fixes, M doc updates" "" "Doc-Verified: <doc> <commit>" "Co-Authored-By: Claude <model> <noreply@anthropic.com>" > "$REM_MSG"
   REM_INDEX=$(mktemp /tmp/kompara-rem-index.XXXXXX)
   on_main() { [ "$(git -C "$MAIN_PATH" symbolic-ref -q HEAD)" = refs/heads/main ] && [ "$(git -C "$MAIN_PATH" rev-parse refs/heads/main)" = "$START" ]; }
   not_rems() { while IFS= read -r p; do
     new=""; case "$p" in docs/*) [ -n "$REM_TMP" ] && new="$REM_TMP/$(printf '%s' "$p" | tr / _).new" ;; esac
     if [ -n "$new" ] && [ -f "$new" ]; then want=$(git -C "$MAIN_PATH" hash-object --path="$p" -- "$new")
     elif [ -f "$MAIN_PATH/$p" ]; then want=$(git -C "$MAIN_PATH" hash-object -- "$p")
     else want=""; fi
     have=$(GIT_INDEX_FILE="$REM_INDEX" git -C "$MAIN_PATH" ls-files -s -- ":(literal)$p" | cut -d' ' -f2)
     [ "$have" = "$want" ] || printf '%s\n' "$p"
   done < "$REM_LIST"; }
   if [ -n "$PINNED" ] && [ "$PINNED" != "$START" ]; then
     echo "STOP: Phase 3 checked $PINNED, not the start commit"
   elif ! { on_main && cp "$(git -C "$MAIN_PATH" rev-parse --path-format=absolute --git-path index)" "$REM_INDEX" && on_main; }; then
     echo "STOP: main moved since Phase 0, or $MAIN_PATH is no longer on main"
   elif ! PATHS=$(GIT_INDEX_FILE="$REM_INDEX" git -C "$MAIN_PATH" diff --cached --name-only --no-renames "$START") || [ -z "$PATHS" ]; then
     echo "STOP: nothing is staged, or the staged paths could not be listed"
   elif BAD=$( { printf '%s\n' "$PATHS" | grep -vxF -f "$REM_LIST"; printf '%s\n' "$PATHS" | grep -vxE 'CLAUDE\.md|techdebt\.md|pming/\.workers|pming/\.activity-log|(docs|pming)/.+\.md'; not_rems; } | sort -u ); [ -n "$BAD" ]; then
     printf 'STOP: staged paths rem must not commit:\n%s\n' "$BAD"
   elif TREE=$(GIT_INDEX_FILE="$REM_INDEX" git -C "$MAIN_PATH" write-tree) &&
        COMMIT=$(git -C "$MAIN_PATH" commit-tree "$TREE" -p "$START" -F "$REM_MSG") &&
        git -C "$MAIN_PATH" update-ref -m "rem: nightly maintenance" refs/heads/main "$COMMIT" "$START"; then
     echo "COMMITTED $COMMIT"
   else
     echo "STOP: the commit failed; main may have moved"
   fi
   ```
   The block copies the index and commits the copy on top of the start commit, moving `main` only if it still points there. So it never overwrites or reverts a commit that landed during the run (it stops instead), and a path staged after the copy stays staged and out of the commit. In the copy, every path that differs from the start commit must be one this run staged (`$REM_LIST`) and one rem may change (Safety Guardrail 1): Markdown files under `docs/` or `pming/`, `pming/.workers`, `pming/.activity-log`, `CLAUDE.md` and `techdebt.md`. Each listed path must also hold what rem wrote: a doc under `docs/` holds the reviewed text Phase 3.3 applied from `$REM_TMP` (Phase 3 applies no doc outside `docs/`; Phase 2 writes `CLAUDE.md`), any other path its working copy, and a path whose file is gone holds nothing. Anything else stops the commit: app code in `android/`, the backend in `backend/`, `pming/worker.sh`, `AGENTS.md`, the legacy web app in `src/`, or someone else's staged work. Rem must never commit source changes. `--no-renames` lists both sides of a move. `PINNED` keeps the Phase 3 rule: if Phase 3 checked a commit other than the start commit, its doc updates and `Doc-Verified:` lines describe that other commit, so committing them would mark source changes as seen that no one checked. `commit-tree` runs no commit hooks; Kompara has none.

   Go on only if the block prints `COMMITTED <commit>`. On anything else, don't commit some other way and don't unstage anything (it may be someone else's work): display the report with the block's output and STOP.
3. Push: `git push origin main`
4. Display a brief summary (3-5 lines) of what was done.

If the commit or push fails, display the error and STOP. Do not retry.

## Safety Guardrails

These are non-negotiable constraints. Violating any of them is a critical failure.

1. **Never modify source code.** Rem only touches: the Markdown files in `docs/` and `pming/` (its own report files included), `pming/.workers` and `pming/.activity-log` (through `worker.sh`), `CLAUDE.md`, `techdebt.md`, and the run's scratch files under `/tmp` (Phase 3.2, and the path list, commit message and index copy in Phase 6.2). If you find yourself about to write anywhere else (the app in `android/`, the backend in `backend/`, `pming/worker.sh`, `AGENTS.md`, `design-system/`, `.github/`, `.claude/`, the legacy web app in `src/`) or to any `.kt`, `.kts` or `.ts` file — STOP.
2. **Never delete files.** Only move PM files between `pending/` and `done/`. Never `rm` a doc, a task file, or anything else.
3. **Doc update size cap.** If a doc-updater subagent returns content that changes >40% of the original doc's lines, skip that doc and flag for human review.
4. **CLAUDE.md change cap.** Never apply more than 15 changed lines to CLAUDE.md in a single run.
5. **Dry-run mode.** When invoked as `/rem dry`, make ZERO modifications to any file outside the run's scratch directory under `/tmp`, where the doc-updaters put their proposals. Only read and report.
6. **Idempotency.** Running rem twice in a row should produce no changes on the second run (all drift already fixed).
7. **No destructive git.** Never use `--force`, `--no-verify`, `--hard`, `clean -f`, or `branch -D`.
8. **No interactive prompts.** Rem runs autonomously. Never ask the user a question. If uncertain, skip the action and flag it in "Needs Human Attention".
