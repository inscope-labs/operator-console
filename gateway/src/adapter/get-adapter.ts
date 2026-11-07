/**
 * Adapter initialization helper.
 * Extracted to break circular dependency between index.ts and routes.
 */

import { MultiRepoGitHubAdapter } from './multi-repo-adapter';

export interface AdapterEnv {
  REPOS?: string;
  GITHUB_APP_ID?: string;
  GITHUB_APP_PRIVATE_KEY?: string;
  GITHUB_APP_INSTALLATION_ID?: string;
  __adapter?: MultiRepoGitHubAdapter;
}

export async function getAdapter(env: AdapterEnv): Promise<MultiRepoGitHubAdapter> {
  if (env.__adapter) return env.__adapter;
  if (
    !env.REPOS ||
    !env.GITHUB_APP_ID ||
    !env.GITHUB_APP_PRIVATE_KEY ||
    !env.GITHUB_APP_INSTALLATION_ID
  ) {
    throw new Error(
      'adapter credentials incomplete — set REPOS, GITHUB_APP_ID, GITHUB_APP_PRIVATE_KEY, GITHUB_APP_INSTALLATION_ID'
    );
  }
  const adapter = await MultiRepoGitHubAdapter.create({
    reposJson: env.REPOS,
    secrets: {
      appId: env.GITHUB_APP_ID,
      privateKeyPem: env.GITHUB_APP_PRIVATE_KEY,
      installationId: env.GITHUB_APP_INSTALLATION_ID,
    },
  });
  env.__adapter = adapter;
  return adapter;
}
