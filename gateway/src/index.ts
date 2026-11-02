/**
 * cbridge Worker entry point.
 *
 * Sole enforcement point for the operator console.
 * All routes, capability verification, guards and GitHub interaction
 * are reached through this handler.
 */

import { dispatch } from './routes/router';
import { registerStatusRoutes } from './routes/status';
import { registerPlanRoutes } from './routes/plans';
import { MultiRepoGitHubAdapter } from './adapter/multi-repo-adapter';

export interface Env {
  // Secrets / bindings (names only — values never in source)
  SIGNING_KEYS: string;
  OPERATOR_TOKEN: string;
  REPOS: string;

  /** GitHub App credentials for InstallationTokenProvider */
  GITHUB_APP_ID: string;
  GITHUB_APP_PRIVATE_KEY: string;
  GITHUB_APP_INSTALLATION_ID: string;

  // Durable Object bindings
  PLAN_DO: DurableObjectNamespace;
  NONCE_STORE: DurableObjectNamespace;
  ENROLMENT_REGISTRY: DurableObjectNamespace;
  AUDIT_STREAM: DurableObjectNamespace;

  /** Optional: cached adapter on the isolate (set after first successful create). */
  __adapter?: MultiRepoGitHubAdapter;
}

registerStatusRoutes();
registerPlanRoutes();

/**
 * Lazily construct the MultiRepoGitHubAdapter once per isolate.
 * Failures surface as structured errors from routes that need it.
 */
export async function getAdapter(env: Env): Promise<MultiRepoGitHubAdapter> {
  if (env.__adapter) return env.__adapter;
  if (!env.REPOS || !env.GITHUB_APP_ID || !env.GITHUB_APP_PRIVATE_KEY || !env.GITHUB_APP_INSTALLATION_ID) {
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

export default {
  async fetch(
    request: Request,
    env: Env,
    ctx: ExecutionContext
  ): Promise<Response> {
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'access-control-allow-origin': '*',
          'access-control-allow-methods': 'GET, POST, DELETE, OPTIONS',
          'access-control-allow-headers':
            'authorization, content-type, x-request-id, x-device-id',
          'access-control-max-age': '86400',
        },
      });
    }

    return dispatch(request, env, ctx);
  },
};
