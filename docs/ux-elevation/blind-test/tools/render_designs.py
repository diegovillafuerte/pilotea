"""Render every design's four states on the identical host. Usage: render_designs.py <design-id> [...] (default: all in gen/)."""
import os, sys, subprocess
T=os.path.dirname(os.path.abspath(__file__))
CH="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
tpl=open(f'{T}/host-clean.html').read()
ids=sys.argv[1:] or sorted(d for d in os.listdir(f'{T}/gen') if os.path.isdir(f'{T}/gen/{d}'))
FARE={'green':('$148.00','6 min (2.0 km)','18 min (10.0 km)'),'yellow':('$120.00','5 min (2.0 km)','22 min (10.0 km)'),'red':('$62.00','12 min (5.2 km)','18 min (7.1 km)'),'nodata':('— —','4 min (1.5 km)','15 min (6.0 km)')}
for d in ids:
    os.makedirs(f'{T}/render/{d}',exist_ok=True)
    for st in ('green','yellow','red','nodata'):
        snip=open(f'{T}/gen/{d}/{st}.html').read()
        page=tpl.replace('<!--CHIP-->',snip)
        f,a,b=FARE[st]
        page=page.replace('>$148.00<',f'>{f}<',1).replace('6 min (2.0 km)',a,1).replace('18 min (10.0 km)',b,1)
        p=f'{T}/render/{d}/{st}.html'; open(p,'w').write(page)
        subprocess.run([CH,'--headless=new','--hide-scrollbars','--force-device-scale-factor=2','--window-size=393,852',f'--screenshot={T}/render/{d}/{st}.png','--virtual-time-budget=4000','--disable-gpu','--no-first-run','file://'+p],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=90)
    print(d, all(os.path.exists(f'{T}/render/{d}/{s}.png') for s in ('green','yellow','red','nodata')))
