import { For, Show, createEffect, createSignal } from "solid-js";
import {
  messages,
  activeConversationId,
  isStreaming,
  isViewingActiveStream,
  streamingText,
  streamingThinking,
  streamingImages,
  chatError,
  sendMessage,
  stopStreaming,
  selectConversation,
  activeConversation,
  retryMessage,
  editMessage,
  branchToNewChat,
  navigateBranch,
  branchState,
  selectedModel,
  setSelectedModel,
  searchEnabled,
  setSearchEnabled,
  urlContextEnabled,
  setUrlContextEnabled,
  pendingAttachments,
  addAttachment,
  removeAttachment,
  clearAttachments,
  loadAttachmentsFromParts,
  recoveryText,
  recoveryAttachments,
  setRecoveryText,
  setRecoveryAttachments,
} from "../lib/stores/chat";
import type { Message, MessagePart } from "../lib/db";
import { AVAILABLE_MODELS } from "../lib/api/types";
import { renderMarkdown } from "../lib/markdown";
import { account } from "../lib/stores/auth";
import {
  customInstructions,
  activeInstructionIds,
  createCustomInstruction,
  updateCustomInstruction,
  deleteCustomInstruction,
  toggleInstructionActive,
} from "../lib/stores/custom-instructions";
import {
  thinkingEnabled, setThinkingEnabled,
  thinkingBudget, setThinkingBudget,
  thinkingLevel, setThinkingLevel,
  isGemini3Model, modelSupportsThinking,
  modelAlwaysThinking, getModelThinkingLevels,
  clampThinkingLevelForModel,
} from "../lib/stores/thinking";
import { sidebarOpen, setSidebarOpen } from "../App";
import "./ChatView.css";

// --- Greetings & Suggestions (imported from constants to keep component lean) ---

import {
  SUGGESTIONS,
  MORNING_GREETINGS,
  AFTERNOON_GREETINGS,
  EVENING_GREETINGS,
  NIGHT_GREETINGS,
  SUBTITLES,
} from "./chat-constants";

function pickRandom<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

function pickN<T>(arr: T[], n: number): T[] {
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}

function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 5) return pickRandom(NIGHT_GREETINGS);
  if (hour < 12) return pickRandom(MORNING_GREETINGS);
  if (hour < 17) return pickRandom(AFTERNOON_GREETINGS);
  if (hour < 22) return pickRandom(EVENING_GREETINGS);
  return pickRandom(NIGHT_GREETINGS);
}

function getSubtitle(): string {
  return pickRandom(SUBTITLES);
}

export default function ChatView() {
  let messagesEndRef: HTMLDivElement | undefined;
  let inputRef: HTMLTextAreaElement | undefined;
  let fileInputRef: HTMLInputElement | undefined;

  const [toolsMenuOpen, setToolsMenuOpen] = createSignal(false);
  const [modelMenuOpen, setModelMenuOpen] = createSignal(false);
  const [instructionsMenuOpen, setInstructionsMenuOpen] = createSignal(false);
  const [thinkingMenuOpen, setThinkingMenuOpen] = createSignal(false);

  // Custom instruction editor state
  const [editingInstruction, setEditingInstruction] = createSignal<{ id?: string; name: string; content: string } | null>(null);

  // Edit mode state: message id being edited, text populates the main input
  const [editingMessageId, setEditingMessageId] = createSignal<string | null>(null);

  // Stable greeting/subtitle/suggestions per mount
  const greeting = getGreeting();
  const subtitle = getSubtitle();
  const suggestions = pickN(SUGGESTIONS, 4);

  const scrollToBottom = () => {
    messagesEndRef?.scrollIntoView({ behavior: "smooth" });
  };

  createEffect(() => {
    messages.length;
    streamingText();
    scrollToBottom();
  });

  // Repopulate input when error recovery data is available
  createEffect(() => {
    const text = recoveryText();
    if (text !== null) {
      if (inputRef) {
        inputRef.value = text;
        inputRef.style.height = "auto";
        inputRef.style.height = Math.min(inputRef.scrollHeight, 200) + "px";
      }
      // Restore attachments
      for (const att of recoveryAttachments) {
        addAttachment(att.file);
      }
      // Clear recovery data
      setRecoveryText(null);
      setRecoveryAttachments([]);
    }
  });

  const doSubmit = async () => {
    const input = inputRef;
    const value = input?.value?.trim();
    if (!value && pendingAttachments.length === 0) return;
    if (isStreaming()) return;

    const editId = editingMessageId();
    const text = value || "";
    input!.value = "";
    input!.style.height = "auto";

    if (editId) {
      setEditingMessageId(null);
      const atts = [...pendingAttachments];
      clearAttachments();
      await editMessage(editId, text, atts);
    } else {
      await sendMessage(text);
    }
  };

  const handleSubmit = (e: Event) => {
    e.preventDefault();
    doSubmit();
  };

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      doSubmit();
    }
    if (e.key === "Escape" && editingMessageId()) {
      cancelEdit();
    }
  };

  const handleFileSelect = async (e: Event) => {
    const input = e.target as HTMLInputElement;
    const files = input.files;
    if (!files) return;
    for (const file of Array.from(files)) {
      await addAttachment(file);
    }
    input.value = "";
  };

  const handleSuggestionClick = (label: string) => {
    if (inputRef) {
      inputRef.value = label;
      inputRef.focus();
    }
  };

  const activeToolCount = () => {
    let count = 0;
    if (searchEnabled()) count++;
    if (urlContextEnabled()) count++;
    return count;
  };

  const activeInstructionCount = () => activeInstructionIds.length;

  const handleInstructionSave = async () => {
    const ep = editingInstruction();
    if (!ep || !ep.name.trim() || !ep.content.trim()) return;
    if (ep.id) {
      await updateCustomInstruction(ep.id, { name: ep.name.trim(), content: ep.content.trim() });
    } else {
      await createCustomInstruction(ep.name.trim(), ep.content.trim(), false);
    }
    setEditingInstruction(null);
  };

  const handleInstructionCancel = () => setEditingInstruction(null);

  const thinkingLabel = () => {
    if (!modelSupportsThinking(selectedModel())) return "";
    if (modelAlwaysThinking(selectedModel())) {
      const lvl = thinkingLevel();
      return lvl.charAt(0).toUpperCase() + lvl.slice(1);
    }
    if (!thinkingEnabled()) return "Off";
    if (isGemini3Model(selectedModel())) {
      const lvl = thinkingLevel();
      return lvl.charAt(0).toUpperCase() + lvl.slice(1);
    }
    return `${thinkingBudget()}`;
  };

  const currentModelName = () => {
    return AVAILABLE_MODELS.find((m) => m.id === selectedModel())?.name ?? selectedModel();
  };

  // --- Edit Mode Helpers ---

  const startEdit = (msg: Message) => {
    const text = msg.parts
      .filter((p) => p.type === "text")
      .map((p) => (p as { type: "text"; text: string }).text)
      .join("\n");

    // Load existing file attachments into pending attachments strip
    loadAttachmentsFromParts(msg.parts);

    setEditingMessageId(msg.id);
    if (inputRef) {
      inputRef.value = text;
      inputRef.style.height = "auto";
      inputRef.style.height = Math.min(inputRef.scrollHeight, 200) + "px";
      inputRef.focus();
    }
  };

  const cancelEdit = () => {
    setEditingMessageId(null);
    clearAttachments();
    if (inputRef) {
      inputRef.value = "";
      inputRef.style.height = "auto";
    }
  };

  // --- File type mapping ---

  const getFileTypeInfo = (mimeType: string): { icon: string; label: string } => {
    if (mimeType.startsWith("image/")) return { icon: "image", label: mimeType.split("/")[1]?.toUpperCase() || "IMG" };
    if (mimeType.startsWith("video/")) return { icon: "videocam", label: "Video" };
    if (mimeType.startsWith("audio/")) return { icon: "audio_file", label: "Audio" };
    if (mimeType === "application/pdf") return { icon: "picture_as_pdf", label: "PDF" };
    if (mimeType.startsWith("text/")) return { icon: "description", label: "TXT" };
    return { icon: "attach_file", label: "File" };
  };

  // --- Part Renderers ---

  const renderPart = (part: MessagePart, isUser: boolean) => {
    switch (part.type) {
      case "text":
        return <div class="message-text" innerHTML={renderMarkdown(part.text)} />;

      case "thinking":
        return (
          <details class="message-thinking">
            <summary class="md-typescale-label-medium thinking-label">
              <md-icon class="thinking-icon">psychology</md-icon>
              Thinking
            </summary>
            <div class="thinking-content md-typescale-body-small message-text" innerHTML={renderMarkdown(part.text)} />
          </details>
        );

      case "inlineData":
        // User inlineData is rendered in the attachment row by renderMessage
        if (isUser) return null;
        if (part.mimeType.startsWith("image/")) {
          return (
            <div class="message-image-container">
              <img
                src={`data:${part.mimeType};base64,${part.data}`}
                alt={part.label || "Image"}
                class="message-image"
                loading="lazy"
              />
            </div>
          );
        }
        return (
          <div class="message-file-chip">
            <md-icon>description</md-icon>
            <span class="md-typescale-label-medium">{part.label || "File"}</span>
          </div>
        );

      case "searchGrounding":
        if (!part.sources || part.sources.length === 0) return null;
        return (
          <div class="search-grounding">
            <div class="search-grounding-header md-typescale-label-medium">
              <md-icon>travel_explore</md-icon>
              Sources
            </div>
            <div class="search-sources">
              <For each={part.sources}>
                {(source) => (
                  <a
                    class="search-source-chip"
                    href={source.uri}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <md-icon>open_in_new</md-icon>
                    <span class="md-typescale-label-small">{source.title || new URL(source.uri).hostname}</span>
                  </a>
                )}
              </For>
            </div>
          </div>
        );

      case "functionCall":
        return (
          <div class="function-call-chip">
            <md-icon>functions</md-icon>
            <span class="md-typescale-label-medium">{part.name}</span>
          </div>
        );

      case "functionResponse":
        return null;

      default:
        return null;
    }
  };

  const copyMessageText = (msg: Message) => {
    const text = msg.parts
      .filter((p) => p.type === "text")
      .map((p) => (p as { type: "text"; text: string }).text)
      .join("\n");
    navigator.clipboard.writeText(text);
  };

  const formatTime = (ts: number) => {
    const d = new Date(ts);
    return d.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  };

  const renderMessage = (msg: Message) => {
    const isUser = msg.role === "user";
    const isLast = () => messages[messages.length - 1]?.id === msg.id;

    // Branch info — on user messages that have been edited
    const branch = () => msg.branchGroupId ? branchState[msg.branchGroupId] : undefined;

    // For user messages, separate attachments (inlineData) from text parts
    const userAttachParts = () =>
      isUser ? msg.parts.filter((p) => p.type === "inlineData") as { type: "inlineData"; mimeType: string; data: string; label?: string }[] : [];
    const userTextParts = () =>
      isUser ? msg.parts.filter((p) => p.type !== "inlineData") : [];

    return (
      <div class={`message ${isUser ? "message-user" : "message-model"}`}>
        <Show when={!isUser}>
          <div class="message-avatar">
            <div class="avatar-icon lumi-logo" />
          </div>
        </Show>
        <div class="message-content-wrapper">
          {/* User attachment row: image thumbnails + file type chips */}
          <Show when={isUser && userAttachParts().length > 0}>
            <div class="user-attach-row">
              <For each={userAttachParts()}>
                {(part) => {
                  const isImage = part.mimeType.startsWith("image/");
                  const info = getFileTypeInfo(part.mimeType);
                  return (
                    <div class="user-attach-item">
                      <Show
                        when={isImage}
                        fallback={
                          <div class="user-attach-file-icon">
                            <md-icon>{info.icon}</md-icon>
                          </div>
                        }
                      >
                        <img
                          class="user-attach-img"
                          src={`data:${part.mimeType};base64,${part.data}`}
                          alt={part.label || "Image"}
                          loading="lazy"
                        />
                      </Show>
                      <span class="user-attach-name md-typescale-label-small">{part.label || info.label}</span>
                    </div>
                  );
                }}
              </For>
            </div>
          </Show>

          {/* Bubble: model gets all parts; user gets only text parts */}
          <Show when={!isUser || userTextParts().length > 0}>
            <div class={`message-bubble ${isUser ? "bubble-user" : "bubble-model"}`}>
              <For each={isUser ? userTextParts() : msg.parts}>
                {(part) => renderPart(part, isUser)}
              </For>
            </div>
          </Show>

          <div class={`message-actions ${isUser ? "actions-user" : "actions-model"}`}>
            <span class="message-time md-typescale-label-small">{formatTime(msg.createdAt)}</span>

            {/* Branch navigation on user messages */}
            <Show when={isUser && branch()}>
              {(_br) => {
                const b = branch()!;
                return (
                  <div class="branch-nav">
                    <md-icon-button
                      class="action-btn"
                      type="button"
                      disabled={b.activeIndex <= 0}
                      onClick={() => navigateBranch(msg.branchGroupId!, b.activeIndex - 1)}
                    >
                      <md-icon>chevron_left</md-icon>
                    </md-icon-button>
                    <span class="branch-indicator md-typescale-label-small">
                      {b.activeIndex + 1}/{b.total}
                    </span>
                    <md-icon-button
                      class="action-btn"
                      type="button"
                      disabled={b.activeIndex >= b.total - 1}
                      onClick={() => navigateBranch(msg.branchGroupId!, b.activeIndex + 1)}
                    >
                      <md-icon>chevron_right</md-icon>
                    </md-icon-button>
                  </div>
                );
              }}
            </Show>

            <md-icon-button class="action-btn" type="button" onClick={() => copyMessageText(msg)}>
              <md-icon>content_copy</md-icon>
            </md-icon-button>
            <Show when={isUser && !isStreaming()}>
              <md-icon-button class="action-btn" type="button" onClick={() => startEdit(msg)}>
                <md-icon>edit</md-icon>
              </md-icon-button>
            </Show>
            <Show when={!isUser && isLast() && !isStreaming()}>
              <md-icon-button class="action-btn" type="button" onClick={() => retryMessage()}>
                <md-icon>refresh</md-icon>
              </md-icon-button>
            </Show>
            {/* Branch to new chat — only on model (response) messages */}
            <Show when={!isUser && !isStreaming()}>
              <md-icon-button class="action-btn" type="button" onClick={() => branchToNewChat(msg.id)}>
                <md-icon>call_split</md-icon>
              </md-icon-button>
            </Show>
          </div>
        </div>
      </div>
    );
  };

  // --- Welcome Screen ---

  const WelcomeScreen = () => {
    const userName = () => {
      const email = account()?.email;
      if (!email) return "";
      const name = email.split("@")[0];
      return name.charAt(0).toUpperCase() + name.slice(1);
    };

    return (
      <div class="welcome-screen">
        <div class="welcome-content">
          <h1 class="welcome-greeting">
            {greeting}{userName() ? `, ${userName()}` : ""}
          </h1>
          <p class="welcome-subtitle">{subtitle}</p>
          {/* Suggestion chips inside welcome on mobile */}
          <div class="welcome-suggestions">
            <For each={suggestions}>
              {([icon, label]) => (
                <button class="suggestion-chip" onClick={() => handleSuggestionClick(label)}>
                  <md-icon>{icon}</md-icon>
                  <span>{label}</span>
                </button>
              )}
            </For>
          </div>
        </div>
      </div>
    );
  };

  // --- Suggestion Chips Row (below input on welcome — desktop only) ---

  const SuggestionRow = () => (
    <div class="suggestion-row">
      <For each={suggestions}>
        {([icon, label]) => (
          <button class="suggestion-chip" onClick={() => handleSuggestionClick(label)}>
            <md-icon>{icon}</md-icon>
            <span>{label}</span>
          </button>
        )}
      </For>
    </div>
  );

  // --- Input Area (shared between welcome and conversation) ---

  const InputArea = () => (
    <div class="chat-input-area">
      {/* File preview strip */}
      <Show when={pendingAttachments.length > 0}>
        <div class="attachment-strip">
          <For each={pendingAttachments}>
            {(att) => (
              <div class="attachment-preview">
                <Show
                  when={att.preview}
                  fallback={
                    <div class="attachment-file-icon">
                      <md-icon>description</md-icon>
                    </div>
                  }
                >
                  <img src={att.preview} alt={att.file.name} class="attachment-thumb" />
                </Show>
                <span class="attachment-name md-typescale-label-small">{att.file.name}</span>
                <button class="attachment-remove" onClick={() => removeAttachment(att.id)}>
                  <md-icon>close</md-icon>
                </button>
              </div>
            )}
          </For>
        </div>
      </Show>

      <form class="chat-form" onSubmit={handleSubmit}>
        {/* Edit mode banner */}
        <Show when={editingMessageId()}>
          <div class="edit-banner">
            <md-icon class="edit-banner-icon">edit</md-icon>
            <span class="md-typescale-label-medium">Editing message</span>
            <md-icon-button class="edit-banner-close" type="button" onClick={() => cancelEdit()}>
              <md-icon>close</md-icon>
            </md-icon-button>
          </div>
        </Show>

        <div class="input-row">
          <div class="input-field-wrapper">
            <textarea
              ref={inputRef}
              rows={1}
              placeholder={editingMessageId() ? "Edit your message..." : "Message Lumi AI..."}
              class="chat-input"
              onKeyDown={handleKeyDown}
              disabled={isViewingActiveStream()}
              onInput={(e) => {
                const el = e.currentTarget;
                el.style.height = "auto";
                el.style.height = Math.min(el.scrollHeight, 200) + "px";
              }}
            />
          </div>
        </div>
        <div class="input-toolbar">
          {/* Attach button */}
          <md-icon-button
            type="button"
            aria-label="Attach files"
            onClick={() => fileInputRef?.click()}
            disabled={isViewingActiveStream()}
          >
            <md-icon>add</md-icon>
          </md-icon-button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/*,video/*,audio/*,application/pdf,text/*,.py,.js,.ts,.json,.csv,.md,.xml,.html,.rtf,.epub"
            style="display:none"
            onChange={handleFileSelect}
          />

          {/* Tools button */}
          <div class="toolbar-menu-anchor">
            <md-icon-button
              type="button"
              aria-label="Tools"
              onClick={() => setToolsMenuOpen(!toolsMenuOpen())}
              class={activeToolCount() > 0 ? "tools-active" : ""}
            >
              <md-icon>build</md-icon>
            </md-icon-button>
            <Show when={activeToolCount() > 0}>
              <span class="tool-badge">{activeToolCount()}</span>
            </Show>
            <Show when={toolsMenuOpen()}>
              <div class="toolbar-popup tools-popup" onClick={(e) => e.stopPropagation()}>
                <div class="popup-header md-typescale-title-small">Tools</div>
                <label class="tool-toggle">
                  <md-icon>travel_explore</md-icon>
                  <span>Google Search</span>
                  <input
                    type="checkbox"
                    checked={searchEnabled()}
                    onChange={(e) => setSearchEnabled(e.currentTarget.checked)}
                  />
                  <span class={`toggle-track ${searchEnabled() ? "on" : ""}`}>
                    <span class="toggle-thumb" />
                  </span>
                </label>
                <label class="tool-toggle">
                  <md-icon>link</md-icon>
                  <span>URL Context</span>
                  <input
                    type="checkbox"
                    checked={urlContextEnabled()}
                    onChange={(e) => setUrlContextEnabled(e.currentTarget.checked)}
                  />
                  <span class={`toggle-track ${urlContextEnabled() ? "on" : ""}`}>
                    <span class="toggle-thumb" />
                  </span>
                </label>
              </div>
              <div class="popup-backdrop" onClick={() => setToolsMenuOpen(false)} />
            </Show>
          </div>

          {/* Custom Instructions button */}
          <div class="toolbar-menu-anchor">
            <md-icon-button
              type="button"
              aria-label="Custom Instructions"
              onClick={() => { setInstructionsMenuOpen(!instructionsMenuOpen()); setEditingInstruction(null); }}
              class={activeInstructionCount() > 0 ? "tools-active" : ""}
            >
              <md-icon>tune</md-icon>
            </md-icon-button>
            <Show when={activeInstructionCount() > 0}>
              <span class="tool-badge">{activeInstructionCount()}</span>
            </Show>
            <Show when={instructionsMenuOpen()}>
              <div class="toolbar-popup instructions-popup" onClick={(e) => e.stopPropagation()}>
                <div class="popup-header md-typescale-title-small">
                  <span>Custom Instructions</span>
                  <button
                    type="button"
                    class="icon-btn"
                    aria-label="Add instruction"
                    onClick={() => setEditingInstruction({ name: "", content: "" })}
                  >
                    <md-icon>add</md-icon>
                  </button>
                </div>

                {/* Inline editor */}
                <Show when={editingInstruction()}>
                  <div class="instruction-editor">
                    <input
                      type="text"
                      class="instruction-editor-name"
                      placeholder="Instruction name"
                      value={editingInstruction()!.name}
                      onInput={(e) => setEditingInstruction({ ...editingInstruction()!, name: e.currentTarget.value })}
                    />
                    <textarea
                      class="instruction-editor-content"
                      placeholder="Instruction content..."
                      rows={4}
                      value={editingInstruction()!.content}
                      onInput={(e) => setEditingInstruction({ ...editingInstruction()!, content: e.currentTarget.value })}
                    />
                    <div class="instruction-editor-actions">
                      <button type="button" class="text-btn" onClick={handleInstructionCancel}>Cancel</button>
                      <button type="button" class="tonal-btn" onClick={handleInstructionSave}>Save</button>
                    </div>
                  </div>
                </Show>

                {/* Instruction list */}
                <Show when={customInstructions.length > 0}>
                  <div class="instruction-list">
                    <For each={customInstructions}>
                      {(instruction) => (
                        <div class="instruction-item">
                          <label class="instruction-toggle">
                            <input
                              type="checkbox"
                              checked={activeInstructionIds.includes(instruction.id)}
                              onChange={() => toggleInstructionActive(instruction.id)}
                            />
                            <span class={`toggle-track ${activeInstructionIds.includes(instruction.id) ? "on" : ""}`}>
                              <span class="toggle-thumb" />
                            </span>
                          </label>
                          <div class="instruction-info" onClick={() => toggleInstructionActive(instruction.id)}>
                            <span class="md-typescale-body-medium">{instruction.name}</span>
                            <span class="md-typescale-label-small instruction-preview">
                              {instruction.content.slice(0, 60)}{instruction.content.length > 60 ? "…" : ""}
                            </span>
                          </div>
                          <button
                            type="button"
                            class="icon-btn icon-btn-sm"
                            aria-label="Edit"
                            onClick={() => setEditingInstruction({ id: instruction.id, name: instruction.name, content: instruction.content })}
                          >
                            <md-icon>edit</md-icon>
                          </button>
                          <button
                            type="button"
                            class="icon-btn icon-btn-sm"
                            aria-label="Delete"
                            onClick={() => deleteCustomInstruction(instruction.id)}
                          >
                            <md-icon>delete</md-icon>
                          </button>
                        </div>
                      )}
                    </For>
                  </div>
                </Show>

                <Show when={customInstructions.length === 0 && !editingInstruction()}>
                  <div class="instruction-empty md-typescale-body-small">
                    No custom instructions yet. Add one to customize AI behavior.
                  </div>
                </Show>
              </div>
              <div class="popup-backdrop" onClick={() => setInstructionsMenuOpen(false)} />
            </Show>
          </div>

          {/* Thinking toggle (only for models that support it) */}
          <Show when={modelSupportsThinking(selectedModel())}>
            <div class="toolbar-menu-anchor">
              <button
                type="button"
                class={`thinking-btn ${thinkingEnabled() ? "thinking-active" : ""}`}
                onClick={() => setThinkingMenuOpen(!thinkingMenuOpen())}
              >
                <md-icon>psychology</md-icon>
                <span class="md-typescale-label-medium">{thinkingLabel()}</span>
              </button>
              <Show when={thinkingMenuOpen()}>
                <div class="toolbar-popup thinking-popup" onClick={(e) => e.stopPropagation()}>
                  <div class="popup-header md-typescale-title-small">Thinking</div>

                  {/* Hide enable toggle for models that always think (e.g. Gemini 3 Pro) */}
                  <Show when={!modelAlwaysThinking(selectedModel())}>
                    <label class="tool-toggle">
                      <md-icon>psychology</md-icon>
                      <span>Enable Thinking</span>
                      <input
                        type="checkbox"
                        checked={thinkingEnabled()}
                        onChange={(e) => setThinkingEnabled(e.currentTarget.checked)}
                      />
                      <span class={`toggle-track ${thinkingEnabled() ? "on" : ""}`}>
                        <span class="toggle-thumb" />
                      </span>
                    </label>
                  </Show>

                  {/* Gemini 3: level selector (always visible for alwaysThinking, conditional for others) */}
                  <Show when={isGemini3Model(selectedModel()) && (modelAlwaysThinking(selectedModel()) || thinkingEnabled())}>
                    <div class="thinking-levels">
                      <div class="md-typescale-label-small thinking-levels-label">Thinking Level</div>
                      <div class="thinking-level-options">
                        <For each={getModelThinkingLevels(selectedModel())}>
                          {(lvl) => (
                            <button
                              type="button"
                              class={`thinking-level-btn ${thinkingLevel() === lvl ? "selected" : ""}`}
                              onClick={() => setThinkingLevel(lvl as "low" | "medium" | "high")}
                            >
                              {lvl.charAt(0).toUpperCase() + lvl.slice(1)}
                            </button>
                          )}
                        </For>
                      </div>
                    </div>
                  </Show>

                  {/* Gemini 2.5: budget slider */}
                  <Show when={!isGemini3Model(selectedModel()) && thinkingEnabled()}>
                    <div class="thinking-budget">
                      <div class="thinking-budget-header">
                        <span class="md-typescale-label-small">Budget</span>
                        <span class="md-typescale-label-small thinking-budget-value">{thinkingBudget()}</span>
                      </div>
                      <input
                        type="range"
                        class="thinking-budget-slider"
                        min={1024}
                        max={32768}
                        step={1024}
                        value={thinkingBudget()}
                        onInput={(e) => setThinkingBudget(parseInt(e.currentTarget.value))}
                      />
                      <div class="thinking-budget-labels">
                        <span class="md-typescale-label-small">1K</span>
                        <span class="md-typescale-label-small">32K</span>
                      </div>
                    </div>
                  </Show>
                </div>
                <div class="popup-backdrop" onClick={() => setThinkingMenuOpen(false)} />
              </Show>
            </div>
          </Show>

          <div class="toolbar-spacer" />

          {/* Model selector */}
          <div class="toolbar-menu-anchor">
            <button
              type="button"
              class="model-selector-btn"
              onClick={() => setModelMenuOpen(!modelMenuOpen())}
              disabled={isViewingActiveStream()}
            >
              <span class="md-typescale-label-large">{currentModelName()}</span>
              <md-icon>expand_more</md-icon>
            </button>
            <Show when={modelMenuOpen()}>
              <div class="toolbar-popup model-popup" onClick={(e) => e.stopPropagation()}>
                <For each={AVAILABLE_MODELS}>
                  {(model) => (
                    <button
                      type="button"
                      class={`model-option ${model.id === selectedModel() ? "selected" : ""}`}
                      onClick={() => {
                        setSelectedModel(model.id);
                        clampThinkingLevelForModel(model.id);
                        setModelMenuOpen(false);
                      }}
                    >
                      <div>
                        <div class="md-typescale-body-medium">{model.name}</div>
                      </div>
                      <Show when={model.id === selectedModel()}>
                        <md-icon class="model-check">check_circle</md-icon>
                      </Show>
                    </button>
                  )}
                </For>
              </div>
              <div class="popup-backdrop" onClick={() => setModelMenuOpen(false)} />
            </Show>
          </div>

          {/* Send / Stop button */}
          <md-filled-tonal-icon-button
            type="button"
            aria-label={isViewingActiveStream() ? "Stop generating" : "Send message"}
            disabled={isStreaming() && !isViewingActiveStream()}
            class={`send-button ${isViewingActiveStream() ? "is-stop" : ""}`}
            onClick={() => isViewingActiveStream() ? stopStreaming() : doSubmit()}
          >
            <md-icon>{isViewingActiveStream() ? "stop" : editingMessageId() ? "check" : "send"}</md-icon>
          </md-filled-tonal-icon-button>
        </div>
      </form>
    </div>
  );

  return (
    <div class="chat-view">
      <div class="chat-topbar">
        <md-icon-button class="sidebar-toggle" type="button" aria-label="Toggle sidebar" onClick={() => setSidebarOpen((prev) => !prev)}>
          <md-icon>{sidebarOpen() ? "menu_open" : "menu"}</md-icon>
        </md-icon-button>
        <span class="md-typescale-title-medium chat-topbar-title">
          {activeConversation()?.title || "Lumi AI"}
        </span>
        <div class="topbar-spacer" />
        <Show when={activeConversationId()}>
          <md-icon-button type="button" aria-label="New chat" onClick={() => selectConversation(null)}>
            <md-icon>edit_square</md-icon>
          </md-icon-button>
        </Show>
      </div>

      <Show
        when={activeConversationId()}
        fallback={
          <div class="welcome-layout">
            <WelcomeScreen />
            <div class="welcome-input-group">
              <InputArea />
              <SuggestionRow />
            </div>
          </div>
        }
      >
        <div class="chat-messages">
          <For each={messages}>{(msg) => renderMessage(msg)}</For>

          {/* Streaming response — only shown when viewing the branch that's streaming */}
          <Show when={isViewingActiveStream()}>
            <div class="message message-model">
              <div class="message-avatar">
                <div class="avatar-icon lumi-logo" />
              </div>
              <div class="message-bubble bubble-model">
                <Show when={streamingThinking()}>
                  <details class="message-thinking" open>
                    <summary class="md-typescale-label-medium thinking-label">
                      <md-icon class="thinking-icon">psychology</md-icon>
                      Thinking...
                    </summary>
                    <div class="thinking-content md-typescale-body-small message-text" innerHTML={renderMarkdown(streamingThinking())} />
                  </details>
                </Show>
                <Show when={streamingImages.length > 0}>
                  <For each={streamingImages}>
                    {(img) => (
                      <div class="message-image-container">
                        <img src={`data:${img.mimeType};base64,${img.data}`} alt="Generated" class="message-image" />
                      </div>
                    )}
                  </For>
                </Show>
                <Show when={streamingText()}>
                  <div class="message-text" innerHTML={renderMarkdown(streamingText())} />
                </Show>
                <Show when={!streamingText() && !streamingThinking()}>
                  <div class="typing-indicator">
                    <span></span><span></span><span></span>
                  </div>
                </Show>
              </div>
            </div>
          </Show>

          <Show when={chatError()}>
            <div class="chat-error md-typescale-body-medium">
              <md-icon class="error-icon">error_outline</md-icon>
              {chatError()}
            </div>
          </Show>

          <div ref={messagesEndRef}></div>
        </div>

        <InputArea />
      </Show>
    </div>
  );
}
