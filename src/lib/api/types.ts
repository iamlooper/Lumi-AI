// --- Gemini API Request Types ---

export interface GeminiInlineData {
  mimeType: string;
  data: string; // base64-encoded
}

export interface GeminiFileData {
  mimeType: string;
  fileUri: string;
}

export interface GeminiContentPart {
  text?: string;
  thought?: boolean;
  thoughtSignature?: string;
  inlineData?: GeminiInlineData;
  fileData?: GeminiFileData;
  functionCall?: {
    name: string;
    args: Record<string, unknown>;
    id?: string;
  };
  functionResponse?: {
    name: string;
    id?: string;
    response: unknown;
  };
}

export interface GeminiContent {
  role: "user" | "model";
  parts: GeminiContentPart[];
}

export interface GeminiThinkingConfig {
  includeThoughts?: boolean;
  thinkingBudget?: number;
  thinkingLevel?: "minimal" | "low" | "medium" | "high";
}

export interface GeminiGenerationConfig {
  maxOutputTokens?: number;
  temperature?: number;
  topP?: number;
  topK?: number;
  thinkingConfig?: GeminiThinkingConfig;
  responseModalities?: string[]; // e.g. ["TEXT", "IMAGE"]
}

// --- Tool Types ---

export interface GeminiFunctionDeclaration {
  name: string;
  description: string;
  parameters?: Record<string, unknown>;
}

// NOTE: codeExecution tool is intentionally excluded — the Antigravity gateway
// (cloudcode-pa.googleapis.com) rejects it with HTTP 400.
export type GeminiTool =
  | { functionDeclarations: GeminiFunctionDeclaration[] }
  | { googleSearch: Record<string, never> }
  | { urlContext: Record<string, never> };

export interface GeminiRequest {
  contents: GeminiContent[];
  generationConfig?: GeminiGenerationConfig;
  systemInstruction?: {
    role?: string;
    parts: { text: string }[];
  };
  tools?: GeminiTool[];
}

export interface GeminiWrappedRequest {
  project: string;
  model: string;
  request: GeminiRequest;
  userAgent?: string;
  requestId?: string;
  requestType?: string;
}

// --- Gemini API Response Types ---

export interface GeminiGroundingChunk {
  web?: { uri: string; title: string };
}

export interface GeminiGroundingSupport {
  segment?: { startIndex?: number; endIndex?: number; text?: string };
  groundingChunkIndices?: number[];
  confidenceScores?: number[];
}

export interface GeminiGroundingMetadata {
  webSearchQueries?: string[];
  searchEntryPoint?: { renderedContent?: string };
  groundingChunks?: GeminiGroundingChunk[];
  groundingSupports?: GeminiGroundingSupport[];
}

export interface GeminiResponseCandidate {
  content: {
    role: string;
    parts: GeminiContentPart[];
  };
  finishReason?: string;
  groundingMetadata?: GeminiGroundingMetadata;
}

export interface GeminiUsageMetadata {
  promptTokenCount?: number;
  candidatesTokenCount?: number;
  totalTokenCount?: number;
  thoughtsTokenCount?: number;
}

export interface GeminiApiResponse {
  response?: {
    candidates?: GeminiResponseCandidate[];
    usageMetadata?: GeminiUsageMetadata;
    modelVersion?: string;
    responseId?: string;
  };
  candidates?: GeminiResponseCandidate[];
  usageMetadata?: GeminiUsageMetadata;
  traceId?: string;
  error?: {
    code: number;
    message: string;
    status: string;
  };
}

// --- Model Definitions ---

export interface ModelDefinition {
  id: string;
  name: string;
  contextWindow: number;
  maxOutput: number;
  supportsThinking: boolean;
  alwaysThinking?: boolean;
  defaultThinkingLevel?: string;
  thinkingLevels?: string[];
}

export const AVAILABLE_MODELS: ModelDefinition[] = [
  {
    id: "gemini-2.5-flash",
    name: "Gemini 2.5 Flash",
    contextWindow: 1048576,
    maxOutput: 65536,
    supportsThinking: true,
  },
  {
    id: "gemini-2.5-pro",
    name: "Gemini 2.5 Pro",
    contextWindow: 1048576,
    maxOutput: 65536,
    supportsThinking: true,
  },
  {
    id: "gemini-3-flash-preview",
    name: "Gemini 3 Flash Preview",
    contextWindow: 1048576,
    maxOutput: 65536,
    supportsThinking: true,
    defaultThinkingLevel: "medium",
    thinkingLevels: ["low", "medium", "high"],
  },
  {
    id: "gemini-3-pro-preview",
    name: "Gemini 3 Pro Preview",
    contextWindow: 1048576,
    maxOutput: 65536,
    supportsThinking: true,
    alwaysThinking: true,
    defaultThinkingLevel: "high",
    thinkingLevels: ["low", "high"],
  },
];

export const DEFAULT_MODEL_ID = "gemini-3-pro-preview";
