# Growth attribution — the shareable earnings card (B-055)

The "Tu Semana / Tu Mes" card is the top of Kompara's acquisition funnel: a driver shares a branded
PNG to a WhatsApp group, other drivers see it, some install the app. This note documents how we
attribute those installs and what is (and isn't) realistic.

## The hard constraint: a shared image is not a clickable link

The card is an **image** (`image/png`) shared via `ACTION_SEND`, with a short text caption
("…descárgala gratis · kompara.mx"). WhatsApp/Instagram/Telegram render the image inline; the text
URL is plain text the recipient must type or copy. **There is no clickable, attributable link baked
into the pixels** — a "UTM on the card" would be cosmetic only, because nothing carries the campaign
parameter from the viewer's eyeballs to the Play Store install.

So per-card, per-sharer attribution ("driver X's card drove install Y") is **not achievable** with the
image-only share. We design for realistic expectations below.

## What we actually do: Play Install Referrer (coarse, free, reliable)

The realistic, zero-backend attribution is the **Google Play Install Referrer API**
(`com.android.installreferrer`). When a user installs from a Play Store link that carries a
`referrer=` parameter, Play stores it and the freshly-installed app can read it once on first run.

The plan (a follow-up task — see techdebt TD entry):

1. Publish the download CTA as a Play Store link with a static UTM-style referrer, e.g.
   `https://play.google.com/store/apps/details?id=mx.kompara.app&referrer=utm_source%3Dshare_card%26utm_medium%3Dwhatsapp`.
   The card's caption points at a short `kompara.mx` redirect that 302s to this link.
2. On first launch, call `InstallReferrerClient`, read `installReferrer`, and bucket the install by
   `utm_source` (e.g. `share_card`). This tells us **how many installs came from the share-card
   channel overall** — a coarse but honest funnel signal.
3. Combine with the **local, anonymous share counter** already shipped in B-055
   (`Settings.shareCount`, incremented on each "Compartir" tap) to estimate a share→install ratio at
   the population level (shares are device-local and never uploaded by themselves; only aggregate
   counts would ever be derived, behind the existing consent flag).

What this gives us: **channel-level** attribution ("the share card drives N% of installs"), not
**individual** attribution. That is the correct expectation for an image share.

## Future option: dynamic links / deferred deep links

If we later want *per-sharer* attribution (e.g. to reward the driver whose card drove an install — the
referrals feature), we need a clickable link that survives the install:

- A **deferred deep link** service (Firebase Dynamic Links was the canonical one; it is sunsetting, so
  evaluate Branch, Adjust, AppsFlyer, or a self-hosted Play-referrer redirect) embeds a per-sharer
  token in the URL. The recipient taps the link → Play install → the app reads the token via Install
  Referrer on first run and credits the sharer.
- This requires the share to carry a **clickable link**, not just an image. Practically that means the
  card's caption text includes the dynamic link, or the share switches to a link-with-preview format
  where the platform allows it. WhatsApp will make a pasted URL tappable, so a per-sharer
  `kompara.mx/r/<token>` redirect in the caption is the realistic path — it just relies on the
  recipient tapping the text link, not the image.

## Summary

| Goal | Mechanism | Status |
|---|---|---|
| Channel-level install attribution | Play Install Referrer + static UTM on the CTA link | Documented; follow-up task to wire the referrer read on first run |
| Share volume signal | Local anonymous `Settings.shareCount` | Shipped in B-055 |
| Per-sharer attribution | Deferred deep link / dynamic link with a per-sharer token in the caption | Future option |
