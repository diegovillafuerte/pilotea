---
name: status
description: Quick production dashboard — deploy status, endpoint health, recent errors, and local vs deployed state in one glance. Lighter and faster than /canary.
argument-hint: [optional: "staging"]
---

# Status: Quick Production Dashboard

Fast, at-a-glance view of production health. Unlike `/canary` (structured pass/fail audit), this is a quick snapshot.

## Step 1: Determine environment

If $ARGUMENTS contains "staging":
- **Base URL:** TODO_STAGING_URL
- **Service ID:** TODO_STAGING_SERVICE_ID
- **Env:** staging

Otherwise (default):
- **Base URL:** TODO_PRODUCTION_URL
- **Service ID:** TODO_PRODUCTION_SERVICE_ID
- **Env:** production

## Step 2: Gather data in parallel

Run all of these simultaneously:

1. **Deploy status:** `mcp__render__list_deploys` for the service ID (latest 1)
2. **Health ping:**
   ```bash
   curl -s -o /dev/null -w "%{http_code} %{time_total}" <BASE_URL>/health
   ```
3. **Recent logs:** `mcp__render__list_logs` for the service (last 2 minutes). Count lines matching `ERROR|FATAL|uncaughtException|unhandledRejection`.
4. **Git state:** `git log --oneline -1` — what's the latest local commit? Compare to deployed commit SHA.

## Step 3: Display

Compact single-screen output. Two modes:

**All clear:**
```
## [env] — [timestamp]

Deploy:  abc1234 "Fix webhook timeout" — live since 14:30 (28 min ago)
Health:  200 OK (0.3s)
Errors:  none in last 2 min
Local:   def5678 — 1 commit ahead of deploy

Everything looks good.
```

**Issues detected:**
```
## production — 2026-03-28 15:00

Deploy:  abc1234 "Fix webhook timeout" — failed at 14:30
Health:  503 (timeout)
Errors:  3 distinct errors in last 2 min
  - ECONNREFUSED to postgres (x12)
  - unhandledRejection: Cannot read property 'id' of null (x3)
  - 502 upstream error (x1)
Local:   def5678 — 1 commit ahead of deploy

Production is DOWN. Consider /rollback or check the Render dashboard.
```

## Rules

- Read-only — never modify anything
- Maximum ~10 lines of output
- If any check fails to respond within 5 seconds, show "unavailable" rather than blocking
- Don't duplicate /canary — this is the quick glance, /canary is the thorough audit
