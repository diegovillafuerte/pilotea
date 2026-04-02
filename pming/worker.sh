#!/usr/bin/env bash
set -euo pipefail

# Worker coordination script for parallel Claude Code sessions.
# All .workers mutations go through this script for atomicity and safety.

MAIN_PATH=$(git worktree list --porcelain 2>/dev/null | head -1 | sed 's/worktree //')
WORKERS_FILE="$MAIN_PATH/pming/.workers"
LOCK_DIR="$MAIN_PATH/pming/.workers.lockdir"
MERGE_LOCK_DIR="$MAIN_PATH/pming/.workers.merge-lock"
WORKTREES_DIR="$MAIN_PATH/.worktrees"
ACTIVITY_LOG="$MAIN_PATH/pming/.activity-log"
LOCK_TOKEN=""
MY_HOSTNAME=$(hostname)

# Find the Claude Code process PID (stable across all bash calls in a session).
# Walks up the process tree from $$ looking for "claude" in the command name.
find_session_pid() {
  local pid=$$
  while [ "$pid" != "1" ] && [ -n "$pid" ] && [ "$pid" != "0" ]; do
    local cmd
    cmd=$(ps -o comm= -p "$pid" 2>/dev/null || echo "")
    if [[ "$cmd" == *claude* ]]; then
      echo "$pid"
      return 0
    fi
    pid=$(ps -o ppid= -p "$pid" 2>/dev/null | tr -d ' ')
  done
  # Fallback: if claude not found in tree, use grandparent
  local gp
  gp=$(ps -o ppid= -p "${PPID:-$$}" 2>/dev/null | tr -d ' ')
  echo "${gp:-$$}"
}

SESSION_PID=$(find_session_pid)

# Ensure .workers file exists
[ -f "$WORKERS_FILE" ] || echo "# Parallel Workers — auto-managed by worker.sh" > "$WORKERS_FILE"

# --- Locking ---

acquire_lock() {
  local token
  token=$(uuidgen | tr '[:upper:]' '[:lower:]')
  local attempts=0
  while [ $attempts -lt 20 ]; do
    if mkdir "$LOCK_DIR" 2>/dev/null; then
      echo "$token" > "$LOCK_DIR/token"
      echo "$$" > "$LOCK_DIR/pid"
      echo "$MY_HOSTNAME" > "$LOCK_DIR/host"
      LOCK_TOKEN="$token"
      return 0
    fi
    # Check if lock holder is dead
    if [ -f "$LOCK_DIR/pid" ] && [ -f "$LOCK_DIR/host" ]; then
      local lock_pid lock_host
      lock_pid=$(cat "$LOCK_DIR/pid" 2>/dev/null || echo "0")
      lock_host=$(cat "$LOCK_DIR/host" 2>/dev/null || echo "")
      if [ "$lock_host" = "$MY_HOSTNAME" ] && ! kill -0 "$lock_pid" 2>/dev/null; then
        rm -rf "$LOCK_DIR"
        attempts=$((attempts + 1))
        continue
      fi
    fi
    sleep 0.3
    attempts=$((attempts + 1))
  done
  echo "ERROR: could not acquire .workers lock after 20 attempts" >&2
  return 1
}

release_lock() {
  [ -n "$LOCK_TOKEN" ] || return 0
  if [ -f "$LOCK_DIR/token" ]; then
    local stored
    stored=$(cat "$LOCK_DIR/token" 2>/dev/null || echo "")
    if [ "$stored" = "$LOCK_TOKEN" ]; then
      rm -rf "$LOCK_DIR"
    fi
  fi
  LOCK_TOKEN=""
}

trap 'release_lock 2>/dev/null; true' EXIT

# --- UUID Resolution ---

resolve_uuid() {
  # 1. Check --uuid flag override
  [ -n "${OPT_UUID:-}" ] && echo "$OPT_UUID" && return 0

  # 2. Check if pwd is inside a worktree (works even after git checkout main)
  local cwd
  cwd=$(pwd)
  if [[ "$cwd" == */.worktrees/* ]]; then
    local task_dir="${cwd#*/.worktrees/}"
    task_dir="${task_dir%%/*}"
    if [ -f "$WORKTREES_DIR/$task_dir/.worker-uuid" ]; then
      cat "$WORKTREES_DIR/$task_dir/.worker-uuid"
      return 0
    fi
  fi

  # 3. Check git branch (works when cd'd into worktree root)
  local branch
  branch=$(git branch --show-current 2>/dev/null || echo "")
  if [[ "$branch" == work/* ]] || [[ "$branch" == fix/* ]]; then
    local task_id="${branch#work/}"
    task_id="${task_id#fix/}"
    if [ -f "$WORKTREES_DIR/$task_id/.worker-uuid" ]; then
      cat "$WORKTREES_DIR/$task_id/.worker-uuid"
      return 0
    fi
  fi

  # 4. Check /tmp by session PID (stable Claude Code process PID)
  if [ -f "/tmp/kompara-worker-$SESSION_PID.uuid" ]; then
    cat "/tmp/kompara-worker-$SESSION_PID.uuid"
    return 0
  fi

  return 1
}

persist_uuid() {
  local uuid="$1" task="$2"
  # Always write to /tmp (fallback, works before worktree exists)
  echo "$uuid" > "/tmp/kompara-worker-$SESSION_PID.uuid"
  # Also write to worktree dir if it exists
  if [ -d "$WORKTREES_DIR/$task" ]; then
    echo "$uuid" > "$WORKTREES_DIR/$task/.worker-uuid"
  fi
}

# --- Stale Cleanup (runs inside claim) ---

cleanup_stale() {
  local tmp_file
  tmp_file=$(mktemp)
  local removed=0
  local now
  now=$(date +%s)

  while IFS= read -r line; do
    # Preserve comments
    if [[ "$line" =~ ^# ]] || [[ -z "$line" ]]; then
      echo "$line" >> "$tmp_file"
      continue
    fi
    # Skip non-JSON lines (old format migration)
    if ! echo "$line" | jq -e . >/dev/null 2>&1; then
      removed=$((removed + 1))
      continue
    fi

    local entry_host entry_pid entry_status entry_started
    entry_host=$(echo "$line" | jq -r '.hostname')
    entry_pid=$(echo "$line" | jq -r '.pid')
    entry_status=$(echo "$line" | jq -r '.status // "active"')
    entry_started=$(echo "$line" | jq -r '.started_at')

    # Remove dead PIDs on this host
    if [ "$entry_host" = "$MY_HOSTNAME" ] && [ "$entry_pid" != "0" ]; then
      if ! kill -0 "$entry_pid" 2>/dev/null; then
        removed=$((removed + 1))
        continue
      fi
    fi

    # Remove legacy entries (pid=0)
    if [ "$entry_pid" = "0" ]; then
      removed=$((removed + 1))
      continue
    fi

    # Remove stale pending entries (>5 min old)
    if [ "$entry_status" = "pending" ] && [ -n "$entry_started" ]; then
      local entry_epoch
      entry_epoch=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$entry_started" +%s 2>/dev/null || echo "0")
      local age=$(( now - entry_epoch ))
      if [ "$age" -gt 300 ]; then
        removed=$((removed + 1))
        continue
      fi
    fi

    echo "$line" >> "$tmp_file"
  done < "$WORKERS_FILE"

  mv "$tmp_file" "$WORKERS_FILE"
  echo "$removed"
}

# --- Commands ---

cmd_claim() {
  local task="$1" mode="$2" target="$3" branch="$4"
  local pending="${5:-}"
  local status="active"
  [ "$pending" = "--pending" ] && status="pending"

  local uuid
  uuid=$(uuidgen | tr '[:upper:]' '[:lower:]')
  local id="${uuid:0:8}"

  acquire_lock

  # Opportunistic cleanup
  cleanup_stale >/dev/null

  # Check for conflict
  local existing
  existing=$(grep -v '^#' "$WORKERS_FILE" 2>/dev/null | grep -v '^$' | jq -r "select(.task == \"$task\") | .id" 2>/dev/null || true)
  if [ -n "$existing" ]; then
    release_lock
    echo "CONFLICT: worker $existing already on $task" >&2
    return 1
  fi

  # Build and append entry
  local started_at
  started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  local entry
  entry=$(jq -nc \
    --arg id "$id" \
    --arg hostname "$MY_HOSTNAME" \
    --argjson pid "$SESSION_PID" \
    --arg uuid "$uuid" \
    --arg task "$task" \
    --arg mode "$mode" \
    --arg target "$target" \
    --arg branch "$branch" \
    --arg status "$status" \
    --arg started_at "$started_at" \
    '{id:$id,hostname:$hostname,pid:$pid,uuid:$uuid,task:$task,mode:$mode,target:$target,branch:$branch,status:$status,started_at:$started_at}')

  echo "$entry" >> "$WORKERS_FILE"

  release_lock

  # Persist UUID
  persist_uuid "$uuid" "$task"

  echo "OK $uuid"
}

cmd_activate() {
  local uuid
  uuid=$(resolve_uuid) || { echo "ERROR: no UUID found" >&2; return 1; }

  acquire_lock

  local tmp_file
  tmp_file=$(mktemp)
  local found=0
  while IFS= read -r line; do
    if [[ "$line" =~ ^# ]] || [[ -z "$line" ]]; then
      echo "$line" >> "$tmp_file"
      continue
    fi
    local entry_uuid
    entry_uuid=$(echo "$line" | jq -r '.uuid' 2>/dev/null || echo "")
    if [ "$entry_uuid" = "$uuid" ]; then
      echo "$line" | jq -c '.status = "active"' >> "$tmp_file"
      found=1
    else
      echo "$line" >> "$tmp_file"
    fi
  done < "$WORKERS_FILE"
  mv "$tmp_file" "$WORKERS_FILE"

  release_lock
  [ "$found" -eq 1 ] && echo "OK" || echo "OK (no entry found)"
}

cmd_release() {
  local do_worktree="${OPT_WORKTREE:-}"
  local uuid
  uuid=$(resolve_uuid) || { echo "OK (no UUID)"; return 0; }

  acquire_lock

  local tmp_file
  tmp_file=$(mktemp)
  local removed_task="" removed_branch=""
  while IFS= read -r line; do
    if [[ "$line" =~ ^# ]] || [[ -z "$line" ]]; then
      echo "$line" >> "$tmp_file"
      continue
    fi
    local entry_uuid
    entry_uuid=$(echo "$line" | jq -r '.uuid' 2>/dev/null || echo "")
    if [ "$entry_uuid" = "$uuid" ]; then
      removed_task=$(echo "$line" | jq -r '.task')
      removed_branch=$(echo "$line" | jq -r '.branch')
      continue  # skip = remove
    fi
    echo "$line" >> "$tmp_file"
  done < "$WORKERS_FILE"
  mv "$tmp_file" "$WORKERS_FILE"

  release_lock

  # Clean up worktree if requested
  if [ "$do_worktree" = "yes" ] && [ -n "$removed_task" ]; then
    git worktree remove "$WORKTREES_DIR/$removed_task" 2>/dev/null || true
    git branch -d "$removed_branch" 2>/dev/null || true
  fi

  # Clean up UUID files
  rm -f "$WORKTREES_DIR/${removed_task:-.nonexistent}/.worker-uuid" 2>/dev/null || true
  rm -f "/tmp/kompara-worker-$SESSION_PID.uuid" 2>/dev/null || true

  echo "OK"
}

cmd_update_task() {
  local new_task="$1" new_branch="$2"
  local uuid
  uuid=$(resolve_uuid) || { echo "ERROR: no UUID found" >&2; return 1; }

  acquire_lock

  local tmp_file
  tmp_file=$(mktemp)
  local old_task=""
  while IFS= read -r line; do
    if [[ "$line" =~ ^# ]] || [[ -z "$line" ]]; then
      echo "$line" >> "$tmp_file"
      continue
    fi
    local entry_uuid
    entry_uuid=$(echo "$line" | jq -r '.uuid' 2>/dev/null || echo "")
    if [ "$entry_uuid" = "$uuid" ]; then
      old_task=$(echo "$line" | jq -r '.task')
      echo "$line" | jq -c --arg t "$new_task" --arg b "$new_branch" '.task=$t | .branch=$b' >> "$tmp_file"
    else
      echo "$line" >> "$tmp_file"
    fi
  done < "$WORKERS_FILE"
  mv "$tmp_file" "$WORKERS_FILE"

  release_lock

  # Migrate UUID file to new task's worktree
  if [ -n "$old_task" ] && [ -d "$WORKTREES_DIR/$new_task" ]; then
    persist_uuid "$uuid" "$new_task"
    rm -f "$WORKTREES_DIR/$old_task/.worker-uuid" 2>/dev/null || true
  fi

  echo "OK"
}

cmd_list() {
  local json_mode="${OPT_JSON:-}"

  # Read non-comment, non-empty lines
  local entries
  entries=$(grep -v '^#' "$WORKERS_FILE" 2>/dev/null | grep -v '^$' || true)

  if [ -z "$entries" ]; then
    [ "$json_mode" = "yes" ] && echo "[]" || echo "No active workers."
    return 0
  fi

  if [ "$json_mode" = "yes" ]; then
    echo "$entries" | jq -s '.' 2>/dev/null || echo "[]"
  else
    echo "Active workers:"
    echo "$entries" | while IFS= read -r line; do
      local id task branch started status
      id=$(echo "$line" | jq -r '.id' 2>/dev/null)
      task=$(echo "$line" | jq -r '.task' 2>/dev/null)
      branch=$(echo "$line" | jq -r '.branch' 2>/dev/null)
      started=$(echo "$line" | jq -r '.started_at' 2>/dev/null)
      status=$(echo "$line" | jq -r '.status // "active"' 2>/dev/null)
      echo "  ● $id: $task on $branch since $started [$status]"
    done
  fi
}

cmd_cleanup() {
  acquire_lock
  local removed
  removed=$(cleanup_stale)

  # Prune activity log to 50 entries
  if [ -f "$ACTIVITY_LOG" ]; then
    local line_count
    line_count=$(wc -l < "$ACTIVITY_LOG" | tr -d ' ')
    if [ "$line_count" -gt 50 ]; then
      local tmp_file
      tmp_file=$(mktemp)
      tail -50 "$ACTIVITY_LOG" > "$tmp_file"
      mv "$tmp_file" "$ACTIVITY_LOG"
    fi
  fi

  release_lock
  echo "Removed $removed stale entries."
}

cmd_info() {
  local uuid
  uuid=$(resolve_uuid) || return 1

  local entries
  entries=$(grep -v '^#' "$WORKERS_FILE" 2>/dev/null | grep -v '^$' || true)
  [ -z "$entries" ] && return 1

  local match
  match=$(echo "$entries" | jq -c "select(.uuid == \"$uuid\")" 2>/dev/null || true)
  if [ -n "$match" ]; then
    echo "$match"
    return 0
  fi
  return 1
}

cmd_merge_lock_acquire() {
  local token
  token=$(uuidgen | tr '[:upper:]' '[:lower:]')
  local attempts=0
  local now

  while [ $attempts -lt 200 ]; do
    if mkdir "$MERGE_LOCK_DIR" 2>/dev/null; then
      echo "$token" > "$MERGE_LOCK_DIR/token"
      echo "$$" > "$MERGE_LOCK_DIR/pid"
      echo "$MY_HOSTNAME" > "$MERGE_LOCK_DIR/host"
      date +%s > "$MERGE_LOCK_DIR/acquired_at"
      # Persist token for release
      echo "$token" > "/tmp/kompara-merge-lock-$SESSION_PID.token"
      echo "OK"
      return 0
    fi

    # Check if lock holder is dead or stale
    if [ -d "$MERGE_LOCK_DIR" ]; then
      local lock_pid lock_host lock_time
      lock_pid=$(cat "$MERGE_LOCK_DIR/pid" 2>/dev/null || echo "0")
      lock_host=$(cat "$MERGE_LOCK_DIR/host" 2>/dev/null || echo "")
      lock_time=$(cat "$MERGE_LOCK_DIR/acquired_at" 2>/dev/null || echo "0")
      now=$(date +%s)
      local age=$(( now - lock_time ))

      # Dead owner on same host
      if [ "$lock_host" = "$MY_HOSTNAME" ] && [ "$lock_pid" != "0" ] && ! kill -0 "$lock_pid" 2>/dev/null; then
        rm -rf "$MERGE_LOCK_DIR"
        attempts=$((attempts + 1))
        continue
      fi

      # Stale lock (>5 min)
      if [ "$age" -gt 300 ]; then
        rm -rf "$MERGE_LOCK_DIR"
        attempts=$((attempts + 1))
        continue
      fi
    fi

    sleep 0.3
    attempts=$((attempts + 1))
  done

  echo "TIMEOUT: merge lock held too long" >&2
  return 1
}

cmd_merge_lock_release() {
  [ -d "$MERGE_LOCK_DIR" ] || { echo "OK (no lock)"; return 0; }

  local my_token stored_token
  my_token=$(cat "/tmp/kompara-merge-lock-$SESSION_PID.token" 2>/dev/null || echo "")
  stored_token=$(cat "$MERGE_LOCK_DIR/token" 2>/dev/null || echo "")

  if [ -n "$my_token" ] && [ "$my_token" = "$stored_token" ]; then
    rm -rf "$MERGE_LOCK_DIR"
    rm -f "/tmp/kompara-merge-lock-$SESSION_PID.token" 2>/dev/null || true
    echo "OK"
  else
    echo "OK (not owner)"
  fi
}

cmd_log_activity() {
  local task="$1" title="$2" commit="$3"

  # Resolve worker ID from UUID
  local uuid worker_id
  uuid=$(resolve_uuid 2>/dev/null) || true
  if [ -n "${uuid:-}" ]; then
    worker_id="${uuid:0:8}"
  else
    worker_id="unknown"
  fi

  # Get files changed from the commit
  local files_raw files_json
  files_raw=$(git diff-tree --no-commit-id --name-only -r "$commit" 2>/dev/null || true)
  if [ -n "$files_raw" ]; then
    files_json=$(echo "$files_raw" | jq -R -s 'split("\n") | map(select(. != ""))' 2>/dev/null || echo '[]')
  else
    files_json='[]'
  fi

  local timestamp
  timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

  # Build entry
  local entry
  entry=$(jq -nc \
    --arg task "$task" \
    --arg title "$title" \
    --argjson files_changed "$files_json" \
    --arg worker_id "$worker_id" \
    --arg timestamp "$timestamp" \
    --arg commit "$commit" \
    '{task:$task,title:$title,files_changed:$files_changed,worker_id:$worker_id,timestamp:$timestamp,commit:$commit}')

  acquire_lock

  # Create file if it doesn't exist
  [ -f "$ACTIVITY_LOG" ] || touch "$ACTIVITY_LOG"

  # Append entry
  echo "$entry" >> "$ACTIVITY_LOG"

  # Prune: keep last 50 entries
  local line_count
  line_count=$(wc -l < "$ACTIVITY_LOG" | tr -d ' ')
  if [ "$line_count" -gt 50 ]; then
    local tmp_file
    tmp_file=$(mktemp)
    tail -50 "$ACTIVITY_LOG" > "$tmp_file"
    mv "$tmp_file" "$ACTIVITY_LOG"
  fi

  release_lock
  echo "OK"
}

cmd_recent_activity() {
  local count="${1:-10}"

  if [ ! -f "$ACTIVITY_LOG" ] || [ ! -s "$ACTIVITY_LOG" ]; then
    echo "[]"
    return 0
  fi

  tail -"$count" "$ACTIVITY_LOG" | jq -s '.' 2>/dev/null || echo "[]"
}

cmd_validate() {
  local findings=0

  # Read workers
  local entries
  entries=$(grep -v '^#' "$WORKERS_FILE" 2>/dev/null | grep -v '^$' || true)

  # Check each worker entry
  if [ -n "$entries" ]; then
    echo "$entries" | while IFS= read -r line; do
      local id task branch
      id=$(echo "$line" | jq -r '.id' 2>/dev/null)
      task=$(echo "$line" | jq -r '.task' 2>/dev/null)
      branch=$(echo "$line" | jq -r '.branch' 2>/dev/null)

      # Check worktree exists
      if [ ! -d "$WORKTREES_DIR/$task" ]; then
        echo "WORKER_NO_WORKTREE: $id claims $task but worktree missing"
      fi

      # Check task file is not in done/
      if [ -f "$MAIN_PATH/pming/tasks/done/$task.md" ]; then
        echo "WORKER_DONE_TASK: $id claims $task but task is in done/"
      fi
    done
  fi

  # Check for orphaned worktrees (worktrees with no worker entry)
  if [ -d "$WORKTREES_DIR" ]; then
    for dir in "$WORKTREES_DIR"/*/; do
      [ -d "$dir" ] || continue
      local wt_name
      wt_name=$(basename "$dir")
      local has_worker
      has_worker=$(echo "$entries" | jq -r "select(.task == \"$wt_name\") | .id" 2>/dev/null || true)
      if [ -z "$has_worker" ]; then
        echo "WORKTREE_NO_WORKER: .worktrees/$wt_name exists but no worker entry"
      fi
    done
  fi
}

# --- Main Dispatch ---

OPT_UUID=""
OPT_WORKTREE=""
OPT_JSON=""

main() {
  local cmd="${1:-help}"
  shift || true

  # Parse global flags from remaining args
  local positional=()
  while [ $# -gt 0 ]; do
    case "$1" in
      --uuid) OPT_UUID="$2"; shift 2 ;;
      --worktree) OPT_WORKTREE="yes"; shift ;;
      --json) OPT_JSON="yes"; shift ;;
      --pending) positional+=("--pending"); shift ;;
      *) positional+=("$1"); shift ;;
    esac
  done

  case "$cmd" in
    claim)
      [ ${#positional[@]} -ge 4 ] || { echo "Usage: worker.sh claim <task> <mode> <target> <branch> [--pending]" >&2; return 1; }
      cmd_claim "${positional[0]}" "${positional[1]}" "${positional[2]}" "${positional[3]}" "${positional[4]:-}"
      ;;
    activate)    cmd_activate ;;
    release)     cmd_release ;;
    update-task)
      [ ${#positional[@]} -ge 2 ] || { echo "Usage: worker.sh update-task <new-task> <new-branch>" >&2; return 1; }
      cmd_update_task "${positional[0]}" "${positional[1]}"
      ;;
    list)        cmd_list ;;
    cleanup)     cmd_cleanup ;;
    info)        cmd_info ;;
    merge-lock)
      local subcmd="${positional[0]:-}"
      case "$subcmd" in
        acquire) cmd_merge_lock_acquire ;;
        release) cmd_merge_lock_release ;;
        *) echo "Usage: worker.sh merge-lock acquire|release" >&2; return 1 ;;
      esac
      ;;
    validate)    cmd_validate ;;
    log-activity)
      [ ${#positional[@]} -ge 3 ] || { echo "Usage: worker.sh log-activity <task> <title> <commit>" >&2; return 1; }
      cmd_log_activity "${positional[0]}" "${positional[1]}" "${positional[2]}"
      ;;
    recent-activity)
      cmd_recent_activity "${positional[0]:-10}"
      ;;
    help|--help|-h)
      echo "Usage: pming/worker.sh <command> [options]"
      echo ""
      echo "Commands:"
      echo "  claim <task> <mode> <target> <branch> [--pending]  Register a worker"
      echo "  activate                                           Pending → active"
      echo "  release [--worktree] [--uuid UUID]                 Remove own entry"
      echo "  update-task <new-task> <new-branch>                Update task in group"
      echo "  list [--json]                                      Show active workers"
      echo "  cleanup                                            Remove stale entries"
      echo "  info [--uuid UUID]                                 Show own entry"
      echo "  merge-lock acquire|release                         Serialize merges"
      echo "  validate                                           Cross-check state"
      echo "  log-activity <task> <title> <commit>               Log shipped task"
      echo "  recent-activity [count]                            Show recent activity"
      ;;
    *) echo "Unknown command: $cmd. Run 'worker.sh help' for usage." >&2; return 1 ;;
  esac
}

main "$@"
