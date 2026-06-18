A label, a big tabular value, and an optional trailing percentile badge. The unit of the stats screens.

```jsx
<MetricCard label="$ POR HORA" value="$165.00" badge={<PercentileBadge topPercent={22} />} />
<MetricCard label="VIAJES POR HORA" value="1.7" />
```

Labels are short uppercase ("$ POR KM", "$ POR VIAJE"). Caller formats the value string. Value uses tabular figures.
