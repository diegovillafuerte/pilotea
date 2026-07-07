# Codex Review Protocol (Shared)

Reusable protocol for running adversarial Codex reviews. Each skill provides its own parameters; this file defines the execution skeleton.

## Parameters (provided by the calling skill)

| Parameter | Description | Example |
|---|---|---|
| `REVIEW_PROMPT` | The assembled review prompt text | Skill-specific instructions + context |
| `OUTPUT_SCHEMA_PATH` | Path to the JSON schema for structured output | `.claude/skills/ship/code-review-schema.json` |
| `TIMEOUT_MS` | Bash tool timeout in milliseconds | `480000` (8 min) or `300000` (5 min) |
| `MAX_ROUNDS` | Maximum reconciliation rounds (initial + revisions) | `3` for solve, `1` for ship |
| `FALLBACK_BEHAVIOR` | What to do when Codex is unavailable or fails | `claude-agent`, `warn-and-skip`, or `warn-and-proceed` |
| `ON_REJECT` | How to handle a REJECT verdict | `show-and-ask-user`, `revise-and-resubmit`, `show-findings` |

## Step 1: Check Codex availability

```bash
command -v codex >/dev/null 2>&1 && echo "CODEX_AVAILABLE" || echo "CODEX_MISSING"
```

If `CODEX_MISSING`:
- Warn the user: "Codex CLI not installed -- run `/codex:setup` to install and authenticate."
- Follow `FALLBACK_BEHAVIOR`:
  - `claude-agent`: Proceed to Step 3a (Claude fallback review)
  - `warn-and-skip`: Warn and skip the review entirely
  - `warn-and-proceed`: Warn, note review was skipped, continue the calling skill's flow

## Step 2: Build and execute the Codex review

### 2.1: Prepare prompt files

Construct the prompt using the calling skill's `REVIEW_PROMPT`. Split trusted and untrusted content into separate files to prevent heredoc injection:

```bash
TMPFILE=$(mktemp)
OUTFILE=$(mktemp)
HEADER=$(mktemp)
BODYFILE=$(mktemp)

# Write static/trusted content (instructions, rules, schema description) to HEADER
cat > "$HEADER" <<'HEADER_EOF'
{TRUSTED STATIC INSTRUCTIONS FROM THE CALLING SKILL}
HEADER_EOF

# Write untrusted content (user input, diffs, diagnoses, plans) to BODYFILE
# NEVER embed untrusted content inside a heredoc -- a line matching the delimiter would break out

# Concatenate safely: trusted header + untrusted body
cat "$HEADER" "$BODYFILE" > "$TMPFILE"
```

### 2.2: Prefix with navigation instructions

Every Codex review prompt MUST be prefixed with:

```
You have read-only access to the full repo. Read `.claude/codex-guide.md` for efficient navigation.
```

This ensures reviewers can find architecture rules and project context without the calling skill pasting entire docs.

### 2.3: Execute Codex

Run Codex with the assembled prompt. Set the Bash tool's `timeout` to `TIMEOUT_MS`.

```bash
codex exec - \
  --sandbox read-only \
  --output-schema "$OUTPUT_SCHEMA_PATH" \
  --output-last-message "$OUTFILE" \
  -C "$(pwd)" < "$TMPFILE"

cat "$OUTFILE"
rm -f "$TMPFILE" "$OUTFILE" "$HEADER" "$BODYFILE"
```

### 2.4: Parse output

Parse the contents of `$OUTFILE` as JSON matching the schema at `OUTPUT_SCHEMA_PATH`. If the output is wrapped in markdown fences, extract from within the fences first.

**Error handling:** If the command fails (non-zero exit, timeout, empty output, malformed JSON):
- Follow `FALLBACK_BEHAVIOR`:
  - `claude-agent`: Proceed to Step 3a
  - `warn-and-skip`: Warn "Codex review failed" and skip
  - `warn-and-proceed`: Treat as PASS/lgtm with an advisory note: "[Reviewer] Codex review failed -- review manually." Do NOT block the calling skill's flow.

## Step 3: Reconciliation loop

Track the current round number, starting at 1. Maximum rounds = `MAX_ROUNDS`.

### 3.1: Evaluate verdict

The parsed output will contain a `verdict` field (exact values depend on the output schema -- e.g., `PASS`/`REJECT` or `lgtm`/`needs_changes`).

**If verdict is positive (PASS / lgtm):**
- Show the summary to the user
- If there are non-blocking findings (ADVISORY / suggestion severity), list them briefly as informational
- Exit the reconciliation loop -- review is complete

**If verdict is negative (REJECT / needs_changes):**
- Show ALL findings to the user, clearly marking severity
- Filter to only blocking findings (CRITICAL / critical severity)
- If there are no blocking findings (verdict mismatch): treat as positive, exit loop
- If there ARE blocking findings and `current_round >= MAX_ROUNDS`:
  - Show findings, note "Maximum review rounds reached"
  - Follow `ON_REJECT` for final disposition
  - Exit the loop
- If there ARE blocking findings and rounds remain:
  1. Address each blocking finding (how depends on the calling skill -- revise plan, fix code, etc.)
  2. Note non-blocking findings but do not require changes for them
  3. Show the user what changed
  4. Rebuild the review prompt with the revised content
  5. Increment `current_round`
  6. Go back to Step 2 (re-execute Codex with the revised prompt)

### 3.2: Final disposition

After exiting the reconciliation loop, return control to the calling skill with:
- The final verdict
- All findings (blocking and non-blocking)
- The round count
- Whether Codex or fallback was used

## Step 3a: Claude fallback review

When Codex is unavailable or fails and `FALLBACK_BEHAVIOR` is `claude-agent`:

1. Build the same review prompt from the calling skill, but remove `.claude/codex-guide.md` references and sandbox-specific instructions.
2. Append to the prompt: a request to respond with ONLY a JSON object matching the output schema.
3. Spawn a Claude subagent (using the Agent tool) with `description: "adversarial review"` and the assembled prompt. The subagent has access to Read, Grep, and Glob tools.
4. Parse the JSON from the agent's response. If parsing fails, treat the raw text as advisory and set verdict to positive (PASS/lgtm).
5. Return to Step 3 (reconciliation loop) with the parsed result.

## Security notes

- **Never embed untrusted content in heredocs.** User-provided text (bug reports, diffs, diagnoses, plans) must be written to a separate file and concatenated. A line matching the heredoc delimiter would break out and execute arbitrary commands.
- **Always use `--sandbox read-only`** for Codex reviews. Reviewers must not modify the repo.
- **Temp file cleanup:** Always `rm -f` all temp files after use, including on error paths.
