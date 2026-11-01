/**
 * MultiRepoGitHubAdapter — §11.
 *
 * Owns the configured repository set, shared transport, installation token,
 * and typed handles. Construction is fail-closed on G1 (consumer repos must
 * be private). No repository may be added after construction.
 */

import { parseRepos, assertConsumerPrivacy } from '../config/repos';
import type { ParsedRepos } from '../config/repos';
import {
  createInstallationTokenProvider,
  type InstallationTokenProvider,
} from './token-provider';
import type { GitHubAppCredentials } from './github-app-auth';
import { GitHubClient } from './github-client';
import { ConsumerHandle } from './handles/consumer';
import { ProductHandle } from './handles/product';
import { ReadOnlyHandle } from './handles/read-only';

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
  reset: number;
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

export interface AdapterSecrets {
  appId: string;
  privateKeyPem: string;
  installationId: string;
}

export interface AdapterOptions {
  /** REPOS env JSON string. */
  reposJson: string;
  secrets: AdapterSecrets;
  /** Skip live G1 GitHub privacy check (unit tests only). */
  skipPrivacyCheck?: boolean;
  fetchImpl?: typeof fetch;
}

export class MultiRepoGitHubAdapter {
  private readonly parsed: ParsedRepos;
  private readonly client: GitHubClient;
  private readonly tokenProvider: InstallationTokenProvider;
  private readonly consumerHandle: ConsumerHandle;
  private readonly productHandle: ProductHandle;
  private readonly readOnlyHandles = new Map<string, ReadOnlyHandle>();

  private constructor(
    parsed: ParsedRepos,
    client: GitHubClient,
    tokenProvider: InstallationTokenProvider,
    consumerHandle: ConsumerHandle,
    productHandle: ProductHandle
  ) {
    this.parsed = parsed;
    this.client = client;
    this.tokenProvider = tokenProvider;
    this.consumerHandle = consumerHandle;
    this.productHandle = productHandle;
  }

  /**
   * Async factory — parses REPOS, builds handles, enforces G1.
   * Throws on misconfiguration or non-private consumer.
   */
  static async create(opts: AdapterOptions): Promise<MultiRepoGitHubAdapter> {
    const parsed = parseRepos(opts.reposJson);
    const creds: GitHubAppCredentials = {
      appId: opts.secrets.appId,
      privateKeyPem: opts.secrets.privateKeyPem,
      installationId: opts.secrets.installationId,
    };
    const tokenProvider = createInstallationTokenProvider(
      creds,
      opts.fetchImpl
    );
    const client = new GitHubClient({
      tokenProvider,
      fetchImpl: opts.fetchImpl,
    });

    const consumerKey = parsed.consumerKeys[0];
    const consumerCfg = parsed.map.get(consumerKey);
    if (!consumerCfg) {
      throw new Error('REPOS consumer entry missing after parse');
    }
    const productKey = parsed.productKey;
    if (!productKey) {
      throw new Error('REPOS product entry missing after parse');
    }
    const productCfg = parsed.map.get(productKey);
    if (!productCfg) {
      throw new Error('REPOS product config missing');
    }

    const consumerHandle = new ConsumerHandle(client, consumerCfg);
    const productHandle = new ProductHandle(client, productCfg);

    const adapter = new MultiRepoGitHubAdapter(
      parsed,
      client,
      tokenProvider,
      consumerHandle,
      productHandle
    );

    // Wire read-only handles
    for (const [key, cfg] of parsed.map) {
      if (cfg.role === 'read_only') {
        const id = key.startsWith('read_only:')
          ? key.slice('read_only:'.length)
          : key;
        adapter.readOnlyHandles.set(id, new ReadOnlyHandle(client, cfg));
      }
    }

    if (!opts.skipPrivacyCheck) {
      await assertConsumerPrivacy(parsed, async (cfg) => {
        // Use a temporary handle for the specific consumer cfg
        const h = new ConsumerHandle(client, cfg);
        const meta = await h.getRepoMetadata();
        return meta.private === true;
      });
    }

    return adapter;
  }

  consumer(): ConsumerHandle {
    return this.consumerHandle;
  }

  product(): ProductHandle {
    return this.productHandle;
  }

  readOnly(key: string): ReadOnlyHandle {
    const h = this.readOnlyHandles.get(key);
    if (!h) {
      throw new Error(`UNKNOWN_REPO: read_only:${key}`);
    }
    return h;
  }

  async rateBudget(): Promise<RateBudget> {
    // Placeholder until rate-coordinator is wired; GitHub returns headers per call.
    return { remaining: 5000, reset: Math.floor(Date.now() / 1000) + 3600 };
  }

  async transaction(_steps: TransactionStep[]): Promise<TransactionResult> {
    // Phase 2
    throw new Error('transaction executor not yet implemented');
  }

  /** Expose token expiry for /admin/status diagnostics (never the token). */
  tokenExpiresAt(): number | null {
    return this.tokenProvider.peekExpiresAt();
  }
}
