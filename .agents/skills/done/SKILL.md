---
name: done
description: Mark the current or specified task as done. If working through a story or epic group, automatically advances to the next task. Use /done for current task or /done B-XXX for a specific one.
argument-hint: [B-XXX]
---

# Complete Task

Mark a task as done and handle group progression.

## Step 0: Resolve main worktree path

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

## Step 1: Determine which task

From $ARGUMENTS:
- **`B-XXX`**: mark that specific task as done
- **Empty**: find own worker entry via `$MAIN_PATH/pming/worker.sh info`. Parse the JSON output to get the `task` field. If no matching worker entry (exit code 1), ask which task to complete.

## Step 2: Mark done

1. Read the task file (check both `pming/tasks/pending/B-XXX.md` and `pming/tasks/done/B-XXX.md`)
2. Update `status: done` in the YAML frontmatter
3. Move the file to `pming/tasks/done/` if it's in `pming/tasks/pending/`
4. Write the file back

## Step 3: Update parent status

After marking the task done:
- Read all tasks in the same story → if all are `done`, update story status to `done` and move to `pming/stories/done/`
- Read all stories in the same epic → if all are `done`, update epic status to `done` and move to `pming/epics/done/`

## Step 4: Handle group progression

Check for own worker entry: `$MAIN_PATH/pming/worker.sh info`

### If working through a group (mode is `story` or `epic`):

1. Find remaining `todo` tasks in the group (same story or epic, depending on mode)
2. Get active workers: `$MAIN_PATH/pming/worker.sh list --json` — skip tasks claimed by other workers
3. **Skip `ops` and `business` category tasks** — these require human action, not code
4. **If more unclaimed code tasks remain:**
   - Show: `Completed B-XXX: "[title]"`
   - Show the next task preview: `Next in [group name]: B-YYY "[title]"` with a brief description
   - Ask: **"Continue with B-YYY?"**
   - If user confirms: update own worker entry via `$MAIN_PATH/pming/worker.sh update-task B-YYY work/B-YYY`, set B-YYY to `in_progress`, and begin working on it (same behavior as `/work B-YYY`)
4. **If no more unclaimed tasks in group:**
   - Show: `Completed B-XXX: "[title]"`
   - Show: `All tasks in [group name] are complete!` (or "All remaining tasks are being handled by other workers")
   - Release own worker entry: `$MAIN_PATH/pming/worker.sh release`
   - Show what the highest-priority remaining task would be globally (respecting active workers)

### If NOT working through a group (mode is `task`):

1. Show: `Completed B-XXX: "[title]"`
2. Release own worker entry: `$MAIN_PATH/pming/worker.sh release`
3. Briefly mention the next highest-priority task available (respecting active workers)

## Step 5: For code tasks — verify before marking done

Before marking a code task as done:
- Run `pnpm test` to verify tests pass
- Run `pnpm lint` to verify lint is clean
- If tests or lint fail, fix the issues first instead of marking done

For non-code tasks (ops, business), just confirm completion with the user.

## Guidelines
- Keep momentum — the goal is smooth flow through the task list
- When advancing in a group, load just enough context for the next task to keep things moving
- If the user says "done" conversationally (not as a command), use judgment — they might mean the task is complete
- Worker coordination is handled by `pming/worker.sh` — never read/write `pming/.workers` directly
