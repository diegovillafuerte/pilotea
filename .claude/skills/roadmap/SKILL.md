---
name: roadmap
description: View the high-level project roadmap showing all epics, their stories, and progress percentages. Provides a bird's-eye view of the entire project organized by initiative.
---

# Project Roadmap

Display the full project roadmap with progress tracking.

## How to build the view

1. Read all epic files: glob `pming/epics/pending/E-*.md` and `pming/epics/done/E-*.md`, read frontmatter
2. Read all story files: glob `pming/stories/pending/S-*.md` and `pming/stories/done/S-*.md`, read frontmatter
3. Read all task files: glob `pming/tasks/pending/B-*.md` and `pming/tasks/done/B-*.md`, read frontmatter only (just need status, epic, story)
4. Resolve main worktree path: `MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')`
5. Get active workers: `$MAIN_PATH/pming/worker.sh list --json`
6. Compute progress for each story: done tasks / total tasks
7. Compute progress for each epic: done tasks across all its stories / total
8. Sort epics by priority, then by ID

## Active workers

If `$MAIN_PATH/pming/worker.sh list` shows workers, show at the top:

```
Active workers:
  ● a3f9: B-019 "Build Incode client" (S-006, E-002)
  ● c7b2: B-025 "Create Buro client" (S-008, E-003)
```

## Display format

```
# Kompara Roadmap

## E-002: Infrastructure Foundations          ░░░░░░░░░░░░░░░░  0% (0/10)
   Priority: urgent | Status: backlog
   S-003: Async job processing                ░░░░░░░░░░░░░░░░  0% (0/6)
   S-004: Cloud media storage                 ░░░░░░░░░░░░░░░░  0% (0/1)
   S-005: Security hardening                  ░░░░░░░░░░░░░░░░  0% (0/3)

## E-001: WhatsApp Flows                      ██████░░░░░░░░░░ 38% (3/8)
   Priority: high | Status: active
   S-001: Flow JSON definitions               ████████████████ 100% (3/3) ✓
   S-002: Deployment and testing              ░░░░░░░░░░░░░░░░  0% (0/5)

## E-007: Business Development                ░░░░░░░░░░░░░░░░  0% (0/6)
   Priority: high | Status: backlog
   S-018: SOFOM partnership                   ░░░░░░░░░░░░░░░░  0% (0/1)  business
   S-019: Integration partner accounts        ░░░░░░░░░░░░░░░░  0% (0/5)  business

## E-003: Integration Clients                 ░░░░░░░░░░░░░░░░  0% (0/6)
   ...
```

Use `█` for filled progress and `░` for empty. 16 characters wide.
Show category tag only if not `code`.

For tasks currently being worked on by a parallel worker, annotate the story line:
```
   S-006: Identity and employment clients     ██░░░░░░░░░░░░░░ 13% (1/8)  ← worker a3f9
```

## Summary section

At the bottom:

```
---
Overall: 3/53 tasks done (6%)
Active workers: 2 | In progress: 2 | Todo: 47 | Backlog: 3 | Done: 3

By category: 42 code | 6 business | 5 ops

Critical path: E-001 → E-002 → E-004 → E-006
Next recommended: [highest priority todo task not claimed by a worker]
```
