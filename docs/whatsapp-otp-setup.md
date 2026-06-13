# WhatsApp OTP Delivery — Setup Runbook

How to get real WhatsApp login codes flowing to drivers' phones. Extends
[launch-runbook.md](launch-runbook.md) §3 with the WhatsApp-specific detail.

> **Legend:** 🟢 = done in code (no action needed) · 🔴 = needs Juan (account /
> credentials / external approval — these can't be automated and some have
> multi-day lead time).

## How delivery works (and the one gotcha)

A login OTP is a **business-initiated** WhatsApp message to someone who hasn't
messaged you in the last 24h. WhatsApp **rejects** those unless they use a
**pre-approved template** — a freeform message fails with Twilio
[error 63016](https://www.twilio.com/docs/api/errors/63016). So production needs
an approved template, sent via `ContentSid` + `ContentVariables`.

- 🟢 **Code is ready.** `backend/src/auth/message-sender.ts` sends the approved
  template when `TWILIO_TEMPLATE_SID` is set, and falls back to a freeform body
  (sandbox only) when it isn't. Set the env var and it just works.

---

## Fast path: get a real code on YOUR phone today (Twilio sandbox)

Use this to actually receive a WhatsApp code on +52… **before** the slow
production approvals. The sandbox lets you send freeform messages to numbers
that have opted in, so no approved template is required yet.

1. 🔴 Create a **Twilio account** (free trial is fine) → Console.
2. 🔴 Open **Messaging → Try it out → WhatsApp sandbox**. Note the sandbox
   number (`+1 415 523 8886`) and your **join code** (e.g. `join silver-tiger`).
3. 🔴 From the test phone (e.g. **+52 33 1010 3136**), send that `join …` message
   on WhatsApp to the sandbox number. You'll get a confirmation reply.
4. 🔴 Run the backend with your Twilio creds (leave `TWILIO_TEMPLATE_SID` unset):
   ```bash
   cd backend
   TWILIO_ACCOUNT_SID=ACxxxx \
   TWILIO_AUTH_TOKEN=xxxx \
   npx tsx scripts/dev-pglite.ts
   ```
   (Or set them in `backend/.env`.) With creds present, the app uses the real
   Twilio sender instead of the console logger.
5. 🔴 On the device: `adb reverse tcp:8080 tcp:8080`, open the app, enter the
   number, tap "Recibir código por WhatsApp" → **the code arrives on WhatsApp.**

Sandbox limits: only opted-in numbers receive messages, and the opt-in lapses
after 24h of inactivity (just re-send `join …`). Not for real users — that's the
production path below.

---

## Production path: real delivery to any driver

### 1. 🔴 Twilio WhatsApp sender approval — START FIRST (has lead time)
- In Twilio Console: **Messaging → Senders → WhatsApp senders → Create**.
- This connects a phone number to a **Meta WhatsApp Business Account (WABA)** and
  requires **Meta Business verification**. Approval can take hours to days —
  begin this before everything else.
- Once approved, note the sender, e.g. `whatsapp:+52…` → this is `TWILIO_WHATSAPP_FROM`.

### 2. 🔴 Create + submit the OTP template
- **Content Template Builder** → new template, category **Authentication**.
- Body (es_MX), one variable for the code:
  > `Tu código de Kompara es {{1}}. Caduca en 10 minutos. Si no lo solicitaste, ignóralo.`
- Submit for Meta approval. When approved, copy its **Content SID** (`HX…`) →
  this is `TWILIO_TEMPLATE_SID`. (Authentication templates approve fast and can
  include a copy-code button; the `{{1}}` mapping in the code matches this.)

### 3. 🔴 Deploy the backend
- Easiest: apply the repo blueprint — **New → Blueprint** in Render, which reads
  [`render.yaml`](../render.yaml) (now includes the `kompara-backend` service +
  `kompara-backend-db`). Or create the service manually per
  [launch-runbook.md](launch-runbook.md) §3 (root `backend/`, start `pnpm start`).
- Set the secrets (Render → service → Environment):
  `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_WHATSAPP_FROM` (approved
  sender), `TWILIO_TEMPLATE_SID` (approved template), plus `ANTHROPIC_API_KEY`,
  `R2_*`, `ADMIN_TOKEN`, `SPEC_SIGNING_PRIVATE_KEY` (replace the committed dev
  key — TD-013).
- `DATABASE_URL` is injected from `kompara-backend-db`. Migrations run in the
  build (`pnpm db:migrate`); seed once via Render Shell:
  ```bash
  pnpm db:seed && pnpm db:seed:specs && pnpm db:seed:fiscal && pnpm db:seed:app
  ```

### 4. 🔴 Point the app at the backend
- The Android **release** build already targets `https://api.kompara.mx`
  (`android/sync/build.gradle.kts`). Point that DNS at the Render service (or
  change the release `API_BASE_URL` to the `…onrender.com` URL and rebuild).
- Build a release APK/AAB, install, sign up with a real number → code arrives.

---

## Verify it works
```bash
# Against the deployed backend (no code is ever returned in the response):
curl -s -X POST https://api.kompara.mx/v1/auth/otp/request \
  -H 'Content-Type: application/json' -d '{"phone":"+523310103136"}'   # → {"ok":true}
# The code should arrive on WhatsApp; then verify in-app or via /v1/auth/otp/verify.
```

## Troubleshooting
- **63016 / "use a Message Template"** → `TWILIO_TEMPLATE_SID` is unset or the
  template isn't approved. Set the approved template SID.
- **63007 / "channel not found" or 21910 / "From and To… same channel"** →
  `TWILIO_WHATSAPP_FROM` isn't a valid `whatsapp:` sender, or doesn't match an
  approved WhatsApp sender.
- **Nothing arrives, no error** → in the sandbox, the number isn't opted in
  (re-send `join …`); in production, the sender or template isn't approved yet.
- **App shows "No pudimos enviar el código"** → the request didn't reach the
  backend (DNS / service down), not a delivery problem.

Sources: [Twilio error 63016](https://www.twilio.com/docs/api/errors/63016).
