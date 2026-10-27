#!/usr/bin/env python3
"""PreToolUse hook: refuse a generic subagent launched without an explicit model.

The named agents in .claude/agents/ (coder, builder, scout, checker, researcher) carry
their model in frontmatter. A generic subagent (general-purpose, claude) or the built-in
Plan agent launched with no model inherits the session model, Fable, the scarcest seat.
Built-in Explore drops to Opus under a Fable subscription session but inherits Fable on
other providers, so it is guarded too. This hook turns that silent inheritance into a
visible choice. Adapted from the claude-routing-team-kit.

Exit 0 with no output means "no opinion": the normal permission flow applies.
Any parsing problem also exits 0, so the hook can never block by accident.
To disable: remove the PreToolUse entry for this file from .claude/settings.json.
"""
import json
import sys

INHERITS_SESSION_MODEL = {"", "general-purpose", "claude", "Plan", "Explore"}
NAMED = "coder, builder, scout, checker, researcher"


def main() -> int:
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 0
    if data.get("tool_name") not in ("Agent", "Task"):
        return 0
    tool_input = data.get("tool_input") or {}
    subagent_type = str(tool_input.get("subagent_type") or "").strip()
    model = str(tool_input.get("model") or "").strip()
    if subagent_type in INHERITS_SESSION_MODEL and not model:
        reason = (
            "Routing rule (CLAUDE.md): a generic, Plan or Explore subagent can inherit the session model, Fable, the scarcest seat. "
            "Use a named agent (" + NAMED + "; scout replaces Explore for reading) or pass model=opus, sonnet or haiku explicitly. "
            "Pass model=fable only when the task truly needs the top model in a subagent seat."
        )
        print(json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": reason,
            }
        }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
