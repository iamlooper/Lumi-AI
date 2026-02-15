import { createSignal } from "solid-js";
import { AVAILABLE_MODELS } from "../api/types";

// --- State ---

const [thinkingEnabled, setThinkingEnabled] = createSignal(true);
const [thinkingBudget, setThinkingBudget] = createSignal(8192);
const [thinkingLevel, setThinkingLevel] = createSignal<"low" | "medium" | "high">("high");

export {
  thinkingEnabled, setThinkingEnabled,
  thinkingBudget, setThinkingBudget,
  thinkingLevel, setThinkingLevel,
};

// --- Helpers ---

export function isGemini3Model(modelId: string): boolean {
  return modelId.includes("gemini-3");
}

export function modelSupportsThinking(modelId: string): boolean {
  return AVAILABLE_MODELS.find((m) => m.id === modelId)?.supportsThinking ?? false;
}

export function modelAlwaysThinking(modelId: string): boolean {
  return AVAILABLE_MODELS.find((m) => m.id === modelId)?.alwaysThinking ?? false;
}

export function getModelThinkingLevels(modelId: string): string[] {
  const model = AVAILABLE_MODELS.find((m) => m.id === modelId);
  return model?.thinkingLevels ?? ["low", "medium", "high"];
}

/**
 * Clamps the current thinking level to a valid value for the given model.
 * If the current level isn't in the model's supported levels, resets to
 * the model's default (or the highest available level as a fallback).
 */
export function clampThinkingLevelForModel(modelId: string): void {
  const model = AVAILABLE_MODELS.find((m) => m.id === modelId);
  const levels = model?.thinkingLevels;
  if (!levels || levels.length === 0) return;
  if (levels.includes(thinkingLevel())) return;
  const fallback = (model?.defaultThinkingLevel ?? levels[levels.length - 1]) as "low" | "medium" | "high";
  setThinkingLevel(fallback);
}
