# Account, verification & onboarding design

> **Date:** 2026-06-18 · **Status:** Decisions locked by founder; ready to implement (legal copy via [B-038](../pming/tasks/pending/B-038.md))
> **Companion to:** [earnings-import-strategy.md](earnings-import-strategy.md) — the import flow is also the verification mechanism, so the two are designed together.
> **Founder decisions (2026-06-18):** signup = **phone + WhatsApp OTP**; verification = **hard gate before paid tiers**; Android merges on green tests + codex/Gemini review with on-device verification (real S25 connected).

---

## 0. TL;DR

- **Signup is already built and matches the decision** — phone → WhatsApp OTP → perfil, gating the app shell (B-067, B-069). **TD-008 and TD-017 are stale** (they say auth UI is deferred; it shipped). Action: mark them resolved. No new signup work — only polish + the additions below.
- **Identity = a stable internal `driver.id` (UUID).** Phone is a *re-verifiable contact attribute*, not the identity. A phone change is an OTP re-verify that keeps the same account and all data. The `anonymousDeviceId` binds the install → account at first verify (so pre-signup reader data merges in).
- **Verification = "import-as-proof," enforced as a hard gate.** A driver is *verified* once they successfully import one real Uber/DiDi statement. Paid surfaces (benchmarks, Comparar table, fiscal) require **verified AND entitled**. This is server-authoritative (`drivers.verified_at`), survives reinstall/device-change, and is self-reinforcing: Comparar needs imported data anyway, so the act that gives you something to compare is the act that verifies you.
- **The reader and the free Comparar hero stay open** (no verification, no account needed for the reader) — the hook must never be gated.
- **Onboarding is unchanged in shape; verification is NOT forced into it.** Forcing an import during onboarding would block the free reader (most new drivers won't have a PDF handy). Verification is prompted at the paid gate and inside the import flow.
- **Anti-resale posture:** server-authoritative verification + `anonymousDeviceId` binding + the existing one-redemption-per-device fraud heuristic + Play subscription tied to the Google account. Documented, not over-built.

---

## 0.5 Review revisions (codex gpt-5.5 + independent review — 2026-06-18)

Both reviewers cross-checked against the code. **Where this conflicts with text below, this governs.**

- **Rename "driver verification" → "import/data verification."** A parseable upload proves a real-looking statement was supplied, **not** identity / current-driver-status / ownership (a borrowed or old PDF verifies). Don't claim "verified driver." Strengthen the bar: require minimum `data_completeness`, **nonzero trips & earnings**, a **recent week**; where the Uber PDF exposes name/phone/email, optionally compare to account signals.
- **Model verification as DERIVED, not a frozen `verified_at`.** `verified = EXISTS(successful, non-revoked import)` computed from the `imports` table (add a `revoked` status) → gives a revocation path if an import is later found fraudulent. Keep a materialized read for speed, but truth is derivable.
- **Gate ONLY population-dependent surfaces.** Apply `&& verified` to **BENCHMARKS and COMPARE** only. Do **NOT** gate HISTORY, FISCAL, or RECOMMENDATIONS — those are the driver's OWN data they paid for; blocking them is product-hostile and a refund/Play risk. (§3.2's "paid set" list is wrong; restrict it.)
- **Promo and debug BYPASS verification.** Under launch promo (`!paywallEnabled`) or debug override, everything unlocks including benchmark surfaces — verification constrains **only the premium-by-payment path**, not the promo/debug escape hatches. Don't write `(...) && verified` flatly (that breaks promo); gate as: `benchmarkUnlocked = !paywallEnabled || debugOverride || (premium && verified)`.
- **VerificationSource caching:** cache a positive result **sticky** in DataStore; fail closed only when there is **no** cached value. A transient `/v1/me` blip must never downgrade an already-unlocked verified user.
- **Phone-change rewrite (§4.1) — session-takeover hole.** A stolen bearer must not silently move the account. Required: a `pending_phone_changes` table bound to `driver_id`; OTP to the **current** phone (or recent re-auth) in **addition** to verifying the new phone; **notify both** old and new numbers; **revoke all other sessions** on success; rate-limit **per authenticated driver** (not just per target phone) + cap distinct target numbers/day; handle the unique-phone collision transactionally with a generic error; if the colliding account is **empty** (no imports/verified/subscription) allow reclaim, else a support path (no dead end); **reset verification** on phone change (re-prove on the new identity — closes a resale vector).
- **Anti-resale (§4.2) — downgrade the claims.** Server-stored premium/grants ride `driver.id`, so they DO transfer with a handed-over account; `anonymousDeviceId` is a soft signal, not a binding. The real lever is **server-side Play purchase-token verification** (already a launch blocker in techdebt) + a future device/session inventory and concurrent-paid-session cap. State this honestly.
- **Sequencing (replaces §8):** docs first (this revision) → privacy/dry-run/merge backend cleanup → verification (derived + scoped) → Android gate shipped **inert** behind the kill-switch → wizard/share-target/Comparar entry → **then** flip the gate on. The gate must not reach production-enabled before the wizard is reachable (chicken-and-egg).

---

## 1. Why phone + WhatsApp OTP (confirmed)

| Option | Verdict |
|---|---|
| **Phone + WhatsApp OTP** | **Chosen.** Already built end-to-end; matches StopClub/GigU (the proven flow for this exact demographic); WhatsApp is universal among MX drivers; anonymous-first (reader works with no account); one clean identity for phone-change/resale rules. |
| + Google sign-in | Rejected for v1. A second identity to reconcile, a "Sign in with Google" Play surface, and no real adoption gain — WhatsApp OTP already clears the bar. Revisit only if OTP delivery cost/deliverability becomes a problem. |
| Google-only | Rejected. Excludes drivers without a reliable Google login and discards the built OTP flow. |

**Current flow (keep):** `OnboardingNavGraph` PITCH → **SIGNUP** (phone → OTP → perfil: name + city) → DISCLOSURE → ACCESSIBILITY → OEM → DONE. Signup is required and gates the shell; the `anonymousDeviceId` is sent on `verifyOtp` so locally-captured reader data attaches to the new account. ([SignupViewModel.kt](../android/ui/src/main/java/mx/kompara/ui/auth/SignupViewModel.kt), [AuthRepository.kt](../android/sync/src/main/java/mx/kompara/sync/auth/AuthRepository.kt), backend [auth.ts](../backend/src/routes/auth.ts) + [me.ts](../backend/src/routes/me.ts).)

---

## 2. Account / identity model

**Identity is `drivers.id` (UUID), not the phone.** The phone is unique and `notNull` today, but treat it as a contact/login attribute that can change. Everything (sessions, weekly_aggregates, imports, subscriptions, premium_grants) already FKs to `drivers.id`, so identity is already correctly anchored — we just must never *re-key* on phone.

**Device binding.** `anonymousDeviceId` (generated first run, persisted forever, survives logout) is sent on `verifyOtp`. It:
- merges pre-signup reader data into the account,
- is the unit for the one-redemption-per-device referral fraud heuristic ([referral_redemptions](../backend/src/db/schema.ts)),
- gives us a continuity signal for anti-resale.

---

## 3. Verification — hard gate before paid tiers

### 3.1 Mechanism: import-as-proof

A driver becomes **verified** the first time `POST /v1/imports` (non-dry-run) parses successfully — i.e. they uploaded a genuine Uber PDF / DiDi screenshots that Claude Vision could read. This is the natural proof of being a real ride-hailing driver, and it doubles as the data seed Comparar needs.

- **Backend:** add `drivers.verified_at TIMESTAMP NULL`. Set it (if null) inside the imports success path ([imports.ts](../backend/src/routes/imports.ts) step 9). Expose `verified: boolean` + `verified_at` on `GET /v1/me`.
- **What counts (v1):** any successful non-dry-run parse, any supported platform. (Known limitation: a sufficiently real-looking fake image that parses would verify — acceptable bar for v1; tighten later with a min `data_completeness` or cross-checks. Log as tech debt.)
- **Persistence:** server-authoritative → survives reinstall and device change; cannot be spoofed client-side.

### 3.2 The gate

Today: `unlockedAll = !paywallEnabled || premium || debugOverride` ([TierGate.kt](../android/billing/src/main/java/mx/kompara/billing/TierGate.kt)). Add **verification as a required dimension for paid capabilities**:

```
paidUnlocked = (!paywallEnabled || premium || debugOverride) && driverVerified
```

- Applies to `BENCHMARKS`, `COMPARE`, `FISCAL`, `RECOMMENDATIONS`, `HISTORY` (the paid set).
- **Does NOT apply to:** the reader/today-stats (never a capability), and the **free Comparar hero** (the percentile standing sentence) — those stay open as the hook.
- A premium-but-unverified driver sees a distinct gate state: not "paga", but **"verifica tu cuenta — importa una semana real de Uber o DiDi"** routing to the import flow. After a successful import, the same surface unlocks with no extra payment.

**Thread it through:** `TierGatekeeper` gains a `VerificationSource` (a `Flow<Boolean>` fed from `GET /v1/me`'s `verified`, cached in DataStore, failing **closed** = unverified). `GateStates.derive(...)` takes `driverVerified` and applies the formula above. The `PaywallGate`/gate-state needs a third visual state (`NEEDS_VERIFICATION`) distinct from `LOCKED`, with verify-CTA copy.

### 3.3 Why hard, not soft

The founder chose the hard gate: benchmark integrity (percentiles are only meaningful against *real* drivers) and anti-resale both want a real-driver proof before the paid value is handed over. The friction lands exactly where it's justified (at the paywall, not at the reader), and the required action (import) is one the driver must do anyway to have anything to compare.

---

## 4. Edge cases the founder called out

### 4.1 Driver changes phone number

Today `PATCH /v1/me` updates name/city/platforms but **not phone**. Add a dedicated, OTP-protected phone-change so the account and all data survive:

- **New endpoint:** `POST /v1/auth/phone/change` (bearer-authed) → sends OTP to the **new** phone → `POST /v1/auth/phone/verify` with the code → updates `drivers.phone` (handling the unique constraint: reject if the new phone already belongs to another account).
- **Android:** an "Cambiar número" row in [AccountScreen.kt](../android/ui/src/main/java/mx/kompara/ui/screens/AccountScreen.kt) → phone entry → OTP (reuse `MxPhone`/`SignupScreens` components) → success. The `driver.id`, verification, history, and subscription are untouched.
- **Collision rule:** if the new phone is already an account, do **not** silently merge (data-loss risk) — surface "Ese número ya tiene una cuenta Kompara"; account-merge is a separate, later feature.

### 4.2 Driver sells/shares their *Kompara* account

Levers (mostly already present; document the posture, build only what's cheap):
- **Server-authoritative verification** — a buyer can't fake verified status client-side; if they never import, paid surfaces stay gated.
- **Play subscription is tied to the buyer's Google account**, not Kompara's — premium doesn't transfer with a handed-over phone/login.
- **`anonymousDeviceId` continuity** + the **one-redemption-per-device** heuristic limit referral/grant farming across throwaway accounts.
- **Phone-change requires OTP** to the new number, so the seller can't quietly retain control.
- Not built for v1: hard device-count limits or biometric binding (over-engineering; revisit if resale abuse shows up in telemetry).

### 4.3 The underlying *Uber/DiDi* account changes hands

MX account-renting is real, but it's mostly a **data-integrity** matter, not a Kompara-identity one. Mitigation: import-as-proof re-confirms a real statement on each import; a future check can flag when an imported statement's identity (the PDF carries name/phone/email — see earnings-import §2.3) diverges from the account. v1: document; don't block.

---

## 5. Onboarding adaptation

**Shape unchanged.** PITCH → SIGNUP → DISCLOSURE → ACCESSIBILITY → OEM → DONE stays. Two additions:

1. **Do not force verification in onboarding.** A new driver rarely has a weekly PDF mid-onboarding; forcing it would block the free reader (the hook). Verification is prompted later — at the paid gate and in Comparar.
2. **Post-onboarding nudge (soft):** the DONE screen and the Comparar empty state both offer **"Traer mis ganancias"** → the import flow (which verifies + seeds Comparar). This is the bridge from "account created" to "verified + has data to compare," and the literal satisfaction of the earnings-import "reachable from Comparar" requirement.

So the driver journey is: **install → reader works (anonymous)** → **onboarding signup (account)** → **reader + free hero** → **import one week (verifies + seeds)** → **paid surfaces unlock**.

---

## 6. Backend changes (summary)

1. `drivers.verified_at TIMESTAMP NULL` (migration) + set on first successful import ([imports.ts](../backend/src/routes/imports.ts)).
2. `GET /v1/me` returns `verified` + `verified_at`.
3. `POST /v1/auth/phone/change` + `/verify` (OTP-protected phone change, unique-collision handled).
4. (from earnings-import §6) reconciliation **field-level coalesce** + **null-clobber fix** in the imports upsert.
5. Tests: pglite coverage for verified-on-first-import, phone-change happy/collision, coalesce/clobber.

## 7. Android changes (summary)

1. `VerificationSource` (`Flow<Boolean>` from `/v1/me`, cached, fails closed) → into `TierGatekeeper`; `GateStates.derive` applies `&& driverVerified` to the paid set; add a `NEEDS_VERIFICATION` gate visual + verify-CTA copy routing to import.
2. Import wizard + share-target + Comparar EmptyState CTA (earnings-import §4) — these also drive verification.
3. "Cambiar número" in `AccountScreen` (OTP re-verify).
4. Mark TD-008/TD-017 resolved in `techdebt.md`.
5. Tests: gate derivation (verified × premium × promo matrix), phone-change VM, wizard validate-before-accept; on-device smoke on the S25.

## 8. Sequencing

1. **Backend PR** — verified_at + /v1/me field + reconciliation coalesce + null-clobber + phone-change endpoint (+ tests). Most foundational, fully verifiable.
2. **Android gate PR** — VerificationSource + derive + NEEDS_VERIFICATION visual.
3. **Android import PR(s)** — wizard + share-target + Comparar entry (earnings-import §4.4 build list).
4. **Android account PR** — phone-change UI.
5. **Docs/PM** — mark TD-008/TD-017 resolved; legal copy under B-038.

Each PR: tests + lint green, **codex + Gemini review**, on-device smoke where UI-visible, then merge.

> **Open follow-ups (non-blocking):** tighten what counts as a verifying import (min completeness / anti-fake); decide whether the free Comparar hero needs *any* floor of real data; account-merge feature for the phone-collision case.
