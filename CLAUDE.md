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

Next.js 15 monolith (App Router) deployed on Render. React frontend + API routes in a single deployable unit.

**Layers and boundaries:**
- `src/app/` — Next.js pages and API routes. Pages can import from `components/`, `hooks/`, `lib/`. API routes can import from `lib/`.
- `src/lib/` — Shared library code. Modules: `db/` (Drizzle ORM), `auth/` (sessions, magic links), `parsers/` (Claude Vision extraction), `storage/` (R2), `whatsapp/` (Twilio), `percentiles/` (stats engine). Lib modules should not import from `app/` or `components/`.
- `src/components/` — React UI components. Can import from `hooks/` and `lib/constants.ts`. Should not import from `lib/db/` or `lib/auth/` directly.
- `src/hooks/` — React hooks. Can import from `lib/`.

**External services:** Render Postgres, Cloudflare R2, Claude API (Vision), Twilio WhatsApp.

See `docs/technical-design.md` for full architecture details.

## Commands

```bash
pnpm install          # install deps
pnpm build            # compile TypeScript (tsc)
pnpm dev              # start dev server
pnpm test             # run all tests
pnpm lint             # eslint
```

## Tech stack

- **Framework:** Next.js 15 (App Router), React 19, TypeScript (strict)
- **Styling:** Tailwind CSS 4
- **Database:** Render Postgres + Drizzle ORM
- **File storage:** Cloudflare R2 (S3-compatible)
- **AI/OCR:** Claude Sonnet API (vision)
- **Auth:** Custom WhatsApp magic links via Twilio + jose JWT
- **Validation:** Zod
- **Testing:** Vitest (unit/integration), Playwright (e2e)
- **Hosting:** Render Web Service

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
