---
name: ask-codex
description: "DEPRECATED: Use /codex:rescue instead. Thin redirect for backward compatibility."
argument-hint: <question or problem description>
---

# Ask Codex — DEPRECATED

**This skill is deprecated.** Use `/codex:rescue` from the Codex plugin instead.

`/codex:rescue` provides the same capability with better infrastructure: background execution, job tracking, resume threads, and status monitoring.

## Redirect

1. Tell the user: "`/ask-codex` has been replaced by `/codex:rescue`. Routing your request there."
2. Use the Skill tool to invoke `codex:rescue` with the same $ARGUMENTS.

If the Codex plugin is not loaded (no `codex:rescue` skill available), fall back to the direct CLI:

```bash
TMPFILE=$(mktemp)
OUTFILE=$(mktemp)
cat > "$TMPFILE" <<'PROMPT_EOF'
You are being consulted as a second opinion on a software engineering problem in the Pilotea codebase (a web application built with Node.js/TypeScript).

You have read-only access to the full repo. Read `.claude/codex-guide.md` FIRST for efficient navigation.

{CONTEXT AND QUESTION}
PROMPT_EOF

codex exec - \
  --sandbox read-only \
  --output-last-message "$OUTFILE" \
  -C "$(pwd)" < "$TMPFILE"

cat "$OUTFILE"
rm -f "$TMPFILE" "$OUTFILE"
```

Set `timeout: 480000` on the Bash call.
