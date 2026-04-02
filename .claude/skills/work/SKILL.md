---
name: work
description: Start working on a task in an isolated worktree. Spins up a branch, plans the approach, then implements. Use /work to resume, /work B-XXX for a task, /work S-XXX for a story, /work E-XXX for an epic.
argument-hint: [B-XXX | S-XXX | E-XXX]
---

# Start Working

Begin work on a task. Isolates work in a git worktree, plans inline, then implements with Codex review checkpoints. Review is tiered: Codex checks the plan and implementation here in `/work`, then `/ship` runs a lighter or heavier gate depending on change size and risk.

## Step 0: Resolve main worktree path

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

Worker coordination is handled atomically by `$MAIN_PATH/pming/worker.sh`. All worker state lives in `$MAIN_PATH/pming/.workers` (JSONL format).

## Step 1: Parse input

From $ARGUMENTS, determine what to work on:
- **`B-XXX`**: a specific task
- **`S-XXX`**: a story (work through its tasks sequentially)
- **`E-XXX`**: an epic (work through its tasks sequentially)
- **Empty or `next`**: resume current work session or pick highest-priority `todo` task

## Step 1.5: Stale worker cleanup

Run cleanup to remove dead sessions and expired pending entries:

```bash
$MAIN_PATH/pming/worker.sh cleanup
```

## Step 2: Determine the target task

### Specific task (B-XXX)
1. Read the task file (check both `pming/tasks/pending/B-XXX.md` and `pming/tasks/done/B-XXX.md`)
2. Check for conflicts: `$MAIN_PATH/pming/worker.sh list --json` — if another worker is already on this task, warn the user and ask to confirm
3. Update its status to `in_progress`
4. Register: `$MAIN_PATH/pming/worker.sh claim B-XXX task B-XXX work/B-XXX`
   - If output starts with `CONFLICT:`, another worker has it. Warn user and ask to confirm.

### Story (S-XXX)
1. Read the story file (check `pming/stories/pending/S-XXX.md`)
2. Find all tasks in this story: glob `pming/tasks/pending/B-*.md`, filter by `story: S-XXX`
3. Sort by ID number
4. Get active workers: `$MAIN_PATH/pming/worker.sh list --json` — skip tasks already claimed
5. **Skip `ops` and `business` category tasks** — these require human action
6. Find first unclaimed code task with status `todo` and set it to `in_progress`
7. Update story status to `active` if currently `backlog` or `todo`
8. Register: `$MAIN_PATH/pming/worker.sh claim B-XXX story S-XXX work/B-XXX`

### Epic (E-XXX)
1. Read the epic file (check `pming/epics/pending/E-XXX.md`)
2. Find all tasks in this epic: glob `pming/tasks/pending/B-*.md`, filter by `epic: E-XXX`
3. Sort by story order (S-XXX number), then task ID within story
4. Get active workers: `$MAIN_PATH/pming/worker.sh list --json` — skip tasks already claimed
5. **Skip `ops` and `business` category tasks** — these require human action
6. Find first unclaimed code task with status `todo` and set it to `in_progress`
7. Update epic status to `active` if currently `backlog`
8. Register: `$MAIN_PATH/pming/worker.sh claim B-XXX epic E-XXX work/B-XXX`

### Resume (no args)
1. Check for an existing worker entry: `$MAIN_PATH/pming/worker.sh info`
   - If exit code 0 (entry found): parse the JSON output to get `task` and `branch`
   - Check if a worktree already exists for this task (`.worktrees/<task-id>/`)
   - If worktree exists, `cd` into it
   - If the task's status field shows `pending` (two-phase claim not yet activated): run `$MAIN_PATH/pming/worker.sh activate`
   - If the task is still `in_progress`, resume working on it (skip to Step 4 if worktree exists, Step 3 if not)
   - If the task is `done`, find next `todo` task in the group (respecting mode/target) and start it
2. If `worker.sh info` returns exit code 1 (no entry): use **conflict-aware auto-selection** (see Step 2a)

### Step 2a: Conflict-aware auto-selection

When no specific task/story/epic is given and no active session exists:

1. Read all pending tasks: glob `pming/tasks/pending/B-*.md`
2. Get active workers: `$MAIN_PATH/pming/worker.sh list --json`
3. Filter candidates:
   - Exclude tasks already claimed by active workers
   - **Skip `ops` and `business` category tasks** — these require human action, not code
   - **Tier 1 (safest):** tasks in stories AND epics that no active worker is touching
   - **Tier 2:** tasks in different stories but same epic as an active worker
   - **Tier 3:** tasks in the same story as an active worker (warn: may cause merge conflicts)
4. Within the best available tier, apply priority ordering: urgent > high > medium > low, then lowest B-XXX number
5. If ALL remaining tasks are Tier 3 or claimed, warn the user: "All remaining tasks overlap with active workers. Pick manually or wait for a worker to finish."
6. Register the selected task: `$MAIN_PATH/pming/worker.sh claim B-XXX task B-XXX work/B-XXX`

## Step 3: Spin up worktree

Create an isolated branch and worktree for this work session:

1. Derive branch name: `work/<task-id>` (e.g., `work/B-042`)
2. Check if worktree already exists: `git worktree list | grep <task-id>`
3. If it exists, `cd` into it and skip to Step 4
4. If not:
   ```bash
   git worktree add .worktrees/<task-id> -b work/<task-id>
   ```
5. `cd` into `.worktrees/<task-id>`
6. Write UUID to worktree: after creating the worktree, persist the UUID from the claim step:
   ```bash
   # The UUID was captured from the claim output: "OK <uuid>"
   echo "<uuid>" > .worktrees/<task-id>/.worker-uuid
   ```
7. Confirm to user: "Worktree ready on branch `work/<task-id>`"

## Step 4: Plan

Read the task and plan the approach inline:

1. **Show the task**: Display full task content including description and acceptance criteria
2. **Show hierarchy context**: Which epic and story, what comes before/after
3. **If working through a group**: Show progress (e.g., "Task 3/6 in S-003")
4. **Read relevant source files** mentioned in the task
5. **Produce a step-by-step implementation plan** with specific files and changes
6. Proceed to Step 4b (Codex plan review)

## Step 4b: Codex plan review

Before implementing, send the plan to Codex for a quick sanity check. This catches architectural violations and design issues before code is written — much cheaper to fix at this stage.

1. Write the plan to a temp file with this prompt:

   ```
   You are reviewing an implementation plan for a task in the Pilotea codebase (a web application, Node.js/TypeScript).

   You have read-only access to the full repo. Read `.claude/codex-guide.md` FIRST for efficient navigation, then read `CLAUDE.md` for architecture boundary rules.

   ## Task
   {TASK DESCRIPTION AND ACCEPTANCE CRITERIA}

   ## Plan
   {STEP-BY-STEP PLAN FROM STEP 4}

   ## Review Instructions
   1. Check the plan against architecture boundary rules in CLAUDE.md (engine vs flows vs underwriting vs banking-core)
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
     -C "$(pwd)" < /tmp/pilotea-plan-review.txt && \
   cat "$OUTFILE"
   rm -f "$OUTFILE" /tmp/pilotea-plan-review.txt
   ```

3. **If PROCEED**: Show Codex's assessment briefly, move to Step 5.
4. **If REVISE**: Read the concerns, revise the plan to address them, then move to Step 5. Do NOT re-run the review — one pass is enough.
5. **If Codex fails** (timeout, CLI missing, error): Warn briefly ("Codex plan review unavailable — proceeding"), move to Step 5. This step is advisory, not blocking.

## Step 5: Implement

Execute the plan:

1. **If category is `code`**:
   - Follow the plan step by step
   - Run tests frequently as you go
   - When complete, run `pnpm test` and `pnpm lint` to verify
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
   codex exec - --sandbox read-only --output-last-message /tmp/pilotea-impl-review.json -C "$(pwd)" <<'EOF'
   Review the uncommitted changes in this repository. Focus on bugs, data integrity risks, and missing error handling. Be brief — list only material issues.
   EOF
   cat /tmp/pilotea-impl-review.json
   rm -f /tmp/pilotea-impl-review.json
   ```

2. **If the review is clean**: Tell the user "Implementation check passed. Ready to ship." and stop.
3. **If issues are found**: Fix them, re-run `pnpm test` and `pnpm lint`, then tell the user it's ready to ship.
4. **If Codex fails**: Warn briefly ("Implementation check unavailable"), tell the user it's ready to ship. The `/ship` review will still gate quality.

## Important notes
- Always update the task file's `status` field to `in_progress` when starting
- Worker coordination is handled by `pming/worker.sh` — never read/write `pming/.workers` directly
- When starting a group, also update the parent story/epic status to `active`
- If a task is already `in_progress`, confirm whether to resume it or switch
- If a task is already `done`, skip it and find the next one
- Worktrees live under `.worktrees/` — ensure this directory is in `.gitignore`
- On `/ship`, the ship skill handles merging the worktree's feature branch back to main
- **All `worker.sh` calls use `$MAIN_PATH/pming/worker.sh`** — works from any worktree
