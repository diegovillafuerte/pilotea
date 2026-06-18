---
name: canary
description: Post-deploy health check. Verifies production is healthy after a deploy by checking endpoints, Render deploy status, and logs for errors. Use after pushing to main or when you want to verify production health.
argument-hint: [optional: "staging" to check staging instead of production]
---

# Canary: Post-Deploy Health Check

Verify that production (or staging) is healthy after a deploy.

## Step 1: Determine target environment

If $ARGUMENTS contains "staging":
- **Base URL:** TODO_STAGING_URL
- **Service ID:** TODO_STAGING_SERVICE_ID
- **Environment:** staging

Otherwise (default = production):
- **Base URL:** TODO_PRODUCTION_URL
- **Service ID:** TODO_PRODUCTION_SERVICE_ID
- **Environment:** production

## Step 2: Check deploy status

Use `mcp__render__list_deploys` for the service ID — check the latest deploy:
- Is it **live**? Note the commit SHA and timestamp.
- If it's **building** or **deploying**: tell the user, wait 30 seconds, check again. Repeat up to 5 times. If still not live, report as DEPLOYING and stop.
- If the latest deploy **failed**: report immediately as CRITICAL with the failure reason.

## Step 3: Health endpoint check

```bash
curl -s -o /dev/null -w "%{http_code} %{time_total}s" <BASE_URL>/health
```

Expected: HTTP 200, response time < 2s.

If `/health` returns 404, try `<BASE_URL>/` instead and check for non-5xx.

Flag as:
- **OK**: 200 in < 2s
- **SLOW**: 200 but > 2s
- **DOWN**: 5xx or timeout

## Step 4: Webhook endpoint check

```bash
curl -s -o /dev/null -w "%{http_code}" -X GET "<BASE_URL>/webhook?hub.mode=subscribe&hub.verify_token=canary_probe&hub.challenge=canary_check"
```

This tests the Meta webhook verification endpoint. The verify token won't match, but:
- **200 or 403**: server is responding, webhook route is live — OK
- **5xx or timeout**: webhook route is broken — CRITICAL

## Step 5: Check recent logs for errors

Use `mcp__render__list_logs` for the service ID. Look at the last 5 minutes of logs.

Search for patterns indicating problems:
- `ERROR`, `FATAL`, `uncaughtException`, `unhandledRejection`
- `ECONNREFUSED`, `ENOTFOUND`, `ETIMEDOUT`
- `502`, `503`, `504` (upstream errors)
- Stack traces (multi-line with `at ` prefix)

Ignore:
- Expected warnings and deprecation notices
- Health check log lines
- Normal request logging

Count distinct errors (deduplicate repeated occurrences of the same error).

## Step 6: Report

Present a clear status table:

```
## Canary Report — [environment] — [timestamp]

| Check              | Status | Details                          |
|--------------------|--------|----------------------------------|
| Deploy             | OK/FAIL| [commit sha] deployed [time ago] |
| Health endpoint    | OK/FAIL| [status code] in [time]s         |
| Webhook endpoint   | OK/FAIL| [status code]                    |
| Error logs (5 min) | OK/FAIL| [count] distinct errors found    |

**Verdict: HEALTHY / DEGRADED / DOWN**
```

**Verdict logic:**
- **HEALTHY**: all checks pass, zero errors in logs
- **DEGRADED**: endpoints respond but errors found in logs, or health is SLOW
- **DOWN**: any endpoint returns 5xx or deploy failed

If DEGRADED or DOWN:
- Show the specific errors found (with timestamps)
- Suggest the user check the Render dashboard
- Ask if they want to check the previous deploy's commit for a potential rollback target
