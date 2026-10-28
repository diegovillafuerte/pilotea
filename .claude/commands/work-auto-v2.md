# Work-Auto v2: Agent-Per-Task Autonomous Loop

Fully automated cycle using **fresh Agent per task** to prevent context rot. The main loop stays lightweight (task selection, session tracking, reporting). Each task gets a clean context window via a spawned Agent with `isolation: "worktree"`.

## Arguments

From $ARGUMENTS:
- **Empty**: Run until all tasks are done or a stop condition is hit
- **Number (e.g., `5`)**: Complete at most N tasks, then stop and generate session report
- **`B-XXX B-YYY ...`**: Work on specific tasks in order

## Step 0: Resolve paths and session

`$MAIN_PATH` is the main checkout, the first entry `git worktree list` prints. Claude Code's Bash tool keeps no shell variables between commands, so each block below that uses `$MAIN_PATH` sets it on its first line, and each one that uses `$SESSION_FILE` sets that too. Run each block as one command.

This session's checkout may be on any branch: it may even be the main checkout, with another session's uncommitted work. So this run never switches, pulls or commits in it, and never reads or edits the task files there:
- **Read task files from a freshly fetched `origin/main`.** This lists every pending task's fields, one `origin/main:<file>:<field>: <value>` line each, from any folder of the repository:
  ```bash
  git fetch origin main && git grep --full-name -E '^(id|title|status|priority|category|story|epic|depends_on|review):' origin/main -- ':(top)pming/tasks/pending/'
  ```
  After that fetch, `git show origin/main:pming/tasks/pending/B-XXX.md` prints one task file, and `git ls-tree --full-tree --name-only origin/main pming/tasks/done/` lists the finished ones.
- **Never write a task file in a checkout.** Each agent sets its task's status in its own worktree, where `/ship` commits it with the task. `/ship` marks it done, and the session report's commit moves a task this run shipped to `done/` on `main` if it isn't there yet (Step 4).
- **Check and commit to `main` in a temporary worktree** at `origin/main` (Step 1 and Step 4).
- **Check whether a task shipped** with this block, which prints `SHIPPED` or `NOT SHIPPED`. `/ship` moves the task file to `done/` once it has pushed: on `origin/main`, or, while it leaves that move uncommitted (TD-047 item 2), in the agent's worktree under `.claude/worktrees/agent-*`, which the block only reads. `STOP:` means it can't tell: stop here and tell the user; the next run resumes from the same point.
  ```bash
  MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
  if ! git fetch origin main; then
    echo "STOP: could not fetch origin main"; false
  elif git cat-file -e "origin/main:pming/tasks/done/{TASK_ID}.md" 2>/dev/null ||
      git worktree list --porcelain | sed -n 's/^worktree //p' | while IFS= read -r W; do
        case "$W" in "$MAIN_PATH"/.claude/worktrees/agent-*) [ -f "$W/pming/tasks/done/{TASK_ID}.md" ] && echo "$W" ;; esac
      done | grep -q .; then
    echo "SHIPPED"
  else
    echo "NOT SHIPPED"
  fi
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
echo "$SESSION_FILE"
```

It prints the session file's path. Each block below that uses `$SESSION_FILE` sets it to that path on its first line, `SESSION_FILE=<the path Step 0 printed>`.

If the session file exists and is < 24 hours old, resume from it. If `stop_reason` is set, go to Step 4 (session report). If `current_task` is set, check whether it shipped (above): `SHIPPED` → record success; `NOT SHIPPED` → record skip. Then continue the loop.

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

Run once on fresh start (skip on resume), on a clean baseline: a freshly fetched `origin/main`, checked out in a temporary worktree. Never `git checkout main` or `git pull` in this session's checkout (Step 0): with another session's uncommitted work there, that fails or carries the work along.

1. Fetch `origin/main` and check it out, detached, in a new temporary worktree, the **baseline worktree**. Its folder is named after this session's file, so the blocks that use it can tell it from another run's. Run this as one command:
   ```bash
   SESSION_FILE=<the path Step 0 printed>
   git fetch origin main || { echo "STOP: could not fetch origin main"; false; } &&
     BASE=$(mktemp -d "${TMPDIR:-/tmp}/$(basename "$SESSION_FILE" .json).baseline.XXXXXX") && BASE=$(cd "$BASE" && pwd -P) &&
     git worktree add --detach "$BASE" origin/main && echo "BASELINE: $BASE"
   ```
   It prints `BASELINE: <path>`; `<baseline>` below stands for that path.
2. `cd "<baseline>/backend" && pnpm install --frozen-lockfile`
3. The AGENTS.md **Verify** rows for `android/` and `backend/` — all must pass. Start each command with `cd "<baseline>/android" &&` or `cd "<baseline>/backend" &&`, since a `cd` doesn't carry over to the next command. The new worktree has no `android/local.properties`, so Gradle finds the SDK through `ANDROID_HOME` (AGENTS.md, "Java for Gradle").
4. Remove the baseline worktree, whether the checks passed or not. It is this run's own detached checkout of `origin/main`, so `--force` loses nothing. The block first makes sure the path is one of this run's baseline worktrees: in `$TMPDIR`, named after this session's file, the root of a worktree, and detached:
   ```bash
   SESSION_FILE=<the path Step 0 printed>
   BASE="<baseline>"
   NAME=$(basename "$SESSION_FILE" .json); TMPR=$(cd "${TMPDIR:-/tmp}" && pwd -P)
   case "$BASE" in "$TMPR/$NAME.baseline."*) ;; *) BASE= ;; esac
   if [ -z "$BASE" ] || [ "$(git -C "$BASE" rev-parse --show-toplevel 2>/dev/null)" != "$BASE" ] || git -C "$BASE" symbolic-ref -q HEAD >/dev/null; then
     echo "STOP: not this run's baseline worktree"; false
   else
     git worktree remove --force "$BASE"
   fi
   ```

If the fetch or any check fails: STOP. Remove the baseline worktree if it exists (item 4), and exit with the failure as the final message. Nothing was claimed, created or committed.

## Step 2: The Loop

Each iteration picks a task, spawns an Agent to do the work, and records the result.

### 2a. Pick the next task

1. Clean stale entries:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   "$MAIN_PATH/pming/worker.sh" cleanup
   ```
2. Read session file for `specific_tasks`, `completed_tasks`, `skipped_tasks`
3. If specific tasks given, pick next unprocessed one
4. Otherwise, conflict-aware auto-selection:
   a. Read all pending tasks from `origin/main` (the Step 0 listing) — id, priority, category, status, story, epic. Leave out this run's `completed_tasks` and `skipped_tasks`: a task this run shipped stays in `pending/` there until Step 4 publishes its move to `done/`
   b. Get active workers, and exclude their tasks:
      ```bash
      MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
      "$MAIN_PATH/pming/worker.sh" list --json
      ```
   c. **Skip `ops`, `business` category tasks and `blocked` status tasks**
   d. **Skip tasks with `review: human` in frontmatter**
   e. **Skip tasks whose `depends_on` lists a task that is neither in `pming/tasks/done/` on `origin/main` (Step 0) nor in this run's `completed_tasks`**
   f. Prefer tasks in different stories/epics from active workers
   g. Priority ordering: critical > urgent > high > medium > low, then lowest B-XXX number
5. If no tasks remain: set `stop_reason` to `"all_done"`, go to Step 4

Read the task file's content from `origin/main` (Step 0). Record `current_task` in session file.

### 2b. Spawn task Agent

Spawn a **single Agent** with `isolation: "worktree"` and `model: "opus"`. The Agent gets a fresh context window with no baggage from previous tasks. Its worktree is Claude Code's, under `.claude/worktrees/`: made from a freshly fetched `origin/<default branch>` by default, or from this session's HEAD when the `worktree.baseRef` setting is `head`. Either way the agent starts its own branch from `origin/main` (Phase 1).

Fill in `{MAIN_PATH}` with the main checkout's path, which this prints. An agent isolated in a worktree can't compute it: Claude Code refuses its commands that run git inside `$(...)`.
```bash
git worktree list --porcelain | head -1 | sed 's/worktree //'
```

**Agent prompt template** (fill in the values — the main loop constructs this):

````
You are working on task {TASK_ID} for the Kompara project autonomously.

## Task
{FULL TASK FILE CONTENT}

## Instructions

You are in an isolated worktree, which Claude Code created for you under `.claude/worktrees/`. Claude Code refuses some git commands from an agent isolated in a worktree: git inside `$(...)`, and `git -C` or a `cd` before git that points at the main checkout or at a path computed in the same command. Its Bash tool also keeps no shell variables between commands. So:
- Every `worker.sh` call sets `MAIN_PATH="{MAIN_PATH}"`, the main checkout's path, in the same command, then runs `"$MAIN_PATH/pming/worker.sh"`, as below.
- Where a skill block, such as `/ship`'s, sets `MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')`, set `MAIN_PATH="{MAIN_PATH}"` instead.
- Split any other command Claude Code refuses into plain commands, as its message says.

### Phase 1: Setup
1. Register as a worker:
   ```bash
   MAIN_PATH="{MAIN_PATH}"
   "$MAIN_PATH/pming/worker.sh" claim {TASK_ID} task {TASK_ID} work/{TASK_ID}
   ```
   If `CONFLICT:` is returned, respond with: `{"success": false, "reason": "worker conflict"}`
2. Start this task's branch, `work/{TASK_ID}`, from a freshly fetched `origin/main`, the `main` that `/ship` merges it into. This worktree may have started from another commit, whose commits `/ship` would merge too. A `work/{TASK_ID}` an earlier session left with no commits of its own is reset to `origin/main`. Run this as one command:
   ```bash
   if git status --porcelain --untracked-files=normal | grep -q .; then
     echo "STOP: this worktree has uncommitted changes"; false
   elif ! git fetch origin main; then
     echo "STOP: could not fetch origin main"; false
   elif ! git show-ref --verify --quiet refs/heads/work/{TASK_ID}; then
     git switch --no-track -c work/{TASK_ID} origin/main
   elif git merge-base --is-ancestor work/{TASK_ID} origin/main; then
     git switch --no-track -C work/{TASK_ID} origin/main
   else
     echo "LEFTOVER: work/{TASK_ID} has commits origin/main doesn't:"; git log --oneline origin/main..work/{TASK_ID}; false
   fi
   ```
   If it fails, respond with `{"success": false, "reason": "branch setup failed: <its STOP: or LEFTOVER: line, or git's fatal: line>"}`. Git refuses to switch to a `work/{TASK_ID}` checked out in another worktree. Never reuse, reset or delete a branch with commits of its own; a human decides.
3. Update the task status to `in_progress` in this worktree's copy of the task file; `/ship` commits it with the task
4. If the task touches `backend/`: `(cd backend && pnpm install --frozen-lockfile)`

### Phase 2: Plan
1. Read the task description and acceptance criteria carefully
2. Read all relevant source files mentioned in the task
3. Produce a step-by-step implementation plan
4. If a Codex CLI is available (`which codex`), write the plan to a temp file and run a quick Codex plan review:
   ```bash
   codex exec - --sandbox read-only --output-last-message /tmp/plan-review-{TASK_ID}.txt -C "$(pwd)" < /tmp/plan-{TASK_ID}.txt > /tmp/plan-review-{TASK_ID}.log 2>&1
   rc=$?
   if [ "$rc" -eq 0 ] && [ -s /tmp/plan-review-{TASK_ID}.txt ]; then cat /tmp/plan-review-{TASK_ID}.txt; else echo "CODEX FAILED (exit $rc)"; tail -n 20 /tmp/plan-review-{TASK_ID}.log; fi
   ```
   Address any REVISE feedback. If Codex fails, proceed anyway.

### Phase 3: Implement
1. Follow the plan step by step
2. Run the AGENTS.md verify rows for the areas you touched when done
3. If a check fails, fix and retry (up to 3 attempts)
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
- Every worker.sh call sets `MAIN_PATH="{MAIN_PATH}"` in the same command, then runs `"$MAIN_PATH/pming/worker.sh"`; never compute it with `$(git ...)`, which Claude Code refuses here
````

**Agent call parameters:**
- `model: "opus"` — all agents must use Opus
- `isolation: "worktree"` — gets its own worktree, Claude Code's, under `.claude/worktrees/`. The Agent tool removes it if the agent left no changes, and keeps it otherwise; never remove one yourself (Step 5)
- `timeout` is implicitly capped by the Agent tool, but aim for completion within 60 minutes

### 2c. Process Agent result

Parse the Agent's return message. Extract the JSON result.

**On success** (`"success": true`):
1. Verify it shipped with the Step 0 check — trust but verify. If it prints `NOT SHIPPED`, handle the result as a failure: the agent's work didn't reach `main`
2. Update session file: append to `completed_tasks`, increment `task_count`, reset `consecutive_skips` to 0, clear `current_task`
3. Check stop conditions:
   - `task_count >= budget` → set `stop_reason` to `"budget_reached"`
   - All `specific_tasks` processed → set `stop_reason` to `"specific_tasks_exhausted"`

**On failure** (`"success": false`):
1. Leave the agent's worktree alone: it is Claude Code's, under `.claude/worktrees/`. The Agent tool removes it if the agent left no changes, and keeps it otherwise, for a human.
2. Leave the task file alone. The agent set `in_progress` only in its own worktree, so `origin/main` still has the status the task had, and the main checkout's copy may be another session's work.
3. Release the agent's claim if it is still registered, by its UUID. The agent shares this session's process, so its claim wrote this session's claim marker (`/tmp/kompara-worker-<pid>.uuid`, the pid from the session file's name). The block uses it only when `worker.sh info` shows that claim is this session's and on this task, and otherwise releases nothing. A plain `release` would resolve the UUID from the cwd's worktree first, whose `.worker-uuid` may be another session's:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0 printed>
   P=${SESSION_FILE##*-}; P=${P%.json}
   U=
   for C in "$(cat "/tmp/kompara-worker-$P.uuid" 2>/dev/null)"; do
     if [ -n "$C" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$C" | jq -e --arg t "{TASK_ID}" --argjson p "$P" '.task == $t and .pid == $p' > /dev/null; then U=$C; break; fi
   done
   if [ -n "$U" ]; then "$MAIN_PATH/pming/worker.sh" release --uuid "$U"; else echo "No claim of this session's on {TASK_ID} to release"; fi
   ```
4. Update session file: append to `skipped_tasks`, increment `consecutive_skips`, clear `current_task`
5. Check stop conditions:
   - `consecutive_skips >= 3` → set `stop_reason` to `"consecutive_skips"`

**On unparseable result** (Agent returned something that isn't valid JSON):
- Treat as failure with reason "agent returned unparseable result"
- Run the Step 0 check to determine if it actually shipped

### 2d. Continue or stop

If `stop_reason` is set → go to Step 4 (session report).
Otherwise → go to Step 2a (pick next task).

**No `/clear` needed.** The main loop stays lightweight because all implementation context lives inside the Agent, which is already gone.

## Step 3: (removed — no clear/re-invoke needed)

## Step 4: Session report

When the loop stops (any reason), generate `session-report.md` and commit it to `main` from a new baseline worktree (item 3), never from this session's checkout.

1. Read the session file, the path Step 0 printed
2. Map `stop_reason` to human-readable text:
   - `"budget_reached"` → "Budget of N tasks reached."
   - `"all_done"` → "All pending tasks completed."
   - `"consecutive_skips"` → "3 consecutive tasks skipped — systemic issue suspected."
   - `"specific_tasks_exhausted"` → "All specified tasks processed."

3. Gather data from `main` as it is now: run the Step 1 item 1 block again. It prints `BASELINE: <path>`, a new baseline worktree at a freshly fetched `origin/main`, `<baseline>` below. Items 5 and 7 use it too, and Step 5 removes it.
   - the AGENTS.md verify rows for `android/` and `backend/` in `<baseline>`, after its backend install (Step 1 items 2 and 3) — pass/fail
   - commits since `session_start`: `git -C "<baseline>" log --oneline --since="<session_start>" HEAD`
   - Remaining pending tasks by priority (the Step 0 listing), leaving out this run's `completed_tasks`, whose move to `done/` item 7 publishes

4. Analyze patterns:
   - Multiple skips for same reason → identify systemic issue
   - Same reviewer rejecting → note which and what

5. Write `<baseline>/session-report.md`:
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
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
SESSION_FILE=<the path Step 0 printed>
TELEMETRY_FILE="$MAIN_PATH/pming/.telemetry.jsonl"
if [ -f "$TELEMETRY_FILE" ]; then
  COMPLETED=$(jq '.completed_tasks | length' "$SESSION_FILE")
  SKIPPED=$(jq '.skipped_tasks | length' "$SESSION_FILE")
  START=$(jq -r '.session_start' "$SESSION_FILE")
  DURATION_MIN=$(( ($(date +%s) - $(date -j -u -f "%Y-%m-%dT%H:%M:%SZ" "$START" +%s 2>/dev/null || echo $(date +%s))) / 60 ))
  echo "{\"ts\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"event\":\"work_auto_v2_session\",\"tasks_completed\":$COMPLETED,\"tasks_skipped\":$SKIPPED,\"duration_min\":$DURATION_MIN}" >> "$TELEMETRY_FILE"
fi
```

7. Commit and push the report from the baseline worktree. With it goes this run's own PM state: each task in `completed_tasks` still in `pending/` on `main` moves to `done/` with `status: done`, which `/ship` leaves uncommitted in the agent's worktree today (TD-047 item 2). Nothing else is committed: the `pming/` changes in this session's checkout or the main checkout may be other sessions' work, and neither checkout is on `main`. If `main` moved since the fetch, the block rebases the commit onto it once. If the push still fails, the commit stays in the baseline worktree: Step 5 keeps it and the session file, and the final message says so.
```bash
SESSION_FILE=<the path Step 0 printed>
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

## Step 5: Post-session cleanup

1. Remove no worktrees. Apart from the baseline worktree, this run creates none of its own: each agent's worktree is Claude Code's, under `.claude/worktrees/`, and the Agent tool removes it when the agent left no changes. Live sessions' worktrees are there too, and a clean one whose branch has no commits of its own counts as merged, so a sweep of merged worktrees would remove them.

2. Release this session's claim, if one is still registered, by its UUID (Step 2c):
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   SESSION_FILE=<the path Step 0 printed>
   P=${SESSION_FILE##*-}; P=${P%.json}
   U=
   for C in "$(cat "/tmp/kompara-worker-$P.uuid" 2>/dev/null)"; do
     if [ -n "$C" ] && "$MAIN_PATH/pming/worker.sh" info --uuid "$C" | jq -e --argjson p "$P" '.pid == $p' > /dev/null; then U=$C; break; fi
   done
   if [ -n "$U" ]; then "$MAIN_PATH/pming/worker.sh" release --uuid "$U"; else echo "No claim of this session's to release"; fi
   ```

3. Delete the session file: `rm -f "<the path Step 0 printed>"`

4. Remove the baseline worktree with the Step 1 item 4 block.

If Step 4 couldn't push its commit, skip items 3 and 4: keep the session file and the baseline worktree, which holds the commit, and name both in the final message. Running `/work-auto-v2` again within 24 hours resumes at Step 4 with a new baseline worktree.

This step commits nothing. Step 4 committed the session report and this run's task moves to `done/`; each task's code went through its agent's `/ship`. Any other `pming/` change in this session's checkout or the main checkout may be another session's.

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
- **Never switch, pull or commit in this session's checkout, or read or edit its task files** — read them from `origin/main`, and check and commit to `main` in a baseline worktree (Step 0)
- **Never remove a worktree other than the baseline** — the agents' are Claude Code's (`.claude/worktrees/*`), and the Agent tool cleans them up
