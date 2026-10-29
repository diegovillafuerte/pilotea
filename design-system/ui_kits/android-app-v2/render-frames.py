import re, sys, os, subprocess, html
import os
SRC = os.environ.get("KOMPARA_INDEX") or "/Users/panama/pilotea/design-system/ui_kits/android-app-v2/index.html"
OUT = sys.argv[1]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
s = open(SRC, encoding="utf-8").read()
head = s[: s.index("<body>") + len("<body>")]
svg_defs = s[s.index('<svg style="display:none"'): s.index("</svg>") + len("</svg>")]
# find each top-level .frame div by matching nested divs
frames = []
pos = 0
tok = re.compile(r"<div\b|</div>")
while True:
    i = s.find('<div class="frame">', pos)
    if i < 0: break
    depth = 0; j = i
    for m in tok.finditer(s, i):
        depth += 1 if m.group(0) == "<div" else -1
        if depth == 0:
            j = m.end(); break
    frag = s[i:j]
    cap = re.search(r'<div class="cap"><b>([A-Z]\d[a-z]?\d?)', frag)
    fid = cap.group(1) if cap else f"frame{len(frames)}"
    frames.append((fid, frag))
    pos = j
print("frames:", len(frames), [f[0] for f in frames])
only = set(sys.argv[2:])
os.makedirs(OUT, exist_ok=True)
for fid, frag in frames:
    if only and fid not in only: continue
    page = head.replace('href="base.css"', 'href="file:///Users/panama/pilotea/design-system/ui_kits/android-app-v2/base.css"').replace('href="base-v3.css"', 'href="file:///Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css"') + svg_defs + \
        '<style>body{padding:0;margin:0;background:#17181a;width:393px}.frame .cap{display:none}.phone{box-shadow:none;border-radius:0}.night-note{display:none}</style>' + \
        '<div class="grid" style="gap:0">' + frag + "</div></body></html>"
    p = os.path.join(OUT, f"{fid}.html")
    open(p, "w", encoding="utf-8").write(page)
    png = os.path.join(OUT, f"{fid}.png")
    subprocess.run([CHROME, "--headless=new", "--hide-scrollbars", "--force-device-scale-factor=2",
                    "--window-size=393,852", f"--screenshot={png}", "--virtual-time-budget=3000",
                    "--disable-gpu", "--no-first-run", "--no-default-browser-check", "file://" + p],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
    print(fid, os.path.exists(png))
