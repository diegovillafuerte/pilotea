"use client";

import { useState, useMemo } from "react";
import { CITIES, PLATFORMS } from "@/lib/constants";
import type { City } from "@/lib/constants";

type Step = 1 | 2 | 3;

export default function OnboardingPage() {
  const [step, setStep] = useState<Step>(1);
  const [name, setName] = useState("");
  const [cityKey, setCityKey] = useState("");
  const [citySearch, setCitySearch] = useState("");
  const [cityDropdownOpen, setCityDropdownOpen] = useState(false);
  const [selectedPlatforms, setSelectedPlatforms] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const filteredCities = useMemo(() => {
    if (!citySearch.trim()) return CITIES;
    const q = citySearch.toLowerCase();
    return CITIES.filter(
      (c) =>
        c.displayName.toLowerCase().includes(q) ||
        c.key.toLowerCase().includes(q),
    );
  }, [citySearch]);

  const selectedCity: City | undefined = CITIES.find(
    (c) => c.key === cityKey,
  );

  function togglePlatform(key: string) {
    setSelectedPlatforms((prev) =>
      prev.includes(key) ? prev.filter((p) => p !== key) : [...prev, key],
    );
  }

  function canAdvance(): boolean {
    if (step === 1) return name.trim().length >= 2;
    if (step === 2) return cityKey !== "";
    if (step === 3) return selectedPlatforms.length >= 1;
    return false;
  }

  function handleNext() {
    if (!canAdvance()) return;
    if (step < 3) {
      setStep((step + 1) as Step);
      setError("");
    }
  }

  function handleBack() {
    if (step > 1) {
      setStep((step - 1) as Step);
      setError("");
    }
  }

  async function handleSubmit() {
    if (!canAdvance()) return;
    setSubmitting(true);
    setError("");

    try {
      const res = await fetch("/api/drivers/onboarding", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: name.trim(),
          city: cityKey,
          platforms: selectedPlatforms,
        }),
      });

      if (!res.ok) {
        const data = await res.json().catch(() => null);
        setError(
          data?.error || "Ocurrio un error. Intenta de nuevo.",
        );
        setSubmitting(false);
        return;
      }

      // Redirect to dashboard on success
      window.location.href = "/dashboard";
    } catch {
      setError("Error de conexion. Intenta de nuevo.");
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      {/* Progress bar */}
      <div className="w-full bg-gray-200">
        <div
          className="h-1.5 bg-blue-600 transition-all duration-300"
          style={{ width: `${(step / 3) * 100}%` }}
        />
      </div>

      <div className="flex flex-1 flex-col items-center justify-center px-6 py-8">
        <div className="w-full max-w-md">
          {/* Step indicator */}
          <p className="mb-2 text-sm text-gray-500">
            Paso {step} de 3
          </p>

          {/* Step 1: Name */}
          {step === 1 && (
            <div>
              <h1 className="mb-2 text-2xl font-bold text-gray-900">
                Como te llamas?
              </h1>
              <p className="mb-6 text-gray-600">
                Tu nombre aparecera en tu perfil de conductor.
              </p>
              <label htmlFor="name-input" className="sr-only">
                Nombre
              </label>
              <input
                id="name-input"
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Tu nombre"
                autoFocus
                className="w-full rounded-xl border border-gray-300 px-4 py-4 text-lg focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleNext();
                }}
              />
            </div>
          )}

          {/* Step 2: City */}
          {step === 2 && (
            <div>
              <h1 className="mb-2 text-2xl font-bold text-gray-900">
                En que ciudad manejas?
              </h1>
              <p className="mb-6 text-gray-600">
                Selecciona la ciudad donde trabajas la mayor parte del tiempo.
              </p>

              <div>
                <label htmlFor="city-search" className="sr-only">
                  Buscar ciudad
                </label>
                <input
                  id="city-search"
                  type="text"
                  value={
                    citySearch || (selectedCity ? selectedCity.displayName : "")
                  }
                  onChange={(e) => {
                    setCitySearch(e.target.value);
                    setCityDropdownOpen(true);
                    if (cityKey) setCityKey("");
                  }}
                  onFocus={() => {
                    if (selectedCity && !citySearch) {
                      setCitySearch("");
                      setCityKey("");
                    }
                    setCityDropdownOpen(true);
                  }}
                  placeholder="Buscar ciudad..."
                  autoFocus
                  className={`w-full rounded-xl border px-4 py-4 text-lg focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 ${
                    selectedCity && !citySearch
                      ? "border-blue-500 bg-blue-50"
                      : "border-gray-300"
                  }`}
                />
              </div>

              {cityDropdownOpen && (
                <ul
                  className="mt-2 max-h-60 overflow-y-auto rounded-xl border border-gray-200 bg-white shadow-lg"
                  role="listbox"
                  aria-label="Ciudades disponibles"
                >
                  {filteredCities.length === 0 ? (
                    <li className="px-4 py-3 text-gray-500">
                      No se encontraron ciudades
                    </li>
                  ) : (
                    filteredCities.map((city) => (
                      <li
                        key={city.key}
                        role="option"
                        aria-selected={city.key === cityKey}
                        className={`cursor-pointer px-4 py-3 text-lg transition-colors hover:bg-blue-50 ${
                          city.key === cityKey
                            ? "bg-blue-100 font-medium text-blue-800"
                            : "text-gray-900"
                        }`}
                        onClick={() => {
                          setCityKey(city.key);
                          setCitySearch("");
                          setCityDropdownOpen(false);
                        }}
                      >
                        {city.displayName}
                      </li>
                    ))
                  )}
                </ul>
              )}
            </div>
          )}

          {/* Step 3: Platforms */}
          {step === 3 && (
            <div>
              <h1 className="mb-2 text-2xl font-bold text-gray-900">
                En que plataformas manejas?
              </h1>
              <p className="mb-6 text-gray-600">
                Selecciona todas las que uses. Puedes cambiarlas despues.
              </p>

              <div className="space-y-3">
                {PLATFORMS.map((platform) => {
                  const isSelected = selectedPlatforms.includes(platform.key);
                  return (
                    <button
                      key={platform.key}
                      type="button"
                      onClick={() => togglePlatform(platform.key)}
                      className={`flex w-full items-center rounded-xl border-2 px-5 py-4 text-left text-lg font-medium transition-all ${
                        isSelected
                          ? "border-blue-500 bg-blue-50 text-blue-800"
                          : "border-gray-200 bg-white text-gray-900 hover:border-gray-300"
                      }`}
                    >
                      <span
                        className={`mr-3 flex h-6 w-6 items-center justify-center rounded-md border-2 text-sm ${
                          isSelected
                            ? "border-blue-500 bg-blue-500 text-white"
                            : "border-gray-300"
                        }`}
                      >
                        {isSelected ? "\u2713" : ""}
                      </span>
                      {platform.displayName}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Error message */}
          {error && (
            <p className="mt-4 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </p>
          )}

          {/* Navigation buttons */}
          <div className="mt-8 flex gap-3">
            {step > 1 && (
              <button
                type="button"
                onClick={handleBack}
                className="flex-1 rounded-xl border border-gray-300 px-6 py-4 text-lg font-medium text-gray-700 transition-colors hover:bg-gray-100"
              >
                Atras
              </button>
            )}

            {step < 3 ? (
              <button
                type="button"
                onClick={handleNext}
                disabled={!canAdvance()}
                className="flex-1 rounded-xl bg-blue-600 px-6 py-4 text-lg font-medium text-white transition-colors hover:bg-blue-700 disabled:bg-gray-300 disabled:text-gray-500"
              >
                Siguiente
              </button>
            ) : (
              <button
                type="button"
                onClick={handleSubmit}
                disabled={!canAdvance() || submitting}
                className="flex-1 rounded-xl bg-blue-600 px-6 py-4 text-lg font-medium text-white transition-colors hover:bg-blue-700 disabled:bg-gray-300 disabled:text-gray-500"
              >
                {submitting ? "Guardando..." : "Comenzar"}
              </button>
            )}
          </div>

          {/* Privacy badge */}
          <div className="mt-6 flex items-center justify-center gap-2 text-sm text-gray-500">
            <svg
              className="h-4 w-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
              />
            </svg>
            <span>Tus datos, tu control</span>
          </div>
        </div>
      </div>
    </div>
  );
}
