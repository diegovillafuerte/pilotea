One filled green CTA per surface; everything else is outlined, tonal, or text — pick the tier by importance, not by looks.

```jsx
<Button variant="primary" fullWidth>Encender lector</Button>
<Button variant="secondary" fullWidth>Probar en el simulador</Button>
<Button variant="tonal">Configurar costos</Button>
<Button variant="text">Ahora no</Button>
```

Variants: `primary` (filled brand green, ≥52px, the single main action) · `secondary` (outlined + green text) · `tonal` (soft fill on a card) · `text` (inline, low-stakes). Sizes `md` (default) / `sm`. Use `fullWidth` for the app's full-bleed CTAs; pass a leading glyph via `icon`. Honors the active theme (`.theme-dark`).
