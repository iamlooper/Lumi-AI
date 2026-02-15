import { createSignal, createMemo } from "solid-js";
import { createStore, produce } from "solid-js/store";
import { db, type Conversation, type Message, type MessagePart } from "../db";
import { streamChat, sendChat, type StreamCallbacks } from "../api/gemini";
import type {
  GeminiContent,
  GeminiContentPart,
  GeminiGenerationConfig,
  GeminiTool,
  GeminiGroundingMetadata,
  GeminiInlineData,
} from "../api/types";
import { DEFAULT_MODEL_ID } from "../api/types";
import { getActiveSystemInstruction } from "./custom-instructions";
import { thinkingEnabled, thinkingBudget, thinkingLevel, isGemini3Model, modelSupportsThinking, modelAlwaysThinking } from "./thinking";

// --- Helpers ---

/** Deep-clone a store value into a plain object safe for IndexedDB's structured clone. */
function toPlain<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj));
}

// --- Attachment Types ---

export interface FileAttachment {
  id: string;
  file: File;
  mimeType: string;
  base64: string;
  preview?: string; // data URL for image thumbnails
}

// --- State ---

const [conversations, setConversations] = createStore<Conversation[]>([]);
const [activeConversationId, setActiveConversationId] = createSignal<string | null>(null);
const [messages, setMessages] = createStore<Message[]>([]);
const [selectedModel, setSelectedModel] = createSignal(DEFAULT_MODEL_ID);

// Per-conversation streaming: which conversations are currently streaming
const [streamingConvIds, setStreamingConvIds] = createStore<Record<string, boolean>>({});

// UI streaming signals — only reflect the currently viewed conversation's stream
const [streamingText, setStreamingText] = createSignal("");
const [streamingThinking, setStreamingThinking] = createSignal("");
const [streamingImages, setStreamingImages] = createStore<GeminiInlineData[]>([]);
const [chatError, setChatError] = createSignal<string | null>(null);

// Tool toggles
const [searchEnabled, setSearchEnabled] = createSignal(false);
const [urlContextEnabled, setUrlContextEnabled] = createSignal(false);

// File attachments pending send
const [pendingAttachments, setPendingAttachments] = createStore<FileAttachment[]>([]);

// Error recovery: restore user input on stream failure
const [recoveryText, setRecoveryText] = createSignal<string | null>(null);
const [recoveryAttachments, setRecoveryAttachments] = createStore<FileAttachment[]>([]);

// Branch navigation state: branchGroupId → { total, activeIndex }
const [branchState, setBranchState] = createStore<Record<string, { total: number; activeIndex: number }>>({});

// Reactive branch context for active streams: convId → branchCtx
const [streamingBranchCtx, setStreamingBranchCtx] = createStore<Record<string, { branchGroupId: string; branchIndex: number } | undefined>>({});

// Background stream buffers keyed by conversationId
const backgroundStreams = new Map<string, {
  abortController: AbortController;
  fullText: string;
  fullThinking: string;
  branchCtx?: { branchGroupId: string; branchIndex: number };
}>();

export {
  conversations,
  activeConversationId,
  messages,
  selectedModel,
  setSelectedModel,
  streamingConvIds,
  streamingText,
  streamingThinking,
  streamingImages,
  chatError,
  searchEnabled,
  setSearchEnabled,
  urlContextEnabled,
  setUrlContextEnabled,
  pendingAttachments,
  branchState,
  recoveryText,
  recoveryAttachments,
  setRecoveryText,
  setRecoveryAttachments,
};

// --- Derived ---

export const activeConversation = createMemo(() => {
  const id = activeConversationId();
  return conversations.find((c) => c.id === id) ?? null;
});

/** Whether the currently viewed conversation is streaming */
export const isStreaming = createMemo(() => {
  const id = activeConversationId();
  return id ? !!streamingConvIds[id] : false;
});

/** Whether we are actively viewing a stream (conversation + branch match) */
export const isViewingActiveStream = createMemo(() => {
  const convId = activeConversationId();
  if (!convId || !streamingConvIds[convId]) return false;
  const ctx = streamingBranchCtx[convId];
  if (!ctx) return true; // no branch context = visible for this conv
  const current = branchState[ctx.branchGroupId];
  return current?.activeIndex === ctx.branchIndex;
});

// --- File Attachment Helpers ---

function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      const base64 = result.split(",")[1] ?? "";
      resolve(base64);
    };
    reader.onerror = () => reject(new Error(`Failed to read file: ${file.name}`));
    reader.readAsDataURL(file);
  });
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(new Error(`Failed to read file: ${file.name}`));
    reader.readAsDataURL(file);
  });
}

export async function addAttachment(file: File): Promise<void> {
  const base64 = await readFileAsBase64(file);
  const isImage = file.type.startsWith("image/");
  let preview: string | undefined;
  if (isImage) {
    preview = await readFileAsDataUrl(file);
  }
  const attachment: FileAttachment = {
    id: crypto.randomUUID(),
    file,
    mimeType: file.type || "application/octet-stream",
    base64,
    preview,
  };
  setPendingAttachments(produce((draft) => draft.push(attachment)));
}

export function removeAttachment(id: string): void {
  setPendingAttachments(produce((draft) => {
    const idx = draft.findIndex((a) => a.id === id);
    if (idx !== -1) draft.splice(idx, 1);
  }));
}

export function clearAttachments(): void {
  setPendingAttachments([]);
}

/**
 * Populate pending attachments from existing message parts (for edit mode).
 * Reconstructs FileAttachment objects from stored inlineData parts.
 */
export function loadAttachmentsFromParts(parts: MessagePart[]): void {
  const atts: FileAttachment[] = [];
  for (const part of parts) {
    if (part.type === "inlineData") {
      const isImage = part.mimeType.startsWith("image/");
      const preview = isImage ? `data:${part.mimeType};base64,${part.data}` : undefined;
      const byteString = atob(part.data);
      const ab = new ArrayBuffer(byteString.length);
      const ia = new Uint8Array(ab);
      for (let i = 0; i < byteString.length; i++) {
        ia[i] = byteString.charCodeAt(i);
      }
      const file = new File([ab], part.label || "file", { type: part.mimeType });
      atts.push({
        id: crypto.randomUUID(),
        file,
        mimeType: part.mimeType,
        base64: part.data,
        preview,
      });
    }
  }
  setPendingAttachments(atts);
}

// --- Actions ---

export async function loadConversations(): Promise<void> {
  const all = await db.conversations.orderBy("updatedAt").reverse().toArray();
  setConversations(all);
}

export async function selectConversation(id: string | null): Promise<void> {
  setActiveConversationId(id);
  setChatError(null);

  if (id) {
    const msgs = await db.messages.where("conversationId").equals(id).sortBy("createdAt");
    setMessages(msgs);

    // Load branch state first so we can check stream branch context
    await loadBranchState(id);

    // Restore streaming UI only if the stream's branch matches the current view
    const bg = backgroundStreams.get(id);
    if (bg) {
      const ctx = bg.branchCtx;
      const branchMatches = !ctx || branchState[ctx.branchGroupId]?.activeIndex === ctx.branchIndex;
      if (branchMatches) {
        setStreamingText(bg.fullText);
        setStreamingThinking(bg.fullThinking);
      } else {
        setStreamingText("");
        setStreamingThinking("");
      }
    } else {
      setStreamingText("");
      setStreamingThinking("");
    }
  } else {
    setMessages([]);
    setStreamingText("");
    setStreamingThinking("");
  }
  setStreamingImages([]);
}

export async function createConversation(title?: string): Promise<string> {
  const now = Date.now();
  const conv: Conversation = {
    id: crypto.randomUUID(),
    title: title || "New Chat",
    model: selectedModel(),
    createdAt: now,
    updatedAt: now,
  };
  await db.conversations.put(conv);
  setConversations(produce((draft) => draft.unshift(conv)));
  await selectConversation(conv.id);
  return conv.id;
}

export async function deleteConversation(id: string): Promise<void> {
  // Stop any active stream for this conversation
  const bg = backgroundStreams.get(id);
  if (bg) {
    bg.abortController.abort();
    backgroundStreams.delete(id);
    setStreamingConvIds(produce((d) => { delete d[id]; }));
    setStreamingBranchCtx(produce((d) => { delete d[id]; }));
  }

  await db.conversations.delete(id);
  await db.messages.where("conversationId").equals(id).delete();
  await db.thoughtSignatures.where("conversationId").equals(id).delete();
  await db.messageBranches.where("conversationId").equals(id).delete();
  setConversations(produce((draft) => {
    const idx = draft.findIndex((c) => c.id === id);
    if (idx !== -1) draft.splice(idx, 1);
  }));
  if (activeConversationId() === id) {
    await selectConversation(null);
  }
}

// --- Conversation Management: Rename, Pin, Archive ---

export async function renameConversation(id: string, title: string): Promise<void> {
  const trimmed = title.trim();
  if (!trimmed) return;
  await db.conversations.update(id, { title: trimmed, updatedAt: Date.now() });
  setConversations(produce((draft) => {
    const conv = draft.find((c) => c.id === id);
    if (conv) { conv.title = trimmed; conv.updatedAt = Date.now(); }
  }));
}

export async function togglePinConversation(id: string): Promise<void> {
  const conv = conversations.find((c) => c.id === id);
  if (!conv) return;
  const pinned = conv.pinned ? 0 : Date.now();
  await db.conversations.update(id, { pinned });
  setConversations(produce((draft) => {
    const c = draft.find((c) => c.id === id);
    if (c) c.pinned = pinned;
  }));
}

export async function toggleArchiveConversation(id: string): Promise<void> {
  const conv = conversations.find((c) => c.id === id);
  if (!conv) return;
  const archived = !conv.archived;
  // When archiving, also unpin to avoid pinned+archived conflict
  const updates: Partial<Conversation> = { archived };
  if (archived && conv.pinned) updates.pinned = 0;
  await db.conversations.update(id, updates);
  setConversations(produce((draft) => {
    const c = draft.find((c) => c.id === id);
    if (c) {
      c.archived = archived;
      if (archived && c.pinned) c.pinned = 0;
    }
  }));
  if (archived && activeConversationId() === id) {
    await selectConversation(null);
  }
}

// --- Streaming Control ---

export function stopStreaming(): void {
  const convId = activeConversationId();
  if (!convId) return;
  // Only stop if we are viewing the active stream
  if (!isViewingActiveStream()) return;
  const bg = backgroundStreams.get(convId);
  if (bg) {
    bg.abortController.abort();
    backgroundStreams.delete(convId);
  }
  setStreamingConvIds(produce((d) => { delete d[convId]; }));
  setStreamingBranchCtx(produce((d) => { delete d[convId]; }));
  setStreamingText("");
  setStreamingThinking("");
  setStreamingImages([]);
}

// --- Branch Operations ---

async function loadBranchState(conversationId: string): Promise<void> {
  const branches = await db.messageBranches.where("conversationId").equals(conversationId).toArray();
  const state: Record<string, { total: number; activeIndex: number }> = {};
  for (const b of branches) {
    if (!state[b.branchGroupId]) {
      state[b.branchGroupId] = { total: 1, activeIndex: 0 };
    }
    // total = number of branch records + 1 (active). activeIndex = max + 1
    const current = state[b.branchGroupId];
    current.total = Math.max(current.total, b.branchIndex + 2); // +1 for 0-indexed, +1 for active
    current.activeIndex = current.total - 1; // active is always the last one
  }
  setBranchState(state);
}

export async function navigateBranch(branchGroupId: string, targetIndex: number): Promise<void> {
  const convId = activeConversationId();
  if (!convId) return;
  const state = branchState[branchGroupId];
  if (!state || targetIndex === state.activeIndex || targetIndex < 0 || targetIndex >= state.total) return;

  // Clear streaming UI when switching branches (stream continues in background)
  const bg = backgroundStreams.get(convId);
  if (bg) {
    setStreamingText("");
    setStreamingThinking("");
    setStreamingImages([]);
  }

  // Find the branch point user message
  const branchPointMsg = messages.find((m) => m.branchGroupId === branchGroupId);
  if (!branchPointMsg) return;
  const branchPointIdx = messages.findIndex((m) => m.id === branchPointMsg.id);
  if (branchPointIdx === -1) return;

  // Save current active messages from branch point onwards as a branch record
  const currentSnapshot: Message[] = toPlain(messages.slice(branchPointIdx));
  const currentBranchIndex = state.activeIndex;

  // Check if a branch record already exists for the current active index
  const existingActive = await db.messageBranches
    .where("branchGroupId").equals(branchGroupId)
    .filter((b) => b.branchIndex === currentBranchIndex)
    .first();

  if (!existingActive) {
    // Save current as a branch record
    await db.messageBranches.put({
      id: crypto.randomUUID(),
      conversationId: convId,
      branchGroupId,
      branchIndex: currentBranchIndex,
      snapshot: currentSnapshot,
      createdAt: Date.now(),
    });
  } else {
    // Update existing branch snapshot
    await db.messageBranches.update(existingActive.id, { snapshot: currentSnapshot });
  }

  // Load target branch snapshot
  const targetBranch = await db.messageBranches
    .where("branchGroupId").equals(branchGroupId)
    .filter((b) => b.branchIndex === targetIndex)
    .first();

  if (!targetBranch) return;

  // Remove current messages from branch point onwards from DB
  const toRemove = messages.slice(branchPointIdx);
  for (const msg of toRemove) {
    await db.messages.delete(msg.id);
  }

  // Insert target branch's messages into DB
  for (const msg of targetBranch.snapshot) {
    await db.messages.put(msg);
  }

  // Reload messages
  const allMsgs = await db.messages.where("conversationId").equals(convId).sortBy("createdAt");
  setMessages(allMsgs);

  // Update active index
  setBranchState(produce((d) => {
    if (d[branchGroupId]) d[branchGroupId].activeIndex = targetIndex;
  }));

  // If we navigated back to the branch that's streaming, restore streaming UI
  const bgAfter = backgroundStreams.get(convId);
  if (bgAfter?.branchCtx?.branchGroupId === branchGroupId && bgAfter.branchCtx.branchIndex === targetIndex) {
    setStreamingText(bgAfter.fullText);
    setStreamingThinking(bgAfter.fullThinking);
  }
}

export async function branchToNewChat(messageId: string): Promise<void> {
  const convId = activeConversationId();
  if (!convId) return;

  const idx = messages.findIndex((m) => m.id === messageId);
  if (idx === -1) return;

  const messagesToCopy = messages.slice(0, idx + 1);

  const now = Date.now();
  const originalConv = activeConversation();
  const newConvId = crypto.randomUUID();
  const newConv: Conversation = {
    id: newConvId,
    title: `Branch: ${originalConv?.title || "Chat"}`,
    model: selectedModel(),
    createdAt: now,
    updatedAt: now,
  };
  await db.conversations.put(newConv);

  // Copy messages with new IDs, stripping branchGroupId so they're clean
  const newMsgs: Message[] = [];
  for (const msg of messagesToCopy) {
    const newMsg: Message = {
      id: crypto.randomUUID(),
      conversationId: newConvId,
      role: msg.role,
      parts: toPlain(msg.parts) as MessagePart[],
      createdAt: msg.createdAt,
    };
    await db.messages.put(newMsg);
    newMsgs.push(newMsg);
  }

  // Update UI state
  setConversations(produce((draft) => draft.unshift(newConv)));
  setActiveConversationId(newConvId);
  setMessages(newMsgs);
  setBranchState({});
  setChatError(null);
  setStreamingText("");
  setStreamingThinking("");
  setStreamingImages([]);
}

// --- Retry / Edit ---

export async function retryMessage(): Promise<void> {
  const convId = activeConversationId();
  if (!convId || streamingConvIds[convId]) return;

  const allMsgs = [...messages];
  if (allMsgs.length < 2) return;

  const lastMsg = allMsgs[allMsgs.length - 1];
  if (lastMsg.role !== "model") return;

  // Delete the last model message from DB and state
  await db.messages.delete(lastMsg.id);
  setMessages(produce((draft) => draft.pop()));

  // Determine branch context for retry
  const prevUserMsg = allMsgs[allMsgs.length - 2];
  let retryBranchCtx: { branchGroupId: string; branchIndex: number } | undefined;
  if (prevUserMsg?.branchGroupId) {
    const currentBranch = branchState[prevUserMsg.branchGroupId];
    if (currentBranch) {
      retryBranchCtx = { branchGroupId: prevUserMsg.branchGroupId, branchIndex: currentBranch.activeIndex };
    }
  }

  // Rebuild and stream
  const remainingMsgs = await db.messages.where("conversationId").equals(convId).sortBy("createdAt");
  const contents = buildContentsFromMessages(remainingMsgs);
  const userText = prevUserMsg?.parts.find((p) => p.type === "text")?.text ?? "";
  await startStream(convId, contents, userText, remainingMsgs.length, retryBranchCtx);
}

/**
 * Edits a user message: saves old branch, creates new message, streams response.
 */
export async function editMessage(messageId: string, newText: string, newAttachments?: FileAttachment[]): Promise<void> {
  const convId = activeConversationId();
  if (!convId || streamingConvIds[convId]) return;

  const idx = messages.findIndex((m) => m.id === messageId);
  if (idx === -1) return;

  const originalMsg = messages[idx];

  // Assign branchGroupId if not already set
  let branchGroupId = originalMsg.branchGroupId;
  if (!branchGroupId) {
    branchGroupId = crypto.randomUUID();
    // Update the original message in DB to have the branchGroupId
    await db.messages.update(messageId, { branchGroupId });
    setMessages(produce((draft) => { draft[idx].branchGroupId = branchGroupId; }));
  }

  // Save current messages from branch point onwards as a branch snapshot
  const currentSnapshot: Message[] = toPlain(messages.slice(idx));
  const currentBranchCount = branchState[branchGroupId]?.total ?? 1;
  const currentActiveIndex = branchState[branchGroupId]?.activeIndex ?? 0;

  // Save as branch record if this is the first edit (save original as branch 0)
  if (currentBranchCount <= 1) {
    await db.messageBranches.put({
      id: crypto.randomUUID(),
      conversationId: convId,
      branchGroupId,
      branchIndex: 0,
      snapshot: currentSnapshot,
      createdAt: Date.now(),
    });
  } else {
    // Save current active as its branch index
    const existingActive = await db.messageBranches
      .where("branchGroupId").equals(branchGroupId)
      .filter((b) => b.branchIndex === currentActiveIndex)
      .first();
    if (existingActive) {
      await db.messageBranches.update(existingActive.id, { snapshot: currentSnapshot });
    } else {
      await db.messageBranches.put({
        id: crypto.randomUUID(),
        conversationId: convId,
        branchGroupId,
        branchIndex: currentActiveIndex,
        snapshot: currentSnapshot,
        createdAt: Date.now(),
      });
    }
  }

  // Delete messages from branch point onwards from DB
  const toDelete = messages.slice(idx);
  for (const msg of toDelete) {
    await db.messages.delete(msg.id);
  }
  setMessages(produce((draft) => draft.splice(idx)));

  // Build new user message parts
  const userParts: MessagePart[] = [];

  // Handle attachments:
  // - undefined = keep original non-text parts
  // - FileAttachment[] (even empty) = replace with these attachments
  if (newAttachments !== undefined) {
    for (const att of newAttachments) {
      userParts.push({ type: "inlineData", mimeType: att.mimeType, data: att.base64, label: att.file.name });
    }
  } else {
    for (const part of toPlain(originalMsg.parts)) {
      if (part.type !== "text") userParts.push(part);
    }
  }

  if (newText.trim()) {
    userParts.push({ type: "text", text: newText });
  }

  // Create new user message with same branchGroupId
  const newBranchIndex = currentBranchCount <= 1 ? 1 : currentBranchCount;
  const userMsg: Message = {
    id: crypto.randomUUID(),
    conversationId: convId,
    role: "user",
    parts: userParts,
    createdAt: Date.now(),
    branchGroupId,
  };
  await db.messages.put(userMsg);

  // Update branch state BEFORE pushing the message so the branch navigator
  // renders immediately when the new message appears in <For>.
  setBranchState(produce((d) => {
    d[branchGroupId!] = { total: newBranchIndex + 1, activeIndex: newBranchIndex };
  }));

  setMessages(produce((draft) => draft.push(userMsg)));

  // Stream response
  const allMsgs = await db.messages.where("conversationId").equals(convId).sortBy("createdAt");
  const contents = buildContentsFromMessages(allMsgs);
  const editAttachments = newAttachments ? [...newAttachments] : [];

  await startStream(convId, contents, newText, allMsgs.length, {
    branchGroupId: branchGroupId!,
    branchIndex: newBranchIndex,
  }, async () => {
    // Remove the new user message
    await db.messages.delete(userMsg.id);

    // Restore the previous branch's messages from snapshot
    const prevBranch = await db.messageBranches
      .where("branchGroupId").equals(branchGroupId!)
      .filter((b) => b.branchIndex === currentActiveIndex)
      .first();

    if (prevBranch) {
      // Remove any current messages from branch point onwards
      const currentMsgs = await db.messages.where("conversationId").equals(convId!).sortBy("createdAt");
      const branchPointIdx = currentMsgs.findIndex((m) => m.branchGroupId === branchGroupId);
      if (branchPointIdx !== -1) {
        for (const m of currentMsgs.slice(branchPointIdx)) {
          await db.messages.delete(m.id);
        }
      }

      // Re-insert the previous snapshot
      for (const m of prevBranch.snapshot) {
        await db.messages.put(m);
      }
    }

    // Reload messages and revert branch state
    const restored = await db.messages.where("conversationId").equals(convId!).sortBy("createdAt");
    setMessages(restored);
    setBranchState(produce((d) => {
      d[branchGroupId!] = { total: newBranchIndex + 1, activeIndex: currentActiveIndex };
    }));

    // Set recovery data so UI can repopulate input
    setRecoveryText(newText);
    setRecoveryAttachments(editAttachments);
  });
}

// --- Conversation History → GeminiContent[] ---

function buildContentsFromMessages(msgs: Message[]): GeminiContent[] {
  return msgs.map((msg) => ({
    role: msg.role,
    parts: msg.parts
      .map((p): GeminiContentPart | null => {
        switch (p.type) {
          case "text":
            return { text: p.text };
          case "thinking":
            return {
              thought: true,
              text: p.text,
              ...(p.thoughtSignature ? { thoughtSignature: p.thoughtSignature } : {}),
            };
          case "inlineData":
            return { inlineData: { mimeType: p.mimeType, data: p.data } };
          case "functionCall":
            return { functionCall: { name: p.name, args: p.args, ...(p.id ? { id: p.id } : {}) } };
          case "functionResponse":
            return { functionResponse: { name: p.name, response: p.response, ...(p.id ? { id: p.id } : {}) } };
          case "searchGrounding":
            return null; // Not sent back to API
          default:
            return null;
        }
      })
      .filter((p): p is GeminiContentPart => p !== null && (p.text !== "" || !!p.inlineData || !!p.functionCall || !!p.functionResponse)),
  })).filter((c) => c.parts.length > 0);
}

// --- Build active tools ---

function buildActiveTools(): GeminiTool[] {
  const tools: GeminiTool[] = [];
  if (searchEnabled()) {
    tools.push({ googleSearch: {} as Record<string, never> });
  }
  if (urlContextEnabled()) {
    tools.push({ urlContext: {} as Record<string, never> });
  }
  return tools;
}

// --- Build generation config ---

function buildGenerationConfig(model: string): GeminiGenerationConfig | undefined {
  if (!modelSupportsThinking(model)) return undefined;

  const enabled = thinkingEnabled();

  if (isGemini3Model(model)) {
    // Gemini 3 Pro: always thinks — ignore the toggle
    if (modelAlwaysThinking(model)) {
      return {
        maxOutputTokens: 65536,
        thinkingConfig: {
          includeThoughts: true,
          thinkingLevel: thinkingLevel(),
        },
      };
    }
    // Gemini 3 Flash: "minimal" when thinking is disabled
    if (!enabled) {
      return {
        maxOutputTokens: 65536,
        thinkingConfig: { thinkingLevel: "minimal" },
      };
    }
    return {
      maxOutputTokens: 65536,
      thinkingConfig: {
        includeThoughts: true,
        thinkingLevel: thinkingLevel(),
      },
    };
  }

  if (!enabled) {
    return { maxOutputTokens: 65536 };
  }

  // Gemini 2.5 and others: numeric budget
  const budget = thinkingBudget();
  return {
    maxOutputTokens: Math.max(65536, budget + 1024),
    thinkingConfig: {
      includeThoughts: true,
      thinkingBudget: budget,
    },
  };
}

// --- Thought Signature Cache ---

async function cacheThoughtSignature(
  conversationId: string,
  model: string,
  signature: string,
  turnIndex: number,
): Promise<void> {
  const id = `${conversationId}:${turnIndex}`;
  await db.thoughtSignatures.put({
    id,
    conversationId,
    model,
    signature,
    createdAt: Date.now(),
  });
}

// --- Title Generation ---

const TITLE_MODEL = "gemini-2.5-flash-lite";

async function generateTitle(userText: string, modelText: string, convId: string): Promise<void> {
  try {
    const contents: GeminiContent[] = [
      {
        role: "user",
        parts: [
          {
            text: `Generate a very short title (max 6 words) for this conversation. Reply with ONLY the title, nothing else.\n\nUser: ${userText.slice(0, 500)}\nAssistant: ${modelText.slice(0, 500)}`,
          },
        ],
      },
    ];

    const result = await sendChat(TITLE_MODEL, contents, { maxOutputTokens: 30 });
    const title = result.parts
      .filter((p) => p.text)
      .map((p) => p.text!)
      .join("")
      .trim()
      .replace(/^["']|["']$/g, "");

    if (title && title.length > 0 && title.length <= 80) {
      await db.conversations.update(convId, { title, updatedAt: Date.now() });
      setConversations(produce((draft) => {
        const conv = draft.find((c) => c.id === convId);
        if (conv) {
          conv.title = title;
          conv.updatedAt = Date.now();
        }
      }));
    }
  } catch (err) {
    // Title generation failed — fallback title is already set
  }
}

// --- Core Streaming Engine ---

/**
 * Starts a streaming response for a conversation. Supports background streaming:
 * if the user navigates away, the stream continues and saves on completion.
 */
async function startStream(
  convId: string,
  contents: GeminiContent[],
  userText: string,
  turnCount: number,
  branchCtx?: { branchGroupId: string; branchIndex: number },
  onErrorRecovery?: () => Promise<void>,
): Promise<void> {
  setChatError(null);
  const model = selectedModel();
  const generationConfig = buildGenerationConfig(model);
  const tools = buildActiveTools();
  let hadError = false;

  const controller = new AbortController();
  const isViewing = () => {
    if (activeConversationId() !== convId) return false;
    if (!branchCtx) return true;
    const current = branchState[branchCtx.branchGroupId];
    return current?.activeIndex === branchCtx.branchIndex;
  };

  let fullText = "";
  let fullThinking = "";
  let lastThoughtSignature: string | undefined;
  const collectedParts: MessagePart[] = [];
  const collectedImages: GeminiInlineData[] = [];
  let groundingResult: { queries: string[]; sources: { uri: string; title: string }[] } | null = null;

  // Register background stream
  backgroundStreams.set(convId, { abortController: controller, fullText: "", fullThinking: "", branchCtx });
  setStreamingConvIds(produce((d) => { d[convId] = true; }));
  setStreamingBranchCtx(produce((d) => { d[convId] = branchCtx; }));

  if (isViewing()) {
    setStreamingText("");
    setStreamingThinking("");
    setStreamingImages([]);
  }

  const callbacks: StreamCallbacks = {
    onText: (chunk) => {
      fullText += chunk;
      const bg = backgroundStreams.get(convId);
      if (bg) bg.fullText = fullText;
      if (isViewing()) setStreamingText(fullText);
    },
    onThinking: (chunk, signature) => {
      fullThinking += chunk;
      if (signature) lastThoughtSignature = signature;
      const bg = backgroundStreams.get(convId);
      if (bg) bg.fullThinking = fullThinking;
      if (isViewing()) setStreamingThinking(fullThinking);
    },
    onInlineData: (data) => {
      collectedImages.push(data);
      if (isViewing()) setStreamingImages(produce((draft) => draft.push(data)));
    },
    onGroundingMetadata: (metadata: GeminiGroundingMetadata) => {
      const queries = metadata.webSearchQueries ?? [];
      const sources = (metadata.groundingChunks ?? [])
        .filter((c) => c.web)
        .map((c) => ({ uri: c.web!.uri, title: c.web!.title }));
      if (queries.length > 0 || sources.length > 0) {
        groundingResult = { queries, sources };
      }
    },
    onFunctionCall: (call) => {
      collectedParts.push({
        type: "functionCall",
        name: call.name,
        args: call.args,
        ...(call.id ? { id: call.id } : {}),
      });
    },
    onError: (error) => {
      hadError = true;
      if (isViewing()) setChatError(error);
    },
    onDone: async () => {
      backgroundStreams.delete(convId);
      setStreamingConvIds(produce((d) => { delete d[convId]; }));
      setStreamingBranchCtx(produce((d) => { delete d[convId]; }));

      const parts: MessagePart[] = [];

      if (fullThinking) {
        parts.push({
          type: "thinking",
          text: fullThinking,
          ...(lastThoughtSignature ? { thoughtSignature: lastThoughtSignature } : {}),
        });
        if (lastThoughtSignature) {
          await cacheThoughtSignature(convId, model, lastThoughtSignature, turnCount);
        }
      }

      parts.push(...collectedParts);

      for (const img of collectedImages) {
        parts.push({ type: "inlineData", mimeType: img.mimeType, data: img.data });
      }

      if (fullText) {
        parts.push({ type: "text", text: fullText });
      }

      if (groundingResult && groundingResult.sources.length > 0) {
        parts.push({
          type: "searchGrounding",
          queries: groundingResult.queries,
          sources: groundingResult.sources,
        });
      }

      if (parts.length > 0) {
        const assistantMsg: Message = {
          id: crypto.randomUUID(),
          conversationId: convId,
          role: "model",
          parts,
          createdAt: Date.now(),
        };

        if (isViewing()) {
          // Normal: save to DB and push to UI
          await db.messages.put(assistantMsg);
          setMessages(produce((draft) => draft.push(assistantMsg)));
        } else if (branchCtx) {
          // Stream completed while viewing a different branch.
          // Append model response to the branch snapshot in messageBranches.
          const branchRecord = await db.messageBranches
            .where("branchGroupId").equals(branchCtx.branchGroupId)
            .filter((b) => b.branchIndex === branchCtx.branchIndex)
            .first();
          if (branchRecord) {
            await db.messageBranches.update(branchRecord.id, {
              snapshot: [...branchRecord.snapshot, assistantMsg],
            });
          }
        } else {
          // No branch context, not viewing — still save to DB (background conv)
          await db.messages.put(assistantMsg);
        }

        await db.conversations.update(convId, { updatedAt: Date.now() });
        setConversations(produce((draft) => {
          const conv = draft.find((c) => c.id === convId);
          if (conv) conv.updatedAt = Date.now();
        }));

        // Title generation on first exchange
        if (turnCount <= 1 && fullText) {
          generateTitle(userText, fullText, convId);
        }
      }

      // Error recovery: if no model response was produced and an error occurred,
      // clean up the orphaned user message and restore input state.
      if (parts.length === 0 && hadError && onErrorRecovery && isViewing()) {
        await onErrorRecovery();
      }

      if (isViewing()) {
        setStreamingText("");
        setStreamingThinking("");
        setStreamingImages([]);
      }
    },
  };

  try {
    await streamChat(model, contents, generationConfig, getActiveSystemInstruction(), callbacks, controller.signal, tools);
  } catch (err) {
    // Network / fetch error — streamChat threw before callbacks fired
    backgroundStreams.delete(convId);
    setStreamingConvIds(produce((d) => { delete d[convId]; }));
    setStreamingBranchCtx(produce((d) => { delete d[convId]; }));
    if (isViewing()) {
      setChatError(err instanceof Error ? err.message : "Network error");
      setStreamingText("");
      setStreamingThinking("");
      setStreamingImages([]);
      if (onErrorRecovery) await onErrorRecovery();
    }
  }
}

// --- Main Send Message ---

export async function sendMessage(text: string): Promise<void> {
  setChatError(null);

  let convId = activeConversationId();

  // Allow sending even if another conversation is streaming (background streaming)
  // But block if THIS conversation is already streaming
  if (convId && streamingConvIds[convId]) return;

  if (!convId) {
    convId = await createConversation(text.slice(0, 60));
  }

  // Build user message parts
  const userParts: MessagePart[] = [];

  const attachments = [...pendingAttachments];
  for (const att of attachments) {
    userParts.push({
      type: "inlineData",
      mimeType: att.mimeType,
      data: att.base64,
      label: att.file.name,
    });
  }

  if (text.trim()) {
    userParts.push({ type: "text", text });
  }

  clearAttachments();

  const userMsg: Message = {
    id: crypto.randomUUID(),
    conversationId: convId,
    role: "user",
    parts: userParts,
    createdAt: Date.now(),
  };
  await db.messages.put(userMsg);
  setMessages(produce((draft) => draft.push(userMsg)));

  const allMsgs = await db.messages.where("conversationId").equals(convId).sortBy("createdAt");
  const contents = buildContentsFromMessages(allMsgs);

  await startStream(convId, contents, text, allMsgs.length, undefined, async () => {
    // Remove orphaned user message from DB and store
    await db.messages.delete(userMsg.id);
    setMessages(produce((draft) => {
      const idx = draft.findIndex((m) => m.id === userMsg.id);
      if (idx !== -1) draft.splice(idx, 1);
    }));

    // Set recovery data so UI can repopulate input
    setRecoveryText(text);
    setRecoveryAttachments(attachments);
  });
}

// --- Session Recovery ---

export async function recoverSession(conversationId: string): Promise<void> {
  const conv = await db.conversations.get(conversationId);
  if (!conv) {
    throw new Error(`Conversation ${conversationId} not found`);
  }
  await selectConversation(conversationId);
  setChatError(null);
}
