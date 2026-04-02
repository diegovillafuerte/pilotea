---
name: rollback
description: Emergency production rollback. Finds the last healthy deploy, triggers redeployment of that version via Render, and verifies recovery with a canary check.
argument-hint: [optional: "staging" to rollback staging]
---

# Rollback: Emergency Production Recovery

Roll back to the last known-good deploy when production is broken.

## Step 1: Determine target environment

If $ARGUMENTS contains "staging":
- **Service ID:** TODO_STAGING_SERVICE_ID
- **Base URL:** TODO_STAGING_URL
- **Environment:** staging

Otherwise (default = production):
- **Service ID:** TODO_PRODUCTION_SERVICE_ID
- **Base URL:** TODO_PRODUCTION_URL
- **Environment:** production

## Step 2: Assess current state

Use `mcp__render__list_deploys` for the service ID. Get the 5 most recent deploys.

Display:
```
## Current State — [environment]
| # | Commit  | Status  | Created            |
|---|---------|---------|--------------------|
| 1 | abc1234 | failed  | 2026-03-28 14:30   | <- current
| 2 | def5678 | live    | 2026-03-28 12:00   |
| 3 | ghi9012 | ...     | 2026-03-27 18:00   |
```

Identify:
- **Current deploy:** the most recent
- **Rollback target:** the most recent deploy with status "live" that is NOT the current one. If the current deploy is already "live" but broken, the target is the second most recent "live" deploy.

If no valid rollback target exists, report: "No previous successful deploy to roll back to." and STOP.

## Step 3: Confirm with user

```
Rolling back [environment]:
  FROM: abc1234 ([commit message]) — [status]
  TO:   def5678 ([commit message]) — deployed [time ago]

This will redeploy the older version. Continue? (y/n)
```

**Always wait for confirmation.** This is a production action — never proceed automatically.

## Step 4: Trigger rollback

Use Render API to redeploy the target commit:

```bash
curl -s -X POST "https://api.render.com/v1/services/$SERVICE_ID/deploys" \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"commitId\": \"$TARGET_COMMIT\"}"
```

If the API call fails, show the error and suggest the user manually trigger from the Render dashboard: https://dashboard.render.com

## Step 5: Monitor rollback deploy

Poll `mcp__render__list_deploys` every 15 seconds, up to 10 times (2.5 minutes):
- **live**: proceed to Step 6
- **building/deploying**: keep waiting
- **failed**: report CRITICAL, suggest checking Render dashboard

## Step 6: Verify recovery

Run canary checks:

1. **Health:** `curl -s -o /dev/null -w "%{http_code} %{time_total}s" <BASE_URL>/health` — expect 200 in <2s
2. **Webhook:** `curl -s -o /dev/null -w "%{http_code}" -X GET "<BASE_URL>/webhook?hub.mode=subscribe&hub.verify_token=canary_probe&hub.challenge=canary_check"` — expect 200 or 403
3. **Logs:** `mcp__render__list_logs` — check last 2 minutes for ERROR/FATAL

## Step 7: Report

```
## Rollback Report — [environment] — [timestamp]

| Check           | Status | Details                    |
|-----------------|--------|----------------------------|
| Rollback deploy | OK     | def5678 now live           |
| Health endpoint | OK     | 200 in 0.3s               |
| Webhook         | OK     | responding                 |
| Error logs      | OK     | no errors in last 2 min    |

**Verdict: RECOVERED / STILL DOWN**

Rolled back from abc1234 -> def5678.
```

If STILL DOWN:
- Suggest checking Render dashboard directly
- Suggest examining logs for the rollback deploy
- Note the issue may predate recent deploys

## Step 8: Log telemetry

If `pming/.telemetry.jsonl` exists, append:
```bash
echo '{"ts":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","event":"rollback","environment":"[env]","from":"[commit]","to":"[commit]","verdict":"[RECOVERED/STILL_DOWN]"}' >> pming/.telemetry.jsonl
```

## Rules

- ALWAYS confirm with the user before triggering a rollback (Step 3)
- Never force-push or revert git commits — this only redeploys a previous build artifact
- If the Render API is unreachable, provide the dashboard URL as fallback
- Keep the user informed at each step — rollbacks are stressful, clarity reduces panic
