---
name: ship
description: Adversarial Codex-powered review, commit, and push code changes. Runs tests and lint, then selects a review lane (fast/lean/full) based on change size and risk. Full review spawns parallel specialized reviewers; lean mode runs a single lightweight reviewer for simple changes.
argument-hint: [optional commit message hint]
---

# Ship: Codex Team Review + Commit + Push

Follow these steps exactly in order. Do NOT skip steps.

## Step 0: Resolve main worktree path

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

## Step 1: Pre-flight checks

Run these sequentially. If ANY fail, STOP and show errors.

1. `git status` — verify there are changes to commit (staged or unstaged modified/untracked files). If working tree is clean, tell the user and stop.
2. `pnpm lint` — must pass clean.
3. `pnpm test` — all tests must pass.

If lint or tests fail, show the output and STOP. Tell the user what failed.

## Step 1b: Simplify pass (for diffs > 100 lines)

Check the size of the pending changes:
```bash
git diff --stat | tail -1
```

If the total lines changed exceeds 100, run a quality pass before review:

1. Snapshot the current tree: `git stash push -m 'pre-simplify'` then `git stash pop` (this creates a stash entry as a restore point without changing the working tree).
2. Use the Skill tool to invoke `simplify`. This spawns parallel agents that check for code reuse opportunities, quality issues, and efficiency improvements, then auto-fixes them.
3. If `/simplify` made any changes, re-run `pnpm lint` and `pnpm test` to verify the fixes don't break anything.
4. If lint or tests fail after simplify's changes, restore the pre-simplify state: `git checkout stash@{0} -- .` then `git stash drop` and proceed without simplification. If tests passed, just `git stash drop` to clean up.

If the diff is <= 100 lines, skip this step silently.

## Step 2: Analyze changes

Run these in parallel:
- `git diff` and `git diff --cached` to capture changes to tracked files
- For each untracked file listed by `git status`, capture its content with `git diff --no-index /dev/null <file>` to generate a diff
- `git log --oneline -10` for recent commit style
- `git status` to list all changed and untracked files
- `git branch --show-current` for the current branch
- Read documentation files for the reviewers' context: `ARCHITECTURE.md`, `CLAUDE.md`, `techdebt.md`. If any flows were changed, also read the relevant flow's `knowledge-base.md` and `tone.md`.
- Read YAML frontmatter from all docs in `docs/` to build a source-to-doc mapping. For each doc, extract the `sources:` list and `last_verified:` date. This will be used in Step 3.1 to determine whether the Docs reviewer should run.

**Important:** `git diff` does not show untracked (new) files. You MUST capture new file contents separately so the reviewers see the complete picture.

Combine all diffs into a single `FULL_DIFF` text block for the reviewers.

## Step 3: Codex tiered review

> **Shared Codex protocol:** `.claude/skills/_shared/codex-review-protocol.md` defines the reusable execution skeleton (availability check, prompt assembly, execution, parsing, reconciliation, fallback). This section specifies ship-specific parameters and orchestration logic.

The review is tiered to balance thoroughness with speed. Three lanes exist:
1. **Fast lane** — pre-screen only (docs, config, PM files, tests with small diffs)
2. **Lean lane** — single lightweight reviewer with condensed checklist (simple code changes)
3. **Full review** — parallel specialized reviewers (large or critical-path changes)

A fast native pre-screen runs first to classify concerns. Lane selection follows in Step 3.2.

### 3.1: Native Codex pre-screen

Run the plugin's native review on the current changes. This is a fast general-purpose scan via the Codex app server.

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/codex-companion.mjs" review --wait
```
Set `timeout: 480000` (8 minutes).

If `CLAUDE_PLUGIN_ROOT` is not set, fall back to:
```bash
codex exec - --sandbox read-only --output-last-message /tmp/pilotea-prescreen.txt -C "$(pwd)" <<'EOF'
Review the uncommitted changes in this repo. Report material issues only — bugs, security risks, data integrity, architectural violations. Be brief.
EOF
cat /tmp/pilotea-prescreen.txt
rm -f /tmp/pilotea-prescreen.txt
```

Read the pre-screen output. Classify it as:
- **CLEAN**: No material issues found, or only minor style/naming comments
- **HAS_CONCERNS**: Material issues flagged (bugs, security, data integrity, etc.)

If the pre-screen fails (Codex error, timeout): treat as HAS_CONCERNS (proceed to full review).

### 3.2: Decide review depth

Check these conditions using the diff stats and changed file list from Step 2:

**Critical paths:** `src/banking-core/`, `src/underwriting/`, `src/db/migrations/`, `src/engine/machine/`, `src/engine/transport/`

**Full review (specialized reviewers) when ANY of:**
- Pre-screen classified as HAS_CONCERNS
- Total diff lines > 100
- Changed files touch any critical path above

**Lean lane (single lightweight reviewer) when ALL of:**
- Pre-screen classified as CLEAN
- Total diff lines > 50 and <= 100, OR files changed > 3 and <= 6
- No changes to critical paths above
- OR: total diff lines <= 50 AND files changed <= 3 AND at least one `.ts` file outside of docs/config

The lean lane runs a single condensed review using the Codex CLI (or Claude fallback) with the lean reviewer prompt (`.claude/skills/ship/reviewers/lean.md`). This covers security, architecture, and correctness in one pass with a shorter checklist, skipping the parallel specialized reviewer battery.

**Fast lane (pre-screen only) when ALL of:**
- Pre-screen classified as CLEAN
- Total diff lines <= 50
- Files changed <= 3
- Changes are limited to: docs (`docs/`, `*.md`), config files (`*.json`, `*.yaml`, `*.yml`, `*.toml`, `.eslintrc*`, `tsconfig*`), PM files (`pming/`), or test files (`test/`)

If **fast lane**: Skip to Step 3.6 (Log telemetry). The pre-screen is the sole review gate. Show the user: "Pre-screen passed. Fast lane — skipping specialized reviewers."

If **lean lane**: Continue to Step 3.2a.

If **full review**: Continue to Step 3.3.

### 3.2a: Lean review

Run a single lightweight review using the lean reviewer prompt:

1. Read `.claude/skills/ship/reviewers/lean.md`
2. Replace `{DIFF}` with the full diff from Step 2
3. Prefix with the navigation header from the shared protocol (Step 2.2)
4. Execute via Codex CLI (same as Step 3.4 but with a single reviewer):

```bash
OUTFILE="/tmp/pilotea-review-lean-out.json"
codex exec - \
  --sandbox read-only \
  --output-schema .claude/skills/ship/code-review-schema.json \
  --output-last-message "$OUTFILE" \
  -C "$(pwd)" < /tmp/pilotea-review-lean.txt && \
cat "$OUTFILE"
rm -f /tmp/pilotea-review-lean.txt /tmp/pilotea-review-lean-out.json
```

Set `timeout: 300000` (5 minutes — shorter than full review since it is a single reviewer).

If Codex is unavailable or the review fails, warn the user: "Lean review unavailable -- Codex failed. Run full review or ship without review?" Do NOT silently proceed. Unlike the full review path (where individual reviewer failures are tolerable because other reviewers still gate the ship), the lean lane has a single reviewer, so its failure means zero review coverage.

**Parse the output** as JSON. If verdict is PASS: show the user "Lean review passed." and skip to Step 3.6. If verdict is REJECT: show findings and follow the same gate logic as Step 4 (ask user to fix or override).

Show the user: "Lean mode — single lightweight review (skipping full adversarial battery)."

### 3.3: Determine which specialized reviewers to run

**Always run:** Security, Architecture, Correctness (3 reviewers)

**Conditional — UX reviewer:** If ANY changed file path contains: `src/flows/`, `src/orchestrator/`, `tone.md`, `knowledge-base.md`, `src/engine/renderer/`, `src/engine/transport/`

**Conditional — Docs reviewer:** If ANY changed file matches a doc's `sources:` YAML frontmatter pattern, or changes include `src/db/migrations/`. Pass the list of impacted docs in the prompt.

### 3.4: Prepare and launch specialized reviewers

> **Codex execution follows the shared protocol in `.claude/skills/_shared/codex-review-protocol.md`.**
> Ship-specific parameters: `OUTPUT_SCHEMA_PATH=.claude/skills/ship/code-review-schema.json`, `TIMEOUT_MS=480000`, `MAX_ROUNDS=1`, `FALLBACK_BEHAVIOR=warn-and-proceed`, `ON_REJECT=show-and-ask-user`.

For each reviewer to run:

1. Read the reviewer's prompt file from `.claude/skills/ship/reviewers/<name>.md`
2. Replace `{DIFF}` with the full diff. For Docs reviewer, also replace `{IMPACTED_DOCS}` with full doc content.
3. Include relevant context doc excerpts from Step 2 in the prompt (speeds up Codex — it won't need to read them).
4. Prefix each assembled prompt with the navigation header from the shared protocol (Step 2.2):
   ```
   You have read-only access to the full repo. Read `.claude/codex-guide.md` for efficient navigation.

   Review the following code changes. Return ONLY a JSON object matching the output schema — no other text, no markdown fences, just raw JSON:
   {"verdict":"PASS or REJECT","findings":[{"severity":"CRITICAL or ADVISORY","title":"...","location":"file:line","evidence":"...","why":"...","fix":"..."}],"summary":"..."}
   ```
5. Build prompt files per shared protocol Step 2.1 (split trusted/untrusted content into separate files, concatenate safely — never embed diffs in heredocs).
6. Write each assembled prompt to a temp file: `/tmp/pilotea-review-<name>.txt`

Launch ALL reviewers in parallel per shared protocol Step 2.3:
```bash
OUTFILE="/tmp/pilotea-review-<name>-out.json"
codex exec - \
  --sandbox read-only \
  --output-schema .claude/skills/ship/code-review-schema.json \
  --output-last-message "$OUTFILE" \
  -C "$(pwd)" < /tmp/pilotea-review-<name>.txt && \
cat "$OUTFILE"
```

Set `timeout: 480000` (8 minutes) for each. After all complete:
```bash
rm -f /tmp/pilotea-review-*.txt /tmp/pilotea-review-*-out.json
```

**Error handling per shared protocol Step 2.4:** If a reviewer fails (non-zero exit, timeout, empty output), treat as PASS with advisory: "[Reviewer] Codex review failed — review manually." Do NOT block ship.

### 3.5: Aggregate results

Per shared protocol Step 2.4 (parse output) and Step 3.1 (evaluate verdict):

1. **Parse each reviewer's output** as JSON. Extract from markdown fences if needed.
2. **Handle failures**: Malformed/empty → PASS with advisory note.
3. **Combine findings**: Tag each with reviewer name (e.g., "[Security]").
4. **Aggregate verdict**: ANY REJECT → aggregate REJECT. All PASS → aggregate PASS.
5. **If ALL failed**: "review unavailable" → Step 4's failure path.

### 3.6: Log telemetry

After aggregation, append ship result to telemetry (if the file exists):

```bash
TELEMETRY_FILE="$MAIN_PATH/pming/.telemetry.jsonl"
if [ -f "$TELEMETRY_FILE" ]; then
  # Log the aggregate result — include review_lane to track which path was taken
  echo '{"ts":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","event":"ship_result","task":"<TASK_ID>","verdict":"<PASS|REJECT>","review_lane":"<fast|lean|full>","reviewers":{"security":"<verdict|skipped>","architecture":"<verdict|skipped>","correctness":"<verdict|skipped>","ux":"<verdict|skipped>","docs":"<verdict|skipped>","lean":"<verdict|skipped>"},"iterations":<N>,"files_changed":<N>}' >> "$TELEMETRY_FILE"
fi
```

Replace placeholders with actual values from the review results. `review_lane` is one of `fast`, `lean`, or `full` based on the path taken in Step 3.2. For fast lane, all specialized reviewers are `skipped`. For lean lane, set `lean` to the lean reviewer's verdict and all specialized reviewers to `skipped`. For full review, set `lean` to `skipped`. `iterations` starts at 1 and increments if the code goes through fix-and-re-review cycles (tracked by `/work-auto`; for manual `/ship` calls, always 1). `files_changed` is the count of changed files from `git diff --name-only`.

If any reviewer returned REJECT, also log each critical finding:
```bash
echo '{"ts":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","event":"ship_rejected","task":"<TASK_ID>","reviewer":"<reviewer_name>","reason":"<finding_title>"}' >> "$TELEMETRY_FILE"
```

Build and display a consolidated report. The report format adapts to the review lane:

**Full review report:**
```
## Review Results (full)

| Reviewer     | Verdict | Critical | Advisory |
|--------------|---------|----------|----------|
| Security     | PASS    | 0        | 1        |
| Architecture | PASS    | 0        | 0        |
| Correctness  | REJECT  | 1        | 0        |
| UX           | skipped | -        | -        |
| Docs         | PASS    | 0        | 1        |

### Critical Findings
1. [Correctness] Floating promise in webhook handler — src/engine/transport/webhook.ts:45 ...

### Advisory Findings
1. [Security] Broad error message could include less detail — src/flows/credit-card/integrations/moffin.ts:112 ...
```

**Lean review report:**
```
## Review Results (lean)

| Reviewer | Verdict | Critical | Advisory |
|----------|---------|----------|----------|
| Lean     | PASS    | 0        | 1        |

### Advisory Findings
1. [Lean] Consider adding test for new helper — src/utils/format.ts:22 ...
```

**Fast lane report:**
```
## Review Results (fast lane)
Pre-screen passed. No specialized review needed.
```

## Step 4: Gate decision

Based on the aggregate review results:

- **PASS** (all reviewers passed, with or without ADVISORY findings):
  Show any advisory findings briefly, then proceed to Step 4.1.

**4.1: Update impacted docs**

If the Docs reviewer found ADVISORY or CRITICAL findings about stale docs:
1. For each impacted doc, update the content to match the code changes
2. Update `last_verified:` date in the doc's frontmatter to today
3. Stage the updated docs alongside the code changes

If no doc findings, skip this step and proceed to Step 5.

- **REJECT** (any reviewer found CRITICAL issues):
  Show ALL findings clearly to the user.
  Ask: "Reviewers found critical issues. Fix first, or override and ship anyway?"
  - If fix → STOP
  - If override → proceed to Step 5

- **Review unavailable** (all Codex reviewers failed):
  Tell the user the review was skipped. Ask whether to proceed without review or retry.

## Step 5: Commit

1. Stage changed files by name (NEVER use `git add -A` or `git add .`). Do not stage files that look like they contain secrets (.env, credentials, tokens).
2. Draft a commit message:
   - Use the user's hint from `$ARGUMENTS` if provided
   - Match the style of recent commits from the git log
   - Focus on WHY over WHAT
   - Keep the first line under 72 characters
3. Commit using a HEREDOC.

## Step 6: Merge into main and push

1. Get current branch via `git branch --show-current`.

2. **If already on `main`:**
   - `git push origin main`
   - Log activity (see 6.4 below)

3. **If on a feature branch:**
   a. **Acquire the merge lock** to prevent parallel ship collisions:
      ```bash
      $MAIN_PATH/pming/worker.sh merge-lock acquire
      ```
      If this times out, tell the user another worker is merging. Wait or retry.

   b. `git checkout main`
   c. `git pull origin main` — **critical: fetches changes merged by parallel workers**

   c2. **Read recent activity from parallel workers:**
      ```bash
      $MAIN_PATH/pming/worker.sh recent-activity 10
      ```
      If entries exist, show a brief one-line-per-entry summary: "Recent changes on main:" with task ID, title, and key files for each. This is informational — does not gate the merge.

   d. `git merge <branch-name>`
   e. If merge conflicts:
      - **Release the merge lock immediately**: `$MAIN_PATH/pming/worker.sh merge-lock release`
      - Show conflicting files clearly
      - STOP and tell the user: "Main has moved since your branch diverged (likely from a parallel worker). These files conflict: [list]. Please resolve manually."
   f. After successful merge, **re-run `pnpm test`** — two branches may each pass individually but break when combined
   g. If post-merge tests fail:
      - **Release the merge lock**: `$MAIN_PATH/pming/worker.sh merge-lock release`
      - STOP and explain: "Merge succeeded but tests fail — this branch may conflict with changes from a parallel worker. Fix the integration issues before pushing."
   h. `git push origin main`
   i. If push is rejected (another worker pushed between pull and push):
      - `git pull --rebase origin main`
      - Re-run `pnpm test`
      - If tests pass: `git push origin main`
      - If tests fail or push fails again: **Release merge lock** and STOP
   j. **Release the merge lock**: `$MAIN_PATH/pming/worker.sh merge-lock release`

4. **Log activity** (6.4):
   After successful push, log the shipped task to the activity log so parallel workers can see what changed:
   ```bash
   COMMIT=$(git rev-parse --short HEAD)
   $MAIN_PATH/pming/worker.sh log-activity "<task-id>" "<task-title>" "$COMMIT"
   git add pming/.activity-log 2>/dev/null || true
   ```
   The task ID and title come from the worker info (Step 7 reads this). If no worker entry exists (shipping from main directly), use the commit message first line as the title and "manual" as the task ID.
   The staged activity log will be committed with the next commit (PM state in Step 7, or a future ship).

5. Show `git log --oneline -3` to confirm.

**CRITICAL: Always release the merge lock, even on failure.** If any step in 3b-3i fails, release the lock before stopping. The merge lock must never be left held.

## Step 7: Auto-complete task

After a successful push, automatically complete the task:

1. Check for own worker entry: `$MAIN_PATH/pming/worker.sh info`
2. If a worker entry exists (exit code 0):
   - Parse the JSON output to get `task`, `mode`, `target`
   - Read the task file and update `status: done`
   - Move task file to `pming/tasks/done/` if in `pming/tasks/pending/`
   - Update parent story/epic status if all their tasks are now done
   - Handle group progression:
     - If working through a group (mode is `story` or `epic`): find remaining `todo` tasks, get active workers via `$MAIN_PATH/pming/worker.sh list --json`, skip tasks claimed by other workers, **skip `ops` and `business` category tasks**. If more code tasks remain, show the next one and ask "Continue with B-YYY?". If user confirms, update own worker entry: `$MAIN_PATH/pming/worker.sh update-task B-YYY work/B-YYY`, set B-YYY to `in_progress`, and start working on it.
     - If no more tasks or mode is `task`: release and clean up the worktree: `$MAIN_PATH/pming/worker.sh release --worktree`
   - Show: `Shipped and completed B-XXX: "[title]"`
3. If no worker entry found (e.g., shipping from main directly): skip this step

## Rules

- NEVER use `--force`, `--no-verify`, or `--no-gpg-sign`
- NEVER amend existing commits
- If anything fails unexpectedly, **release the merge lock first**, then STOP and explain — do not retry blindly
- Keep status updates brief (one line per step)
- The review is tiered: fast lane (pre-screen only), lean lane (single lightweight reviewer), or full review (parallel specialized reviewers). Lane selection is automatic based on diff size, file count, and whether critical paths are touched. All active reviewers must PASS for the ship to proceed. Never review the code yourself in the main conversation.
- Codex reviews need time — always set `timeout: 480000` on full review Bash tool calls that invoke `codex exec`, and `timeout: 300000` for lean review calls.
- If the `codex` CLI is not installed or not authenticated, warn the user and suggest running `/codex:setup`. Do NOT fall back to Claude agents — the user has opted for Codex-based reviews to preserve Claude tokens.
- Worker coordination is handled by `pming/worker.sh` — never read/write `pming/.workers` directly
