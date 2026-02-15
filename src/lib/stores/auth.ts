import { createSignal } from "solid-js";
import type { AuthAccount } from "../db";
import { getAccount, loginWithGoogle, logout as logoutOAuth, resumeMobileOAuth } from "../auth/oauth";

const [account, setAccount] = createSignal<AuthAccount | null>(null);
const [authLoading, setAuthLoading] = createSignal(false);
const [authError, setAuthError] = createSignal<string | null>(null);

export { account, authLoading, authError };

/**
 * Initializes auth state by loading the persisted account from Dexie.
 */
export async function initAuth(): Promise<void> {
  try {
    // Complete any pending mobile OAuth flow (navigated away and back)
    const resumed = await resumeMobileOAuth();
    if (resumed) {
      setAccount(resumed);
      return;
    }

    const stored = await getAccount();
    setAccount(stored);
  } catch (err) {
    // Auth load failed — account will remain null
  }

  // Handle bfcache restoration on mobile.  When history.go() restores the
  // page from the session cache after OAuth, the JS context is unfrozen but
  // initAuth() won't re-run.  The pageshow event fires in this case, so we
  // complete the pending OAuth flow and reset the loading spinner here.
  if (typeof window !== "undefined") {
    window.addEventListener("pageshow", async (event: PageTransitionEvent) => {
      if (!event.persisted) return;
      try {
        const resumed = await resumeMobileOAuth();
        if (resumed) {
          setAccount(resumed);
        }
      } catch {
        // Resume failed — user can retry manually
      }
      // Always clear the loading state that was left by the never-resolving
      // loginWithGoogleImpl promise before the page navigated away.
      setAuthLoading(false);
    });
  }
}

/**
 * Starts the Google OAuth login flow.
 */
export async function login(): Promise<void> {
  setAuthLoading(true);
  setAuthError(null);
  try {
    const acct = await loginWithGoogle();
    setAccount(acct);
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    setAuthError(msg);
    throw err;
  } finally {
    setAuthLoading(false);
  }
}

/**
 * Logs out and clears auth state.
 */
export async function logout(): Promise<void> {
  await logoutOAuth();
  setAccount(null);
}
