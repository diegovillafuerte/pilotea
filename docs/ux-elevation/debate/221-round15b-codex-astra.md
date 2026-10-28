**Round 15b — Codex Astra: NOT_YET. Structural 71/80; craft 33/40.** The previous placement defects are fixed. C12 introduces a craft hard fail.

Measurements use the supplied 2× renders; boundaries exclude shadows.

| My Round 15 item | Status | Render evidence |
|---|---|---|
| C1/C2/C10 — competing financial heroes | **Done; preserved** | Numeral ink heights: approximately **52px chip / 65px host** in C1/C2; **67px / 86px** in C10. Host fare retains priority. |
| C10 — insufficient clearance | **Done** | Plate ends at **y906px**; host begins **y938px**: **32px = 16dp**, correcting the previous 12dp. |
| C8 — pickup-stroke clearance | **Done** | Plate occupies **x24–504, y295–519px**. Pickup’s outer stroke begins at **y551px**: **16dp** clear. |
| A1 — demo hides pickup | **Done** | Pickup is visible at **(436,570px)**. Its outer stroke ends near **y587px**; plate starts at **y674px**: **43.5dp** clear. Legend now includes the chip’s shape cues. |

C10’s large-text footprint remains **624/786px = 79.4%** of screen width—an unchanged limitation, already accepted last round.

**Blocking regression — C12 loses the signature.** Its **240 × 56dp** compact overlay is a rounded green rectangle containing ▲, `$9.49`, and a verdict phrase. Both named limits and their relational marker disappear. Those were precisely the elements that justified Signature = 8 in my previous report. This matches the [craft rubric’s explicit hard fail](/Users/panama/pilotea/docs/ux-elevation/debate/03-rubric-r11-craft.md).

Its geometry succeeds: plate **y540–652px**, card starts **684px**, giving **16dp** clearance; puck ring and halo clear it by **43dp** and approximately **19dp**. This is a signature failure, not a placement failure.

Other checked changes hold: C1/C2/C7 have **16dp** card clearance and **16.5dp** pickup clearance; C9 clears the destination by **16dp** and next manoeuvre by approximately **127dp**. C11 correctly suppresses within its **80dp** available band. No further rendered regression established in D1/D3b/F1/F4/G2/G4/H3 or the night keylines.

One contract inconsistency remains: [spec §12](/Users/panama/pilotea/docs/ux-elevation-spec.md:778) still states suppression below **144dp**, while §6 specifies the compact threshold of **88dp**.

| # | Criterion | Score |
|---|---|---:|
| 1 | One job per screen | 9 |
| 2 | One primary action | 9 |
| 3 | Navigation | 9 |
| 4 | Numbers discipline | 9 |
| 5 | Copy | 9 |
| 6 | States | 9 |
| 7 | Time-to-value | 8 |
| 8 | Consistency & feel | 9 |
| | **Structural** | **71/80** |
| 9 | Signature idea | **7** |
| 10 | Glance physics | 8 |
| 11 | Craft | 9 |
| 12 | Radical simplicity | 9 |
| | **Craft** | **33/40** |

**Hard-fail check:** C12 triggers the rounded-colour-rectangle + number + glyph prohibition; no other hard fail established; mounted one-second recognition remains unverified.

`python3 check-fixture.py` passes: **152 figures, 16 chips, 27 offer rows**, plus the remaining guards. It does not validate rendered geometry.

**Shortest path:** give the compact form a perceptible relation to both personal limits while preserving its clearance; verify green/yellow/red/grey, large text and night on C12’s host geometry; synchronize the suppression contract. Changing the corner radius or deleting the triangle alone would not restore the missing idea.