import { getValidAccessToken } from "../auth/token";
import { platformFetch } from "../platform";
import {
  ANTIGRAVITY_ENDPOINT,
  ANTIGRAVITY_DEFAULT_PROJECT_ID,
  LUMI_SYSTEM_INSTRUCTION,
} from "../auth/constants";
import type {
  GeminiContent,
  GeminiGenerationConfig,
  GeminiWrappedRequest,
  GeminiApiResponse,
  GeminiContentPart,
  GeminiTool,
  GeminiGroundingMetadata,
  GeminiInlineData,
} from "./types";

// --- API Fetch ---

async function apiFetch(
  path: string,
  init: RequestInit,
  signal?: AbortSignal,
): Promise<Response> {
  return platformFetch(`${ANTIGRAVITY_ENDPOINT}${path}`, { ...init, signal });
}

// --- Streaming callback types ---

export interface StreamCallbacks {
  onText?: (text: string) => void;
  onThinking?: (text: string, signature?: string) => void;
  onFunctionCall?: (call: { name: string; args: Record<string, unknown>; id?: string }) => void;
  onInlineData?: (data: GeminiInlineData) => void;
  onGroundingMetadata?: (metadata: GeminiGroundingMetadata) => void;
  onUsage?: (usage: { promptTokens: number; outputTokens: number; totalTokens: number; thoughtsTokens?: number }) => void;
  onError?: (error: string) => void;
  onDone?: () => void;
}

// --- SSE Line Parser ---

function parseSseLine(line: string): GeminiApiResponse | null {
  if (!line.startsWith("data: ")) return null;
  const jsonStr = line.slice(6).trim();
  if (!jsonStr || jsonStr === "[DONE]") return null;
  try {
    return JSON.parse(jsonStr) as GeminiApiResponse;
  } catch {
    return null;
  }
}

interface ExtractedResponse {
  parts: GeminiContentPart[];
  groundingMetadata?: GeminiGroundingMetadata;
}

function extractFromResponse(data: GeminiApiResponse): ExtractedResponse {
  const candidates = data.response?.candidates ?? data.candidates;
  if (!candidates || candidates.length === 0) return { parts: [] };
  const firstCandidate = candidates[0];
  return {
    parts: firstCandidate?.content?.parts ?? [],
    groundingMetadata: firstCandidate?.groundingMetadata,
  };
}

// --- System Instruction Builder ---

function buildSystemInstruction(userInstruction?: string): { parts: { text: string }[] } {
  const text = userInstruction
    ? LUMI_SYSTEM_INSTRUCTION + "\n\n" + userInstruction
    : LUMI_SYSTEM_INSTRUCTION;
  return { parts: [{ text }] };
}

// --- Main API Client ---

/**
 * Sends a streaming chat request to the Antigravity API.
 * Calls back with text/thinking chunks as they arrive via SSE.
 */
export async function streamChat(
  model: string,
  contents: GeminiContent[],
  generationConfig: GeminiGenerationConfig | undefined,
  systemInstruction: string | undefined,
  callbacks: StreamCallbacks,
  signal?: AbortSignal,
  tools?: GeminiTool[],
): Promise<void> {
  const { token, projectId } = await getValidAccessToken();

  const requestBody: GeminiWrappedRequest = {
    project: projectId || ANTIGRAVITY_DEFAULT_PROJECT_ID,
    model,
    request: {
      contents,
      ...(generationConfig ? { generationConfig } : {}),
      systemInstruction: buildSystemInstruction(systemInstruction),
      ...(tools && tools.length > 0 ? { tools } : {}),
    },
    userAgent: "antigravity",
    requestId: `agent-${crypto.randomUUID()}`,
    requestType: "agent",
  };

  const resp = await apiFetch(
    "/v1internal:streamGenerateContent?alt=sse",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify(requestBody),
    },
    signal,
  );

  if (!resp.ok) {
    let errorMsg: string;
    try {
      const errBody = await resp.json() as GeminiApiResponse;
      errorMsg = errBody.error?.message ?? `API error ${resp.status}`;
    } catch {
      errorMsg = `API error ${resp.status}: ${await resp.text().catch(() => "Unknown")}`;
    }
    callbacks.onError?.(errorMsg);
    callbacks.onDone?.();
    return;
  }

  // Process SSE stream
  const reader = resp.body?.getReader();
  if (!reader) {
    callbacks.onError?.("No response body");
    callbacks.onDone?.();
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      // Keep the last incomplete line in the buffer
      buffer = lines.pop() ?? "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        const parsed = parseSseLine(trimmed);
        if (!parsed) continue;

        // Check for errors in SSE payload
        if (parsed.error) {
          callbacks.onError?.(parsed.error.message);
          continue;
        }

        const { parts, groundingMetadata } = extractFromResponse(parsed);
        for (const part of parts) {
          if (part.thought && part.text) {
            callbacks.onThinking?.(part.text, part.thoughtSignature);
          } else if (part.text) {
            callbacks.onText?.(part.text);
          } else if (part.inlineData) {
            callbacks.onInlineData?.(part.inlineData);
          } else if (part.functionCall) {
            callbacks.onFunctionCall?.(part.functionCall);
          }
        }

        if (groundingMetadata) {
          callbacks.onGroundingMetadata?.(groundingMetadata);
        }

        // Extract usage metadata
        const usage = parsed.response?.usageMetadata ?? parsed.usageMetadata;
        if (usage) {
          callbacks.onUsage?.({
            promptTokens: usage.promptTokenCount ?? 0,
            outputTokens: usage.candidatesTokenCount ?? 0,
            totalTokens: usage.totalTokenCount ?? 0,
            thoughtsTokens: usage.thoughtsTokenCount,
          });
        }
      }
    }

    // Process any remaining buffer
    if (buffer.trim()) {
      const parsed = parseSseLine(buffer.trim());
      if (parsed && !parsed.error) {
        const { parts, groundingMetadata } = extractFromResponse(parsed);
        for (const part of parts) {
          if (part.thought && part.text) {
            callbacks.onThinking?.(part.text, part.thoughtSignature);
          } else if (part.text) {
            callbacks.onText?.(part.text);
          } else if (part.inlineData) {
            callbacks.onInlineData?.(part.inlineData);
          }
        }
        if (groundingMetadata) {
          callbacks.onGroundingMetadata?.(groundingMetadata);
        }
      }
    }
  } catch (err) {
    if (signal?.aborted) {
      // Aborted by user — not an error
    } else {
      callbacks.onError?.(err instanceof Error ? err.message : String(err));
    }
  } finally {
    reader.releaseLock();
    callbacks.onDone?.();
  }
}

/**
 * Sends a non-streaming chat request to the Antigravity API with endpoint fallback.
 * Returns the full response.
 */
export async function sendChat(
  model: string,
  contents: GeminiContent[],
  generationConfig?: GeminiGenerationConfig,
  systemInstruction?: string,
): Promise<{ parts: GeminiContentPart[]; usage?: { promptTokens: number; outputTokens: number; totalTokens: number } }> {
  const { token, projectId } = await getValidAccessToken();

  const requestBody: GeminiWrappedRequest = {
    project: projectId || ANTIGRAVITY_DEFAULT_PROJECT_ID,
    model,
    request: {
      contents,
      ...(generationConfig ? { generationConfig } : {}),
      systemInstruction: buildSystemInstruction(systemInstruction),
    },
    userAgent: "antigravity",
    requestId: `agent-${crypto.randomUUID()}`,
    requestType: "agent",
  };

  const resp = await apiFetch("/v1internal:generateContent", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestBody),
  });

  if (!resp.ok) {
    let errorMsg: string;
    try {
      const errBody = await resp.json() as GeminiApiResponse;
      errorMsg = errBody.error?.message ?? `API error ${resp.status}`;
    } catch {
      errorMsg = `API error ${resp.status}`;
    }
    throw new Error(errorMsg);
  }

  const data = (await resp.json()) as GeminiApiResponse;
  const { parts } = extractFromResponse(data);
  const usageMeta = data.response?.usageMetadata ?? data.usageMetadata;

  return {
    parts,
    usage: usageMeta
      ? {
          promptTokens: usageMeta.promptTokenCount ?? 0,
          outputTokens: usageMeta.candidatesTokenCount ?? 0,
          totalTokens: usageMeta.totalTokenCount ?? 0,
        }
      : undefined,
  };
}

