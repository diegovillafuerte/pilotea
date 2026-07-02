# Kompara — Privacy Policy (DRAFT)

> **DRAFT — pending MX data-privacy counsel sign-off (B-038).** This text is written to be *literally
> true* against the shipped architecture (see [play-compliance.md](play-compliance.md) and
> [android-technical-design.md](android-technical-design.md)) so the Play Data Safety declaration and
> this policy agree. Counsel must review before publication; the published URL goes in the Play listing
> and in-app. Spanish is the primary language (es-MX); an English courtesy translation follows.

_Last updated: DRAFT (not yet published)._

## Español (es-MX)

**Qué es Kompara.** Kompara es una herramienta de apoyo a la decisión para conductores de apps de
transporte (Uber, DiDi, inDrive) en México. Lee la oferta de viaje que ya aparece en tu pantalla y te
muestra al instante cuánto ganarías de verdad (por km y por hora), ya descontados tus costos. **Kompara
solo lee información; nunca acepta, rechaza ni toca viajes por ti.**

**Lo que Kompara lee de tu pantalla — y que NUNCA sale de tu teléfono.**
Para darte el veredicto, Kompara usa el servicio de accesibilidad de Android (y, en algunos equipos, la
captura de pantalla del sistema) para leer los textos de la oferta: **tarifa, distancia, tiempo y
destino**. Todo el procesamiento (lectura, OCR y cálculo) ocurre **únicamente en tu teléfono**:

- La imagen de la pantalla se analiza en memoria y **se descarta de inmediato**; no se guarda ni se
  envía a ningún servidor.
- No guardamos el nombre del pasajero, su calificación, ni la dirección de origen o destino.
- No vendemos, no compartimos y no subimos a internet el contenido de tu pantalla.
- El historial de tus viajes (tarifa, distancia, tiempo, veredicto) se guarda **solo en tu teléfono**.

**Lo que sí guarda tu cuenta (solo si creas una).** El lector funciona sin cuenta. Si creas una para
las funciones de comparación/benchmarks, guardamos tu **número de WhatsApp**, tu configuración y tus
**totales** (agregados semanales). Tus totales se sincronizan **solo si aceptas explícitamente**
compartirlos.

**Datos que se transmiten, y solo con tu consentimiento o a tu iniciativa:**
- **Totales semanales** (agregados de ganancias/métricas) — solo si activas "Compartir datos con
  Kompara". Nunca viajes individuales ni datos del pasajero.
- **Importación de tus ingresos** — si tú subes tu resumen semanal (PDF/captura) de Uber/DiDi, se
  procesa en el servidor para leer tus totales. Es una acción que tú inicias.
- **Salud del lector** (contadores anónimos de aciertos/fallos de lectura, sin contenido de pantalla)
  para detectar cuándo una app cambia su diseño. Puedes desactivarlo.
- **Reportes de diagnóstico** (estructura de pantalla con datos personales enmascarados) — solo con tu
  consentimiento por reporte.

**Autenticación.** Usamos tu número de WhatsApp para enviarte un código de acceso. No publicamos ni
compartimos tu número.

**Tus derechos.** Puedes **borrar tu cuenta y tus datos** desde la app (Ajustes → Cuenta). El
historial local se elimina al desinstalar.

**Riesgo que debes conocer.** Usar herramientas de terceros puede contravenir los términos de Uber o
DiDi. Kompara solo lee información y nunca actúa por ti, pero la decisión de usarla es tuya.

**Contacto.** _[correo de soporte — pendiente]_

## English (courtesy translation)

**What Kompara is.** Kompara is a decision-support tool for ride-hailing drivers (Uber, DiDi, inDrive)
in Mexico. It reads the trip offer already on your screen and instantly shows what you'd really earn
(per km and per hour) after your costs. **Kompara only reads information; it never accepts, declines,
or taps trips for you.**

**What Kompara reads from your screen — and that NEVER leaves your phone.**
To produce the verdict, Kompara uses Android's accessibility service (and, on some devices, the
system screen capture) to read the offer text: **fare, distance, time, and destination**. All
processing (reading, OCR, and calculation) happens **only on your phone**:

- The screen image is analyzed in memory and **discarded immediately**; it is never stored or sent to
  any server.
- We do not store the passenger's name, rating, or the pickup/dropoff address.
- We do not sell, share, or upload your screen content.
- Your trip history (fare, distance, time, verdict) is stored **only on your phone**.

**What your account stores (only if you create one).** The reader works without an account. If you
create one for the comparison/benchmark features, we store your **WhatsApp number**, your settings,
and your **weekly totals**. Totals sync **only if you explicitly opt in**.

**Data transmitted only with your consent or at your initiative:**
- **Weekly totals** (earnings/metric aggregates) — only if you enable "Share data with Kompara."
  Never individual trips or passenger data.
- **Earnings import** — if you upload your Uber/DiDi weekly summary (PDF/screenshot), it is processed
  on the server to read your totals. This is a user-initiated action.
- **Reader health** (anonymous parse success/failure counters, no screen content) to detect when an
  app changes its layout. You can turn this off.
- **Diagnostic reports** (screen structure with personal data masked) — only with your per-report
  consent.

**Authentication.** We use your WhatsApp number to send an access code. We do not publish or share it.

**Your rights.** You can **delete your account and data** from the app (Settings → Account). Local
history is removed on uninstall.

**Risk to consider.** Using third-party tools may conflict with Uber's or DiDi's terms. Kompara only
reads information and never acts for you, but the decision to use it is yours.

**Contact.** _[support email — pending]_
