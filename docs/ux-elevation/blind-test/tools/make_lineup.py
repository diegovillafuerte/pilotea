"""Build a blind lineup: make_lineup.py <outdir> <seed> <design> ... ; writes lineup-green.png, design-X.png and JUDGE.md; key saved next to the scratch folder."""
import json,sys,os,random
from PIL import Image, ImageDraw, ImageFont
T=os.path.dirname(os.path.abspath(__file__))
out,seed,designs=sys.argv[1],int(sys.argv[2]),sys.argv[3:]
os.makedirs(out,exist_ok=True)
rnd=random.Random(seed); order=designs[:]; rnd.shuffle(order)
letters=[chr(65+i) for i in range(len(order))]; key={letters[i]:order[i] for i in range(len(order))}
json.dump(key,open(os.path.join(os.path.dirname(T),f'blind-key-{os.path.basename(out)}.json'),'w'),indent=1)
font=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Bold.ttf',36); small=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',22)
def region(d,st):
    im=Image.open(f'{T}/render/{d}/{st}.png').convert('RGB'); w,h=im.size
    return im.crop((0,200,w,1096)).resize((393,448))
pad=14; cw,chh=393,448; cols=4; rows=(len(letters)+cols-1)//cols
sheet=Image.new('RGB',(cols*cw+(cols+1)*pad,rows*(chh+50)+pad),(24,24,24)); dr=ImageDraw.Draw(sheet)
for i,L in enumerate(letters):
    x=pad+(i%cols)*(cw+pad); y=pad+(i//cols)*(chh+50); dr.text((x,y),L,fill=(240,240,240),font=font); sheet.paste(region(key[L],'green'),(x,y+44))
sheet.save(f'{out}/lineup-green.png')
for L in letters:
    s=Image.new('RGB',(4*cw+5*pad,chh+60+pad),(24,24,24)); d=ImageDraw.Draw(s); d.text((pad,pad-4),f'Diseño {L}',fill=(240,240,240),font=font)
    for j,st in enumerate(('green','yellow','red','nodata')):
        x=pad+j*(cw+pad); d.text((x,pad+36),['verde $9.49/km','amarillo $7.16/km','rojo $2.20/km','sin datos'][j],fill=(180,180,180),font=small); s.paste(region(key[L],st),(x,pad+60))
    s.save(f'{out}/design-{L}.png')
n=len(letters); last=letters[-1]
j=open(f'{T}/lineup2/JUDGE.md').read().replace('Eleven overlay designs, labelled A–K',f'{n} overlay designs, labelled A–{last}').replace('all eleven designs',f'all {n} designs').replace('design-A.png … design-K.png',f'design-A.png … design-{last}.png').replace('Group the eleven designs',f'Group the {n} designs').replace('Rank all eleven',f'Rank all {n}')
open(f'{out}/JUDGE.md','w').write(j); print(n,'designs →',out)
