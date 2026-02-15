import Dexie, { type EntityTable } from "dexie";

// --- Schema Definitions ---

export interface AuthAccount {
  id: string;
  email: string;
  refreshToken: string;
  accessToken: string;
  tokenExpiry: number;
  projectId: string;
  createdAt: number;
  updatedAt: number;
}

export interface Conversation {
  id: string;
  title: string;
  model: string;
  createdAt: number;
  updatedAt: number;
  pinned?: number; // timestamp when pinned, 0 or undefined = not pinned
  archived?: boolean;
}

export interface Message {
  id: string;
  conversationId: string;
  role: "user" | "model";
  parts: MessagePart[];
  createdAt: number;
  branchGroupId?: string; // Shared ID across all versions of this message at a branch point
}

export interface MessageBranch {
  id: string;
  conversationId: string;
  branchGroupId: string; // Links to the branchGroupId on user messages at the branch point
  branchIndex: number;
  snapshot: Message[]; // Complete messages from branch point to end of conversation
  createdAt: number;
}

export type MessagePart =
  | { type: "text"; text: string }
  | { type: "thinking"; text: string; thoughtSignature?: string }
  | { type: "inlineData"; mimeType: string; data: string; label?: string }
  | { type: "functionCall"; name: string; args: Record<string, unknown>; id?: string }
  | { type: "functionResponse"; name: string; id?: string; response: unknown }
  | { type: "searchGrounding"; queries: string[]; sources: { uri: string; title: string }[] };

export interface AppSettings {
  key: string;
  value: unknown;
}

export interface CustomInstruction {
  id: string;
  name: string;
  content: string;
  isDefault: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface ThoughtSignatureEntry {
  id: string; // conversationId:turnIndex
  conversationId: string;
  model: string;
  signature: string;
  createdAt: number;
}

// --- Database ---

class LumiDB extends Dexie {
  auth!: EntityTable<AuthAccount, "id">;
  conversations!: EntityTable<Conversation, "id">;
  messages!: EntityTable<Message, "id">;
  settings!: EntityTable<AppSettings, "key">;
  thoughtSignatures!: EntityTable<ThoughtSignatureEntry, "id">;
  messageBranches!: EntityTable<MessageBranch, "id">;
  customInstructions!: EntityTable<CustomInstruction, "id">;

  constructor() {
    super("LumiAI");

    this.version(1).stores({
      auth: "id, email",
      conversations: "id, updatedAt",
      messages: "id, conversationId, createdAt",
      settings: "key",
    });

    this.version(2).stores({
      auth: "id, email",
      conversations: "id, updatedAt",
      messages: "id, conversationId, createdAt",
      settings: "key",
      thoughtSignatures: "id, conversationId, model",
    });

    this.version(3).stores({
      auth: "id, email",
      conversations: "id, updatedAt",
      messages: "id, conversationId, createdAt, branchGroupId",
      settings: "key",
      thoughtSignatures: "id, conversationId, model",
      messageBranches: "id, conversationId, branchGroupId",
    });

    this.version(4).stores({
      auth: "id, email",
      conversations: "id, updatedAt",
      messages: "id, conversationId, createdAt, branchGroupId",
      settings: "key",
      thoughtSignatures: "id, conversationId, model",
      messageBranches: "id, conversationId, branchGroupId",
      customInstructions: "id",
    });
  }
}

export const db = new LumiDB();

/**
 * Pre-opens the database, handling schema conflicts gracefully.
 * If a previous DB version has incompatible primary keys, the database
 * is deleted and recreated from scratch. Must be called before any DB operations.
 */
export async function initDB(): Promise<void> {
  try {
    await db.open();
  } catch (err: unknown) {
    const isUpgradeError =
      err instanceof Error &&
      (err.name === "UpgradeError" || err.message.includes("changing primary key"));
    if (isUpgradeError) {
      db.close();
      await db.delete();
      await db.open();
    } else {
      throw err;
    }
  }
}
