"""tally_run5.py <workflow output .json> <walk5 dir> — run 5 (interactive prototype): per-task self-report (success, SEQ) and the
prototype's own log (taps, misses, end screen, time) per judge folder j1…j6; gate: mean SEQ >= 6.0, vs Uber >= 7.5, no task < 5.5."""
import json, re, sys, os
journal, W = sys.argv[1], sys.argv[2]
rep = {}
txt = open(journal).read()
try: items = json.loads(txt).get('result', [])  # the workflow's output file: {"result": [{"j", "model", "report"}, …]}
except Exception: items = []
for it in items:
    if isinstance(it, dict) and 'report' in it: rep[it['j']] = (it.get('model'), it['report'])
D = {}
for jn, (model, t) in sorted(rep.items()):
    m = re.findall(r'```json\s*(\{.*?)\s*```', t or '', re.S)
    if m:
        try: D[jn] = (model, json.JSONDecoder().raw_decode(m[-1].strip())[0])  # tolerate trailing text after the object
        except Exception as e: print('bad json', jn, e)
logs = {}
for jn in range(1, 7):
    p = os.path.join(W, f'j{jn}', 'log.json')
    if os.path.exists(p):
        s = json.load(open(p)); a = json.loads(s.get('kompara-prototipo-v1', '{}')).get('attempts', [])
        logs[jn] = {x['task']: x for x in a}
OK = {'8': {'A3s', 'B3', 'A3b'}, '1': {'B1'}, '2': {'C2'}, '3': {'D1a'}, '4': {'E1', 'E1d', 'E1b'}, '5': {'F1', 'E1f'}, '6': None, '7': {'A3', 'A3b', 'A3s', 'B2r'}, '9': {'D2e', 'D2'}}
names = {'8': 'First day setup', '1': 'Hoy: kept today + reader', '2': 'Offer: worth it?', '3': 'Every offer today', '4': 'Last week after car', '5': 'Which app paid more', '6': 'Change tu mínimo', '7': 'Overlay stopped', '9': 'Last trip detail'}
print(f'{len(D)} judges · logs from {len(logs)}')
print('| task | self success (yes/partial/no) | reached the answer screen (log) | mean taps (log) | mean misses (log) | mean time (s) | mean SEQ |')
print('|---|---|---|---|---|---|---|')
E = []; per = {}
for k, n in names.items():
    s = [d['tasks'][k]['success'] for _, d in D.values() if k in d['tasks']]
    e = [d['tasks'][k]['ease'] for _, d in D.values() if k in d['tasks']]; E += e; per[k] = sum(e) / len(e) if e else 0
    L = [logs[j][int(k)] for j in logs if int(k) in logs[j]]
    def reached(x):
        ids = {y['id'] for y in x.get('screens', [])}
        if k == '6': return x.get('limits', {}).get('min') == '6.50'
        return bool(ids & OK[k])
    rch = sum(1 for x in L if reached(x))
    tp = [x.get('tapCount', 0) for x in L]; ms = [x.get('missCount', 0) for x in L]; tm = [x.get('durationMs', 0) / 1000 for x in L]
    avg = lambda a: sum(a) / len(a) if a else float('nan')
    print(f"| {n} | {s.count('yes')}/{s.count('partial')}/{s.count('no')} | {rch}/{len(L)} | {avg(tp):.1f} | {avg(ms):.1f} | {avg(tm):.0f} | {per[k]:.2f} |")
vs = [d['ease_vs_uber'] for _, d in D.values()]; un = [d['understand_all'] for _, d in D.values()]
print(f"\nmean SEQ {sum(E)/len(E):.2f} · ease vs Uber {sum(vs)/len(vs):.1f}/10 · understand every screen {sum(un)/len(un):.1f}/10 · lowest task {min(per.values()):.2f}")
us = [d.get('uber_seq', {}) for _, d in D.values()]
for k in ('1', '4', '9'):
    u = [x[k] for x in us if k in x]
    if u: print(f"matched task {k}: Kompara {per[k]:.2f} vs Uber (self-estimated) {sum(u)/len(u):.2f} → diff {per[k]-sum(u)/len(u):+.2f}")
gate = sum(E)/len(E) >= 6.0 and sum(vs)/len(vs) >= 7.5 and min(per.values()) >= 5.5
print('GATE (mean >= 6.0, vs Uber >= 7.5, no task < 5.5):', 'PASS' if gate else 'NOT MET')
# the driver-test plan's pre-registered rule, applied to the model run (comprehension probes are not part of the walkthrough)
mean=sum(E)/len(E); comp=all((per[k]-sum(x[k] for x in us if k in x)/max(1,len([x for x in us if k in x])))>=-0.5 for k in ('1','4','9') if any(k in x for x in us))
mi=[d.get('money_inconsistency') for _,d in D.values() if isinstance(d.get('money_inconsistency'),dict)]
money_ok=(not any(m.get('any') for m in mi)) if mi else None
flags=[names[k] for k in per if per[k]<5.5]
r1='PASS' if mean>=6.0 else ('ADD FIVE (5.6–5.99)' if mean>=5.6 else 'FAIL')
print(f"PLAN RULE: 1 overall {mean:.2f} → {r1} · 2 matched vs Uber → {'PASS' if comp else 'FAIL'} · 3 per-task flags: {flags or 'none'} · 5 money consistency → {('PASS' if money_ok else 'FAIL — '+'; '.join(m.get('which','')[:120] for m in mi if m.get('any'))) if money_ok is not None else 'not asked'}")
for jn, (model, d) in sorted(D.items()):
    print(f'\n--- judge {jn} ({model}) · vs Uber {d["ease_vs_uber"]} · understood {d["understand_all"]} · install: {d.get("install","")[:160]}')
    for p in d['problems']: print('-', p['screen'], '|', p['problem'][:220], '| fix:', p['fix'][:180])
