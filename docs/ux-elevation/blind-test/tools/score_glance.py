import json,re,sys,os
T=os.path.dirname(os.path.abspath(__file__))
key=json.load(open(os.path.join(os.path.dirname(T),'glance-key.json')))
def parse(path):
    s=open(path).read(); m=re.findall(r'```json\s*(\{.*?\})\s*```',s,re.S)
    return json.loads(m[-1]) if m else None
def score(d,ans):
    tot={};ok={}
    for name,v in key[d].items():
        deg=v['degradation']; a=ans.get(name)
        st=a['state'] if isinstance(a,dict) else a
        tot[deg]=tot.get(deg,0)+1; ok[deg]=ok.get(deg,0)+(1 if st==({'green':'verde','yellow':'amarillo','red':'rojo','nodata':'sin datos'}[v['state']]) else 0)
    return ok,tot
rows=[]
src=sys.argv[1] if len(sys.argv)>1 else 'codex'
for d in ('v3','r10','opus-1','sonnet-1','haiku-2','codex-1'):
    p=f'{T}/glance-out/{d}-{src}.md'
    if not os.path.exists(p): continue
    ans=parse(p)
    if not ans: print(d,'unparsed'); continue
    ok,tot=score(d,ans)
    rows.append((d,ok,tot))
    print(f"{d:9s} "+'  '.join(f"{k}:{ok[k]}/{tot[k]}" for k in ('small','blur','gray','deutan'))+f"   total {sum(ok.values())}/{sum(tot.values())}")
