"""Build the 4-state snippets for the v3 chip and the Round 10 chip (control) in the same injectable format as the generic chips,
and a host template that carries ONLY host CSS (no product chip CSS), so every design is rendered on an identical host."""
import re, os, json
T=os.path.dirname(os.path.abspath(__file__))
K='/Users/panama/pilotea/design-system/ui_kits/android-app-v2'
R10='/Users/panama/pilotea/design-system/ui_kits/android-app-v2-r10'
def rules(css):
    css=re.sub(r'/\*.*?\*/','',css,flags=re.S)
    out=[];depth=0;buf=''
    for ch in css:
        buf+=ch
        if ch=='{': depth+=1
        elif ch=='}':
            depth-=1
            if depth==0: out.append(buf.strip()); buf=''
    return out
v3css=open(f'{K}/base-v3.css').read()
R=rules(v3css)
HOST_KEYS=['.phone','.statusbar','.map','.hosttop','.offer','.host-top','html,body','*{',':root','.grid','.frame']
host=[r for r in R if any(k in r.split('{')[0] for k in HOST_KEYS) and '.chip' not in r and 'lector-tab' not in r and '.night' not in r.split('{')[0]]
chipr=[r for r in R if '.chip' in r.split('{')[0] and '.sample' not in r.split('{')[0]]
tpl=open(f'{T}/host-template.html').read()
tpl=re.sub(r'<link rel="stylesheet" href="file:///Users/panama/pilotea/design-system/ui_kits/android-app-v2/base-v3.css">','<style>\n'+'\n'.join(host)+'\n</style>',tpl)
open(f'{T}/host-clean.html','w').write(tpl)
# v3 chip snippets (CSS tokens resolved to literals so nothing depends on :root)
tok={'--chip-ink':'#0E0F10','--on-chip-ink':'#EDEDEA','--verde':'#187A36','--on-verde':'#FFFFFF','--amarillo':'#F2B705','--on-amarillo':'#1A1300','--rojo':'#D32F2F','--on-rojo':'#FFFFFF','--font-money':'"Barlow Semi Condensed","Roboto Condensed",sans-serif','--font-ui':'"Barlow","Roboto",system-ui,sans-serif'}
v3c='\n'.join(chipr)
for k,v in tok.items(): v3c=v3c.replace(f'var({k})',v)
v3c="@import url('https://fonts.googleapis.com/css2?family=Barlow+Semi+Condensed:wght@500;600;700&family=Barlow:wght@400;600&display=swap');\n"+v3c
def chip(cls,zt,zb): return f'<div class="chip {cls}"><div class="zt">{zt}</div><div class="lane"></div><div class="zb">{zb}</div><span class="kk">K</span></div>'
v3={'green':chip('v','$9.49<small>/km</small>','neto aprox. · $285/h'),'yellow':chip('a','$7.16<small>/km</small>','neto aprox. · $191/h'),'red':chip('r','$2.20<small>/km</small>','neto aprox. · $54/h'),'nodata':chip('g','Sin lectura','falta la tarifa')}
os.makedirs(f'{T}/gen/v3',exist_ok=True)
for k,m in v3.items(): open(f'{T}/gen/v3/{k}.html','w').write(f'<style>{v3c}</style>{m}')
# Round 10 chip (control): its CSS from r10 base.css + head, classes prefixed r10- so nothing leaks
r10css='\n'.join(r for r in rules(open(f'{R10}/base.css').read()) if '.chip' in r.split('{')[0])
head=open(f'{R10}/_head-ab.html').read()
headcss=re.search(r'<style>(.*?)</style>',head,re.S).group(1)
r10css+='\n'+'\n'.join(r for r in rules(headcss) if any(k in r.split('{')[0] for k in ['.k-ov','.g ','.chip']) or r.split('{')[0].strip()=='.g')
r10vars={'var(--verde)':'#187a36','var(--amarillo)':'#f2b705','var(--on-amarillo)':'#1a1300','var(--rojo)':'#d32f2f','var(--emerald-600)':'#059669','var(--font)':'"Inter",system-ui,sans-serif'}
for k,v in r10vars.items(): r10css=r10css.replace(k,v)
r10css=re.sub(r'\.(chip|body|rate2|rate|word|hint|strip|green|yellow|red|gray|compact|wide|k-ov|g)\b',lambda m:'.r10-'+m.group(1),r10css)
r10css="@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&display=swap');\n"+r10css
pc=open(f'{R10}/part-c.html').read()
def r10chip(cls_word):
    m=re.search(r'<div class="chip '+cls_word+r'[^"]*" style="[^"]*">(.*?)<div class="k-ov"[^>]*>K</div></div>',pc,re.S)
    inner=m.group(0)
    inner=re.sub(r'style="[^"]*"','style="right:12px;top:144px;min-width:168px"',inner,count=1)
    inner=re.sub(r'class="([^"]*)"',lambda mm:'class="'+' '.join('r10-'+c for c in mm.group(1).split())+'"',inner)
    return inner
r10={'green':r10chip('green'),'yellow':r10chip('yellow'),'red':r10chip('red'),'nodata':r10chip('gray')}
os.makedirs(f'{T}/gen/r10',exist_ok=True)
for k,m in r10.items(): open(f'{T}/gen/r10/{k}.html','w').write(f'<style>{r10css}</style>{m}')
print('host rules',len(host),'· v3 chip rules',len(chipr),'· r10 css chars',len(r10css))
print(r10['green'][:200])
