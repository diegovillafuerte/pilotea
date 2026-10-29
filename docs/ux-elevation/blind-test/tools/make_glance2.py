"""Harsh glance proxy (peripheral view of a ~3°-wide chip on a windshield mount, ~25° off-axis).
tiny: whole phone at 40px wide, smoothed back up · blur14: gaussian 14 · gray14: luminance of blur14 · deutan14: Machado deuteranopia of blur14 ·
glare: blur 8, contrast ×0.4, brightness +35% (sun on the screen). 4 states × 5 = 20 shuffled tests per design."""
import os, sys, json, random
import numpy as np
from PIL import Image, ImageFilter, ImageEnhance
T=os.path.dirname(os.path.abspath(__file__))
M=np.array([[0.367322,0.860646,-0.227968],[0.280085,0.672501,0.047413],[-0.011820,0.042940,0.968881]])
def deutan(im):
    a=np.asarray(im.convert('RGB')).astype(float)/255.0
    lin=np.where(a<=0.04045,a/12.92,((a+0.055)/1.055)**2.4); out=np.clip(lin@M.T,0,1)
    return Image.fromarray((np.where(out<=0.0031308,out*12.92,1.055*out**(1/2.4)-0.055)*255).astype('uint8'))
keys={}
for d in sys.argv[1:]:
    od=f'{T}/glance2/{d}'; os.makedirs(od,exist_ok=True); rnd=random.Random(sum(map(ord,d))*7+3); items=[]
    for st in ('green','yellow','red','nodata'):
        full=Image.open(f'{T}/render/{d}/{st}.png').convert('RGB').resize((393,852))
        base=full.crop((0,44,393,560)); base.save(f'{od}/ref-{st}.png')
        tiny=full.resize((40,87),Image.BILINEAR).resize((393,852),Image.BILINEAR).crop((0,44,393,560))
        b14=base.filter(ImageFilter.GaussianBlur(14))
        glare=ImageEnhance.Brightness(ImageEnhance.Contrast(base.filter(ImageFilter.GaussianBlur(8))).enhance(0.4)).enhance(1.35)
        for deg,img in (('tiny',tiny),('blur14',b14),('gray14',b14.convert('L').convert('RGB')),('deutan14',deutan(b14)),('glare',glare)): items.append((deg,st,img))
    rnd.shuffle(items); key={}
    for i,(deg,st,img) in enumerate(items):
        n=f'test-{i+1:02d}.png'; img.save(f'{od}/{n}'); key[n]={'state':st,'degradation':deg}
    keys[d]=key
    g=open(f'{T}/glance/GLANCE.md').read().replace('test-01.png … test-16.png','test-01.png … test-20.png').replace('all sixteen','all twenty').replace('sixteen test images','twenty test images').replace('tiny, blurred, without colour, or as a red-green colour-blind driver sees it','very small, heavily blurred, without colour, as a red-green colour-blind driver sees it, or washed out by sunlight')
    open(f'{od}/GLANCE.md','w').write(g)
json.dump(keys,open(os.path.join(os.path.dirname(T),'glance2-key.json'),'w'),indent=1)
print('harsh sets:',list(keys))
