# Adversarial Review Criteria

Use this checklist when reviewing code changes. Not every category applies to every diff — focus on what's relevant. Each section is tagged with the reviewer(s) responsible.

## Critical (blocks ship)

### Security [Security reviewer]
- [ ] No secrets, API keys, or tokens in code (check string literals, config defaults)
- [ ] No SQL injection (parameterized queries only)
- [ ] No command injection (no unsanitized user input in shell commands)
- [ ] No XSS vectors (user input rendered in responses must be escaped)
- [ ] Auth/authorization checks present where needed
- [ ] No PII logged or exposed in error messages

### Architecture boundaries [Architecture reviewer]
- [ ] Engine code (`src/engine/`) does NOT import from `src/flows/`
- [ ] Flow code (`src/flows/`) only imports from `engine/types.ts` and `engine/machine/types.ts`
- [ ] Layer dependency rules respected: types -> config -> db -> engine -> flows -> index
- [ ] No reverse imports (flows -> engine internals, engine -> flows)

### Correctness [Correctness reviewer]
- [ ] Async functions are properly awaited (no floating promises)
- [ ] Error handling doesn't swallow errors silently
- [ ] Database queries use transactions where atomicity is needed
- [ ] No infinite loops or unbounded recursion
- [ ] Type assertions (`as`) are justified, not hiding type errors

### Data integrity [Correctness reviewer]
- [ ] Database migrations are reversible or safe to apply
- [ ] No data loss paths (overwriting without backup, DELETE without WHERE)
- [ ] Idempotency preserved for webhook handlers

### Documentation freshness [Architecture reviewer]
- [ ] New modules, layers, or flows are reflected in `ARCHITECTURE.md`
- [ ] New architectural decisions (AD-N pattern) added to `CLAUDE.md` if they affect engine/flow boundary or layer rules
- [ ] Consciously deferred work has an entry in `techdebt.md` with date, severity, and resolution criteria
- [ ] If flow behavior changed, the flow's `knowledge-base.md` still accurately describes the product
- [ ] If flow persona changed, the flow's `tone.md` still reflects the intended voice

### User experience [UX reviewer — conditional]
- [ ] Message templates include warm context (what the user is doing and why)
- [ ] WhatsApp Flow screens have clear explanations, not just bare form fields
- [ ] Conversation flow doesn't feel cold or transactional
- [ ] Error messages guide the user toward resolution
- [ ] Tone matches the flow's `tone.md` guidelines

## Advisory (does not block, but worth noting)

### Test coverage [Correctness reviewer]
- [ ] New code paths have corresponding tests
- [ ] Edge cases tested (empty input, null, boundary values)
- [ ] Error paths tested, not just happy paths

### Consistency [Architecture reviewer]
- [ ] Naming follows existing codebase conventions (kebab-case files, etc.)
- [ ] Error messages include remediation instructions (per project convention)
- [ ] Structured logging used (per project convention)
- [ ] New files placed in correct directory per architecture

### Performance [Correctness reviewer]
- [ ] No N+1 query patterns introduced
- [ ] No unbounded data loading (missing LIMIT, loading full tables)
- [ ] No synchronous blocking in async context

### Harness integrity [Architecture reviewer]
- [ ] `CLAUDE.md` stays under 300 lines (progressive disclosure, not encyclopedia)
- [ ] New enforceable rules use linters with remediation messages, not just documentation (principle: never send an LLM to do a linter's job)
- [ ] Custom lint error messages include remediation instructions pointing to relevant docs
- [ ] `pming/` task/story status reflects completed work (if tasks were worked on)
- [ ] No LLM-generated context blobs added to instruction files (reduces agent success rates)
