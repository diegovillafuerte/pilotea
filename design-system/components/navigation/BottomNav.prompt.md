The app's five-tab bottom bar. Flat tabs, no raised centre button; active tab tinted brand-green.

```jsx
const [tab, setTab] = React.useState("inicio");
<BottomNav current={tab} onSelect={setTab} />
```

Tabs default to Inicio · Comparar · Lector · Fiscal · Ajustes (exported as `KOMPARA_TABS`). Pass your own `tabs` to relabel. Sits on a tonal surface with a top border.
