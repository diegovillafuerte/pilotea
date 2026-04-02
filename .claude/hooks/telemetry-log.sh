#!/bin/bash
# Telemetry logger — records skill invocations to pming/.telemetry.jsonl
# Runs as PostToolUse hook on Skill calls

INPUT=$(cat)
SKILL=$(echo "$INPUT" | jq -r '.tool_input.skill // empty' 2>/dev/null)

if [ -z "$SKILL" ]; then
  exit 0
fi

# Resolve repo root (two levels up from .claude/hooks/)
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TELEMETRY_FILE="$REPO_ROOT/pming/.telemetry.jsonl"

# Append event using jq for safe JSON construction
jq -n --arg ts "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg skill "$SKILL" \
  '{ts:$ts,event:"skill_invoked",skill:$skill}' >> "$TELEMETRY_FILE"

exit 0
