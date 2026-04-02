# Pilotea

A web app for uber drivers. (Details TBD — project scaffolding phase.)

## How to work

The project uses a markdown-based PM system in `pming/` (epics → stories → tasks) and Claude Code skills for workflow automation.

**Using skills (preferred):**
1. `/tasks` — see what's available
2. `/work B-XXX` — pick up a task in an isolated worktree (or `/work` to auto-select next)
3. Implement the task
4. `/ship` — adversarial review, commit, merge, push
5. `/done` — mark complete, advance to next

**Working manually:**
1. Find a task in `pming/tasks/pending/` — read its description and acceptance criteria
2. Implement the change
3. Run `pnpm test` — all tests must pass
4. Run `pnpm lint` — must be clean
5. Commit with a descriptive message

**Other skills:** `/plan-session` (daily/weekly planning), `/roadmap` (bird's-eye view), `/housekeeper` (project health check), `/rem` (self-healing maintenance), `/task` (create new work items), `/seenext` (preview next task), `/solve` (diagnose and fix bugs), `/work-me` (walk through manual ops tasks), `/canary` (post-deploy health check), `/status` (quick production dashboard), `/rollback` (emergency production recovery), `/cso` (security audit), `/migration-check` (DB migration safety review), `/telemetry` (harness analytics).

### When to stop and ask

- The spec is ambiguous or contradictory → add a `<!-- QUESTION: ... -->` comment to the task file and STOP
- You want to deviate from the spec → add a `<!-- DEVIATION: ... -->` comment explaining why and STOP
- A previous phase left something broken or incomplete → report it and STOP
- **Never stop for implementation decisions** (naming, patterns, library choices). Decide and move on.

## Architecture

TBD — to be defined once project details are provided.

## Commands

```bash
pnpm install          # install deps
pnpm build            # compile TypeScript (tsc)
pnpm dev              # start dev server
pnpm test             # run all tests
pnpm lint             # eslint
```

## Tech stack

TBD — to be defined once project details are provided.

## File conventions

- One export per file where practical
- Files named `kebab-case.ts`
- Types co-located in `types.ts` within each module
- Tests mirror source structure

## Tech debt

Track tech debt in `techdebt.md`. Whenever you make a conscious decision to defer work — security hardening, missing validation, known shortcuts, manual setup not yet done — add an entry. Each entry needs: date, severity, context, why deferred, and when to fix. Review and update `techdebt.md` when working on related areas.

## Documentation map

| Question | Read |
|---|---|
| System architecture | `ARCHITECTURE.md` (TBD) |
| Tech debt tracking | `techdebt.md` |
| PM system quick reference | `pming/skills.md` |
