#!/usr/bin/env python3
"""Assemble index.html from the parts. With --artifact OUT, also write a self-contained page
(kit.css inlined, no document wrapper) for publishing as an Artifact."""
import pathlib, re, sys

here = pathlib.Path(__file__).parent
PARTS = ["_head.html", "part-s.html", "part-p.html", "part-l.html", "part-k.html", "part-m.html", "part-j.html", "_foot.html"]
html = "".join((here / p).read_text(encoding="utf-8") for p in PARTS)
(here / "index.html").write_text(html, encoding="utf-8")
print("index.html:", len(re.findall(r'<div class="frame" id="', html)), "frames")

if "--artifact" in sys.argv:
    out = pathlib.Path(sys.argv[sys.argv.index("--artifact") + 1])
    head = html[: html.index("<body>")]
    body = html[html.index("<body>") + len("<body>") : html.rindex("</body>")]
    title = re.search(r"<title>.*?</title>", head, re.S).group(0)
    font = re.search(r'<link rel="stylesheet" href="https://fonts[^>]+>', head).group(0)
    css = (here / "kit.css").read_text(encoding="utf-8")
    out.write_text(f'{title}\n<meta name="claude:viewport-fit-sides" content="cover">\n{font}\n<style>\n{css}</style>\n{body}', encoding="utf-8")
    print("artifact:", out)
