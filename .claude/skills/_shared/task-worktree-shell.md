# Keeping the shell in the task worktree (shared)

`/work`, `/solve` and `/work-auto` build each task in `$MAIN_PATH/.worktrees/<id>` and end their worktree step with a `cd` into it. Later edits, verify rows, Codex reviews and `/ship` all run in the Bash tool's working directory, so the shell must stay there. `/ship` merges whatever branch that directory has checked out.

## How Claude Code moves the shell

Claude Code keeps the Bash tool's working directory between commands only inside the session's folders: the folder the session started in, and folders added to it.

- **A `cd` into a session folder** (or a folder inside one) sticks when the command exits 0.
- **A `cd` anywhere else, in a command that exits 0,** sends the shell back to the folder the session **started in**, and the output ends with `Shell cwd was reset to …`. That happens even when the shell was in another session folder before the command, such as the task worktree.
- **A failed command's `cd`, and a `cd` inside a subshell `( … )`,** never move the shell.

A session started in the main checkout has `.worktrees/` inside its folder, so its `cd` into a task worktree sticks. A session started in a Claude Code worktree (`.claude/worktrees/<name>`: the desktop app's worktree sessions, `claude -w`) doesn't, until `$MAIN_PATH/.worktrees` is added to it.

## When a check prints `OUTSIDE:`

The worktree step that sends you here runs a check after its `cd`. If the check prints `OUTSIDE:`, the shell is not in the task worktree. Never go on from there: edits, verify rows and `/ship` would act on the session's own checkout and its branch.

1. **Add `<MAIN_PATH>/.worktrees` to the session's folders**, with the path written out (the check prints it). One grant covers every task worktree for the rest of the session.
   - **Claude desktop app:** call the `mcp__ccd_directory__request_directory` tool with `path` set to it. If the tool is listed as deferred, load it first with ToolSearch (`select:mcp__ccd_directory__request_directory`). The user approves the folder, except in bypass permissions mode.
   - **Terminal (`claude` CLI):** ask the user to run `/add-dir <MAIN_PATH>/.worktrees`, and wait until they say it's done. (Started as `claude -w --add-dir "<MAIN_PATH>/.worktrees"`, a worktree session has it from the start.)
   - **Neither works:** stop and tell the user to add that folder to the session, or to start the session in the main checkout. `/work-auto` instead skips the task, as its step 2 says.
2. **Move into the worktree** with a plain `cd "<the worktree's path>"`, then run the check again.

## For the rest of the task

- **`cd` outside the session's folders only inside a subshell**, `( cd /elsewhere && … )`, or use `git -C <dir>`. A plain `cd` there that succeeds sends the shell back to the folder the session started in, not to the task worktree. This applies in every session, the main checkout's included.
- **If a result shows `Shell cwd was reset to …`,** `cd` back into the task worktree before doing anything else.
- `/ship` checks again before it commits (its Step 1).
