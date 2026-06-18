The 6-cell code input for WhatsApp OTP verification. Digits only; paste and backspace work.

```jsx
const [code, setCode] = React.useState("");
<OtpInput value={code} onChange={setCode} />
```

Active cell shows a brand-green focus ring. Pass `length` to change cell count.
