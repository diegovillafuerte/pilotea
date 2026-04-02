---
name: work-me
description: List outstanding ops-category tasks the user needs to complete manually, then walk through each one step-by-step with detailed instructions. Use when the user wants to knock out their ops backlog fast.
argument-hint: [B-XXX]
---

# Work Me — Ops Task Runner

Help the user blast through their `ops` (and `business`) category tasks — the ones that require human hands on a keyboard/dashboard/portal, not code.

## Step 1: Gather ops tasks

1. Glob all pending task files: `pming/tasks/pending/B-*.md`
2. Read each file's YAML frontmatter
3. Filter to tasks where `category: ops` OR `category: business`
4. Exclude tasks with `status: done` or `status: cancelled`
5. Sort by: priority (urgent > high > medium > low), then by ID number

## Step 2: Display the ops backlog

Show a clear overview:

```
# Ops Backlog — X tasks outstanding

| # | Task | Title | Priority | Status | Epic/Story |
|---|------|-------|----------|--------|------------|
| 1 | B-004 | Get Meta Business verification | high | todo | E-001 / S-002 |
| 2 | B-006 | Set WhatsApp Flow env vars | medium | todo | E-001 / S-002 |
...
```

If `$ARGUMENTS` contains a specific `B-XXX`, skip the overview and jump directly to that task in Step 3.

If no ops tasks remain, say so and exit.

## Step 3: Start with the first task (or specified task)

For each ops task, deliver a **complete, actionable walkthrough**:

### 3a. Show task context

- Display the full task content (title + body)
- Show which epic and story it belongs to, and WHY it matters (read the epic/story files for context)
- If other tasks depend on this one being done, mention that

### 3b. Research what's needed

Before giving instructions, gather the information needed to give precise guidance:

- Read any related source files, config files, or documentation referenced in the task
- Check environment variables, Render config, Meta API details, etc. from CLAUDE.md, memory, or project files
- Look at related code to understand what the ops task is configuring or enabling
- Check `techdebt.md` for any related notes

### 3c. Give step-by-step instructions

Provide **exact, copy-pasteable instructions** — not vague guidance. The goal is to minimize the user's thinking and maximize their doing:

- **URLs**: Give the exact URLs to visit (dashboards, portals, settings pages) when known
- **Commands**: Give exact CLI commands to run, with real values filled in (not placeholders when possible)
- **Values**: Tell them exactly what to enter in each field
- **Screenshots/locations**: Describe exactly where to click ("In the left sidebar, click Environment, then click Add Environment Variable")
- **Verification**: After each step, tell them how to verify it worked

Format as a numbered checklist so they can track progress:

```
## B-006: Enable WhatsApp Flows on Render

### Why this matters
WhatsApp Flows need `WA_FLOWS_ENABLED=true` set in production so the engine uses
structured forms instead of falling back to conversational collection.

### Steps

1. Open Render dashboard: https://dashboard.render.com/
2. Navigate to the production service
3. Click **Environment** in the left sidebar
4. Click **Add Environment Variable**
5. Set:
   - Key: `WA_FLOWS_ENABLED`
   - Value: `true`
6. Click **Save Changes**
7. The service will redeploy automatically

Note: Flow IDs are hardcoded in flow definitions (e.g., `WA_FLOW_IDS` in
`src/flows/credit-card/definition.ts`). No per-flow env vars needed.

### Verify
- Check Render logs for a clean startup
- Send a test message on WhatsApp that triggers address collection
- Confirm the WhatsApp Flow form appears instead of conversational fallback
```

### 3d. Interactive guidance

After presenting the steps:

- Ask: **"Ready? I'll wait while you work through these. Let me know when each step is done or if you hit a snag."**
- When the user reports progress or asks a question, provide immediate, specific help
- If they hit an error, diagnose it and give the exact fix
- If a step requires information you can look up (env var values, API keys, config), do that lookup for them

### 3e. Confirm completion

When the user says they're done:

1. If there's a way to verify programmatically (e.g., check env vars via Render API, test an endpoint), do it
2. Ask the user to confirm: **"All good? Want me to mark B-XXX as done?"**
3. If confirmed:
   - Update the task file: set `status: done`
   - Move the file from `pming/tasks/pending/` to `pming/tasks/done/`
   - Update parent story/epic if all their tasks are now done

## Step 4: Advance to next task

After completing a task:

1. Show: **"Done! X ops tasks remaining."**
2. Show the next task's title and priority
3. Ask: **"Moving on to B-YYY? Or want to stop here?"**
4. If they continue, loop back to Step 3 for the next task

## Guidelines

- **Speed is the goal.** Be concise but complete. Don't explain what ops tasks are — just help them do it.
- **Do the research so they don't have to.** Read config files, check memory for credentials/URLs, look up whatever context makes the instructions more precise.
- **Real values over placeholders.** If you can determine the actual value (from code, config, memory), use it. Only use `<placeholder>` when the value truly requires human lookup.
- **Group related tasks.** If two ops tasks touch the same system (e.g., both are Render env vars), mention that they can batch them.
- **Don't block on perfect.** If you're unsure about an exact URL or value, give your best guess and flag it: "This should be at X — verify before proceeding."
- **Track state in conversation.** No need to use .workers — ops tasks are done interactively with the user, not in parallel worktrees.
