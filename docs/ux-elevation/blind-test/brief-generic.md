# Design brief (self-contained — do not open any other file)

You are designing one UI element for an Android app used by ride-hailing drivers in Mexico City. While a trip offer from the ride app (Uber/DiDi) is on screen, the app draws a small **overlay** on top of the ride app that tells the driver, at a glance, whether the trip is worth it **after the driver's own costs** (fuel and wear), judged against the driver's own two limits: **green from $8.00/km net**, **red below $6.00/km net**, yellow in between. If the app could not read the offer, it shows a neutral "no data" state instead of a verdict. The overlay only informs; it never accepts or rejects. Copy is in Mexican Spanish.

Design the overlay in **four states**, using exactly these values:
1. green — $9.49 per km net · $285 per hour
2. yellow — $7.16 per km net · $191 per hour
3. red — $2.20 per km net · $54 per hour
4. no data — the fare could not be read

The host screen (the ride app) is fixed: a 393×852 phone with a status bar, a top control row (menu circle, "En línea" pill, search circle at y≈52–100), a map, and the offer card at the bottom (from y≈548). Your overlay must not cover the controls or the offer card.

Deliver exactly four HTML snippets, one per state, each a self-contained block: one `<style>` element plus your markup, with every class name prefixed `g-` and your root element `position:absolute` inside the phone (top/left/right in px). Use any fonts available from Google Fonts via `@import` inside your style if you want. No images, no external JS. Make it the best design you can.
