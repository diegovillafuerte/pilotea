# Codex Navigation Guide

How to efficiently navigate the Kompara codebase. Read this FIRST before doing anything else.

> **Plugin:** This project uses the `codex-plugin-cc` plugin for Claude Code integration. There is no project-level Codex config.

## Step 1: Read the map

Read `AGENTS.md` in the repo root (Codex loads it automatically). It is the instruction file every agent shares — it describes:
- What the project is and how it's structured
- Architecture and key design decisions
- Project structure with file paths for every module
- Commands, conventions, and testing approach
- Tech stack and infrastructure details

**Use AGENTS.md as your guide for what to read next.** Don't read the entire codebase — read only what's relevant to your task.

## Step 2: Go deeper only where needed

Based on your task, selectively read:

| If your task involves... | Read... |
|---|---|
| Architecture questions | `docs/android-technical-design.md` — the Android architecture and capture design |
| Deferred work or known shortcuts | `techdebt.md` — consciously deferred items |
| Code review (ship) | `.claude/skills/ship/review-criteria.md` — the review checklist |

## Step 3: Navigate source code efficiently

- **Don't read entire directories.** Use `grep`/`rg` to find specific symbols, then read those files.
- **Follow imports.** When you need to understand a module, read its entry point and follow imports only as needed.
- **Tests sit near the source.** Android tests are in each module's `src/test` (Room instrumented tests in `android/data/src/androidTest`); backend tests are `*.test.ts` next to the file they test.

## What NOT to read

- `node_modules/` — external dependencies
- `dist/`, `android/**/build/` — build output
- `src/` and the other legacy web paths listed in AGENTS.md — unless the change touches them
- `.worktrees/` — temporary work branches
- `pming/` — project management files (unless your task is about PM)
- Every file in a directory — be surgical, not exhaustive
