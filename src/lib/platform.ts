/**
 * Platform abstraction layer.
 * Detects whether the app is running inside Tauri or a plain browser,
 * and provides unified APIs for HTTP fetch, shell open, and OAuth listener.
 */

// --- Detection ---

export function isTauri(): boolean {
  return (
    typeof window !== "undefined" &&
    "__TAURI_INTERNALS__" in window
  );
}

export function isMobile(): boolean {
  return /android|iphone|ipad/i.test(navigator.userAgent);
}

// --- HTTP Fetch ---

export async function platformFetch(
  url: string | URL,
  init?: RequestInit,
): Promise<Response> {
  if (isTauri()) {
    const { fetch: tauriFetch } = await import("@tauri-apps/plugin-http");
    return tauriFetch(url as string, init);
  }
  return fetch(String(url), init);
}

// --- Open URL (open URL in system browser / default handler) ---

export async function platformOpenUrl(url: string): Promise<void> {
  if (isTauri()) {
    const { openUrl } = await import("@tauri-apps/plugin-opener");
    await openUrl(url);
  } else {
    window.open(url, "_blank", "noopener,noreferrer");
  }
}

// --- OAuth Listener ---

/**
 * Starts an OAuth listener appropriate for the current platform.
 *
 * Tauri (desktop + Android): Invokes the Rust `start_oauth_listener` command
 * which binds a TCP listener on 127.0.0.1:{port}, then opens the auth URL in
 * the system browser. The OAuth redirect hits the local server, which captures
 * the authorization code and serves a success page.
 *
 * Web: Opens a centered popup to `authUrl`. The popup redirects to
 * /oauth-callback.html which posts the callback URL back via postMessage.
 */
export async function platformStartOAuthListener(
  port: number,
  authUrl?: string,
): Promise<string> {
  if (isTauri()) {
    // Desktop: TCP listener + system browser
    const { invoke } = await import("@tauri-apps/api/core");
    const { openUrl } = await import("@tauri-apps/plugin-opener");
    const listenerPromise = invoke<string>("start_oauth_listener", { port });
    await openUrl(authUrl ?? "");
    return listenerPromise;
  }

  return new Promise<string>((resolve, reject) => {
    const width = 500;
    const height = 700;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;
    const features = `width=${width},height=${height},left=${left},top=${top},toolbar=no,menubar=no`;

    const popup = window.open(authUrl ?? "", "lumi-oauth", features);
    if (!popup) {
      reject(new Error("Popup blocked. Please allow popups for this site."));
      return;
    }

    const pollClosed = setInterval(() => {
      if (popup.closed) {
        clearInterval(pollClosed);
        clearTimeout(timeout);
        window.removeEventListener("message", handler);
        reject(new Error("OAuth window was closed before completing authentication."));
      }
    }, 500);

    const timeout = setTimeout(() => {
      clearInterval(pollClosed);
      window.removeEventListener("message", handler);
      popup.close();
      reject(new Error("OAuth timed out after 5 minutes"));
    }, 300_000);

    function handler(event: MessageEvent) {
      if (event.origin !== window.location.origin) return;
      if (event.data?.type !== "oauth-callback") return;

      clearTimeout(timeout);
      clearInterval(pollClosed);
      window.removeEventListener("message", handler);

      if (event.data.error) {
        reject(new Error(event.data.error));
      } else {
        resolve(event.data.url as string);
      }
    }

    window.addEventListener("message", handler);
  });
}

// --- OAuth Redirect URI ---

/**
 * Returns the OAuth redirect URI for the current platform.
 * Tauri: loopback address for the TCP listener.
 * Web: /oauth-callback.html on the current origin.
 */
export function getOAuthRedirectUri(port: number): string {
  if (isTauri()) {
    return `http://127.0.0.1:${port}`;
  }
  return `${window.location.origin}/oauth-callback.html`;
}
