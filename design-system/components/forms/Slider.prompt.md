Range slider with a label + big bold value readout — the threshold floor editor (verde/rojo, $/km or $/h).

```jsx
const [green, setGreen] = React.useState(9.2);
<Slider label="Verde desde" valueText={`$${green.toFixed(1)}/km`}
  value={green} min={3} max={20} step={0.1} onChange={setGreen} />
```
