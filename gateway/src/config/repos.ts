/**
 * REPOS map parsing + G1 privacy validation at startup.
 * Spec §11.3, §18.2.
 *
 * The set of repositories is configuration, not request input.
 * Construction is fail-closed if any consumer entry is not private.
 */

import type { RepoConfig, RepoKey } from '../adapter/multi-repo-adapter';

export interface ParsedRepos {
  map: ReadonlyMap<RepoKey, RepoConfig>;
  consumerKeys: RepoKey[];
  productKey: RepoKey | null;
}

/**
 * Parse the REPOS environment JSON.
 * Throws on malformed input or missing required roles.
 */
export function parseRepos(raw: string): ParsedRepos {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new Error('REPOS is not valid JSON');
  }
  if (!parsed || typeof parsed !== 'object') {
    throw new Error('REPOS must be a JSON object');
  }

  const map = new Map<RepoKey, RepoConfig>();
  const consumerKeys: RepoKey[] = [];
  let productKey: RepoKey | null = null;

  for (const [key, value] of Object.entries(
    parsed as Record<string, unknown>
  )) {
    const cfg = value as Partial<RepoConfig>;
    if (!cfg.role || !cfg.owner || !cfg.name) {
      throw new Error(`REPOS entry ${key} missing role/owner/name`);
    }
    if (!['consumer', 'product', 'read_only'].includes(cfg.role)) {
      throw new Error(`REPOS entry ${key} has invalid role: ${cfg.role}`);
    }
    const repoKey = key as RepoKey;
    const entry: RepoConfig = {
      role: cfg.role,
      owner: cfg.owner,
      name: cfg.name,
      private_required: cfg.private_required ?? cfg.role === 'consumer',
      permission: cfg.permission ?? 'contents:write',
    };
    map.set(repoKey, entry);
    if (entry.role === 'consumer') consumerKeys.push(repoKey);
    if (entry.role === 'product') productKey = repoKey;
  }

  if (consumerKeys.length === 0) {
    throw new Error('REPOS must contain at least one consumer repository');
  }
  if (!productKey) {
    throw new Error('REPOS must contain a product repository');
  }

  return { map, consumerKeys, productKey };
}

/**
 * G1 startup check — every consumer repo must be private.
 * Called once at Worker/adapter construction.
 * `checkPrivacy` should call the GitHub API (getRepoMetadata) and return
 * whether the repository is private.
 *
 * Fail closed: any non-private consumer aborts construction.
 */
export async function assertConsumerPrivacy(
  repos: ParsedRepos,
  checkPrivacy: (cfg: RepoConfig) => Promise<boolean>
): Promise<void> {
  for (const key of repos.consumerKeys) {
    const cfg = repos.map.get(key);
    if (!cfg) continue;
    if (!cfg.private_required) continue;
    const isPrivate = await checkPrivacy(cfg);
    if (!isPrivate) {
      throw new Error(
        `G1 fail-closed: consumer repo ${cfg.owner}/${cfg.name} is not private`
      );
    }
  }
}
