# Kompara v3 mockups

Static HTML mockups for the UX elevation spec (`docs/ux-elevation-spec.md`), one `<div class="frame">` per screen, captions name the screen id (A1 … H6 incl. C8–C11, N1 … N10; 80 frames). Stylesheet: `base-v3.css` (the v3 "paper, ink and the raya" system); the frozen Round 10 kit is `../android-app-v2-r10/`.

- `index.html` is assembled: `cat _head.html part-a.html part-b.html part-c.html part-d.html part-e.html part-f.html part-g.html part-h.html part-n.html > index.html`. Edit the parts, never `index.html`.
- `base-v3.css` carries the tokens (from `design-system/tokens`) and the sizing contract (≥48dp targets, chip footprint, verdict colours).
- `fixture.json` is the golden fixture (raw inputs only). `check-fixture.py` derives every displayed figure from it and asserts the strings in `index.html` and the spec, and fails on known stale strings. Run it after any edit:

```bash
python3 design-system/ui_kits/android-app-v2/check-fixture.py
```

- Render every frame to PNG, then build the per-section contact sheets (`sheets.py` reads the frames from `$KOMPARA_OUT/frames`):

```bash
cd design-system/ui_kits/android-app-v2
python3 render-frames.py <out-dir>
mkdir -p <sheets-dir> && ln -sfn <out-dir> <sheets-dir>/frames
KOMPARA_OUT=<sheets-dir> python3 sheets.py
```

- Serve locally with `python3 -m http.server 8765` from the repo root (`.claude/launch.json` → `design-static`); `file://` rendering is not reliable.
- Review history: `docs/ux-elevation/debate/`.
