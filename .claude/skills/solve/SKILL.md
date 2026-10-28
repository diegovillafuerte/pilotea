---
name: solve
description: Diagnose and fix a bug or issue described by the user. Accepts a description (text, images, files). Diagnoses first, then plans and fixes in an isolated worktree. Use when something is broken and needs fixing now.
argument-hint: <description of the bug or issue>
---

# Solve: Diagnose and Fix

Immediate bug-fix flow. Diagnose the problem, plan the fix, implement it — all in an isolated worktree.

## Step 1: Understand the report

From $ARGUMENTS and any attached images/files, extract:
- **Symptom**: What's happening? (error message, wrong behavior, crash, visual glitch)
- **Context**: Where does it happen? (endpoint, flow state, UI screen, specific input)
- **Reproduction**: How to trigger it? (user steps, specific data, conditions)

Display a one-line summary: **"Investigating: [symptom] in [context]"**

## Step 2: Diagnose

This is the critical step. Do NOT skip to fixing. Understand the root cause first.

1. **Trace from the symptom inward:**
   - If an error message or stack trace is provided, follow it to the source file and line
   - If a screenshot or visual bug, identify the rendering/data path responsible
   - If behavioral (wrong output, missing data), trace the data flow from input to output
2. **Map the relevant code** — have a `scout` trace the call chain (`path:line` and a one-line role per hop), then read yourself only the functions the diagnosis hinges on. Don't stop at the file where the error surfaces.
3. **Check recent changes** — `git log --oneline --stat -20` to see if this was introduced recently. If a commit looks suspect, write its diff to a file (`git show <sha> > /tmp/kompara-agents/solve/<sha>.diff`) and have a `scout` summarize it instead of printing it here.
4. **Form a hypothesis** — state it clearly: "The bug is caused by X because Y"
5. **Verify the hypothesis** — find confirming evidence in the code. If your first hypothesis doesn't hold, form another. Do not proceed until you have a confirmed root cause.

Present the diagnosis to the user:
- **Root cause**: one sentence
- **Evidence**: specific file:line references
- **Blast radius**: what else might be affected

## Step 3: Spin up worktree

1. Derive a short slug from the bug (e.g., `fix-missing-guard-prequal`, `fix-spei-timeout`)
2. Create the worktree, move into it, and register it in worker coordination. Run this as one command, since shell variables don't carry over to the next one. The worktree goes in `$MAIN_PATH/.worktrees/` (the main checkout's), where `worker.sh` looks for it, even when this session runs in another worktree (a relative `.worktrees/` would nest it there). The branch starts from a freshly fetched `origin/main`, the `main` that `/ship` merges it into, never from this checkout's HEAD, whose commits `/ship` would merge too:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   git fetch origin main &&
     git worktree add --no-track -b fix/<slug> "$MAIN_PATH/.worktrees/<slug>" origin/main &&
     cd "$MAIN_PATH/.worktrees/<slug>" &&
     "$MAIN_PATH/pming/worker.sh" claim <slug> task <slug> fix/<slug>
   ```
   If the fetch fails, tell the user and stop. If `fix/<slug>` already exists (an earlier session's), git refuses and the block creates nothing. If a `.worktrees/<slug>` folder is in the way, git refuses it too, but may first create `fix/<slug>` at `origin/main`, with nothing on it. Either way, leave both alone, pick another slug and run the block again.
3. Check that the shell stayed in the worktree. Claude Code keeps a `cd` only into the session's folders. A session started in a Claude Code worktree (`.claude/worktrees/<name>`) doesn't have `.worktrees/` among them, so the block's output ends with `Shell cwd was reset to …` and the shell is back in the session's own checkout:
   ```bash
   MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
   WT="$MAIN_PATH/.worktrees/<slug>"
   if [ "$(git rev-parse --show-toplevel)" = "$WT" ]; then echo "IN: $WT"; else echo "OUTSIDE: the shell is in $(pwd -P), not in $WT; add $MAIN_PATH/.worktrees to the session"; fi
   ```
   If it prints `OUTSIDE:`, follow `.claude/skills/_shared/task-worktree-shell.md`: add `$MAIN_PATH/.worktrees` to the session, run `cd "<the worktree's path>"`, and run the check again. Never go on outside the worktree: the fix, its verify rows and `/ship` would act on the session's own checkout and branch. From here on, `cd` outside the session's folders only inside a subshell, and run `cd "<the worktree's path>"` again whenever a result shows `Shell cwd was reset to …`.

## Step 4: Plan the fix

Enter plan mode (use the EnterPlanMode tool):

1. Based on the diagnosis, list the specific changes needed
2. Consider side effects — will the fix break anything else?
3. Identify what tests to add or update to cover this case
4. Keep the fix minimal — solve the bug, don't refactor the neighborhood

Capture the complete plan text for Codex review in Step 4b. Proceed to Step 4b (do NOT present to user or exit plan mode yet).

## Step 4b: Codex plan review

Before presenting the fix plan to the user, send it to Codex for adversarial review. This catches misdiagnoses, missed side effects, and architectural violations before human review.

> **Codex execution follows the shared protocol in `.claude/skills/_shared/codex-review-protocol.md`.**
> Solve-specific parameters: `OUTPUT_SCHEMA_PATH=.claude/skills/solve/diagnosis-review-schema.json`, `TIMEOUT_MS=300000`, `MAX_ROUNDS=3`, `FALLBACK_BEHAVIOR=claude-agent`, `ON_REJECT=revise-and-resubmit`.

### 4b.1: Check Codex availability

Per shared protocol Step 1. If `CODEX_MISSING`: warn the user ("Codex CLI not installed -- run `/codex:setup` to install and authenticate, or falling back to Claude review"), then skip to substep 4b.3a (Claude fallback per shared protocol Step 3a).

### 4b.2: Build the review prompt

Construct the prompt per shared protocol Steps 2.1 and 2.2. Do NOT paste the full contents of CLAUDE.md or ARCHITECTURE.md -- Codex runs with read-only sandbox access to the repo and can read any file it needs.

**Solve-specific prompt template:**

```
You are an adversarial reviewer for the Kompara project. Your job is to find flaws in the diagnosis and fix plan below before any code is written. Be skeptical — assume the diagnosis might be wrong and the plan might have gaps.

You have read-only access to the full repo.

## How to navigate the codebase
Read `.claude/codex-guide.md` FIRST — it explains how to efficiently explore this repo. Then, for this review:
1. Read `CLAUDE.md` for project constraints and architecture rules
2. Read the specific source files cited in the diagnosis (don't read the whole codebase)
3. Use grep to verify claims about code behavior rather than reading entire modules
4. Only read `ARCHITECTURE.md` if the fix touches architecture boundaries
Do NOT read the entire codebase. Use the guide to navigate surgically to what matters for this review.

## Bug Report
{SYMPTOM, CONTEXT, AND REPRODUCTION DETAILS FROM STEP 1}

## Diagnosis
{ROOT CAUSE, EVIDENCE, AND BLAST RADIUS FROM STEP 2}

## Fix Plan
{COMPLETE PLAN TEXT FROM STEP 4}

## Review Instructions
1. Verify the diagnosis: Does the root cause actually explain the symptom? Read the cited source files and confirm.
2. Check for alternative explanations: Could something else cause this symptom?
3. Check the fix plan against architecture boundary rules (engine vs domain, layer dependencies)
4. Check for missing side effects — will the fix break anything else?
5. Check that the fix is minimal and doesn't include unnecessary refactoring
6. Check that test coverage is planned for the bug reproduction
7. Flag any fix steps that would require modifying engine code for domain-specific behavior

For each finding, specify:
- severity: "critical" (blocks implementation — wrong diagnosis, fix will break existing code, architectural violation) or "suggestion" (improvement opportunity — additional tests, edge cases, minor gaps)
- area: "diagnosis" (root cause is wrong or incomplete) or "fix_approach" (plan has issues)
- issue: what is wrong
- suggestion: how to fix it

Set verdict to "needs_changes" ONLY if there are critical findings. Suggestions alone = "lgtm".
Set diagnosis_valid to false ONLY if the root cause explanation is incorrect or incomplete.
```

### 4b.3: Execute Codex review

Per shared protocol Step 2.3. Write the assembled prompt to temp files using the trusted/untrusted split from shared protocol Step 2.1 (never embed user-provided text in heredocs). Execute with `timeout: 300000` (5 minutes).

Parse the output per shared protocol Step 2.4: JSON object with `verdict`, `diagnosis_valid`, `findings`, and `summary` fields.

If the command fails (non-zero exit, timeout, malformed output): warn the user ("Codex review failed -- falling back to Claude review"), then go to substep 4b.3a.

### 4b.3a: Claude fallback review

Per shared protocol Step 3a (`FALLBACK_BEHAVIOR=claude-agent`). Spawn a Claude subagent with the same review prompt from Step 4b.2, requesting JSON output matching the diagnosis-review schema. Continue to Step 4b.4 with the parsed result.

### 4b.4: Reconciliation loop (up to 3 rounds)

Per shared protocol Step 3 with `MAX_ROUNDS=3`. Process the Codex review result with these solve-specific behaviors:

**If `diagnosis_valid` is false:**
- Show the diagnosis findings to the user
- STOP and go back to Step 2 (re-diagnose) -- do not proceed with a fix based on a wrong diagnosis

**If verdict is `lgtm` (shared protocol: positive verdict):**
- Show the summary to the user
- If there are `suggestion`-severity findings, list them briefly as "Codex suggestions (non-blocking):" with one line per finding
- Proceed to substep 4b.5

**If verdict is `needs_changes` (shared protocol: negative verdict):**
- Per shared protocol Step 3.1: show ALL findings, filter to `critical` severity
- If no `critical` findings (verdict mismatch): treat as `lgtm`, proceed to 4b.5
- If `critical` findings and round 3: show findings, note "Maximum review rounds reached -- presenting plan with unresolved findings for your judgment", proceed to 4b.5
- If `critical` findings and rounds remain: revise the plan to address each critical finding, rebuild prompt, re-run Codex (per shared protocol reconciliation loop)

### 4b.5: Present to user for approval

Per shared protocol Step 3.2 (final disposition). Present the final plan to the user:

- If Codex reviewed and approved: "Plan reviewed by Codex (verdict: lgtm). Here's the fix plan:"
- If Codex reviewed with unresolved findings: "Plan reviewed by Codex with unresolved findings noted above. Here's the fix plan:"
- If Codex was skipped: present the plan without Codex context

Display the complete plan, then wait for user approval. Once approved, exit plan mode (use the ExitPlanMode tool).

## Step 5: Fix

Delegate the approved plan to the `coder` agent (Opus 5.5) with the worktree path, per the routing in `CLAUDE.md`. It must:

1. Make the code changes
2. Add or update tests that reproduce the bug and verify the fix
3. Run the AGENTS.md **Verify** rows for the areas it touched — the new test must pass and every existing check must still pass

## Important notes
- Diagnosis before action — resist the urge to jump to a fix before confirming root cause
- Minimal changes — fix the bug, don't improve adjacent code
- Worktrees live under `$MAIN_PATH/.worktrees/` — the `/ship` skill handles merging back to main
- After shipping the fix, release the worker and clean up the worktree:
  ```bash
  MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
  "$MAIN_PATH/pming/worker.sh" release --worktree
  ```
- If the diagnosis reveals the issue is NOT a bug (user error, config issue, expected behavior), say so and stop
