# Kompara v2 mockups

Static HTML mockups for the UX elevation spec (`docs/ux-elevation-spec.md`), one `<div class="frame">` per screen, captions name the screen id (A1 … H6; 60 frames).

- `index.html` is assembled: `cat _head-ab.html part-c.html part-d.html part-e.html part-f.html > index.html`. Edit the parts, never `index.html`.
- `base.css` carries the tokens (from `design-system/tokens`) and the sizing contract (≥48dp targets, chip footprint, verdict colours).
- `fixture.json` is the golden fixture (raw inputs only). `check-fixture.py` derives every displayed figure from it and asserts the strings in `index.html` and the spec, and fails on known stale strings. Run it after any edit:

```bash
python3 design-system/ui_kits/android-app-v2/check-fixture.py
```

- Serve locally with `python3 -m http.server 8765` from the repo root (`.claude/launch.json` → `design-static`); `file://` rendering is not reliable.
- Review history: `docs/ux-elevation/debate/`.
