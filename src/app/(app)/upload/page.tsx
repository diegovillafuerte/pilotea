"use client";

import { useState, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { PLATFORMS } from "@/lib/constants";
import FileDropzone from "@/components/upload/file-dropzone";
import ProcessingSteps from "@/components/upload/processing-steps";
import { useUpload } from "@/hooks/use-upload";

// ─── Platform instructions (Spanish) ────────────────────────
const PLATFORM_INSTRUCTIONS: Record<
  string,
  { title: string; description: string; hint?: string }
> = {
  uber: {
    title: "Uber",
    description:
      "Sube una captura de pantalla de tu resumen semanal o el PDF que Uber te envia por correo.",
    hint: "PDF semanal (recomendado)",
  },
  didi: {
    title: "DiDi",
    description:
      "Sube una captura de pantalla de tu resumen semanal de ganancias en DiDi.",
  },
  indrive: {
    title: "InDrive",
    description:
      "Sube una captura de pantalla de tu resumen de ganancias en InDrive.",
  },
};

const FILE_ACCEPT =
  "image/png,image/jpeg,image/webp,application/pdf";

export default function UploadPage() {
  const router = useRouter();
  const {
    status,
    processingStep,
    error,
    upload,
    reset,
  } = useUpload();

  const [driverPlatforms, setDriverPlatforms] = useState<string[]>([]);
  const [activePlatform, setActivePlatform] = useState<string>("");
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Fetch driver's platforms from the server
  useEffect(() => {
    async function loadProfile() {
      try {
        const res = await fetch("/api/drivers/me");
        if (!res.ok) {
          setProfileError("No se pudo cargar tu perfil.");
          setLoadingProfile(false);
          return;
        }
        const data = await res.json();
        const platforms: string[] = data.platforms ?? [];

        if (platforms.length === 0) {
          // Fallback: show all platforms if none set
          setDriverPlatforms(PLATFORMS.map((p) => p.key));
        } else {
          setDriverPlatforms(platforms);
        }
        setActivePlatform(platforms[0] ?? PLATFORMS[0].key);
      } catch {
        // If the API doesn't exist yet (B-006 dependency), use all platforms
        setDriverPlatforms(PLATFORMS.map((p) => p.key));
        setActivePlatform(PLATFORMS[0].key);
      } finally {
        setLoadingProfile(false);
      }
    }

    loadProfile();
  }, []);

  // Redirect to dashboard on success
  useEffect(() => {
    if (status === "success") {
      const timer = setTimeout(() => {
        router.push("/dashboard?highlight=new");
      }, 1200);
      return () => clearTimeout(timer);
    }
  }, [status, router]);

  const handleFileSelected = useCallback(
    (file: File) => {
      const uploadType: "screenshot" | "pdf" =
        file.type === "application/pdf" ? "pdf" : "screenshot";
      upload(file, activePlatform, uploadType);
    },
    [upload, activePlatform],
  );

  const handleRetry = useCallback(() => {
    reset();
  }, [reset]);

  // ─── Loading state ──────────────────────────────────────────
  if (loadingProfile) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center px-6">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-blue-600" />
      </main>
    );
  }

  // ─── Profile error ──────────────────────────────────────────
  if (profileError) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center px-6">
        <p className="text-center text-gray-600">{profileError}</p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="mt-4 rounded-xl bg-blue-600 px-6 py-3 text-base font-medium text-white"
        >
          Reintentar
        </button>
      </main>
    );
  }

  // ─── Processing / uploading state ───────────────────────────
  if (status === "uploading" || status === "processing") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center px-6 py-8">
        <ProcessingSteps currentStep={processingStep} />
      </main>
    );
  }

  // ─── Success state ──────────────────────────────────────────
  if (status === "success") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center px-6 py-8">
        <div className="flex flex-col items-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
            <svg
              className="h-8 w-8 text-green-600"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
          <h2 className="mt-4 text-xl font-bold text-gray-900">
            Datos procesados
          </h2>
          <p className="mt-2 text-sm text-gray-500">
            Redirigiendo al dashboard...
          </p>
        </div>
      </main>
    );
  }

  // ─── Error state ────────────────────────────────────────────
  if (status === "error" && error) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center px-6 py-8">
        <div className="w-full max-w-md">
          <div className="flex flex-col items-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
              <svg
                className="h-8 w-8 text-red-600"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </div>
            <h2 className="mt-4 text-xl font-bold text-gray-900">
              Error al procesar
            </h2>
            <p className="mt-2 text-center text-sm text-gray-600">
              {error.message}
            </p>
          </div>

          {error.retryable && (
            <button
              type="button"
              onClick={handleRetry}
              className="mt-6 w-full rounded-xl bg-blue-600 px-6 py-4 text-lg font-medium text-white transition-colors hover:bg-blue-700 active:bg-blue-800"
            >
              Intentar de nuevo
            </button>
          )}

          <button
            type="button"
            onClick={() => router.push("/dashboard")}
            className="mt-3 w-full rounded-xl border border-gray-300 px-6 py-4 text-lg font-medium text-gray-700 transition-colors hover:bg-gray-100"
          >
            Volver al dashboard
          </button>
        </div>
      </main>
    );
  }

  // ─── Idle state — main upload UI ────────────────────────────
  const platformInfo = PLATFORM_INSTRUCTIONS[activePlatform] ?? {
    title: activePlatform,
    description: "Sube una captura de pantalla de tu resumen semanal.",
  };

  const visiblePlatforms = PLATFORMS.filter((p) =>
    driverPlatforms.includes(p.key),
  );

  return (
    <main className="flex min-h-screen flex-col px-4 pb-24 pt-6 sm:px-6">
      <div className="mx-auto w-full max-w-lg">
        {/* Header */}
        <h1 className="text-2xl font-bold text-gray-900">Subir datos</h1>
        <p className="mt-1 text-sm text-gray-500">
          Sube tu resumen semanal para ver tus metricas
        </p>

        {/* Platform tabs */}
        {visiblePlatforms.length > 1 && (
          <div className="mt-6 flex gap-2" role="tablist" aria-label="Plataformas">
            {visiblePlatforms.map((platform) => {
              const isActive = platform.key === activePlatform;
              return (
                <button
                  key={platform.key}
                  type="button"
                  role="tab"
                  aria-selected={isActive}
                  aria-controls={`panel-${platform.key}`}
                  onClick={() => setActivePlatform(platform.key)}
                  className={`
                    flex-1 rounded-xl px-4 py-3 text-base font-medium
                    transition-all duration-200
                    ${
                      isActive
                        ? "bg-blue-600 text-white shadow-sm"
                        : "bg-gray-100 text-gray-600 hover:bg-gray-200"
                    }
                  `}
                >
                  {platform.displayName}
                </button>
              );
            })}
          </div>
        )}

        {/* Platform content */}
        <div
          id={`panel-${activePlatform}`}
          role="tabpanel"
          className="mt-6"
        >
          {/* Instructions */}
          <div className="rounded-xl bg-gray-50 px-4 py-4">
            <p className="text-sm text-gray-700">{platformInfo.description}</p>
            {platformInfo.hint && (
              <p className="mt-2 flex items-center gap-1.5 text-sm font-medium text-blue-700">
                <svg
                  className="h-4 w-4 flex-shrink-0"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                {platformInfo.hint}
              </p>
            )}
          </div>

          {/* File dropzone */}
          <div className="mt-5">
            <FileDropzone
              onFileSelected={handleFileSelected}
              accept={FILE_ACCEPT}
              platformName={platformInfo.title}
            />
          </div>
        </div>
      </div>
    </main>
  );
}
