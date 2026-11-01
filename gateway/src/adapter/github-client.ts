/**
 * Thin GitHub REST helper used by RepoHandles.
 * All calls go through the shared token provider.
 */

import type { InstallationTokenProvider } from './token-provider';

export interface GitHubClientOptions {
  tokenProvider: InstallationTokenProvider;
  fetchImpl?: typeof fetch;
  userAgent?: string;
}

export class GitHubClient {
  private readonly tokens: InstallationTokenProvider;
  private readonly fetchImpl: typeof fetch;
  private readonly userAgent: string;

  constructor(opts: GitHubClientOptions) {
    this.tokens = opts.tokenProvider;
    this.fetchImpl = opts.fetchImpl ?? fetch;
    this.userAgent = opts.userAgent ?? 'cbridge-gateway';
  }

  async request<T = unknown>(
    method: string,
    path: string,
    init: { body?: unknown; etag?: string; accept?: string } = {}
  ): Promise<{ status: number; data: T; etag?: string; headers: Headers }> {
    const token = await this.tokens.getToken();
    const headers: Record<string, string> = {
      authorization: `Bearer ${token}`,
      accept: init.accept ?? 'application/vnd.github+json',
      'x-github-api-version': '2022-11-28',
      'user-agent': this.userAgent,
    };
    if (init.etag) headers['if-none-match'] = init.etag;
    if (init.body !== undefined) headers['content-type'] = 'application/json';

    const res = await this.fetchImpl(`https://api.github.com${path}`, {
      method,
      headers,
      body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
    });

    if (res.status === 401) {
      // Force refresh once and retry.
      await this.tokens.refresh();
      const retryToken = await this.tokens.getToken();
      headers.authorization = `Bearer ${retryToken}`;
      const retry = await this.fetchImpl(`https://api.github.com${path}`, {
        method,
        headers,
        body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
      });
      return this.parse(retry);
    }

    return this.parse(res);
  }

  private async parse<T>(
    res: Response
  ): Promise<{ status: number; data: T; etag?: string; headers: Headers }> {
    const etag = res.headers.get('etag') ?? undefined;
    if (res.status === 304) {
      return { status: 304, data: null as T, etag, headers: res.headers };
    }
    const text = await res.text();
    let data: T = null as T;
    if (text) {
      try {
        data = JSON.parse(text) as T;
      } catch {
        data = text as unknown as T;
      }
    }
    if (!res.ok) {
      const msg =
        typeof data === 'object' && data && 'message' in data
          ? String((data as { message: string }).message)
          : text.slice(0, 200);
      const err = new Error(`GitHub ${res.status}: ${msg}`) as Error & {
        status: number;
      };
      err.status = res.status;
      throw err;
    }
    return { status: res.status, data, etag, headers: res.headers };
  }
}
