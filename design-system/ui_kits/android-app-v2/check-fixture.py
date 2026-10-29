#!/usr/bin/env python3
"""Golden-fixture checker for the Kompara v2 mockups and spec.
Derives every displayed figure from fixture.json (raw inputs only) and asserts each one INSIDE ITS NAMED FRAME's screen text
(tags stripped; captions and the fixture summary box are excluded), checks the free-window date rule, asserts the key figures in the
spec, and fails on known stale strings. It does not compare spec prose with frame prose. Exit 1 on any problem."""
import json, sys, os, re, math, datetime as dt
H=os.path.dirname(os.path.abspath(__file__)); F=json.load(open(os.path.join(H,'fixture.json')))
raw=open(os.environ.get('KOMPARA_INDEX') or os.path.join(H,'index.html')).read()
spec_path=sys.argv[sys.argv.index('--spec')+1] if '--spec' in sys.argv else os.path.join(H,'../../../docs/ux-elevation-spec.md')
spec=open(spec_path).read() if os.path.exists(spec_path) else ''
def strip(x): return re.sub(r'\s+',' ',re.sub(r'<[^>]+>','',x))
frames={}; caps={}; chunks={}
for chunk in raw.split('<div class="frame">')[1:]:
    ci=chunk.find('<div class="cap">'); cap=chunk[ci:] if ci>=0 else ''; m=re.search(r'<b>([A-Z]\d+[a-z]?)\b',cap)
    if not m: continue
    frames[m.group(1)]=strip(chunk[:ci] if ci>=0 else chunk); caps[m.group(1)]=strip(cap); chunks[m.group(1)]=chunk
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
    ex(fr, f'${cn[k]/ch[k]["km"]:.2f}/km'); S.append(f'${cn[k]/ch[k]["km"]:.2f}/km')  # Round 16: the chip carries the deciding rate only; $/h lives in the offer detail (D2)
ex('C4','Sin lectura'); fh=F['floors_per_hour_example']; ex('C5','Falta tiempo','sin cálculo',f"${fh['verde_min_per_hour']}/h",f"${fh['rojo_below_per_hour']}/h")
sh=F['today_shifts']; nets=[net(x['gross'],x['km']) for x in sh]; day=sum(nets); trips=sum(x['trips'] for x in sh)
b1=nets[0]+cn['verde']; hourly=F['today_hourly_net_shift1']; assert r(sum(hourly.values()))==r(nets[0]), 'hourly nets must sum to shift 1'
pd0=F['car_cost']['weekly']/7  # Round 17: every 'te quedan' total is after the car's daily share (one money definition)
best=max(hourly,key=hourly.get); ex('B1',pesos(b1-pd0),'6 viajes tomados'); S+= [pesos(b1-pd0),pesos(day-pd0),pesos(nets[0]-pd0)]
ex('B2',pesos(day-pd0)); ex('B5',pesos(nets[0]-pd0))
ex('D1',pesos(day-pd0),f'{trips} viajes tomados',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km',f'${cn["rojo"]/ch["rojo"]["km"]:.2f}/km')
ex('D2',pesos(cn['verde']),pesos(ch['verde']['fare']),f'{ch["verde"]["km"]:.1f} km',f'{ch["verde"]["min"]} min','−'+pesos(ch['verde']['km']*cpk),f'${cn["verde"]/ch["verde"]["km"]:.2f}/km')
ex('D3',pesos(sum(x['gross'] for x in sh)),f"{sum(x['km'] for x in sh):.1f} km",'−'+pesos(sum(x['km'] for x in sh)*cpk),'−'+pesos(pd0),pesos(day-pd0))
car=F['car_cost']['weekly']; per_day=car/7
W=F['weeks']; w=W['2026-10-12']; wn=net(w['gross'],w['km'])-car; days=w['days']; fri=days['V']
assert sum(v['trips'] for v in days.values())==w['trips'] and abs(sum(v['gross'] for v in days.values())-w['gross'])<0.01 and abs(sum(v['km'] for v in days.values())-w['km'])<0.01, 'days must sum to the week'
best_day=max(days,key=lambda k: net(days[k]['gross'],days[k]['km'])); dname={'L':'lunes','M1':'martes','M2':'miércoles','J':'jueves','V':'viernes','S':'sábado','D':'domingo'}[best_day]
ex('E1',pesos(wn),f'{w["trips"]} viajes tomados')
ex('B4',pesos(net(days['D']['gross'],days['D']['km'])-pd0),days['D']['last_read']); S+=[pesos(wn),pesos(net(w['gross'],w['km']))]
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
dn,dp=dd,w2['by_app']['didi']; ex('F4',pesos(dn['gross']/dn['trips'])+'/viaje',pesos(dp['gross']/dp['trips'])+'/viaje',f"+{r(r(dn['gross']/dn['trips'])/r(dp['gross']/dp['trips'])*100-100)} %")  # Opus R15-04: the difference is computed from the figures printed beside it
w3=W['2026-09-28']; rd_=4; w3n=net(w3['gross'],w3['km'])-rd_*per_day
# Round 20 (Codex R20): the month is read at Hoy's moment (19 oct, 14:40) — today counts as Tu día at 14:40 ($548, 6 trips), never the 21:10 day
t1440=F['today_at_1440']['trips']; g1440_=sh[0]['gross']+ch['verde']['fare']; km1440_=sh[0]['km']+ch['verde']['km']
rows=[b1-per_day,wn,w2n,w3n]; octo=sum(rows)
ex('E3',pesos(octo),f'{t1440+w['trips']+w2['trips']+w3['trips']} viajes tomados'); S+=[pesos(octo)]
ex('E3d',f'{t1440} viajes',pesos(b1-per_day))
g_oct=g1440_+w['gross']+w2['gross']+w3['gross']; km_oct=km1440_+w['km']+w2['km']+w3['km']
ex('D3b',pesos(g_oct),'−'+pesos(km_oct*cpk),'−'+pesos(19*per_day),pesos(octo))  # Round 18 (Opus R17-R1): the month opens on D3's four lines
ex('E3d',pesos(octo),pesos(sum(r(x) for x in rows)),*[pesos(x) for x in rows])
dn2=[net(days[k]['gross'],days[k]['km'])-per_day for k in ['L','M1','M2','J','V','S','D']]; ex('E1b',*[pesos(x) for x in dn2],pesos(sum(r(x) for x in dn2)),pesos(wn))  # each day after its car share; the days add to the week (rounded once)
ex('D2',pesos(cn['verde']/ch['verde']['min']*60)+'/h')  # the offer detail carries $/h since Round 16
ex('A5',f'${cpk:.2f} por km',pesos(12*cpk)); ex('H5c',pesos(w2['by_app']['didi']['gross']),f"{w2['by_app']['didi']['trips']} viajes")
ex('E2',f'hace {(dt.date(2026,11,2)-dt.date(2026,10,5)).days//7} semanas'); ex('E6','19 – 25 oct · en curso','12 – 18 oct','5 – 11 oct','28 sep – 4 oct','14 – 20 sep')
for fr_ in ('H1','H2','H2b'): ex(fr_,f"${F['plan']['price_monthly']}")
ex('G5',f"{dt.date.fromisoformat(F['car_cost']['from']).day} sep")
cn7=net(F['chips']['rojo_negativo']['fare'],F['chips']['rojo_negativo']['km']); ex('C7',f'−${-cn7/F["chips"]["rojo_negativo"]["km"]:.2f}/km')
b6=F['b6_partial_support']; ex('B6',pesos(b6['net']-pd0),f'{b6["trips"]} viajes tomados',b6['hours']); ex('G9',f'${F["city_proposals"]["Monterrey"]["verde_min_per_km"]:.2f}/km')
alt=F['alt_scenarios']['E3c_sept_mixed']['weeks']; a1=net(alt['2026-09-21']['gross'],alt['2026-09-21']['km']); a2=net(alt['2026-09-28_partial']['gross'],alt['2026-09-28_partial']['km'])-alt['2026-09-28_partial']['days']*per_day
ex('E3c',pesos(a1+a2),f"{alt['2026-09-21']['trips']+alt['2026-09-28_partial']['trips']} viajes tomados",pesos(W['2026-09-14']['paid']),'no entra en el total')
tba=F['today_by_app_1440']; assert tba['uber']+tba['didi']==g1440_, 'the 14:40 split must add to the 14:40 gross'
uber_oct=tba['uber']+u['gross']+w2['by_app']['uber']['gross']+w3['by_app']['uber']['gross']; didi_oct=tba['didi']+dd['gross']+w2['by_app']['didi']['gross']+w3['by_app']['didi']['gross']
ex('E4',pesos(uber_oct),pesos(didi_oct)); f_=F['imss']['example_factor']; mn=F['imss']['min_monthly_2026']
ex('E5',pesos(uber_oct*f_),pesos(didi_oct*f_),pesos(mn-r(didi_oct*f_)),pesos(mn),str(f_)); S+=[pesos(uber_oct*f_),pesos(didi_oct*f_),pesos(mn-r(didi_oct*f_))]
imp=W['2026-09-14']; ex('E3b',pesos(imp['paid']),f'{imp["trips"]} viajes'); ex('H5',pesos(imp['paid']),str(imp['trips']),f'{imp["hours"]} h',f'{imp["commission_pct"]} %'); ex('H5d',pesos(imp['paid']),str(imp['trips']),f'{imp["hours"]} h'); S+=[pesos(imp['paid'])]
h5=F['h5b_partial_import']; hs=h5['statement']; wk=W[h5['uber_week']]
ex('H5b',pesos(hs['paid']),str(hs['trips']),str(h5['reader_trips_with_km']),f'{h5["km_known"]:.1f}','−'+pesos(h5['km_known']*cpk),f'faltan los km de {h5["trips_without_km"]} viajes',pesos(wk['by_app']['uber']['gross'])); S+=[pesos(hs['paid']),pesos(h5['km_known']*cpk)]
assert h5['reader_trips_with_km']+h5['trips_without_km']==hs['trips'] and h5['reader_trips_with_km']==wk['by_app']['uber']['trips'] and abs(h5['km_known']-wk['by_app']['uber']['km'])<0.01, 'H5b must bind to its week'
ex('G2',f'${F["floors"]["verde_min_per_km"]:.2f}/km',f'${F["floors"]["rojo_below_per_km"]:.2f}/km'); ex('G3',f'${F["costs"]["fuel_price_per_l"]:.2f}',f'{F["costs"]["km_per_l"]} km'); ex('G5',pesos(car),pesos(per_day))
pu=dt.date.fromisoformat(F['plan']['premium_until']); viewed=dt.date.fromisoformat(F['plan']['h2b_viewed_on']); ex('H2b',f'{(pu-viewed).days} días',f'{pu.day} de noviembre'); ex('H2',f'{pu.day} de noviembre'); ex('H2d',f'{pu.day} de noviembre')
pd_=dt.date.fromisoformat(F['plan']['purchase_date']); renew=dt.date(pd_.year+(pd_.month==12),(pd_.month%12)+1,pd_.day); grace=renew+dt.timedelta(days=F['plan']['grace_days']); ex('H2c',f'{grace.day} de {meses_full[grace.month]}'); banked=grace+dt.timedelta(days=(pu-pd_).days); ex('H2c',f'{banked.day} de {meses_full[banked.month]}')
# Round 20: at 14:40 the month's rows foot exactly (rounded weeks and ledger lines both add to the total), so no rounding note — see NX
ex('D2','Te dejó · aprox.')  # Round 21: a trip names what it leaves out (the car), where the number is; Round 24: as "sin el coche" right after it (below)
ex('B8','−'+pesos(per_day-cn['verde']))
ex('E1c','−'+pesos(per_day),'Sin viajes')
g1440=sh[0]['gross']+ch['verde']['fare']; km1440=sh[0]['km']+ch['verde']['km']
ex('D3a',pesos(g1440),f'{km1440:.1f} km','−'+pesos(km1440*cpk),'−'+pesos(per_day),pesos(b1-per_day))  # pre-test: Cómo se calculó at 14:40 adds up to Hoy's $548
ex('D1a',pesos(b1-per_day),'6 viajes tomados en 4 h 50 min')  # pre-test: Tu día at 14:40 — the same moment as Hoy; on shift 8:00–12:30 + 14:20–14:40
mt=F['today_morning_trips']
for fr in ('D1a','D1b'):
    ex(fr,*[f"${net(mt[k]['fare'],mt[k]['km'])/mt[k]['km']:.2f}/km" for k in ('11:20','10:41','10:02','9:24','8:15')])
    mm=F['today_morning_missed']; ex(fr,*[f"${net(mm[k]['fare'],mm[k]['km'])/mm[k]['km']:.2f}/km" for k in ('9:51','8:40')],'Ofertas de hoy · 10')  # Round 20 (Opus N6): every offer so far, the list scrolls to 08:15
assert sum(v['fare'] for k,v in mt.items() if not k.startswith('_'))==sh[0]['gross'], 'morning trips sum to shift 1'  # pre-test fix: the list runs on under the nav, as on D1
ex('D1b',pesos(b1-per_day),'sin semáforo en pantalla')  # pre-test fix: Tu día at 14:40 in B9's world (the 12:10 offer suppressed)
ex('C6','Semáforo apagado','Dejamos de leer tus ofertas','Abre Kompara al estacionarte')  # capture stopped, service alive: never the permission (nothing draws without it)
ex('C13','En línea')  # the permission lost: nothing draws over the host
ex('G4f','Semáforo preparado','Última lectura ayer 20:10'); ex('G4g','Semáforo funcionando','Por ahora no la leemos')
ex('G2d',f'Menor que tu meta (${F["floors"]["verde_min_per_km"]:.2f}/km)','Guardar')  # Round 21: tu mínimo is typed in place, the helper under its field — no dialog (run 5: the entry hid behind a dotted underline)
ex('G2e','¿Guardar el cambio?','Tu mínimo: $6.50/km','No guardar')  # Round 21: back with an unsaved change asks
ex('G4i','Guardado: tu mínimo ahora es $6.50/km',f'${F["floors"]["verde_min_per_km"]:.2f}/km · $6.50/km')  # Round 21: save-and-return — the opener already shows the new pair, the snackbar names it
ex('D2d',f'${cn["rojo"]/ch["rojo"]["km"]:.2f}/km',pesos(ch['rojo']['fare']),'sin semáforo en pantalla')  # pre-test fixes: the 12:10 detail in B9's world
ex('H6b','Se apagó el semáforo','Falta el permiso de Accesibilidad','ayer 20:31')  # pre-test fixes: the shade the next morning (task 7)
for fr in ('G4','G4b','G4c'): ex(fr,'Lectura comprobada · 14:32')  # the doors are at 14:40; the last offer read was 14:32
for fr in ('G2b','G2','G2c','G2d'): ex(fr,'Ejemplo')  # the preview is an example: G2b is reached from first-run and morning screens too
ex('D1','Ofertas de hoy · 13')  # Round 20: one heading for Tu día's list at any minute
ex('G4h','Semáforo apagado','Falta el permiso de Accesibilidad','Volver a dar el permiso'); ex('G1b','Apagado')  # Round 20 (Opus N5): Semáforo and Ajustes in the off state
# Round 21 (run 5 → Codex 301 + Opus 302): open periods say "Llevas", closed ones "Te quedaron"; no colour words; one cost basis said where the number is
for fr in ('B1','B5','B7','B8','B9','D1a','D1b','D1','D3','D3a'): ex(fr,'Llevas hoy')
ex('B6','Llevas hoy sin contar DiDi'); ex('E3d','Llevas en octubre'); ex('D3b','Llevas en octubre')
for fr in ('B1','D1a','D1'): ex(fr,'11:48 · No pudimos leer la tarifa')
for fr in ('E1','E7b'): ex(fr,'vie '+pesos(net(days[best_day]['gross'],days[best_day]['km'])-per_day))  # Round 21: the best day is named under its bar at 13sp, so the bold figure is the week alone
ex('D2b','Te habría dejado · aprox.','sin el coche'); ex('D2d','Te habría dejado · aprox.','sin el coche')  # Round 21: an offer not taken; Round 24: the basis after the number
ex('A3s','Ya lo activé','Regresar sin activarlo'); ex('A3n','Semáforo apagado · aún falta el permiso'); ex('B2','Que no se vuelva a apagar'); ex('B2r','Que no se vuelva a apagar')  # Round 23: the text button names what it prevents
ex('E8','Llevas esta semana'); ex('E3','Llevas en octubre'); ex('E1','Cuadrar con tu resumen de Uber','Por app','DiDi te dejó $1.21 más por km','Semana anterior')  # Round 22 (run 6, 6/6): the reconciliation leaves the hero area; the per-app answer is one row away
ub=w['by_app']['uber']; di=w['by_app']['didi']; assert ub['gross']+di['gross']==w['gross']
ex('E1d',f"Uber {pesos(ub['gross'])} · DiDi {pesos(di['gross'])}"); ex('E1e','Solo Uber, antes de gastos',pesos(ub['gross']),pesos(st['paid']),'+'+pesos(st['paid']-ub['gross']))
ex('F1','lo del coche cuesta igual en las dos apps','Manejaste cada app a distintas horas')
for fr in ('D1a','D1b','D1'): ex(fr,'te deja por km, sin el coche')  # Round 22: the list's column says what the figure is, in the trip's verb
for fr in ('G2b','G2','G2c'): ex(fr,'de $0.50 en $0.50','▲ desde','▼ abajo de')
ex('A3','EJEMPLO','Abrir ajustes'); ex('A2','Uber y DiDi'); ex('B3','Listo. Abre tu app de viajes')
ex('D1a','2 ofertas arriba de tu meta se fueron sin tomar')
stale_frames_r21=['Uber reporta $34 más','Leímos $5,028','neto por km','Te quedan hoy','Te quedan esta semana','Te quedan en octubre','ofertas verdes','verde desde','rojo abajo de','de $0.10 en $0.10','Faltó la tarifa','Tu resumen de Uber dice','A distintas horas en cada app']
# Round 23 (runs 5–7 → Codex 311 + Opus 312)
ex('D2','sin el coche','Llevas hoy, con el coche','Se resta por día ($214), no por viaje')  # Round 24 (run 8, 4/6 again): the basis sits beside the number it qualifies
ex('D2c','cumple tu mínimo de $6.00/km','sin el coche')
ex('B2','Ayer, lunes 19 oct, te quedaron'); ex('B2r','Semáforo preparado','permiso listo','Ayer, lunes 19 oct, te quedaron',pesos(day-pd0)); ex('B4','Ayer, domingo 18 oct, te quedaron')
ex('E1','Por app'); ex('E1f','Por app · 12 – 18 oct','$10.34/km','$9.13/km','DiDi te dejó $1.21 más por km','te dejó por km, sin el coche')  # Round 24c: the basis once, as the column note
ex('A4','1. En Aplicaciones'); ex('A4r','1. En Aplicaciones','Semáforo preparado','permiso listo')
stale_frames_r23=['Entre tu meta y tu mínimo','Que no vuelva a pasar','Ayer te quedaron','Ofertas sin semáforo','Llevas hoy, ya con lo del coche','antes de lo del coche']
NX_G4_ZERO=('G4','G4b','G4g','G4i')  # Round 24c (Opus 322): the count row is drawn only when 1 or more  # G4e and G4f (preparado) leave the row out: no offer read yet today
ex('G4c','No cupo el semáforo en 1 oferta hoy')
for fr in ('B1','B5','B6','B7','B8','B9','N1'): ex(fr,'te deja por km, sin el coche')  # Round 24b: Hoy's rates carry the basis too
NX_A4R={'A4r':['La primera oferta','aún falta el permiso']}
mt_=F['today_morning_trips']['11:20']; n1120=net(mt_['fare'],mt_['km'])
ex('D2e',pesos(n1120)+' sin el coche',f"${n1120/mt_['km']:.2f}/km",pesos(mt_['fare']),'−'+pesos(mt_['km']*cpk),f"{pesos(n1120/mt_['min']*60)}/h",'Llevas hoy, con el coche',pesos(b1-per_day))  # Round 24c (Codex 321): the last finished trip at 14:40 has its own detail
ex('B3n','Semáforo apagado','Activar el semáforo')  # Round 24c: the first day without the permission (A3's «Ahora no»)
for _g, _s in (('G1e', 'Preparado · permiso listo'), ('G1n', 'Apagado · falta el permiso')):  # Round 24e: the first day's Ajustes (B3's and B3n's ⚙)
    ex(_g, _s, 'Entrar con WhatsApp', f'${cpk:.2f} por km · típicos, sin confirmar', 'Sin agregar', f'${F["floors"]["verde_min_per_km"]:.2f}/km')
NX={'B1':['4 h 50 min','en turno'],'C6':['permiso'],'C13':['Semáforo apagado'],'G4e':['ayer'],
    'G4':['hace 2 min'],'G4b':['hace 2 min'],'G4c':['hace 2 min'],'G2b':['La oferta de las'],'G2':['La oferta de las'],'G2c':['La oferta de las'],'G2d':['La oferta de las','Escribe tu mínimo','Cancelar'],
    'H6b':['Tu resumen de hoy'],'B3':['Permiso listo'],'E3d':['Redondeado'],'D3b':['Redondeado'],'E3':['21:10'],'E3d':['Redondeado','21:10'],'D3b':['Redondeado','21:10'],'D2d':['pantalla: no cupo en la pantalla'],'D1':['Ofertas · 23','· 23']}  # per-frame strings that must NOT appear
for _g in ('G1e', 'G1n'): NX[_g] = ['Funcionando', pesos(car), 'confirmados', 'CDMX y Edomex', 'Prueba Premium']  # Round 24e: no fixture-driver setting leaks into the first day
ex('D2b',f'${cn["rojo"]/ch["rojo"]["km"]:.2f}/km',pesos(ch['rojo']['fare'])); ex('D2c',f'${cn["amarillo"]/ch["amarillo"]["km"]:.2f}/km',pesos(ch['amarillo']['fare']))  # each offer row opens its own detail
at=F['today_afternoon_trips']; ex('D1',*[f"${net(at[k]['fare'],at[k]['km'])/at[k]['km']:.2f}/km" for k in ('16:04','17:21')])  # Round 19: D1's newest rows carry their own rate

ex('E1d',pesos(w['gross']),'−'+pesos(w['km']*cpk),'−'+pesos(car),pesos(wn))  # Round 19: Cómo se calculó · semana — the week's four lines
ex('G2b','$8.00/km','$6.00/km')  # Round 19: Tu meta y tu mínimo at rest (the door's destination)
  # Round 18: a day without trips still carries the car's share  # Round 18 (Opus R17-R4): the negative day, drawn — the first trip alone has not covered the car's share
S.append('Cuadrar con tu resumen de Uber'); S+=['Llevas hoy · aprox.','Te dejó · aprox.','sin el coche','de $0.50 en $0.50','Guardado: tu mínimo ahora es $6.50/km']  # Round 21 vocabulary lives in the spec too
# Round 22: the discrepancy's amount moved from E1 to its sheet, E1e ('+$34', asserted with the Round 22 lines above)
ex('A1',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km'); ex('B3',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km'); ex('G2',f'${cn["verde"]/ch["verde"]["km"]:.2f}/km')
ex('G1',f'${cpk:.2f} por km',f'${F["floors"]["verde_min_per_km"]:.2f}/km',pesos(car))
def hm(hours): return f'{int(hours)} h {r((hours-int(hours))*60)} min' if r((hours-int(hours))*60) else f'{int(hours)} h'
def dur(a,b): (h1,m1),(h2,m2)=[map(int,x.split(':')) for x in (a,b)]; return (h2*60+m2-h1*60-m1)/60
ex('D1',hm(sum(dur(x['start'],x['end']) for x in sh))); ex('B5',hm(dur(sh[0]['start'],sh[0]['end']))); ex('H6',f'{trips} viajes',f'{w["trips"]} viajes')
pass
# free-window rule: a week leaves "this week + 3 previous" on the Monday 4 weeks after its start
e2_start=dt.date(2026,10,5); leaves=e2_start+dt.timedelta(weeks=4); meses=['','ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic']
cap_exp={'E2':[f'{leaves.day} {meses[leaves.month]}']}
stale_caps=['one number each','after the free access, never before','states that the payment counts','9 nov','Gris = no se pudo leer','Purchase pending / cancelled use the same card','56 + 32','240 × 56 —','the band alone, ▲']
fails=0
for fr,vals in E.items():
    if fr not in frames: fails+=1; print(f'NO FRAME {fr}'); continue
    for v in vals:
        if v not in frames[fr]: fails+=1; print(f'MISSING in {fr}: {v!r}')
NX.update(NX_A4R)
for fr in NX_G4_ZERO: NX.setdefault(fr,[]).append('No cupo el semáforo')
for fr,vals in NX.items():
    for v in vals:
        if v in frames.get(fr,''): fails+=1; print(f'NOT ALLOWED in {fr}: {v!r}')
for fr,vals in cap_exp.items():
    for v in vals:
        if v not in caps.get(fr,''): fails+=1; print(f'MISSING in caption {fr}: {v!r}')
if '$'+f'{cn["verde"]/ch["verde"]["km"]:.2f}' in frames.get('C5',''): fails+=1; print('C5 carries a rate')
for fr,cp in caps.items():
    for sp in stale_caps:
        if sp in cp: fails+=1; print(f'STALE caption {fr}: {sp!r}')
for v in S:
    if spec and v not in spec: fails+=1; print(f'MISSING in spec: {v!r}')
stale_frames=['$12,169','$12,170','158 viajes','$21,040','1,688.9 km','$4,799','$12,608','$8,432','$11,852','$7,926','$1,656','según el lector,','$…/km','desgaste ni coche','Se suma a septiembre','Ya tengo cuenta','ve si te deja','$762','$1,240','Tu mejor hora hoy','Tu mejor día fue','Tu lugar entre los de Uber','La mejor fue','la regla oficial se enlaza','(28 – 30 sep, antes de instalar','$640','$600 ','$4,190','$13,920','$7.20','$113.92','$85.92','$11,850','$7,925','$1,657','$27.07','$126/viaje','$1,148','Viajes 36','no se pudo leer','Premium se pausa','Guardar solo lo leído','no manejaste','Viaje confirmado','· estimado','(estimado)','Estimado de',' est.','17 – 23 ago','Tu lugar se calcula al cerrar','Seguir con Premium después']
stale_spec=['Estimado de viajes tomados','Estimado de 9 viajes','106 h en turno · hasta hoy','$4,190','$13,920','$113.92','$85.92','$11,850','$7,925','$1,657','$27.07','$1,148','17 – 23 ago','Tu lugar se calcula al cerrar','Premium se pausa','Guardar solo lo leído','9 nov, the first Monday']
alltext=' '.join(frames.values())
stale_frames+=stale_frames_r21+stale_frames_r23
for sp in stale_frames:
    n=alltext.count(sp)
    if n: fails+=1; print(f'STALE in frames: {sp!r} ×{n}')
for sp in stale_spec:
    n=spec.count(sp)
    if n: fails+=1; print(f'STALE in spec: {sp!r} ×{n}')

# Round 14 guards (Opus R14-04): the old vocabulary must not come back, and every verdict object must say the phrase of its own state
for sp in ['tu rojo','Tu rojo','mínimo $8','mínimo de $8','Verde desde $','Rojo debajo','debajo de tu mínimo','▬','carril']:
    n=alltext.count(sp)
    if n: fails+=1; print(f'STALE vocabulary in frames: {sp!r} ×{n}')
if 'class="chip ' in raw: fails+=1; print('STALE markup: an old .chip plate is still in index.html')
PHRASE={'v':'Arriba de tu meta','y':'Cumple tu mínimo','r':'Abajo de tu mínimo'}  # Round 23: the yellow phrase states the comparison that decides (Codex 311)
nreg=0; nrow=0
for fr,ch_ in chunks.items():
    for m in re.finditer(r'<div[^>]*class="regla (rg-g|v|y|r)\b[^"]*"',ch_):
        st=m.group(1); seg=ch_[m.end():m.end()+1400]; lab=re.search(r'aria-label="([^"]*)"',seg); nreg+=1
        if not lab: fails+=1; print(f'REGLA {fr} ({st}): no accessible name on the band'); continue
        want=PHRASE.get(st)
        if want and not lab.group(1).startswith(want): fails+=1; print(f'REGLA {fr} ({st}): aria-label {lab.group(1)[:40]!r} does not start with {want!r}')
        if st=='rg-g' and any(p_ in lab.group(1) for p_ in PHRASE.values()): fails+=1; print(f'REGLA {fr} (gris) speaks a verdict')
    for m in re.finditer(r'<div class="of[^"]*"><span class="mark (v|a|r|g)\b[^"]*"[^>]*>.*?<em>(.*?)</em>',ch_,re.S):
        mk,em=m.group(1),strip(m.group(2))
        ok={'v':'Arriba de tu meta' in em,'a':'Cumple tu mínimo' in em,'r':('Abajo de tu mínimo' in em) or ('sin semáforo' in em),'g':any(x in em for x in ('No pudimos leer','Sin lectura','falta','sin confirmar'))}[mk]
        nrow+=1
        if not ok: fails+=1; print(f'ROW {fr}: mark {mk} says {em!r}')
import datetime as dt
# bar charts: E1/E7b day bars are each day after its car share (Round 17) relative to the best day; E8's today bar is relative to that same best day
order=['L','M1','M2','J','V','S','D']; dn_=[net(days[k]['gross'],days[k]['km'])-per_day for k in order]; mx=max(dn_); exp_bars=[round(x/mx*100) for x in dn_]  # Round 17: day bars after the car share
def bars(fr):
    m=re.search(r'<div class="spark[^"]*">(.*?)</div>',chunks.get(fr,''),re.S)
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
if '--contract' in sys.argv:
    # regenerate _frame-contract.md from the same expectations this checker asserts (one line per frame, in index order)
    out=['# Frame contract — strings check-fixture.py asserts INSIDE each frame\'s screen text (captions excluded). Keep every one verbatim (same characters, including the − minus sign and plain spaces).','# Generated by `python3 check-fixture.py --contract`; do not edit by hand.','']
    for fr in frames: out.append(f'- **{fr}**: '+(' · '.join(f'`{v}`' for v in E[fr]) if E.get(fr) else '(no asserted figures)'))
    out+=['',f'Bar charts (height:% values inside `<div class="spark…">`, in DOM order): E1 and E7b = {" ".join(map(str,exp_bars))} · E8 first bar 100 · E3 = {" ".join(map(str,e3exp))} · E3c = {" ".join(map(str,e3cexp))}.',
          'Caption rule: the E2 caption must contain "'+cap_exp['E2'][0]+'". C5 must not carry $9.49.','','Spec strings that must stay in docs/ux-elevation-spec.md: '+' · '.join(f'`{v}`' for v in S)]
    open(os.path.join(H,'_frame-contract.md'),'w').write('\n'.join(out)+'\n'); print('wrote _frame-contract.md')
print(f'checked {nE} figures in {len(E)} frames, {nreg} chips and {nrow} offer rows against their state phrase, {len(S)} spec strings, 1 date rule, {len(stale_frames)+len(stale_spec)+9} stale patterns (9 of them vocabulary guards), 1 markup guard, {len(stale_caps)} caption lints, 5 bar charts → {"OK" if not fails else str(fails)+" problems"}')
sys.exit(1 if fails else 0)
