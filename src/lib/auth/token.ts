import {
  ANTIGRAVITY_OAUTH_CLIENT_ID,
  ANTIGRAVITY_OAUTH_CLIENT_SECRET,
  GOOGLE_OAUTH_TOKEN_URL,
  TOKEN_EXPIRY_BUFFER_MS,
} from "./constants";
import { db, type AuthAccount } from "../db";
import { getAccount } from "./oauth";
import { platformFetch } from "../platform";

/**
 * Checks whether the stored access token is expired or about to expire.
 */
export function isTokenExpired(account: AuthAccount): boolean {
  if (!account.accessToken || typeof account.tokenExpiry !== "number") {
    return true;
  }
  return account.tokenExpiry <= Date.now() + TOKEN_EXPIRY_BUFFER_MS;
}

/**
 * Refreshes the access token using the stored refresh token.
 * Uses the standard OAuth 2.0 token endpoint for all platforms.
 * Returns null if refresh fails (caller should trigger re-login).
 */
export async function refreshAccessToken(account: AuthAccount): Promise<AuthAccount | null> {
  if (!account.refreshToken) {
    return null;
  }

  try {
    const startTime = Date.now();
    const resp = await platformFetch(GOOGLE_OAUTH_TOKEN_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: account.refreshToken,
        client_id: ANTIGRAVITY_OAUTH_CLIENT_ID,
        client_secret: ANTIGRAVITY_OAUTH_CLIENT_SECRET,
      }).toString(),
    });

    if (!resp.ok) {
      return null;
    }

    const payload = (await resp.json()) as {
      access_token: string;
      expires_in: number;
      refresh_token?: string;
    };

    const expiresIn = typeof payload.expires_in === "number" ? payload.expires_in : 3600;
    const updated: AuthAccount = {
      ...account,
      accessToken: payload.access_token,
      tokenExpiry: startTime + expiresIn * 1000,
      refreshToken: payload.refresh_token ?? account.refreshToken,
      updatedAt: Date.now(),
    };

    await db.auth.put(updated);
    return updated;
  } catch {
    return null;
  }
}

/**
 * Returns a valid access token, refreshing if necessary.
 * Throws if no account exists or refresh fails.
 */
export async function getValidAccessToken(): Promise<{ token: string; projectId: string }> {
  let account = await getAccount();
  if (!account) {
    throw new Error("Not authenticated. Please log in.");
  }

  if (isTokenExpired(account)) {
    const refreshed = await refreshAccessToken(account);
    if (!refreshed) {
      throw new Error("Session expired. Please log in again.");
    }
    account = refreshed;
  }

  return { token: account.accessToken, projectId: account.projectId };
}
