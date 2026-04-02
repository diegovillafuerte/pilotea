"use client";

import { useState, useRef, useCallback } from "react";
import type { ValidationError } from "@/hooks/use-upload";
import { validateFile } from "@/hooks/use-upload";

type FileDropzoneProps = {
  onFileSelected: (file: File) => void;
  accept: string;
  disabled?: boolean;
  platformName: string;
};

export default function FileDropzone({
  onFileSelected,
  accept,
  disabled = false,
  platformName,
}: FileDropzoneProps) {
  const [isDragOver, setIsDragOver] = useState(false);
  const [validationError, setValidationError] =
    useState<ValidationError | null>(null);
  const [selectedFileName, setSelectedFileName] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFile = useCallback(
    (file: File) => {
      setValidationError(null);
      const error = validateFile(file);
      if (error) {
        setValidationError(error);
        setSelectedFileName(null);
        return;
      }
      setSelectedFileName(file.name);
      onFileSelected(file);
    },
    [onFileSelected],
  );

  const handleDragOver = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (!disabled) {
        setIsDragOver(true);
      }
    },
    [disabled],
  );

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);

      if (disabled) return;

      const files = e.dataTransfer.files;
      if (files.length > 0) {
        handleFile(files[0]);
      }
    },
    [disabled, handleFile],
  );

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const files = e.target.files;
      if (files && files.length > 0) {
        handleFile(files[0]);
      }
      // Reset input so the same file can be re-selected
      e.target.value = "";
    },
    [handleFile],
  );

  const handleClick = useCallback(() => {
    if (!disabled && inputRef.current) {
      inputRef.current.click();
    }
  }, [disabled]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if ((e.key === "Enter" || e.key === " ") && !disabled) {
        e.preventDefault();
        inputRef.current?.click();
      }
    },
    [disabled],
  );

  return (
    <div className="w-full">
      <div
        role="button"
        tabIndex={disabled ? -1 : 0}
        aria-label={`Seleccionar archivo para ${platformName}`}
        aria-disabled={disabled}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        className={`
          relative flex flex-col items-center justify-center
          rounded-2xl border-2 border-dashed
          px-6 py-10
          transition-all duration-200
          ${
            disabled
              ? "cursor-not-allowed border-gray-200 bg-gray-50 opacity-60"
              : isDragOver
                ? "cursor-pointer border-blue-500 bg-blue-50"
                : selectedFileName
                  ? "cursor-pointer border-green-400 bg-green-50"
                  : "cursor-pointer border-gray-300 bg-white hover:border-blue-400 hover:bg-blue-50/50"
          }
        `}
      >
        {/* Icon */}
        {selectedFileName ? (
          <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-green-100">
            <svg
              className="h-7 w-7 text-green-600"
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
        ) : (
          <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-blue-100">
            <svg
              className="h-7 w-7 text-blue-600"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"
              />
            </svg>
          </div>
        )}

        {/* Text */}
        {selectedFileName ? (
          <>
            <p className="text-base font-medium text-green-700">
              {selectedFileName}
            </p>
            <p className="mt-1 text-sm text-green-600">
              Toca para cambiar archivo
            </p>
          </>
        ) : (
          <>
            <p className="text-base font-medium text-gray-700">
              Toca para seleccionar archivo
            </p>
            <p className="mt-1 text-sm text-gray-500">
              O arrastra y suelta aqui
            </p>
            <p className="mt-2 text-xs text-gray-400">
              PNG, JPG, WebP o PDF (max 10 MB)
            </p>
          </>
        )}

        {/* Hidden file input */}
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          onChange={handleInputChange}
          className="hidden"
          aria-hidden="true"
          disabled={disabled}
        />
      </div>

      {/* Validation error */}
      {validationError && (
        <div className="mt-3 rounded-lg bg-red-50 px-4 py-3">
          <p className="text-sm font-medium text-red-700">
            {validationError.message}
          </p>
        </div>
      )}
    </div>
  );
}
