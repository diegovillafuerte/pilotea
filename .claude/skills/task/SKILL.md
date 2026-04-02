---
name: task
description: Create a new task, story, bug, or epic. Interprets the user's description, classifies the item, determines placement in the hierarchy (epic/story), asks clarifying questions if needed, and creates a properly-organized work item in pming/.
argument-hint: [description of what needs to be done]
---

# Task Creation

You are the smart intake system for the Kompara project. Your job is to help the user capture work items quickly while keeping them well-organized.

## Step 1: Understand the request

Read the user's input from $ARGUMENTS. Determine:
- **What** needs to be done
- **What type** of item this is (epic, story, task, or bug)
- **Where** it fits in the existing hierarchy

## Step 2: Scan existing structure

Read existing epics (`pming/epics/E-*.md`) and stories (`pming/stories/S-*.md`) frontmatter to understand current organization. Use Glob to find files, then Read just the frontmatter of each.

## Step 3: Classify the item

- **Epic**: Large initiative spanning weeks with multiple stories. User usually says "epic" explicitly. Rare.
- **Story**: Coherent capability or feature within an epic. Contains multiple tasks.
- **Task**: Concrete, actionable work item. Most common. Usually takes hours to a day.
- **Bug**: Defect in existing functionality. User mentions something broken/wrong/not working.

Default to **task** unless context clearly indicates otherwise.

## Step 4: Determine placement

- For **tasks** and **bugs**: determine which epic and story they belong to. If no existing story fits, propose creating a new one.
- For **stories**: determine which epic they belong to.
- For **epics**: standalone, no parent needed.

## Step 5: Ask clarifying questions ONLY if needed

Ask the user if:
- The type is genuinely ambiguous
- No existing epic/story fits and you want to confirm creating new ones
- The description is too vague to create an actionable item

Do NOT ask about:
- Priority — default to medium, user can adjust later
- Category — infer from context: `code` for technical work, `business` for partnerships/legal, `ops` for infrastructure/deployment
- Due dates — leave empty unless user mentions a deadline

Be opinionated. Suggest a placement and let the user correct you rather than asking for every detail.

## Step 6: Create the item

### Auto-increment ID
- For tasks/bugs: Glob `pming/tasks/B-*.md`, extract highest number, add 1. Pad to 3 digits.
- For stories: Glob `pming/stories/S-*.md`, extract highest number, add 1. Pad to 3 digits.
- For epics: Glob `pming/epics/E-*.md`, extract highest number, add 1. Pad to 3 digits.
- Bugs use B-XXX numbering (same sequence as tasks) with `type: bug` in frontmatter.

### File formats

**Epic** (`pming/epics/E-XXX.md`):
```yaml
---
id: E-XXX
title: Short epic title
status: backlog
priority: medium
created: YYYY-MM-DD
---

Description of the initiative and its goals.
```

**Story** (`pming/stories/S-XXX.md`):
```yaml
---
id: S-XXX
title: Capability description
status: backlog
priority: medium
epic: E-XXX
category: code
created: YYYY-MM-DD
---

What this story delivers and why it matters.
```

**Task** (`pming/tasks/B-XXX.md`):
```yaml
---
id: B-XXX
title: Short, actionable title
type: task
status: backlog
priority: medium
epic: E-XXX
story: S-XXX
category: code
created: YYYY-MM-DD
---

What needs to be done. 1-3 sentences.
```

**Bug** (`pming/tasks/B-XXX.md`):
```yaml
---
id: B-XXX
title: Short bug description
type: bug
status: backlog
priority: high
epic: E-XXX
story: S-XXX
category: code
created: YYYY-MM-DD
---

What's broken and how to reproduce it.
```

### Defaults
- status: `backlog` (use `todo` if user says it's urgent/immediate)
- priority: `medium`
- category: `code`
- type: `task`

## Step 7: Confirm

After creating, show a brief confirmation:
```
Created B-054: "Add rate limiting to webhook"
  Type: task | Epic: E-002 Infrastructure | Story: S-005 Security hardening
  Priority: medium | Category: code
```

## Guidelines
- Keep titles short, actionable, starting with a verb when practical
- Descriptions: 1-3 sentences, enough to be actionable
- Don't over-elaborate simple tasks
- One-liners from the user are fine
- Infer category from keywords: "deploy", "env var", "set up" → ops; "partner", "sign up", "legal" → business; default → code
- When creating a bug, default priority to `high` unless clearly minor

## Task granularity

Tasks that are very similar in shape (e.g., "create tool A", "create tool B", "create tool C" where each follows the same pattern) should be **merged into a single task** rather than split into separate items. Each task carries overhead: worktree setup, adversarial review, merge/push, PM bookkeeping. If the only difference between tasks is a name or a config value, they belong together.

**Merge when:**
- Multiple tasks follow the same pattern with different inputs (e.g., "add tool X" × 3)
- Tasks touch the same file with the same type of change (e.g., "add field A to config", "add field B to config")
- One task's output is trivially needed by the next and both are small
- The combined task still fits in a single review cycle (roughly one day of work or less, with a single coherent set of acceptance criteria)

**Keep separate when:**
- Tasks require different skills or domain knowledge
- Tasks touch different parts of the codebase with different risk profiles
- A task is large enough to warrant its own review cycle
- Merging would push the combined scope beyond a day of work or mix unrelated acceptance criteria
