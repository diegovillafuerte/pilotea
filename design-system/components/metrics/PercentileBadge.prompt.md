The "Top X%" standing pill that sits on a metric card.

```jsx
<PercentileBadge topPercent={22} />   {/* better than 78% of drivers */}
<PercentileBadge locked />            {/* gated → "🔒 Kompara Premium" */}
```

Brand-green fill. Pass the pre-computed `topPercent` (`100 − displayPercentile`, floored at 1). The `locked` variant teases the gated feature without leaking the number.
