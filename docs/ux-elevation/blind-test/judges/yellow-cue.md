You are a principal product designer reviewing one detail of an overlay for ride-hailing drivers in Mexico. The overlay sits on the ride app while a trip offer is on screen and tells the driver, at a glance, how the offer compares with the driver's own two limits: "tu meta $8" (green from $8.00/km net) and "tu mínimo $6" (red below $6.00/km net); yellow is in between. The coloured band sits above, between or below the two labelled lines, and a small solid glyph sits left of the number.

Five candidate sets (P, Q, R, S, T) differ ONLY in the glyph of the middle (yellow, "in between") plate. In every set the left plate is green (▲) and the right plate is red with a genuinely NEGATIVE amount (−$1.05/km, the trip costs more than it pays).

Files (Read only these): all.png (all five sets at full size), all-grey-small.png (the same, greyscale and small — roughly what a driver with red-green colour blindness sees on a windshield-mounted phone), set-P.png … set-T.png.

For each set P–T answer:
(a) Read the yellow plate aloud as a driver would in one second. Could it be read as a NEGATIVE amount (e.g. "minus $7.16")? yes/no.
(b) In all-grey-small.png, can the yellow glyph be told apart from ▲ and ▼ by shape alone? 1–10.
(c) Does the glyph say "in between / within your limits" (1–10), and does it carry any wrong meaning (warning, stop, pause, equals, bullet point, button)?
(d) Craft fit with ▲ and ▼ as one family, 1–10.
Then rank P–T best to worst.

End with ONE fenced ```json block: {"sets":{"P":{"reads_negative":false,"shape_sep":8,"between":7,"wrong_meaning":"...","family":8},...},"rank":["P","Q","R","S","T"],"notes":"..."}
