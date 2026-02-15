import { For, Show, createSignal, createMemo } from "solid-js";
import { Portal } from "solid-js/web";
import {
  conversations,
  activeConversationId,
  selectConversation,
  deleteConversation,
  renameConversation,
  togglePinConversation,
  toggleArchiveConversation,
  streamingConvIds,
} from "../lib/stores/chat";
import type { Conversation } from "../lib/db";
import { account, logout } from "../lib/stores/auth";
import { setSidebarOpen } from "../App";
import "./Sidebar.css";

export default function Sidebar() {
  const [searchQuery, setSearchQuery] = createSignal("");
  const [contextMenuId, setContextMenuId] = createSignal<string | null>(null);
  const [renamingId, setRenamingId] = createSignal<string | null>(null);
  const [renameValue, setRenameValue] = createSignal("");
  const [showArchived, setShowArchived] = createSignal(false);
  const [deleteConfirmId, setDeleteConfirmId] = createSignal<string | null>(null);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = createSignal(false);

  // Split conversations into pinned, regular, archived
  const pinnedConversations = createMemo(() => {
    const q = searchQuery().toLowerCase().trim();
    return conversations.filter((c) => {
      if (c.archived) return false;
      if (!c.pinned) return false;
      return !q || c.title.toLowerCase().includes(q);
    });
  });

  const regularConversations = createMemo(() => {
    const q = searchQuery().toLowerCase().trim();
    return conversations.filter((c) => {
      if (c.archived) return false;
      if (c.pinned) return false;
      return !q || c.title.toLowerCase().includes(q);
    });
  });

  const archivedConversations = createMemo(() => {
    const q = searchQuery().toLowerCase().trim();
    return conversations.filter((c) => {
      if (!c.archived) return false;
      return !q || c.title.toLowerCase().includes(q);
    });
  });

  const hasAnyConversation = createMemo(() =>
    pinnedConversations().length > 0 || regularConversations().length > 0 || archivedConversations().length > 0
  );

  const handleNewChat = () => {
    selectConversation(null);
    setSidebarOpen(false);
  };

  const openContextMenu = (e: Event, convId: string) => {
    e.stopPropagation();
    setContextMenuId(contextMenuId() === convId ? null : convId);
  };

  const startRename = (convId: string) => {
    const conv = conversations.find((c) => c.id === convId);
    if (!conv) return;
    setRenamingId(convId);
    setRenameValue(conv.title);
    setContextMenuId(null);
  };

  let renamingJustEnded = false;

  const submitRename = (convId: string) => {
    const value = renameValue().trim();
    if (value) renameConversation(convId, value);
    setRenamingId(null);
    setRenameValue("");
    // Brief guard window so the blur→click race doesn't close the sidebar
    renamingJustEnded = true;
    setTimeout(() => { renamingJustEnded = false; }, 150);
  };

  const handleRenameKeyDown = (e: KeyboardEvent, convId: string) => {
    if (e.key === "Enter") submitRename(convId);
    if (e.key === "Escape") { setRenamingId(null); setRenameValue(""); renamingJustEnded = true; setTimeout(() => { renamingJustEnded = false; }, 150); }
  };

  const renderConvItem = (conv: Conversation) => {
    const isActive = () => conv.id === activeConversationId();
    const isRenaming = () => renamingId() === conv.id;
    const isContextOpen = () => contextMenuId() === conv.id;
    const isConvStreaming = () => !!streamingConvIds[conv.id];

    return (
      <div class="conv-item-wrapper">
        <button
          class={`conversation-item ${isActive() ? "active" : ""}`}
          onClick={() => {
            if (renamingId() || renamingJustEnded) return;
            selectConversation(conv.id);
            setSidebarOpen(false);
          }}
        >
          <Show when={isConvStreaming()} fallback={
            <md-icon class="conv-icon">
              {conv.pinned ? "push_pin" : "chat_bubble_outline"}
            </md-icon>
          }>
            <div class="conv-spinner" />
          </Show>

          <Show when={isRenaming()} fallback={
            <span class="conv-title">{conv.title}</span>
          }>
            <input
              class="conv-rename-input"
              value={renameValue()}
              onInput={(e) => setRenameValue(e.currentTarget.value)}
              onKeyDown={(e) => handleRenameKeyDown(e, conv.id)}
              onBlur={() => submitRename(conv.id)}
              onClick={(e: Event) => e.stopPropagation()}
              autofocus
            />
          </Show>

          <md-icon-button
            class="conv-menu-btn"
            type="button"
            aria-label="Menu"
            onClick={(e: Event) => openContextMenu(e, conv.id)}
          >
            <md-icon>more_horiz</md-icon>
          </md-icon-button>
        </button>

        {/* Context Menu */}
        <Show when={isContextOpen()}>
          <div class="conv-context-menu" onClick={(e) => e.stopPropagation()}>
            <button class="context-menu-item" onClick={() => startRename(conv.id)}>
              <md-icon>edit</md-icon>
              <span>Rename</span>
            </button>
            <button class="context-menu-item" onClick={() => { togglePinConversation(conv.id); setContextMenuId(null); }}>
              <md-icon>{conv.pinned ? "push_pin" : "push_pin"}</md-icon>
              <span>{conv.pinned ? "Unpin" : "Pin chat"}</span>
            </button>
            <button class="context-menu-item" onClick={() => { toggleArchiveConversation(conv.id); setContextMenuId(null); }}>
              <md-icon>{conv.archived ? "unarchive" : "archive"}</md-icon>
              <span>{conv.archived ? "Unarchive" : "Archive"}</span>
            </button>
            <button class="context-menu-item context-menu-danger" onClick={() => { setDeleteConfirmId(conv.id); setContextMenuId(null); }}>
              <md-icon>delete</md-icon>
              <span>Delete</span>
            </button>
          </div>
          <div class="popup-backdrop" onClick={() => setContextMenuId(null)} />
        </Show>
      </div>
    );
  };

  return (
    <>
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="sidebar-brand">
          <div class="brand-icon lumi-logo" />
          <span class="md-typescale-title-medium">Lumi AI</span>
        </div>
        <md-icon-button type="button" aria-label="New chat" onClick={handleNewChat}>
          <md-icon>edit_square</md-icon>
        </md-icon-button>
      </div>

      <div class="sidebar-search">
        <md-icon class="search-icon">search</md-icon>
        <input
          type="text"
          class="search-input md-typescale-body-medium"
          placeholder="Search chats..."
          value={searchQuery()}
          onInput={(e) => setSearchQuery(e.currentTarget.value)}
        />
        <Show when={searchQuery()}>
          <md-icon-button class="search-clear" type="button" onClick={() => setSearchQuery("")}>
            <md-icon>close</md-icon>
          </md-icon-button>
        </Show>
      </div>

      <div class="sidebar-conversations">
        <Show
          when={hasAnyConversation()}
          fallback={
            <div class="sidebar-empty md-typescale-body-medium">
              {searchQuery() ? "No matching chats" : "No conversations yet"}
            </div>
          }
        >
          {/* Pinned Section */}
          <Show when={pinnedConversations().length > 0}>
            <div class="sidebar-section-label md-typescale-label-medium">Pinned</div>
            <For each={pinnedConversations()}>{(conv) => renderConvItem(conv)}</For>
          </Show>

          {/* Regular Chats */}
          <Show when={regularConversations().length > 0}>
            <div class="sidebar-section-label md-typescale-label-medium">Chats</div>
            <For each={regularConversations()}>{(conv) => renderConvItem(conv)}</For>
          </Show>

          {/* Archived Section */}
          <Show when={archivedConversations().length > 0}>
            <button class="archived-toggle" onClick={() => setShowArchived((v) => !v)}>
              <md-icon>archive</md-icon>
              <span class="md-typescale-label-medium">Archived ({archivedConversations().length})</span>
              <md-icon class="archived-chevron">{showArchived() ? "expand_less" : "expand_more"}</md-icon>
            </button>
            <Show when={showArchived()}>
              <For each={archivedConversations()}>{(conv) => renderConvItem(conv)}</For>
            </Show>
          </Show>
        </Show>
      </div>

      <div class="sidebar-footer">
        <md-divider></md-divider>
        <div class="sidebar-account">
          <div class="account-info">
            <md-icon class="account-icon">account_circle</md-icon>
            <span class="md-typescale-body-medium account-email">
              {account()?.email || "Unknown"}
            </span>
          </div>
          <md-icon-button type="button" aria-label="Sign out" onClick={() => setLogoutConfirmOpen(true)}>
            <md-icon>logout</md-icon>
          </md-icon-button>
        </div>
      </div>
    </aside>

    {/* Delete Confirmation Dialog — Portal to body for proper viewport centering */}
    <Show when={deleteConfirmId()}>
      <Portal>
        <div class="confirm-dialog-backdrop" onClick={() => setDeleteConfirmId(null)}>
          <div class="confirm-dialog" onClick={(e) => e.stopPropagation()}>
            <md-icon class="confirm-dialog-icon confirm-dialog-icon-error">delete_outline</md-icon>
            <h2 class="md-typescale-headline-small confirm-dialog-title">Delete chat?</h2>
            <p class="md-typescale-body-medium confirm-dialog-body">
              This will permanently delete this conversation and all its messages. This action cannot be undone.
            </p>
            <div class="confirm-dialog-actions">
              <button class="dialog-btn dialog-btn-cancel" onClick={() => setDeleteConfirmId(null)}>
                Cancel
              </button>
              <button
                class="dialog-btn dialog-btn-danger"
                onClick={() => {
                  const id = deleteConfirmId();
                  if (id) deleteConversation(id);
                  setDeleteConfirmId(null);
                }}
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      </Portal>
    </Show>

    {/* Logout Confirmation Dialog */}
    <Show when={logoutConfirmOpen()}>
      <Portal>
        <div class="confirm-dialog-backdrop" onClick={() => setLogoutConfirmOpen(false)}>
          <div class="confirm-dialog" onClick={(e) => e.stopPropagation()}>
            <md-icon class="confirm-dialog-icon">logout</md-icon>
            <h2 class="md-typescale-headline-small confirm-dialog-title">Sign out?</h2>
            <p class="md-typescale-body-medium confirm-dialog-body">
              You will need to sign in again to continue using Lumi AI.
            </p>
            <div class="confirm-dialog-actions">
              <button class="dialog-btn dialog-btn-cancel" onClick={() => setLogoutConfirmOpen(false)}>
                Cancel
              </button>
              <button
                class="dialog-btn dialog-btn-confirm"
                onClick={() => {
                  setLogoutConfirmOpen(false);
                  logout();
                }}
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      </Portal>
    </Show>
    </>
  );
}
