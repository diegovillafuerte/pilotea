"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Suspense } from "react";

type LoginState = "idle" | "loading" | "sent" | "error";

function LoginForm() {
  const searchParams = useSearchParams();
  const errorParam = searchParams.get("error");

  const [phone, setPhone] = useState("+52");
  const [state, setState] = useState<LoginState>("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(
    errorParam === "invalid_or_expired"
      ? "El link ha expirado o ya fue usado. Solicita uno nuevo."
      : errorParam === "missing_token"
        ? "Link invalido. Solicita uno nuevo."
        : null,
  );

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setState("loading");
    setErrorMessage(null);

    try {
      const res = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ phone }),
      });

      const data = await res.json();

      if (!res.ok) {
        setState("error");
        setErrorMessage(data.error ?? "Error desconocido");
        return;
      }

      setState("sent");
    } catch {
      setState("error");
      setErrorMessage("Error de conexion. Intenta de nuevo.");
    }
  }

  if (state === "sent") {
    return (
      <div className="flex flex-col items-center text-center">
        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
          <svg
            className="h-8 w-8 text-green-600"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>
        <h2 className="text-xl font-semibold text-gray-900">
          Revisa tu WhatsApp
        </h2>
        <p className="mt-2 text-gray-600">
          Te enviamos un link al numero{" "}
          <span className="font-medium">{phone}</span>
        </p>
        <p className="mt-1 text-sm text-gray-500">
          Toca el link para entrar. Expira en 15 minutos.
        </p>
        <button
          onClick={() => {
            setState("idle");
            setErrorMessage(null);
          }}
          className="mt-6 text-sm text-blue-600 underline"
        >
          Usar otro numero
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-sm">
      <div className="mb-6">
        <label
          htmlFor="phone"
          className="mb-2 block text-sm font-medium text-gray-700"
        >
          Numero de telefono
        </label>
        <input
          id="phone"
          type="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          placeholder="+5215512345678"
          className="w-full rounded-lg border border-gray-300 px-4 py-3 text-lg focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
          disabled={state === "loading"}
          required
        />
        <p className="mt-1 text-xs text-gray-500">
          Incluye el codigo de pais (+52 para Mexico)
        </p>
      </div>

      {errorMessage && (
        <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {errorMessage}
        </div>
      )}

      <button
        type="submit"
        disabled={state === "loading" || phone.length < 10}
        className="w-full rounded-lg bg-green-600 px-4 py-3 text-lg font-semibold text-white transition-colors hover:bg-green-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {state === "loading" ? (
          <span className="flex items-center justify-center gap-2">
            <svg className="h-5 w-5 animate-spin" viewBox="0 0 24 24">
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
                fill="none"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
              />
            </svg>
            Enviando...
          </span>
        ) : (
          "Enviar link por WhatsApp"
        )}
      </button>
    </form>
  );
}

export default function LoginPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-6">
      <div className="mb-8 text-center">
        <h1 className="text-3xl font-bold text-gray-900">Kompara</h1>
        <p className="mt-2 text-gray-600">
          Compara tus ganancias como conductor
        </p>
      </div>
      <Suspense
        fallback={
          <div className="flex items-center gap-2 text-gray-500">
            Cargando...
          </div>
        }
      >
        <LoginForm />
      </Suspense>
    </main>
  );
}
