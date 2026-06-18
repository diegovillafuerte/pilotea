Thin rounded progress track. Weekly goal, data completeness, IMSS coverage.

```jsx
<ProgressBar value={0.69} />                 {/* goal — brand green */}
<ProgressBar value={1} tone="verde" />        {/* IMSS cubierto */}
<ProgressBar value={0.71} tone="amarillo" />  {/* en camino */}
```

`value` is 0–1. Tones: `primary` · `verde` · `amarillo` · `rojo`.
