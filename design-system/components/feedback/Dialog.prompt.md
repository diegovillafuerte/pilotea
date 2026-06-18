Modal over a scrim — the Play-required prominent-disclosure consent, delete confirmations, etc.

```jsx
<Dialog open={show} title="Kompara leerá tu pantalla"
  confirmText="Continuar" cancelText="Ahora no"
  onConfirm={accept} onCancel={() => setShow(false)}>
  Para mostrarte el semáforo sobre las ofertas, Kompara captura tu pantalla y la analiza únicamente en tu teléfono.
</Dialog>
```

Scrims its positioned ancestor (wrap the phone frame in `position:relative`). Pass `destructive` to make the confirm red.
