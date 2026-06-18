---
name: kompara-design
description: Use this skill to generate well-branded interfaces and assets for Kompara — earnings analytics and the real-time trip-offer "semáforo" verdict for ride-hailing drivers in Mexico — either for production or throwaway prototypes/mocks. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

Read the `README.md` file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.

## Quick orientation
- **Brand:** Kompara — Android-first, Spanish (es-MX, `tú`), dark-by-default, money-first. The hero is the floating verdict chip (verde/amarillo/rojo) over Uber/DiDi offers.
- **One stylesheet:** link `styles.css` — it pulls every token + the Inter webfont. Add `class="theme-dark"` for the app palette (light is default).
- **Components:** load `_ds_bundle.js`, then `const { Button, Card, MetricCard, VerdictChip, ... } = window.KomparaDesignSystem_722871`. Each component has a `.prompt.md` next to it with usage.
- **Foundations:** `tokens/` (colors, type, spacing, fonts) and `guidelines/` (specimen cards). `assets/` has the logos + `fonts/inter.ttf`.
- **References:** `ui_kits/android-app/` (canonical), `ui_kits/marketing-site/` (legacy web), `templates/app-screen/` (scaffold).

## Non-negotiables
- Plain es-MX, `tú`. Let colour + number talk; cut redundant words. Say "por kilómetro/por hora", never "IPK/IPH".
- Verde/amarillo/rojo are for verdicts ONLY — never decorative. Colour is never the only signal.
- Emerald `#059669` brand green; slate neutrals; Inter type; 16px spacing rhythm; tonal cards (no shadow) in-app.
- Icons: Material Symbols (Rounded). No platform logos. Emoji only 🔥 / 🔒 / 🎉.
