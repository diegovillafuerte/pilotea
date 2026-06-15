# Parked: verdict chip tap-to-expand detail

**Status:** removed from the live overlay chip on 2026-06-15 (branch `feat/chip-brand-strip`).
Parked here for reuse — **not abandoned**.

## Why it was removed
The collapsed chip floats over the driver's Uber/DiDi screen and is draggable. Tap-to-expand caused:
- **Glitches** — the expanded panel covers part of the host screen and forces a relayout/recalculation
  of the floating window each toggle.
- **Accidental triggers** — a driver trying to *move* the chip (drag) frequently registered a tap and
  toggled the panel instead.
- **Low payoff** — the expanded rows (net profit, $/min, gross $/km, a "¿por qué?" line) don't change
  the accept/decline decision the colour + net rate already drive.

Drag (move) and long-press (quick-threshold sheet) are unchanged. Only the tap→expand behaviour went.

## How to reinstate
The data is still there: `VerdictChipState` **retains** `netProfit`, `netPerMin`, `grossPerKm`,
`explainKind` (+ the `ExplainKind` enum) and `VerdictChipState.from(...)` still computes them, so
reinstating is purely a UI add-back:

1. In `VerdictChipUi.kt`, re-add the `expanded` state and an `onTap` to the gesture detector
   (alongside the existing `onLongPress`), then render `ExpandedDetail(state)` under `CollapsedContent`.
2. Paste the three composables below back into `VerdictChipUi.kt`.
3. The string resources they use are still in `overlay/res/values/strings.xml`
   (`overlay_net_profit_label`, `overlay_per_min_label`, `overlay_gross_per_km_label`,
   `overlay_explain_both_strong` / `_km_weak` / `_hour_weak` / `_both_weak`).
4. Restore the "Toca para ver el detalle" clause in `overlay_chip_content_description`.

Consider gating expand behind a deliberate affordance (a small chevron/handle) rather than a tap on the
whole chip, so it can't fire while dragging.

Full pre-removal source: `git show c38a4e4:android/overlay/src/main/java/mx/kompara/overlay/VerdictChipUi.kt`

## Removed code (verbatim)

Gesture + render hook (inside `VerdictChipUi`):

```kotlin
var expanded by remember { mutableStateOf(false) }
// ...
.pointerInput(Unit) {
    detectTapGestures(
        onTap = { expanded = !expanded },
        onLongPress = { sheetOpen = true },
    )
}
// ...
CollapsedContent(state)

if (expanded) {
    Spacer(Modifier.height(8.dp))
    ExpandedDetail(state)
}
```

The detail composables:

```kotlin
@Composable
private fun ExpandedDetail(state: VerdictChipState) {
    val onColor = state.level.onBrandColor
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ExplainLine(state.explainKind, color = onColor)
        DetailRow(label = stringResource(R.string.overlay_net_profit_label), value = state.netProfit, color = onColor)
        DetailRow(label = stringResource(R.string.overlay_per_min_label), value = state.netPerMin, color = onColor)
        DetailRow(label = stringResource(R.string.overlay_gross_per_km_label), value = state.grossPerKm, color = onColor)
    }
}

/** The one-line "¿por qué?" — which floor passed/failed — at the top of the expanded detail. */
@Composable
private fun ExplainLine(kind: VerdictChipState.ExplainKind, color: Color) {
    val res = when (kind) {
        VerdictChipState.ExplainKind.NONE -> return
        VerdictChipState.ExplainKind.BOTH_STRONG -> R.string.overlay_explain_both_strong
        VerdictChipState.ExplainKind.KM_WEAK -> R.string.overlay_explain_km_weak
        VerdictChipState.ExplainKind.HOUR_WEAK -> R.string.overlay_explain_hour_weak
        VerdictChipState.ExplainKind.BOTH_WEAK -> R.string.overlay_explain_both_weak
    }
    Text(
        text = stringResource(res),
        color = color,
        fontSize = 11.sp,
        modifier = Modifier.widthIn(max = 180.dp).padding(bottom = 2.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = color, fontSize = 12.sp)
        Spacer(Modifier.width(12.dp))
        Text(text = value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
```
