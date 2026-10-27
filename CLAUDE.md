# Kompara

@AGENTS.md

Everything above comes from `AGENTS.md`, the instructions shared with Codex. This file adds what is specific to Claude Code: the skills and the model routing.

## How to work (skills)

1. `/tasks` — see what's available
2. `/work B-XXX` — pick up a task in an isolated worktree (or `/work` to auto-select next); the session plans, the `coder` agent implements
3. `/ship` — adversarial review, commit, merge, push
4. `/done` — mark complete, advance to next

**Other skills:** `/plan-session` (daily/weekly planning), `/roadmap` (bird's-eye view), `/housekeeper` (project health check), `/rem` (self-healing maintenance), `/task` (create new work items), `/seenext` (preview next task), `/solve` (diagnose and fix bugs), `/work-me` (walk through manual ops tasks), `/canary` (post-deploy health check), `/status` (quick production dashboard), `/rollback` (emergency production recovery), `/cso` (security audit), `/migration-check` (DB migration safety review), `/telemetry` (harness analytics), `/design-sync` (sync `design-system/` with claude.ai/design), `/review-doc` (review a document in the Canvas app), `/codex:rescue` (hand a stuck problem to Codex).

## Model routing

Top-quality product first; within that, protect Fable's allowance, the scarcest resource. (Decided 2026-10-27.)

| Seat | Model | How to call it | Use for |
|---|---|---|---|
| Orchestrator | Fable 5.1, the session model | the main session | planning, decisions, delegating, judging results, talking to the user |
| Coder | Opus 5.5 | `coder` agent | implementing any change that needs judgment: features, fixes, refactors and their tests. The default for code. |
| Builder | Sonnet 5.5 | `builder` agent | very easy, mechanical code changes |
| Scout | Haiku 5.5 | `scout` agent | reading: find code, map a module, summarize a file or a log |
| Checker | Sonnet 5.5 | `checker` agent | acceptance criteria against the changes; consistency between docs and code |
| Researcher | Sonnet 5.5 | `researcher` agent | web research with sources |
| Reviewer and escalation | Codex (another model family) | the review steps in `/work`, `/ship` and `/solve`; `/codex:rescue` | plan and code review; taking over a problem Claude is stuck on |

Rules:
- The orchestrator doesn't write production code and doesn't read raw material. It reads plans, agent summaries, review verdicts and the few files a decision hinges on. Send a `scout` before reading a large file, a log, or more than two files. Use `scout` rather than the built-in Explore agent, which runs on Opus or even Fable depending on the provider.
- Every subagent call names an agent or a model. A hook (`.claude/hooks/guard-subagent-model.py`) refuses a generic, Plan or Explore subagent launched without a model, because it could silently run on Fable; anything else left unassigned defaults to Sonnet (`CLAUDE_CODE_SUBAGENT_MODEL` in `.claude/settings.json`).
- `coder`, `checker` and `researcher` write their full output to files under `/tmp/kompara-agents/` and return at most ten lines; `scout` and `builder` answer inline within their caps. Open a file only when the next step needs it.
- Never pull whole logs, Gradle output, Codex progress or long diffs into the conversation. Files carry them; the conversation carries conclusions and paths.
- Fixes after a review go back to the agent that wrote the code (continue it with SendMessage): it already has the context.
- A re-review covers only what changed since the last one. Don't re-review unchanged work or re-read what is already in context.
- Stuck after two genuinely different attempts: escalate to Codex (`/codex:rescue`), never to a bigger Claude model.
- Quality is never traded for tokens. When a step needs the top model's own judgment, such as an architecture call or a subtle bug diagnosis, the orchestrator does it and says why.
- If the session isn't running on Fable, tell the user and suggest switching in the model picker.
