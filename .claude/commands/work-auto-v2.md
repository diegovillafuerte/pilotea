# Work-Auto v2: Agent-Per-Task Autonomous Loop

Fully automated cycle using **fresh Agent per task** to prevent context rot. The main loop stays lightweight (task selection, session tracking, reporting). Each task gets a clean context window via a spawned Agent with `isolation: "worktree"`.

## Arguments

From $ARGUMENTS:
- **Empty**: Run until all tasks are done or a stop condition is hit
- **Number (e.g., `5`)**: Complete at most N tasks, then stop and generate session report
- **`B-XXX B-YYY ...`**: Work on specific tasks in order

## Step 0: Resolve paths and session

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

### Session file

```bash
SESSION_PID=$$
CURRENT=$SESSION_PID
while [ "$CURRENT" != "1" ] && [ -n "$CURRENT" ] && [ "$CURRENT" != "0" ]; do
  CMD=$(ps -o comm= -p "$CURRENT" 2>/dev/null || echo "")
  if [[ "$CMD" == *claude* ]]; then SESSION_PID=$CURRENT; break; fi
  CURRENT=$(ps -o ppid= -p "$CURRENT" 2>/dev/null | tr -d ' ')
done
SESSION_FILE="/tmp/kompara-work-auto-v2-$SESSION_PID.json"
```

If the session file exists and is < 24 hours old, resume from it. If `stop_reason` is set, go to Step 4 (session report). If `current_task` is set, check disk: done → record success; pending+in_progress → record skip. Then continue the loop.

If the session file doesn't exist, parse $ARGUMENTS and create it:
```json
{
  "version": 2,
  "session_start": "<ISO 8601>",
  "budget": null,
  "specific_tasks": null,
  "completed_tasks": [],
  "skipped_tasks": [],
  "task_count": 0,
  "consecutive_skips": 0,
  "current_task": null,
  "stop_reason": null
}
```

## Step 1: Pre-flight

Run once on fresh start (skip on resume):

1. `git checkout main && git pull origin main`
2. `pnpm install`
3. `pnpm test` — must pass
4. `pnpm lint` — must be clean

If tests or lint fail: STOP, write minimal session report, exit.

## Step 2: The Loop

Each iteration picks a task, spawns an Agent to do the work, and records the result.

### 2a. Pick the next task

1. `$MAIN_PATH/pming/worker.sh cleanup`
2. Read session file for `specific_tasks`, `completed_tasks`, `skipped_tasks`
3. If specific tasks given, pick next unprocessed one
4. Otherwise, conflict-aware auto-selection:
   a. Read all `pming/tasks/pending/B-*.md` — extract id, priority, category, status, story, epic
   b. `$MAIN_PATH/pming/worker.sh list --json` — get active workers, exclude their tasks
   c. **Skip `ops`, `business` category tasks and `blocked` status tasks**
   d. **Skip tasks with `review: human` in frontmatter**
   e. **Skip tasks whose `depends_on` lists a task not yet in `pming/tasks/done/`**
   f. Prefer tasks in different stories/epics from active workers
   g. Priority ordering: critical > urgent > high > medium > low, then lowest B-XXX number
5. If no tasks remain: set `stop_reason` to `"all_done"`, go to Step 4

Read the task file content. Record `current_task` in session file.

### 2b. Spawn task Agent

Spawn a **single Agent** with `isolation: "worktree"` and `model: "opus"`. The Agent gets a fresh context window with no baggage from previous tasks.

**Agent prompt template** (fill in the values — the main loop constructs this):

````
You are working on task {TASK_ID} for the Kompara project autonomously.

## Task
{FULL TASK FILE CONTENT}

## Instructions

You are in an isolated worktree. The main worktree is at {MAIN_PATH}.
Worker coordination script: {MAIN_PATH}/pming/worker.sh

### Phase 1: Setup
1. Register as a worker:
   ```bash
   {MAIN_PATH}/pming/worker.sh claim {TASK_ID} task {TASK_ID} work/{TASK_ID}
   ```
   If `CONFLICT:` is returned, respond with: `{"success": false, "reason": "worker conflict"}`
2. Update the task status to `in_progress` in the task file
3. Run `pnpm install`

### Phase 2: Plan
1. Read the task description and acceptance criteria carefully
2. Read all relevant source files mentioned in the task
3. Produce a step-by-step implementation plan
4. If a Codex CLI is available (`which codex`), write the plan to a temp file and run a quick Codex plan review:
   ```bash
   codex exec - --sandbox read-only --output-last-message /tmp/plan-review-{TASK_ID}.txt -C "$(pwd)" < /tmp/plan-{TASK_ID}.txt
   ```
   Address any REVISE feedback. If Codex fails, proceed anyway.

### Phase 3: Implement
1. Follow the plan step by step
2. Run `pnpm test` and `pnpm lint` when done
3. If tests or lint fail, fix and retry (up to 3 attempts)
4. If still failing after 3 attempts, respond with: `{"success": false, "reason": "tests/lint failed after 3 attempts"}`

### Phase 4: Ship
1. Use the Skill tool to invoke `ship` — this runs adversarial Codex review, commits, merges to main, and pushes
2. `/ship` also handles: task completion (marks done, moves file), PM state updates, worktree cleanup, worker release
3. If `/ship` review rejects, fix the issues and re-invoke `/ship` (up to 3 total attempts)
4. If `/ship` passes and pushes successfully, the task is done

### Phase 5: Report result
After completion (success or failure), your final message MUST be a JSON object:

**On success:**
```json
{
  "success": true,
  "task_id": "{TASK_ID}",
  "title": "<task title>",
  "files_changed": ["file1.ts", "file2.ts"],
  "notes": "<brief summary of what was done>"
}
```

**On failure:**
```json
{
  "success": false,
  "task_id": "{TASK_ID}",
  "title": "<task title>",
  "reason": "<why it failed>",
  "reviewer": "<which reviewer rejected, or N/A>"
}
```

### Rules
- Never stop to ask for confirmation — this is fully autonomous
- If the task is ambiguous, add a `<!-- QUESTION: ... -->` comment and report failure
- If you want to deviate from spec, add a `<!-- DEVIATION: ... -->` comment and report failure
- Never skip adversarial review — time and tokens are not the bottleneck
- All worker.sh calls use the full path: {MAIN_PATH}/pming/worker.sh
````

**Agent call parameters:**
- `model: "opus"` — all agents must use Opus
- `isolation: "worktree"` — gets its own copy of the repo
- `timeout` is implicitly capped by the Agent tool, but aim for completion within 60 minutes

### 2c. Process Agent result

Parse the Agent's return message. Extract the JSON result.

**On success** (`"success": true`):
1. Verify the task file is in `pming/tasks/done/` (trust but verify)
2. Update session file: append to `completed_tasks`, increment `task_count`, reset `consecutive_skips` to 0, clear `current_task`
3. Check stop conditions:
   - `task_count >= budget` → set `stop_reason` to `"budget_reached"`
   - All `specific_tasks` processed → set `stop_reason` to `"specific_tasks_exhausted"`

**On failure** (`"success": false`):
1. Check if worktree cleanup is needed: if the Agent's worktree still exists and has uncommitted changes, the `isolation: "worktree"` cleanup handles this automatically
2. Ensure the task status is reset to `todo` if still `in_progress`:
   ```bash
   # Check and reset if needed
   grep -q 'status: in_progress' "$MAIN_PATH/pming/tasks/pending/{TASK_ID}.md" 2>/dev/null && \
     sed -i '' 's/status: in_progress/status: todo/' "$MAIN_PATH/pming/tasks/pending/{TASK_ID}.md"
   ```
3. Release worker if still registered: `$MAIN_PATH/pming/worker.sh release`
4. Update session file: append to `skipped_tasks`, increment `consecutive_skips`, clear `current_task`
5. Check stop conditions:
   - `consecutive_skips >= 3` → set `stop_reason` to `"consecutive_skips"`

**On unparseable result** (Agent returned something that isn't valid JSON):
- Treat as failure with reason "agent returned unparseable result"
- Check disk for task status to determine if it actually shipped

### 2d. Continue or stop

If `stop_reason` is set → go to Step 4 (session report).
Otherwise → go to Step 2a (pick next task).

**No `/clear` needed.** The main loop stays lightweight because all implementation context lives inside the Agent, which is already gone.

## Step 3: (removed — no clear/re-invoke needed)

## Step 4: Session report

When the loop stops (any reason), generate `session-report.md` in the project root.

1. Read `$SESSION_FILE`
2. Map `stop_reason` to human-readable text:
   - `"budget_reached"` → "Budget of N tasks reached."
   - `"all_done"` → "All pending tasks completed."
   - `"consecutive_skips"` → "3 consecutive tasks skipped — systemic issue suspected."
   - `"specific_tasks_exhausted"` → "All specified tasks processed."

3. Gather data:
   - `pnpm test` and `pnpm lint` on main — pass/fail
   - `git log` commits since `session_start`
   - Remaining pending tasks by priority

4. Analyze patterns:
   - Multiple skips for same reason → identify systemic issue
   - Same reviewer rejecting → note which and what

5. Write `session-report.md`:
```markdown
# Session Report — YYYY-MM-DD

## Summary
Completed N tasks. Skipped M. [Stop reason].

## Completed
For each task: ID, title, files changed, notes

## Skipped
For each: ID, title, reason, reviewer (if applicable), recommended action

## Patterns
[Only if patterns found]

## State of main
Tests: PASSING/FAILING (count)
Lint: CLEAN/N violations
Last commit: hash "message"

## Next priorities
Top 3-5 remaining tasks by priority
```

6. Log to telemetry:
```bash
TELEMETRY_FILE="$MAIN_PATH/pming/.telemetry.jsonl"
if [ -f "$TELEMETRY_FILE" ]; then
  COMPLETED=$(jq '.completed_tasks | length' "$SESSION_FILE")
  SKIPPED=$(jq '.skipped_tasks | length' "$SESSION_FILE")
  START=$(jq -r '.session_start' "$SESSION_FILE")
  DURATION_MIN=$(( ($(date +%s) - $(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$START" +%s 2>/dev/null || echo $(date +%s))) / 60 ))
  echo "{\"ts\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"event\":\"work_auto_v2_session\",\"tasks_completed\":$COMPLETED,\"tasks_skipped\":$SKIPPED,\"duration_min\":$DURATION_MIN}" >> "$TELEMETRY_FILE"
fi
```

7. Commit and push:
```bash
git add session-report.md pming/
git commit -m "Update session report — N tasks completed"
git push origin main
```

## Step 5: Post-session cleanup

1. Clean orphaned worktrees:
   ```bash
   git worktree list
   ```
   For merged branches: `git worktree remove <path> && git branch -d <branch>`

2. Release own worker: `$MAIN_PATH/pming/worker.sh release`

3. Delete session file: `rm -f "$SESSION_FILE"`

4. Commit PM state if dirty:
   ```bash
   git add pming/ && git diff --cached --quiet || git commit -m "Post-session cleanup" && git push origin main
   ```

## Rules

- Never stop to ask for confirmation — this is fully autonomous
- **Each task runs in a fresh Agent with `isolation: "worktree"`** — no context rot between tasks
- **The main loop never reads source code or implements anything** — it only does task selection, session tracking, and reporting
- **All Agents must use `model: "opus"`** — never downgrade to Sonnet/Haiku
- **Session state persisted to `/tmp/kompara-work-auto-v2-$SESSION_PID.json`** — all writes use atomic rename
- Skip `ops`/`business` category tasks (non-code) — leave as `todo`
- Skip tasks with `review: human` in frontmatter
- Skip tasks whose `depends_on` is not yet done
- Budget: `task_count >= budget` → stop
- 3 consecutive skips → stop (systemic issue)
- Worker coordination via `pming/worker.sh` — never read/write `.workers` directly
- Conflict-aware selection: always check what other workers are doing
- Agent timeout: if an agent hasn't returned after ~60 minutes, treat as failure
