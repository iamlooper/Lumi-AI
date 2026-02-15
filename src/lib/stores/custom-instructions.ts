import { createStore, produce } from "solid-js/store";
import { db, type CustomInstruction } from "../db";

// --- State ---

const [customInstructions, setCustomInstructions] = createStore<CustomInstruction[]>([]);
const [activeInstructionIds, setActiveInstructionIds] = createStore<string[]>([]);

export { customInstructions, activeInstructionIds };

// --- Init ---

export async function loadCustomInstructions(): Promise<void> {
  const all = await db.customInstructions.toArray();
  setCustomInstructions(all);

  // Activate defaults on load
  const defaults = all.filter((p) => p.isDefault).map((p) => p.id);
  setActiveInstructionIds(defaults);
}

// --- CRUD ---

export async function createCustomInstruction(
  name: string,
  content: string,
  isDefault = false,
): Promise<CustomInstruction> {
  const now = Date.now();
  const instruction: CustomInstruction = {
    id: crypto.randomUUID(),
    name,
    content,
    isDefault,
    createdAt: now,
    updatedAt: now,
  };
  await db.customInstructions.put(instruction);
  setCustomInstructions(produce((d) => d.push(instruction)));
  if (isDefault) {
    setActiveInstructionIds(produce((d) => d.push(instruction.id)));
  }
  return instruction;
}

export async function updateCustomInstruction(
  id: string,
  updates: Partial<Pick<CustomInstruction, "name" | "content" | "isDefault">>,
): Promise<void> {
  const now = Date.now();
  await db.customInstructions.update(id, { ...updates, updatedAt: now });
  setCustomInstructions(produce((d) => {
    const idx = d.findIndex((p) => p.id === id);
    if (idx !== -1) {
      Object.assign(d[idx], updates, { updatedAt: now });
    }
  }));
}

export async function deleteCustomInstruction(id: string): Promise<void> {
  await db.customInstructions.delete(id);
  setCustomInstructions(produce((d) => {
    const idx = d.findIndex((p) => p.id === id);
    if (idx !== -1) d.splice(idx, 1);
  }));
  setActiveInstructionIds(produce((d) => {
    const idx = d.indexOf(id);
    if (idx !== -1) d.splice(idx, 1);
  }));
}

// --- Activation (stacking) ---

export function toggleInstructionActive(id: string): void {
  setActiveInstructionIds(produce((d) => {
    const idx = d.indexOf(id);
    if (idx !== -1) {
      d.splice(idx, 1);
    } else {
      d.push(id);
    }
  }));
}

// --- Derived: combined system instruction text ---

export function getActiveSystemInstruction(): string | undefined {
  const ids = activeInstructionIds;
  if (ids.length === 0) return undefined;
  const texts = ids
    .map((id) => customInstructions.find((p) => p.id === id))
    .filter((p): p is CustomInstruction => !!p)
    .map((p) => p.content.trim())
    .filter((t) => t.length > 0);
  return texts.length > 0 ? texts.join("\n\n") : undefined;
}
