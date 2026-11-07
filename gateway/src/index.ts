/**
 * cbridge Worker entry point.
 * Sole enforcement point for the operator console.
 */

import { dispatch } from './routes/router';
import { registerHealthRoutes } from './routes/health';
import { registerStatusRoutes } from './routes/status';
import { registerPlanRoutes } from './routes/plans';
import { registerTaskRoutes } from './routes/tasks';
import { registerEnrollRoutes } from './routes/enroll';
import { registerBranchRoutes } from './routes/branches';
import { registerAdminRoutes } from './routes/admin';
import { registerDecisionRoutes } from './routes/decisions';
import { registerDoctorRoutes } from './routes/doctor';
import { registerSyncRoutes } from './routes/sync';
import { MultiRepoGitHubAdapter } from './adapter/multi-repo-adapter';
import { NonceStore } from './durable-objects/nonce-store';
import { EnrolmentRegistry } from './durable-objects/enrolment-registry';
import './guards/register-core';

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

  PLAN_DO: DurableObjectNamespace;
  NONCE_STORE: DurableObjectNamespace;
  ENROLMENT_REGISTRY: DurableObjectNamespace;
  AUDIT_STREAM: DurableObjectNamespace;

  __adapter?: MultiRepoGitHubAdapter;
}

registerHealthRoutes();
registerStatusRoutes();
registerPlanRoutes();
registerTaskRoutes();
registerEnrollRoutes();
registerBranchRoutes();
registerAdminRoutes();
registerDecisionRoutes();
registerDoctorRoutes();
registerSyncRoutes();

export { getAdapter } from './adapter/get-adapter';

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

export { NonceStore, EnrolmentRegistry };
