"use client";

import type { ProcessingStep } from "@/hooks/use-upload";

const STEPS = [
  "Leyendo archivos...",
  "Extrayendo datos con IA...",
  "Calculando metricas...",
  "Comparando con conductores...",
] as const;

type ProcessingStepsProps = {
  currentStep: ProcessingStep;
  driverCount?: number;
};

export default function ProcessingSteps({
  currentStep,
  driverCount,
}: ProcessingStepsProps) {
  return (
    <div className="w-full max-w-sm mx-auto">
      {/* Spinner */}
      <div className="flex justify-center mb-8">
        <div className="relative h-16 w-16">
          <div className="absolute inset-0 rounded-full border-4 border-gray-200" />
          <div className="absolute inset-0 animate-spin rounded-full border-4 border-transparent border-t-blue-600" />
        </div>
      </div>

      {/* Steps */}
      <div className="space-y-4">
        {STEPS.map((label, index) => {
          const stepIndex = index as ProcessingStep;
          const isActive = stepIndex === currentStep;
          const isCompleted = stepIndex < currentStep;
          const isPending = stepIndex > currentStep;

          // Replace placeholder in last step
          const displayLabel =
            index === 3 && driverCount
              ? `Comparando con ${driverCount.toLocaleString("es-MX")} conductores...`
              : label;

          return (
            <div
              key={index}
              className={`flex items-center gap-3 transition-all duration-500 ${
                isPending ? "opacity-30" : "opacity-100"
              }`}
            >
              {/* Step indicator */}
              <div className="flex-shrink-0">
                {isCompleted ? (
                  <div className="flex h-7 w-7 items-center justify-center rounded-full bg-green-500">
                    <svg
                      className="h-4 w-4 text-white"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2.5}
                        d="M5 13l4 4L19 7"
                      />
                    </svg>
                  </div>
                ) : isActive ? (
                  <div className="flex h-7 w-7 items-center justify-center rounded-full bg-blue-100">
                    <div className="h-3 w-3 animate-pulse rounded-full bg-blue-600" />
                  </div>
                ) : (
                  <div className="flex h-7 w-7 items-center justify-center rounded-full bg-gray-100">
                    <div className="h-2.5 w-2.5 rounded-full bg-gray-300" />
                  </div>
                )}
              </div>

              {/* Step text */}
              <span
                className={`text-base transition-all duration-300 ${
                  isActive
                    ? "font-medium text-gray-900"
                    : isCompleted
                      ? "text-green-700"
                      : "text-gray-400"
                }`}
              >
                {displayLabel}
              </span>
            </div>
          );
        })}
      </div>

      {/* Reassurance text */}
      <p className="mt-8 text-center text-sm text-gray-500">
        Esto toma entre 5 y 15 segundos
      </p>
    </div>
  );
}
