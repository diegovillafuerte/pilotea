The product's hero: the floating chip that overlays Uber/DiDi and gives an instant profitability verdict before the driver accepts.

```jsx
<VerdictChip level="green" heroRate="$9.20/km" secondaryRate="$165/h" />
<VerdictChip level="red" heroRate="$3.10/km" secondaryRate="$78/h" missingHint="Sin distancia" />
```

Always carries the brand strip (so a shared screenshot self-brands). No verdict word — colour carries the meaning, the big net rate is the read. The hero rate is the driver's *preferred* metric ($/km or $/h); the other sits under it as context.
