import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock the S3Client before importing the module
const mockSend = vi.fn();
vi.mock("@aws-sdk/client-s3", () => ({
  S3Client: vi.fn().mockImplementation(() => ({
    send: mockSend,
  })),
  PutObjectCommand: vi.fn().mockImplementation((params) => params),
  GetObjectCommand: vi.fn().mockImplementation((params) => params),
  DeleteObjectCommand: vi.fn().mockImplementation((params) => params),
}));

describe("R2 storage", () => {
  beforeEach(() => {
    vi.resetModules();
    mockSend.mockReset();
    // Set required env vars
    process.env.R2_ENDPOINT = "https://test.r2.cloudflarestorage.com";
    process.env.R2_ACCESS_KEY_ID = "test-key";
    process.env.R2_SECRET_ACCESS_KEY = "test-secret";
    process.env.R2_BUCKET_NAME = "test-bucket";
  });

  it("uploadFile sends PutObject command", async () => {
    mockSend.mockResolvedValueOnce({});
    const { uploadFile } = await import("@/lib/storage/r2");

    await uploadFile("test/file.pdf", Buffer.from("data"), "application/pdf");
    expect(mockSend).toHaveBeenCalledOnce();
  });

  it("getFile returns buffer from GetObject", async () => {
    const testData = Buffer.from("file content");
    mockSend.mockResolvedValueOnce({
      Body: {
        transformToByteArray: () => Promise.resolve(new Uint8Array(testData)),
      },
    });
    const { getFile } = await import("@/lib/storage/r2");

    const result = await getFile("test/file.pdf");
    expect(Buffer.isBuffer(result)).toBe(true);
    expect(mockSend).toHaveBeenCalledOnce();
  });

  it("deleteFile sends DeleteObject command", async () => {
    mockSend.mockResolvedValueOnce({});
    const { deleteFile } = await import("@/lib/storage/r2");

    await deleteFile("test/file.pdf");
    expect(mockSend).toHaveBeenCalledOnce();
  });

  it("uploadFile throws with clear message on error", async () => {
    mockSend.mockRejectedValueOnce(new Error("Network error"));
    const { uploadFile } = await import("@/lib/storage/r2");

    await expect(
      uploadFile("test/file.pdf", Buffer.from("data"), "application/pdf"),
    ).rejects.toThrow("Error al subir archivo a R2");
  });

  it("getFile throws with clear message on error", async () => {
    mockSend.mockRejectedValueOnce(new Error("Not found"));
    const { getFile } = await import("@/lib/storage/r2");

    await expect(getFile("test/file.pdf")).rejects.toThrow(
      "Error al descargar archivo de R2",
    );
  });

  it("getFile throws when body is empty", async () => {
    mockSend.mockResolvedValueOnce({ Body: null });
    const { getFile } = await import("@/lib/storage/r2");

    await expect(getFile("test/file.pdf")).rejects.toThrow(
      "Error al descargar archivo de R2",
    );
  });
});
