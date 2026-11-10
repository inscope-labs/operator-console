/**
 * Gateway A Worker entry point.
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
import { registerHaltRoutes } from './routes/halt';
import { NonceStore } from './durable-objects/nonce-store';
import { EnrolmentRegistry } from './durable-objects/enrolment-registry';
import { HaltState } from './durable-objects/halt-state';
import { PlanDO } from './durable-objects/plan-do';
import './guards/register-core';

export type { Env } from './runtime';
export { getAdapter } from './runtime';

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
registerHaltRoutes();

export default {
  async fetch(
    request: Request,
    env: import('./runtime').Env,
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

export { NonceStore, EnrolmentRegistry, HaltState, PlanDO };
