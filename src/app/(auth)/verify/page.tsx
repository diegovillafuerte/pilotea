"use client";

import { useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Suspense } from "react";

type VerifyState = "verifying" | "error";

function VerifyContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const token = searchParams.get("token");

  // Compute initial state based on whether token is present
  const initialState: VerifyState = token ? "verifying" : "error";
  const initialError = token
    ? null
    : "Link invalido. No se encontro el token.";

  const [state, setState] = useState<VerifyState>(initialState);
  const [errorMessage, setErrorMessage] = useState<string | null>(initialError);

  useEffect(() => {
    if (!token) {
      return;
    }

    // The actual verification is handled by the API route at /api/auth/verify
    // which sets the cookie and redirects. This page is a fallback in case
    // the user navigates here directly or the redirect doesn't work.
    // Normally the user clicks the link which goes to /api/auth/verify?token=xxx
    // and gets redirected. This page handles the edge case.
    async function verify() {
      try {
        const res = await fetch(`/api/auth/verify?token=${token}`, {
          redirect: "follow",
        });

        if (res.redirected) {
          router.push(new URL(res.url).pathname);
          return;
        }

        if (!res.ok) {
          setState("error");
          setErrorMessage(
            "El link ha expirado o ya fue usado. Solicita uno nuevo.",
          );
        }
      } catch {
        setState("error");
        setErrorMessage("Error de conexion. Intenta de nuevo.");
      }
    }

    verify();
  }, [token, router]);

  if (state === "error") {
    return (
      <div className="flex flex-col items-center text-center">
        <div className="mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
          <svg
            className="h-8 w-8 text-red-600"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z"
            />
          </svg>
        </div>
        <h2 className="text-xl font-semibold text-gray-900">
          No pudimos verificar tu link
        </h2>
        <p className="mt-2 text-gray-600">{errorMessage}</p>
        <a
          href="/login"
          className="mt-6 rounded-lg bg-blue-600 px-6 py-2 text-white transition-colors hover:bg-blue-700"
        >
          Volver a iniciar sesion
        </a>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center text-center">
      <div className="mb-6">
        <svg className="h-12 w-12 animate-spin text-blue-600" viewBox="0 0 24 24">
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
      </div>
      <h2 className="text-xl font-semibold text-gray-900">Verificando...</h2>
      <p className="mt-2 text-gray-600">Validando tu enlace magico</p>
    </div>
  );
}

export default function VerifyPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-6">
      <Suspense
        fallback={
          <div className="flex items-center gap-2 text-gray-500">
            Cargando...
          </div>
        }
      >
        <VerifyContent />
      </Suspense>
    </main>
  );
}
