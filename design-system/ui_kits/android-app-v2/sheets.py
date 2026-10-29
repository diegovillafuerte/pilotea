import os, subprocess, sys
import os
S=os.environ.get("KOMPARA_OUT") or "/private/tmp/claude-501/-Users-panama-pilotea/89e8962a-cc98-46cd-a467-dfdcdb459052/scratchpad/r11"
order=['A1','A2','A3','A3s','A3n','A4','A4b','A4r','A5','A6','A7','B1','B2','B2r','B3','B3n','B4','B5','B6','B7','B8','B9','C1','C2','C3','C7','C4','C5','C6','C8','C9','C10','C11','C12','C13','D1a','D1b','D1','D2','D2b','D2c','D2d','D2e','D3','D3a','D3b','E1','E1b','E1c','E1d','E1e','E1f','E2','E3','E3d','E3b','E3c','E4','E5','E6','E7','E7b','E8','F1','F2','F3','F4','F5','G1','G1b','G1e','G1n','G2b','G2','G2c','G2d','G2e','G3','G4','G4b','G4c','G4d','G4e','G4f','G4g','G4h','G4i','G5','G6','G6b','G7','G8','G9','H1','H2','H2b','H2c','H2d','H1b','H3','H4','H5','H5b','H5c','H5d','H5e','H6','H6b','N1','N2','N3','N4','N5','N6','N7','N8','N9','N10']
groups={}
for f in order: groups.setdefault(f[0],[]).append(f)
only=sys.argv[1:]
if only: groups={k:v for k,v in groups.items() if k in only}
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
os.makedirs(f"{S}/sheets",exist_ok=True)
for g,fs in groups.items():
    cols=min(7,len(fs)); w=260; rows=(len(fs)+cols-1)//cols
    html='<html><body style="margin:0;background:#111;display:grid;grid-template-columns:repeat(%d,%dpx);gap:8px;padding:8px">'%(cols,w)
    for f in fs: html+=f'<div><div style="color:#ddd;font:600 14px Inter,sans-serif;margin:0 0 4px">{f}</div><img src="file://{S}/frames/{f}.png" style="width:{w}px;display:block"></div>'
    html+='</body></html>'
    p=f"{S}/sheets/{g}.html"; open(p,'w').write(html)
    W=cols*(w+8)+8; H=rows*(int(w*852/393)+30)+16
    subprocess.run([CH,"--headless=new","--hide-scrollbars","--window-size=%d,%d"%(W,H),f"--screenshot={S}/sheets/{g}.png","--virtual-time-budget=2000","--disable-gpu","--no-first-run","file://"+p],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=60)
    print(g,len(fs),W,H,os.path.exists(f"{S}/sheets/{g}.png"))
