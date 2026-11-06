/**
 * POST /decisions/:id/decide
 *
 * Fail-closed stub returning 501 NOT_IMPLEMENTED (T1-103) until Phase 3 (Stage 3.6).
 */

import { register, type RouteHandler } from './router';
import { errorResponse } from '../errors/respond';

const decideStubHandler: RouteHandler = async (request) => {
  const requestId = request.headers.get('x-request-id') ?? crypto.randomUUID();
  return errorResponse(
    'NOT_IMPLEMENTED',
    'decide endpoint not yet implemented',
    requestId
  );
};

export function registerDecisionRoutes(): void {
  register('POST', '/decisions/:id/decide', decideStubHandler, {
    requiresOperatorToken: true,
    requiresCapability: true,
  });
}
