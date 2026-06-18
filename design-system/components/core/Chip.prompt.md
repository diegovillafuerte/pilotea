Selectable pill for the platform switcher and similar single-axis filters.

```jsx
<Chip selected onClick={() => select(null)}>Todas</Chip>
<Chip onClick={() => select("uber")}>Uber</Chip>
<Chip onClick={() => select("didi")}>DiDi</Chip>
```

Selected = brand-green tonal fill + green text; unselected = outlined neutral. Fully round (pill).
