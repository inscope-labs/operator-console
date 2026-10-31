/**
 * MultiRepoGitHubAdapter — §11.
 *
 * Owns the configured repository set, shared transport, installation token,
 * rate-limit coordinator, ETag cache, typed handles, and cross-repo transaction executor.
 *
 * Construction is fail-closed on G1 (consumer repos must be private).
 * No repository may be added after construction.
 *
 * This file is an anchor. Full implementation is TODO.
 */

export type RepoKey = 'consumer' | 'product' | `read_only:${string}`;

export interface RepoConfig {
  role: 'consumer' | 'product' | 'read_only';
  owner: string;
  name: string;
  private_required: boolean;
  permission: string;
}

export interface RateBudget {
  remaining: number;
  reset: number; // Unix seconds
}

export interface TransactionStep {
  repo: RepoKey;
  idempotencyKey: string;
  run: () => Promise<unknown>;
}

export interface TransactionResult {
  ok: boolean;
  completed: string[];
  failedAt?: string;
  reason?: string;
}

/** Typed surface — compile-time expression of branch-scoped authority. */
export interface ConsumerHandle {
  getContents(path: string, ref?: string): Promise<{ sha: string; content: string; etag?: string }>;
  putContents(path: string, content: string, opts: { ifMatch: string }): Promise<void>;
  // ... remaining methods (TODO)
}

export interface ProductHandle {
  createCommitFromSha(/* ... */): Promise<string>;
  createPullRequest(/* ... */): Promise<{ number: number }>;
  getPullRequest(number: number): Promise<unknown>;
}

export interface ReadOnlyHandle {
  getContents(path: string, ref?: string): Promise<{ sha: string; content: string; etag?: string }>;
  // no write methods by design
}

/**
 * Anchor skeleton. Implementations of construction, token refresh,
 * rate coordination, ETag cache, transaction executor and typed handles
 * are left as TODO for subsequent agents.
 */
export class MultiRepoGitHubAdapter {
  // private repos: Map<RepoKey, RepoHandle> — immutable after construction
  // private dispatcher — shared undici
  // private token: InstallationTokenProvider
  // private rate: RateCoordinator
  // private etags: Map<string, string>

  constructor(
    _config: Readonly<Record<string, RepoConfig>>,
    _secrets: { installationId?: string /* ... */ }
  ) {
    // TODO: parse REPOS, enforce private_required for consumer (G1), build handles
    throw new Error('MultiRepoGitHubAdapter construction not yet implemented');
  }

  consumer(): ConsumerHandle {
    // TODO
    throw new Error('not implemented');
  }

  product(): ProductHandle {
    // TODO
    throw new Error('not implemented');
  }

  readOnly(_key: string): ReadOnlyHandle {
    // TODO
    throw new Error('not implemented');
  }

  async rateBudget(): Promise<RateBudget> {
    // TODO
    throw new Error('not implemented');
  }

  async transaction(_steps: TransactionStep[]): Promise<TransactionResult> {
    // TODO: sequential execution, idempotency keys, HEAD_MOVED protection
    throw new Error('not implemented');
  }
}
