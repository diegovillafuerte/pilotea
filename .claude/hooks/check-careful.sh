#!/bin/bash
# Safety guardrails — warns before destructive commands
# Runs as PreToolUse hook on all Bash calls
# Exit 0 = allow, exit 0 with deny JSON = block

INPUT=$(cat)
COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty' 2>/dev/null)

# If we can't parse or command is empty, allow (fail open)
if [ -z "$COMMAND" ]; then
  exit 0
fi

deny() {
  jq -n --arg reason "$1" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

# --- Destructive file operations ---

# rm with force+recursive on non-whitelisted targets
if echo "$COMMAND" | grep -qE 'rm\s+(-[a-zA-Z]*r[a-zA-Z]*f|-[a-zA-Z]*f[a-zA-Z]*r|-rf|-fr)\b'; then
  # Whitelist build artifacts
  if echo "$COMMAND" | grep -qE '\b(node_modules|dist|\.next|build|coverage|\.cache|\.turbo|__pycache__|\.nyc_output|\.parcel-cache)\b'; then
    exit 0
  fi
  deny "rm -rf on non-build target. Review the target path before proceeding. Command: $COMMAND"
fi

# --- Destructive SQL ---

if echo "$COMMAND" | grep -qiE '(DROP\s+(TABLE|DATABASE|SCHEMA)|TRUNCATE\s+TABLE)'; then
  deny "Destructive SQL operation (DROP/TRUNCATE). Command: $COMMAND"
fi

# --- Destructive git operations ---

# Force push (including --force-with-lease which is safer but still destructive)
if echo "$COMMAND" | grep -qE 'git\s+push\s+.*(\s-f\b|--force\b|--force-with-lease\b)'; then
  deny "Force push rewrites remote history. Command: $COMMAND"
fi

# Hard reset
if echo "$COMMAND" | grep -qE 'git\s+reset\s+--hard'; then
  deny "git reset --hard discards all uncommitted changes. Command: $COMMAND"
fi

# Clean with force (deletes untracked files)
if echo "$COMMAND" | grep -qE 'git\s+clean\s+.*-[a-zA-Z]*f'; then
  deny "git clean -f permanently deletes untracked files. Command: $COMMAND"
fi

# Checkout that discards all changes
if echo "$COMMAND" | grep -qE 'git\s+checkout\s+(--\s+\.|\.)\s*$'; then
  deny "git checkout -- . discards all unstaged changes. Command: $COMMAND"
fi

# Restore that discards all changes
if echo "$COMMAND" | grep -qE 'git\s+restore\s+\.\s*$'; then
  deny "git restore . discards all unstaged changes. Command: $COMMAND"
fi

# Force branch delete (allow work/ and fix/ task branches)
# Only match commands that START with git branch, not mentions in commit messages
if echo "$COMMAND" | grep -qE '^git\s+branch\s+.*-D\b'; then
  if echo "$COMMAND" | grep -qE '^git\s+branch\s+-D\s+(work|fix)/'; then
    :  # Allow deletion of task worktree branches
  else
    deny "Force-deleting a branch bypasses merge status checks. Command: $COMMAND"
  fi
fi

# --- Container/infra destructive ops ---

if echo "$COMMAND" | grep -qE '(docker\s+rm\s+-f|docker\s+system\s+prune|kubectl\s+delete)'; then
  deny "Destructive container/infra operation. Command: $COMMAND"
fi

# --- All clear ---
exit 0
