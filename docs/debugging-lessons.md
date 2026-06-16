# Debugging lessons

Hard-won lessons from real debugging sessions on Kompara. Written to make the next hard bug
faster to find and the fix more durable. The founding case study is the **verdict-chip "reads $0 /
blinks / covers the fare" saga (2026-06-15)** — referenced throughout as "the chip saga."

---

## The headline principle: complete fix > quick fix

**Always take the most complete fix, not the quickest.** When a symptom appears, the tempting move
is the narrowest change that makes the immediate report go away. Resist it. Spend the time to
understand the whole data flow first, then fix the root cause across the entire pipeline — even when
that's more work.

In the chip saga the quick fixes each *looked* reasonable and each *partly* worked, which is exactly
why they were dangerous:

- Revert the OCR self-mask (PR #14) — made the blink go away, reintroduced the $0.
- Add a "drop the chip below the fare" branch — fixed "above," left "below" flickering.
- Add a drag constraint — chip still covered the fare.

Every one of those was a patch on a symptom. The actual bug was three independent causes stacked
(occlusion, self-capture, and a coordinate-space offset), and only understanding the **full
capture → coordinate-space → parse → position** pipeline produced a fix that held. The churn
(PR #12 → revert #14 → re-add) was the cost of patching before understanding.

> If you can't explain *why* the bug happens end to end, you're guessing at the fix — even if the
> symptom disappears.

---

## Lessons

### 1. Get ground truth before theorizing
The breakthrough came the moment we logged the chip's **actual** on-screen rect next to the fare's
rect with real pixel values. Until then we reasoned from a mental model ("`avoid()` pins the chip
above the fare") that was *correct in code and wrong in reality* because of an invisible 91px offset.
Hours went into plausible-but-unverified hypotheses; one log line ended it.

**Apply:** When observed behavior contradicts what the code obviously does, stop reasoning harder and
**measure the runtime values**. Add the instrumentation; don't out-think the system.

### 2. Instrument early — logging is a first-class tool, not a last resort
We delayed adding chip-rect logging for several rebuild cycles, trying to deduce the geometry. For
any **geometric, stateful, or timing-dependent** bug, the rebuild cost of adding instrumentation is
far cheaper than another iteration on a wrong guess.

**Apply:** Reach for targeted logging on the *first* sign a bug is spatial/stateful/timing-related.
Tag it `TEMP DIAG` and remove it cleanly once the fix lands (we did — keep that discipline).

### 3. One symptom can have several independent causes
"Chip covers the fare" had three stacked roots: (a) physical occlusion needing positioning,
(b) self-capture (the chip's own "MXN" text parsed as the fare), (c) a params-space ↔ screen-space
offset. Each fix was necessary and insufficient. The trap: a correct fix that doesn't fully resolve
the symptom looks like a *failed* fix, inviting you to thrash on it.

**Apply:** When a fix you're confident in doesn't fully resolve the symptom, suspect a **second
independent cause** before assuming the fix was wrong. Peel the layers.

### 4. Verify in the real environment — tests validate the model, not the model's premises
Every unit test passed at every step. An adversarial multi-agent review even "proved SHIP" with
exhaustive arithmetic — **all correct math in the wrong coordinate space.** The bug lived in the gap
between the verified pure function and the real Android coordinate system, exactly where unit tests
and formal reasoning can't reach.

**Apply:** For integration / coordinate / timing bugs, on-device (or real-environment) verification
is irreplaceable. Treat "tests green" and even "formally verified" as necessary, never sufficient —
they can't catch a wrong premise.

### 5. Coordinate spaces, units, and frames are classic silent mismatches
Two subsystems (MediaProjection capture, overlay window) were each internally consistent but used
different y-origins — the window sits below the status bar (91px), the capture is full-screen. A
code comment even *asserted* "they share one coordinate space." It was never true, and the comment
made it look verified.

**Apply:** When two subsystems exchange spatial/numeric data, **explicitly verify they share units,
origin, and frame — measure a round-trip.** A comment claiming an invariant is documentation, not
verification. Distrust assumed-shared coordinate systems by default.

### 6. The user's precise observations are high-value data
"Above works, below flickers" and "still covers the fare when near the bottom" were not vague
complaints — they were diagnostic. Position-dependent failure pointed straight at an offset that
only manifests low on the screen. We could have mapped that to the cause sooner.

**Apply:** Treat each specific phenomenological report as a clue and ask "what does *this exact
pattern* imply about the cause?" Position-, timing-, or state-dependent symptoms narrow the search
dramatically.

### 7. Build a repeatable capture/repro harness
The tight loop only clicked once we had: a continuous background `logcat` capture to a file, a known
repro recipe (offer up → drag chip down), and a clear diagnostic log line. Before that, each attempt
yielded a yes/no, not data.

**Apply:** Invest early in a repeatable capture + repro setup so every iteration produces evidence,
not just an outcome. On a connected device this is a live oversight loop: the user drives, you watch
the log stream.

### 8. Don't merge on reasoning alone when the effect is only observable in a hard-to-test place
Earlier in the saga, changes were merged before on-device confirmation and had to be reverted.
Switching to "one verified build at a time; verify before merge" stopped the churn.

**Apply:** If a change's real effect is only visible in an environment your tests don't cover, gate
the merge on verification there — not on the strength of tests + reasoning.

### 9. Reuse history instead of rewriting
The correct fix needed plumbing that had been built (and reverted) before. Recovering it from git
(`git show <commit>`) and re-integrating the already-reviewed code beat rewriting it from scratch.

**Apply:** Before rebuilding something, check whether a prior, reviewed version exists in history.
Re-integrate and adapt it.

---

## What worked — keep doing

- **Adversarial review (codex / multi-agent) before shipping** caught real regressions unit tests
  missed (e.g., a ledger duplicate, an over-holding card signature). Keep running it — but remember
  Lesson 4: it verifies the model you give it.
- **Screenshots / direct observation** of the actual on-screen state, not just logs.
- **Honest course-correction.** When a fix went out unverified, naming it plainly and changing the
  process (verify-before-merge) was what turned the saga around.

---

## A checklist for the next hard bug

1. Reproduce reliably. Build the capture/repro harness first.
2. Instrument to capture the **actual runtime values** at the suspected boundary.
3. Trace the **full pipeline** end to end; write down where each value comes from and what space/unit
   it's in.
4. Form a hypothesis that explains **every** observed symptom (incl. position/timing dependence).
5. Suspect multiple independent causes if a confident fix doesn't fully resolve the symptom.
6. Choose the **complete** fix at the root, across the pipeline — not the local patch.
7. Verify in the **real environment**, not only in tests.
8. Remove diagnostics, then land.
