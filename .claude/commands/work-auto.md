# Work-Auto: Autonomous Task Loop

Fully automated cycle: pre-flight → pick task → worktree → plan → implement → review → ship → **clear context** → repeat → session report → cleanup.

## Arguments

From $ARGUMENTS:
- **Empty**: Run until all tasks are done or a stop condition is hit
- **Number (e.g., `5`)**: Complete at most N tasks, then stop and generate session report
- **`B-XXX B-YYY ...`**: Work on specific tasks in order

## Step 0: Main checkout path

`$MAIN_PATH` is the main checkout, the first entry `git worktree list` prints. Worker coordination is handled atomically by `$MAIN_PATH/pming/worker.sh`. Claude Code's Bash tool keeps no shell variables between commands, so each block below that uses `$MAIN_PATH` sets it on its first line, and each one that uses `$SESSION_FILE` sets that too (Step 0a). Run each block as one command.

This session's checkout may be on any branch: it may even be the main checkout, with another session's uncommitted work. So this run never switches, pulls or commits in it, and never reads or edits the task files there:
- **Read task files from a freshly fetched `origin/main`.** This lists every pending task's fields, one `origin/main:<file>:<field>: <value>` line each, from any folder of the repository:
  ```bash
  git fetch origin main && git grep --full-name -E '^(id|title|status|priority|category|story|epic|depends_on|review):' origin/main -- ':(top)pming/tasks/pending/'
  ```
  After that fetch, `git show origin/main:pming/tasks/pending/B-XXX.md` prints one task file, and `git ls-tree --full-tree --name-only origin/main pming/tasks/done/` lists the finished ones.
- **Never write a task file in a checkout.** The claim (step 1b) marks a task as taken. `/ship` marks it done, and the session report's commit moves a task this run shipped to `done/` on `main` if it isn't there yet (Step 8).
- **Check and commit to `main` in a temporary worktree** at `origin/main` (Step 0c and Step 8).

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
SESSION_FILE="/tmp/kompara-work-auto-$SESSION_PID.json"
echo "$SESSION_FILE"
```

It prints the session file's path. Each block below that uses `$SESSION_FILE` sets it to that path on its first line, `SESSION_FILE=<the path Step 0a printed>`.

## Step 0b: Resume detection

Check if a session file already exists (meaning we're resuming after a `/clear`).

**If the session file exists — this is a RESUME:**

1. Read the session file, the path Step 0a printed
2. Parse all fields. If the JSON is malformed, warn "Corrupt session file — starting fresh", delete the file, and treat as a fresh start.
3. **Staleness check:** If `session_start` is more than 24 hours ago, warn "Found stale work-auto session from <date>. Starting fresh.", delete the file, and treat as a fresh start.
4. **If `stop_reason` is not null:** A stop condition was hit before the last `/clear`. Proceed directly to Step 8 (session report).
5. **If `current_task` is not null:** A task was in-flight when the session was interrupted.
   - Check whether it shipped. `/ship` moves the task file to `done/` once it has pushed: on `origin/main`, or, while it leaves that move uncommitted (TD-047 item 2), in the task worktree. This prints `SHIPPED` or `NOT SHIPPED`:
     ```bash
     MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
     if ! git fetch origin main; then
       echo "STOP: could not fetch origin main"; false
     elif git cat-file -e "origin/main:pming/tasks/done/<task-id>.md" 2>/dev/null || [ -f "$MAIN_PATH/.worktrees/<task-id>/pming/tasks/done/<task-id>.md" ]; then
       echo "SHIPPED"
     else
       echo "NOT SHIPPED"
     fi
     ```
     `STOP:` means it can't tell: stop here and tell the user; the next `/work-auto` resumes from the same point.
   - Either way, release this run's claim on the task if it is still live (`/ship` releases it after shipping, but the interruption may have come first), by its UUID, as every release in this run does (step 1b explains why). A repeated release is a no-op.
     ```bash
     MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
     SESSION_FILE=<the path Step 0a printed>
     P=${SESSION_FILE##*-}; P=${P%.json}
     U=
     for C in "$(jq -r '.current_claim // empty' "$SESSION_FILE" 2>/dev/null)" "$(cat "/tmp/kompara-worker-$P.uuid" 2>/dev/null)"; do
       if [ -n "$C" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$C" | jq -e --arg t "<task-id>" --argjson p "$P" '.task == $t and .pid == $p' > /dev/null; then U=$C; break; fi
     done
     if [ -n "$U" ]; then "$MAIN_PATH/pming/worker.sh" release --uuid "$U"; else echo "No claim of this session's on <task-id> to release"; fi
     ```
   - If `SHIPPED`: Add to `completed_tasks` if not already there, increment `task_count`, reset `consecutive_skips` to 0. Clear `current_task` and `current_claim` in session file.
   - If `NOT SHIPPED`: Add to `skipped_tasks` with reason "interrupted mid-task". Increment `consecutive_skips`. Clear `current_task` and `current_claim` in session file. Its task worktree, if step 2 made one, stays: Step 9 lists it as `LEFT`, for `/housekeeper`.
6. **Skip pre-flight.** Go directly to Step 1 (The Loop — pick next task).

**If the session file does not exist — this is a FRESH START:**

1. Parse `$ARGUMENTS` (empty, number, or task IDs).
2. Run Step 0c (Pre-flight).
3. After pre-flight passes, create the session file:
   ```bash
   SESSION_FILE=<the path Step 0a printed>
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
     "stop_reason": null,
     "current_claim": null,
     "created_worktrees": []
   }
   EOF
   ```
   `current_claim` is the UUID of this run's live claim (step 1b). `created_worktrees` lists the task worktrees step 2 creates: Step 9 acts only on those.
4. Proceed to Step 1 (The Loop).

## Step 0c: Pre-flight

Before entering the task loop, verify a clean baseline: a freshly fetched `origin/main`, checked out in a temporary worktree. Never `git checkout main` or `git pull` in this session's checkout (Step 0): with another session's uncommitted work there, that fails or carries the work along.

1. Fetch `origin/main` and check it out, detached, in a new temporary worktree, the **baseline worktree**. Its folder is named after this session's file, so the blocks that use it can tell it from another run's. Run this as one command:
   ```bash
   SESSION_FILE=<the path Step 0a printed>
   git fetch origin main || { echo "STOP: could not fetch origin main"; false; } &&
     BASE=$(mktemp -d "${TMPDIR:-/tmp}/$(basename "$SESSION_FILE" .json).baseline.XXXXXX") && BASE=$(cd "$BASE" && pwd -P) &&
     git worktree add --detach "$BASE" origin/main && echo "BASELINE: $BASE"
   ```
   It prints `BASELINE: <path>`; `<baseline>` below stands for that path.
2. Install backend deps there: `cd "<baseline>/backend" && pnpm install --frozen-lockfile`
3. Run the AGENTS.md **Verify** rows for `android/` and `backend/` there. Start each command with `cd "<baseline>/android" &&` or `cd "<baseline>/backend" &&`, since a `cd` doesn't carry over to the next command. The new worktree has no `android/local.properties`, so Gradle finds the SDK through `ANDROID_HOME` (AGENTS.md, "Java for Gradle").
4. Remove the baseline worktree, whether the checks passed or not. It is this run's own detached checkout of `origin/main`, so `--force` loses nothing. The block first makes sure the path is one of this run's baseline worktrees: in `$TMPDIR`, named after this session's file, the root of a worktree, and detached:
   ```bash
   SESSION_FILE=<the path Step 0a printed>
   BASE="<baseline>"
   NAME=$(basename "$SESSION_FILE" .json); TMPR=$(cd "${TMPDIR:-/tmp}" && pwd -P)
   case "$BASE" in "$TMPR/$NAME.baseline."*) ;; *) BASE= ;; esac
   if [ -z "$BASE" ] || [ "$(git -C "$BASE" rev-parse --show-toplevel 2>/dev/null)" != "$BASE" ] || git -C "$BASE" symbolic-ref -q HEAD >/dev/null; then
     echo "STOP: not this run's baseline worktree"; false
   else
     git worktree remove --force "$BASE"
   fi
   ```

If the fetch or any check fails: **STOP immediately.** Remove the baseline worktree if it exists (item 4), and exit with this report as the final message: "Pre-flight failed: main is broken before work started. [error output]", or that `origin/main` could not be fetched. Nothing was claimed, created or committed, and there is no session file yet.

## The Loop

Run this cycle. Each iteration completes one task end-to-end, then clears context before the next.

### 1. Pick the next task

1. Clean stale entries:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   "$MAIN_PATH/pming/worker.sh" cleanup
   ```
2. Read the session file to get `specific_tasks`, `completed_tasks`, and `skipped_tasks` (to know which tasks are already processed).
3. If specific tasks were given (`specific_tasks` is not null), pick the next unprocessed one from that list.
4. Otherwise, check for a claim this run made and hasn't released, the session file's `current_claim` (step 1b). A plain `info` would resolve the UUID from the cwd's worktree first, whose `.worker-uuid` may be another session's:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0a printed>
   U=$(jq -r '.current_claim // empty' "$SESSION_FILE")
   [ -n "$U" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$U"
   ```
   - If exit code 0 (entry found): parse JSON to get task. If it isn't in `completed_tasks` or `skipped_tasks`, use it.
   - If exit code 1 (no entry): use **conflict-aware auto-selection**:
     a. Read all pending tasks from `origin/main` (the Step 0 listing), leaving out this run's `completed_tasks` and `skipped_tasks`: a task this run shipped stays in `pending/` there until Step 8 publishes its move to `done/`
     b. Get active workers, and exclude their tasks:
        ```bash
        MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
        "$MAIN_PATH/pming/worker.sh" list --json
        ```
     c. Prefer tasks in different stories/epics from active workers
     d. Apply priority ordering: urgent > high > medium > low, then lowest B-XXX number
5. If no tasks remain, set `stop_reason` to `"all_done"` in session file and proceed to Step 8 (session report).

Read the task file from `origin/main` (Step 0). Leave its status alone: the claim marks the task as taken (Step 0).

**Record the in-flight task** in the session file (crash recovery breadcrumb):
```bash
SESSION_FILE=<the path Step 0a printed>
jq --arg t "B-XXX" '.current_task = $t' "$SESSION_FILE" > "${SESSION_FILE}.tmp" \
  && mv "${SESSION_FILE}.tmp" "$SESSION_FILE"
```

### 1b. Register worker

The block prints `OK <uuid>` and records the UUID in the session file's `current_claim`. Every release in this run names the claim by UUID (`--uuid`): a plain `release`, or an empty `--uuid`, resolves the UUID from the cwd's worktree first, and once this run has left a worktree behind, another session's claim may have written its `.worker-uuid` there. The release blocks try `current_claim`, then this session's own claim marker (`/tmp/kompara-worker-<pid>.uuid`, the pid from the session file's name, which covers an interruption between the claim and its record), and use one only when `worker.sh info` shows it is this session's claim on the task. If neither is, they release nothing.
```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
SESSION_FILE=<the path Step 0a printed>
OUT=$("$MAIN_PATH/pming/worker.sh" claim B-XXX task B-XXX work/B-XXX) && echo "$OUT" &&
  jq --arg u "${OUT#OK }" '.current_claim = $u' "$SESSION_FILE" > "${SESSION_FILE}.tmp" && mv "${SESSION_FILE}.tmp" "$SESSION_FILE"
```

If `CONFLICT:` is returned, handle as a skip (see "When a task is skipped" in Step 7).

### 2. Spin up worktree

1. Branch name: `work/<task-id>`
2. Create the worktree and move into it. Run this as one command, since shell variables don't carry over to the next one. It goes in `$MAIN_PATH/.worktrees/`, where `worker.sh` looks for it, even when this session runs in another worktree. The branch starts from a freshly fetched `origin/main`, the `main` that `/ship` merges it into, never from this checkout's HEAD, whose commits `/ship` would merge too. A `work/<task-id>` branch an earlier session left with no commits of its own is reset to `origin/main`. Once it has created the worktree, the block records it, with its branch, in the session file's `created_worktrees`, the only worktrees Step 9 acts on. If the record can't be written, the block fails like any other step 2 failure, and the worktree stays for a human:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0a printed>
   WT="$MAIN_PATH/.worktrees/<task-id>"
   BR="work/<task-id>"
   if git worktree list --porcelain | grep -Fxq "worktree $WT"; then
     echo "LEFTOVER: an earlier session left a worktree at $WT"; false
   elif ! git fetch origin main; then
     echo "STOP: could not fetch origin main"; false
   elif ! git show-ref --verify --quiet "refs/heads/$BR"; then
     git worktree add --no-track -b "$BR" "$WT" origin/main
   elif [ -z "$(git rev-list -1 "origin/main..$BR")" ]; then
     git worktree add --no-track -B "$BR" "$WT" origin/main
   else
     echo "LEFTOVER: $BR has commits origin/main doesn't:"; git log --oneline "origin/main..$BR"; false
   fi &&
     jq --arg t "<task-id>" --arg p "$WT" --arg b "$BR" \
       '.created_worktrees += [{"task_id":$t,"path":$p,"branch":$b}]' \
       "$SESSION_FILE" > "${SESSION_FILE}.tmp" && mv "${SESSION_FILE}.tmp" "$SESSION_FILE" && cd "$WT"
   ```
   If it fails, handle the task as a skip (Step 7) whose reason is `step 2 failed: ` followed by the line that says why: the `LEFTOVER:` line, the `STOP:` line (the fetch failed) or git's `fatal:` line. Step 9 finds these skips by that prefix, even after a `/clear`. `LEFTOVER:` and `STOP:` create nothing. A `fatal:` line saying the folder already exists means a folder git doesn't list as a worktree is in the way; git may have created the branch at `origin/main` first, with nothing on it. Never reuse or remove an earlier session's worktree, branch or folder; a human decides.

### 3. Plan + Codex plan review

1. Read the task description, acceptance criteria, and relevant source files
2. Produce a step-by-step implementation plan inline (do NOT use EnterPlanMode/ExitPlanMode — those require interactive approval and block autonomous execution)
3. Run a quick Codex plan review (same as `/work` Step 4b): write the plan to a temp file, run `codex exec - --sandbox read-only`, read feedback. If Codex says REVISE, address the concerns before implementing. If Codex fails, proceed anyway.
4. Proceed to implementation

### 4. Implement + Codex implementation check

1. Delegate implementation to the `coder` agent (Opus 5.5; `builder` only for very easy, mechanical tasks) with the task file, the plan and the worktree path. It runs the AGENTS.md verify rows for the areas it touched and fixes failures.
2. Run a Codex implementation check (same as `/work` Step 5b): native review of working tree changes via companion script or `codex exec -`. Fix any issues found before proceeding to /ship.

### 5. Ship review loop

This is the final quality gate. **The review is mandatory.** Do NOT skip it to save time or tokens — time and tokens are not the bottleneck; shipping broken code is. The only acceptable reason to skip is if all Codex reviewers fail, in which case warn the user.

The `/ship` skill now uses a tiered review: a fast native pre-screen runs first, and specialized reviewers only run for large or critical changes. This means most tasks that already passed the implementation check in Step 4 will hit the fast lane in `/ship`.

Loop until the code passes review:

```
repeat:
  a. Run the AGENTS.md verify rows for the touched areas — fix failures before reviewing
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

1. Update the session file — append to `completed_tasks`, increment `task_count`, reset `consecutive_skips` to 0, clear `current_task` and `current_claim` (`/ship` released the claim):
   ```bash
   SESSION_FILE=<the path Step 0a printed>
   jq \
     --arg id "B-XXX" \
     --arg title "task title" \
     --argjson files '["file1.ts","file2.ts"]' \
     --arg notes "any notes" \
     '.completed_tasks += [{"task_id":$id,"title":$title,"files_changed":$files,"notes":$notes}]
      | .task_count += 1
      | .consecutive_skips = 0
      | .current_task = null
      | .current_claim = null' \
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

1. If the task was started (claimed in step 1b), release the worker, by its UUID (step 1b):
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0a printed>
   P=${SESSION_FILE##*-}; P=${P%.json}
   U=
   for C in "$(jq -r '.current_claim // empty' "$SESSION_FILE" 2>/dev/null)" "$(cat "/tmp/kompara-worker-$P.uuid" 2>/dev/null)"; do
     if [ -n "$C" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$C" | jq -e --arg t "<task-id>" --argjson p "$P" '.task == $t and .pid == $p' > /dev/null; then U=$C; break; fi
   done
   if [ -n "$U" ]; then "$MAIN_PATH/pming/worker.sh" release --uuid "$U"; else echo "No claim of this session's on <task-id> to release"; fi
   ```
   Only if step 2 created the task's worktree in this iteration, release with `--worktree` instead, which also removes that worktree when nothing changed in it. After step 2 failed, a worktree at that path is an earlier session's, and `--worktree` would remove it:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0a printed>
   P=${SESSION_FILE##*-}; P=${P%.json}
   U=
   for C in "$(jq -r '.current_claim // empty' "$SESSION_FILE" 2>/dev/null)" "$(cat "/tmp/kompara-worker-$P.uuid" 2>/dev/null)"; do
     if [ -n "$C" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$C" | jq -e --arg t "<task-id>" --argjson p "$P" '.task == $t and .pid == $p' > /dev/null; then U=$C; break; fi
   done
   if [ -n "$U" ]; then "$MAIN_PATH/pming/worker.sh" release --worktree --uuid "$U"; else echo "No claim of this session's on <task-id> to release"; fi
   ```
2. Update the session file — append to `skipped_tasks`, increment `consecutive_skips`, clear `current_task` and `current_claim`:
   ```bash
   SESSION_FILE=<the path Step 0a printed>
   jq \
     --arg id "B-XXX" \
     --arg title "task title" \
     --arg reason "reason for skip" \
     --arg reviewer "reviewer that rejected, or N/A" \
     '.skipped_tasks += [{"task_id":$id,"title":$title,"reason":$reason,"reviewer":$reviewer}]
      | .consecutive_skips += 1
      | .current_task = null
      | .current_claim = null' \
     "$SESSION_FILE" > "${SESSION_FILE}.tmp" && mv "${SESSION_FILE}.tmp" "$SESSION_FILE"
   ```
3. Check stop conditions:
   - If `consecutive_skips >= 3`: set `stop_reason` to `"consecutive_skips"`.
4. **If a stop condition was hit:** Proceed to Step 8.
5. **If continuing:** `/clear` → re-invoke `/work-auto` with no arguments. **STOP here.**

### 8. Session report

When the loop stops (any reason), generate `session-report.md` and commit it to `main` from a new baseline worktree (item 3), never from this session's checkout.

1. Read the session file to get all tracking data:
   ```bash
   SESSION_FILE=<the path Step 0a printed>
   cat "$SESSION_FILE"
   ```
   Extract `completed_tasks`, `skipped_tasks`, `session_start`, `stop_reason`, `task_count`.

2. Map `stop_reason` to human-readable text:
   - `"budget_reached"` → "Budget of N tasks reached."
   - `"all_done"` → "All pending tasks completed."
   - `"consecutive_skips"` → "3 consecutive tasks skipped — systemic issue suspected."
   - `"specific_tasks_exhausted"` → "All specified tasks processed."

3. Gather additional data from `main` as it is now: run the Step 0c item 1 block again. It prints `BASELINE: <path>`, a new baseline worktree at a freshly fetched `origin/main`, `<baseline>` below. Items 5 and 7 and Step 9 use it too, and Step 9 removes it.
   - Current state of main: in `<baseline>`, install the backend deps and run the AGENTS.md verify rows for `android/` and `backend/` (Step 0c items 2 and 3), report pass/fail
   - Git log of all commits made this session: `git -C "<baseline>" log --oneline --since="<session_start>" HEAD`
   - Any remaining pending tasks by priority (the Step 0 listing), leaving out this run's `completed_tasks`, whose move to `done/` item 7 publishes

4. **Analyze patterns** in the session data before writing the report:
   - If multiple tasks were skipped for the same reason, identify the pattern (e.g., "3 skips due to architecture boundary violations → may need a refactor before these tasks are feasible")
   - If multiple rejections came from the same reviewer, note which reviewer and the common finding
   - If iterations-to-pass was consistently >1, note what kept failing

5. Write `<baseline>/session-report.md` with this structure:

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
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0a printed>
   TELEMETRY_FILE="$MAIN_PATH/pming/.telemetry.jsonl"
   if [ -f "$TELEMETRY_FILE" ]; then
     COMPLETED=$(jq '.completed_tasks | length' "$SESSION_FILE")
     SKIPPED=$(jq '.skipped_tasks | length' "$SESSION_FILE")
     START=$(jq -r '.session_start' "$SESSION_FILE")
     DURATION_MIN=$(( ($(date +%s) - $(date -j -u -f "%Y-%m-%dT%H:%M:%SZ" "$START" +%s 2>/dev/null || echo $(date +%s))) / 60 ))
     echo "{\"ts\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"event\":\"work_auto_session\",\"tasks_completed\":$COMPLETED,\"tasks_skipped\":$SKIPPED,\"duration_min\":$DURATION_MIN}" >> "$TELEMETRY_FILE"
   fi
   ```

7. Commit and push the session report from the baseline worktree. With it goes this run's own PM state: each task in `completed_tasks` still in `pending/` on `main` moves to `done/` with `status: done`, which `/ship` leaves uncommitted in the task worktree today (TD-047 item 2). Nothing else is committed: the `pming/` changes in this session's checkout or the main checkout may be other sessions' work, and neither checkout is on `main`. If `main` moved since the fetch, the block rebases the commit onto it once. If the push still fails, the commit stays in the baseline worktree: Step 9 keeps it and the session file, and the final message says so.
```bash
SESSION_FILE=<the path Step 0a printed>
BASE="<baseline>"
NAME=$(basename "$SESSION_FILE" .json); TMPR=$(cd "${TMPDIR:-/tmp}" && pwd -P)
case "$BASE" in "$TMPR/$NAME.baseline."*) ;; *) BASE= ;; esac
if [ -z "$BASE" ] || [ "$(git -C "$BASE" rev-parse --show-toplevel 2>/dev/null)" != "$BASE" ] || git -C "$BASE" symbolic-ref -q HEAD >/dev/null; then
  echo "STOP: not this run's baseline worktree"; false
else
  MOVED=ok
  for ID in $(jq -r '.completed_tasks[]?.task_id' "$SESSION_FILE"); do
    [ -f "$BASE/pming/tasks/pending/$ID.md" ] || continue
    mkdir -p "$BASE/pming/tasks/done" &&
      git -C "$BASE" mv "pming/tasks/pending/$ID.md" "pming/tasks/done/$ID.md" &&
      sed -i '' '2,/^---$/s/^status: .*/status: done/' "$BASE/pming/tasks/done/$ID.md" &&
      git -C "$BASE" add "pming/tasks/done/$ID.md" || MOVED=
  done
  [ -n "$MOVED" ] && git -C "$BASE" add session-report.md &&
    git -C "$BASE" commit -m "Update session report — N tasks completed" &&
    { git -C "$BASE" push origin HEAD:main ||
      { git -C "$BASE" fetch origin main && git -C "$BASE" rebase origin/main && git -C "$BASE" push origin HEAD:main; }; }
fi
```

### 9. Post-session cleanup

1. Clean up after the task worktrees this run created, the ones step 2 recorded in `created_worktrees`, and nothing else. Every other worktree may be another session's work, live or not: a Claude Code worktree (`.claude/worktrees/*`) whose branch has no commits of its own counts as merged, and is never touched.
   - **No worktree is removed here.** The only removals are by `release --worktree` as this run gives up a task's claim, at `/ship` and on the skip path; it refuses a worktree with changes. By now every claim is released, so another session may have claimed a task and reused a worktree left behind (`/work` does), and no check here could rule that out before removing it. The block names each one still there as `LEFT`; `/housekeeper` CODE-5 reports leftover worktrees and removes one only when it is safe and the user agrees.
   - **A recorded branch whose worktree is gone** is deleted only if it is merged into `origin/main` (the baseline's `HEAD`) and `git branch -d` agrees (it refuses one checked out anywhere): `REMOVED`, else `KEPT`.
   - It leaves out every task in `skipped_tasks` whose `reason` starts with `step 2 failed:`: an earlier session left its worktree, branch and folder, and a human decides.
   ```bash
   SESSION_FILE=<the path Step 0a printed>
   BASE="<baseline>"
   NAME=$(basename "$SESSION_FILE" .json); TMPR=$(cd "${TMPDIR:-/tmp}" && pwd -P)
   case "$BASE" in "$TMPR/$NAME.baseline."*) ;; *) BASE= ;; esac
   if [ -z "$BASE" ] || [ "$(git -C "$BASE" rev-parse --show-toplevel 2>/dev/null)" != "$BASE" ] || git -C "$BASE" symbolic-ref -q HEAD >/dev/null; then
     echo "STOP: not this run's baseline worktree"; false
   else
     BASE_HEAD=$(git -C "$BASE" rev-parse HEAD)
     jq -r '[.skipped_tasks[]? | select((.reason // "") | startswith("step 2 failed:")) | .task_id] as $left
       | .created_worktrees[]? | select(.task_id | IN($left[]) | not) | [.path, .branch] | @tsv' "$SESSION_FILE" |
     while IFS=$'\t' read -r WT BR; do
       if git -C "$BASE" worktree list --porcelain | grep -Fxq "worktree $WT"; then
         echo "LEFT: $WT"
       elif git -C "$BASE" show-ref --verify --quiet "refs/heads/$BR"; then
         if git -C "$BASE" merge-base --is-ancestor "refs/heads/$BR" "$BASE_HEAD" && git -C "$BASE" branch -d "$BR"; then
           echo "REMOVED: $BR"
         else
           echo "KEPT: $BR (not merged into origin/main, or checked out)"
         fi
       fi
     done
   fi
   ```
   A `STOP:` line means Step 8 has no baseline worktree of this run's: leave every worktree alone and say so.

2. Release own worker entry, if this session still holds one, by its UUID (step 1b), on whatever task:
```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
SESSION_FILE=<the path Step 0a printed>
P=${SESSION_FILE##*-}; P=${P%.json}
U=
for C in "$(jq -r '.current_claim // empty' "$SESSION_FILE" 2>/dev/null)" "$(cat "/tmp/kompara-worker-$P.uuid" 2>/dev/null)"; do
  if [ -n "$C" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$C" | jq -e --argjson p "$P" '.pid == $p' > /dev/null; then U=$C; break; fi
done
if [ -n "$U" ]; then "$MAIN_PATH/pming/worker.sh" release --uuid "$U"; else echo "No claim of this session's to release"; fi
```

3. Delete the session file:
```bash
SESSION_FILE=<the path Step 0a printed>
rm -f "$SESSION_FILE"
```

4. Remove the baseline worktree with the Step 0c item 4 block.

If Step 8 couldn't push its commit, skip items 3 and 4: keep the session file and the baseline worktree, which holds the commit, and name both in the final message. Running `/work-auto` again within 24 hours resumes at Step 8 (Step 0b item 4) with a new baseline worktree.

This step commits nothing. Step 8 committed the session report and this run's task moves to `done/`; each task's code went through `/ship`. Any other `pming/` change in this session's checkout or the main checkout may be another session's.

## Rules

- Never stop to ask for confirmation — this is fully autonomous
- **Between every task iteration, `/clear` and re-invoke to prevent context rot** — each task gets a fresh context window
- **Session state is persisted to `/tmp/kompara-work-auto-$SESSION_PID.json`** — never keep tracking data in conversation memory
- **All session file writes use atomic rename** (`jq > .tmp && mv .tmp file`) to prevent corruption
- If tests or lint fail after 3 consecutive fix attempts, skip the task and move to the next one
- If the adversarial reviewer rejects 3 times in a row, skip the task and move to the next one
- If a task is `ops` or `business` category (non-code), skip it (leave as `todo`) and continue
- If a task budget was set and `task_count >= budget`, proceed to Step 8 (session report)
- If working on specific tasks and all are processed, proceed to Step 8 (session report)
- Keep going until all tasks are done, budget is reached, or 3 consecutive tasks are skipped
- If 3 consecutive tasks are skipped, proceed to Step 8 — something systemic may be wrong
- Worker coordination is handled by `pming/worker.sh` — never read/write `pming/.workers` directly
- **Every `worker.sh` call runs `$MAIN_PATH/pming/worker.sh` and sets `MAIN_PATH` in the same command** — works from any worktree
- **Conflict-aware selection**: always check what other workers are doing before picking a task
- **Never switch, pull or commit in this session's checkout, or read or edit its task files** — read them from `origin/main`, and check and commit to `main` in a baseline worktree (Step 0)
- **A task worktree is removed only by `release --worktree` as this run gives up the task's claim** (`/ship`, the skip path); post-session cleanup removes none and only deletes merged branches of the worktrees step 2 recorded (`created_worktrees`) — never touch `.claude/worktrees/*` or any other session's worktree
