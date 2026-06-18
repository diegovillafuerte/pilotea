The tonal container behind most content. Separates from the background by a colour step, not a shadow.

```jsx
<Card>
  <span>Ganancia neta esta semana</span>
</Card>

<Card tone="accent" interactive onClick={openCosts}>
  Configura tus costos para ver ganancias netas
</Card>
```

`tone`: `default` (surface-card) · `variant` (one step up) · `accent` (brand-green tinted nudge). Set `interactive` for a pressable card. 12px radius, 16px interior padding by default.
