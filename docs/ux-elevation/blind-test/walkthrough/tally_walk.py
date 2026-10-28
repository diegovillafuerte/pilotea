"""tally_walk.py <journal.jsonl> [codex.md] — per-task success, taps and ease from the walkthrough judges."""
import json, re, sys
outs=[]
for line in open(sys.argv[1]):
    j=json.loads(line)
    if j.get('type')!='result': continue
    v=j.get('value') or j.get('result'); t=v if isinstance(v,str) else json.dumps(v)
    if '"tasks"' in t: outs.append(t)
for c in sys.argv[2:]:
    try: outs.append(open(c).read())
    except Exception as e: print('missing',c)
D=[]
for t in outs:
    m=re.findall(r'```json\s*(\{.*?\})\s*```',t,re.S)
    if m: D.append(json.loads(m[-1]))
names={'1':'Hoy: kept today + reader','2':'Offer: worth it?','3':'Every offer today','4':'Last week after car','5':'Which app paid more','6':'Change tu mínimo','7':'Overlay stopped','8':'First day setup'}
print(f'{len(D)} judges'); print('| task | success (yes/partial/no) | mean taps | mean ease (1–7) |'); print('|---|---|---|---|')
E=[]
for k,n in names.items():
    s=[d['tasks'][k]['success'] for d in D if k in d['tasks']]; tp=[d['tasks'][k].get('taps',0) for d in D if k in d['tasks']]; e=[d['tasks'][k]['ease'] for d in D if k in d['tasks']]
    E+=e; print(f"| {n} | {s.count('yes')}/{s.count('partial')}/{s.count('no')} | {sum(tp)/len(tp):.1f} | {sum(e)/len(e):.1f} |")
print(f"mean ease {sum(E)/len(E):.2f} · ease vs Uber {sum(d['ease_vs_uber'] for d in D)/len(D):.1f}/10 · understand every screen {sum(d['understand_all'] for d in D)/len(D):.1f}/10")
for d in D:
    for p in d['problems']: print('-',p['screen'],'|',p['problem'][:200],'| fix:',p['fix'][:160])
    print('---')
