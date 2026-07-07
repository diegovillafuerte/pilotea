---
name: tasks
description: List and filter work items. Shows active tasks by default. Filter by status, epic, story, priority, category, or type. Use when checking what's on the plate or reviewing work.
argument-hint: [filter: status | epic-id | story-id | --priority P | --category C | --type T]
---

# List Tasks

Display tasks from `pming/tasks/` with optional filtering.

## How to list

1. Glob all task files: `pming/tasks/pending/B-*.md` and `pming/tasks/done/B-*.md`
2. Read each file's YAML frontmatter (just the `---` block, not the full body)
3. Also read epic names from `pming/epics/pending/E-*.md` and `pming/epics/done/E-*.md`, and story names from `pming/stories/pending/S-*.md` and `pming/stories/done/S-*.md` for display
4. Resolve main worktree path: `MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')`
5. Get active workers: `$MAIN_PATH/pming/worker.sh list --json` — to know which tasks are actively being worked on
6. Apply filters from $ARGUMENTS
7. Sort by: priority (urgent > high > medium > low), then by ID
8. Display grouped by epic and story

## Parsing filters from $ARGUMENTS

- **Status word**: `backlog`, `todo`, `in_progress`, `done`, `cancelled` → filter by status
- **Epic ID**: `E-XXX` → show tasks belonging to that epic
- **Story ID**: `S-XXX` → show tasks belonging to that story
- **`--priority` or `-p`**: `urgent`, `high`, `medium`, `low`
- **`--category` or `-c`**: `code`, `business`, `ops`
- **`--type` or `-t`**: `task`, `bug`
- **No arguments**: show all tasks where status is NOT `done` and NOT `cancelled`

Multiple filters can be combined.

## Display format

Group by epic, then by story within each epic:

```
## E-001: WhatsApp Flows (3/8 done)

### S-001: WhatsApp Flow JSON definitions (3/3 done) ✓
  ✓ B-001  Create CURP collection flow JSON              done     high
  ✓ B-002  Update loan config flow JSON for V1           done     high
  ✓ B-003  Update CLABE entry flow JSON                  done     high

### S-002: WhatsApp Flow deployment and testing (0/5 done)
  ○ B-004  Get Meta Business verification confirmed      todo     high     ops
  ○ B-005  Register flows in Meta Business Manager       todo     high     ops
  ◌ B-006  Set WhatsApp Flow env vars on Render          todo     medium   ops
  ○ B-007  Test each flow end-to-end on WhatsApp         todo     high
  ◌ B-008  Verify conversational fallback                todo     medium
```

Status indicators:
- `✓` done
- `●` in_progress
- `○` todo
- `◌` backlog
- `✕` cancelled

For tasks claimed by a parallel worker, show the worker ID:
```
  ● B-019  Build Incode identity verification client    in_progress  high   ← worker a3f9
  ● B-025  Create Buro de Credito API client            in_progress  high   ← worker c7b2
```

Only show the category tag if it's not `code` (since code is the default).

## Summary

At the end, show:
```
Total: X tasks | Y in progress (Z workers active) | W todo | V done | U backlog
```

If filtered, also show what filter is active:
```
Showing: E-002 tasks with priority >= high
```
