"use client";

import { useState, useCallback, useRef } from "react";

// ─── Types ───────────────────────────────────────────────────
export type UploadStatus =
  | "idle"
  | "validating"
  | "uploading"
  | "processing"
  | "success"
  | "error";

export type ProcessingStep = 0 | 1 | 2 | 3;

export type UploadResult = {
  uploadId: string;
  weeklyDataId: string;
  metrics: Record<string, unknown>;
};

export type UploadError = {
  message: string;
  retryable: boolean;
};

export type UploadState = {
  status: UploadStatus;
  processingStep: ProcessingStep;
  progress: number; // 0-100 for upload progress
  result: UploadResult | null;
  error: UploadError | null;
};

// ─── Constants ───────────────────────────────────────────────
const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
const ALLOWED_TYPES = new Set([
  "image/png",
  "image/jpeg",
  "image/webp",
  "application/pdf",
]);

const PROCESSING_STEP_DURATIONS: number[] = [2000, 4000, 3000, 3000];

// ─── Validation ──────────────────────────────────────────────
export type ValidationError = {
  type: "size" | "format";
  message: string;
};

export function validateFile(file: File): ValidationError | null {
  if (file.size > MAX_FILE_SIZE) {
    return {
      type: "size",
      message: "El archivo es demasiado grande. El maximo es 10 MB.",
    };
  }

  if (!ALLOWED_TYPES.has(file.type)) {
    return {
      type: "format",
      message:
        "Formato no soportado. Usa PNG, JPG, WebP o PDF.",
    };
  }

  return null;
}

// ─── Hook ────────────────────────────────────────────────────
export function useUpload() {
  const [state, setState] = useState<UploadState>({
    status: "idle",
    processingStep: 0,
    progress: 0,
    result: null,
    error: null,
  });

  const abortRef = useRef<AbortController | null>(null);
  const stepTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearTimers = useCallback(() => {
    if (stepTimerRef.current) {
      clearTimeout(stepTimerRef.current);
      stepTimerRef.current = null;
    }
  }, []);

  const advanceProcessingSteps = useCallback(() => {
    // Simulate the 4-step processing animation.
    // The server processes synchronously, so we animate on the client side
    // while waiting for the response.
    let currentStep: ProcessingStep = 0;

    const advance = () => {
      if (currentStep < 3) {
        currentStep = (currentStep + 1) as ProcessingStep;
        setState((prev) => {
          // Only advance if we're still processing
          if (prev.status !== "uploading" && prev.status !== "processing") {
            return prev;
          }
          return { ...prev, processingStep: currentStep, status: "processing" };
        });
        stepTimerRef.current = setTimeout(
          advance,
          PROCESSING_STEP_DURATIONS[currentStep],
        );
      }
    };

    stepTimerRef.current = setTimeout(
      advance,
      PROCESSING_STEP_DURATIONS[0],
    );
  }, []);

  const upload = useCallback(
    async (
      fileOrFiles: File | File[],
      platform: string,
      uploadType: "screenshot" | "pdf",
    ) => {
      const files = Array.isArray(fileOrFiles) ? fileOrFiles : [fileOrFiles];

      // Validate all files
      for (const file of files) {
        const validationError = validateFile(file);
        if (validationError) {
          setState({
            status: "error",
            processingStep: 0,
            progress: 0,
            result: null,
            error: {
              message: validationError.message,
              retryable: false,
            },
          });
          return;
        }
      }

      // Reset state
      setState({
        status: "uploading",
        processingStep: 0,
        progress: 0,
        result: null,
        error: null,
      });

      // Start processing animation
      advanceProcessingSteps();

      // Create abort controller
      abortRef.current = new AbortController();

      try {
        const formData = new FormData();
        formData.append("platform", platform);
        formData.append("upload_type", uploadType);
        for (const file of files) {
          formData.append("files", file);
        }

        const response = await fetch("/api/uploads", {
          method: "POST",
          body: formData,
          signal: abortRef.current.signal,
        });

        clearTimers();

        if (!response.ok) {
          const data = await response.json().catch(() => null);
          const errorMessage =
            data?.error ||
            "Ocurrio un error al subir el archivo. Intenta de nuevo.";

          setState({
            status: "error",
            processingStep: 0,
            progress: 0,
            result: null,
            error: {
              message: errorMessage,
              retryable: true,
            },
          });
          return;
        }

        const data = await response.json();

        // Jump to final step briefly before success
        setState({
          status: "processing",
          processingStep: 3,
          progress: 100,
          result: null,
          error: null,
        });

        // Small delay so user sees the final step
        await new Promise((resolve) => setTimeout(resolve, 800));

        setState({
          status: "success",
          processingStep: 3,
          progress: 100,
          result: {
            uploadId: data.upload_id,
            weeklyDataId: data.weekly_data_id,
            metrics: data.metrics,
          },
          error: null,
        });
      } catch (err: unknown) {
        clearTimers();

        if (err instanceof DOMException && err.name === "AbortError") {
          setState({
            status: "idle",
            processingStep: 0,
            progress: 0,
            result: null,
            error: null,
          });
          return;
        }

        setState({
          status: "error",
          processingStep: 0,
          progress: 0,
          result: null,
          error: {
            message: "Error de conexion. Verifica tu internet e intenta de nuevo.",
            retryable: true,
          },
        });
      }
    },
    [advanceProcessingSteps, clearTimers],
  );

  const reset = useCallback(() => {
    clearTimers();
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    setState({
      status: "idle",
      processingStep: 0,
      progress: 0,
      result: null,
      error: null,
    });
  }, [clearTimers]);

  const cancel = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    clearTimers();
    setState({
      status: "idle",
      processingStep: 0,
      progress: 0,
      result: null,
      error: null,
    });
  }, [clearTimers]);

  return {
    ...state,
    upload,
    reset,
    cancel,
  };
}
