/**
 * Object storage for uploaded import originals.
 *
 * Defines a small {@link StorageAdapter} interface with two implementations:
 *   - {@link R2Storage}    — Cloudflare R2 (S3-compatible), env-configured.
 *     Ported from the web app's src/lib/storage/r2.ts (same key scheme, same
 *     Spanish error wrapping), behind the adapter interface.
 *   - {@link MemoryStorage} — in-memory map, used in tests and in local/dev when
 *     the R2_* env vars are absent. Never touches the network.
 *
 * {@link storageFromEnv} picks R2 when R2_ENDPOINT / R2_ACCESS_KEY_ID /
 * R2_SECRET_ACCESS_KEY / R2_BUCKET_NAME are all set, else MemoryStorage — the
 * same "real-when-configured, dev-logger-otherwise" pattern the auth layer uses.
 */

import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
} from "@aws-sdk/client-s3";

export interface StorageAdapter {
  /** Upload a file. `key` follows `{driverId}/{importId}.{ext}`. */
  uploadFile(key: string, body: Buffer, contentType: string): Promise<void>;
  /** Download a previously-stored file as a Buffer. */
  getFile(key: string): Promise<Buffer>;
  /** Delete a stored file. Idempotent. */
  deleteFile(key: string): Promise<void>;
}

// ─── R2 / S3-compatible storage ───────────────────────────────
export class R2Storage implements StorageAdapter {
  private client: S3Client;
  private bucket: string;

  constructor(opts: {
    endpoint: string;
    accessKeyId: string;
    secretAccessKey: string;
    bucket: string;
  }) {
    this.client = new S3Client({
      region: "auto",
      endpoint: opts.endpoint,
      credentials: {
        accessKeyId: opts.accessKeyId,
        secretAccessKey: opts.secretAccessKey,
      },
    });
    this.bucket = opts.bucket;
  }

  async uploadFile(key: string, body: Buffer, contentType: string): Promise<void> {
    try {
      await this.client.send(
        new PutObjectCommand({
          Bucket: this.bucket,
          Key: key,
          Body: body,
          ContentType: contentType,
        }),
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Error desconocido";
      throw new Error(`Error al subir archivo a R2 (key: ${key}): ${message}`);
    }
  }

  async getFile(key: string): Promise<Buffer> {
    try {
      const response = await this.client.send(
        new GetObjectCommand({
          Bucket: this.bucket,
          Key: key,
        }),
      );

      if (!response.Body) {
        throw new Error("El archivo esta vacio o no se encontro");
      }

      const byteArray = await response.Body.transformToByteArray();
      return Buffer.from(byteArray);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Error desconocido";
      throw new Error(`Error al descargar archivo de R2 (key: ${key}): ${message}`);
    }
  }

  async deleteFile(key: string): Promise<void> {
    try {
      await this.client.send(
        new DeleteObjectCommand({
          Bucket: this.bucket,
          Key: key,
        }),
      );
    } catch (error) {
      const message = error instanceof Error ? error.message : "Error desconocido";
      throw new Error(`Error al eliminar archivo de R2 (key: ${key}): ${message}`);
    }
  }
}

// ─── In-memory storage (tests / dev default) ──────────────────
export class MemoryStorage implements StorageAdapter {
  private store = new Map<string, Buffer>();

  async uploadFile(key: string, body: Buffer): Promise<void> {
    this.store.set(key, Buffer.from(body));
  }

  async getFile(key: string): Promise<Buffer> {
    const value = this.store.get(key);
    if (!value) {
      throw new Error(`El archivo esta vacio o no se encontro (key: ${key})`);
    }
    return value;
  }

  async deleteFile(key: string): Promise<void> {
    this.store.delete(key);
  }

  /** Test/dev helper: list stored keys. */
  keys(): string[] {
    return [...this.store.keys()];
  }
}

/**
 * Build the storage adapter from the environment: R2 when fully configured,
 * else an in-memory adapter (dev/CI). Keeps production wiring a one-liner while
 * letting tests and local runs work with zero credentials.
 */
export function storageFromEnv(): StorageAdapter {
  const endpoint = process.env.R2_ENDPOINT;
  const accessKeyId = process.env.R2_ACCESS_KEY_ID;
  const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY;
  const bucket = process.env.R2_BUCKET_NAME;

  if (endpoint && accessKeyId && secretAccessKey && bucket) {
    return new R2Storage({ endpoint, accessKeyId, secretAccessKey, bucket });
  }

  return new MemoryStorage();
}
