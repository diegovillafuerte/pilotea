"""Final confirmation boards (Round 14). Run after the kit is propagated and rendered to $S/final/kit/.
board4: Hoy — kit B1 (final) vs Hoy v5 prototype, v3, four generic screens; shuffled letters; key outside the judge folder.
board5: whole product — six kit screens next to three Uber Driver references (not blind vs generics; standalone Uber-class read)."""
import json, os, random, shutil, sys
from PIL import Image
S = sys.argv[1]; B3 = f"{S}/blind/hoy/board3"; K = f"{S}/final/kit"
key3 = json.load(open(f"{S}/blind-key-hoy3.json")); inv = {v: k for k, v in key3.items()}
src = {"kit-B1": f"{K}/B1.png", "hoy-v5": f"{B3}/hoy-{inv['hoy-v5']}.png", "v3": f"{B3}/hoy-{inv['v3']}.png"}
for g in ("gen-sonnet", "gen-haiku", "gen-codex", "gen-opus"): src[g] = f"{B3}/hoy-{inv[g]}.png"
names = list(src); random.Random(1427).shuffle(names); letters = "ABCDEFG"
D4 = f"{S}/final/board4"; shutil.rmtree(D4, ignore_errors=True); os.makedirs(D4)
key = {}
for L, n in zip(letters, names):
    im = Image.open(src[n]).convert("RGB"); c = Image.new("RGB", (786, 1764), "white"); c.paste(im, (0, 0)); c.save(f"{D4}/hoy-{L}.png"); key[L] = n
w = 400; strip = Image.new("RGB", (w * 7 + 8 * 6, 900), "#222")
for i, L in enumerate(letters):
    im = Image.open(f"{D4}/hoy-{L}.png"); im = im.resize((w, int(im.height * w / im.width))); strip.paste(im, (i * (w + 8), 0))
strip.save(f"{D4}/hoy-all.png")
for r in ("uber-2", "uber-3", "uber-5"): shutil.copy(f"{S}/blind/bench-hi/{r}.png", f"{D4}/ref-{r}.png")
shutil.copy(f"{B3}/JUDGE.md", f"{D4}/JUDGE.md")
json.dump(key, open(f"{S}/blind-key-hoy4.json", "w"), indent=1)
D5 = f"{S}/final/board5"; shutil.rmtree(D5, ignore_errors=True); os.makedirs(D5)
for i, f in enumerate(["B1", "C1", "C3", "D1", "E1", "F1", "G2", "A1"]):
    shutil.copy(f"{K}/{f}.png", f"{D5}/screen-{i+1}.png")
for r in ("uber-1", "uber-2", "uber-3", "uber-5", "uber-6"): shutil.copy(f"{S}/blind/bench-hi/{r}.png", f"{D5}/ref-{r}.png")
print("board4", key); print("board5 ok")
