# Work-Auto: Autonomous Task Loop

Fully automated cycle: pre-flight → pick task → worktree → plan → implement → review → ship → **clear context** → repeat → session report → cleanup.

## Arguments

From $ARGUMENTS:
- **Empty**: Run until all tasks are done or a stop condition is hit
- **Number (e.g., `5`)**: Complete at most N tasks, then stop and generate session report
- **`B-XXX B-YYY ...`**: Work on specific tasks in order

## Step 0: Resolve main worktree path

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

Worker coordination is handled atomically by `$MAIN_PATH/pming/worker.sh`.

## Step 0a: Determine session file path

Session state is persisted to disk so it survives `/clear` between task iterations. This prevents context rot — each task starts with a fresh context window.

```bash
# Same PID-walk as worker.sh find_session_pid()
SESSION_PID=$$
CURRENT=$SESSION_PID
while [ "$CURRENT" != "1" ] && [ -n "$CURRENT" ] && [ "$CURRENT" != "0" ]; do
  CMD=$(ps -o comm= -p "$CURRENT" 2>/dev/null || echo "")
  if [[ "$CMD" == *claude* ]]; then SESSION_PID=$CURRENT; break; fi
  CURRENT=$(ps -o ppid= -p "$CURRENT" 2>/dev/null | tr -d ' ')
done
SESSION_FILE="/tmp/pilotea-work-auto-$SESSION_PID.json"
echo "$SESSION_FILE"
```

## Step 0b: Resume detection

Check if a session file already exists (meaning we're resuming after a `/clear`).

**If `$SESSION_FILE` exists — this is a RESUME:**

1. Read the session file: `cat $SESSION_FILE`
2. Parse all fields. If the JSON is malformed, warn "Corrupt session file — starting fresh", delete the file, and treat as a fresh start.
3. **Staleness check:** If `session_start` is more than 24 hours ago, warn "Found stale work-auto session from <date>. Starting fresh.", delete the file, and treat as a fresh start.
4. **If `stop_reason` is not null:** A stop condition was hit before the last `/clear`. Proceed directly to Step 8 (session report).
5. **If `current_task` is not null:** A task was in-flight when the session was interrupted.
   - Check the task's status on disk (look in both `pming/tasks/done/` and `pming/tasks/pending/`):
   - If the task file is in `done/`: it shipped successfully. Add to `completed_tasks` if not already there, increment `task_count`, reset `consecutive_skips` to 0. Clear `current_task` in session file.
   - If the task file is in `pending/` with `status: in_progress`: it was not shipped. Reset status to `todo`. Release worker entry: `$MAIN_PATH/pming/worker.sh release`. Add to `skipped_tasks` with reason "interrupted mid-task". Increment `consecutive_skips`. Clear `current_task` in session file.
6. **Skip pre-flight.** Go directly to Step 1 (The Loop — pick next task).

**If `$SESSION_FILE` does not exist — this is a FRESH START:**

1. Parse `$ARGUMENTS` (empty, number, or task IDs).
2. Run Step 0c (Pre-flight).
3. After pre-flight passes, create the session file:
   ```bash
   cat > "$SESSION_FILE" << EOF
   {
     "version": 1,
     "session_start": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
     "budget": <N or null from $ARGUMENTS>,
     "specific_tasks": <["B-XXX","B-YYY"] or null>,
     "completed_tasks": [],
     "skipped_tasks": [],
     "task_count": 0,
     "consecutive_skips": 0,
     "current_task": null,
     "stop_reason": null
   }
   EOF
   ```
4. Proceed to Step 1 (The Loop).

## Step 0c: Pre-flight

Before entering the task loop, verify a clean baseline:

1. Ensure on main branch: `git checkout main`
2. Pull latest: `git pull origin main`
3. Install deps: `pnpm install`
4. Run tests: `pnpm test`
5. Run lint: `pnpm lint`

If tests or lint fail on clean main: **STOP immediately.**
Report: "Pre-flight failed: main is broken before work started. [error output]"
Write a minimal session report (see Step 8), clean up the session file, and exit.

## The Loop

Run this cycle. Each iteration completes one task end-to-end, then clears context before the next.

### 1. Pick the next task

1. Clean stale entries: `$MAIN_PATH/pming/worker.sh cleanup`
2. Read the session file to get `specific_tasks`, `completed_tasks`, and `skipped_tasks` (to know which tasks are already processed).
3. If specific tasks were given (`specific_tasks` is not null), pick the next unprocessed one from that list.
4. Otherwise, check for existing session: `$MAIN_PATH/pming/worker.sh info`
   - If exit code 0 (entry found): parse JSON to get task. If task is `in_progress`, use it.
   - If exit code 1 (no entry): use **conflict-aware auto-selection**:
     a. Read all pending tasks from `pming/tasks/pending/B-*.md`
     b. Get active workers: `$MAIN_PATH/pming/worker.sh list --json` — exclude their tasks
     c. Prefer tasks in different stories/epics from active workers
     d. Apply priority ordering: urgent > high > medium > low, then lowest B-XXX number
5. If no tasks remain, set `stop_reason` to `"all_done"` in session file and proceed to Step 8 (session report).

Read the task file. Update status to `in_progress`.

**Record the in-flight task** in the session file (crash recovery breadcrumb):
```bash
jq --arg t "B-XXX" '.current_task = $t' "$SESSION_FILE" > "${SESSION_FILE}.tmp" \
  && mv "${SESSION_FILE}.tmp" "$SESSION_FILE"
```

### 1b. Register worker

```bash
$MAIN_PATH/pming/worker.sh claim B-XXX task B-XXX work/B-XXX
```

If `CONFLICT:` is returned, handle as a skip (see "When a task is skipped" in Step 7).

### 2. Spin up worktree

1. Branch name: `work/<task-id>`
2. `git worktree add .worktrees/<task-id> -b work/<task-id>`
3. `cd` into `.worktrees/<task-id>`

### 3. Plan + Codex plan review

1. Read the task description, acceptance criteria, and relevant source files
2. Produce a step-by-step implementation plan inline (do NOT use EnterPlanMode/ExitPlanMode — those require interactive approval and block autonomous execution)
3. Run a quick Codex plan review (same as `/work` Step 4b): write the plan to a temp file, run `codex exec - --sandbox read-only`, read feedback. If Codex says REVISE, address the concerns before implementing. If Codex fails, proceed anyway.
4. Proceed to implementation

### 4. Implement + Codex implementation check

1. Follow the plan. Run `pnpm test` and `pnpm lint` when done. Fix any failures.
2. Run a Codex implementation check (same as `/work` Step 5b): native review of working tree changes via companion script or `codex exec -`. Fix any issues found before proceeding to /ship.

### 5. Ship review loop

This is the final quality gate. **The review is mandatory.** Do NOT skip it to save time or tokens — time and tokens are not the bottleneck; shipping broken code is. The only acceptable reason to skip is if all Codex reviewers fail, in which case warn the user.

The `/ship` skill now uses a tiered review: a fast native pre-screen runs first, and specialized reviewers only run for large or critical changes. This means most tasks that already passed the implementation check in Step 4 will hit the fast lane in `/ship`.

Loop until the code passes review:

```
repeat:
  a. Run `pnpm lint` and `pnpm test` — fix failures before reviewing
  b. Run /ship (which runs native pre-screen, then conditionally specialized Codex reviewers)
  c. If review passes → break, proceed to step 6
  d. If any reviewer returns REJECT:
     - Read ALL critical findings
     - Fix every critical issue in the worktree
     - Go back to (a)
  e. If Codex reviewers are UNAVAILABLE or ALL FAIL:
     - Retry once
     - If still fails: handle as a skip (see "When a task is skipped" in Step 7)
       with reason "Codex review unavailable"
```

**Important:** Each iteration fixes issues and re-runs the full /ship flow. Do not skip the review on subsequent passes — fixes may introduce new issues.

**Never shortcut this step.** Even for "simple" tasks, the review catches boundary violations, stale documentation, and integration issues that tests alone miss.

### 6. Ship

Once /ship passes (reviewer approved, committed, merged to main, pushed), the task is shipped.

### 7. Mark done and clear context

`/ship` (Step 6) already handles task completion, PM state updates, worktree cleanup, and push via its own Step 7. Do NOT duplicate that work here.

**When a task is successfully shipped:**

1. Update the session file — append to `completed_tasks`, increment `task_count`, reset `consecutive_skips` to 0, clear `current_task`:
   ```bash
   jq \
     --arg id "B-XXX" \
     --arg title "task title" \
     --argjson files '["file1.ts","file2.ts"]' \
     --arg notes "any notes" \
     '.completed_tasks += [{"task_id":$id,"title":$title,"files_changed":$files,"notes":$notes}]
      | .task_count += 1
      | .consecutive_skips = 0
      | .current_task = null' \
     "$SESSION_FILE" > "${SESSION_FILE}.tmp" && mv "${SESSION_FILE}.tmp" "$SESSION_FILE"
   ```
2. Check stop conditions:
   - If `task_count >= budget` (and budget is not null): set `stop_reason` to `"budget_reached"`.
   - If `specific_tasks` is set and all are now in `completed_tasks` or `skipped_tasks`: set `stop_reason` to `"specific_tasks_exhausted"`.
3. **If a stop condition was hit:** Proceed to Step 8 (session report). No `/clear` needed.
4. **If continuing:** Tell the user: **"Task B-XXX shipped. Clearing context for next task."** Then:
   a. Run `/clear`
   b. After clear, re-invoke `/work-auto` with no arguments.

   **STOP here.** Do not proceed past `/clear`. The re-invocation will hit the resume path in Step 0b, read the session file, and continue the loop with a fresh context window.

**When a task is skipped** (ops/business category, 3 fix failures, review unavailable, conflict):

1. If the task was started (worktree exists): reset task status to `todo`, release worker: `$MAIN_PATH/pming/worker.sh release --worktree`.
2. Update the session file — append to `skipped_tasks`, increment `consecutive_skips`, clear `current_task`:
   ```bash
   jq \
     --arg id "B-XXX" \
     --arg title "task title" \
     --arg reason "reason for skip" \
     --arg reviewer "reviewer that rejected, or N/A" \
     '.skipped_tasks += [{"task_id":$id,"title":$title,"reason":$reason,"reviewer":$reviewer}]
      | .consecutive_skips += 1
      | .current_task = null' \
     "$SESSION_FILE" > "${SESSION_FILE}.tmp" && mv "${SESSION_FILE}.tmp" "$SESSION_FILE"
   ```
3. Check stop conditions:
   - If `consecutive_skips >= 3`: set `stop_reason` to `"consecutive_skips"`.
4. **If a stop condition was hit:** Proceed to Step 8.
5. **If continuing:** `/clear` → re-invoke `/work-auto` with no arguments. **STOP here.**

### 8. Session report

When the loop stops (any reason), generate `session-report.md` in the project root.

1. Read `$SESSION_FILE` to get all tracking data:
   ```bash
   cat "$SESSION_FILE"
   ```
   Extract `completed_tasks`, `skipped_tasks`, `session_start`, `stop_reason`, `task_count`.

2. Map `stop_reason` to human-readable text:
   - `"budget_reached"` → "Budget of N tasks reached."
   - `"all_done"` → "All pending tasks completed."
   - `"consecutive_skips"` → "3 consecutive tasks skipped — systemic issue suspected."
   - `"specific_tasks_exhausted"` → "All specified tasks processed."

3. Gather additional data:
   - Current state of main: run `pnpm test` and `pnpm lint`, report pass/fail
   - Git log of all commits made this session (since `session_start`)
   - Any remaining pending tasks by priority

4. **Analyze patterns** in the session data before writing the report:
   - If multiple tasks were skipped for the same reason, identify the pattern (e.g., "3 skips due to architecture boundary violations → may need a refactor before these tasks are feasible")
   - If multiple rejections came from the same reviewer, note which reviewer and the common finding
   - If iterations-to-pass was consistently >1, note what kept failing

5. Write `session-report.md` with this structure:

```markdown
# Session Report — YYYY-MM-DD

## Summary
Completed N tasks. Skipped M. [Stop reason].

## Completed
For each task: ID, title, files changed, notes

## Skipped
For each: ID, title, reason, reviewer (if applicable), recommended action

## Patterns
[Only if patterns were found in step 4]
- "Architecture reviewer rejected 3/5 tasks for boundary violations — consider adding lint rule for [pattern]"
- "2 tasks skipped because [reason] — underlying issue: [analysis]"

## State of main
Tests: PASSING/FAILING (count)
Lint: CLEAN/N violations
Last commit: hash "message"

## Next priorities
Top 3-5 remaining tasks by priority
```

6. Log session to telemetry (if `pming/.telemetry.jsonl` exists):
   ```bash
   TELEMETRY_FILE="$MAIN_PATH/pming/.telemetry.jsonl"
   if [ -f "$TELEMETRY_FILE" ]; then
     COMPLETED=$(jq '.completed_tasks | length' "$SESSION_FILE")
     SKIPPED=$(jq '.skipped_tasks | length' "$SESSION_FILE")
     START=$(jq -r '.session_start' "$SESSION_FILE")
     DURATION_MIN=$(( ($(date +%s) - $(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$START" +%s 2>/dev/null || echo $(date +%s))) / 60 ))
     echo "{\"ts\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"event\":\"work_auto_session\",\"tasks_completed\":$COMPLETED,\"tasks_skipped\":$SKIPPED,\"duration_min\":$DURATION_MIN}" >> "$TELEMETRY_FILE"
   fi
   ```

7. Commit and push the session report:
```bash
git add session-report.md pming/
git commit -m "Update session report — N tasks completed"
git push origin main
```

### 9. Post-session cleanup

1. Clean orphaned worktrees:
   - List all worktrees: `git worktree list`
   - For each worktree that is NOT the main repo:
     - Check if its branch is fully merged to main: `git branch --merged main | grep <branch>`
     - If merged: `git worktree remove <path>` and `git branch -d <branch>`
     - If not merged but task is done: remove worktree, keep branch (human can review)

2. Release own worker entry: `$MAIN_PATH/pming/worker.sh release`

3. Delete the session file:
```bash
rm -f "$SESSION_FILE"
```

4. Commit any PM state changes:
```bash
git add pming/
git commit -m "Post-session cleanup"
git push origin main
```

## Rules

- Never stop to ask for confirmation — this is fully autonomous
- **Between every task iteration, `/clear` and re-invoke to prevent context rot** — each task gets a fresh context window
- **Session state is persisted to `/tmp/pilotea-work-auto-$SESSION_PID.json`** — never keep tracking data in conversation memory
- **All session file writes use atomic rename** (`jq > .tmp && mv .tmp file`) to prevent corruption
- If tests or lint fail after 3 consecutive fix attempts, skip the task and move to the next one
- If the adversarial reviewer rejects 3 times in a row, skip the task and move to the next one
- If a task is `ops` or `business` category (non-code), skip it (leave as `todo`) and continue
- If a task budget was set and `task_count >= budget`, proceed to Step 8 (session report)
- If working on specific tasks and all are processed, proceed to Step 8 (session report)
- Keep going until all tasks are done, budget is reached, or 3 consecutive tasks are skipped
- If 3 consecutive tasks are skipped, proceed to Step 8 — something systemic may be wrong
- Worker coordination is handled by `pming/worker.sh` — never read/write `pming/.workers` directly
- **All `worker.sh` calls use `$MAIN_PATH/pming/worker.sh`** — works from any worktree
- **Conflict-aware selection**: always check what other workers are doing before picking a task
