---
name: work
description: Start working on a task in an isolated worktree. Spins up a branch, plans the approach, then implements. Use /work to resume, /work B-XXX for a task, /work S-XXX for a story, /work E-XXX for an epic.
argument-hint: [B-XXX | S-XXX | E-XXX]
---

# Start Working

Begin work on a task. Isolates work in a git worktree, plans inline, then implements with Codex review checkpoints. Review is tiered: Codex checks the plan and implementation here in `/work`, then `/ship` runs a lighter or heavier gate depending on change size and risk. Model routing (see `CLAUDE.md`): this session plans and orchestrates; the `coder` agent (Opus 5.5) implements.

## Step 0: Main checkout path

`$MAIN_PATH` is the main checkout, the first entry `git worktree list` prints. Worker coordination is handled atomically by `$MAIN_PATH/pming/worker.sh`. All worker state lives in `$MAIN_PATH/pming/.workers` (JSONL format). Claude Code's Bash tool keeps no shell variables between commands, so each block below that uses `$MAIN_PATH` sets it on its first line. Run each block as one command.

## Step 1: Parse input

From $ARGUMENTS, determine what to work on:
- **`B-XXX`**: a specific task
- **`S-XXX`**: a story (work through its tasks sequentially)
- **`E-XXX`**: an epic (work through its tasks sequentially)
- **Empty or `next`**: resume current work session or pick highest-priority `todo` task

## Step 1.5: Stale worker cleanup

Run cleanup to remove dead sessions and expired pending entries:

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
"$MAIN_PATH/pming/worker.sh" cleanup
```

## Step 2: Determine the target task

This step uses two `worker.sh` commands. Run each one only where a step below says to:
- **list** prints the active workers as JSON:
  ```bash
  MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
  "$MAIN_PATH/pming/worker.sh" list --json
  ```
- **claim** registers this session on a task. Put the task's ID in place of `<task-id>`, the mode (`task`, `story` or `epic`) in place of `<mode>`, and the ID it works through (the task, story or epic) in place of `<target>`. It prints `OK <uuid>`, or `CONFLICT: ...` when another worker has the task:
  ```bash
  MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
  "$MAIN_PATH/pming/worker.sh" claim <task-id> <mode> <target> work/<task-id>
  ```

### Specific task (B-XXX)
1. Read the task file (check both `pming/tasks/pending/B-XXX.md` and `pming/tasks/done/B-XXX.md`)
2. Check for conflicts: run **list** — if another worker is already on this task, warn the user and ask to confirm
3. Update its status to `in_progress`
4. Register: run **claim** for B-XXX, mode `task`, target B-XXX
   - If output starts with `CONFLICT:`, another worker has it. Warn user and ask to confirm.

### Story (S-XXX)
1. Read the story file (check `pming/stories/pending/S-XXX.md`)
2. Find all tasks in this story: glob `pming/tasks/pending/B-*.md`, filter by `story: S-XXX`
3. Sort by ID number
4. Get active workers: run **list** — skip tasks already claimed
5. **Skip `ops` and `business` category tasks** — these require human action
6. Find first unclaimed code task with status `todo` and set it to `in_progress`
7. Update story status to `active` if currently `backlog` or `todo`
8. Register: run **claim** for B-XXX, mode `story`, target S-XXX

### Epic (E-XXX)
1. Read the epic file (check `pming/epics/pending/E-XXX.md`)
2. Find all tasks in this epic: glob `pming/tasks/pending/B-*.md`, filter by `epic: E-XXX`
3. Sort by story order (S-XXX number), then task ID within story
4. Get active workers: run **list** — skip tasks already claimed
5. **Skip `ops` and `business` category tasks** — these require human action
6. Find first unclaimed code task with status `todo` and set it to `in_progress`
7. Update epic status to `active` if currently `backlog`
8. Register: run **claim** for B-XXX, mode `epic`, target E-XXX

### Resume (no args)
1. Check for an existing worker entry:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   "$MAIN_PATH/pming/worker.sh" info
   ```
   - If exit code 0 (entry found): parse the JSON output to get `task` and `branch`
   - If a worktree exists for this task, `cd` into it (if the `cd` fails, there is none yet). Run this as one command, since shell variables don't carry over to the next one:
     ```bash
     MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
     cd "$MAIN_PATH/.worktrees/<task-id>" && git log --oneline origin/main..HEAD
     ```
     When the `cd` works, the block also lists the worktree's commits that `origin/main` doesn't have: check them as Step 3 says for a worktree that already existed.
   - If the task's status field shows `pending` (two-phase claim not yet activated), activate it:
     ```bash
     MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
     "$MAIN_PATH/pming/worker.sh" activate
     ```
   - If the task is still `in_progress`, resume working on it (skip to Step 4 if worktree exists, Step 3 if not)
   - If the task is `done`, find next `todo` task in the group (respecting mode/target) and start it
2. If `worker.sh info` returns exit code 1 (no entry): use **conflict-aware auto-selection** (see Step 2a)

### Step 2a: Conflict-aware auto-selection

When no specific task/story/epic is given and no active session exists:

1. Read all pending tasks: glob `pming/tasks/pending/B-*.md`
2. Get active workers: run **list**
3. Filter candidates:
   - Exclude tasks already claimed by active workers
   - **Skip `ops` and `business` category tasks** — these require human action, not code
   - **Tier 1 (safest):** tasks in stories AND epics that no active worker is touching
   - **Tier 2:** tasks in different stories but same epic as an active worker
   - **Tier 3:** tasks in the same story as an active worker (warn: may cause merge conflicts)
4. Within the best available tier, apply priority ordering: urgent > high > medium > low, then lowest B-XXX number
5. If ALL remaining tasks are Tier 3 or claimed, warn the user: "All remaining tasks overlap with active workers. Pick manually or wait for a worker to finish."
6. Register the selected task: run **claim** for B-XXX, mode `task`, target B-XXX

## Step 3: Spin up worktree

Create an isolated branch and worktree for this work session:

1. Derive branch name: `work/<task-id>` (e.g., `work/B-042`)
2. Create the worktree, or move into it if it already exists. Run this as one command, since shell variables don't carry over to the next one. The worktree goes in `$MAIN_PATH/.worktrees/`, where `worker.sh` looks for it, even when this session runs in another worktree (a relative `.worktrees/` would nest it there). The branch starts from a freshly fetched `origin/main`, the `main` that `/ship` merges it into, never from this checkout's HEAD: that may be another branch (a Claude Code worktree's, or whatever the main checkout has checked out), and `/ship` would merge its commits too. Put this session's claim UUID in place of `<uuid>`: the `OK <uuid>` that **claim** printed, or, after `update-task` moved the claim to this task, the `uuid` field `worker.sh info` prints. The block writes it to the worktree only when it creates the worktree:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   WT="$MAIN_PATH/.worktrees/<task-id>"
   BR="work/<task-id>"
   if git worktree list --porcelain | grep -Fxq "worktree $WT"; then
     cd "$WT" && git log --oneline origin/main..HEAD
   elif ! git fetch origin main; then
     echo "STOP: could not fetch origin main"; false
   elif ! git show-ref --verify --quiet "refs/heads/$BR"; then
     git worktree add --no-track -b "$BR" "$WT" origin/main && echo "<uuid>" > "$WT/.worker-uuid" && cd "$WT"
   elif [ -z "$(git rev-list -1 "origin/main..$BR")" ]; then
     git worktree add --no-track -B "$BR" "$WT" origin/main && echo "<uuid>" > "$WT/.worker-uuid" && cd "$WT"
   else
     echo "LEFTOVER: $BR has commits origin/main doesn't:"; git log --oneline "origin/main..$BR"; false
   fi
   ```
   - If the worktree already existed, the block moves into it and lists its commits that `origin/main` doesn't have. `/ship` merges them all, so each must be this task's own work: a worktree made before branches started from `origin/main` may carry another branch's commits. If any isn't, show the list and ask the user before going on.
   - A `work/<task-id>` branch an earlier session left with no commits of its own is reset to `origin/main` (`-B`): nothing is lost.
   - `STOP:` the fetch failed. Tell the user and stop: the branch must start from the current `origin/main`.
   - A git error that the folder already exists: a folder git doesn't list as a worktree is in the way (`/housekeeper` CODE-5 reports these). Git may have created `work/<task-id>` at `origin/main` first, with nothing on it. Tell the user and stop; never delete the folder.
   - `LEFTOVER:` an earlier session left `work/<task-id>` with the commits listed, which `origin/main` doesn't have, and the block created nothing. Show the list and ask the user which to do; never delete the branch:
     - **Resume it**, only if every listed commit is this task's own work, since `/ship` merges them all into `main`:
       ```bash
       MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
       WT="$MAIN_PATH/.worktrees/<task-id>"
       git worktree add "$WT" "work/<task-id>" && echo "<uuid>" > "$WT/.worker-uuid" && cd "$WT"
       ```
     - **Start over**: keep the old commits under a new branch name, then run the block above again:
       ```bash
       git branch -m "work/<task-id>" "work/<task-id>-old-$(date +%Y%m%d%H%M%S)"
       ```
3. Confirm to user: "Worktree ready on branch `work/<task-id>`"

## Step 4: Plan

Read the task and plan the approach inline:

1. **Show the task**: Display full task content including description and acceptance criteria
2. **Show hierarchy context**: Which epic and story, what comes before/after
3. **If working through a group**: Show progress (e.g., "Task 3/6 in S-003")
4. **Locate the relevant code** with a `scout` (paths, symbols, a short summary). Read yourself only the files the plan's decisions hinge on.
5. **Produce a step-by-step implementation plan** with specific files and changes
6. Proceed to Step 4b (Codex plan review)

## Step 4b: Codex plan review

Before implementing, send the plan to Codex for a quick sanity check. This catches architectural violations and design issues before code is written — much cheaper to fix at this stage.

1. Write the plan to a temp file with this prompt:

   ```
   You are reviewing an implementation plan for a task in the Kompara codebase (a native Android app in Kotlin and Jetpack Compose, plus a TypeScript backend).

   You have read-only access to the full repo. Read `.claude/codex-guide.md` FIRST for efficient navigation, then `AGENTS.md` for the repo map, module boundaries and the rules that are easy to break.

   ## Task
   {TASK DESCRIPTION AND ACCEPTANCE CRITERIA}

   ## Plan
   {STEP-BY-STEP PLAN FROM STEP 4}

   ## Review Instructions
   1. Check the plan against AGENTS.md: module boundaries, read-only capture, on-device processing, and the verify commands the change will need
   2. Will the planned changes break anything else? Check for side effects.
   3. Is the plan minimal — focused on the task, no unnecessary refactoring?
   4. Does the plan address all acceptance criteria?
   5. Are there simpler approaches the plan missed?

   Return a brief assessment in this format:
   VERDICT: PROCEED or REVISE
   If REVISE, list specific concerns (max 3) with one line each.
   ```

2. Run Codex (set `timeout: 300000` — 5 minutes):
   ```bash
   OUTFILE=$(mktemp)
   codex exec - \
     --sandbox read-only \
     --output-last-message "$OUTFILE" \
     -C "$(pwd)" < /tmp/kompara-plan-review.txt > "$OUTFILE.log" 2>&1
   rc=$?
   if [ "$rc" -eq 0 ] && [ -s "$OUTFILE" ]; then cat "$OUTFILE"; else echo "CODEX FAILED (exit $rc)"; tail -n 20 "$OUTFILE.log"; fi
   rm -f "$OUTFILE" "$OUTFILE.log" /tmp/kompara-plan-review.txt
   ```

3. **If PROCEED**: Show Codex's assessment briefly, move to Step 5.
4. **If REVISE**: Read the concerns, revise the plan to address them, then move to Step 5. Do NOT re-run the review — one pass is enough.
5. **If Codex fails** (timeout, CLI missing, error): Warn briefly ("Codex plan review unavailable — proceeding"), move to Step 5. This step is advisory, not blocking.

## Step 5: Implement

Execute the plan:

1. **If category is `code`**, delegate per the routing in `CLAUDE.md`:
   - Hand the task to the `coder` agent (Opus 5.5): the task file path, the approved plan, the worktree path and the files that matter. Use `builder` (Sonnet 5.5) only when the change is very easy and mechanical.
   - The agent implements, runs the AGENTS.md **Verify** rows for the areas it touched, and returns a short summary with a report path. Open the report only when something failed or looks off.
   - If the agent stops on an ambiguity or a needed deviation, apply "When to stop and ask" (AGENTS.md).
   - If it is still stuck after two genuinely different attempts, escalate to Codex (`/codex:rescue`), not to a bigger Claude model.
   - Proceed to Step 5b (Codex implementation check)
2. **If category is `ops` or `business`**:
   - Show what needs to be done
   - Provide any relevant links or context
   - Ask if the user wants guidance or has already done it

## Step 5b: Codex implementation check

After implementation passes lint and tests, run a quick Codex review to catch issues before `/ship`. This reduces the review load on ship and catches bugs earlier.

1. Run native Codex review on the working tree changes:
   ```bash
   node "${CLAUDE_PLUGIN_ROOT}/scripts/codex-companion.mjs" review --wait
   ```
   Set `timeout: 480000` (8 minutes).

   If `CLAUDE_PLUGIN_ROOT` is not set, fall back to:
   ```bash
   rm -f /tmp/kompara-impl-review.json
   codex exec - --sandbox read-only --output-last-message /tmp/kompara-impl-review.json -C "$(pwd)" > /tmp/kompara-impl-review.log 2>&1 <<'EOF'
   Review the uncommitted changes in this repository. Focus on bugs, data integrity risks, and missing error handling. Be brief — list only material issues.
   EOF
   rc=$?
   if [ "$rc" -eq 0 ] && [ -s /tmp/kompara-impl-review.json ]; then cat /tmp/kompara-impl-review.json; else echo "CODEX FAILED (exit $rc)"; tail -n 20 /tmp/kompara-impl-review.log; fi
   rm -f /tmp/kompara-impl-review.json /tmp/kompara-impl-review.log
   ```

2. **If the review is clean**: Tell the user "Implementation check passed. Ready to ship." and stop.
3. **If issues are found**: Send them back to the agent that implemented the change, `coder` or `builder` (continue it with SendMessage so it keeps its context) to fix and re-verify, then tell the user it's ready to ship.
4. **If Codex fails**: Warn briefly ("Implementation check unavailable"), tell the user it's ready to ship. The `/ship` review will still gate quality.

## Important notes
- Always update the task file's `status` field to `in_progress` when starting
- Worker coordination is handled by `pming/worker.sh` — never read/write `pming/.workers` directly
- When starting a group, also update the parent story/epic status to `active`
- If a task is already `in_progress`, confirm whether to resume it or switch
- If a task is already `done`, skip it and find the next one
- Worktrees live under `$MAIN_PATH/.worktrees/` — ensure `.worktrees/` is in `.gitignore`
- On `/ship`, the ship skill handles merging the worktree's feature branch back to main
- **Every `worker.sh` call runs `$MAIN_PATH/pming/worker.sh` and sets `MAIN_PATH` in the same command** — works from any worktree
