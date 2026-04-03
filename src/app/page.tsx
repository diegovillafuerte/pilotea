import { Logo } from "@/components/ui/logo";
import Link from "next/link";

function BarChart() {
  return (
    <svg
      className="w-full h-full"
      viewBox="0 0 400 200"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* Grid lines */}
      {[0, 1, 2, 3, 4].map((i) => (
        <line
          key={i}
          x1="40"
          y1={40 + i * 35}
          x2="380"
          y2={40 + i * 35}
          stroke="#e2e8f0"
          strokeWidth="1"
        />
      ))}
      {/* Y axis labels */}
      <text x="30" y="44" textAnchor="end" fill="#94a3b8" fontSize="10">
        $800
      </text>
      <text x="30" y="79" textAnchor="end" fill="#94a3b8" fontSize="10">
        $600
      </text>
      <text x="30" y="114" textAnchor="end" fill="#94a3b8" fontSize="10">
        $400
      </text>
      <text x="30" y="149" textAnchor="end" fill="#94a3b8" fontSize="10">
        $200
      </text>
      {/* Bars — "your earnings" */}
      {[
        { x: 70, h: 90, label: "Lun" },
        { x: 120, h: 110, label: "Mar" },
        { x: 170, h: 70, label: "Mie" },
        { x: 220, h: 130, label: "Jue" },
        { x: 270, h: 100, label: "Vie" },
        { x: 320, h: 140, label: "Sab" },
      ].map(({ x, h, label }) => (
        <g key={label}>
          <rect
            x={x}
            y={175 - h}
            width="22"
            height={h}
            rx="4"
            fill="#059669"
            opacity="0.85"
          />
          <rect
            x={x + 24}
            y={175 - h * 0.7}
            width="22"
            height={h * 0.7}
            rx="4"
            fill="#94a3b8"
            opacity="0.3"
          />
          <text
            x={x + 22}
            y="192"
            textAnchor="middle"
            fill="#64748b"
            fontSize="10"
          >
            {label}
          </text>
        </g>
      ))}
      {/* Legend */}
      <rect x="130" y="12" width="10" height="10" rx="2" fill="#059669" />
      <text x="144" y="21" fill="#475569" fontSize="10">
        Tus ganancias
      </text>
      <rect x="225" y="12" width="10" height="10" rx="2" fill="#94a3b8" opacity="0.3" />
      <text x="239" y="21" fill="#475569" fontSize="10">
        Promedio
      </text>
    </svg>
  );
}

function FeatureCard({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="rounded-2xl border border-neutral-200 bg-white p-6 shadow-sm">
      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
        {icon}
      </div>
      <h3 className="mt-4 text-lg font-semibold text-neutral-900">{title}</h3>
      <p className="mt-2 text-sm leading-relaxed text-neutral-500">
        {description}
      </p>
    </div>
  );
}

function StepCard({
  number,
  title,
  description,
}: {
  number: string;
  title: string;
  description: string;
}) {
  return (
    <div className="flex gap-4">
      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-600 text-sm font-bold text-white">
        {number}
      </div>
      <div>
        <h3 className="font-semibold text-neutral-900">{title}</h3>
        <p className="mt-1 text-sm text-neutral-500">{description}</p>
      </div>
    </div>
  );
}

export default function HomePage() {
  return (
    <div className="flex min-h-screen flex-col">
      {/* Nav */}
      <header className="sticky top-0 z-40 border-b border-neutral-200 bg-white/80 backdrop-blur-md">
        <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Logo variant="full" size={36} />
          <div className="flex items-center gap-3">
            <Link
              href="/login"
              className="text-sm font-medium text-neutral-600 hover:text-neutral-900 transition-colors"
            >
              Iniciar sesion
            </Link>
            <Link
              href="/onboarding"
              className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-primary-700 transition-colors"
            >
              Comenzar gratis
            </Link>
          </div>
        </nav>
      </header>

      <main className="flex-1">
        {/* Hero */}
        <section className="relative overflow-hidden bg-gradient-to-b from-primary-50/50 via-white to-white">
          <div className="mx-auto max-w-6xl px-4 pb-16 pt-20 sm:px-6 sm:pb-24 sm:pt-28">
            <div className="grid gap-12 lg:grid-cols-2 lg:items-center lg:gap-16">
              <div>
                <div className="inline-flex items-center gap-2 rounded-full border border-primary-200 bg-primary-50 px-3 py-1 text-xs font-medium text-primary-700">
                  <span className="h-1.5 w-1.5 rounded-full bg-primary-500" />
                  Gratis para conductores en Mexico
                </div>
                <h1 className="mt-6 text-4xl font-bold leading-tight tracking-tight text-neutral-900 sm:text-5xl lg:text-6xl">
                  Sabe cuanto{" "}
                  <span className="text-primary-600">realmente</span> ganas
                </h1>
                <p className="mt-5 max-w-lg text-lg leading-relaxed text-neutral-500">
                  Sube tus capturas de Uber, DiDi o InDriver. Kompara extrae
                  tus datos, calcula percentiles y te muestra como estas vs.
                  otros conductores.
                </p>
                <div className="mt-8 flex flex-col gap-3 sm:flex-row">
                  <Link
                    href="/onboarding"
                    className="inline-flex items-center justify-center rounded-xl bg-primary-600 px-6 py-3 text-base font-semibold text-white shadow-md shadow-primary-600/20 hover:bg-primary-700 transition-all hover:shadow-lg"
                  >
                    Subir mis capturas
                  </Link>
                  <a
                    href="#como-funciona"
                    className="inline-flex items-center justify-center rounded-xl border border-neutral-300 bg-white px-6 py-3 text-base font-semibold text-neutral-700 hover:bg-neutral-50 transition-colors"
                  >
                    Como funciona
                  </a>
                </div>
              </div>

              {/* Hero illustration — earnings comparison chart */}
              <div className="rounded-2xl border border-neutral-200 bg-white p-4 shadow-xl shadow-neutral-200/50 lg:p-6">
                <div className="mb-3 flex items-center gap-2">
                  <Logo variant="monogram" size={24} />
                  <span className="text-sm font-semibold text-neutral-700">
                    Tu resumen semanal
                  </span>
                </div>
                <BarChart />
                <div className="mt-4 grid grid-cols-3 gap-3">
                  {[
                    { label: "Ganancia total", value: "$4,280", delta: "+12%" },
                    { label: "Percentil", value: "P72", delta: "Top 28%" },
                    { label: "Hrs activas", value: "38.5h", delta: "$111/hr" },
                  ].map((stat) => (
                    <div
                      key={stat.label}
                      className="rounded-lg bg-neutral-50 p-3 text-center"
                    >
                      <p className="text-xs text-neutral-400">{stat.label}</p>
                      <p className="mt-1 text-lg font-bold text-neutral-900">
                        {stat.value}
                      </p>
                      <p className="text-xs font-medium text-primary-600">
                        {stat.delta}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Features */}
        <section className="border-t border-neutral-100 bg-white py-20 sm:py-24">
          <div className="mx-auto max-w-6xl px-4 sm:px-6">
            <div className="text-center">
              <h2 className="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
                Todo lo que necesitas para ganar mas
              </h2>
              <p className="mt-3 text-neutral-500">
                Datos reales, no estimaciones. Construido por y para conductores.
              </p>
            </div>
            <div className="mt-14 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              <FeatureCard
                icon={
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                  </svg>
                }
                title="Sube capturas"
                description="Toma foto de tu pantalla de ganancias en Uber, DiDi o InDriver. Nuestra IA extrae los numeros automaticamente."
              />
              <FeatureCard
                icon={
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
                  </svg>
                }
                title="Ve tus percentiles"
                description="Descubre si estas en el top 10% o en el promedio. Compara por ciudad, plataforma y horario."
              />
              <FeatureCard
                icon={
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                  </svg>
                }
                title="Mejora tus ganancias"
                description="Recibe tips personalizados basados en tus datos. Sabe cuando y donde conviene mas manejar."
              />
              <FeatureCard
                icon={
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                }
                title="Privado y seguro"
                description="Tus datos son tuyos. No compartimos informacion personal con nadie. Encriptacion de extremo a extremo."
              />
              <FeatureCard
                icon={
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M17 8h2a2 2 0 012 2v6a2 2 0 01-2 2h-2v4l-4-4H9a1.994 1.994 0 01-1.414-.586m0 0L11 14h4a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2v4l.586-.586z" />
                  </svg>
                }
                title="Via WhatsApp"
                description="No necesitas bajar otra app. Manda tus capturas por WhatsApp y recibe tu reporte al instante."
              />
              <FeatureCard
                icon={
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 7h6m0 10v-3m-3 3h.01M9 17h.01M9 14h.01M12 14h.01M15 11h.01M12 11h.01M9 11h.01M7 21h10a2 2 0 002-2V5a2 2 0 00-2-2H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
                  </svg>
                }
                title="Info fiscal"
                description="Calcula tus impuestos estimados y ten tu informacion lista para tu contador."
              />
            </div>
          </div>
        </section>

        {/* How it works */}
        <section
          id="como-funciona"
          className="border-t border-neutral-100 bg-neutral-50 py-20 sm:py-24"
        >
          <div className="mx-auto max-w-3xl px-4 sm:px-6">
            <div className="text-center">
              <h2 className="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
                Asi de facil
              </h2>
              <p className="mt-3 text-neutral-500">
                De captura a reporte en menos de un minuto.
              </p>
            </div>
            <div className="mt-14 space-y-8">
              <StepCard
                number="1"
                title="Toma una captura de pantalla"
                description="Abre tu app de Uber, DiDi o InDriver y toma un screenshot de tus ganancias semanales."
              />
              <StepCard
                number="2"
                title="Mandala por WhatsApp"
                description="Envia la captura a nuestro numero de WhatsApp o subela directo en la web."
              />
              <StepCard
                number="3"
                title="Recibe tu reporte"
                description="En segundos, nuestra IA analiza tu imagen y te devuelve tu percentil, comparaciones y tips."
              />
            </div>
            <div className="mt-12 text-center">
              <Link
                href="/onboarding"
                className="inline-flex items-center justify-center rounded-xl bg-primary-600 px-6 py-3 text-base font-semibold text-white shadow-md shadow-primary-600/20 hover:bg-primary-700 transition-all hover:shadow-lg"
              >
                Empezar ahora — es gratis
              </Link>
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-neutral-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-4 px-4 py-8 sm:flex-row sm:justify-between sm:px-6">
          <Logo variant="monogram" size={28} />
          <p className="text-sm text-neutral-400">
            &copy; {new Date().getFullYear()} Kompara. Hecho en Mexico.
          </p>
        </div>
      </footer>
    </div>
  );
}
