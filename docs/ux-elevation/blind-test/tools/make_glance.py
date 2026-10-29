"""Glance proxy: degrade each state the way a windshield-mounted phone is seen in peripheral vision, then shuffle.
Degradations: small (phone at ~1/4 size, smoothed back up), blur (gaussian), gray (luminance only, blurred), deutan (Machado 2009
deuteranopia, severity 1.0, blurred). Output per design: 4 clean reference images + 16 shuffled test images + a private answer key."""
import os, sys, json, random
import numpy as np
from PIL import Image, ImageFilter
T=os.path.dirname(os.path.abspath(__file__))
designs=sys.argv[1:]
M=np.array([[0.367322,0.860646,-0.227968],[0.280085,0.672501,0.047413],[-0.011820,0.042940,0.968881]])
def deutan(im):
    a=np.asarray(im.convert('RGB')).astype(float)/255.0
    lin=np.where(a<=0.04045,a/12.92,((a+0.055)/1.055)**2.4)
    out=np.clip(lin@M.T,0,1)
    srgb=np.where(out<=0.0031308,out*12.92,1.055*out**(1/2.4)-0.055)
    return Image.fromarray((srgb*255).astype('uint8'))
key_all={}
for d in designs:
    od=f'{T}/glance/{d}'; os.makedirs(od,exist_ok=True)
    rnd=random.Random(hash(d)%10**6); items=[]
    for st in ('green','yellow','red','nodata'):
        full=Image.open(f'{T}/render/{d}/{st}.png').convert('RGB').resize((393,852))
        full.crop((0,44,393,560)).save(f'{od}/ref-{st}.png')
        base=full.crop((0,44,393,560))
        small=base.resize((98,129),Image.BILINEAR).resize((393,516),Image.BILINEAR)
        blur=base.filter(ImageFilter.GaussianBlur(5))
        gray=blur.convert('L').convert('RGB')
        dt=deutan(blur)
        for deg,img in (('small',small),('blur',blur),('gray',gray),('deutan',dt)): items.append((deg,st,img))
    rnd.shuffle(items); key={}
    for i,(deg,st,img) in enumerate(items):
        name=f'test-{i+1:02d}.png'; img.save(f'{od}/{name}'); key[name]={'state':st,'degradation':deg}
    key_all[d]=key
json.dump(key_all,open(os.path.join(os.path.dirname(T),'glance-key.json'),'w'),indent=1)
print('glance sets:',designs)
