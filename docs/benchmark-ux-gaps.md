# Benchmark & UX Gap Analysis — Kompara vs StopClub/GigU, Ruta Rentable, Gridwise

> **Date:** 2026-06-12
> **Inputs:** `docs/competitive-analysis.md` (updated same day), fresh web research on Gridwise, GigU (StopClub rebrand), Ruta Rentable Play data, DecideRider.
> **Purpose:** Rank the concrete product/UX moves that close the gap to "top of the class driver ecosystem app in Mexico." Strategy lives in the competitive analysis; this doc is the to-do list derived from it.

## 1. Scorecard (2026-06-12)

| Capability | GigU (BR/US) | Ruta Rentable | Gridwise | **Kompara today** |
|---|---|---|---|---|
| Traffic-light overlay verdict | ✅ green/yellow/red | ✅ 3 named levels | — | ✅ Conviene/Regular/No conviene |
| Overlay shows $/km **and** $/hr | ✅ | ✅ | — | ✅ *(shipped 2026-06-12: $/hr under the $/km hero)* |
| **Net** (cost-aware) math in overlay | ✅ | ❌ gross vs goals | — | ✅ net of fuel+maintenance |
| Two-tier thresholds (yellow band) | ~ (single floor + warn) | ~ | — | ✅ *(shipped 2026-06-12: green floor + red floor)* |
| Threshold edit without leaving host app | ❌ | ❌ | — | ✅ long-press quick sheet |
| Required account w/ UUID | ✅ phone+WhatsApp code | ❌ none | ✅ email/phone | ✅ *(shipped 2026-06-12: WhatsApp OTP signup in onboarding)* |
| Benchmarks vs other drivers | ❌ | ❌ | ✅ regional | ✅ city percentiles (synthetic seed) |
| Cross-platform compare | ❌ | ~ expense log | ✅ | ✅ Uber vs DiDi |
| Fiscal / social-security tooling | ❌ | ❌ | ✅ US tax exports | ✅ IMSS + ISR/IVA + PDF (unique in MX) |
| Safety suite (camera, radar) | ✅ | ✅ secret camera | ❌ | ❌ deliberate skip for v1 |
| Help center / config tutorials | ✅ | ~ | ✅ | ❌ **gap** |
| Creator/affiliate program | ✅ | ✅ Partners | ~ | ~ referral codes built, no creator program |
| Play Store presence | ✅ 4.2★/21k | ✅ 2.4★/27 votes | ✅ 4.9★/28k (iOS) | not yet submitted |

Key external facts to keep in view:
- **StopClub = GigU** (rebranded 2024; 250k+ users, breakeven, R$12.90/mo BR / $6.95/mo US after 30-day no-card trial).
- **Ruta Rentable is weak**: 2.4★ from only 27 ratings despite ~100k installs — heavy paid acquisition, poor retention. The MX incumbent is beatable on quality.
- **DecideRider (Chile) lists Mexico as "próximamente"** and already shows commission-deducted net + pickup distance on its overlay at ~US$3.70/mo. The MX window is real but closing.
- Every competitor markets a "~30% more per shift" claim and a no-card trial.

## 2. What shipped today (2026-06-12)

1. **$/hr on the chip** — net $/hr directly under the net $/km hero; "ganancia neta" demoted to expanded detail (it duplicated the fare the host app already shows).
2. **Two-tier thresholds** — green floor + red floor per metric (between = yellow band); long-press sheet now has both $/km sliders; persisted per platform with migration from single-floor installs.
3. **Required signup** — phone → WhatsApp OTP → profile (name + city) inside onboarding (after the pitch, before the disclosure), plus a standalone gate for installs that pre-date accounts. Driver UUID + device merge already existed server-side (B-042); the app now actually uses it.
4. **Copy polish** — fixed the awkward onboarding lines ("Sabe si conviene…" → "Entérate si conviene…", "Hasta un +30% mejor" → "Gana hasta 30% más", "Kompara es limitada" → "queda limitada") and rewrote the "funciona sin cuenta" claims the new account requirement invalidated.

## 3. Prioritized gaps (next)

**P0 — credibility at first contact (pre-Play-submission)** — *all three shipped 2026-06-12 (B-070, B-071)*
1. ~~**In-app verdict explainer ("¿por qué?")**~~ ✅ — the expanded chip opens with one line naming which floor passed/failed, driven by per-metric levels the engine now exposes on `Verdict`.
2. ~~**Per-hour floors in Ajustes**~~ ✅ — Ajustes → "Tu semáforo": all four floors (verde/rojo × $/km/$/hr) per platform, immediate persistence shared with the quick sheet, plus "Volver a la mediana de <ciudad>" (first consumer of the city-seeded defaults table).
3. ~~**Help center seed**~~ ✅ — Ajustes → "Ayuda": seven offline es-MX FAQ entries (reader activation incl. DiDi screen capture, semáforo semantics, OEM battery, privacy, account, price, troubleshooting). Public web version deferred until the marketing site exists.

**P1 — retention & trust**
4. **Logout + account management in Ajustes** — signup exists now; the mirror surface (see phone, edit name/city, cerrar sesión, borrar cuenta for Play data-safety) does not. Play's data-deletion policy will require it at submission.
5. **Session-expiry handling** — backend sessions last 30 days; the app has no 401-driven re-auth path. Add an interceptor that flips to AUTH on expired sessions.
6. **Onboarding "value math" moment** — after the simulator, show "con tus costos, un turno típico deja $X — Kompara te ayuda a subirlo" (every rival anchors the +30% claim; we have the engine to make it honest).

**P2 — growth**
7. **Creator/affiliate program** (Ruta Rentable Partners / GigU model) on top of the existing referral codes.
8. **Marketing numbers discipline** — instrument capture-success and verdict-shown counts now so "X ofertas analizadas" claims are real when marketing needs them (Gridwise's "$11B tracked" playbook).
9. **Safety camera** — all LATAM rivals have it; cheap retention surface, but only after the reader is rock-solid.

## 4. Risks tied to this benchmark

- **Required signup adds funnel friction** the competitors' research says to minimize: GigU's praised flow is phone+code with **no card**; ours matches (WhatsApp OTP, no card) but we must watch the SIGNUP → SIGNUP_DONE funnel counter for drop-off once beta starts (B-054).
- **Debug builds point at `10.0.2.2:8080`** — signup on a physical test device needs `adb reverse tcp:8080 tcp:8080` with the backend running locally, or a deployed `api.kompara.mx`. The backend deploy is now on the launch critical path (it wasn't while the app was anonymous-first).
- **Twilio WhatsApp sender** must be production-approved before beta; OTP is now a hard gate, not an optional feature.
