# Codex Navigation Guide

How to efficiently navigate the Kompara codebase. Read this FIRST before doing anything else.

> **Plugin:** This project uses the `codex-plugin-cc` plugin for Claude Code integration. Project-level Codex config is in `.codex/config.toml`.

## Step 1: Read the map

Read `CLAUDE.md` in the repo root. This is the project's instruction file — it describes:
- What the project is and how it's structured
- Architecture and key design decisions
- Project structure with file paths for every module
- Commands, conventions, and testing approach
- Tech stack and infrastructure details

**Use CLAUDE.md as your guide for what to read next.** Don't read the entire codebase — read only what's relevant to your task.

## Step 2: Go deeper only where needed

Based on your task, selectively read:

| If your task involves... | Read... |
|---|---|
| Architecture questions | `ARCHITECTURE.md` — the full architecture document |
| Deferred work or known shortcuts | `techdebt.md` — consciously deferred items |
| Code review (ship) | `.claude/skills/ship/review-criteria.md` — the review checklist |

## Step 3: Navigate source code efficiently

- **Don't read entire directories.** Use `grep`/`rg` to find specific symbols, then read those files.
- **Follow imports.** When you need to understand a module, read its entry point and follow imports only as needed.
- **Tests mirror source.** If you want to understand how a module behaves, check `test/` with the same path structure.

## What NOT to read

- `node_modules/` — external dependencies
- `dist/` — build output
- `.worktrees/` — temporary work branches
- `pming/` — project management files (unless your task is about PM)
- Every file in a directory — be surgical, not exhaustive
