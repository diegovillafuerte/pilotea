---
name: telemetry
description: Analyze harness telemetry — skill usage frequency, ship quality metrics, throughput trends, and actionable recommendations. Use to understand how the agentic development system is performing.
argument-hint: [7d | 14d | 30d | all]
---

# Telemetry: Harness Analytics

Analyze the agentic development telemetry to surface usage patterns, quality trends, and recommendations.

## Step 1: Determine time window

From $ARGUMENTS:
- `7d` (default): last 7 days
- `14d`: last 14 days
- `30d`: last 30 days
- `all`: entire history

Calculate the cutoff timestamp for filtering.

## Step 2: Load telemetry data

Read `pming/.telemetry.jsonl`. Each line is a JSON event:

```jsonl
{"ts":"...","event":"skill_invoked","skill":"ship"}
{"ts":"...","event":"ship_result","task":"B-220","verdict":"PASS","reviewers":{"security":"PASS","architecture":"PASS","correctness":"PASS"},"iterations":1,"files_changed":4}
{"ts":"...","event":"ship_rejected","task":"B-221","reviewer":"architecture","reason":"boundary violation"}
{"ts":"...","event":"work_auto_skip","task":"B-222","reason":"ops category"}
{"ts":"...","event":"work_auto_session","tasks_completed":5,"tasks_skipped":1,"duration_min":45}
```

Filter events to the selected time window by comparing `ts` to the cutoff.

If the file doesn't exist or is empty, report: "No telemetry data yet. Telemetry collects automatically when skills are invoked via the PostToolUse hook. Run a few skills and check back." and STOP.

## Step 3: Compute metrics

### 3.1: Skill usage

For each unique `skill` value in `skill_invoked` events:
- Count invocations within the time window
- Find the most recent invocation date
- Calculate days since last use

Also check for skills that are defined but have ZERO invocations. To find defined skills:
```bash
ls .claude/skills/ | sort
ls .claude/commands/ | sort
```

Sort output by invocation count (descending), then alphabetically for zero-count skills.

### 3.2: Ship quality (from `ship_result` and `ship_rejected` events)

- **First-attempt pass rate:** count of `ship_result` events with `iterations=1` and `verdict=PASS` / total `ship_result` events
- **Average iterations to pass:** mean of `iterations` field across all `ship_result` events
- **Rejection breakdown:** aggregate `ship_rejected` events by `reviewer` field — which reviewer catches the most issues?
- **Top rejection reasons:** aggregate `ship_rejected` events by `reason` field — what keeps failing?

If fewer than 3 `ship_result` events exist, note "Insufficient ship data for quality analysis."

### 3.3: Throughput

Using `skill_invoked` events where skill is `done` or `ship`:
- Count per calendar day within the window
- Calculate daily average
- Compare last 7d average to previous 7d average for trend direction

### 3.4: Work-auto efficiency (from `work_auto_session` and `work_auto_skip` events)

- Total autonomous sessions
- Tasks completed vs skipped (aggregate)
- Skip reasons breakdown

## Step 4: Recommendations

Generate specific, data-backed recommendations:

**Unused skills (>14 days idle):**
- Flag each with days since last use
- Distinguish between "should be periodic" skills (/cso, /housekeeper, /canary — recommend scheduling) and "on-demand" skills (/rollback, /solve — idle is fine)
- Skills with ZERO lifetime invocations: "Evaluate if this skill is still needed."

**Quality patterns:**
- If first-attempt pass rate < 70%: "Consider tightening lint rules — [top rejection reason] accounts for X% of rejections"
- If one reviewer catches >40% of all rejections: "The [reviewer] reviewer is doing heavy lifting. Convert its most common finding ([reason]) into a lint rule."
- If average iterations > 2.0: "Review iterations are high — tasks may need better scoping or the review criteria may be too strict."

**Throughput:**
- If throughput is declining (last 7d < previous 7d by >30%): flag with possible causes
- If work-auto skip rate is >30%: "High skip rate in autonomous sessions — check if tasks need better specs or if there's a systemic issue."

**Automation:**
- If /housekeeper or /cso haven't run in >14 days: "Schedule these via /schedule for regular automated runs."

## Step 5: Display

```
## Telemetry Report — [window] — [date]

### Skill Usage
| Skill            | [window] | Last Used    | Status     |
|------------------|----------|--------------|------------|
| /work            | 12       | today        |            |
| /ship            | 11       | today        |            |
| /done            | 8        | today        |            |
| /plan-session    | 2        | 2026-03-26   |            |
| /canary          | 1        | 2026-03-28   |            |
| /cso             | 0        | 2026-03-15   | idle 13d   |
| /qa:review       | 0        | never        | unused     |

### Ship Quality
- First-attempt pass rate: 82% (14/17)
- Avg iterations to pass: 1.3
- Top rejection reasons:
  1. Architecture boundary violation (5x)
  2. Floating promise (3x)
  3. Missing test coverage (2x)
- Reviewer catch rate: correctness 35%, architecture 30%, security 20%, UX 15%

### Throughput
- This period: N tasks shipped
- Daily average: X tasks/day
- Trend: [up/stable/down] vs previous period

### Work-Auto Sessions
- Sessions: 2 (completed 6, skipped 1)
- Avg duration: 45 min
- Skip reasons: ops category (1)

### Recommendations
1. [specific, data-backed recommendation]
2. [specific, data-backed recommendation]
3. [specific, data-backed recommendation]
```

## Guidelines

- Be data-driven — every recommendation cites a specific metric
- Don't alarm on low usage for inherently infrequent skills (/rollback, /solve, /migration-check). Flag only if they've never been used OR their recommended cadence is overdue.
- Recommended cadences: /cso every 14d, /housekeeper every 7d, /canary after every deploy
- Keep the report to one screen of output
- If insufficient data (<5 total events), say so and suggest checking back later
