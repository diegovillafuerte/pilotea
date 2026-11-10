#!/usr/bin/env python3
"""Render each section of index.html to a PNG sheet at full size (1 dp = 1 px), four frames to a row.

  python3 render.py <out-dir> [section-letter ...]
"""
import math, pathlib, re, subprocess, sys

here = pathlib.Path(__file__).parent
out = pathlib.Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
only = set(sys.argv[2:])
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
s = (here / "index.html").read_text(encoding="utf-8")
head = s[: s.index("</head>")].replace('href="kit.css"', f'href="file://{here}/kit.css"')
sprite = s[s.index("<svg width=\"0\""): s.index("</svg>") + len("</svg>")]
for sec in re.findall(r"<section>.*?</section>", s, re.S):
    ids = re.findall(r'<div class="frame" id="([A-Z]\d+)">', sec)
    if not ids or (only and ids[0][0] not in only):
        continue
    rows = math.ceil(len(ids) / 4)
    page = (head + "<style>:root{--z:1}.wrap{max-width:none;padding-block:8px}.wrap h2{margin-top:8px}.grid{gap:36px 24px}</style></head><body>"
            + sprite + '<div class="wrap">' + sec + "</div></body></html>")
    tmp = out / f"_{ids[0][0]}.html"; tmp.write_text(page, encoding="utf-8")
    png = out / f"{ids[0][0]}.png"
    subprocess.run([CHROME, "--headless=new", "--hide-scrollbars", f"--window-size=1640,{rows * 960 + 230}", f"--screenshot={png}",
                    "--virtual-time-budget=5000", "--disable-gpu", "--no-first-run", f"file://{tmp}"],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=90)
    tmp.unlink()
    print(ids[0][0], len(ids), "frames ->", png.name)
