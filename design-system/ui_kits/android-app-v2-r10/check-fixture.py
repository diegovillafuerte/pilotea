#!/usr/bin/env python3
"""Golden-fixture checker for the Kompara v2 mockups and spec.
Derives every displayed figure from fixture.json (raw inputs only) and asserts each one INSIDE ITS NAMED FRAME's screen text
(tags stripped; captions and the fixture summary box are excluded), checks the free-window date rule, asserts the key figures in the
spec, and fails on known stale strings. It does not compare spec prose with frame prose. Exit 1 on any problem."""
import json, sys, os, re, math, datetime as dt
H=os.path.dirname(os.path.abspath(__file__)); F=json.load(open(os.path.join(H,'fixture.json')))
raw=open(os.path.join(H,'index.html')).read()
spec_path=sys.argv[sys.argv.index('--spec')+1] if '--spec' in sys.argv else os.path.join(H,'../../../docs/ux-elevation-spec.md')
spec=open(spec_path).read() if os.path.exists(spec_path) else ''
def strip(x): return re.sub(r'\s+',' ',re.sub(r'<[^>]+>','',x))
frames={}; caps={}
for chunk in raw.split('<div class="frame">')[1:]:
    ci=chunk.find('<div class="cap">'); cap=chunk[ci:] if ci>=0 else ''; m=re.search(r'<b>([A-Z]\d[a-z]?)\b',cap)
    if not m: continue
    frames[m.group(1)]=strip(chunk[:ci] if ci>=0 else chunk); caps[m.group(1)]=strip(cap)
def r(x): return int(math.floor(x+0.5))
meses_full=['','enero','febrero','marzo','abril','mayo','junio','julio','agosto','septiembre','octubre','noviembre','diciembre']
def pesos(x): return '$'+format(r(x),',')
cpk=F['costs']['fuel_price_per_l']/F['costs']['km_per_l']+F['costs']['wear_per_km']
def net(g,km): return g-km*cpk
E={}  # frame -> list of expected strings
def ex(fr,*vals): E.setdefault(fr,[]).extend(vals)
S=[]  # strings that must also be in the spec
ch=F['chips']; cn={k:net(v['fare'],v['km']) for k,v in ch.items()}
for fr,k in (('C1','verde'),('C2','amarillo'),('C3','rojo')):
    ex(fr, f'${cn[k]/ch[k]["km"]:.2f}/km', pesos(cn[k]/ch[k]['min']*60)+'/h'); S.append(f'${cn[k]/ch[k]["km"]:.2f}/km')
ex('C4','Sin lectura'); ex('C5','Falta tiempo','sin semáforo')
sh=F['today_shifts']; nets=[net(x['gross'],x['km']) for x in sh]; day=sum(nets); trips=sum(x['trips'] for x in sh)
b1=nets[0]+cn['verde']; hourly=F['today_hourly_net_shift1']; assert r(sum(hourly.values()))==r(nets[0]), 'hourly nets must sum to shift 1'
best=max(hourly,key=hourly.get); ex('B1',pesos(b1),'6 viajes tomados',f'Tu mejor hora hoy: {best} a {int(best)+1} h'); S+= [pesos(b1),pesos(nets[0]),pesos(nets[1]),pesos(day)]
ex('B2',pesos(day)); ex('B5',pesos(nets[0]))
ex('D1',pesos(day),f'{trips} viajes tomados',pesos(nets[0]),pesos(nets[1]),f'${cn["verde"]/ch["verde"]["km"]:.2f}/km',f'${cn["rojo"]/ch["rojo"]["km"]:.2f}/km')
ex('D2',pesos(cn['verde']),pesos(ch['verde']['fare']),f'{ch["verde"]["km"]:.1f} km',f'{ch["verde"]["min"]} min','−'+pesos(ch['verde']['km']*cpk),f'${cn["verde"]/ch["verde"]["km"]:.2f}/km')
ex('D3',pesos(sum(x['gross'] for x in sh)),f"{sum(x['km'] for x in sh):.1f} km",'−'+pesos(sum(x['km'] for x in sh)*cpk),pesos(day))
car=F['car_cost']['weekly']; per_day=car/7
W=F['weeks']; w=W['2026-10-12']; wn=net(w['gross'],w['km'])-car; days=w['days']; fri=days['V']
assert sum(v['trips'] for v in days.values())==w['trips'] and abs(sum(v['gross'] for v in days.values())-w['gross'])<0.01 and abs(sum(v['km'] for v in days.values())-w['km'])<0.01, 'days must sum to the week'
best_day=max(days,key=lambda k: net(days[k]['gross'],days[k]['km'])); dname={'L':'lunes','M1':'martes','M2':'miércoles','J':'jueves','V':'viernes','S':'sábado','D':'domingo'}[best_day]
ex('E1',pesos(wn),f'{w["trips"]} viajes tomados','−'+pesos(car),f'Tu mejor día fue el {dname}','Tu lugar entre los de Uber en CDMX')
ex('B4',pesos(net(days['D']['gross'],days['D']['km'])),days['D']['last_read']); S+=[pesos(wn),pesos(net(w['gross'],w['km']))]
ex('E7b',pesos(wn),'Sin conexión'); ex('E8',pesos(b1-per_day),pesos(per_day),'6 viajes tomados'); S.append(pesos(b1-per_day))
u,dd=w['by_app']['uber'],w['by_app']['didi']; ukm=net(u['gross'],u['km'])/u['km']; dkm=net(dd['gross'],dd['km'])/dd['km']
ex('F1',f'${ukm:.2f}/km',f'${dkm:.2f}/km',f'${dkm-ukm:.2f}'); S+=[f'${ukm:.2f}/km',f'${dkm:.2f}/km',f'${dkm-ukm:.2f}']
st=w['statement_uber']; spt=st['paid']/st['trips']; co=F['cohort']; dec=co['per_trip_deciles']; ks=sorted(int(k) for k in dec); pct=None
for a,b in zip(ks,ks[1:]):
    if dec[str(a)]<=spt<=dec[str(b)]: pct=a+(b-a)*(spt-dec[str(a)])/(dec[str(b)]-dec[str(a)]); break
mean=co['sum_driver_per_trip']/co['drivers']
ex('F2',f'{r(pct)} %',pesos(spt)+'/viaje',pesos(mean)+'/viaje',format(co['drivers'],',')+' conductores'); S+=[f'{r(pct)} %',pesos(spt)+'/viaje',pesos(mean)]
ex('H3',f'{r(pct)} %',str(st['trips'])); ex('H1',pesos(spt))
w2=W['2026-10-05']; w2n=net(w2['gross'],w2['km'])-car; ex('E2',pesos(w2n),f'{w2["trips"]} viajes tomados'); ex('F5',pesos(w2['by_app']['uber']['gross']/w2['by_app']['uber']['trips'])+'/viaje')
dn,dp=dd,w2['by_app']['didi']; ex('F4',pesos(dn['gross']/dn['trips'])+'/viaje',pesos(dp['gross']/dp['trips'])+'/viaje',f"+{r((dn['gross']/dn['trips'])/(dp['gross']/dp['trips'])*100-100)} %")
w3=W['2026-09-28']; rd_=4; w3n=net(w3['gross'],w3['km'])-rd_*per_day
rows=[day-per_day,wn,w2n,w3n]; octo=sum(rows)
ex('E3',pesos(octo),f'{trips+w['trips']+w2['trips']+w3['trips']} viajes tomados','La mejor fue 12 – 18 oct'); S+=[pesos(octo)]
inc=day+net(w['gross'],w['km'])+net(w2['gross'],w2['km'])+net(w3['gross'],w3['km']); ex('D3b',pesos(octo),'−'+pesos(19*per_day),pesos(inc))
ex('E3d',pesos(octo),pesos(sum(r(x) for x in rows)),*[pesos(x) for x in rows])
dn2=[net(days[k]['gross'],days[k]['km']) for k in ['L','M1','M2','J','V','S','D']]; ex('E1b',*[pesos(x) for x in dn2],pesos(sum(dn2)),pesos(sum(r(x) for x in dn2)),pesos(wn))
for fr_ in ('A1','B3','G2'): ex(fr_,pesos(cn['verde']/ch['verde']['min']*60)+'/h')
ex('A5',f'${cpk:.2f} por km',pesos(12*cpk)); ex('H5c',pesos(w2['by_app']['didi']['gross']),f"{w2['by_app']['didi']['trips']} viajes")
ex('E2',f'hace {(dt.date(2026,11,2)-dt.date(2026,10,5)).days//7} semanas'); ex('E6','19 – 25 oct · en curso','12 – 18 oct','5 – 11 oct','28 sep – 4 oct','14 – 20 sep')
for fr_ in ('H1','H2','H2b'): ex(fr_,f"${F['plan']['price_monthly']}")
ex('G5',f"{dt.date.fromisoformat(F['car_cost']['from']).day} sep")
cn7=net(F['chips']['rojo_negativo']['fare'],F['chips']['rojo_negativo']['km']); ex('C7',f'−${-cn7/F["chips"]["rojo_negativo"]["km"]:.2f}/km','−'+pesos(-cn7/F['chips']['rojo_negativo']['min']*60)+'/h')
b6=F['b6_partial_support']; ex('B6',pesos(b6['net']),f'{b6["trips"]} viajes tomados',b6['hours']); ex('G9',f'${F["city_proposals"]["Monterrey"]["verde_min_per_km"]:.2f}/km')
alt=F['alt_scenarios']['E3c_sept_mixed']['weeks']; a1=net(alt['2026-09-21']['gross'],alt['2026-09-21']['km']); a2=net(alt['2026-09-28_partial']['gross'],alt['2026-09-28_partial']['km'])-alt['2026-09-28_partial']['days']*per_day
ex('E3c',pesos(a1+a2),f"{alt['2026-09-21']['trips']+alt['2026-09-28_partial']['trips']} viajes tomados",pesos(W['2026-09-14']['paid']),'no entra en el total','La mejor fue 21 – 27 sep')
uber_oct=F['today_by_app']['uber']+u['gross']+w2['by_app']['uber']['gross']+w3['by_app']['uber']['gross']; didi_oct=F['today_by_app']['didi']+dd['gross']+w2['by_app']['didi']['gross']+w3['by_app']['didi']['gross']
ex('E4',pesos(uber_oct),pesos(didi_oct)); f_=F['imss']['example_factor']; mn=F['imss']['min_monthly_2026']
ex('E5',pesos(uber_oct*f_),pesos(didi_oct*f_),pesos(mn-r(didi_oct*f_)),pesos(mn),str(f_)); S+=[pesos(uber_oct*f_),pesos(didi_oct*f_),pesos(mn-r(didi_oct*f_))]
imp=W['2026-09-14']; ex('E3b',pesos(imp['paid']),f'{imp["trips"]} viajes'); ex('H5',pesos(imp['paid']),str(imp['trips']),f'{imp["hours"]} h',f'{imp["commission_pct"]} %'); ex('H5d',pesos(imp['paid']),str(imp['trips']),f'{imp["hours"]} h'); S+=[pesos(imp['paid'])]
h5=F['h5b_partial_import']; hs=h5['statement']; wk=W[h5['uber_week']]
ex('H5b',pesos(hs['paid']),str(hs['trips']),str(h5['reader_trips_with_km']),f'{h5["km_known"]:.1f}','−'+pesos(h5['km_known']*cpk),f'faltan los km de {h5["trips_without_km"]} viajes',pesos(wk['by_app']['uber']['gross'])); S+=[pesos(hs['paid']),pesos(h5['km_known']*cpk)]
assert h5['reader_trips_with_km']+h5['trips_without_km']==hs['trips'] and h5['reader_trips_with_km']==wk['by_app']['uber']['trips'] and abs(h5['km_known']-wk['by_app']['uber']['km'])<0.01, 'H5b must bind to its week'
ex('G2',f'${F["floors"]["verde_min_per_km"]:.2f}/km',f'${F["floors"]["rojo_below_per_km"]:.2f}/km'); ex('G3',f'${F["costs"]["fuel_price_per_l"]:.2f}',f'{F["costs"]["km_per_l"]} km'); ex('G5',pesos(car),pesos(per_day))
pu=dt.date.fromisoformat(F['plan']['premium_until']); viewed=dt.date.fromisoformat(F['plan']['h2b_viewed_on']); ex('H2b',f'{(pu-viewed).days} días',f'{pu.day} de noviembre'); ex('H2',f'{pu.day} de noviembre'); ex('H2d',f'{pu.day} de noviembre')
pd_=dt.date.fromisoformat(F['plan']['purchase_date']); renew=dt.date(pd_.year+(pd_.month==12),(pd_.month%12)+1,pd_.day); grace=renew+dt.timedelta(days=F['plan']['grace_days']); ex('H2c',f'{grace.day} de {meses_full[grace.month]}'); banked=grace+dt.timedelta(days=(pu-pd_).days); ex('H2c',f'{banked.day} de {meses_full[banked.month]}')
ex('E3d',pesos(net(w3['gross'],w3['km'])-car))
ex('A1',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km'); ex('B3',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km'); ex('G2',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km')
ex('G1',f'${cpk:.2f} por km',f'${F["floors"]["verde_min_per_km"]:.2f}/km',pesos(car))
def hm(hours): return f'{int(hours)} h {r((hours-int(hours))*60)} min' if r((hours-int(hours))*60) else f'{int(hours)} h'
def dur(a,b): (h1,m1),(h2,m2)=[map(int,x.split(':')) for x in (a,b)]; return (h2*60+m2-h1*60-m1)/60
ex('D1',hm(sum(dur(x['start'],x['end']) for x in sh))); ex('B5',hm(dur(sh[0]['start'],sh[0]['end']))); ex('H6',f'{trips} viajes',f'{w["trips"]} viajes')
pass
# free-window rule: a week leaves "this week + 3 previous" on the Monday 4 weeks after its start
e2_start=dt.date(2026,10,5); leaves=e2_start+dt.timedelta(weeks=4); meses=['','ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic']
cap_exp={'E2':[f'{leaves.day} {meses[leaves.month]}']}
stale_caps=['one number each','after the free access, never before','states that the payment counts','9 nov','Gris = no se pudo leer','Purchase pending / cancelled use the same card']
fails=0
for fr,vals in E.items():
    if fr not in frames: fails+=1; print(f'NO FRAME {fr}'); continue
    for v in vals:
        if v not in frames[fr]: fails+=1; print(f'MISSING in {fr}: {v!r}')
for fr,vals in cap_exp.items():
    for v in vals:
        if v not in caps.get(fr,''): fails+=1; print(f'MISSING in caption {fr}: {v!r}')
if '$'+f'{cn["verde"]/ch["verde"]["km"]:.2f}' in frames.get('C5',''): fails+=1; print('C5 carries a rate')
for fr,cp in caps.items():
    for sp in stale_caps:
        if sp in cp: fails+=1; print(f'STALE caption {fr}: {sp!r}')
for v in S:
    if spec and v not in spec: fails+=1; print(f'MISSING in spec: {v!r}')
stale_frames=['la regla oficial se enlaza','(28 – 30 sep, antes de instalar','$640','$600 ','$4,190','$13,920','$7.20','$113.92','$85.92','$11,850','$7,925','$1,657','$27.07','$126/viaje','$1,148','Viajes 36','no se pudo leer','Premium se pausa','Guardar solo lo leído','no manejaste','Viaje confirmado','· estimado','(estimado)','Estimado de',' est.','17 – 23 ago','4 h 50 min','Tu lugar se calcula al cerrar','Seguir con Premium después']
stale_spec=['Estimado de viajes tomados','Estimado de 9 viajes','106 h en turno · hasta hoy','$4,190','$13,920','$113.92','$85.92','$11,850','$7,925','$1,657','$27.07','$1,148','17 – 23 ago','Tu lugar se calcula al cerrar','Premium se pausa','Guardar solo lo leído','9 nov, the first Monday']
alltext=' '.join(frames.values())
for sp in stale_frames:
    n=alltext.count(sp)
    if n: fails+=1; print(f'STALE in frames: {sp!r} ×{n}')
for sp in stale_spec:
    n=spec.count(sp)
    if n: fails+=1; print(f'STALE in spec: {sp!r} ×{n}')

import datetime as dt
# bar charts: E1/E7b day bars are the day nets (before the car cost) relative to the best day; E8's today bar is relative to that same best day
order=['L','M1','M2','J','V','S','D']; dn_=[net(days[k]['gross'],days[k]['km']) for k in order]; mx=max(dn_); exp_bars=[round(x/mx*100) for x in dn_]
def bars(fr):
    m=re.search(r'<div class="spark">(.*?)</div>',raw.split('<div class="frame">')[list(frames).index(fr)+1] if fr in frames else '',re.S)
    return [int(x) for x in re.findall(r'height:(\d+)%',m.group(1))] if m else None
for fr in ('E1','E7b'):
    got=bars(fr)
    if got!=exp_bars: fails+=1; print(f'BARS {fr}: {got} expected {exp_bars}')
g8=bars('E8')
if not g8 or g8[0]!=100: fails+=1; print(f'BARS E8: {g8} expected first 100 (the open week scales to its own best day)')
g3=bars('E3'); e3exp=[round(x/max(rows)*100) for x in [rows[3],rows[2],rows[1],rows[0]]]
if g3!=e3exp: fails+=1; print(f'BARS E3: {g3} expected {e3exp}')
g3c=bars('E3c'); e3cexp=[0,100,round(a2/a1*100)]
if g3c!=e3cexp: fails+=1; print(f'BARS E3c: {g3c} expected {e3cexp}')
nE=sum(len(v) for v in E.values())
print(f'checked {nE} figures in {len(E)} frames, {len(S)} spec strings, 1 date rule, {len(stale_frames)+len(stale_spec)} stale patterns, {len(stale_caps)} caption lints, 5 bar charts → {"OK" if not fails else str(fails)+" problems"}')
sys.exit(1 if fails else 0)
