---
name: review-doc
description: Structure Claude's response as a reviewable document in the Canvas app instead of dumping it into chat. Writes current.json, waits for review.json, feeds structured feedback back.
argument-hint: [optional: document title or topic]
---

# Review-Doc: Structured Document Review via Canvas

Structure your response as a multi-section document, send it to the Canvas app for human review, and ingest the structured feedback.

## Step 1: Structure the response as a CanvasDocument

Based on the conversation context and $ARGUMENTS, organize your response into titled sections. Each section should be a coherent unit that can be independently approved, rejected, or questioned.

Guidelines for sectioning:
- 3-8 sections is the sweet spot — fewer feels thin, more feels overwhelming
- Each section gets a stable ID (`sec-001`, `sec-002`, etc.)
- Headings should be scannable (2-5 words)
- Content is markdown — use lists, tables, code blocks as needed
- Keep sections focused on one topic each

Generate a UUID v4 for the document ID.

Build the JSON object matching the `CanvasDocument` schema from `src/canvas/types.ts`:

```json
{
  "id": "<uuid-v4>",
  "version": 1,
  "title": "<document title>",
  "createdAt": "<ISO 8601 now>",
  "updatedAt": "<ISO 8601 now>",
  "sections": [
    {
      "id": "sec-001",
      "heading": "<heading>",
      "content": "<markdown content>"
    }
  ]
}
```

## Step 2: Write current.json

1. Create the canvas directory if it doesn't exist:
   ```bash
   mkdir -p ~/.claude/canvas
   ```

2. Delete any stale `review.json` from a previous session:
   ```bash
   rm -f ~/.claude/canvas/review.json
   ```

3. Write the document. Use the Write tool to create `~/.claude/canvas/current.json` with the JSON from Step 1. Ensure the JSON is valid and pretty-printed.

## Step 3: Notify the user

Print a short message in chat:

```
Document "{{title}}" sent to canvas for review ({{N}} sections).
Open the canvas app to review, then submit when ready.
```

If the canvas app has a known launch command, attempt to open it:
```bash
# macOS — try opening if the app exists
open -a "Claude Canvas" 2>/dev/null || true
```

Do NOT block on whether the app opened — it's informational.

## Step 4: Wait for review.json

Poll for the review file. Use a background-compatible polling approach:

```bash
# Poll every 3 seconds for up to 10 minutes (200 iterations)
CANVAS_DIR="$HOME/.claude/canvas"
for i in $(seq 1 200); do
  if [ -f "$CANVAS_DIR/review.json" ]; then
    echo "REVIEW_READY"
    cat "$CANVAS_DIR/review.json"
    exit 0
  fi
  sleep 3
done
echo "REVIEW_TIMEOUT"
```

Set `timeout: 600000` (10 minutes) on the Bash tool call.

**If `REVIEW_TIMEOUT`:**
- Tell the user: "No review received after 10 minutes. You can still submit a review — run `/review-doc` again to re-check, or continue the conversation."
- STOP here. Do not proceed to Step 5.

**If `REVIEW_READY`:**
- Parse the JSON output (everything after the `REVIEW_READY` line).
- Proceed to Step 5.

## Step 5: Present structured feedback

Read the `CanvasReview` JSON and present the feedback section by section, mapped back to the original document sections.

Format:

```
## Review received

### sec-001: {{heading}}
**approve** — {{comment or "No comment"}}

### sec-002: {{heading}}
**reject** — {{comment}}

### sec-003: {{heading}}
**question** — {{comment}}

---

### General comments
- {{comment 1}}
- {{comment 2}}
```

Use visual indicators:
- `approve` — present the heading plainly
- `reject` — emphasize the comment (this needs attention)
- `question` — frame as something to address or clarify

## Step 6: Feed into context

After presenting the review:

1. Summarize the actionable items:
   - Rejected sections that need revision
   - Questions that need answers
   - Suggestions from general comments

2. Ask: "Would you like me to revise the document based on this feedback?"

3. If the user says yes:
   - Revise the relevant sections
   - Increment the version number
   - Update `updatedAt`
   - **Include `commentReplies` in document metadata** (see below)
   - Write a new `current.json` (go back to Step 2)
   - Delete the consumed `review.json` so the canvas shows the updated document

### Comment Replies

When the review contains inline comments (found in `sectionReviews[].metadata.inlineComments`), the revised document MUST include replies in `metadata.commentReplies`. This creates a visible conversation thread in the Canvas UI.

Each inline comment has an `id` field. Map each comment ID to your reply:

```json
{
  "id": "<uuid>",
  "version": 2,
  "title": "...",
  "metadata": {
    "commentReplies": {
      "cmt-abc123": "Good point — I expanded this section to address your concern.",
      "cmt-def456": "Removed the ambiguous phrasing as suggested.",
      "cmt-ghi789": "This is intentional because X — added a clarifying note."
    }
  },
  "sections": [...]
}
```

Rules for comment replies:
- Reply to EVERY inline comment, even if your reply is brief ("Acknowledged" or "Fixed")
- For question-type comments, provide a substantive answer
- For suggestions you adopted, confirm what you changed
- For suggestions you declined, explain why
- Keep replies concise (1-2 sentences)

4. Clean up when the review cycle is complete:
   ```bash
   rm -f ~/.claude/canvas/review.json
   ```

## Edge cases

- **Canvas app not installed / not open:** The document is still written to disk. The user can open the file manually or with any JSON viewer. Don't block on the app.
- **User cancels review (never submits review.json):** The timeout in Step 4 handles this gracefully.
- **Empty review (no section reviews, no comments):** Accept it — tell the user "Review submitted with no feedback. Document accepted as-is."
- **review.json references wrong document ID or version:** Warn the user: "Review doesn't match the current document (expected ID {{id}}, got {{review.documentId}}). Ignoring — please re-review the latest version."
- **Malformed review.json:** If JSON parsing fails, show the error and ask the user to re-submit.

## Rules

- Never modify files outside `~/.claude/canvas/` — this skill only reads/writes `current.json` and `review.json`
- Always delete stale `review.json` before writing a new `current.json` to avoid reading old feedback
- Section IDs must be stable across versions (same section = same ID) so the review maps correctly
- The skill is read-only with respect to the Pilotea codebase — it does not modify source files
- Keep the chat output minimal during polling — the document lives in the canvas, not in chat
