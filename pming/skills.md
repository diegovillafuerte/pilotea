# Project Management Skills

Skills for managing the Pilotea project roadmap. All data lives in `pming/` as markdown files with YAML frontmatter.

## Hierarchy

```
Epic (pming/epics/{pending,done}/E-XXX.md)       Large initiative, spans multiple stories
  └── Story (pming/stories/{pending,done}/S-XXX.md)   Coherent capability, contains tasks
        └── Task (pming/tasks/{pending,done}/B-XXX.md)      Concrete work item (type: task)
        └── Bug (pming/tasks/{pending,done}/B-XXX.md)       Defect (type: bug, same numbering)
```

## Folder structure

Each of `epics/`, `stories/`, `tasks/` has two subdirectories:
- **`pending/`** — active items (backlog, todo, in_progress). Read these when checking what's on the plate.
- **`done/`** — completed or cancelled items. Immutable historical records. Only read when you need context on past work.

When a task/story/epic is completed, move the file from `pending/` to `done/`.

## Statuses

| Status | Meaning |
|--------|---------|
| `backlog` | Captured, not yet planned |
| `todo` | Planned, ready to pick up |
| `in_progress` | Actively being worked on |
| `done` | Completed |
| `cancelled` | Won't do |

## Priorities

`urgent` > `high` > `medium` > `low`

## Categories

| Category | Use for |
|----------|---------|
| `code` | Technical implementation (default) |
| `business` | Partnerships, legal, non-technical |
| `ops` | Infrastructure, deployment, setup |

## Available Skills

### `/task` — Create a new work item
Interprets your description, classifies the type, determines where it fits in the hierarchy, asks clarifying questions if needed, and creates it.

```
/task Add rate limiting to API endpoint
/task bug: Page crashes on mobile viewport
/task story: User can view trip earnings breakdown
/task epic: Driver Dashboard MVP
```

### `/tasks` — List and filter work items
View tasks with optional filtering.

```
/tasks                          # all active (not done/cancelled)
/tasks in_progress              # what's being worked on
/tasks E-002                    # tasks in an epic
/tasks S-003                    # tasks in a story
/tasks --priority high          # by priority
/tasks --category business      # by category
```

### `/seenext` — Preview the next task
Shows what `/work` would pick up, without changing anything.

### `/work` — Start working
Picks up a task, updates status, loads context, explores code, and begins.

```
/work                           # resume current or pick next
/work B-042                     # start specific task
/work S-003                     # work through a story sequentially
/work E-002                     # work through an epic sequentially
```

When working through a story or epic, `/done` automatically advances to the next task.

### `/done` — Complete a task
Marks done, verifies tests/lint for code tasks. Advances to next task if in a group.

```
/done                           # complete current task
/done B-042                     # complete specific task
```

### `/roadmap` — View project roadmap
Bird's-eye view of all epics, stories, and completion progress.

### `/plan` — Planning session
Reviews priorities, surfaces blockers, recommends what to work on next.

```
/plan                           # daily planning (default)
/plan weekly                    # weekly scope
```

## Parallel work

The system supports up to 4 concurrent Claude Code sessions working on different tasks simultaneously.

### How it works

State is tracked in `pming/.workers` — a multi-slot file where each active session registers itself. All skills read/write this file at the **main worktree's absolute path** (resolved via `git worktree list`) so that workers in different git worktrees share the same coordination state.

Each worker entry records:
- `worker`: random session ID
- `hostname` + `pid`: for stale detection (PID check on same machine, hostname for cross-computer)
- `task`, `mode`, `target`, `branch`: what it's working on and how

### Conflict avoidance

When `/work` auto-selects a task, it avoids conflicts with active workers:
1. Never picks a task already claimed by another worker
2. Prefers tasks in **different stories** from active workers (reduces merge conflicts)
3. Prefers tasks in **different epics** from active workers (safest isolation)

### Stale detection

On every `/work` invocation, stale entries are cleaned:
- Same hostname: checks if PID is still alive (`kill -0`)
- Different hostname: left alone (the other machine cleans its own)

### Cross-computer sync

`pming/.workers` is committed to git. When switching computers, commit + push on machine A, pull on machine B. Foreign-hostname entries are preserved until that machine cleans them.

### Usage

Open multiple terminals and run `/work` in each. Each instance automatically picks a non-conflicting task. `/ship` handles the case where main has moved (pulls latest, merges, re-runs tests).
