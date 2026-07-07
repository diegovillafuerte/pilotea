---
name: plan-session
description: Run a planning session. Reviews current state of all work, analyzes priorities, surfaces stale or blocked items, and helps decide what to work on next. Good for daily check-ins or weekly planning.
argument-hint: [daily | weekly]
---

# Planning Session

Help the user plan their work by reviewing current state and making recommendations.

## Gather state

1. Read all task frontmatter from `pming/tasks/pending/B-*.md` and `pming/tasks/done/B-*.md`
2. Read all story frontmatter from `pming/stories/pending/S-*.md` and `pming/stories/done/S-*.md`
3. Read all epic frontmatter from `pming/epics/pending/E-*.md` and `pming/epics/done/E-*.md`
4. Resolve main worktree path: `MAIN_PATH=$(git worktree list --porcelain | head -1 | sed 's/worktree //')`
5. Get active workers: `$MAIN_PATH/pming/worker.sh list --json`
6. Check recent git log (`git log --oneline -20`) for context on recently completed work

## Analysis sections

### 1. Active workers
- Show all entries from `worker.sh list`:
  ```
  ### Currently Active
  ● Worker a3f9: B-019 "Build Incode client" (S-006, E-002, since 10:00)
  ● Worker c7b2: B-025 "Create Buro client" (S-008, E-003, since 10:05)
  ```
- If no workers are active, say so

### 2. Current status
- What's `in_progress` right now? (both from `worker.sh list` and task status fields)
- Any tasks that have been `in_progress` for a while without completion?

### 3. Recently completed
- Tasks marked `done` (cross-reference with git log)
- Stories or epics completed

### 4. Velocity

Calculate throughput metrics to inform planning:

1. **Recent throughput** from git log:
   ```bash
   # Tasks shipped this week (commits that mention task IDs)
   git log --oneline --since="7 days ago" --grep="B-" | wc -l
   # Tasks shipped last week
   git log --oneline --since="14 days ago" --until="7 days ago" --grep="B-" | wc -l
   ```

2. **Telemetry data** (if `pming/.telemetry.jsonl` exists):
   - Count `ship_result` events from the last 7 days
   - Compare to previous 7 days
   - Note first-attempt pass rate if available

3. Display:
   ```
   ### Velocity
   This week: 8 tasks shipped (up from 5 last week)
   Daily avg: 1.6 tasks/day
   Trend: improving
   ```

   If telemetry data is available, also show:
   ```
   Ship pass rate: 82% first-attempt
   ```

   If no data exists for either source, skip this section silently.

### 5. What's next by priority
- Group remaining `todo` and `backlog` tasks by priority
- Identify the critical path: which tasks unblock the most other work
- Reference the dependency chain: E-001 → E-002 → E-004 → E-006
- **Flag which tasks are safe to parallelize** with active workers (different story/epic = safe)

### 6. Blockers and dependencies
- Business/ops tasks blocking code work (e.g., account signups blocking integration clients)
- Cross-epic dependencies
- Any external blockers (Meta verification, partner accounts)

### 7. Recommendations

Based on the analysis, recommend concretely:

**If daily planning (default):**
- "Here's what I'd focus on today" — 1-3 specific tasks
- Consider mix of code + business/ops to unblock future work
- If parallel workers are active, recommend tasks that don't conflict with them

**If weekly planning:**
- "Here's what's achievable this week" — a set of tasks or a full story
- Identify the most impactful story to complete this week
- Flag any business/ops tasks that should happen in parallel
- Suggest optimal parallelization: "With 2-4 workers, tackle these stories simultaneously: ..."

## Display format

Keep it conversational, opinionated, and actionable:

```
## Planning — 2026-03-17

### Active Workers
● a3f9: B-019 "Build Incode client" (S-006, E-002)
● c7b2: B-025 "Create Buro client" (S-008, E-003)

### Recently Completed
✓ B-001, B-002, B-003: WhatsApp Flow JSONs → S-001 complete ✓

### Recommended Next
1. 🔴 B-009: Add Redis on Render (urgent, ops — unblocks all async work)
2. 🟠 B-049-B-052: Sign up for sandbox accounts (high, business — unblocks E-003)
3. 🟠 B-016: HMAC-sign data-exchange tokens (high, security)

### Parallel Opportunities
Safe to run alongside active workers (different stories/epics):
  - S-005: Security hardening (E-002) — 3 tasks, no overlap with active work
  - S-019: Integration partner accounts (E-007) — business tasks, always safe

### Blockers
⚠ E-003 (Integration Clients) blocked until sandbox accounts created (B-049-B-052)
⚠ B-004 (Meta Business verification) — status unknown, check before registering flows

### Suggested Focus
Complete S-003 (async job processing) + S-005 (security hardening) → E-002 at 90%
```

## Guidelines
- Be opinionated about priorities — recommend, don't just list
- Flag tasks that seem stuck or stale
- Consider the critical path
- Balance code tasks with business/ops tasks that unblock future work
- Default to daily planning if no argument given
- Account for the fact that business/ops tasks (signups, partnerships) can happen outside coding sessions
- **When parallel workers are active, recommend tasks that can safely run alongside them**
