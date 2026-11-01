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

export interface Env {
  // Secrets / bindings (names only — values never in source)
  SIGNING_KEYS: string; // JSON public keys
  OPERATOR_TOKEN: string;
  // GITHUB_TOKEN is obtained via InstallationTokenProvider, not a long-lived secret
  REPOS: string; // JSON map of RepoConfig

  // Durable Object bindings
  PLAN_DO: DurableObjectNamespace;
  NONCE_STORE: DurableObjectNamespace;
  ENROLMENT_REGISTRY: DurableObjectNamespace;
  AUDIT_STREAM: DurableObjectNamespace;
}

// Register read-only surfaces once at isolate startup.
registerStatusRoutes();
registerPlanRoutes();

export default {
  async fetch(
    request: Request,
    env: Env,
    ctx: ExecutionContext
  ): Promise<Response> {
    // CORS preflight for local console development (tightened later).
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

// Durable Object class exports will be added once implemented.
// export { PlanDO } from './durable-objects/plan-do';
// export { NonceStore } from './durable-objects/nonce-store';
// export { EnrolmentRegistry } from './durable-objects/enrolment-registry';
// export { AuditStream } from './durable-objects/audit-stream';
