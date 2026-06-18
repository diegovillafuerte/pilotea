---
name: seenext
description: Preview the next task that /work would pick up, without changing any status. Shows task details, its place in the hierarchy, and what's coming after.
---

# See Next Task

Show what would be picked up next by `/work`, without modifying anything.

## Step 0: Resolve main worktree path

```bash
MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')
```

## Logic

1. Check for own worker entry: `$MAIN_PATH/pming/worker.sh info`
   - If exit code 0: parse JSON for task, mode, target — "what am I working on"
   - If exit code 1: no active session
2. Get all active workers: `$MAIN_PATH/pming/worker.sh list --json`
3. If own worker exists and current task is `in_progress`: show that task (it's what you're on)
4. If own worker exists and current task is `done`: find the next `todo` task in the group, skipping tasks claimed by other workers
5. If no own worker entry: find the highest-priority `todo` task globally using conflict-aware selection:
   - Exclude tasks claimed by active workers
   - Prefer tasks in different stories/epics from active workers
   - Priority order: urgent > high > medium > low
   - Within same priority: lower B-XXX number first

## Display

First, show active workers (if any):

```
Active workers:
  ● You (a3f9): B-019 "Build Incode client" — S-006, E-002
  ● Worker c7b2: B-025 "Create Buro client" — S-008, E-003
```

Then show the next task:

```
Next up: B-010 "Install BullMQ and create engine queue module"
  Epic:     E-002 Infrastructure Foundations
  Story:    S-003 Async job processing
  Priority: urgent | Category: code | Status: todo
  Parallel: Safe — different story from all active workers

  Add BullMQ dependency. Create src/engine/queue/ with queue.ts (setup,
  connection), worker.ts (job processing), types.ts (job payload types).
```

If working through a group, also show progress:
```
  Working through: S-003 Async job processing (2/6 tasks done)
  Up after this: B-011 "Implement resumeWorkflowRun"
```

If no tasks are available:
```
No pending tasks found. All caught up!
Use /task to create new work items or /roadmap to review the project.
```
