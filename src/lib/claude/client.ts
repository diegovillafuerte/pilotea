import Anthropic from "@anthropic-ai/sdk";

// ─── Types ────────────────────────────────────────────────────
export interface VisionInput {
  data: string; // base64-encoded file content
  mediaType: "image/jpeg" | "image/png" | "image/webp" | "image/gif" | "application/pdf";
}

export interface VisionRequest {
  systemPrompt: string;
  inputs: VisionInput[];
  userPrompt: string;
  maxTokens?: number;
}

export interface VisionResponse {
  text: string;
  usage: {
    input_tokens: number;
    output_tokens: number;
  };
}

// ─── Constants ────────────────────────────────────────────────
const DEFAULT_MODEL = "claude-sonnet-4-20250514";
const DEFAULT_MAX_TOKENS = 2000;

// ─── Client ───────────────────────────────────────────────────
let _client: Anthropic | null = null;

function getClient(): Anthropic {
  if (!_client) {
    _client = new Anthropic();
  }
  return _client;
}

/**
 * Send files (images or PDFs) to Claude Vision API and get a text response.
 * Reusable by all parsers (Uber PDF, Uber screenshot, DiDi, InDrive).
 */
export async function callClaudeVision(request: VisionRequest): Promise<VisionResponse> {
  const client = getClient();

  const contentBlocks: Anthropic.Messages.ContentBlockParam[] = [];

  for (const input of request.inputs) {
    if (input.mediaType === "application/pdf") {
      contentBlocks.push({
        type: "document",
        source: {
          type: "base64",
          media_type: "application/pdf",
          data: input.data,
        },
      });
    } else {
      contentBlocks.push({
        type: "image",
        source: {
          type: "base64",
          media_type: input.mediaType,
          data: input.data,
        },
      });
    }
  }

  contentBlocks.push({
    type: "text",
    text: request.userPrompt,
  });

  const response = await client.messages.create({
    model: DEFAULT_MODEL,
    max_tokens: request.maxTokens ?? DEFAULT_MAX_TOKENS,
    system: request.systemPrompt,
    messages: [
      {
        role: "user",
        content: contentBlocks,
      },
    ],
  });

  const textBlock = response.content.find((block) => block.type === "text");
  if (!textBlock || textBlock.type !== "text") {
    throw new Error("Claude Vision did not return a text response");
  }

  return {
    text: textBlock.text,
    usage: {
      input_tokens: response.usage.input_tokens,
      output_tokens: response.usage.output_tokens,
    },
  };
}

/**
 * Parse a JSON response from Claude, handling markdown code fences.
 * Claude sometimes wraps JSON in ```json ... ``` blocks.
 */
export function extractJsonFromResponse(text: string): unknown {
  let cleaned = text.trim();

  // Strip markdown code fences if present
  const fenceMatch = cleaned.match(/^```(?:json)?\s*\n?([\s\S]*?)\n?\s*```$/);
  if (fenceMatch) {
    cleaned = fenceMatch[1].trim();
  }

  try {
    return JSON.parse(cleaned);
  } catch {
    throw new Error(
      `No se pudo interpretar la respuesta de Claude como JSON. Respuesta: ${cleaned.substring(0, 200)}...`,
    );
  }
}

/** Reset the singleton client (for testing) */
export function _resetClient(): void {
  _client = null;
}
