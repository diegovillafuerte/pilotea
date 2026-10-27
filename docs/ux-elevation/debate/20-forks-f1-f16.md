
## Forks the synthesizer needs resolved explicitly (both of you must take a position on each, with reasons)

F1. **Third tab.** Codex: Hoy · Ganancias · Comparar with settings behind a gear. Opus: Hoy · Ganancias · Cuenta with "Tu lugar" one tap from the week. (Context: the founder earlier locked "Inicio = individual, Comparar = comparison" and treats Comparar as the paid revenue surface; Uber's own bar is Home · Earnings · Inbox · Account.)
F2. **Chip non-colour cue and vocabulary.** Codex: the word "Conviene / Regular / No conviene" + one rate + "Tras gasolina y desgaste". Opus: glyph ✓/⚠/✕/? + rate + "neto · second rate", and retire the Conviene set in favour of verde/amarillo/rojo everywhere. (Context: docs/design-principles.md records that the chip deliberately dropped the verdict word as redundant; the brand voice doc says "speak in net pesos".)
F3. **Chip tap.** Codex: tap opens a "por qué" panel. Opus: tap does nothing (tap-to-expand was parked, see docs/parked/verdict-chip-tap-to-expand.md); "why" = ▼ marker + after-the-fact detail in Tu día.
F4. **Missing-input behaviour.** Codex: never swap the deciding unit; neutral "Falta distancia / Sin cálculo". Opus: substitute the other metric, capped at amarillo, labelled "falta distancia".
F5. **Import.** Codex kills the import flow as a violation of "on-device". Opus keeps it (detect → parse once → review → save) with an honest "tu archivo se sube solo para leer tus números" line. (Clarification from the synthesizer: the on-device constraint is about SCREEN CONTENT captured by the reader; a driver choosing to upload their own weekly PDF is a different act — but the privacy copy must say so plainly.)
F6. **IMSS.** Codex removes coverage status, the month-end notification and tax estimates as unsupported assertions. Opus makes the IMSS tracker FREE with status words ("Ya te toca / En camino / Difícil") and keeps retenciones + PDF as Premium. (Context: IMSS tracking is the one uniquely-Mexican differentiator in docs/competitive-analysis.md §5.)
F7. **Signup placement and optionality.** Codex: account only when activating the trial/linking (fully optional). Opus: onboarding steps 5–6 after the reader is live, effectively required (escape only on network failure), city inferred from area code.
F8. **Hoy content.** Codex: status row + hero + one line + "Ver mi día" (nothing else). Opus: status pill + hero + ONE insight card + last 3 offers + week row.
F9. **Insights / consejos.** Codex kills the advice feed ("expired offers are not moral judgments"). Opus keeps one insight on Hoy, e.g. "Dejaste ir 2 viajes verdes".
F10. **Goal, streak, referral, share brag grid.** Codex kills all four. Opus keeps the weekly goal (with a setter; framed for renters), streak only on the share card, referral under Cuenta, share card with a 2×2 grid.
F11. **Comparison structure.** Codex: segments "Tus apps / Tu ciudad", ONE metric at a time, two aligned rows, a provenance sheet, strict like-for-like. Opus: "Tu lugar" = hero sentence + 20-person bar + "Tú vs promedio" for 5 metrics + "¿Qué app paga mejor?" + max 2 opportunity cards.
F12. **Trial start.** Opus: auto-start at first week close + manual from any lock card. Codex: only on explicit choice when there is something to show.
F13. **Free history window.** Opus: current + 3 previous weeks. Codex: current + previous calendar week.
F14. **Fixed costs (renta/seguro).** Codex keeps them with explicit per-day deduction rules. Opus removes the fields (nothing reads them today) and gives renters the weekly goal instead.
F15. **Hero labels.** Codex: "Te quedan aprox." / "Después de gasolina y desgaste" / "Ingresos registrados" (scope-explicit). Opus: "Ganancia neta" / "neto".
F16. **Before-shift greeting pill** ("✓ Kompara activo", 2 s, touch pass-through). Opus adds it; Codex says stay silent and never overlay unrelated screens.

For each fork: state your final position in one line, then the reason. Where you change your Round 1 position, say "changed".
