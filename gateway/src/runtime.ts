/**
 * Shared runtime helpers for the Worker entry point and route modules.
 * Extracted from index.ts so routes can call getAdapter without a circular import.
 */

import { MultiRepoGitHubAdapter } from './adapter/multi-repo-adapter';

export interface Env {
  ENVIRONMENT?: string;
  ALLOWED_AUTH_TIERS?: string;
  ATTESTATION_MODE?: string;
  ATTESTATION_ROOT_SHA256?: string;

  SIGNING_KEYS: string;
  OPERATOR_TOKEN: string;
  REPOS: string;

  GITHUB_APP_ID: string;
  GITHUB_APP_PRIVATE_KEY: string;
  GITHUB_APP_INSTALLATION_ID: string;

  PLAN_DO?: DurableObjectNamespace;
  NONCE_STORE: DurableObjectNamespace;
  ENROLMENT_REGISTRY: DurableObjectNamespace;
  AUDIT_STREAM?: DurableObjectNamespace;
  OPERATION_STORE?: DurableObjectNamespace;

  __adapter?: MultiRepoGitHubAdapter;
  /** Unit-test override for global halt marker. */
  __haltActive?: boolean;
  HALT_STATE?: DurableObjectNamespace;
}

export async function getAdapter(env: Env): Promise<MultiRepoGitHubAdapter> {
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
