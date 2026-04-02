---
name: cso
description: Systematic security audit using OWASP Top 10 and STRIDE threat modeling. Reviews secrets, dependencies, API boundaries, LLM security, and infrastructure. Use before launch or periodically for security posture check.
argument-hint: [optional: "quick" for critical/high only, or specific phase like "secrets", "dependencies", "api", "llm", "infra"]
---

# CSO: Security Audit

You are a senior security engineer performing a systematic audit of this codebase. Be thorough but practical — prioritize findings that could lead to real exploitation or data exposure over theoretical risks.

## Step 0: Determine scope

If $ARGUMENTS contains **"quick"**: only report CRITICAL and HIGH findings. Skip MEDIUM/LOW/INFO.
If $ARGUMENTS names a specific phase (e.g., "secrets", "dependencies", "api", "llm", "infra"): run only that phase.
Otherwise: run all phases sequentially.

Read `CLAUDE.md` and `ARCHITECTURE.md` first to understand the system boundaries.

---

## Phase 1: Secrets & Credentials

### 1a. Hardcoded secrets scan

Search the codebase (excluding node_modules, dist, .git) for:

```bash
# API key patterns
grep -rn --include='*.ts' --include='*.js' --include='*.json' --include='*.md' -E '(sk-[a-zA-Z0-9]{20,}|pk_[a-zA-Z0-9]{20,}|Bearer\s+[a-zA-Z0-9._-]{20,})' src/ || true
```

Also search for:
- Connection strings: `postgres://`, `redis://`, `mongodb://`
- Base64-encoded blobs in non-test source files
- Hardcoded passwords or tokens assigned to variables

### 1b. .gitignore coverage

Verify these are gitignored: `.env`, `.env.*`, `*.pem`, `*.key`, `credentials.json`

```bash
git ls-files | grep -iE '\.env|\.pem|\.key|credential|secret' || echo "None tracked — good"
```

### 1c. Environment variable hygiene

Find all `process.env.` references:

```bash
grep -rn 'process\.env\.' src/ --include='*.ts' | grep -v node_modules
```

For sensitive variables (META_ACCESS_TOKEN, META_APP_SECRET, DATABASE_URL, RENDER_API_KEY, ANTHROPIC_API_KEY):
- Are they ever logged? (`console.log`, `logger.`, `JSON.stringify` of config objects)
- Are they included in error messages or API responses?
- Are they passed to the LLM in prompts?

### 1d. Git history secrets

```bash
git log --all --diff-filter=A --name-only --format="" | grep -iE '\.env|\.pem|\.key|credential|secret' | head -20 || echo "None found"
```

---

## Phase 2: Dependency Supply Chain

### 2a. Known vulnerabilities

```bash
pnpm audit 2>&1 || true
```

Flag any HIGH or CRITICAL vulnerabilities. For each, note if the vulnerable code path is reachable from this project.

### 2b. Dependency review

```bash
pnpm ls --depth 0 2>&1
```

Flag dependencies that:
- Are unmaintained (no updates in 2+ years)
- Have very few downloads (potential typosquatting)
- Seem unnecessary for the project scope

### 2c. Lock file integrity

Verify `pnpm-lock.yaml` exists and is committed. An uncommitted lock file means builds aren't reproducible.

---

## Phase 3: OWASP Top 10

For each category, read the relevant source files before making findings. Do not guess — verify.

### A01: Broken Access Control

- **Webhook authentication**: Read `src/engine/transport/`. Is the Meta webhook signature (`X-Hub-Signature-256`) verified on every incoming message? Is the raw body used for HMAC (not parsed JSON)?
- **Admin routes**: Read `src/admin/`. Are admin endpoints protected by authentication? What prevents unauthorized access?
- **Data exchange endpoint**: Read the data-exchange handler. Is it authenticated? Can arbitrary flow data be injected?
- **Card viewer**: Read `src/card-viewer/`. How are tokens validated? Can tokens be brute-forced? Do they expire?

### A02: Cryptographic Failures

- How is PII stored in the database? (names, RFC, CURP, addresses, phone numbers) Is any of it encrypted at rest?
- Check HMAC/token implementations for correctness (constant-time comparison, sufficient key length)
- Are external API calls made over TLS? (check for any `http://` URLs in integration code)

### A03: Injection

- **SQL injection**: Read `src/db/` and any raw SQL queries. Are all queries parameterized? Search for string concatenation in SQL:
  ```bash
  grep -rn --include='*.ts' -E '(query|sql|SELECT|INSERT|UPDATE|DELETE).*\$\{' src/ || echo "None found"
  ```
- **Command injection**: Does any user input flow into `child_process`, `exec`, or shell commands?
- **LLM prompt injection**: How is user input sanitized before inclusion in LLM prompts? Can a user craft a message that overrides system instructions?

### A04: Insecure Design

- **State machine bypass**: Can a user skip states by sending specific messages? Are guards enforced server-side or only in the flow definition?
- **Race conditions**: Can duplicate webhook deliveries cause double-processing? Check idempotency handling (wamid dedup).
- **Flow authorization**: Can a user trigger a flow they shouldn't have access to?

### A05: Security Misconfiguration

- **CORS**: Is CORS configured? What origins are allowed?
- **Security headers**: Check for X-Content-Type-Options, X-Frame-Options, Strict-Transport-Security, Content-Security-Policy
- **Error exposure**: Do 500 errors leak stack traces to clients?
- **Debug endpoints**: Are there any dev/debug routes that shouldn't be in production? (e.g., the reset endpoint)

### A07: Authentication Failures

- **Session management**: How are sessions created and validated? Can sessions be hijacked?
- **Phone number trust**: Is the phone number from the webhook trusted without verification? (Meta signs webhooks, but is that signature always checked?)

### A08: Data Integrity

- **Webhook signature verification**: Is it implemented correctly? Read the exact implementation.
- **External API response validation**: Are responses from Incode, Belvo, Moffin, Pomelo validated/typed or trusted blindly?

### A10: SSRF

- Does any user input flow into URLs for server-side requests?
- Check media download: can a user send a malicious media URL that causes the server to fetch an internal resource?

---

## Phase 4: STRIDE Threat Model

For each major boundary, assess all six STRIDE categories. Focus on the highest-risk boundaries:

### 4a. WhatsApp Webhook (internet-facing)

| Threat | Assessment |
|--------|-----------|
| **Spoofing** | Can someone send fake webhooks? (check signature verification) |
| **Tampering** | Can webhook payload be modified in transit? |
| **Repudiation** | Are all incoming messages logged with enough detail for audit? |
| **Info Disclosure** | Do error responses leak internal state? |
| **DoS** | Rate limiting? What happens under high message volume? |
| **Elevation** | Can a webhook trigger admin actions? |

### 4b. Admin API

| Threat | Assessment |
|--------|-----------|
| **Spoofing** | Authentication mechanism? |
| **Tampering** | Can admin data be modified by non-admins? |
| **Info Disclosure** | Does admin API expose PII without access controls? |
| **Elevation** | Can a regular user access admin endpoints? |

### 4c. Card Viewer (internet-facing, sensitive data)

| Threat | Assessment |
|--------|-----------|
| **Spoofing** | Token-based access — how strong are tokens? |
| **Info Disclosure** | Full card numbers exposed? PAN masking? |
| **Tampering** | Can card data be modified through the viewer? |

### 4d. External Integration APIs

| Threat | Assessment |
|--------|-----------|
| **Info Disclosure** | What PII is sent to each provider? Necessary minimum? |
| **Tampering** | Are responses validated? Could a compromised provider inject bad data? |

---

## Phase 5: LLM / AI Security

### 5a. Prompt injection surface

Read the interpreter and prompt builder (`src/engine/interpreter/`):
- How are user messages included in prompts? Are they clearly delimited?
- Can a user craft a message that overrides system instructions?
- Is there any output parsing that could be exploited? (e.g., if LLM returns `{"field": "value"}` and it's parsed as trusted data)

### 5b. Data sent to LLM

Catalog what PII is sent to Anthropic:
- User messages (contain names, RFC, CURP, addresses, financial details)
- Context/history window contents
- Is there any PII redaction before LLM calls?
- What is the data retention policy?

### 5c. LLM output trust

- Is LLM output validated before state transitions?
- Can the LLM be manipulated into extracting incorrect field values?
- What happens if the LLM returns unexpected/malformed output?

---

## Phase 6: Infrastructure

### 6a. Render configuration

- Are health checks configured?
- Is auto-deploy from main branch safe? (considering pre-commit hooks gate quality, but not security)
- Environment variable management — who has access?

### 6b. Database

- Connection string handling (SSL mode?)
- Migration safety — are migrations reversible?
- Backup strategy

### 6c. Media storage

- Cloudflare R2 / local storage — access controls?
- Are uploaded KYC images accessible without authentication?
- Image retention policy

---

## Step 7: Report

Compile all findings into a structured report:

```
## Security Audit Report — [date]

### Scope
[phases run, files reviewed, what was excluded]

### CRITICAL — fix before launch
[numbered findings with file:line, risk description, specific remediation]

### HIGH — fix soon
[numbered findings]

### MEDIUM — fix when convenient
[numbered findings]

### LOW / INFORMATIONAL
[numbered findings]

### Positive findings
[what's already done well — acknowledge good security practices found]

### Summary
- Total findings: X (C critical, H high, M medium, L low)
- Top 3 actions by impact-to-effort ratio
- Areas not covered that should be reviewed separately
```

For each finding include:
- **What**: one-line description
- **Where**: file:line reference
- **Risk**: what could happen if exploited (be specific, not hypothetical)
- **Fix**: concrete remediation steps (code-level, not abstract advice)
- **Effort**: S/M/L estimate
