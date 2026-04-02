import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand,
} from "@aws-sdk/client-s3";

let cachedClient: S3Client | null = null;

function getR2Client(): S3Client {
  if (cachedClient) return cachedClient;

  const endpoint = process.env.R2_ENDPOINT;
  const accessKeyId = process.env.R2_ACCESS_KEY_ID;
  const secretAccessKey = process.env.R2_SECRET_ACCESS_KEY;

  if (!endpoint || !accessKeyId || !secretAccessKey) {
    throw new Error(
      "R2 configuration missing. Set R2_ENDPOINT, R2_ACCESS_KEY_ID, and R2_SECRET_ACCESS_KEY environment variables.",
    );
  }

  cachedClient = new S3Client({
    region: "auto",
    endpoint,
    credentials: {
      accessKeyId,
      secretAccessKey,
    },
  });

  return cachedClient;
}

function getBucketName(): string {
  const bucket = process.env.R2_BUCKET_NAME;
  if (!bucket) {
    throw new Error(
      "R2_BUCKET_NAME environment variable is not set. Set it to your R2 bucket name (e.g. kompara-uploads).",
    );
  }
  return bucket;
}

/**
 * Upload a file to R2 storage.
 * @param key - Object key, e.g. "{driver_id}/{upload_id}.pdf"
 * @param body - File contents as a Buffer
 * @param contentType - MIME type of the file
 */
export async function uploadFile(
  key: string,
  body: Buffer,
  contentType: string,
): Promise<void> {
  const client = getR2Client();
  const bucket = getBucketName();

  try {
    await client.send(
      new PutObjectCommand({
        Bucket: bucket,
        Key: key,
        Body: body,
        ContentType: contentType,
      }),
    );
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Error desconocido";
    throw new Error(`Error al subir archivo a R2 (key: ${key}): ${message}`);
  }
}

/**
 * Download a file from R2 storage.
 * @param key - Object key to retrieve
 * @returns File contents as a Buffer
 */
export async function getFile(key: string): Promise<Buffer> {
  const client = getR2Client();
  const bucket = getBucketName();

  try {
    const response = await client.send(
      new GetObjectCommand({
        Bucket: bucket,
        Key: key,
      }),
    );

    if (!response.Body) {
      throw new Error("El archivo esta vacio o no se encontro");
    }

    const byteArray = await response.Body.transformToByteArray();
    return Buffer.from(byteArray);
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Error desconocido";
    throw new Error(
      `Error al descargar archivo de R2 (key: ${key}): ${message}`,
    );
  }
}

/**
 * Delete a file from R2 storage.
 * @param key - Object key to delete
 */
export async function deleteFile(key: string): Promise<void> {
  const client = getR2Client();
  const bucket = getBucketName();

  try {
    await client.send(
      new DeleteObjectCommand({
        Bucket: bucket,
        Key: key,
      }),
    );
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Error desconocido";
    throw new Error(
      `Error al eliminar archivo de R2 (key: ${key}): ${message}`,
    );
  }
}
