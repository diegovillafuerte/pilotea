# [NOMBRE POR DEFINIR] — Documento de Contexto del Proyecto

> **Anteriormente llamado "Kompara" (antes "Pilotea", antes "Ruleteo")**
> Última actualización: 2 de abril de 2026
> Status: Pre-desarrollo. Diseño completo, backend diseñado, ningún código en producción.

---

## 1. QUÉ ES ESTE PROYECTO

Una app web (PWA) para conductores de plataformas de ride-hailing en México y LATAM que les permite comparar sus ganancias entre Uber, DiDi e InDrive y recibir recomendaciones accionables para ganar más.

El conductor sube un screenshot o PDF de sus ganancias semanales. La app usa IA (Claude API con vision) para extraer los datos automáticamente, los compara contra miles de otros conductores en la misma ciudad, y le muestra en qué percentil está en métricas como $/viaje, $/km, $/hora, viajes/hora y comisión. Si usa múltiples plataformas, puede ver cuál le paga mejor.

No existe un producto comparable en LATAM. El competidor más cercano es Gridwise en Estados Unidos.

---

## 2. ORIGEN Y EVOLUCIÓN DEL CONCEPTO

El proyecto nació el 28 de febrero de 2026 en una sesión de brainstorming.

**Nombre original: Ruleteo.** "Ruletear" es el término que usan los conductores mexicanos para describir el acto de andar buscando pasajeros. Se construyó un primer prototipo interactivo (React/JSX) con onboarding, upload de datos y dashboard con percentiles.

**Segundo nombre: Pilotea.** Se renombró para facilitar expansión a LATAM. "Pilotea tus ganancias" como tagline. Se registró el dominio pilotea.app. Se construyeron múltiples iteraciones del prototipo (v1 a v5), afinando métricas, flujo de upload, y comparación cross-platform. Posteriormente se renombró a **Kompara** tras descubrir un conflicto de marca.

**Conflicto de marca descubierto (abril 2026):** Existe una empresa mexicana llamada Pilotea® (pilotea.mx) que hace arrendamiento de autos para conductores de Uber y DiDi. Operan en Puebla, Monterrey, Guadalajara y León. Usan el símbolo ® y atienden a la misma audiencia (conductores de plataformas). Esto crea un conflicto de marca que hace inviable usar el nombre "Pilotea".

**Se necesita un nuevo nombre.** Se realizó una investigación exhaustiva de naming con 18 candidatos evaluados por disponibilidad de trademark (IMPI), dominios, y resonancia cultural. Los 3 finalistas son:
- **Jale** — "ir al jale" = ir al trabajo. Culturalmente perfecto para conductores mexicanos.
- **Mero** — "el mero mero" = el jefe, el mejor. Posiciona al conductor como top.
- **Rifa** — "se la rifó" = lo hizo increíble. Energía de hustler. Riesgo: también significa "sorteo/lotería".

**La decisión de nombre está pendiente.** El siguiente paso es verificar formalmente en IMPI (Marcanet) y confirmar disponibilidad de dominios.

---

## 3. QUIÉN LO ESTÁ CONSTRUYENDO

Una sola persona, como side project en tiempo libre y fines de semana. Usa su laptop personal.

**Perfil del fundador:** Regional GM en DiDi, manejando $7B de revenue y ~400 personas. Conocimiento profundo del mercado de ride-hailing en LATAM. No es desarrollador de tiempo completo — entiende arquitectura de software pero construye con herramientas de IA (Claude Code para coding, Claude Chat para estrategia).

### Conflicto de interés con DiDi

Este es el riesgo no técnico más importante del proyecto. El fundador trabaja como ejecutivo senior en DiDi, y este producto procesa y muestra datos de DiDi (entre otras plataformas).

**Riesgos específicos identificados:**
- El contrato de DiDi probablemente incluye cláusulas de IP assignment, non-compete, confidentiality, y restricción de actividades externas.
- Aunque el producto no "compite" con DiDi directamente, sí muestra métricas comparativas donde DiDi podría salir desfavorable vs Uber o InDrive.
- El conocimiento de la estructura de comisiones y datos de DiDi podría interpretarse como uso de información interna.
- El riesgo reputacional si se descubre es alto: un GM construyendo herramientas para que conductores comparen plataformas.

**Estrategia acordada: Construir ahora, lanzar después.**
- Desarrollar todo el producto como proyecto técnico personal de aprendizaje.
- No lanzar públicamente hasta tener claridad legal revisando el contrato de DiDi con un abogado laboral (~$5-10K MXN).
- Opcionalmente, tener un cofundador que sea la cara pública.
- Usar laptop personal, tiempo personal, sin acceso a recursos de DiDi.

---

## 4. MERCADO Y AUDIENCIA

### Audiencia primaria
Conductores de plataformas de ride-hailing en México que trabajan en Uber, DiDi y/o InDrive.

**Perfil típico:**
- Hombre, 25-45 años
- Smartphone Android, datos prepago limitados
- Trabaja 30-50 horas/semana en 1-3 plataformas
- Ingresos semanales de $2,000-$6,000 MXN netos
- Obligaciones fiscales bajo RESICO (Régimen Simplificado de Confianza)
- Comunica por WhatsApp, consume contenido en TikTok y YouTube
- Nivel técnico bajo-medio — usa apps pero no configura cosas complejas

### TAM (Total Addressable Market)
- ~500,000 conductores activos en México (Uber + DiDi + InDrive)
- ~2,500,000 conductores en LATAM
- 40 zonas metropolitanas en México como target inicial

### Ciudades prioritarias para lanzamiento
Valle de México (CDMX + Edomex), Monterrey, Guadalajara, Puebla-Tlaxcala, Toluca, Tijuana, León, Querétaro, Mérida, Cancún — y 30 más que ya están mapeadas en el prototipo.

---

## 5. PROPUESTA DE VALOR

### Para conductores (B2C)
1. **Comparación de eficiencia entre plataformas:** No comparamos ganancias totales (no son comparables porque dependen de horas trabajadas). Comparamos métricas de eficiencia: $/viaje, $/km, $/hora, viajes/hora, comisión %. Si un conductor usa Uber y DiDi, ve cuál le paga mejor por hora invertida.

2. **Benchmarking contra otros conductores:** "Le ganas al 72% de los conductores en tu ciudad." Usa un sistema de percentiles basado en datos agregados de la población de conductores. Visualización con 20 iconos (tipo 20 personas en una fila).

3. **Recomendaciones accionables:** No solo gráficas — recomendaciones como "DiDi te paga 18% más por hora, priorízalo en horas pico" o "Tu comisión de Uber esta semana fue 38% — arriba del promedio de 31%."

4. **Reporte fiscal "Para tu contador":** PDF descargable con desglose de ganancias por plataforma, comisiones pagadas, viajes totales. Compatible con declaraciones RESICO.

5. **"Tu Mes en [App]" — resumen compartible:** Tarjeta tipo Spotify Wrapped con ganancias del mes, percentil, mejor día, mejor plataforma. Diseñada para compartir en WhatsApp → marketing viral gratuito.

### Para el negocio (B2B — segundo revenue stream a escala)
Con 50K+ conductores, el dataset agregado y anonimizado de ride-hailing en LATAM es valioso para:
- Gobiernos municipales (regulación de plataformas)
- Periodistas e investigadores
- Las propias plataformas (DiDi comprando data de cuánto paga Uber)
- Inversionistas en movilidad

Esto es exactamente lo que hace Gridwise — su reporte anual cubre 720M viajes y $8.3B en ganancias.

---

## 6. MONETIZACIÓN

### Modelo Freemium con 3 tiers

| Tier | Precio | Qué incluye |
|------|--------|-------------|
| **Free** | $0 | Dashboard individual, 1 plataforma, percentiles básicos, reporte fiscal mensual |
| **Pro** | $59 MXN/mes | Todo Free + recomendaciones personalizadas, tendencias semana a semana, historial completo |
| **Multi** | $99 MXN/mes | Todo Pro + comparación cross-platform (Uber vs DiDi vs InDrive), análisis de comisiones |

**Principio clave del paywall:** La comparación cross-platform SIEMPRE está detrás del paywall (nunca fue gratis, así que no hay pérdida percibida). El dashboard individual siempre es gratis. Nunca quitar un feature que ya se dio gratis (aprendizaje de Gridwise que enfureció a sus usuarios en 2024).

---

## 7. DATOS: QUÉ SE PUEDE EXTRAER DE CADA PLATAFORMA

Este es uno de los hallazgos más importantes del proyecto. Cada plataforma provee datos diferentes, y ninguna da todo:

### Uber — PDF semanal (FUENTE MÁS RICA)
Se descarga de drivers.uber.com. Contiene:
- ✅ Ganancias netas y brutas
- ✅ Total de viajes
- ✅ Horas en línea
- ✅ $/viaje, $/hora, viajes/hora (calculables)
- ✅ Comisión (monto y porcentaje)
- ✅ Impuestos, incentivos, propinas, surge, tiempo de espera
- ✅ Días activos, día pico
- ❌ **Kilómetros recorridos — Uber NUNCA reporta km**

### Uber — Screenshot "Desglose de la tarifa" (FUENTE LIMITADA)
Solo muestra el pie chart de la distribución de ganancias:
- ✅ Ganancias, comisión %, impuestos, incentivos
- ❌ No viajes, no km, no horas
- Completitud de datos: ~40%

### DiDi — 2 screenshots (ganancias + tablero)
- ✅ Ganancias netas y brutas
- ✅ Total de viajes
- ✅ $/km (DiDi es la ÚNICA que reporta $/km directamente)
- ✅ $/viaje, $/hora
- ✅ Split efectivo/tarjeta
- ✅ Impuestos, recompensas
- ❌ **Comisión explícita — DiDi NO desglosa su comisión**
- Horas: estimable a partir de $/hora

### InDrive — 1 screenshot
- ✅ Ganancias netas
- ✅ Total de solicitudes (viajes)
- ✅ Kilometraje total y $/km
- ✅ Tarifas brutas, pago por servicio (comisión calculable)
- ❌ **Horas — InDrive NO reporta horas en línea**
- Sin horas → no se puede calcular $/hora ni viajes/hora

### Métricas de comparación definidas
Solo se comparan métricas de eficiencia (no totales):
1. $/viaje (earnings per trip)
2. $/km (earnings per km)
3. $/hora (earnings per hour)
4. Viajes/hora (trips per hour)
5. Comisión % (platform commission)

Cada métrica puede ser null si la plataforma no la reporta. El sistema maneja nulls gracefully con mensajes como "InDrive no reporta horas" y un score de data_completeness por upload.

---

## 8. ARQUITECTURA TÉCNICA

### Stack
| Componente | Tecnología | Razón |
|-----------|-----------|-------|
| Frontend | Next.js 14 (App Router) | PWA-capable, SSR, React |
| Base de datos | Supabase (PostgreSQL) | Auth, Storage, RLS, funciones SQL |
| Auth | Twilio SMS vía Supabase Auth | Conductores no usan email |
| OCR / IA | Claude Sonnet API (vision) | Entiende contexto, no solo OCR |
| Hosting | Vercel | Deploy automático, edge |
| Dominio | [pendiente — era pilotea.app, ahora kompara.app] | — |

### Flujo de datos
```
Conductor sube archivo (screenshot/PDF)
    → Supabase Storage (bucket: driver-uploads)
    → API route: POST /api/parse-upload
        → Detecta plataforma + tipo de archivo
        → Envía a Claude Vision con prompt específico por plataforma
        → Claude extrae JSON estructurado
        → Validación + cálculos derivados
        → Guarda en weekly_data (upsert por driver_id/platform/week)
        → Calcula percentiles vs población de la ciudad
    → Retorna: métricas + percentiles + data_completeness
```

### Base de datos — 5 tablas

**drivers:** phone, name, city, platforms[], tier (free/pro/multi), auth_user_id, streak_weeks, last_upload_at

**uploads:** driver_id, platform, upload_type (pdf/screenshot), file_url, status (pending/processing/parsed/failed), error_message, parsed_data (JSONB)

**weekly_data:** Tabla principal. driver_id, platform, week_start, net_earnings, gross_earnings, total_trips, earnings_per_trip, earnings_per_km, earnings_per_hour, trips_per_hour, total_km, hours_online, platform_commission, platform_commission_pct, taxes, incentives, tips, surge_earnings, wait_time_earnings, active_days, peak_day_earnings, peak_day_name, cash_amount, card_amount (DiDi), rewards, raw_extraction (JSONB), data_completeness (0-1)

**population_stats:** city, platform, metric_name, period, sample_size, p10, p25, p50, p75, p90, mean. Seeded con datos sintéticos para CDMX, Monterrey, Guadalajara, y promedios nacionales.

**subscriptions:** driver_id, tier, status, amount, started_at, expires_at, payment_reference

Todo con Row Level Security (RLS) — conductores solo ven sus propios datos.

**Función SQL: get_percentile(city, metric, value)** — calcula el percentil exacto con interpolación lineal entre percentiles conocidos. Hace fallback a datos nacionales si la muestra de la ciudad es <20.

### Parsers (4 archivos TypeScript)

Cada parser envía la imagen/PDF a Claude con un prompt altamente específico que describe el layout exacto de esa pantalla, le pide extraer un JSON con campos definidos, y luego valida, calcula métricas derivadas, y retorna un score de completitud.

1. **uber-pdf.ts** — Parser del PDF semanal de Uber. Fuente más rica. Extrae ~20 campos. data_completeness: ~0.95
2. **uber-screenshot.ts** — Parser del "Desglose de la tarifa". Limitado. data_completeness: ~0.40
3. **didi-screenshot.ts** — Procesa 2 imágenes (ganancias + tablero). Estima horas. data_completeness: ~0.85
4. **indrive-screenshot.ts** — 1 imagen. Sin horas. Calcula comisión. data_completeness: ~0.70

### API Route
**POST /api/parse-upload** — Recibe FormData (driver_id, platform, upload_type, files[]), sube a storage, rutea al parser correcto, guarda datos, calcula percentiles, retorna resultado o error en español.

---

## 9. PROTOTIPO INTERACTIVO

Se construyó un prototipo funcional en React (JSX) que simula la experiencia completa del usuario. Pasó por 5+ iteraciones refinando:

### Flujo de pantallas
1. **Splash** — Logo + "Kompara tus ganancias" (2.2 seg)
2. **Onboarding** — Nombre, ciudad (40 ciudades con buscador), celular + badge de privacidad "Tus datos, tu control"
3. **Verificación SMS** — 6 dígitos con auto-advance, paste, backspace
4. **Selección de plataformas** — Uber 🖤, DiDi 🧡, InDrive 💚 (multi-selección)
5. **Upload** — Tabs por plataforma, instrucciones paso a paso, mockups visuales de cómo se ve cada screenshot, barra de completitud, tiered upload para Uber (PDF recomendado vs screenshot limitado)
6. **Processing** — 4 steps animados: "Leyendo archivos", "Extrayendo datos con IA", "Calculando métricas", "Comparando con 12,847 conductores"
7. **Dashboard** — Ganancias totales, 6 métricas, percentil principal, badge de racha, recomendaciones accionables, percentil por métrica (20 iconos de personas), cards de fiscal y review mensual
8. **Comparar** (paywall Multi $99) — CompareBar por métrica, desglose de comisiones
9. **Fiscal** — "Para tu contador", selector de mes, desglose por plataforma, datos para declaración, botón "Descargar PDF"
10. **Tu Mes** — Shareable card con ganancias, percentil, mejor día, mejor app, racha. Botón "Compartir en WhatsApp"
11. **Tips** — Recomendaciones personalizadas + genéricas + upsell a Pro

### Navegación inferior (5 tabs)
📊 Dashboard | 🆚 Comparar | 📸 Subir | 🧾 Fiscal | 💡 Tips

### Archivos entregados
- `/mnt/user-data/outputs/kompara-mvp.jsx` — Prototipo v1
- `/mnt/user-data/outputs/kompara-mvp-v2.jsx` — Prototipo v2 con learnings de Gridwise integrados

---

## 10. COMPETITIVE LANDSCAPE

### No hay competidor directo en LATAM
Ninguna app en México o LATAM hace comparación de ganancias entre plataformas de ride-hailing para conductores.

### Gridwise (EE.UU.) — el comp más cercano
- 1M+ downloads, ~650K usuarios, ~150K activos
- Revenue: $7.4M USD (dic 2023)
- Equipo: recortado de 59 a 19 personas en 2024
- Modelo: freemium con Plus a $9.99 USD/mes ($95.99/año)
- Funciona con OAuth sync (conecta cuenta de Uber/Lyft directamente)
- Cubre rideshare + delivery (DoorDash, Instacart, Amazon Flex, etc.)
- B2B: Gridwise Analytics y Gridwise Ads como segundo revenue stream
- Ratings: 4.9★ iOS / 4.6★ Android

**Problema principal de Gridwise:** El sync automático se rompe constantemente. Uber/Lyft cambian APIs, revocan tokens. Los conductores se quejan de que las ganancias no sincronizan. Ese es su feature premium y es frágil.

**Nuestro approach de OCR es una ventaja**, no una limitación. Un screenshot siempre funciona. No dependemos de la cooperación de las plataformas. El messaging es "Tus datos, tu control — sin vincular cuentas, sin riesgo."

### GigU / StopClub (Brasil)
- Lee la pantalla del conductor en tiempo real (overlay sobre Uber)
- Tiene "cherry picker" que auto-rechaza viajes poco rentables
- Uber Brasil demandó a StopClub (julio 2023) — y PERDIÓ. El tribunal rechazó la medida cautelar de Uber.
- Ahora opera en EE.UU. como GigU con 600K downloads, 181K activos

**StopClub vs Uber Brasil es el precedente legal clave para este proyecto.** Y nuestro approach es menos agresivo que el de StopClub (ellos leen la pantalla en tiempo real; nosotros procesamos screenshots subidos voluntariamente).

### Otros (EE.UU., no presentes en LATAM)
- **Para** — transparencia de pago en tiempo real
- **Solo** — earnings y mileage tracking
- **Stride** — tracking de deducciones fiscales
- **Mystro** — auto-aceptar/rechazar viajes

Ninguno opera en LATAM. Todos hacen MÁS que nosotros (acceso directo a cuentas) y operan sin problemas legales.

### Learnings de Gridwise integrados en nuestro MVP

Del análisis de Gridwise extrajimos 7 cambios concretos ya implementados en el prototipo v2:

1. **"Tus datos, tu control"** — Badge de privacidad en onboarding y processing. Conductores de Gridwise se quejan de que vende sus datos a aseguradoras. Nosotros nos diferenciamos siendo transparentes.

2. **Recomendaciones accionables** — No solo gráficas. "DiDi te paga 19% más por hora — priorízalo." Conductores de Gridwise dicen que ven datos pero no saben qué hacer con ellos.

3. **Streak counter** — Gamification sutil. ⚡ 4 semanas, 🔥 8+ semanas. Retención.

4. **Notificación semanal (lunes)** — "¿Cómo te fue? Sube tus datos." La naturaleza de la app es semanal — sin push, el conductor se olvida.

5. **Reporte fiscal** — El killer feature de Gridwise no son los insights sino el tax prep. En México, los conductores bajo RESICO necesitan reportar ganancias. PDF listo para el contador.

6. **"Tu Mes en [App]"** — Shareable card tipo Spotify Wrapped. Marketing viral gratuito vía WhatsApp. Cada imagen compartida tiene branding.

7. **5-tab nav** — Dashboard, Comparar, Subir, Fiscal, Tips. Lo que NO copiamos: mileage tracking, expense tracking, eventos/aeropuertos (no relevante para MX).

---

## 11. ANÁLISIS LEGAL

### ¿Pueden Uber/DiDi demandar?
**Riesgo bajo y manejable.** No es ilegal.

**Precedente clave:** StopClub/GigU vs Uber Brasil (julio 2023). Uber demandó alegando extracción ilegal de datos, violación de copyright, competencia desleal. El tribunal rechazó la demanda. StopClub ganó y sigue operando.

**Nuestro riesgo es MENOR que StopClub porque:**
- StopClub lee la pantalla en tiempo real mientras el conductor usa Uber, usa overlay, y auto-rechaza viajes
- Nosotros: el conductor sube voluntariamente su propio screenshot/PDF. Cero interacción con las apps. Solo data histórica por OCR.

**Apps similares operando sin problemas legales:** Gridwise (1M+ downloads desde 2017, sync directo de cuentas), Para, Solo — todos hacen más que nosotros y operan libremente.

### Riesgos legales reales (no de las plataformas)

**1. Protección de datos (LFPDPPP 2025) — riesgo MEDIO-ALTO**
- Aviso de privacidad claro antes de recopilar datos
- Consentimiento explícito para procesar screenshots (contienen datos financieros)
- Derechos ARCO (Acceso, Rectificación, Cancelación, Oposición)
- No almacenar datos sensibles más tiempo del necesario
- Designar oficial de protección de datos
- Costo: ~$5-10K MXN con abogado de datos

**2. Términos de servicio de plataformas — riesgo BAJO**
- Los TOS de Uber/DiDi prohíben compartir datos con terceros
- PERO nosotros no accedemos a APIs ni apps. El conductor comparte SUS datos voluntariamente
- Enforcement contra conductores individuales es imposible a escala

**3. Propiedad intelectual — riesgo BAJO**
- No usar logos de Uber/DiDi/InDrive (usamos emojis: 🖤🧡💚)
- No copiar diseños de interfaces
- Datos numéricos (ganancias, viajes, km) no son protegibles por copyright

**4. Regulación fintech — riesgo BAJO**
- Solo aplica si procesamos pagos. Solución: usar Stripe México o Mercado Pago como procesador.

**5. Entidad legal — REQUERIDO**
- Necesario para operar formalmente y facturar suscripciones
- Empezar como Persona Física con Actividad Empresarial (PFAE)
- Escalar a SAPI/SAS cuando el revenue lo justifique

### Checklist pre-lanzamiento
- [ ] Aviso de privacidad (LFPDPPP 2025)
- [ ] Términos y condiciones
- [ ] Registro de marca en IMPI (~$2,500 MXN por clase)
- [ ] Entidad legal (PFAE mínimo)
- [ ] Procesador de pagos (Stripe MX o Mercado Pago)
- [ ] Registro ante SAT para facturación de suscripciones
- [ ] Consulta con abogado de datos ($5-10K MXN)
- [ ] Revisión del contrato laboral de DiDi con abogado laboral ($5-10K MXN)

**Inversión legal mínima pre-lanzamiento: ~$15-20K MXN ($880-$1,175 USD)**

---

## 12. MODELO FINANCIERO

### Supuestos base
- 4 uploads por conductor por mes
- Costo Claude API: ~$0.005 USD por upload (~$0.085 MXN)
- Tipo de cambio: 1 USD = 17 MXN
- Conversión Pro: 6-13% según escala
- Conversión Multi: 2-12% según escala

### Revenue y profit por escala de usuarios

| Usuarios | Rev mensual (MXN) | Rev anual (MXN) | Rev anual (USD) | Costo mensual (MXN) | Profit mensual (MXN) | Profit anual (USD) | Margen | FTEs |
|----------|-------------------|-----------------|-----------------|--------------------|--------------------|-------------------|--------|------|
| 1,000 | $5,520 | $66K | $3.9K | $3,389 | $2,131 | $1.5K | 38.6% | 0 |
| 10,000 | $86,800 | $1.04M | $61K | $18,990 | $67,810 | $47.9K | 78.1% | 0 |
| 50,000 | $612,500 | $7.35M | $432K | $116,205 | $496,295 | $350K | 81.0% | 1 |
| 100,000 | $1.53M | $18.4M | $1.08M | $294,452 | $1.24M | $874K | 80.8% | 2 |
| 250,000 | $4.35M | $52.2M | $3.07M | $701,700 | $3.65M | $2.58M | 83.9% | 4 |
| 500,000 | $9.69M | $116M | $6.84M | $1.43M | $8.26M | $5.83M | 85.3% | 8 |
| 1,000,000 | $22.55M | $271M | $15.9M | $2.90M | $19.65M | $13.9M | 87.1% | 15 |

**Comparación con Gridwise:** A 500K usuarios, nuestro revenue ($6.8M USD) es comparable al de Gridwise ($7.4M USD), pero con una fracción del equipo (8 vs 19 personas) y mejor margen (85% vs estimado 50-60%). Los márgenes son tipo SaaS puro porque el costo variable principal (Claude API) es centavos por usuario.

**B2B revenue** se activa a partir de 50K usuarios ($50K MXN/mes) y crece significativamente con escala (hasta $3M MXN/mes a 1M usuarios).

**El modelo completo en Excel** está en `/mnt/user-data/outputs/kompara-revenue-model.xlsx` con todos los inputs modificables (celdas azules), incluyendo hoja de sensibilidad.

---

## 13. COSTOS POR FASE

### Beta (10-100 conductores): ~$120-320 MXN/mes
- Supabase: free tier
- Claude API: $1-3 USD/mes
- Vercel: free tier
- Twilio: $5-15 USD/mes

### Early growth (1K-10K): ~$8K-19K MXN/mes
- Break-even con 5% conversión Pro (50 subs × $59 = $2,950 MXN)

### Scale (10K-50K): ~$63K-163K MXN/mes
- Revenue estimado a 7% Pro + 3% Multi: ~$355K MXN/mes
- Margen: 50-70%

### LATAM (50K-500K+): ~$450K-1.24M MXN/mes
- Revenue a 8% Pro + 4% Multi: ~$4.34M MXN/mes
- Margen: 65-80%

---

## 14. APPROACH DE DESARROLLO

### Herramientas
- **Claude Code** (terminal) — para todo el coding. Trabaja directamente en el directorio del proyecto, puede crear/editar archivos, correr dev server, ver errores, testear, commitear a git.
- **Claude Chat** (este chat) — para estrategia, producto, análisis, decisiones de negocio, investigación competitiva, análisis legal.

### Archivos ya entregados

**Backend:**
- `kompara-backend/schema.sql` — Schema completo de Supabase con 5 tablas + RLS + funciones
- `kompara-backend/lib/parsers/uber-pdf.ts` — Parser OCR para PDF semanal de Uber
- `kompara-backend/lib/parsers/uber-screenshot.ts` — Parser OCR para screenshot de Uber
- `kompara-backend/lib/parsers/didi-screenshot.ts` — Parser OCR para screenshots de DiDi
- `kompara-backend/lib/parsers/indrive-screenshot.ts` — Parser OCR para screenshot de InDrive
- `kompara-backend/app/api/parse-upload/route.ts` — API route principal
- `kompara-backend/kompara-architecture.md` — Documentación de arquitectura completa
- `kompara-backend/README.md` — Guía de setup

**Análisis:**
- `kompara-costos-legal.md` — Análisis completo de costos por escala + riesgo legal
- `gridwise-analysis-kompara-learnings.md` — Análisis de Gridwise con learnings para MVP
- `kompara-revenue-model.xlsx` — Modelo financiero en Excel con 7 escenarios

**Prototipos:**
- `kompara-mvp.jsx` — Prototipo interactivo v1
- `kompara-mvp-v2.jsx` — Prototipo v2 con learnings de Gridwise

### Siguiente paso técnico
1. Decidir nombre definitivo y registrar dominio
2. Instalar Claude Code
3. `npx create-next-app [nombre]`
4. Configurar Supabase (pegar schema.sql)
5. Copiar parsers y API route al proyecto
6. Probar primer upload real: un PDF de Uber procesado por el parser y guardado en la base de datos

---

## 15. NAMING — INVESTIGACIÓN Y CANDIDATOS

### ¿Por qué se cambió de "Pilotea"?
Existe Pilotea® (pilotea.mx), empresa de arrendamiento de autos para conductores de Uber/DiDi en Puebla, Monterrey, Guadalajara y León. Misma audiencia, mismo nombre. Conflicto de marca inviable. El producto ahora se llama **Kompara**.

### Patrones de naming identificados
- Apps gig economy exitosas: 74% usan 2 sílabas (Gridwise, Para, Solo, GigU)
- Apps mexicanas exitosas: 63% son 1-2 sílabas, 42% terminan en vocal (Bitso, Rappi, Kavak, Stori)
- Patrón K-por-C en fintech mexicano: Kueski, Konfío, Kavak, Klar

### Top 3 candidatos finales

**1. Jale ★★★★★** — "Ir al jale" = ir al trabajo. Culturalmente perfecto. 4 letras, 2 sílabas. Sin conflictos de trademark encontrados en tech/fintech. Existe una app de contratistas llamada "Jale" pero en mercado diferente.

**2. Mero ★★★★☆** — "El mero mero" = el jefe/el mejor. Profesional, corto, moderno. Sin conflictos en tech/fintech MX/LATAM. .com probablemente tomado pero .app potencialmente disponible.

**3. Rifa ★★★★☆** — "Se la rifó" = lo hizo increíble. Energía de hustler. Riesgo: también significa "sorteo/lotería" y existen apps de rifas digitales.

### Otros candidatos evaluados
- **Ganio** (de "ganar") — inventado, fácil de registrar, .app disponible
- **Viraz** (de "viraje") — todos los dominios potencialmente disponibles
- **Cheko** (de "checar" + resonancia con Checo Pérez) — K-swap, fuerte
- **Jalón** ("dar un jalón" = dar un ride) — sin conflictos
- **Volante** (metáfora de control) — 3 sílabas pero poderoso
- **Feria** (slang para dinero) — .app posiblemente tomado
- **Gana** (imperativo de ganar) — muy genérico, difícil de proteger

### Nombres descartados (ya tomados)
- Neta — startup mexicana con $4.9M de funding
- Timón — fintech de viajes activa
- Klaro — demasiado similar a Klar (fintech MX grande)
- Varo — neobank estadounidense con $992M de funding
- Alto — empresa de ride-hailing partner de Uber
- Rumbo — todos los dominios tomados, trademark registrado

### Pendiente
- Verificación formal en IMPI (Marcanet) para los top 3
- WHOIS lookup en tiempo real para dominios prioritarios
- Test informal con conductores reales (WhatsApp groups, TikTok polls)

---

## 16. RIESGOS Y MITIGACIONES

| Riesgo | Severidad | Mitigación |
|--------|-----------|------------|
| Conflicto de interés con DiDi | ALTA | Construir sin lanzar públicamente. Revisar contrato con abogado laboral. |
| Marca "Pilotea" ya tomada | RESUELTA | Renombrado a Kompara. |
| OCR se rompe si plataformas cambian formato | MEDIA | Claude es adaptable — cambios de prompt lo resuelven. Monitorear error rates. |
| Datos sintéticos de percentiles al inicio | MEDIA | Ser transparente: "Basado en estimados de mercado. Mejora con más datos." |
| Retención baja (uso semanal natural) | MEDIA | Push notification lunes, streak counter, resumen mensual. |
| Free riders comparten screenshots Pro en WhatsApp | BAJA | Es marketing gratis. Cada screenshot tiene branding. |
| Uber lanza su propio comparador | BAJA | Uber nunca se comparará desfavorablemente vs DiDi — no les conviene. |
| Copycats en LATAM | BAJA | First-mover advantage en datos de población. Escalar rápido a LATAM. |

---

## 17. TIMELINE ESTIMADO

| Fase | Qué | Duración |
|------|-----|----------|
| 0. Nombre | Decidir nombre, registrar dominio, iniciar trámite IMPI | 1 semana |
| 1. Setup | Next.js + Supabase + primer parser funcionando | 1 fin de semana |
| 2. Core | 4 parsers + API + dashboard básico | 2-3 fines de semana |
| 3. Polish | Onboarding, auth SMS, comparación, fiscal | 2-3 fines de semana |
| 4. Legal | Aviso privacidad, TOS, consulta abogado laboral | En paralelo |
| 5. Beta | 50-100 conductores en CDMX vía WhatsApp | 2-4 semanas |
| 6. Iterate | Ajustar parsers, refinar UX, seed data real | 2-4 semanas |
| 7. Launch | Deploy público + growth en WhatsApp/TikTok | Cuando legal esté claro |

**Total estimado: 3-4 meses de fines de semana hasta beta funcional.**
**Lanzamiento público: condicionado a claridad legal sobre contrato de DiDi.**
