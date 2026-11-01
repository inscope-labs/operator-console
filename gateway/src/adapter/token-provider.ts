/**
 * InstallationTokenProvider — ghs_ lifecycle.
 * Spec §11.6.
 *
 * - Refreshes at least 5 minutes before expiry.
 * - Serialises concurrent refresh (single in-flight promise).
 * - Fail closed on refresh failure (TOKEN_UNAVAILABLE).
 * - Never logged; never exposed to the console.
 */

import {
  fetchInstallationToken,
  type GitHubAppCredentials,
} from './github-app-auth';

export interface TokenSnapshot {
  token: string;
  expiresAt: number; // Unix seconds
}

export interface InstallationTokenProviderOptions {
  /** Obtain a fresh installation token from GitHub App credentials. */
  fetchNewToken: () => Promise<TokenSnapshot>;
  /** Clock (injectable). */
  now?: () => number;
  /** Refresh skew in seconds (default 300). */
  refreshSkewSeconds?: number;
}

export class InstallationTokenProvider {
  private current: TokenSnapshot | null = null;
  private inflight: Promise<TokenSnapshot> | null = null;
  private readonly fetchNewToken: () => Promise<TokenSnapshot>;
  private readonly now: () => number;
  private readonly refreshSkewSeconds: number;

  constructor(opts: InstallationTokenProviderOptions) {
    this.fetchNewToken = opts.fetchNewToken;
    this.now = opts.now ?? (() => Math.floor(Date.now() / 1000));
    this.refreshSkewSeconds = opts.refreshSkewSeconds ?? 300;
  }

  /**
   * Return a usable token, refreshing if necessary.
   * Concurrent callers share the same refresh promise.
   */
  async getToken(): Promise<string> {
    const now = this.now();
    if (
      this.current &&
      this.current.expiresAt - now > this.refreshSkewSeconds
    ) {
      return this.current.token;
    }
    return (await this.refresh()).token;
  }

  /**
   * Force a refresh (e.g. after a 401 from GitHub).
   */
  async refresh(): Promise<TokenSnapshot> {
    if (this.inflight) {
      return this.inflight;
    }
    this.inflight = this.fetchNewToken()
      .then((snap) => {
        this.current = snap;
        return snap;
      })
      .catch((err) => {
        this.current = null;
        throw err;
      })
      .finally(() => {
        this.inflight = null;
      });
    return this.inflight;
  }

  /** For diagnostics only — never log the token itself. */
  peekExpiresAt(): number | null {
    return this.current?.expiresAt ?? null;
  }
}

/** Build a provider from GitHub App credentials (production path). */
export function createInstallationTokenProvider(
  creds: GitHubAppCredentials,
  fetchImpl: typeof fetch = fetch
): InstallationTokenProvider {
  return new InstallationTokenProvider({
    fetchNewToken: async () => {
      const { token, expiresAt } = await fetchInstallationToken(creds, fetchImpl);
      return { token, expiresAt };
    },
  });
}
